// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType

/**
 * #Guid heap handle.
 *
 * The Guid heap is an array of GUIDs, each 16 bytes wide.
 * Its first element is numbered 1, its second 2, and so on.
 */
@JvmInline
value class GuidHandle internal constructor(
    internal val index: Int,
) {
    fun toHandle(): Handle = Handle(tokenTypeSmall, index)

    val isNil: Boolean
        get() = index == 0

    override fun toString(): String = "$className(index=$index)"

    companion object {
        private val className: String get() = "GuidHandle"
        private val tokenTypeSmall: UByte get() = HandleType.GUID.toUByte()

        internal fun fromIndex(heapIndex: Int): GuidHandle = GuidHandle(heapIndex)

        internal fun fromHandle(handle: Handle): GuidHandle {
            check(handle.vType == tokenTypeSmall) { "handle has wrong kind for $className" }
            return GuidHandle(handle.offset)
        }
    }
}
