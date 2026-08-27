// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (TypeSystem/Handles.TypeSystem.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Note: WinRT-specific virtual string usage belongs to the projection
// machinery cut by ADR 0009, but the virtual-bit layout itself is kept
// intact (the writer uses virtual indexes for its own dedup purposes).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HeapHandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.StringKind
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.StringHandleType

/** #String heap handle. */
@JvmInline
value class StringHandle private constructor(
    // bits:
    //     31: IsVirtual
    // 29..31: type (non-virtual: STRING, DOT_TERMINATED; virtual: VIRTUAL, WIN_RT_PREFIXED)
    //  0..28: Heap offset or Virtual index
    val rawValue: UInt,
) {
    fun toHandle(): Handle =
        Handle(
            vType = (
                ((rawValue and HeapHandleType.VIRTUAL_BIT) shr 24) or
                    HandleType.STRING or
                    ((rawValue and StringHandleType.NON_VIRTUAL_TYPE_MASK) shr HeapHandleType.OFFSET_BIT_COUNT)
                ).toUByte(),
            value = (rawValue and HeapHandleType.OFFSET_MASK).toInt(),
        )

    val isVirtual: Boolean
        get() = (rawValue and HeapHandleType.VIRTUAL_BIT) != 0u

    /** Virtual strings are never nil, so the virtual bit is included. */
    val isNil: Boolean
        get() = (rawValue and (HeapHandleType.VIRTUAL_BIT or HeapHandleType.OFFSET_MASK)) == 0u

    fun getHeapOffset(): Int =
        // WinRT prefixed strings are virtual, the value is a heap offset
        (rawValue and HeapHandleType.OFFSET_MASK).toInt()

    fun getVirtualIndex(): VirtualIndex {
        assert(isVirtual && stringKind != StringKind.WIN_RT_PREFIXED)
        return VirtualIndex.fromRaw((rawValue and HeapHandleType.OFFSET_MASK).toInt())!!
    }

    fun getWriterVirtualIndex(): Int =
        (rawValue and HeapHandleType.OFFSET_MASK).toInt()

    internal val stringKind: StringKind
        get() = StringKind.fromRaw((rawValue shr HeapHandleType.OFFSET_BIT_COUNT).toUByte())!!

    fun withWinRTPrefix(): StringHandle {
        assert(stringKind == StringKind.PLAIN)
        return StringHandle(StringHandleType.WIN_RT_PREFIXED_STRING or rawValue)
    }

    fun withDotTermination(): StringHandle {
        assert(stringKind == StringKind.PLAIN)
        return StringHandle(StringHandleType.DOT_TERMINATED_STRING or rawValue)
    }

    fun suffixRaw(prefixByteLength: Int): StringHandle {
        assert(stringKind == StringKind.PLAIN)
        return StringHandle(StringHandleType.STRING or (rawValue + prefixByteLength.toUInt()))
    }

    override fun toString(): String = "$className(0x%08X)".format(rawValue.toInt())

    companion object {
        // composite vType (virtual-bit layout): it cannot be expressed as a
        // tokenTypeSmall constant, so only the type name is unified.
        private val className: String get() = "StringHandle"

        internal fun fromRaw(rawValue: UInt) = StringHandle(rawValue)

        internal fun fromOffset(heapOffset: Int): StringHandle =
            StringHandle(StringHandleType.STRING or heapOffset.toUInt())

        internal fun fromVirtualIndex(virtualIndex: VirtualIndex): StringHandle =
            StringHandle(StringHandleType.VIRTUAL_STRING or virtualIndex.ordinal.toUInt())

        internal fun fromWriterVirtualIndex(virtualIndex: Int): StringHandle =
            StringHandle(StringHandleType.VIRTUAL_STRING or virtualIndex.toUInt())

        internal fun fromHandle(handle: Handle): StringHandle {
            val vt = handle.vType.toUInt()
            check(
                (vt and (HandleType.VIRTUAL_BIT or HandleType.NON_VIRTUAL_STRING_TYPE_MASK).inv())
                    == HandleType.STRING,
            ) { "handle has wrong kind for StringHandle" }

            // V111 10TT -> VTTx xxxx xxxx xxxx  xxxx xxxx xxxx xxxx
            return StringHandle(
                ((vt and HandleType.VIRTUAL_BIT) shl 24) or
                    ((vt and HandleType.NON_VIRTUAL_STRING_TYPE_MASK) shl HeapHandleType.OFFSET_BIT_COUNT) or
                    handle.offset.toUInt(),
            )
        }
    }

    /** System string names addressable by virtual index (WinRT projection names). */
    enum class VirtualIndex {
        SYSTEM_RUNTIME_WINDOWS_RUNTIME,
        SYSTEM_RUNTIME,
        SYSTEM_OBJECT_MODEL,
        SYSTEM_RUNTIME_WINDOWS_RUNTIME_UI_XAML,
        SYSTEM_RUNTIME_INTEROP_SERVICES_WINDOWS_RUNTIME,
        SYSTEM_NUMERICS_VECTORS,
        DISPOSE,
        ATTRIBUTE_TARGETS,
        ATTRIBUTE_USAGE_ATTRIBUTE,
        COLOR,
        CORNER_RADIUS,
        DATE_TIME_OFFSET,
        DURATION,
        DURATION_TYPE,
        EVENT_HANDLER1,
        EVENT_REGISTRATION_TOKEN,
        EXCEPTION,
        GENERATOR_POSITION,
        GRID_LENGTH,
        GRID_UNIT_TYPE,
        ICOMMAND,
        IDICTIONARY2,
        IDISPOSABLE,
        IENUMERABLE,
        IENUMERABLE1,
        ILIST,
        ILIST1,
        INOTIFY_COLLECTION_CHANGED,
        INOTIFY_PROPERTY_CHANGED,
        IREAD_ONLY_DICTIONARY2,
        IREAD_ONLY_LIST1,
        KEY_TIME,
        KEY_VALUE_PAIR2,
        MATRIX,
        MATRIX3_D,
        MATRIX3X2,
        MATRIX4X4,
        NOTIFY_COLLECTION_CHANGED_ACTION,
        NOTIFY_COLLECTION_CHANGED_EVENT_ARGS,
        NOTIFY_COLLECTION_CHANGED_EVENT_HANDLER,
        NULLABLE1,
        PLANE,
        POINT,
        PROPERTY_CHANGED_EVENT_ARGS,
        PROPERTY_CHANGED_EVENT_HANDLER,
        QUATERNION,
        RECT,
        REPEAT_BEHAVIOR,
        REPEAT_BEHAVIOR_TYPE,
        SIZE,
        SYSTEM,
        SYSTEM_COLLECTIONS,
        SYSTEM_COLLECTIONS_GENERIC,
        SYSTEM_COLLECTIONS_SPECIALIZED,
        SYSTEM_COMPONENT_MODEL,
        SYSTEM_NUMERICS,
        SYSTEM_WINDOWS_INPUT,
        THICKNESS,
        TIME_SPAN,
        TYPE,
        URI,
        VECTOR2,
        VECTOR3,
        VECTOR4,
        WINDOWS_FOUNDATION,
        WINDOWS_UI,
        WINDOWS_UI_XAML,
        WINDOWS_UI_XAML_CONTROLS_PRIMITIVES,
        WINDOWS_UI_XAML_MEDIA,
        WINDOWS_UI_XAML_MEDIA_ANIMATION,
        WINDOWS_UI_XAML_MEDIA_MEDIA3_D,;

        companion object {
            private val byOrdinal = entries

            internal fun fromRaw(raw: Int): VirtualIndex? = byOrdinal.getOrNull(raw)
        }
    }
}
