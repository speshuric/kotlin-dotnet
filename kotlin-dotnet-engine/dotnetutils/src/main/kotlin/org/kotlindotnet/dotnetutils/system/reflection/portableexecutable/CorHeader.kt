// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/CorHeader.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - the PEBinaryReader-based constructor is cut (read-path);
//  - Flags is a raw Int ([Flags] enum, see PEHeader.kt deviation note).

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

data class CorHeader(
    val majorRuntimeVersion: UShort,
    val minorRuntimeVersion: UShort,
    val metadataDirectory: DirectoryEntry,
    val flags: Int,
    val entryPointTokenOrRelativeVirtualAddress: Int,
    val resourcesDirectory: DirectoryEntry,
    val strongNameSignatureDirectory: DirectoryEntry,
    val codeManagerTableDirectory: DirectoryEntry,
    val vtableFixupsDirectory: DirectoryEntry,
    val exportAddressTableJumpsDirectory: DirectoryEntry,
    val managedNativeHeaderDirectory: DirectoryEntry,
)
