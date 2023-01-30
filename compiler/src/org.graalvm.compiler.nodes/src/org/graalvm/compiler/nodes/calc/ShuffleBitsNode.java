/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;
import static org.graalvm.compiler.nodes.calc.ShuffleBitsNode.ShuffleMode.COMPRESS;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Shuffle bits. Intrinsic for {@link Integer#compress}, {@link Integer#expand},
 * {@link Long#compress}, {@link Long#expand}.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class ShuffleBitsNode extends BinaryNode implements ArithmeticLIRLowerable {

    public enum ShuffleMode {
        COMPRESS,
        EXPAND,
    }

    public static final NodeClass<ShuffleBitsNode> TYPE = NodeClass.create(ShuffleBitsNode.class);

    private final ShuffleMode shuffleMode;

    public ShuffleBitsNode(ValueNode value, ValueNode mask, ShuffleMode shuffleMode) {
        super(TYPE, value.stamp(NodeView.DEFAULT).unrestricted(), value, mask);

        this.shuffleMode = shuffleMode;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        // TODO constant stamp
        return stamp(NodeView.DEFAULT);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        // TODO constants
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        if (shuffleMode == COMPRESS) {
            builder.setResult(this, gen.emitIntegerCompress(builder.operand(getX()), builder.operand(getY())));
        } else {
            builder.setResult(this, gen.emitIntegerExpand(builder.operand(getX()), builder.operand(getY())));
        }
    }
}
