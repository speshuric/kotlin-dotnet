// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/SectionHeader.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - the PEBinaryReader-based constructor is cut (read-path);
//  - SectionCharacteristics is a raw UInt ([Flags] enum, see PEHeader.kt
//    deviation note).

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

data class SectionHeader(
    val name: String,
    val virtualSize: Int,
    val virtualAddress: Int,
    val sizeOfRawData: Int,
    val pointerToRawData: Int,
    val pointerToRelocations: Int,
    val pointerToLineNumbers: Int,
    val numberOfRelocations: UShort,
    val numberOfLineNumbers: UShort,
    val sectionCharacteristics: UInt,
) {
    companion object {
        internal const val NAME_SIZE = 8

        internal const val SIZE =
            NAME_SIZE +
                4 + // VirtualSize
                4 + // VirtualAddress
                4 + // SizeOfRawData
                4 + // PointerToRawData
                4 + // PointerToRelocations
                4 + // PointerToLineNumbers
                2 + // NumberOfRelocations
                2 + // NumberOfLineNumbers
                4 // SectionCharacteristics
    }
}
