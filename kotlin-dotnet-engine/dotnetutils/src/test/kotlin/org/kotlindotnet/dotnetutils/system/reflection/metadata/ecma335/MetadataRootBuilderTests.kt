// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata tests (Ecma335/MetadataRootBuilderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Adaptations:
//  - EncHeaders fact is cut (EnC delta not produced by this port, ADR 0009);
//  - round-trip via MetadataReader is deferred to the reader iteration;
//  - null builder / null BlobBuilder cases are enforced by Kotlin types.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MetadataRootBuilderTests {

    @Test
    fun ctor_errors() {
        val mdBuilder = MetadataBuilder()
        // version string of 255 'x' chars exceeds the 254-byte max
        assertFailsWith<IllegalStateException> { MetadataRootBuilder(mdBuilder, "x".repeat(255)) }
    }

    @Test
    fun serialize_errors() {
        val mdBuilder = MetadataBuilder()
        val rootBuilder = MetadataRootBuilder(mdBuilder)
        val builder = BlobBuilder()

        assertFailsWith<IllegalArgumentException> { rootBuilder.serialize(builder, -1, 0) }
        assertFailsWith<IllegalArgumentException> { rootBuilder.serialize(builder, 0, -1) }
    }

    @Test
    fun headers() {
        val mdBuilder = MetadataBuilder()
        val rootBuilder = MetadataRootBuilder(mdBuilder)

        val builder = BlobBuilder()
        rootBuilder.serialize(builder, 0, 0)

        assertContentEquals(
            byteArrayOf(
                // signature:
                0x42, 0x53, 0x4A, 0x42,
                // major version (1)
                0x01, 0x00,
                // minor version (1)
                0x01, 0x00,
                // reserved (0)
                0x00, 0x00, 0x00, 0x00,
                // padded version length:
                0x0C, 0x00, 0x00, 0x00,
                // padded version:
                'v'.code.toByte(), '4'.code.toByte(), '.'.code.toByte(), '0'.code.toByte(),
                '.'.code.toByte(), '3'.code.toByte(), '0'.code.toByte(), '3'.code.toByte(),
                '1'.code.toByte(), '9'.code.toByte(), 0x00, 0x00,
                // flags (0):
                0x00, 0x00,
                // stream count:
                0x05, 0x00,
                // stream headers:
                0x6C, 0x00, 0x00, 0x00,
                0x1C, 0x00, 0x00, 0x00,
                '#'.code.toByte(), '~'.code.toByte(), 0x00, 0x00,
                0x88.toByte(), 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00,
                '#'.code.toByte(), 'S'.code.toByte(), 't'.code.toByte(), 'r'.code.toByte(),
                'i'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 's'.code.toByte(),
                0x00, 0x00, 0x00, 0x00,
                0x8C.toByte(), 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00,
                '#'.code.toByte(), 'U'.code.toByte(), 'S'.code.toByte(), 0x00,
                0x90.toByte(), 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                '#'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'I'.code.toByte(),
                'D'.code.toByte(), 0x00, 0x00, 0x00,
                0x90.toByte(), 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00,
                '#'.code.toByte(), 'B'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(),
                'b'.code.toByte(), 0x00, 0x00, 0x00,
                // #~ stream:
                0x00, 0x00, 0x00, 0x00, // reserved
                0x02,                   // major version
                0x00,                   // minor version
                0x00,                   // heap sizes
                0x01,                   // reserved
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // present tables
                0x00, 0xFA.toByte(), 0x01, 0x33, 0x00, 0x16, 0x00, 0x00, // sorted tables
                // padding and alignment
                0x00, 0x00, 0x00, 0x00,
                // #Strings
                0x00, 0x00, 0x00, 0x00,
                // #US
                0x00, 0x00, 0x00, 0x00,
                // #GUID (empty)
                // #Blob
                0x00, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
    }

    private fun extractVersionBytes(bytes: ByteArray): ByteArray {
        // version starts at offset 16 (after sig+major+minor+reserved+length field)
        val versionLength = (bytes[12].toInt() and 0xFF) or
            ((bytes[13].toInt() and 0xFF) shl 8) or
            ((bytes[14].toInt() and 0xFF) shl 16) or
            ((bytes[15].toInt() and 0xFF) shl 24)
        return bytes.copyOfRange(12, 16 + versionLength)
    }

    @Test
    fun metadataVersion_default() {
        val mdBuilder = MetadataBuilder()
        mdBuilder.addModule(0, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))

        val rootBuilder = MetadataRootBuilder(mdBuilder)
        assertEquals("v4.0.30319", rootBuilder.metadataVersion)

        val builder = BlobBuilder()
        rootBuilder.serialize(builder, 0, 0)

        assertContentEquals(
            byteArrayOf(
                0x0C, 0x00, 0x00, 0x00,
                'v'.code.toByte(), '4'.code.toByte(), '.'.code.toByte(), '0'.code.toByte(),
                '.'.code.toByte(), '3'.code.toByte(), '0'.code.toByte(), '3'.code.toByte(),
                '1'.code.toByte(), '9'.code.toByte(), 0x00, 0x00,
            ),
            extractVersionBytes(builder.toArray()),
        )
    }

    @Test
    fun metadataVersion_empty() {
        val mdBuilder = MetadataBuilder()
        mdBuilder.addModule(0, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))

        val rootBuilder = MetadataRootBuilder(mdBuilder, "")
        assertEquals("", rootBuilder.metadataVersion)

        val builder = BlobBuilder()
        rootBuilder.serialize(builder, 0, 0)

        assertContentEquals(
            byteArrayOf(
                0x04, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            ),
            extractVersionBytes(builder.toArray()),
        )
    }

    @Test
    fun metadataVersion_maxLength() {
        val version = "x".repeat(254)
        val mdBuilder = MetadataBuilder()
        mdBuilder.addModule(0, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))

        val rootBuilder = MetadataRootBuilder(mdBuilder, version)
        assertEquals(version, rootBuilder.metadataVersion)

        val builder = BlobBuilder()
        rootBuilder.serialize(builder, 0, 0)
        val all = builder.toArray()

        // padded version length = align(254 + 1, 4) = 256
        assertEquals(256 and 0xFF, all[12].toInt() and 0xFF)
        assertEquals((256 shr 8) and 0xFF, all[13].toInt() and 0xFF)
        assertEquals(version.length, 254)
    }

    @Test
    fun metadataVersion_surrogatePair() {
        val version = "\u1234\ud800"
        val mdBuilder = MetadataBuilder()
        mdBuilder.addModule(0, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))

        val rootBuilder = MetadataRootBuilder(mdBuilder, version)

        val builder = BlobBuilder()
        rootBuilder.serialize(builder, 0, 0)
        val all = builder.toArray()

        // U+1234 encodes as E1 88 B4 (3B); unpaired surrogate U+D800 with
        // allowUnpairedSurrogates=true encodes as ED A0 80 (3B); pad to 8.
        assertContentEquals(
            byteArrayOf(
                0x08, 0x00, 0x00, 0x00,
                0xE1.toByte(), 0x88.toByte(), 0xB4.toByte(),
                0xED.toByte(), 0xA0.toByte(), 0x80.toByte(),
                0x00, 0x00,
            ),
            extractVersionBytes(all),
        )
    }

    @Test
    fun metadataVersion_tooLong_throws() {
        val mdBuilder = MetadataBuilder()
        // 255 chars > MAX_METADATA_VERSION_BYTE_COUNT (254)
        assertFailsWith<IllegalStateException> { MetadataRootBuilder(mdBuilder, "x".repeat(255)) }
    }

    @Test
    fun suppressValidation_allowsUnsortedTables() {
        val mdBuilder = MetadataBuilder()
        // add ClassLayout out of order
        mdBuilder.addTypeLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(2), 1, 1u)
        mdBuilder.addTypeLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(1), 1, 1u)

        val rootBuilder = MetadataRootBuilder(mdBuilder, suppressValidation = true)
        val builder = BlobBuilder()
        rootBuilder.serialize(builder, 0, 0) // should NOT throw
        assertTrue(builder.count > 0)
    }

    private fun assertTrue(b: Boolean) {
        if (!b) throw AssertionError("expected true")
    }
}
