// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/SectionLocation.cs,
// PortableExecutable/DirectoryEntry.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: the PEBinaryReader-based constructor of DirectoryEntry is cut
// (read-path, see analysis/03 §6).

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

data class SectionLocation(
    val relativeVirtualAddress: Int,
    val pointerToRawData: Int,
)

data class DirectoryEntry(
    val relativeVirtualAddress: Int,
    val size: Int,
)
