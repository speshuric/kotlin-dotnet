// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/PEBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - ImmutableArray is replaced by List;
//  - SectionCharacteristics is a raw UInt and Characteristics is a raw Int
//    ([Flags] enums, see PEHeader.kt deviation note);
//  - there is no wall-clock API in pure Kotlin stdlib, so the time-based
//    content id provider reads the current unix seconds from
//    [Companion.TIME_PROVIDER] (default: constant 0);
//  - Debug.Assert is dropped per porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import org.kotlindotnet.dotnetutils.system.reflection.internal.BitArithmetic
import org.kotlindotnet.dotnetutils.system.reflection.metadata.Blob
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobContentId
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriter

abstract class PEBuilder(
    val header: PEHeaderBuilder,
    deterministicIdProvider: ((Iterable<Blob>) -> BlobContentId)?,
) {
    val idProvider: (Iterable<Blob>) -> BlobContentId
    val isDeterministic: Boolean = deterministicIdProvider != null

    private val lazySections: List<Section> by lazy { createSections() }
    private var lazyChecksum: Blob = Blob.DEFAULT

    init {
        idProvider = deterministicIdProvider ?: BlobContentId.getTimeBasedProvider({ TIME_PROVIDER() })
    }

    /** A section of the PE file being built. */
    data class Section(val name: String, val characteristics: UInt)

    private data class SerializedSection(
        val builder: BlobBuilder,
        val name: String,
        val characteristics: UInt,
        val relativeVirtualAddress: Int,
        val sizeOfRawData: Int,
        val pointerToRawData: Int,
    ) {
        val virtualSize: Int
            get() = builder.count
    }

    protected fun getSections(): List<Section> = lazySections

    protected abstract fun createSections(): List<Section>

    protected abstract fun serializeSection(name: String, location: SectionLocation): BlobBuilder

    protected abstract fun getDirectories(): PEDirectoriesBuilder

    fun serialize(builder: BlobBuilder): BlobContentId {
        // Define and serialize sections in two steps.
        // We need to know about all sections before serializing them.
        val serializedSections = serializeSections()

        // The positions and sizes of directories are calculated during section serialization.
        val directories = getDirectories()

        writePESignature(builder)
        val stampFixup = writeCoffHeader(builder, serializedSections)
        writePEHeader(builder, directories, serializedSections)
        writeSectionHeaders(builder, serializedSections)
        builder.align(header.fileAlignment)

        for (section in serializedSections) {
            builder.linkSuffix(section.builder)
            builder.align(header.fileAlignment)
        }

        val contentId = idProvider(builder.getBlobs())

        // patch timestamp in COFF header:
        val stampWriter = BlobWriter(stampFixup)
        stampWriter.writeUInt32(contentId.stamp)

        return contentId
    }

    private fun serializeSections(): List<SerializedSection> {
        val sections = getSections()
        val result = ArrayList<SerializedSection>(sections.size)
        val sizeOfPeHeaders = header.computeSizeOfPEHeaders(sections.size)

        var nextRva = BitArithmetic.align(sizeOfPeHeaders, header.sectionAlignment)
        var nextPointer = BitArithmetic.align(sizeOfPeHeaders, header.fileAlignment)

        for (section in sections) {
            val builder = serializeSection(section.name, SectionLocation(nextRva, nextPointer))

            val serialized =
                SerializedSection(
                    builder = builder,
                    name = section.name,
                    characteristics = section.characteristics,
                    relativeVirtualAddress = nextRva,
                    sizeOfRawData = BitArithmetic.align(builder.count, header.fileAlignment),
                    pointerToRawData = nextPointer,
                )

            result.add(serialized)

            nextRva =
                BitArithmetic.align(
                    serialized.relativeVirtualAddress + serialized.virtualSize,
                    header.sectionAlignment,
                )
            nextPointer = serialized.pointerToRawData + serialized.sizeOfRawData
        }

        return result
    }

    private fun writePESignature(builder: BlobBuilder) {
        // MS-DOS stub (128 bytes)
        builder.writeBytes(DOS_HEADER)

        // PE Signature "PE\0\0"
        builder.writeUInt32(PE_SIGNATURE)
    }

    private fun writeCoffHeader(builder: BlobBuilder, sections: List<SerializedSection>): Blob {
        // Machine
        val machineValue = if (header.machine == Machine.UNKNOWN) Machine.I386.value else header.machine.value
        builder.writeUInt16(machineValue.toUShort())

        // NumberOfSections
        builder.writeUInt16(sections.size.toUShort())

        // TimeDateStamp:
        val stampFixup = builder.reserveBytes(4)

        // PointerToSymbolTable (TODO: not supported):
        // The file pointer to the COFF symbol table, or zero if no COFF symbol table is present.
        // This value should be zero for a PE image.
        builder.writeUInt32(0u)

        // NumberOfSymbols (TODO: not supported):
        // The number of entries in the symbol table. This data can be used to locate the string table,
        // which immediately follows the symbol table. This value should be zero for a PE image.
        builder.writeUInt32(0u)

        // SizeOfOptionalHeader:
        // The size of the optional header, which is required for executable files but not for object files.
        // This value should be zero for an object file (TODO).
        builder.writeUInt16(PEHeader.size(header.is32Bit).toUShort())

        // Characteristics
        builder.writeUInt16(header.imageCharacteristics.toUShort())

        return stampFixup
    }

    private fun writePEHeader(
        builder: BlobBuilder,
        directories: PEDirectoriesBuilder,
        sections: List<SerializedSection>,
    ) {
        builder.writeUInt16((if (header.is32Bit) PEMagic.PE32.value else PEMagic.PE32PLUS.value).toUShort())
        builder.writeByte(header.majorLinkerVersion)
        builder.writeByte(header.minorLinkerVersion)

        // SizeOfCode:
        builder.writeUInt32(sumRawDataSizes(sections, SectionCharacteristics.CONTAINS_CODE.value).toUInt())

        // SizeOfInitializedData:
        builder.writeUInt32(sumRawDataSizes(sections, SectionCharacteristics.CONTAINS_INITIALIZED_DATA.value).toUInt())

        // SizeOfUninitializedData:
        builder.writeUInt32(sumRawDataSizes(sections, SectionCharacteristics.CONTAINS_UNINITIALIZED_DATA.value).toUInt())

        // AddressOfEntryPoint:
        builder.writeUInt32(directories.addressOfEntryPoint.toUInt())

        // BaseOfCode:
        val codeSectionIndex = indexOfSection(sections, SectionCharacteristics.CONTAINS_CODE.value)
        builder.writeUInt32((if (codeSectionIndex != -1) sections[codeSectionIndex].relativeVirtualAddress else 0).toUInt())

        if (header.is32Bit) {
            // BaseOfData:
            val dataSectionIndex = indexOfSection(sections, SectionCharacteristics.CONTAINS_INITIALIZED_DATA.value)
            builder.writeUInt32((if (dataSectionIndex != -1) sections[dataSectionIndex].relativeVirtualAddress else 0).toUInt())

            builder.writeUInt32(header.imageBase.toUInt())
        } else {
            builder.writeUInt64(header.imageBase)
        }

        // NT additional fields:
        builder.writeUInt32(header.sectionAlignment.toUInt())
        builder.writeUInt32(header.fileAlignment.toUInt())
        builder.writeUInt16(header.majorOperatingSystemVersion)
        builder.writeUInt16(header.minorOperatingSystemVersion)
        builder.writeUInt16(header.majorImageVersion)
        builder.writeUInt16(header.minorImageVersion)
        builder.writeUInt16(header.majorSubsystemVersion)
        builder.writeUInt16(header.minorSubsystemVersion)

        // Win32VersionValue (reserved, should be 0)
        builder.writeUInt32(0u)

        // SizeOfImage:
        val lastSection = sections[sections.size - 1]
        builder.writeUInt32(
            BitArithmetic.align(lastSection.relativeVirtualAddress + lastSection.virtualSize, header.sectionAlignment)
                .toUInt(),
        )

        // SizeOfHeaders:
        builder.writeUInt32(BitArithmetic.align(header.computeSizeOfPEHeaders(sections.size), header.fileAlignment).toUInt())

        // Checksum:
        // Shall be zero for strong name signing.
        lazyChecksum = builder.reserveBytes(4)
        BlobWriter(lazyChecksum).writeUInt32(0u)

        builder.writeUInt16(header.subsystem.value.toUShort())
        builder.writeUInt16(header.dllCharacteristics.toUShort())

        if (header.is32Bit) {
            builder.writeUInt32(header.sizeOfStackReserve.toUInt())
            builder.writeUInt32(header.sizeOfStackCommit.toUInt())
            builder.writeUInt32(header.sizeOfHeapReserve.toUInt())
            builder.writeUInt32(header.sizeOfHeapCommit.toUInt())
        } else {
            builder.writeUInt64(header.sizeOfStackReserve)
            builder.writeUInt64(header.sizeOfStackCommit)
            builder.writeUInt64(header.sizeOfHeapReserve)
            builder.writeUInt64(header.sizeOfHeapCommit)
        }

        // LoaderFlags
        builder.writeUInt32(0u)

        // The number of data-directory entries in the remainder of the header.
        builder.writeUInt32(16u)

        // directory entries:
        builder.writeUInt32(directories.exportTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.exportTable.size.toUInt())
        builder.writeUInt32(directories.importTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.importTable.size.toUInt())
        builder.writeUInt32(directories.resourceTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.resourceTable.size.toUInt())
        builder.writeUInt32(directories.exceptionTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.exceptionTable.size.toUInt())

        // Authenticode CertificateTable directory. Shall be zero before the PE is signed.
        builder.writeUInt32(0u)
        builder.writeUInt32(0u)

        builder.writeUInt32(directories.baseRelocationTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.baseRelocationTable.size.toUInt())
        builder.writeUInt32(directories.debugTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.debugTable.size.toUInt())
        builder.writeUInt32(directories.copyrightTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.copyrightTable.size.toUInt())
        builder.writeUInt32(directories.globalPointerTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.globalPointerTable.size.toUInt())
        builder.writeUInt32(directories.threadLocalStorageTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.threadLocalStorageTable.size.toUInt())
        builder.writeUInt32(directories.loadConfigTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.loadConfigTable.size.toUInt())
        builder.writeUInt32(directories.boundImportTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.boundImportTable.size.toUInt())
        builder.writeUInt32(directories.importAddressTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.importAddressTable.size.toUInt())
        builder.writeUInt32(directories.delayImportTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.delayImportTable.size.toUInt())
        builder.writeUInt32(directories.corHeaderTable.relativeVirtualAddress.toUInt())
        builder.writeUInt32(directories.corHeaderTable.size.toUInt())

        // Reserved, should be 0
        builder.writeUInt64(0uL)
    }

    private fun writeSectionHeaders(builder: BlobBuilder, serializedSections: List<SerializedSection>) {
        for (serializedSection in serializedSections) {
            writeSectionHeader(builder, serializedSection)
        }
    }

    private fun writeSectionHeader(builder: BlobBuilder, serializedSection: SerializedSection) {
        if (serializedSection.virtualSize == 0) {
            return
        }

        val name = serializedSection.name.encodeToByteArray()
        var j = 0
        while (j < 8) {
            if (j < name.size) {
                builder.writeByte(name[j])
            } else {
                builder.writeByte(0)
            }
            j++
        }

        builder.writeUInt32(serializedSection.virtualSize.toUInt())
        builder.writeUInt32(serializedSection.relativeVirtualAddress.toUInt())
        builder.writeUInt32(serializedSection.sizeOfRawData.toUInt())
        builder.writeUInt32(serializedSection.pointerToRawData.toUInt())

        // PointerToRelocations (TODO: not supported):
        builder.writeUInt32(0u)

        // PointerToLinenumbers (TODO: not supported):
        builder.writeUInt32(0u)

        // NumberOfRelocations (TODO: not supported):
        builder.writeUInt16(0.toUShort())

        // NumberOfLinenumbers (TODO: not supported):
        builder.writeUInt16(0.toUShort())

        builder.writeUInt32(serializedSection.characteristics)
    }

    internal fun sign(
        peImage: BlobBuilder,
        strongNameSignatureFixup: Blob,
        signatureProvider: (Iterable<Blob>) -> ByteArray,
    ) {
        val peHeadersSize = header.computeSizeOfPEHeaders(getSections().size)
        val signature = signatureProvider(getContentToSign(peImage, peHeadersSize, header.fileAlignment, strongNameSignatureFixup))

        require(signature.size <= strongNameSignatureFixup.length) {
            "Signature provider returned an invalid signature"
        }

        val writer = BlobWriter(strongNameSignatureFixup)
        writer.writeBytes(signature)

        // Calculate the checksum after the strong name signature has been written.
        val checksum = calculateChecksum(peImage, lazyChecksum)
        BlobWriter(lazyChecksum).writeUInt32(checksum)
    }

    companion object {
        internal const val DOS_HEADER_SIZE = 0x80

        private const val PE_SIGNATURE = 0x00004550u

        private val DOS_HEADER = byteArrayOf(
            0x4d, 0x5a, 0x90.toByte(), 0x00, 0x03, 0x00, 0x00, 0x00,
            0x04, 0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(), 0x00, 0x00,
            0xb8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x80.toByte(), 0x00, 0x00, 0x00, // NT Header offset (0x80 == DosHeader.Length)
            0x0e, 0x1f, 0xba.toByte(), 0x0e, 0x00, 0xb4.toByte(), 0x09, 0xcd.toByte(),
            0x21, 0xb8.toByte(), 0x01, 0x4c, 0xcd.toByte(), 0x21, 0x54, 0x68,
            0x69, 0x73, 0x20, 0x70, 0x72, 0x6f, 0x67, 0x72,
            0x61, 0x6d, 0x20, 0x63, 0x61, 0x6e, 0x6e, 0x6f,
            0x74, 0x20, 0x62, 0x65, 0x20, 0x72, 0x75, 0x6e,
            0x20, 0x69, 0x6e, 0x20, 0x44, 0x4f, 0x53, 0x20,
            0x6d, 0x6f, 0x64, 0x65, 0x2e, 0x0d, 0x0d, 0x0a,
            0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )

        /**
         * Provides the current unix time (seconds) to [BlobContentId.getTimeBasedProvider] when no deterministic id
         * provider is given. Pure Kotlin stdlib has no wall-clock API; configure this for non-deterministic builds.
         */
        @JvmStatic
        internal var TIME_PROVIDER: () -> Long = { 0L }

        private fun indexOfSection(sections: List<SerializedSection>, characteristics: UInt): Int {
            for (i in sections.indices) {
                if ((sections[i].characteristics.and(characteristics)) == characteristics) {
                    return i
                }
            }

            return -1
        }

        private fun sumRawDataSizes(sections: List<SerializedSection>, characteristics: UInt): Int {
            var result = 0
            for (section in sections) {
                if ((section.characteristics.and(characteristics)) == characteristics) {
                    result += section.sizeOfRawData
                }
            }

            return result
        }

        // internal for testing
        internal fun getContentToSign(
            peImage: BlobBuilder,
            peHeadersSize: Int,
            peHeaderAlignment: Int,
            strongNameSignatureFixup: Blob,
        ): List<Blob> {
            // Signed content includes
            // - PE header without its alignment padding
            // - all sections including their alignment padding and excluding strong name signature blob

            // PE specification:
            //   To calculate the PE image hash, Authenticode orders the sections that are specified in the section table
            //   by address range, then hashes the resulting sequence of bytes, passing over the exclusion ranges.
            //
            // Note that sections are by construction ordered by their address, so there is no need to reorder.

            val content = ArrayList<Blob>()

            var remainingHeaderToSign = peHeadersSize
            var remainingHeader = BitArithmetic.align(peHeadersSize, peHeaderAlignment)
            for (blob in peImage.getBlobs()) {
                var blobStart = blob.start
                var blobLength = blob.length
                while (blobLength > 0) {
                    if (remainingHeader > 0) {
                        val length: Int

                        if (remainingHeaderToSign > 0) {
                            length = minOf(remainingHeaderToSign, blobLength)
                            content.add(Blob(blob.buffer, blobStart, length))
                            remainingHeaderToSign -= length
                        } else {
                            length = minOf(remainingHeader, blobLength)
                        }

                        remainingHeader -= length
                        blobStart += length
                        blobLength -= length
                    } else if (blob.buffer === strongNameSignatureFixup.buffer) {
                        val whole = Blob(blob.buffer, blobStart, blobLength)
                        content.add(getPrefixBlob(whole, strongNameSignatureFixup))
                        content.add(getSuffixBlob(whole, strongNameSignatureFixup))
                        break
                    } else {
                        content.add(Blob(blob.buffer, blobStart, blobLength))
                        break
                    }
                }
            }

            return content
        }

        // internal for testing
        internal fun getPrefixBlob(container: Blob, blob: Blob): Blob =
            Blob(container.buffer, container.start, blob.start - container.start)

        // internal for testing
        internal fun getSuffixBlob(container: Blob, blob: Blob): Blob =
            Blob(container.buffer, blob.start + blob.length, container.start + container.length - blob.start - blob.length)

        // internal for testing
        internal fun getContentToChecksum(peImage: BlobBuilder, checksumFixup: Blob): List<Blob> {
            val content = ArrayList<Blob>()
            for (blob in peImage.getBlobs()) {
                if (blob.buffer === checksumFixup.buffer) {
                    content.add(getPrefixBlob(blob, checksumFixup))
                    content.add(getSuffixBlob(blob, checksumFixup))
                } else {
                    content.add(blob)
                }
            }
            return content
        }

        // internal for testing
        internal fun calculateChecksum(peImage: BlobBuilder, checksumFixup: Blob): UInt =
            calculateChecksum(getContentToChecksum(peImage, checksumFixup)) + peImage.count.toUInt()

        private fun calculateChecksum(blobs: Iterable<Blob>): UInt {
            var checksum = 0u
            var pendingByte = -1

            for (blob in blobs) {
                val buffer = requireNotNull(blob.buffer) { "default blob" }
                var offset = blob.start
                val end = blob.start + blob.length

                if (pendingByte >= 0) {
                    // little-endian encoding:
                    checksum = aggregateChecksum(checksum, (((buffer[offset].toInt() and 0xFF) shl 8) or pendingByte).toUShort())
                    offset++
                }

                var evenEnd = end
                if ((evenEnd - offset) % 2 != 0) {
                    evenEnd--
                    pendingByte = buffer[evenEnd].toInt() and 0xFF
                } else {
                    pendingByte = -1
                }

                while (offset < evenEnd) {
                    // little-endian encoding:
                    checksum =
                        aggregateChecksum(
                            checksum,
                            (((buffer[offset + 1].toInt() and 0xFF) shl 8) or (buffer[offset].toInt() and 0xFF)).toUShort(),
                        )
                    offset += 2
                }
            }

            if (pendingByte >= 0) {
                checksum = aggregateChecksum(checksum, pendingByte.toUShort())
            }

            return checksum
        }

        private fun aggregateChecksum(checksum: UInt, value: UShort): UInt {
            val sum = checksum + value.toUInt()
            return (sum shr 16) + (sum and 0xFFFFu)
        }
    }
}
