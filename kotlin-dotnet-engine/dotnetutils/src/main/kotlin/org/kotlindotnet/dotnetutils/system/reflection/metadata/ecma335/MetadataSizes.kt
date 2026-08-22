// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/MetadataSizes.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - internal visibility (analysis/03);
//  - EnC delta and standalone PDB branches are cut per ADR 0009
//    (IsCompressed is always true, no #Pdb/#JTD streams);
//  - debug-table size accounting is cut with the same scope;
//  - ImmutableArray<int> fields are IntArray.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.internal.BitArithmetic

/** Provides information on sizes of various metadata structures. */
internal class MetadataSizes(
    val rowCounts: IntArray,
    val externalRowCounts: IntArray,
    val heapSizes: IntArray,
    metadataVersionByteCount: Int,
) {
    companion object {
        private const val STREAM_ALIGNMENT = 4

        // Call the length of the string (including the terminator) m (we require m <= 255).
        const val MAX_METADATA_VERSION_BYTE_COUNT = 0xff - 1

        // Call it via [MetadataSizes] so that the masks stay next to their C# original.
        internal val SORTED_TYPE_SYSTEM_TABLES: ULong = run {
            listOf(
                TableIndex.INTERFACE_IMPL, TableIndex.CONSTANT, TableIndex.CUSTOM_ATTRIBUTE,
                TableIndex.FIELD_MARSHAL, TableIndex.DECL_SECURITY, TableIndex.CLASS_LAYOUT,
                TableIndex.FIELD_LAYOUT, TableIndex.METHOD_SEMANTICS, TableIndex.METHOD_IMPL,
                TableIndex.IMPL_MAP, TableIndex.FIELD_RVA, TableIndex.NESTED_CLASS,
                TableIndex.GENERIC_PARAM, TableIndex.GENERIC_PARAM_CONSTRAINT,
            ).fold(0uL) { acc, t -> acc or (1uL shl t.value) }
        }
    }

    /** Exact (unaligned) heap sizes; use [getAlignedHeapSize] for aligned ones. */
    val metadataVersionPaddedLength: Int =
        BitArithmetic.align(metadataVersionByteCount + 1, STREAM_ALIGNMENT)

    val isCompressed: Boolean = true // EnC deltas are not produced by this port

    val blobReferenceIsSmall: Boolean = heapSizes[HeapIndex.BLOB.ordinal] <= UShort.MAX_VALUE.toInt()
    val stringReferenceIsSmall: Boolean = heapSizes[HeapIndex.STRING.ordinal] <= UShort.MAX_VALUE.toInt()
    val guidReferenceIsSmall: Boolean = heapSizes[HeapIndex.GUID.ordinal] <= UShort.MAX_VALUE.toInt()
    internal val resolutionScopeCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(2, TableIndex.MODULE, TableIndex.MODULE_REF, TableIndex.ASSEMBLY_REF, TableIndex.TYPE_REF)
    internal val typeDefOrRefCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(2, TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.TYPE_SPEC)
    internal val typeDefReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.TYPE_DEF)
    internal val fieldDefReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.FIELD)
    internal val methodDefReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.METHOD_DEF)
    internal val parameterReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.PARAM)
    internal val eventDefReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.EVENT)
    internal val propertyDefReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.PROPERTY)
    internal val genericParamReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.GENERIC_PARAM)
    internal val moduleRefReferenceIsSmall: Boolean get() = isReferenceSmall(0, TableIndex.MODULE_REF)
    internal val memberRefParentCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(3, TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.MODULE_REF, TableIndex.METHOD_DEF, TableIndex.TYPE_SPEC)
    internal val methodDefOrRefCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(1, TableIndex.METHOD_DEF, TableIndex.MEMBER_REF)
    internal val hasConstantCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(2, TableIndex.FIELD, TableIndex.PARAM, TableIndex.PROPERTY)
    internal val hasCustomAttributeCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(5, TableIndex.METHOD_DEF, TableIndex.FIELD, TableIndex.TYPE_REF, TableIndex.TYPE_DEF, TableIndex.PARAM, TableIndex.INTERFACE_IMPL, TableIndex.MEMBER_REF, TableIndex.MODULE, TableIndex.DECL_SECURITY, TableIndex.PROPERTY, TableIndex.EVENT, TableIndex.STAND_ALONE_SIG, TableIndex.MODULE_REF, TableIndex.TYPE_SPEC, TableIndex.ASSEMBLY, TableIndex.ASSEMBLY_REF, TableIndex.FILE, TableIndex.EXPORTED_TYPE, TableIndex.MANIFEST_RESOURCE, TableIndex.GENERIC_PARAM, TableIndex.GENERIC_PARAM_CONSTRAINT, TableIndex.METHOD_SPEC)
    internal val hasFieldMarshalCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(1, TableIndex.FIELD, TableIndex.PARAM)
    internal val hasSemanticsCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(1, TableIndex.EVENT, TableIndex.PROPERTY)
    internal val implementationCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(2, TableIndex.FILE, TableIndex.ASSEMBLY_REF, TableIndex.EXPORTED_TYPE)
    internal val memberForwardedCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(1, TableIndex.FIELD, TableIndex.METHOD_DEF)
    internal val customAttributeTypeCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(3, TableIndex.METHOD_DEF, TableIndex.MEMBER_REF)
    internal val declSecurityCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(2, TableIndex.METHOD_DEF, TableIndex.TYPE_DEF)
    internal val typeOrMethodDefCodedIndexIsSmall: Boolean
        get() = isCodedIndexSmall(1, TableIndex.TYPE_DEF, TableIndex.METHOD_DEF)


    val presentTablesMask: ULong = computeNonEmptyTableMask(rowCounts)
    val externalTablesMask: ULong = computeNonEmptyTableMask(externalRowCounts)

    val metadataTableStreamSize: Int
    val metadataStreamStorageSize: Int

    init {
        assertNoOverlap()

        var size = calculateTableStreamHeaderSize()
        val small = 2
        val large = 4

        val blobReferenceSize = if (blobReferenceIsSmall) small else large
        val stringReferenceSize = if (stringReferenceIsSmall) small else large
        val guidReferenceSize = if (guidReferenceIsSmall) small else large
        val customAttributeTypeCodedIndexSize = if (isCodedIndexSmall(3, TableIndex.METHOD_DEF, TableIndex.MEMBER_REF)) small else large
        val declSecurityCodedIndexSize = if (isCodedIndexSmall(2, TableIndex.METHOD_DEF, TableIndex.TYPE_DEF)) small else large
        val eventDefReferenceSize = if (isReferenceSmall(0, TableIndex.EVENT)) small else large
        val fieldDefReferenceSize = if (isReferenceSmall(0, TableIndex.FIELD)) small else large
        val genericParamReferenceSize = if (isReferenceSmall(0, TableIndex.GENERIC_PARAM)) small else large
        val hasConstantCodedIndexSize = if (isCodedIndexSmall(2, TableIndex.FIELD, TableIndex.PARAM, TableIndex.PROPERTY)) small else large
        val hasCustomAttributeCodedIndexSize = if (
            isCodedIndexSmall(
                5,
                TableIndex.METHOD_DEF, TableIndex.FIELD, TableIndex.TYPE_REF, TableIndex.TYPE_DEF,
                TableIndex.PARAM, TableIndex.INTERFACE_IMPL, TableIndex.MEMBER_REF, TableIndex.MODULE,
                TableIndex.DECL_SECURITY, TableIndex.PROPERTY, TableIndex.EVENT, TableIndex.STAND_ALONE_SIG,
                TableIndex.MODULE_REF, TableIndex.TYPE_SPEC, TableIndex.ASSEMBLY, TableIndex.ASSEMBLY_REF,
                TableIndex.FILE, TableIndex.EXPORTED_TYPE, TableIndex.MANIFEST_RESOURCE,
                TableIndex.GENERIC_PARAM, TableIndex.GENERIC_PARAM_CONSTRAINT, TableIndex.METHOD_SPEC,
            )
        ) small else large
        val hasFieldMarshalCodedIndexSize = if (isCodedIndexSmall(1, TableIndex.FIELD, TableIndex.PARAM)) small else large
        val hasSemanticsCodedIndexSize = if (isCodedIndexSmall(1, TableIndex.EVENT, TableIndex.PROPERTY)) small else large
        val implementationCodedIndexSize = if (isCodedIndexSmall(2, TableIndex.FILE, TableIndex.ASSEMBLY_REF, TableIndex.EXPORTED_TYPE)) small else large
        val memberForwardedCodedIndexSize = if (isCodedIndexSmall(1, TableIndex.FIELD, TableIndex.METHOD_DEF)) small else large
        val memberRefParentCodedIndexSize = if (
            isCodedIndexSmall(3, TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.MODULE_REF, TableIndex.METHOD_DEF, TableIndex.TYPE_SPEC)
        ) small else large
        val methodDefReferenceSize = if (isReferenceSmall(0, TableIndex.METHOD_DEF)) small else large
        val methodDefOrRefCodedIndexSize = if (isCodedIndexSmall(1, TableIndex.METHOD_DEF, TableIndex.MEMBER_REF)) small else large
        val moduleRefReferenceSize = if (isReferenceSmall(0, TableIndex.MODULE_REF)) small else large
        val parameterReferenceSize = if (isReferenceSmall(0, TableIndex.PARAM)) small else large
        val propertyDefReferenceSize = if (isReferenceSmall(0, TableIndex.PROPERTY)) small else large
        val resolutionScopeCodedIndexSize = if (
            isCodedIndexSmall(2, TableIndex.MODULE, TableIndex.MODULE_REF, TableIndex.ASSEMBLY_REF, TableIndex.TYPE_REF)
        ) small else large
        val typeDefReferenceSize = if (isReferenceSmall(0, TableIndex.TYPE_DEF)) small else large
        val typeDefOrRefCodedIndexSize = if (isCodedIndexSmall(2, TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.TYPE_SPEC)) small else large
        val typeOrMethodDefCodedIndexSize = if (isCodedIndexSmall(1, TableIndex.TYPE_DEF, TableIndex.METHOD_DEF)) small else large

        size += getTableSize(TableIndex.MODULE, 2 + 3 * guidReferenceSize + stringReferenceSize)
        size += getTableSize(TableIndex.TYPE_REF, resolutionScopeCodedIndexSize + stringReferenceSize + stringReferenceSize)
        size += getTableSize(
            TableIndex.TYPE_DEF,
            4 + stringReferenceSize + stringReferenceSize + typeDefOrRefCodedIndexSize +
                fieldDefReferenceSize + methodDefReferenceSize,
        )
        checkRowCountZero(TableIndex.FIELD_PTR)
        size += getTableSize(TableIndex.FIELD, 2 + stringReferenceSize + blobReferenceSize)
        checkRowCountZero(TableIndex.METHOD_PTR)
        size += getTableSize(TableIndex.METHOD_DEF, 8 + stringReferenceSize + blobReferenceSize + parameterReferenceSize)
        checkRowCountZero(TableIndex.PARAM_PTR)
        size += getTableSize(TableIndex.PARAM, 4 + stringReferenceSize)
        size += getTableSize(TableIndex.INTERFACE_IMPL, typeDefReferenceSize + typeDefOrRefCodedIndexSize)
        size += getTableSize(TableIndex.MEMBER_REF, memberRefParentCodedIndexSize + stringReferenceSize + blobReferenceSize)
        size += getTableSize(TableIndex.CONSTANT, 2 + hasConstantCodedIndexSize + blobReferenceSize)
        size += getTableSize(TableIndex.CUSTOM_ATTRIBUTE, hasCustomAttributeCodedIndexSize + customAttributeTypeCodedIndexSize + blobReferenceSize)
        size += getTableSize(TableIndex.FIELD_MARSHAL, hasFieldMarshalCodedIndexSize + blobReferenceSize)
        size += getTableSize(TableIndex.DECL_SECURITY, 2 + declSecurityCodedIndexSize + blobReferenceSize)
        size += getTableSize(TableIndex.CLASS_LAYOUT, 6 + typeDefReferenceSize)
        size += getTableSize(TableIndex.FIELD_LAYOUT, 4 + fieldDefReferenceSize)
        size += getTableSize(TableIndex.STAND_ALONE_SIG, blobReferenceSize)
        size += getTableSize(TableIndex.EVENT_MAP, typeDefReferenceSize + eventDefReferenceSize)
        checkRowCountZero(TableIndex.EVENT_PTR)
        size += getTableSize(TableIndex.EVENT, 2 + stringReferenceSize + typeDefOrRefCodedIndexSize)
        size += getTableSize(TableIndex.PROPERTY_MAP, typeDefReferenceSize + propertyDefReferenceSize)
        checkRowCountZero(TableIndex.PROPERTY_PTR)
        size += getTableSize(TableIndex.PROPERTY, 2 + stringReferenceSize + blobReferenceSize)
        size += getTableSize(TableIndex.METHOD_SEMANTICS, 2 + methodDefReferenceSize + hasSemanticsCodedIndexSize)
        size += getTableSize(TableIndex.METHOD_IMPL, typeDefReferenceSize + methodDefOrRefCodedIndexSize + methodDefOrRefCodedIndexSize)
        size += getTableSize(TableIndex.MODULE_REF, stringReferenceSize)
        size += getTableSize(TableIndex.TYPE_SPEC, blobReferenceSize)
        size += getTableSize(TableIndex.IMPL_MAP, 2 + memberForwardedCodedIndexSize + stringReferenceSize + moduleRefReferenceSize)
        size += getTableSize(TableIndex.FIELD_RVA, 4 + fieldDefReferenceSize)
        size += getTableSize(TableIndex.ENC_LOG, 8)
        size += getTableSize(TableIndex.ENC_MAP, 4)
        size += getTableSize(TableIndex.ASSEMBLY, 16 + blobReferenceSize + stringReferenceSize + stringReferenceSize)
        size += getTableSize(
            TableIndex.ASSEMBLY_REF,
            12 + blobReferenceSize + stringReferenceSize + stringReferenceSize + blobReferenceSize,
        )
        size += getTableSize(TableIndex.FILE, 4 + stringReferenceSize + blobReferenceSize)
        size += getTableSize(TableIndex.EXPORTED_TYPE, 8 + stringReferenceSize + stringReferenceSize + implementationCodedIndexSize)
        size += getTableSize(TableIndex.MANIFEST_RESOURCE, 8 + stringReferenceSize + implementationCodedIndexSize)
        size += getTableSize(TableIndex.NESTED_CLASS, typeDefReferenceSize + typeDefReferenceSize)
        size += getTableSize(TableIndex.GENERIC_PARAM, 4 + typeOrMethodDefCodedIndexSize + stringReferenceSize)
        size += getTableSize(TableIndex.METHOD_SPEC, methodDefOrRefCodedIndexSize + blobReferenceSize)
        size += getTableSize(TableIndex.GENERIC_PARAM_CONSTRAINT, genericParamReferenceSize + typeDefOrRefCodedIndexSize)

        // +1 for terminating 0 byte
        size = BitArithmetic.align(size + 1, STREAM_ALIGNMENT)
        metadataTableStreamSize = size

        size += getAlignedHeapSize(HeapIndex.STRING)
        size += getAlignedHeapSize(HeapIndex.USER_STRING)
        size += getAlignedHeapSize(HeapIndex.GUID)
        size += getAlignedHeapSize(HeapIndex.BLOB)

        metadataStreamStorageSize = size
    }

    fun isPresent(table: TableIndex): Boolean = (presentTablesMask and (1uL shl table.value)) != 0uL

    /**
     * Metadata header size: storage signature, storage header, stream headers.
     */
    val metadataHeaderSize: Int
        get() {
            val regularStreamHeaderSizes = 76
            return 4 + // signature
                2 + // major version
                2 + // minor version
                4 + // reserved
                4 + // padded metadata version length
                metadataVersionPaddedLength +
                2 + // storage header: reserved
                2 + // stream count
                regularStreamHeaderSizes
        }

    /** Total size of metadata (header and all streams). */
    val metadataSize: Int
        get() = metadataHeaderSize + metadataStreamStorageSize

    fun getAlignedHeapSize(index: HeapIndex): Int {
        val i = index.ordinal
        require(i in heapSizes.indices) { "index out of range" }
        return BitArithmetic.align(heapSizes[i], STREAM_ALIGNMENT)
    }

    fun calculateTableStreamHeaderSize(): Int {
        var result = 4 + // Reserved
            2 + // Version (major, minor)
            1 + // Heap index sizes
            1 + // Bit width of RowId
            8 + // Valid table mask
            8   // Sorted table mask

        for (i in rowCounts.indices) {
            if ((presentTablesMask shr i) and 1uL != 0uL) {
                result += 4
            }
        }

        return result
    }

    private fun assertNoOverlap() {
        // a table can either be present or external, it can't be both:
        assert((presentTablesMask and externalTablesMask) == 0uL)
    }

    private fun checkRowCountZero(table: TableIndex) {
        assert(rowCounts[table.value] == 0) { "${table.name} rows are not supported by this layout" }
    }

    private fun computeNonEmptyTableMask(counts: IntArray): ULong {
        var mask = 0uL
        for (i in counts.indices) {
            if (counts[i] > 0) {
                mask = mask or (1uL shl i)
            }
        }
        return mask
    }

    private fun getTableSize(index: TableIndex, rowSize: Int): Int = rowCounts[index.value] * rowSize

    private fun isReferenceSmall(tagBitSize: Int, vararg tables: TableIndex): Boolean {
        val smallBitCount = 16
        return isCompressed && referenceFits(smallBitCount - tagBitSize, tables)
    }

    private fun isCodedIndexSmall(tagBitSize: Int, vararg tables: TableIndex): Boolean =
        isReferenceSmall(tagBitSize, *tables)

    private fun referenceFits(bitCount: Int, tables: Array<out TableIndex>): Boolean {
        val maxIndex = (1 shl bitCount) - 1
        for (table in tables) {
            // table can be either local or external, but not both:
            assert(rowCounts[table.value] == 0 || externalRowCounts[table.value] == 0)

            if (rowCounts[table.value] + externalRowCounts[table.value] > maxIndex) {
                return false
            }
        }
        return true
    }
}
