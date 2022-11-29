/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.profdiff.core;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationTree;

/**
 * An optimization context tree is an inlining tree extended with optimizations placed in their
 * method context. Optimization-context trees make it easier to attribute optimizations to inlining
 * decisions.
 *
 * Consider the inlining and optimization tree below:
 *
 * <pre>
 * A compilation unit of the method a()
 *     Inlining tree
 *         a() at bci -1
 *             b() at bci 1
 *                 d() at bci 3
 *             c() at bci 2
 *     Optimization tree
 *         RootPhase
 *             SomeOptimizationPhase
 *                 SomeOptimization OptimizationA at bci {a(): 3}
 *                 SomeOptimization OptimizationB at bci {b(): 4, a(): 1}
 *                 SomeOptimization OptimizationC at bci {c(): 5, a(): 2}
 *                 SomeOptimization OptimizationD at bci {d(): 6, b(): 3, a(): 1}
 * </pre>
 *
 * By combining the trees, we obtain the following optimization-context tree:
 *
 * <pre>
 * A compilation unit of the method a()
 *     Optimization-context tree
 *         a() at bci -1
 *             SomeOptimization OptimizationA at bci 3
 *             b() at bci 1
 *                 SomeOptimization OptimizationB at bci 4
 *                 d() at bci 3
 *                     SomeOptimization OptimizationD at bci 6
 *             c() at bci 2
 *               SomeOptimization OptimizationC at bci 5
 * </pre>
 */
public final class OptimizationContextTree {
    /**
     * The root node of the tree.
     */
    private final OptimizationContextTreeNode root;

    public OptimizationContextTree(OptimizationContextTreeNode root) {
        this.root = root;
    }

    /**
     * Gets the root node of the tree.
     */
    public OptimizationContextTreeNode getRoot() {
        return root;
    }

    /**
     * Recursively finds the last inlining node on a path. Returns {@code null} if there is no such
     * node. Only tree nodes corresponding to inlining tree nodes are considered. Inlining tree
     * nodes corresponding to abstract methods ({@link InliningTreeNode#isAbstract()}) are skipped
     * as if they were removed from the tree and their children reattached to the closest ancestor.
     *
     * If several nodes match the path, then the first match is returned. Note that backtracking is
     * necessary, because a path element may match 2 different edges, and one of the edges may lead
     * to a dead-end.
     *
     * @param currentRoot the current root node to search from (initially the root of tree)
     * @param lastNode the last non-abstract node seen on the path
     * @param path the path from root
     * @param pathIndex the index to the next path element in the path (initially 0)
     * @return the last node found on the path or {@code null}
     */
    private static OptimizationContextTreeNode findLastNodeAt(OptimizationContextTreeNode currentRoot, OptimizationContextTreeNode lastNode, InliningPath path, int pathIndex) {
        if (pathIndex >= path.size()) {
            return lastNode;
        }
        for (OptimizationContextTreeNode child : currentRoot.getChildren()) {
            if (child.getOriginalInliningTreeNode() == null) {
                continue;
            }
            InliningTreeNode inliningTreeNode = child.getOriginalInliningTreeNode();
            OptimizationContextTreeNode result = null;
            if (inliningTreeNode.isAbstract()) {
                result = findLastNodeAt(child, lastNode, path, pathIndex);
            } else if (path.get(pathIndex).matches(inliningTreeNode.pathElement())) {
                result = findLastNodeAt(child, child, path, pathIndex + 1);
            }
            if (result != null) {
                return result;
            }
        }
        return lastNode;
    }

    /**
     * Creates an optimization-context tree by cloning an inlining tree. Stores the mapping from
     * original nodes to created nodes in {@code replacements}.
     *
     * @param inliningTree the inlining tree to be cloned
     * @param replacements the mapping from inlining-tree nodes to cloned optimization-context nodes
     * @return an optimization-context tree corresponding to a cloned inlining tree
     */
    private static OptimizationContextTree fromInliningTree(InliningTree inliningTree, EconomicMap<InliningTreeNode, OptimizationContextTreeNode> replacements) {
        OptimizationContextTreeNode root = new OptimizationContextTreeNode();
        fromInliningNode(root, inliningTree.getRoot(), replacements);
        return new OptimizationContextTree(root);
    }

    /**
     * Clones an inlining subtree into an optimization-context tree node. Stores the mapping from
     * original nodes to created nodes in {@code replacements}.
     *
     * @param parent the optimization-context node (initially without children)
     * @param replacements the mapping from inlining-tree nodes to cloned optimization-context nodes
     * @param inliningTreeNode the inlining subtree to be cloned into the optimization-context tree
     */
    private static void fromInliningNode(OptimizationContextTreeNode parent, InliningTreeNode inliningTreeNode, EconomicMap<InliningTreeNode, OptimizationContextTreeNode> replacements) {
        OptimizationContextTreeNode optimizationContextTreeNode = new OptimizationContextTreeNode(inliningTreeNode);
        replacements.put(inliningTreeNode, optimizationContextTreeNode);
        parent.addChild(optimizationContextTreeNode);
        for (InliningTreeNode child : inliningTreeNode.getChildren()) {
            fromInliningNode(optimizationContextTreeNode, child, replacements);
        }
    }

    /**
     * Inserts all optimizations from the given optimization tree to the appropriate context in the
     * optimization-context tree. The appropriate context is stated by the optimization's
     * {@link InliningPath#ofEnclosingMethod(Optimization) enclosing methods}. The path to the
     * enclosing method is followed in the optimization-context tree (by only considering node
     * origination in inlining tree nodes), and the optimization is placed in the last node on the
     * path.
     *
     * @param optimizationTree the source optimization tree with optimizations
     */
    private void insertAllOptimizationNodes(OptimizationTree optimizationTree) {
        optimizationTree.getRoot().forEach(node -> {
            if (!(node instanceof Optimization)) {
                return;
            }
            Optimization optimization = (Optimization) node;
            InliningPath optimizationPath = InliningPath.ofEnclosingMethod(optimization);
            OptimizationContextTreeNode lastNode = findLastNodeAt(root, root, optimizationPath, 0);
            lastNode.addChild(new OptimizationContextTreeNode(optimization));
        });
    }

    /**
     * Inserts info nodes to the inlining tree nodes whose path from root is duplicate. Duplicate paths
     * make it impossible to correctly attribute optimizations.
     *
     * As an example, consider the following inlining tree:
     *
     * <pre>
     * Inlining tree
     *     a() at bci -1
     *         b() at bci 1
     *             d() at bci 3
     *         b() at bci 1
     *             d() at bci 3
     *         c() at bci 2
     * </pre>
     *
     * Suppose that we have an optimization with a position like {@code {a(): 1, b(): x}} or
     * {@code {a(): 1, b(): 3, d(): x}}. It is ambiguous to which branch of the tree it belongs. This
     * method inserts a warning to each node which might have ambiguous optimizations.
     *
     * <pre>
     * Optimization-context tree
     *     a() at bci -1
     *         b() at bci 1
     *             Warning
     *             d() at bci 3
     *                 Warning
     *         b() at bci 1
     *             Warning
     *             d() at bci 3
     *                 Warning
     *         c() at bci 2
     * </pre>
     *
     * @param inliningTree the original inlining tree
     * @param replacements the mapping from inlining-tree nodes to cloned optimization-context nodes
     */
    private static void insertWarningNodesForDuplicatePaths(InliningTree inliningTree, EconomicMap<InliningTreeNode, OptimizationContextTreeNode> replacements) {
        MapCursor<InliningTreeNode, OptimizationContextTreeNode> cursor = replacements.getEntries();
        while (cursor.advance()) {
            if (!cursor.getKey().isPositive()) {
                continue;
            }
            List<InliningTreeNode> found = inliningTree.findNodesAt(InliningPath.fromRootToNode(cursor.getKey()));
            if (found.size() > 1) {
                cursor.getValue().addChild(OptimizationContextTreeNode.duplicatePathWarning());
            }
        }
    }

    /**
     * Creates an optimization-context tree from an inlining and optimization tree. It is assumed
     * that the inlining tree is non-empty.
     *
     * @param inliningTree the inlining tree
     * @param optimizationTree the optimization tree
     * @return the created optimization-context tree
     */
    public static OptimizationContextTree createFrom(InliningTree inliningTree, OptimizationTree optimizationTree) {
        assert inliningTree.getRoot() != null;
        EconomicMap<InliningTreeNode, OptimizationContextTreeNode> replacements = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        OptimizationContextTree optimizationContextTree = fromInliningTree(inliningTree, replacements);
        OptimizationContextTree.insertWarningNodesForDuplicatePaths(inliningTree, replacements);
        optimizationContextTree.insertAllOptimizationNodes(optimizationTree);
        optimizationContextTree.root.forEach(node -> node.getChildren().sort(OptimizationContextTreeNode::compareTo));
        return optimizationContextTree;
    }
}
