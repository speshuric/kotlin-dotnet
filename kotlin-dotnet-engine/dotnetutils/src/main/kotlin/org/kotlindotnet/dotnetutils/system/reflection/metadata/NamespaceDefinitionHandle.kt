// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HeapHandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds

/**
 * A handle that represents a namespace definition.
 *
 * Non-virtual (namespace having at least one type or forwarder of its own):
 * heap offset is to the null-terminated full name of the namespace in the
 * #String heap. Virtual (namespace having child namespaces but no types of
 * its own): the virtual index is an auto-incremented value serving solely to
 * create unique values.
 */
@JvmInline
value class NamespaceDefinitionHandle private constructor(
    // bits:
    //     31: IsVirtual
    // 29..31: 0
    //  0..28: Heap offset or Virtual index
    val rawValue: UInt,
) {
    fun toHandle(): Handle =
        Handle(
            vType = (((rawValue and HeapHandleType.VIRTUAL_BIT) shr 24) or HandleType.NAMESPACE).toUByte(),
            value = (rawValue and HeapHandleType.OFFSET_MASK).toInt(),
        )

    val isNil: Boolean
        get() = rawValue == 0u

    val isVirtual: Boolean
        get() = (rawValue and HeapHandleType.VIRTUAL_BIT) != 0u

    fun getHeapOffset(): Int =
        (rawValue and HeapHandleType.OFFSET_MASK).toInt()

    val hasFullName: Boolean
        get() = !isVirtual

    fun getFullName(): StringHandle {
        assert(hasFullName)
        return StringHandle.fromOffset(getHeapOffset())
    }

    override fun toString(): String = "$className(0x%08X)".format(rawValue.toInt())

    // composite vType (virtual-bit layout) — tokenTypeSmall константой не
    // выражается; унифицируем только имя типа.
    companion object {
        private val className: String get() = "NamespaceDefinitionHandle"

        internal fun fromFullNameOffset(stringHeapOffset: Int): NamespaceDefinitionHandle =
            NamespaceDefinitionHandle(stringHeapOffset.toUInt())

        internal fun fromVirtualIndex(virtualIndex: UInt): NamespaceDefinitionHandle {
            // we arbitrarily disallow 0 virtual index to simplify nil check.
            assert(virtualIndex != 0u)

            // only a pathological assembly would hit this, but it must fit in 29 bits.
            check(HeapHandleType.isValidHeapOffset(virtualIndex)) { "too many sub-namespaces" }

            return NamespaceDefinitionHandle(TokenTypeIds.VIRTUAL_BIT or virtualIndex)
        }

        internal fun fromHandle(handle: Handle): NamespaceDefinitionHandle {
            val vt = handle.vType.toUInt()
            check((vt and HandleType.TYPE_MASK) == HandleType.NAMESPACE) {
                "handle has wrong kind for NamespaceDefinitionHandle"
            }

            return NamespaceDefinitionHandle(
                ((vt and HandleType.VIRTUAL_BIT) shl TokenTypeIds.ROW_ID_BIT_COUNT) or
                    handle.offset.toUInt(),
            )
        }
    }
}
