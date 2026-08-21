// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

@JvmInline
value class MemberReferenceHandle internal constructor(
    internal val rowId: Int,
) {
    fun toHandle(): Handle = Handle(HandleType.MEMBER_REF.toUByte(), rowId)

    fun toEntityHandle(): EntityHandle = EntityHandle(TokenTypeIds.MEMBER_REF or rowId.toUInt())

    val isNil: Boolean
        get() = rowId == 0

    override fun toString(): String = "MemberReferenceHandle(rowId=$rowId)"

    companion object {
        private val TOKEN_TYPE: UInt = TokenTypeIds.MEMBER_REF
        private val TOKEN_TYPE_SMALL: UByte = HandleType.MEMBER_REF.toUByte()

        internal fun fromRowId(rowId: Int): MemberReferenceHandle = MemberReferenceHandle(rowId)

        internal fun fromHandle(handle: Handle): MemberReferenceHandle {
            check(handle.vType == TOKEN_TYPE_SMALL) { "handle has wrong kind for MemberReferenceHandle" }
            return MemberReferenceHandle(handle.rowId)
        }

        internal fun fromEntityHandle(entity: EntityHandle): MemberReferenceHandle {
            check(entity.vType == TOKEN_TYPE) { "entity handle has wrong kind for MemberReferenceHandle" }
            return MemberReferenceHandle(entity.rowId)
        }
    }
}
