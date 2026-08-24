// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations (WinRT projection machinery cut per ADR 0009):
//  - nested enum VirtualIndex, FromVirtualIndex/GetVirtualIndex,
//    VirtualValue and SubstituteTemplateParameters are not ported;
//  - storage simplified to the plain heap offset (the original keeps
//    virtual index/value in the low 29 bits for projected blobs).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType

/** #Blob heap handle. */
@JvmInline
value class BlobHandle private constructor(
    private val offset: Int,
) {
    fun toHandle(): Handle = Handle(tokenTypeSmall, offset)

    val isNil: Boolean
        get() = offset == 0

    fun getHeapOffset(): Int = offset

    override fun toString(): String = "$className(offset=$offset)"

    companion object {
        private val className: String get() = "BlobHandle"
        private val tokenTypeSmall: UByte get() = HandleType.BLOB.toUByte()

        internal fun fromOffset(heapOffset: Int): BlobHandle = BlobHandle(heapOffset)

        internal fun fromHandle(handle: Handle): BlobHandle {
            check(handle.vType == tokenTypeSmall) { "handle has wrong kind for $className" }
            return BlobHandle(handle.offset)
        }
    }
}
