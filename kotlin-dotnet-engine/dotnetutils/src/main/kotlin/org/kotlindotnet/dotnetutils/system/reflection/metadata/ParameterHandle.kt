// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

@JvmInline
value class ParameterHandle internal constructor(
    internal val rowId: Int,
) {
    fun toHandle(): Handle = Handle(HandleType.PARAM_DEF.toUByte(), rowId)

    fun toEntityHandle(): EntityHandle = EntityHandle(TokenTypeIds.PARAM_DEF or rowId.toUInt())

    val isNil: Boolean
        get() = rowId == 0

    override fun toString(): String = "ParameterHandle(rowId=$rowId)"

    companion object {
        private val TOKEN_TYPE: UInt = TokenTypeIds.PARAM_DEF
        private val TOKEN_TYPE_SMALL: UByte = HandleType.PARAM_DEF.toUByte()

        internal fun fromRowId(rowId: Int): ParameterHandle = ParameterHandle(rowId)

        internal fun fromHandle(handle: Handle): ParameterHandle {
            check(handle.vType == TOKEN_TYPE_SMALL) { "handle has wrong kind for ParameterHandle" }
            return ParameterHandle(handle.rowId)
        }

        internal fun fromEntityHandle(entity: EntityHandle): ParameterHandle {
            check(entity.vType == TOKEN_TYPE) { "entity handle has wrong kind for ParameterHandle" }
            return ParameterHandle(entity.rowId)
        }
    }
}
