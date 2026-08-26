// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

@JvmInline
value class DocumentHandle private constructor(
    internal val rowId: Int,
) {
    fun toEntityHandle(): EntityHandle = EntityHandle(tokenType or rowId.toUInt())

    val isNil: Boolean
        get() = rowId == 0

    override fun toString(): String = "DocumentHandle(rowId=$rowId)"

    companion object {
        private val tokenType: UInt get() = TokenTypeIds.DOCUMENT

        internal fun fromRowId(rowId: Int) = DocumentHandle(rowId)
    }
}
