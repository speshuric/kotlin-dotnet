// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

@JvmInline
value class ExportedTypeHandle internal constructor(
    internal val rowId: Int,
) {
    fun toHandle(): Handle = Handle(HandleType.EXPORTED_TYPE.toUByte(), rowId)

    fun toEntityHandle(): EntityHandle = EntityHandle(TokenTypeIds.EXPORTED_TYPE or rowId.toUInt())

    val isNil: Boolean
        get() = rowId == 0

    override fun toString(): String = "ExportedTypeHandle(rowId=$rowId)"

    companion object {
        private val TOKEN_TYPE: UInt = TokenTypeIds.EXPORTED_TYPE
        private val TOKEN_TYPE_SMALL: UByte = HandleType.EXPORTED_TYPE.toUByte()

        internal fun fromRowId(rowId: Int): ExportedTypeHandle = ExportedTypeHandle(rowId)

        internal fun fromHandle(handle: Handle): ExportedTypeHandle {
            check(handle.vType == TOKEN_TYPE_SMALL) { "handle has wrong kind for ExportedTypeHandle" }
            return ExportedTypeHandle(handle.rowId)
        }

        internal fun fromEntityHandle(entity: EntityHandle): ExportedTypeHandle {
            check(entity.vType == TOKEN_TYPE) { "entity handle has wrong kind for ExportedTypeHandle" }
            return ExportedTypeHandle(entity.rowId)
        }
    }
}
