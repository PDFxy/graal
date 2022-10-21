/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.graal.code;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;

public class SubstratePlatformConfigurationProvider implements PlatformConfigurationProvider {
    private final BarrierSet barrierSet;

    protected SubstratePlatformConfigurationProvider(BarrierSet barrierSet) {
        this.barrierSet = barrierSet;
    }

    @Override
    public BarrierSet getBarrierSet() {
        return barrierSet;
    }

    @Override
    public boolean canVirtualizeLargeByteArrayAccess() {
        return true;
    }

}

@AutomaticallyRegisteredImageSingleton(value = {PlatformConfigurationProviderFactory.class})
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class SubstratePlatformConfigurationProviderFactory implements PlatformConfigurationProviderFactory {

    @Override
    public SubstratePlatformConfigurationProvider getProvider(BarrierSet barrierSet) {
        return new SubstratePlatformConfigurationProvider(barrierSet);
    }
}
