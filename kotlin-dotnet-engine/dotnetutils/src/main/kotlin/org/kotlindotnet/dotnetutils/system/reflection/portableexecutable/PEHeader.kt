// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/PEHeader.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - the PEBinaryReader-based constructor is cut (read-path, analysis/03 §6);
//  - [Flags] enums (Characteristics, DllCharacteristics) are represented as
//    raw Int values; single-value enums (PEMagic, Subsystem) stay Kotlin enums.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

/**
 * Identifies the format of the image file.
 */
data class PEHeader(
    val magic: PEMagic,
    val majorLinkerVersion: Byte,
    val minorLinkerVersion: Byte,
    val sizeOfCode: Int,
    val sizeOfInitializedData: Int,
    val sizeOfUninitializedData: Int,
    val addressOfEntryPoint: Int,
    val baseOfCode: Int,
    val baseOfData: Int,
    val imageBase: ULong,
    val sectionAlignment: Int,
    val fileAlignment: Int,
    val majorOperatingSystemVersion: UShort,
    val minorOperatingSystemVersion: UShort,
    val majorImageVersion: UShort,
    val minorImageVersion: UShort,
    val majorSubsystemVersion: UShort,
    val minorSubsystemVersion: UShort,
    val sizeOfImage: Int,
    val sizeOfHeaders: Int,
    val checkSum: UInt,
    val subsystem: Subsystem,
    val dllCharacteristics: Int,
    val sizeOfStackReserve: ULong,
    val sizeOfStackCommit: ULong,
    val sizeOfHeapReserve: ULong,
    val sizeOfHeapCommit: ULong,
    val numberOfRvaAndSizes: Int,
    val exportTableDirectory: DirectoryEntry,
    val importTableDirectory: DirectoryEntry,
    val resourceTableDirectory: DirectoryEntry,
    val exceptionTableDirectory: DirectoryEntry,
    val certificateTableDirectory: DirectoryEntry,
    val baseRelocationTableDirectory: DirectoryEntry,
    val debugTableDirectory: DirectoryEntry,
    val copyrightTableDirectory: DirectoryEntry,
    val globalPointerTableDirectory: DirectoryEntry,
    val threadLocalStorageTableDirectory: DirectoryEntry,
    val loadConfigTableDirectory: DirectoryEntry,
    val boundImportTableDirectory: DirectoryEntry,
    val importAddressTableDirectory: DirectoryEntry,
    val delayImportTableDirectory: DirectoryEntry,
    val corHeaderTableDirectory: DirectoryEntry,
) {
    companion object {
        internal const val OFFSET_OF_CHECKSUM =
            2 + // Magic
                1 + // MajorLinkerVersion
                1 + // MinorLinkerVersion
                4 + // SizeOfCode
                4 + // SizeOfInitializedData
                4 + // SizeOfUninitializedData
                4 + // AddressOfEntryPoint
                4 + // BaseOfCode
                8 + // PE32:  BaseOfData (int), ImageBase (int); PE32+: ImageBase (long)
                4 + // SectionAlignment
                4 + // FileAlignment
                2 + // MajorOperatingSystemVersion
                2 + // MinorOperatingSystemVersion
                2 + // MajorImageVersion
                2 + // MinorImageVersion
                2 + // MajorSubsystemVersion
                2 + // MinorSubsystemVersion
                4 + // Win32VersionValue
                4 + // SizeOfImage
                4 // SizeOfHeaders

        /** Size of the "PE\0\0" signature. Lives here since PEHeaders (reader-side) is not ported. */
        internal const val PE_SIGNATURE_SIZE = 4

        fun size(is32Bit: Boolean): Int =
            OFFSET_OF_CHECKSUM +
                4 + // Checksum
                2 + // Subsystem
                2 + // DllCharacteristics
                4 * (if (is32Bit) 4 else 8) + // SizeOfStackReserve, SizeOfStackCommit, SizeOfHeapReserve, SizeOfHeapCommit
                4 + // LoaderFlags
                4 + // NumberOfRvaAndSizes
                16 * 8 // directory entries
    }
}
