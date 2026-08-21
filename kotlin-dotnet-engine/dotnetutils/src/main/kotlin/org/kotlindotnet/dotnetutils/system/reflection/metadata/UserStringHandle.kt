// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType

/** #US heap handle; string indices must fit into 24 bits since they are used in IL stream tokens. */
@JvmInline
value class UserStringHandle internal constructor(
    // bits:
    //     31: 0
    // 24..30: 0
    //  0..23: index
    private val offset: Int,
) {
    fun toHandle(): Handle = Handle(HandleType.USER_STRING.toUByte(), offset)

    val isNil: Boolean
        get() = offset == 0

    fun getHeapOffset(): Int = offset

    override fun toString(): String = "UserStringHandle(offset=$offset)"

    companion object {
        internal fun fromOffset(heapOffset: Int): UserStringHandle = UserStringHandle(heapOffset)

        internal fun fromHandle(handle: Handle): UserStringHandle {
            check(handle.vType.toUInt() == HandleType.USER_STRING) { "handle has wrong kind for UserStringHandle" }
            return UserStringHandle(handle.offset)
        }
    }
}
