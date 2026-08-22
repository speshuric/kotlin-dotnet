// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/PEDirectoriesBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

class PEDirectoriesBuilder {
    var addressOfEntryPoint: Int = 0

    /** Aka IMAGE_DIRECTORY_ENTRY_EXPORT. */
    var exportTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_IMPORT. */
    var importTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_RESOURCE. */
    var resourceTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_EXCEPTION. */
    var exceptionTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_BASERELOC. */
    var baseRelocationTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_DEBUG. */
    var debugTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_COPYRIGHT or IMAGE_DIRECTORY_ENTRY_ARCHITECTURE. */
    var copyrightTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_GLOBALPTR. */
    var globalPointerTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_TLS. */
    var threadLocalStorageTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_LOAD_CONFIG. */
    var loadConfigTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_BOUND_IMPORT. */
    var boundImportTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_IAT. */
    var importAddressTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_DELAY_IMPORT. */
    var delayImportTable: DirectoryEntry = DirectoryEntry(0, 0)

    /** Aka IMAGE_DIRECTORY_ENTRY_COM_DESCRIPTOR. */
    var corHeaderTable: DirectoryEntry = DirectoryEntry(0, 0)
}
