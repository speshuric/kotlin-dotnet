// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

@JvmInline
value class LocalVariableHandle private constructor(
    internal val rowId: Int,
) {
    fun toHandle(): Handle = Handle(tokenTypeSmall, rowId)

    fun toEntityHandle(): EntityHandle = EntityHandle(tokenType or rowId.toUInt())

    val isNil: Boolean
        get() = rowId == 0

    override fun toString(): String = "$className(rowId=$rowId)"

    companion object {
        private val className: String get() = "LocalVariableHandle"
        private val tokenType: UInt get() = TokenTypeIds.LOCAL_VARIABLE
        private val tokenTypeSmall: UByte get() = HandleType.LOCAL_VARIABLE.toUByte()

        internal fun fromRowId(rowId: Int) = LocalVariableHandle(rowId)

        internal fun fromHandle(handle: Handle): LocalVariableHandle {
            check(handle.vType == tokenTypeSmall) { "handle has wrong kind for $className" }
            return fromRowId(handle.rowId)
        }

        internal fun fromEntityHandle(entity: EntityHandle): LocalVariableHandle {
            check(entity.vType == tokenType) { "entity handle has wrong kind for $className" }
            return fromRowId(entity.rowId)
        }
    }
}
