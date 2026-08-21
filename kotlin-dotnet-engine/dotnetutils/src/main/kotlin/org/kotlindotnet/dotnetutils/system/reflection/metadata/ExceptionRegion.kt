// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (IL/ExceptionRegion.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

data class ExceptionRegion internal constructor(
    val kind: ExceptionRegionKind,

    /** Start IL offset of the try block. */
    val tryOffset: Int,

    /** Length in bytes of try block. */
    val tryLength: Int,

    /** Start IL offset of the exception handler. */
    val handlerOffset: Int,

    /** Length in bytes of the exception handler. */
    val handlerLength: Int,

    /** IL offset of the start of the filter block, or token of the catch type. */
    val classTokenOrFilterOffset: Int,
) {
    /** IL offset of the start of the filter block, or -1 if the region is not a filter. */
    val filterOffset: Int
        get() = if (kind == ExceptionRegionKind.FILTER) classTokenOrFilterOffset else -1

    /**
     * Returns a TypeRef, TypeDef, or TypeSpec handle if the region represents a catch,
     * nil handle otherwise.
     */
    val catchType: EntityHandle
        get() =
            if (kind == ExceptionRegionKind.CATCH) {
                EntityHandle(classTokenOrFilterOffset.toUInt())
            } else {
                EntityHandle(0u)
            }
}
