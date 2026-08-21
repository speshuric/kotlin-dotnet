// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Handle.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations from the original:
//  - static fields ModuleDefinition/AssemblyDefinition are deferred
//    until the typed-handle structs iteration.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

/**
 * Represents any metadata entity (type reference/definition/specification, method definition,
 * custom attribute, etc.) or value (string, blob, guid, user string).
 *
 * Use [Handle] to store multiple kinds of handles.
 */
data class Handle internal constructor(
    // bits:
    //    7: IsVirtual
    // 0..6: token type
    val vType: UByte,
    val value: Int,
) {
    // for entity handles:
    internal val rowId: Int
        get() = value

    // for heap handles:
    internal val offset: Int
        get() = value

    /**
     * Token type (0x##000000), does not include virtual flag.
     */
    internal val entityHandleType: UInt
        get() = type shl TokenTypeIds.ROW_ID_BIT_COUNT

    /**
     * Small token type (0x##), does not include virtual flag.
     */
    internal val type: UInt
        get() = vType.toUInt() and HandleType.TYPE_MASK

    /**
     * Value stored in an [EntityHandle].
     */
    internal val entityHandleValue: UInt
        get() = vType.toUInt() shl TokenTypeIds.ROW_ID_BIT_COUNT or value.toUInt()

    /**
     * Value stored in a concrete entity handle
     * (see TypeDefinitionHandle, MethodDefinitionHandle, etc. — ported later).
     */
    internal val specificEntityHandleValue: UInt
        get() = (vType and HandleType.VIRTUAL_BIT.toUByte()).toUInt() shl TokenTypeIds.ROW_ID_BIT_COUNT or value.toUInt()

    internal val isVirtual: Boolean
        get() = (vType and HandleType.VIRTUAL_BIT.toUByte()) != 0u.toUByte()

    internal val isHeapHandle: Boolean
        get() = (vType and HandleType.HEAP_MASK.toUByte()) == HandleType.HEAP_MASK.toUByte()

    /**
     * Port of `Handle.Kind`.
     *
     * Deviation from the original: C# casts any byte to the enum (including values
     * without a named member); here an undefined handle type fails fast. Cannot
     * happen for handles produced by the writer; full reader fidelity (accepting
     * arbitrary input) is deferred to the reader iteration.
     */
    val kind: HandleKind
        get() {
            val t = type

            // Do not surface extra non-virtual string type bits in public handle kind
            if ((t and HandleType.NON_VIRTUAL_STRING_TYPE_MASK.inv()) == HandleType.STRING) {
                return HandleKind.STRING
            }

            return requireNotNull(HandleKind.fromRaw(t.toUByte())) { "unknown handle kind" }
        }

    /** Virtual handles are never nil. */
    val isNil: Boolean
        get() = (value.toUInt() or (vType and HandleType.VIRTUAL_BIT.toUByte()).toUInt()) == 0u

    internal val isEntityOrUserStringHandle: Boolean
        get() = type <= HandleType.USER_STRING

    internal val token: Int
        get() = vType.toInt() shl TokenTypeIds.ROW_ID_BIT_COUNT or value

    fun toEntityHandle(): EntityHandle = EntityHandle(entityHandleValue)

    override fun toString(): String = "Handle(0x%08X)".format(token)

    companion object {
        /**
         * Creates a [Handle] from a token or a token combined with a virtual flag.
         */
        internal fun fromVToken(vToken: UInt): Handle =
            Handle(
                vType = (vToken shr TokenTypeIds.ROW_ID_BIT_COUNT).toUByte(),
                value = (vToken and TokenTypeIds.RID_MASK).toInt(),
            )

        /**
         * Port of `Handle.Compare`. All virtual tokens sort after non-virtual ones.
         * The order of handles that differ in kind is undefined,
         * but we include it so that we ensure consistency with equals.
         */
        internal fun compare(left: Handle, right: Handle): Int {
            val l = (left.value.toLong() and 0xFFFFFFFFL) or (left.vType.toLong() shl 32)
            val r = (right.value.toLong() and 0xFFFFFFFFL) or (right.vType.toLong() shl 32)
            return l.compareTo(r)
        }
    }
}
