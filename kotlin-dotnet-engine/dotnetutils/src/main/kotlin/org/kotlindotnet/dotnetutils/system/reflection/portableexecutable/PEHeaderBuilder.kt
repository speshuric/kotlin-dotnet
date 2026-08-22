// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/PEHeaderBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - Characteristics/DllCharacteristics are raw Int values ([Flags] enums);
//  - Machine default is UNKNOWN instead of C# literal 0.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import org.kotlindotnet.dotnetutils.system.reflection.internal.BitArithmetic

class PEHeaderBuilder(
    val machine: Machine = Machine.UNKNOWN,
    val sectionAlignment: Int = 0x2000,
    val fileAlignment: Int = 0x200,
    val imageBase: ULong = 0x00400000u,
    val majorLinkerVersion: Byte = 0x30,
    val minorLinkerVersion: Byte = 0,
    val majorOperatingSystemVersion: UShort = 4.toUShort(),
    val minorOperatingSystemVersion: UShort = 0.toUShort(),
    val majorImageVersion: UShort = 0.toUShort(),
    val minorImageVersion: UShort = 0.toUShort(),
    val majorSubsystemVersion: UShort = 4.toUShort(),
    val minorSubsystemVersion: UShort = 0.toUShort(),
    val subsystem: Subsystem = Subsystem.WINDOWS_CUI,
    val dllCharacteristics: Int =
        DllCharacteristics.DYNAMIC_BASE.value or
            DllCharacteristics.NX_COMPATIBLE.value or
            DllCharacteristics.NO_SEH.value or
            DllCharacteristics.TERMINAL_SERVER_AWARE.value,
    val imageCharacteristics: Int = Characteristics.DLL.value,
    val sizeOfStackReserve: ULong = 0x00100000u,
    val sizeOfStackCommit: ULong = 0x1000u,
    val sizeOfHeapReserve: ULong = 0x00100000u,
    val sizeOfHeapCommit: ULong = 0x1000u,
) {
    init {
        require(
            fileAlignment in 512..(64 * 1024) && BitArithmetic.countBits(fileAlignment) == 1,
        ) { "fileAlignment out of range" }

        require(sectionAlignment >= fileAlignment && BitArithmetic.countBits(sectionAlignment) == 1) {
            "sectionAlignment out of range"
        }
    }

    companion object {
        fun createExecutableHeader(): PEHeaderBuilder =
            PEHeaderBuilder(imageCharacteristics = Characteristics.EXECUTABLE_IMAGE.value)

        fun createLibraryHeader(): PEHeaderBuilder =
            PEHeaderBuilder(imageCharacteristics = Characteristics.EXECUTABLE_IMAGE.value or Characteristics.DLL.value)
    }

    internal val is32Bit: Boolean
        get() =
            machine != Machine.AMD64 && machine != Machine.IA64 && machine != Machine.ARM64 &&
                machine != Machine.LOONG_ARCH64 && machine != Machine.RISC_V64

    internal fun computeSizeOfPEHeaders(sectionCount: Int): Int =
        PEBuilder.DOS_HEADER_SIZE +
            PEHeader.PE_SIGNATURE_SIZE +
            CoffHeader.SIZE +
            PEHeader.size(is32Bit) +
            SectionHeader.SIZE * sectionCount
}
