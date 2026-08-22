// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (tests/PortableExecutable/
// PEHeaderBuilderTests.cs and PEBuilderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - tests that require PEReader/MetadataReader (reader-side) are deferred
//    to M1 (C#-harness); BasicValidation/Complex/FieldRVAAlignmentVerify,
//    signing (RSA) and checksum-vs-FX-assemblies tests are not ported;
//  - NativeResources verification is done by parsing the raw image bytes
//    with SectionHeader layout instead of PEReader;
//  - ArgumentOutOfRangeException maps to IllegalArgumentException.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class I6PEHeaderBuilderTests {
    @Test
    fun ctorErrors() {
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(sectionAlignment = 0) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(fileAlignment = 0) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(sectionAlignment = 512, fileAlignment = 1024) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(sectionAlignment = 513) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(sectionAlignment = Int.MIN_VALUE) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(fileAlignment = 513) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(fileAlignment = 64 * 1024 * 2) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(fileAlignment = Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { PEHeaderBuilder(fileAlignment = Int.MIN_VALUE) }
    }

    @Test
    fun validateFactoryMethods() {
        val peHeaderExe = PEHeaderBuilder.createExecutableHeader()
        assertTrue((peHeaderExe.imageCharacteristics and Characteristics.EXECUTABLE_IMAGE.value) != 0)

        val peHeaderLib = PEHeaderBuilder.createLibraryHeader()
        assertTrue((peHeaderLib.imageCharacteristics and Characteristics.EXECUTABLE_IMAGE.value) != 0)
        assertTrue((peHeaderLib.imageCharacteristics and Characteristics.DLL.value) != 0)
    }

    @Test
    fun computeSizeOfPEHeaders() {
        // DosHeader(0x80) + PESignature(4) + CoffHeader(20) + PEHeader(32bit: 224, 64bit: 240) + SectionHeader(40) * N
        val builder64 = PEHeaderBuilder(machine = Machine.AMD64)
        assertEquals(false, builder64.is32Bit)
        assertEquals(0x80 + 4 + 20 + PEHeader.size(false) + SectionHeader.SIZE * 3, builder64.computeSizeOfPEHeaders(3))

        val builder32 = PEHeaderBuilder()
        assertTrue(builder32.is32Bit)
        assertEquals(0x80 + 4 + 20 + PEHeader.size(true) + SectionHeader.SIZE * 1, builder32.computeSizeOfPEHeaders(1))
    }
}
