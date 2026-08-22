// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/ManagedTextSection.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - ImageCharacteristics is a raw Int ([Flags] enum, see PEHeader.kt
//    deviation note);
//  - Debug.Assert is dropped per porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import org.kotlindotnet.dotnetutils.system.reflection.internal.BitArithmetic
import org.kotlindotnet.dotnetutils.system.reflection.metadata.Blob
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriter

/**
 * Managed .text PE section.
 *
 * Contains in the following order:
 * - Import Address Table
 * - COR Header
 * - IL
 * - Metadata
 * - Managed Resource Data
 * - Strong Name Signature
 * - Debug Data (directory and extra info)
 * - Import Table
 * - Name Table
 * - Runtime Startup Stub
 * - Mapped Field Data
 */
internal class ManagedTextSection(
    val imageCharacteristics: Int,
    val machine: Machine,
    /** The size of IL stream (unaligned). */
    val ilStreamSize: Int,
    /** Total size of metadata (header and all streams). */
    val metadataSize: Int,
    /** The size of managed resource data stream. */
    val resourceDataSize: Int,
    /** Size of strong name hash. */
    val strongNameSignatureSize: Int,
    /** Size of Debug data. */
    val debugDataSize: Int,
    /** The size of mapped field data stream. Aligned to [MAPPED_FIELD_DATA_ALIGNMENT]. */
    val mappedFieldDataSize: Int,
) {
    /** If set, the module must include a machine code stub that transfers control to the virtual execution system. */
    internal val requiresStartupStub: Boolean
        get() = machine == Machine.I386 || machine == Machine.UNKNOWN

    /**
     * If set, the module contains instructions that assume a 64 bit instruction set. For example it may depend on an
     * address being 64 bits. This may be true even if the module contains only IL instructions because of
     * PlatformInvoke and COM interop.
     */
    internal val requires64bits: Boolean
        get() = machine == Machine.AMD64 || machine == Machine.IA64 || machine == Machine.ARM64

    val is32Bit: Boolean
        get() = !requires64bits

    private val corEntryPointName: ByteArray
        get() =
            if ((imageCharacteristics and Characteristics.DLL.value) != 0) {
                "_CorDllMain".encodeToByteArray()
            } else {
                "_CorExeMain".encodeToByteArray()
            }

    private val sizeOfImportAddressTable: Int
        get() = if (requiresStartupStub) (if (is32Bit) 2 * 4 else 2 * 8) else 0

    // (_is32bit ? 66 : 70);
    private val sizeOfImportTable: Int
        get() {
            val base =
                4 + // RVA
                    4 + // 0
                    4 + // 0
                    4 + // name RVA
                    4 + // import address table RVA
                    20 // ?
            return base +
                (if (is32Bit) 3 * 4 else 2 * 8) + // import lookup table
                2 + // hint
                corEntryPointName.size +
                1 // NUL
        }

    private val sizeOfRuntimeStartupStub: Int
        get() = if (is32Bit) 8 else 16

    internal fun calculateOffsetToMappedFieldDataStreamUnaligned(): Int {
        var result = computeOffsetToImportTable()

        if (requiresStartupStub) {
            result += sizeOfImportTable + SIZE_OF_NAME_TABLE
            result = BitArithmetic.align(result, if (is32Bit) 4 else 8) // optional padding to make startup stub's target address align on word or double word boundary
            result += sizeOfRuntimeStartupStub
        }

        return result
    }

    fun calculateOffsetToMappedFieldDataStream(): Int {
        var result = calculateOffsetToMappedFieldDataStreamUnaligned()
        if (mappedFieldDataSize != 0) {
            result = BitArithmetic.align(result, MAPPED_FIELD_DATA_ALIGNMENT)
        }
        return result
    }

    internal fun computeOffsetToDebugDirectory(): Int =
        computeOffsetToMetadata() +
            metadataSize +
            resourceDataSize +
            strongNameSignatureSize

    private fun computeOffsetToImportTable(): Int =
        computeOffsetToDebugDirectory() +
            debugDataSize

    val offsetToILStream: Int
        get() = sizeOfImportAddressTable + COR_HEADER_SIZE

    private fun computeOffsetToMetadata(): Int = offsetToILStream + BitArithmetic.align(ilStreamSize, 4)

    fun computeSizeOfTextSection(): Int = calculateOffsetToMappedFieldDataStream() + mappedFieldDataSize

    fun getEntryPointAddress(rva: Int): Int =
        // TODO: constants
        if (requiresStartupStub) {
            rva + calculateOffsetToMappedFieldDataStreamUnaligned() - (if (is32Bit) 6 else 10)
        } else {
            0
        }

    fun getImportAddressTableDirectoryEntry(rva: Int): DirectoryEntry =
        if (requiresStartupStub) {
            DirectoryEntry(rva, sizeOfImportAddressTable)
        } else {
            DirectoryEntry(0, 0)
        }

    fun getImportTableDirectoryEntry(rva: Int): DirectoryEntry =
        // TODO: constants
        if (requiresStartupStub) {
            DirectoryEntry(rva + computeOffsetToImportTable(), (if (is32Bit) 66 else 70) + 13)
        } else {
            DirectoryEntry(0, 0)
        }

    fun getCorHeaderDirectoryEntry(rva: Int): DirectoryEntry =
        DirectoryEntry(rva + sizeOfImportAddressTable, COR_HEADER_SIZE)

    /**
     * Serializes .text section data into a specified [builder].
     *
     * @param builder An empty builder to serialize section data to.
     * @param relativeVirtualAddress Relative virtual address of the section within the containing PE file.
     * @param entryPointTokenOrRelativeVirtualAddress Entry point token or RVA.
     * @param corFlags COR flags (raw value).
     * @param baseAddress Base address of the PE image.
     * @param metadataBuilder Builder containing metadata. Must be populated with data. Linked into the [builder] and
     *   can't be expanded afterwards.
     * @param ilBuilder Builder containing IL stream. Must be populated with data. Linked into the [builder] and can't
     *   be expanded afterwards.
     * @param mappedFieldDataBuilderOpt Builder containing mapped field data. Must be populated with data. Linked into
     *   the [builder] and can't be expanded afterwards.
     * @param resourceBuilderOpt Builder containing managed resource data. Must be populated with data. Linked into the
     *   [builder] and can't be expanded afterwards.
     * @param debugDataBuilderOpt Builder containing PE debug table and data. Must be populated with data. Linked into
     *   the [builder] and can't be expanded afterwards.
     * @return Blob reserved in the [builder] for strong name signature.
     */
    fun serialize(
        builder: BlobBuilder,
        relativeVirtualAddress: Int,
        entryPointTokenOrRelativeVirtualAddress: Int,
        corFlags: Int,
        baseAddress: ULong,
        metadataBuilder: BlobBuilder,
        ilBuilder: BlobBuilder,
        mappedFieldDataBuilderOpt: BlobBuilder?,
        resourceBuilderOpt: BlobBuilder?,
        debugDataBuilderOpt: BlobBuilder?,
    ): Blob {
        // TODO: avoid recalculation
        val importTableRva = getImportTableDirectoryEntry(relativeVirtualAddress).relativeVirtualAddress
        val importAddressTableRva = getImportAddressTableDirectoryEntry(relativeVirtualAddress).relativeVirtualAddress

        var strongNameSignature = Blob.DEFAULT

        if (requiresStartupStub) {
            writeImportAddressTable(builder, importTableRva)
        }

        writeCorHeader(builder, relativeVirtualAddress, entryPointTokenOrRelativeVirtualAddress, corFlags)

        // IL:
        ilBuilder.align(4)
        builder.linkSuffix(ilBuilder)

        // metadata:
        builder.linkSuffix(metadataBuilder)

        // managed resources:
        if (resourceBuilderOpt != null) {
            builder.linkSuffix(resourceBuilderOpt)
        }

        // strong name signature:
        strongNameSignature = builder.reserveBytes(strongNameSignatureSize)

        // The bytes are required to be 0 for the purpose of calculating hash of the PE content
        // when strong name signing.
        BlobWriter(strongNameSignature).writeBytes(0.toByte(), strongNameSignatureSize)

        // debug directory and data:
        if (debugDataBuilderOpt != null) {
            builder.linkSuffix(debugDataBuilderOpt)
        }

        if (requiresStartupStub) {
            writeImportTable(builder, importTableRva, importAddressTableRva)
            writeNameTable(builder)
            writeRuntimeStartupStub(builder, importAddressTableRva, baseAddress)
        }

        // mapped field data:
        if (mappedFieldDataBuilderOpt != null) {
            if (mappedFieldDataBuilderOpt.count != 0) {
                builder.align(MAPPED_FIELD_DATA_ALIGNMENT)
            }
            builder.linkSuffix(mappedFieldDataBuilderOpt)
        }

        return strongNameSignature
    }

    private fun writeImportAddressTable(builder: BlobBuilder, importTableRva: Int) {
        val ilRva = importTableRva + 40
        val hintRva = ilRva + (if (is32Bit) 12 else 16)

        // Import Address Table
        if (is32Bit) {
            builder.writeUInt32(hintRva.toUInt()) // 4
            builder.writeUInt32(0u) // 8
        } else {
            builder.writeUInt64(hintRva.toUInt().toULong()) // 8
            builder.writeUInt64(0uL) // 16
        }
    }

    private fun writeImportTable(builder: BlobBuilder, importTableRva: Int, importAddressTableRva: Int) {
        val ilRVA = importTableRva + 40
        val hintRva = ilRVA + (if (is32Bit) 12 else 16)
        val nameRva = hintRva + 12 + 2

        // Import table
        builder.writeUInt32(ilRVA.toUInt()) // 4
        builder.writeUInt32(0u) // 8
        builder.writeUInt32(0u) // 12
        builder.writeUInt32(nameRva.toUInt()) // 16
        builder.writeUInt32(importAddressTableRva.toUInt()) // 20
        builder.writeBytes(0, 20) // 40

        // Import Lookup table
        if (is32Bit) {
            builder.writeUInt32(hintRva.toUInt()) // 44
            builder.writeUInt32(0u) // 48
            builder.writeUInt32(0u) // 52
        } else {
            builder.writeUInt64(hintRva.toUInt().toULong()) // 48
            builder.writeUInt64(0uL) // 56
        }

        // Hint table
        builder.writeUInt16(0.toUShort()) // Hint 54|58

        builder.writeBytes(corEntryPointName) // 65|69
        builder.writeByte(0) // 66|70
    }

    private fun writeNameTable(builder: BlobBuilder) {
        builder.writeBytes(COR_ENTRY_POINT_DLL)
        builder.writeByte(0)
        builder.writeUInt16(0.toUShort())
    }

    private fun writeCorHeader(builder: BlobBuilder, textSectionRva: Int, entryPointTokenOrRva: Int, corFlags: Int) {
        val majorRuntimeVersion: UShort = 2.toUShort()
        val minorRuntimeVersion: UShort = 5.toUShort()
        val metadataRva = textSectionRva + computeOffsetToMetadata()
        val resourcesRva = metadataRva + metadataSize
        val signatureRva = resourcesRva + resourceDataSize

        // Size:
        builder.writeUInt32(COR_HEADER_SIZE.toUInt())

        // Version:
        builder.writeUInt16(majorRuntimeVersion)
        builder.writeUInt16(minorRuntimeVersion)

        // MetadataDirectory:
        builder.writeUInt32(metadataRva.toUInt())
        builder.writeUInt32(metadataSize.toUInt())

        // COR Flags:
        builder.writeUInt32(corFlags.toUInt())

        // EntryPoint:
        builder.writeUInt32(entryPointTokenOrRva.toUInt())

        // ResourcesDirectory:
        builder.writeUInt32((if (resourceDataSize == 0) 0 else resourcesRva).toUInt()) // 28
        builder.writeUInt32(resourceDataSize.toUInt())

        // StrongNameSignatureDirectory:
        builder.writeUInt32((if (strongNameSignatureSize == 0) 0 else signatureRva).toUInt()) // 36
        builder.writeUInt32(strongNameSignatureSize.toUInt())

        // CodeManagerTableDirectory (not supported):
        builder.writeUInt32(0u)
        builder.writeUInt32(0u)

        // VtableFixupsDirectory (not supported):
        builder.writeUInt32(0u)
        builder.writeUInt32(0u)

        // ExportAddressTableJumpsDirectory (not supported):
        builder.writeUInt32(0u)
        builder.writeUInt32(0u)

        // ManagedNativeHeaderDirectory (not supported):
        builder.writeUInt32(0u)
        builder.writeUInt32(0u)
    }

    private fun writeRuntimeStartupStub(sectionBuilder: BlobBuilder, importAddressTableRva: Int, baseAddress: ULong) {
        // entry point code, consisting of a jump indirect to _CorXXXMain
        if (is32Bit) {
            // Write zeros (nops) to pad the entry point code so that the target address is aligned on a 4 byte boundary.
            // Note that the section is aligned to FileAlignment, which is at least 512, so we can align relatively to the start of the section.
            sectionBuilder.align(4)

            sectionBuilder.writeUInt16(0.toUShort())
            sectionBuilder.writeByte(0xff.toByte())
            sectionBuilder.writeByte(0x25.toByte()) // 4
            sectionBuilder.writeUInt32(importAddressTableRva.toUInt() + baseAddress.toUInt()) // 8
        } else {
            // Write zeros (nops) to pad the entry point code so that the target address is aligned on a 8 byte boundary.
            // Note that the section is aligned to FileAlignment, which is at least 512, so we can align relatively to the start of the section.
            sectionBuilder.align(8)

            sectionBuilder.writeUInt32(0u)
            sectionBuilder.writeUInt16(0.toUShort())
            sectionBuilder.writeByte(0xff.toByte())
            sectionBuilder.writeByte(0x25.toByte()) // 8
            sectionBuilder.writeUInt64(importAddressTableRva.toULong() + baseAddress) // 16
        }
    }

    companion object {
        const val MANAGED_RESOURCES_DATA_ALIGNMENT = 8
        const val MAPPED_FIELD_DATA_ALIGNMENT = 8

        private val COR_ENTRY_POINT_DLL = "mscoree.dll".encodeToByteArray()

        private val SIZE_OF_NAME_TABLE = COR_ENTRY_POINT_DLL.size + 1 + 2

        private const val COR_HEADER_SIZE =
            4 + // header size
                2 + // major runtime version
                2 + // minor runtime version
                8 + // metadata directory
                4 + // COR flags
                4 + // entry point
                8 + // resources directory
                8 + // strong name signature directory
                8 + // code manager table directory
                8 + // vtable fixups directory
                8 + // export address table jumps directory
                8 // managed-native header directory
    }
}
