// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/Encoding/LabelHandle.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

/**
 * 1-based id identifying the label within the context of a ControlFlowBuilder
 * (ported with the Encoding iteration).
 */
@JvmInline
value class LabelHandle private constructor(val id: Int) {
    val isNil: Boolean
        get() = id == 0

    // Без reflection — модуль компилируется и в .NET; имя нужно для
    // диагностики и будущей унификации хэндлов.
    private val className: String get() = "LabelHandle"

    companion object {
        internal fun fromId(id: Int) = LabelHandle(id)
    }

    override fun toString(): String = "$className($id)"
}
