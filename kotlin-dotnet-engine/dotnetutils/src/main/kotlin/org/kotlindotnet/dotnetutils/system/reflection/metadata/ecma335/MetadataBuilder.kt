// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata
//   (Ecma335/MetadataBuilder.cs, MetadataBuilder.Heaps.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - C# partial classes are folded into one class; the table-building and
//    serialization halves live as extension functions in
//    MetadataBuilderTables.kt / MetadataBuilderSerializer.kt;
//  - PooledBlobBuilder is replaced by a plain HeapBlobBuilder subclass
//    (no pooling, per ADR 0009);
//  - GetOrAddDocumentName is cut (Portable PDB scope);
//  - EnC delta heap offsets are kept as constructor parameters but are
//    expected to be zero (EnC not produced by this port).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.internal.BlobUtilities
import org.kotlindotnet.dotnetutils.system.reflection.internal.BitArithmetic
import kotlin.uuid.Uuid
import org.kotlindotnet.dotnetutils.system.reflection.metadata.Blob
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriter
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ReservedBlob
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle

class MetadataBuilder(
    userStringHeapStartOffset: Int = 0,
    stringHeapStartOffset: Int = 0,
    blobHeapStartOffset: Int = 0,
    guidHeapStartOffset: Int = 0,
) {
    // --- table state (populated via extensions in MetadataBuilderTables.kt) ---
    internal var moduleRow: ModuleRow? = null
    internal var assemblyRow: AssemblyRow? = null

    internal val assemblyRefTable = ArrayList<AssemblyRefTableRow>()
    internal val classLayoutTable = ArrayList<ClassLayoutRow>()
    internal val constantTable = ArrayList<ConstantRow>()
    internal var constantTableLastParent = 0
    internal var constantTableNeedsSorting = false

    internal val customAttributeTable = ArrayList<CustomAttributeRow>()
    internal var customAttributeTableLastParent = 0
    internal var customAttributeTableNeedsSorting = false

    internal val declSecurityTable = ArrayList<DeclSecurityRow>()
    internal var declSecurityTableLastParent = 0
    internal var declSecurityTableNeedsSorting = false

    internal val eventTable = ArrayList<EventRow>()
    internal val eventMapTable = ArrayList<EventMapRow>()
    internal val exportedTypeTable = ArrayList<ExportedTypeRow>()
    internal val fieldLayoutTable = ArrayList<FieldLayoutRow>()

    internal val fieldMarshalTable = ArrayList<FieldMarshalRow>()
    internal var fieldMarshalTableLastParent = 0
    internal var fieldMarshalTableNeedsSorting = false

    internal val fieldRvaTable = ArrayList<FieldRvaRow>()
    internal val fieldTable = ArrayList<FieldDefRow>()
    internal val fileTable = ArrayList<FileTableRow>()
    internal val genericParamConstraintTable = ArrayList<GenericParamConstraintRow>()
    internal val genericParamTable = ArrayList<GenericParamRow>()
    internal val implMapTable = ArrayList<ImplMapRow>()
    internal val interfaceImplTable = ArrayList<InterfaceImplRow>()
    internal val manifestResourceTable = ArrayList<ManifestResourceRow>()
    internal val memberRefTable = ArrayList<MemberRefRow>()
    internal val methodImplTable = ArrayList<MethodImplRow>()

    internal val methodSemanticsTable = ArrayList<MethodSemanticsRow>()
    internal var methodSemanticsTableLastAssociation = 0
    internal var methodSemanticsTableNeedsSorting = false

    internal val methodSpecTable = ArrayList<MethodSpecRow>()
    internal val methodDefTable = ArrayList<MethodRow>()
    internal val moduleRefTable = ArrayList<ModuleRefRow>()
    internal val nestedClassTable = ArrayList<NestedClassRow>()
    internal val paramTable = ArrayList<ParamRow>()
    internal val propertyMapTable = ArrayList<PropertyMapRow>()
    internal val propertyTable = ArrayList<PropertyRow>()
    internal val typeDefTable = ArrayList<TypeDefRow>()
    internal val typeRefTable = ArrayList<TypeRefRow>()
    internal val typeSpecTable = ArrayList<TypeSpecRow>()
    internal val standAloneSigTable = ArrayList<StandaloneSigRow>()

    // --- heap state ---

    private class HeapBlobBuilder(capacity: Int) : BlobBuilder(capacity) {
        private var capacityExpansion = 0

        override fun allocateChunk(minimalSize: Int): BlobBuilder =
            HeapBlobBuilder(maxOf(maxOf(minimalSize, chunkCapacity), capacityExpansion))

        fun setCapacity(capacity: Int) {
            capacityExpansion = maxOf(0, capacity - count - freeBytes)
        }
    }

    // #US heap
    private val userStrings = HashMap<String, UserStringHandle>(256)
    private val userStringBuilder = HeapBlobBuilder(4 * 1024)
    private val userStringHeapStartOffset: Int

    // #String heap
    private val strings = HashMap<String, StringHandle>(256)
    private val stringHeapStartOffset: Int
    private var stringHeapCapacity = 4 * 1024

    // #Blob heap
    private val blobs = BlobDictionary(1024)
    private val blobHeapStartOffset: Int
    private var blobHeapSize: Int

    // #GUID heap
    private val guids = HashMap<Uuid, GuidHandle>()
    private val guidBuilder = HeapBlobBuilder(16) // full metadata has just a single guid

    init {
        require(userStringHeapStartOffset < USER_STRING_HEAP_SIZE_LIMIT - 1) {
            "user string heap size limit exceeded"
        }
        require(userStringHeapStartOffset >= 0) { "offset out of range" }
        require(stringHeapStartOffset >= 0) { "offset out of range" }
        require(blobHeapStartOffset >= 0) { "offset out of range" }
        require(guidHeapStartOffset >= 0) { "offset out of range" }
        require(guidHeapStartOffset % BlobUtilities.SIZE_OF_GUID == 0) {
            "value must be a multiple of ${BlobUtilities.SIZE_OF_GUID}"
        }

        // Add zero-th entry to all heaps so that nil handles have zero value.
        userStringBuilder.writeByte(0.toByte())

        blobs.getOrAdd(ByteArray(0), ByteArray(0), BlobHandle.fromOffset(0))
        blobHeapSize = 1

        this.userStringHeapStartOffset = userStringHeapStartOffset
        this.stringHeapStartOffset = stringHeapStartOffset
        this.blobHeapStartOffset = blobHeapStartOffset

        guidBuilder.writeBytes(0.toByte(), guidHeapStartOffset)
    }

    /** Sets the capacity of the specified heap to reduce allocations. */
    fun setCapacity(heap: HeapIndex, byteCount: Int) {
        require(byteCount >= 0) { "byteCount out of range" }

        when (heap) {
            HeapIndex.BLOB -> {
                // Not useful to set capacity: by the time the blob heap is
                // serialized we know the exact size we need.
            }
            HeapIndex.GUID -> guidBuilder.setCapacity(byteCount)
            HeapIndex.STRING -> stringHeapCapacity = byteCount
            HeapIndex.USER_STRING -> userStringBuilder.setCapacity(byteCount)
        }
    }

    // --- serialization helpers (internal for testing parity with the original) ---

    internal fun serializeHandle(map: IntArray, handle: StringHandle): Int = map[handle.getWriterVirtualIndex()]

    internal fun serializeHandle(handle: BlobHandle): Int = handle.getHeapOffset()

    internal fun serializeHandle(handle: GuidHandle): Int = handle.index

    internal fun serializeHandle(handle: UserStringHandle): Int = handle.getHeapOffset()

    /**
     * Adds the specified blob to the #Blob heap if it's not there already.
     */
    fun getOrAddBlob(value: BlobBuilder): BlobHandle = getOrAddBlob(value.toArray())

    /**
     * Adds the specified blob to the #Blob heap if it's not there already.
     */
    fun getOrAddBlob(value: ByteArray): BlobHandle = getOrAddBlobImpl(value)

    private fun getOrAddBlobImpl(value: ByteArray): BlobHandle {
        val nextHandle = BlobHandle.fromOffset(blobHeapStartOffset + blobHeapSize)
        val (handle, exists) = blobs.getOrAdd(value, value, nextHandle)
        if (!exists) {
            blobHeapSize += BlobWriterImpl.getCompressedIntegerSize(value.size) + value.size
        }
        return handle
    }

    /**
     * Encodes a constant value to a blob and adds it to the #Blob heap,
     * if it's not there already. Uses UTF-16 for string constants.
     */
    fun getOrAddConstantBlob(value: Any?): BlobHandle =
        if (value is String) {
            getOrAddBlobUTF16(value)
        } else {
            val builder = BlobBuilder()
            builder.writeConstant(value)
            getOrAddBlob(builder)
        }

    /**
     * Encodes a string using UTF-16 into a blob and adds it to the #Blob heap,
     * if it's not there already.
     */
    fun getOrAddBlobUTF16(value: String): BlobHandle {
        val builder = BlobBuilder()
        builder.writeUTF16(value)
        return getOrAddBlob(builder)
    }

    /**
     * Encodes a string using UTF-8 into a blob and adds it to the #Blob heap,
     * if it's not there already.
     *
     * @param allowUnpairedSurrogates True to encode unpaired surrogates as specified,
     * otherwise replace them with U+FFFD character.
     */
    fun getOrAddBlobUTF8(value: String, allowUnpairedSurrogates: Boolean = true): BlobHandle {
        val builder = BlobBuilder()
        builder.writeUTF8(value, allowUnpairedSurrogates)
        return getOrAddBlob(builder)
    }

    /**
     * Adds the specified Guid to the #Guid heap if it's not there already.
     */
    fun getOrAddGuid(guid: Uuid): GuidHandle {
        if (guid == Uuid.NIL) {
            return GuidHandle.fromIndex(0)
        }

        guids[guid]?.let { return it }

        val result = getNewGuidHandle()
        guids[guid] = result
        guidBuilder.writeGuid(guid)
        return result
    }

    /**
     * Reserves space on the #Guid heap for a GUID.
     */
    fun reserveGuid(): ReservedBlob<GuidHandle> {
        val handle = getNewGuidHandle()
        val content = guidBuilder.reserveBytes(BlobUtilities.SIZE_OF_GUID)
        return ReservedBlob(handle, content)
    }

    private fun getNewGuidHandle(): GuidHandle =
        // The Guid heap is an array of GUIDs, each 16 bytes wide.
        // Its first element is numbered 1, its second 2, and so on.
        GuidHandle.fromIndex((guidBuilder.count shr 4) + 1)

    /**
     * Adds the specified string to the #String heap if it's not there already.
     */
    fun getOrAddString(value: String): StringHandle {
        if (value.isEmpty()) {
            return StringHandle.fromRaw(0u) // idx 0 is reserved for the empty string
        }

        return strings[value] ?: StringHandle.fromWriterVirtualIndex(strings.size + 1).also {
            strings[value] = it
        }
    }

    /**
     * Reserves space on the #UserString heap for a string of the specified length.
     */
    fun reserveUserString(length: Int): ReservedBlob<UserStringHandle> {
        require(length >= 0) { "length out of range" }
        val handle = getNewUserStringHandle()
        val encodedLength = BlobUtilities.getUserStringByteLength(length)
        val reserved = userStringBuilder.reserveBytes(
            BlobWriterImpl.getCompressedIntegerSize(encodedLength) + encodedLength,
        )
        return ReservedBlob(handle, reserved)
    }

    /**
     * Adds the specified string to the #UserString heap if it's not there already.
     */
    fun getOrAddUserString(value: String): UserStringHandle {
        return userStrings[value] ?: run {
            val handle = getNewUserStringHandle()
            userStrings[value] = handle
            userStringBuilder.writeUserString(value)
            handle
        }
    }

    private fun getNewUserStringHandle(): UserStringHandle {
        val offset = userStringHeapStartOffset + userStringBuilder.count

        // Native metadata emitter allows strings to exceed the heap size limit
        // as long as the index is within the limits.
        check(offset < USER_STRING_HEAP_SIZE_LIMIT) { "user string heap size limit exceeded" }

        return UserStringHandle.fromOffset(offset)
    }

    /**
     * Fills in the virtual-index-to-offset map and writes the sorted #String
     * heap content into [heapBuilder].
     */
    private fun serializeStringHeap(heapBuilder: BlobBuilder): IntArray {
        // Sort by suffix
        val sorted = strings.entries.sortedWith(SuffixSort)

        // Create VirtIdx to Idx map and add entry for empty string
        val totalCount = sorted.size + 1
        val map = IntArray(totalCount)

        map[0] = 0
        heapBuilder.writeByte(0.toByte())

        // Find strings that can be folded
        var prev = ""
        for (entry in sorted) {
            val position = stringHeapStartOffset + heapBuilder.count

            // It is important to use ordinal comparison otherwise we'd use the current culture!
            if (prev.endsWith(entry.key) && !BlobUtilities.isLowSurrogateChar(entry.key[0].code)) {
                // Map over the tail of prev string. Watch for null-terminator of prev string.
                map[entry.value.getWriterVirtualIndex()] =
                    position - (BlobUtilities.getUTF8ByteCount(entry.key).first + 1)
            } else {
                map[entry.value.getWriterVirtualIndex()] = position
                heapBuilder.writeUTF8(entry.key, allowUnpairedSurrogates = false)
                heapBuilder.writeByte(0.toByte())
            }

            prev = entry.key
        }

        return map
    }

    private object SuffixSort : Comparator<Map.Entry<String, StringHandle>> {
        override fun compare(xPair: Map.Entry<String, StringHandle>, yPair: Map.Entry<String, StringHandle>): Int {
            val x = xPair.key
            val y = yPair.key

            var i = x.length - 1
            var j = y.length - 1
            while (i >= 0 && j >= 0) {
                when {
                    x[i] < y[j] -> return -1
                    x[i] > y[j] -> return 1
                }
                i--
                j--
            }

            return y.length.compareTo(x.length)
        }
    }

    internal fun writeHeapsTo(builder: BlobBuilder, stringHeap: BlobBuilder) {
        writeAligned(stringHeap, builder)
        writeAligned(userStringBuilder, builder)
        writeAligned(guidBuilder, builder)
        writeAlignedBlobHeap(builder)
    }

    private fun writeAlignedBlobHeap(builder: BlobBuilder) {
        val alignment = BitArithmetic.align(blobHeapSize, 4) - blobHeapSize

        val writer = BlobWriter(builder.reserveBytes(blobHeapSize + alignment))

        val startOffset = blobHeapStartOffset
        for (entry in blobs.entries()) {
            val heapOffset = entry.handle.getHeapOffset()

            writer.offset = if (heapOffset == 0) 0 else heapOffset - startOffset
            writer.writeCompressedInteger(entry.content.size)
            writer.writeBytes(entry.content)
        }

        writer.offset = blobHeapSize
        writer.writeBytes(0.toByte(), alignment)
    }

    private fun writeAligned(source: BlobBuilder, target: BlobBuilder) {
        val length = source.count
        target.linkSuffix(source)
        target.writeBytes(0.toByte(), BitArithmetic.align(length, 4) - length)
    }

    // --- metadata root serialization (port of MetadataBuilder.cs main file) ---

    /**
     * Returns serialized metadata (sizes, string heap, string map) for the
     * current builder state.
     */
    internal fun getSerializedMetadata(externalRowCounts: IntArray, metadataVersionByteCount: Int): SerializedMetadata {
        val stringHeapBuilder = HeapBlobBuilder(stringHeapCapacity)
        val stringMap = serializeStringHeap(stringHeapBuilder)

        val heapSizes = intArrayOf(
            userStringBuilder.count,
            stringHeapBuilder.count,
            blobHeapSize,
            guidBuilder.count,
        )

        val sizes = MetadataSizes(getRowCounts(), externalRowCounts, heapSizes, metadataVersionByteCount)

        return SerializedMetadata(sizes, stringHeapBuilder, stringMap)
    }

    internal fun serializeMetadataHeader(builder: BlobBuilder, metadataVersion: String, sizes: MetadataSizes) {
        val startPosition = builder.count

        // signature
        builder.writeUInt32(0x424A5342u)

        // major version, minor version, reserved
        builder.writeUInt16(1.toUShort())
        builder.writeUInt16(1.toUShort())
        builder.writeUInt32(0u)

        // Spec (II.24.2.1 Metadata Root):
        // Length ... Number of bytes allocated to hold version string
        // (including null terminator); m rounded up to a multiple of four.
        builder.writeInt32(sizes.metadataVersionPaddedLength)

        val versionStart = builder.count
        builder.writeUTF8(metadataVersion)
        builder.writeByte(0.toByte())

        repeat(sizes.metadataVersionPaddedLength - (builder.count - versionStart)) {
            builder.writeByte(0.toByte())
        }

        // reserved
        builder.writeUInt16(0.toUShort())

        // number of streams (#~, #Strings, #US, #GUID, #Blob)
        builder.writeUInt16(5.toUShort())

        var offsetFromStartOfMetadata = sizes.metadataHeaderSize
        offsetFromStartOfMetadata = serializeStreamHeader(
            builder,
            offsetFromStartOfMetadata,
            sizes.metadataTableStreamSize,
            "#~",
        )
        offsetFromStartOfMetadata = serializeStreamHeader(
            builder,
            offsetFromStartOfMetadata,
            sizes.getAlignedHeapSize(HeapIndex.STRING),
            "#Strings",
        )
        offsetFromStartOfMetadata = serializeStreamHeader(
            builder,
            offsetFromStartOfMetadata,
            sizes.getAlignedHeapSize(HeapIndex.USER_STRING),
            "#US",
        )
        offsetFromStartOfMetadata = serializeStreamHeader(
            builder,
            offsetFromStartOfMetadata,
            sizes.getAlignedHeapSize(HeapIndex.GUID),
            "#GUID",
        )
        serializeStreamHeader(
            builder,
            offsetFromStartOfMetadata,
            sizes.getAlignedHeapSize(HeapIndex.BLOB),
            "#Blob",
        )

        assert(builder.count - startPosition == sizes.metadataHeaderSize)
    }

    /** Writes one stream header; returns the offset right after the stream. */
    private fun serializeStreamHeader(
        builder: BlobBuilder,
        offsetFromStartOfMetadata: Int,
        alignedStreamSize: Int,
        streamName: String,
    ): Int {
        // 4 for offset + 4 for size + zero-terminated name padded to 4
        val sizeOfStreamHeader = 8 + BitArithmetic.align(streamName.length + 1, 4)
        builder.writeInt32(offsetFromStartOfMetadata)
        builder.writeInt32(alignedStreamSize)
        for (ch in streamName) {
            builder.writeByte(ch.code.toByte())
        }

        // write 0-bytes until the padded size
        repeat(sizeOfStreamHeader - 8 - streamName.length) {
            builder.writeByte(0.toByte())
        }

        return offsetFromStartOfMetadata + alignedStreamSize
    }

    internal companion object {
        private const val USER_STRING_HEAP_SIZE_LIMIT = 0x01000000

        const val METADATA_FORMAT_MAJOR_VERSION: Byte = 2
        const val METADATA_FORMAT_MINOR_VERSION: Byte = 0

        internal val EMPTY_ROW_COUNTS: IntArray = IntArray(MetadataTokens.TABLE_COUNT)
    }
}
