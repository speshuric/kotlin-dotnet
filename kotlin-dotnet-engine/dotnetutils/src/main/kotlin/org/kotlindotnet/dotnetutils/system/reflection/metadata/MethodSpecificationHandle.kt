// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

@JvmInline
value class MethodSpecificationHandle internal constructor(
    internal val rowId: Int,
) {
    fun toHandle(): Handle = Handle(HandleType.METHOD_SPEC.toUByte(), rowId)

    fun toEntityHandle(): EntityHandle = EntityHandle(TokenTypeIds.METHOD_SPEC or rowId.toUInt())

    val isNil: Boolean
        get() = rowId == 0

    override fun toString(): String = "MethodSpecificationHandle(rowId=$rowId)"

    companion object {
        private val TOKEN_TYPE: UInt = TokenTypeIds.METHOD_SPEC
        private val TOKEN_TYPE_SMALL: UByte = HandleType.METHOD_SPEC.toUByte()

        internal fun fromRowId(rowId: Int): MethodSpecificationHandle = MethodSpecificationHandle(rowId)

        internal fun fromHandle(handle: Handle): MethodSpecificationHandle {
            check(handle.vType == TOKEN_TYPE_SMALL) { "handle has wrong kind for MethodSpecificationHandle" }
            return MethodSpecificationHandle(handle.rowId)
        }

        internal fun fromEntityHandle(entity: EntityHandle): MethodSpecificationHandle {
            check(entity.vType == TOKEN_TYPE) { "entity handle has wrong kind for MethodSpecificationHandle" }
            return MethodSpecificationHandle(entity.rowId)
        }
    }
}
