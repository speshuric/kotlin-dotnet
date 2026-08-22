// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/CoffHeader.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: the PEBinaryReader-based constructor is cut (read-path).

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

data class CoffHeader(
    val machine: Machine,
    val numberOfSections: Short,
    val timeDateStamp: Int,
    val pointerToSymbolTable: Int,
    val numberOfSymbols: Int,
    val sizeOfOptionalHeader: Short,
    val characteristics: Int,
) {
    companion object {
        internal const val SIZE =
            2 + // Machine
                2 + // NumberOfSections
                4 + // TimeDateStamp
                4 + // PointerToSymbolTable
                4 + // NumberOfSymbols
                2 + // SizeOfOptionalHeader
                2 // Characteristics
    }
}
