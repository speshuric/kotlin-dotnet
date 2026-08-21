// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

/**
 * Deviation: WinRT projection members (nested enum VirtualIndex, FromVirtualIndex)/ * are cut per ADR 0009 (WinMD not supported); the handle keeps plain row-id storage.
 */
@JvmInline
value class AssemblyReferenceHandle internal constructor(
    internal val rowId: Int,
) {
    fun toHandle(): Handle = Handle(HandleType.ASSEMBLY_REF.toUByte(), rowId)

    fun toEntityHandle(): EntityHandle = EntityHandle(TokenTypeIds.ASSEMBLY_REF or rowId.toUInt())

    val isNil: Boolean
        get() = rowId == 0

    override fun toString(): String = "AssemblyReferenceHandle(rowId=$rowId)"

    companion object {
        private val TOKEN_TYPE: UInt = TokenTypeIds.ASSEMBLY_REF
        private val TOKEN_TYPE_SMALL: UByte = HandleType.ASSEMBLY_REF.toUByte()

        internal fun fromRowId(rowId: Int): AssemblyReferenceHandle = AssemblyReferenceHandle(rowId)

        internal fun fromHandle(handle: Handle): AssemblyReferenceHandle {
            check(handle.vType == TOKEN_TYPE_SMALL) { "handle has wrong kind for AssemblyReferenceHandle" }
            return AssemblyReferenceHandle(handle.rowId)
        }

        internal fun fromEntityHandle(entity: EntityHandle): AssemblyReferenceHandle {
            check(entity.vType == TOKEN_TYPE) { "entity handle has wrong kind for AssemblyReferenceHandle" }
            return AssemblyReferenceHandle(entity.rowId)
        }
    }
}
