// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (EntityHandle.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations from the original:
//  - implicit/explicit conversion operators are ported as functions
//    [toHandle]/[companion fromHandle].

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

/**
 * Represents a metadata entity (type reference/definition/specification, method definition,
 * custom attribute, etc.).
 *
 * Use [EntityHandle] to store multiple kinds of entity handles.
 * It has smaller memory footprint than [Handle].
 */
@JvmInline
value class EntityHandle internal constructor(
    // bits:
    //     31: IsVirtual
    // 24..30: type
    //  0..23: row id
    val vToken: UInt,
) {
    internal val type: UInt
        get() = vToken and TokenTypeIds.TYPE_MASK

    internal val vType: UInt
        get() = vToken and (TokenTypeIds.VIRTUAL_BIT or TokenTypeIds.TYPE_MASK)

    internal val isVirtual: Boolean
        get() = (vToken and TokenTypeIds.VIRTUAL_BIT) != 0u

    /** Virtual handle is never nil. */
    val isNil: Boolean
        get() = (vToken and (TokenTypeIds.VIRTUAL_BIT or TokenTypeIds.RID_MASK)) == 0u

    internal val rowId: Int
        get() = (vToken and TokenTypeIds.RID_MASK).toInt()

    /**
     * Value stored in a specific entity handle
     * (see TypeDefinitionHandle, MethodDefinitionHandle, etc. — ported later).
     */
    internal val specificHandleValue: UInt
        get() = vToken and (TokenTypeIds.VIRTUAL_BIT or TokenTypeIds.RID_MASK)

    val kind: HandleKind
        get() {
            // EntityHandles cannot be StringHandles and therefore we do not need
            // to handle stripping the extra non-virtual string type bits here.
            return requireNotNull(HandleKind.fromRaw((type shr TokenTypeIds.ROW_ID_BIT_COUNT).toUByte())) {
                "unknown entity handle kind"
            }
        }

    internal val token: Int
        get() = vToken.toInt()

    fun toHandle(): Handle = Handle.fromVToken(vToken)

    override fun toString(): String = "EntityHandle(0x%08X)".format(vToken.toInt())

    companion object {
        /** Nil handle (equivalent of C# default(EntityHandle)). */
        val NIL: EntityHandle = EntityHandle(0u)

        val MODULE_DEFINITION: EntityHandle = EntityHandle(TokenTypeIds.MODULE or 1u)

        val ASSEMBLY_DEFINITION: EntityHandle = EntityHandle(TokenTypeIds.ASSEMBLY or 1u)

        /** Port of the C# explicit conversion operator `(EntityHandle)handle`. */
        fun fromHandle(handle: Handle): EntityHandle {
            check(!handle.isHeapHandle) { "heap handle cannot be converted to EntityHandle" }
            return EntityHandle(handle.entityHandleValue)
        }

        /** Port of `EntityHandle.Compare`. All virtual tokens sort after non-virtual ones. */
        internal fun compare(left: EntityHandle, right: EntityHandle): Int =
            left.vToken.compareTo(right.vToken)
    }
}
