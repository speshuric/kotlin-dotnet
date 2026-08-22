// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Internal/MetadataFlags.cs — HandleType, TokenTypeIds).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Note: StringHandleType / HeapHandleType / StringKind / HeapSizes / TableCounts
// from the original file are ported with their consumers in later iterations.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

/**
 * Constants are all in the byte range and apply to the interpretation of
 * [org.kotlindotnet.dotnetutils.system.reflection.metadata.Handle.vType].
 */
internal object HandleType {
    val MODULE: UInt = TableIndex.MODULE.value.toUInt()
    val TYPE_REF: UInt = TableIndex.TYPE_REF.value.toUInt()
    val TYPE_DEF: UInt = TableIndex.TYPE_DEF.value.toUInt()
    val FIELD_DEF: UInt = TableIndex.FIELD.value.toUInt()
    val METHOD_DEF: UInt = TableIndex.METHOD_DEF.value.toUInt()
    val PARAM_DEF: UInt = TableIndex.PARAM.value.toUInt()
    val INTERFACE_IMPL: UInt = TableIndex.INTERFACE_IMPL.value.toUInt()
    val MEMBER_REF: UInt = TableIndex.MEMBER_REF.value.toUInt()
    val CONSTANT: UInt = TableIndex.CONSTANT.value.toUInt()
    val CUSTOM_ATTRIBUTE: UInt = TableIndex.CUSTOM_ATTRIBUTE.value.toUInt()
    val DECL_SECURITY: UInt = TableIndex.DECL_SECURITY.value.toUInt()
    val SIGNATURE: UInt = TableIndex.STAND_ALONE_SIG.value.toUInt()
    val EVENT_MAP: UInt = TableIndex.EVENT_MAP.value.toUInt()
    val EVENT: UInt = TableIndex.EVENT.value.toUInt()
    val PROPERTY_MAP: UInt = TableIndex.PROPERTY_MAP.value.toUInt()
    val PROPERTY: UInt = TableIndex.PROPERTY.value.toUInt()
    val METHOD_SEMANTICS: UInt = TableIndex.METHOD_SEMANTICS.value.toUInt()
    val METHOD_IMPL: UInt = TableIndex.METHOD_IMPL.value.toUInt()
    val MODULE_REF: UInt = TableIndex.MODULE_REF.value.toUInt()
    val TYPE_SPEC: UInt = TableIndex.TYPE_SPEC.value.toUInt()
    val ASSEMBLY: UInt = TableIndex.ASSEMBLY.value.toUInt()
    val ASSEMBLY_REF: UInt = TableIndex.ASSEMBLY_REF.value.toUInt()
    val FILE: UInt = TableIndex.FILE.value.toUInt()
    val EXPORTED_TYPE: UInt = TableIndex.EXPORTED_TYPE.value.toUInt()
    val MANIFEST_RESOURCE: UInt = TableIndex.MANIFEST_RESOURCE.value.toUInt()
    val NESTED_CLASS: UInt = TableIndex.NESTED_CLASS.value.toUInt()
    val GENERIC_PARAM: UInt = TableIndex.GENERIC_PARAM.value.toUInt()
    val METHOD_SPEC: UInt = TableIndex.METHOD_SPEC.value.toUInt()
    val GENERIC_PARAM_CONSTRAINT: UInt = TableIndex.GENERIC_PARAM_CONSTRAINT.value.toUInt()

    // debug tables:
    val DOCUMENT: UInt = TableIndex.DOCUMENT.value.toUInt()
    val METHOD_DEBUG_INFORMATION: UInt = TableIndex.METHOD_DEBUG_INFORMATION.value.toUInt()
    val LOCAL_SCOPE: UInt = TableIndex.LOCAL_SCOPE.value.toUInt()
    val LOCAL_VARIABLE: UInt = TableIndex.LOCAL_VARIABLE.value.toUInt()
    val LOCAL_CONSTANT: UInt = TableIndex.LOCAL_CONSTANT.value.toUInt()
    val IMPORT_SCOPE: UInt = TableIndex.IMPORT_SCOPE.value.toUInt()
    val ASYNC_METHOD: UInt = TableIndex.STATE_MACHINE_METHOD.value.toUInt()
    val CUSTOM_DEBUG_INFORMATION: UInt = TableIndex.CUSTOM_DEBUG_INFORMATION.value.toUInt()

    val USER_STRING: UInt = 0x70u // #UserString heap

    // The following values never appear in a token stored in metadata,
    // they are just helper values to identify the type of a handle.
    val BLOB: UInt = 0x71u // #Blob heap
    val GUID: UInt = 0x72u // #Guid heap

    // #String heap and its modifications (see StringHandleType, ported later).
    val STRING: UInt = 0x78u

    // Namespace handles also have offsets into the #String heap (when non-virtual).
    val NAMESPACE: UInt = 0x7Cu

    val HEAP_MASK: UInt = 0x70u
    val TYPE_MASK: UInt = 0x7Fu

    /**
     * Use the highest bit to mark tokens that are virtual (synthesized).
     */
    val VIRTUAL_BIT: UInt = 0x80u

    /**
     * In the case of string handles, the two lower bits that (in addition to the
     * virtual bit not included in this mask) encode how to obtain the string value.
     */
    val NON_VIRTUAL_STRING_TYPE_MASK: UInt = 0x03u
}

internal object TokenTypeIds {
    const val ROW_ID_BIT_COUNT: Int = 24
    val RID_MASK: UInt = (1u shl ROW_ID_BIT_COUNT) - 1u
    const val VIRTUAL_BIT: UInt = 0x80000000u

    val MODULE: UInt = HandleType.MODULE shl ROW_ID_BIT_COUNT
    val TYPE_REF: UInt = HandleType.TYPE_REF shl ROW_ID_BIT_COUNT
    val TYPE_DEF: UInt = HandleType.TYPE_DEF shl ROW_ID_BIT_COUNT
    val FIELD_DEF: UInt = HandleType.FIELD_DEF shl ROW_ID_BIT_COUNT
    val METHOD_DEF: UInt = HandleType.METHOD_DEF shl ROW_ID_BIT_COUNT
    val PARAM_DEF: UInt = HandleType.PARAM_DEF shl ROW_ID_BIT_COUNT
    val INTERFACE_IMPL: UInt = HandleType.INTERFACE_IMPL shl ROW_ID_BIT_COUNT
    val MEMBER_REF: UInt = HandleType.MEMBER_REF shl ROW_ID_BIT_COUNT
    val CONSTANT: UInt = HandleType.CONSTANT shl ROW_ID_BIT_COUNT
    val CUSTOM_ATTRIBUTE: UInt = HandleType.CUSTOM_ATTRIBUTE shl ROW_ID_BIT_COUNT
    val DECL_SECURITY: UInt = HandleType.DECL_SECURITY shl ROW_ID_BIT_COUNT
    val SIGNATURE: UInt = HandleType.SIGNATURE shl ROW_ID_BIT_COUNT
    val EVENT_MAP: UInt = HandleType.EVENT_MAP shl ROW_ID_BIT_COUNT
    val EVENT: UInt = HandleType.EVENT shl ROW_ID_BIT_COUNT
    val PROPERTY_MAP: UInt = HandleType.PROPERTY_MAP shl ROW_ID_BIT_COUNT
    val PROPERTY: UInt = HandleType.PROPERTY shl ROW_ID_BIT_COUNT
    val METHOD_SEMANTICS: UInt = HandleType.METHOD_SEMANTICS shl ROW_ID_BIT_COUNT
    val METHOD_IMPL: UInt = HandleType.METHOD_IMPL shl ROW_ID_BIT_COUNT
    val MODULE_REF: UInt = HandleType.MODULE_REF shl ROW_ID_BIT_COUNT
    val TYPE_SPEC: UInt = HandleType.TYPE_SPEC shl ROW_ID_BIT_COUNT
    val ASSEMBLY: UInt = HandleType.ASSEMBLY shl ROW_ID_BIT_COUNT
    val ASSEMBLY_REF: UInt = HandleType.ASSEMBLY_REF shl ROW_ID_BIT_COUNT
    val FILE: UInt = HandleType.FILE shl ROW_ID_BIT_COUNT
    val EXPORTED_TYPE: UInt = HandleType.EXPORTED_TYPE shl ROW_ID_BIT_COUNT
    val MANIFEST_RESOURCE: UInt = HandleType.MANIFEST_RESOURCE shl ROW_ID_BIT_COUNT
    val NESTED_CLASS: UInt = HandleType.NESTED_CLASS shl ROW_ID_BIT_COUNT
    val GENERIC_PARAM: UInt = HandleType.GENERIC_PARAM shl ROW_ID_BIT_COUNT
    val METHOD_SPEC: UInt = HandleType.METHOD_SPEC shl ROW_ID_BIT_COUNT
    val GENERIC_PARAM_CONSTRAINT: UInt = HandleType.GENERIC_PARAM_CONSTRAINT shl ROW_ID_BIT_COUNT

    // debug tables:
    val DOCUMENT: UInt = HandleType.DOCUMENT shl ROW_ID_BIT_COUNT
    val METHOD_DEBUG_INFORMATION: UInt = HandleType.METHOD_DEBUG_INFORMATION shl ROW_ID_BIT_COUNT
    val LOCAL_SCOPE: UInt = HandleType.LOCAL_SCOPE shl ROW_ID_BIT_COUNT
    val LOCAL_VARIABLE: UInt = HandleType.LOCAL_VARIABLE shl ROW_ID_BIT_COUNT
    val LOCAL_CONSTANT: UInt = HandleType.LOCAL_CONSTANT shl ROW_ID_BIT_COUNT
    val IMPORT_SCOPE: UInt = HandleType.IMPORT_SCOPE shl ROW_ID_BIT_COUNT
    val ASYNC_METHOD: UInt = HandleType.ASYNC_METHOD shl ROW_ID_BIT_COUNT
    val CUSTOM_DEBUG_INFORMATION: UInt = HandleType.CUSTOM_DEBUG_INFORMATION shl ROW_ID_BIT_COUNT

    val USER_STRING: UInt = HandleType.USER_STRING shl ROW_ID_BIT_COUNT

    val TYPE_MASK: UInt = HandleType.TYPE_MASK shl ROW_ID_BIT_COUNT

    /**
     * Returns true if the token value can escape the metadata reader.
     * We don't allow virtual tokens and heap tokens other than UserString to escape
     * since the token type ids are internal to the reader and not specified by ECMA spec.
     *
     * Spec (Partition III, 1.9 Metadata tokens).
     */
    fun isEntityOrUserStringToken(vToken: UInt): Boolean =
        (vToken and TYPE_MASK) <= USER_STRING

    fun isEntityToken(vToken: UInt): Boolean =
        (vToken and TYPE_MASK) < USER_STRING

    fun isValidRowId(rowId: UInt): Boolean =
        (rowId and RID_MASK.inv()) == 0u

    fun isValidRowId(rowId: Int): Boolean =
        (rowId and RID_MASK.toInt().inv()) == 0
}

internal object HeapHandleType {
    // Heap offset values are limited to 29 bits (max compressed integer)
    val OFFSET_BIT_COUNT: Int = 29
    val OFFSET_MASK: UInt = (1u shl OFFSET_BIT_COUNT) - 1u
    val VIRTUAL_BIT: UInt = 0x80000000u

    fun isValidHeapOffset(offset: UInt): Boolean = (offset and OFFSET_MASK.inv()) == 0u
}

internal object StringHandleType {
    // The 3 high bits above the offset that specify the full string type (including virtual bit)
    val TYPE_MASK: UInt = HeapHandleType.OFFSET_MASK.inv()

    // The string type bits excluding the virtual bit.
    val NON_VIRTUAL_TYPE_MASK: UInt = TYPE_MASK and HeapHandleType.VIRTUAL_BIT.inv()

    // NUL-terminated UTF8 string on a #String heap.
    val STRING: UInt = 0u shl HeapHandleType.OFFSET_BIT_COUNT

    // String on #String heap whose terminator is NUL and '.', whichever comes first.
    val DOT_TERMINATED_STRING: UInt = 1u shl HeapHandleType.OFFSET_BIT_COUNT

    // Reserved values that can be used for future strings:
    val RESERVED_STRING_1: UInt = 2u shl HeapHandleType.OFFSET_BIT_COUNT
    val RESERVED_STRING_2: UInt = 3u shl HeapHandleType.OFFSET_BIT_COUNT

    // Virtual string identified by a virtual index
    val VIRTUAL_STRING: UInt = HeapHandleType.VIRTUAL_BIT or (0u shl HeapHandleType.OFFSET_BIT_COUNT)

    // Virtual string whose value is a "<WinRT>" prefixed string found at the specified heap offset.
    val WIN_RT_PREFIXED_STRING: UInt = HeapHandleType.VIRTUAL_BIT or (1u shl HeapHandleType.OFFSET_BIT_COUNT)

    // Reserved virtual strings that can be used in future:
    val RESERVED_VIRTUAL_STRING_1: UInt = HeapHandleType.VIRTUAL_BIT or (2u shl HeapHandleType.OFFSET_BIT_COUNT)
    val RESERVED_VIRTUAL_STRING_2: UInt = HeapHandleType.VIRTUAL_BIT or (3u shl HeapHandleType.OFFSET_BIT_COUNT)
}

internal enum class StringKind(val value: UByte) {
    PLAIN(0u),
    DOT_TERMINATED(1u),
    VIRTUAL(4u),
    WIN_RT_PREFIXED(5u);

    companion object {
        private val byValue: Map<UByte, StringKind> = entries.associateBy { it.value }

        internal fun fromRaw(raw: UByte): StringKind? = byValue[raw]
    }
}

/**
 * Port of the `TableMask` flags enum: per-table bits and the aggregates
 * used by row-count sizing and token mapping.
 *
 * Note: AssemblyProcessor/AssemblyOS/AssemblyRefProcessor/AssemblyRefOS
 * have no mask bits in the original either.
 */
internal object TableMask {
    private fun bit(index: TableIndex): ULong = 1uL shl index.value

    val PTR_TABLES: ULong =
        bit(TableIndex.FIELD_PTR) or bit(TableIndex.METHOD_PTR) or bit(TableIndex.PARAM_PTR) or
            bit(TableIndex.EVENT_PTR) or bit(TableIndex.PROPERTY_PTR)

    val ENC_TABLES: ULong = bit(TableIndex.ENC_LOG) or bit(TableIndex.ENC_MAP)

    val TYPE_SYSTEM_TABLES: ULong =
        PTR_TABLES or ENC_TABLES or
            bit(TableIndex.MODULE) or bit(TableIndex.TYPE_REF) or bit(TableIndex.TYPE_DEF) or
            bit(TableIndex.FIELD) or bit(TableIndex.METHOD_DEF) or bit(TableIndex.PARAM) or
            bit(TableIndex.INTERFACE_IMPL) or bit(TableIndex.MEMBER_REF) or bit(TableIndex.CONSTANT) or
            bit(TableIndex.CUSTOM_ATTRIBUTE) or bit(TableIndex.FIELD_MARSHAL) or bit(TableIndex.DECL_SECURITY) or
            bit(TableIndex.CLASS_LAYOUT) or bit(TableIndex.FIELD_LAYOUT) or bit(TableIndex.STAND_ALONE_SIG) or
            bit(TableIndex.EVENT_MAP) or bit(TableIndex.EVENT) or bit(TableIndex.PROPERTY_MAP) or
            bit(TableIndex.PROPERTY) or bit(TableIndex.METHOD_SEMANTICS) or bit(TableIndex.METHOD_IMPL) or
            bit(TableIndex.MODULE_REF) or bit(TableIndex.TYPE_SPEC) or bit(TableIndex.IMPL_MAP) or
            bit(TableIndex.FIELD_RVA) or bit(TableIndex.ASSEMBLY) or bit(TableIndex.ASSEMBLY_REF) or
            bit(TableIndex.FILE) or bit(TableIndex.EXPORTED_TYPE) or bit(TableIndex.MANIFEST_RESOURCE) or
            bit(TableIndex.NESTED_CLASS) or bit(TableIndex.GENERIC_PARAM) or bit(TableIndex.METHOD_SPEC) or
            bit(TableIndex.GENERIC_PARAM_CONSTRAINT)

    val DEBUG_TABLES: ULong =
        bit(TableIndex.DOCUMENT) or bit(TableIndex.METHOD_DEBUG_INFORMATION) or
            bit(TableIndex.LOCAL_SCOPE) or bit(TableIndex.LOCAL_VARIABLE) or
            bit(TableIndex.LOCAL_CONSTANT) or bit(TableIndex.IMPORT_SCOPE) or
            bit(TableIndex.STATE_MACHINE_METHOD) or bit(TableIndex.CUSTOM_DEBUG_INFORMATION)

    val ALL_TABLES: ULong = TYPE_SYSTEM_TABLES or DEBUG_TABLES
}
