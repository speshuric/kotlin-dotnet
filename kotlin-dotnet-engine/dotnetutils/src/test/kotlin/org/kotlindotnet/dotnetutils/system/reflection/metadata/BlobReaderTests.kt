// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (tests/Utilities/
// CompressedIntegerTests.cs and a subset of BlobReaderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Deviations:
//  - C# invalid encodings return BlobReader.InvalidCompressedInteger
//    sentinel; our BlobReader throws IllegalStateException instead —
//    tests adapted to assertThrows;
//  - unsafe pointer plumbing of the original is not applicable to the
//    ByteArray-based port.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlobReaderTests {
    // === helpers (spec-faithful reference encoder, II.23.2) ===

    private fun encodeInteger(value: Int, byteCount: Int): ByteArray {
        assertTrue(value >= 0)
        return when (byteCount) {
            1 -> byteArrayOf(value.toByte())
            2 -> byteArrayOf(
                (0x80 or ((value shr 8) and 0x3F)).toByte(),
                (value and 0xFF).toByte(),
            )
            4 -> byteArrayOf(
                (0xC0 or ((value shr 24) and 0x1F)).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte(),
            )
            else -> error("bad byteCount")
        }
    }

    private fun rotate(value: Int, mask: Int): Int {
        val signBit = if (value >= 0) 0 else 1
        var v = value shl 1
        v = v or signBit
        return v and mask
    }

    private fun compressUnsignedReference(value: Int): ByteArray? =
        when {
            value < 0 -> null
            value < (1 shl 7) -> encodeInteger(value, 1)
            value < (1 shl 14) -> encodeInteger(value, 2)
            value < (1 shl 29) -> encodeInteger(value, 4)
            else -> null
        }

    private fun compressSignedReference(value: Int): ByteArray? =
        when {
            value >= -(1 shl 6) && value < (1 shl 6) -> encodeInteger(rotate(value, 0x7F), 1)
            value >= -(1 shl 13) && value < (1 shl 13) -> encodeInteger(rotate(value, 0x3FFF), 2)
            value >= -(1 shl 28) && value < (1 shl 28) -> encodeInteger(rotate(value, 0x1FFFFFFF), 4)
            else -> null
        }

    private fun readerOf(vararg bytes: Int): BlobReader =
        BlobReader(bytes.map { it.toByte() }.toByteArray(), 0, bytes.size)

    private fun readerOf(bytes: ByteArray): BlobReader = BlobReader(bytes, 0, bytes.size)

    // === CompressedIntegerTests ===

    @Test
    fun decompressUnsignedIntegersFromSpecExamples() {
        assertEquals(0x03, readerOf(0x03).readCompressedInteger())
        assertEquals(0x7F, readerOf(0x7F).readCompressedInteger())
        assertEquals(0x80, readerOf(0x80, 0x80).readCompressedInteger())
        assertEquals(0x2E57, readerOf(0xAE, 0x57).readCompressedInteger())
        assertEquals(0x3FFF, readerOf(0xBF, 0xFF).readCompressedInteger())
        assertEquals(0x4000, readerOf(0xC0, 0x00, 0x40, 0x00).readCompressedInteger())
        assertEquals(0x1FFFFFFF, readerOf(0xDF, 0xFF, 0xFF, 0xFF).readCompressedInteger())
    }

    @Test
    fun compressUnsignedIntegersFromSpecExamples() {
        val b = BlobBuilder()
        b.writeCompressedInteger(0x03); assertContentEquals(byteArrayOf(0x03), b.toArray()); b.clear()
        b.writeCompressedInteger(0x7F); assertContentEquals(byteArrayOf(0x7F), b.toArray()); b.clear()
        b.writeCompressedInteger(0x80); assertContentEquals(byteArrayOf(0x80.toByte(), 0x80.toByte()), b.toArray()); b.clear()
        b.writeCompressedInteger(0x2E57); assertContentEquals(byteArrayOf(0xAE.toByte(), 0x57), b.toArray()); b.clear()
        b.writeCompressedInteger(0x3FFF); assertContentEquals(byteArrayOf(0xBF.toByte(), 0xFF.toByte()), b.toArray()); b.clear()
        b.writeCompressedInteger(0x4000); assertContentEquals(byteArrayOf(0xC0.toByte(), 0x00, 0x40, 0x00), b.toArray()); b.clear()
        b.writeCompressedInteger(0x1FFFFFFF); assertContentEquals(byteArrayOf(0xDF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), b.toArray())
    }

    @Test
    fun decompressInvalidUnsignedIntegers() {
        // Too few bytes / leading 111 — in SRM this is the sentinel
        // InvalidCompressedInteger; here it throws (see the file header).
        assertFailsWith<IllegalStateException> { readerOf().readCompressedInteger() }
        assertFailsWith<IllegalStateException> { readerOf(0x80).readCompressedInteger() }
        assertFailsWith<IllegalStateException> { readerOf(0xC0, 0xFF).readCompressedInteger() }
        assertFailsWith<IllegalArgumentException> { readerOf(0xFF, 0xFF, 0xFF, 0xFF).readCompressedInteger() }
        assertFailsWith<IllegalArgumentException> { readerOf(0xE0, 0x00, 0x00, 0x00).readCompressedInteger() }
    }

    @Test
    fun decompressSignedIntegersFromSpecExamples() {
        assertEquals(3, readerOf(0x06).readCompressedSignedInteger())
        assertEquals(-3, readerOf(0x7B).readCompressedSignedInteger())
        assertEquals(64, readerOf(0x80, 0x80).readCompressedSignedInteger())
        assertEquals(-64, readerOf(0x01).readCompressedSignedInteger())
        assertEquals(8192, readerOf(0xC0, 0x00, 0x40, 0x00).readCompressedSignedInteger())
        assertEquals(-8192, readerOf(0x80, 0x01).readCompressedSignedInteger())
        assertEquals(268435455, readerOf(0xDF, 0xFF, 0xFF, 0xFE).readCompressedSignedInteger())
        assertEquals(-268435456, readerOf(0xC0, 0x00, 0x00, 0x01).readCompressedSignedInteger())
    }

    @Test
    fun compressSignedIntegersFromSpecExamples() {
        val b = BlobBuilder()
        b.writeCompressedSignedInteger(3); assertContentEquals(byteArrayOf(0x06), b.toArray()); b.clear()
        b.writeCompressedSignedInteger(-3); assertContentEquals(byteArrayOf(0x7B), b.toArray()); b.clear()
        b.writeCompressedSignedInteger(64); assertContentEquals(byteArrayOf(0x80.toByte(), 0x80.toByte()), b.toArray()); b.clear()
        b.writeCompressedSignedInteger(-64); assertContentEquals(byteArrayOf(0x01), b.toArray()); b.clear()
        b.writeCompressedSignedInteger(8192); assertContentEquals(byteArrayOf(0xC0.toByte(), 0x00, 0x40, 0x00), b.toArray()); b.clear()
        b.writeCompressedSignedInteger(-8192); assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), b.toArray()); b.clear()
        b.writeCompressedSignedInteger(268435455); assertContentEquals(byteArrayOf(0xDF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte()), b.toArray()); b.clear()
        b.writeCompressedSignedInteger(-268435456); assertContentEquals(byteArrayOf(0xC0.toByte(), 0x00, 0x00, 0x01), b.toArray())
    }

    private fun checkUnsigned(valueToRoundTrip: Int, numberOfBytesExpected: Int) {
        val bytes = compressUnsignedReference(valueToRoundTrip)
        if (bytes == null) {
            assertEquals(-1, numberOfBytesExpected)
            assertFailsWith<IllegalArgumentException> {
                val b = BlobBuilder(); b.writeCompressedInteger(valueToRoundTrip)
            }
        } else {
            assertEquals(numberOfBytesExpected, bytes.size)
            assertEquals(valueToRoundTrip, readerOf(bytes).readCompressedInteger())
        }
    }

    private fun checkSigned(valueToRoundTrip: Int, numberOfBytesExpected: Int) {
        val bytes = compressSignedReference(valueToRoundTrip)
        if (bytes == null) {
            assertEquals(-1, numberOfBytesExpected)
            assertFailsWith<IllegalArgumentException> {
                val b = BlobBuilder(); b.writeCompressedSignedInteger(valueToRoundTrip)
            }
        } else {
            assertEquals(numberOfBytesExpected, bytes.size)
            assertEquals(valueToRoundTrip, readerOf(bytes).readCompressedSignedInteger())
        }
    }

    @Test
    fun checkCompressedUnsignedIntegers() {
        // Around 0
        checkUnsigned(0, 1)
        checkUnsigned(-1, -1)
        checkUnsigned(1, 1)

        // Near 1 byte limit
        checkUnsigned((1 shl 7), 2)
        checkUnsigned((1 shl 7) - 1, 1)
        checkUnsigned((1 shl 7) + 1, 2)

        // Near 2 byte limit
        checkUnsigned((1 shl 14), 4)
        checkUnsigned((1 shl 14) - 1, 2)
        checkUnsigned((1 shl 14) + 1, 4)

        // Near 4 byte limit
        checkUnsigned((1 shl 29), -1)
        checkUnsigned((1 shl 29) - 1, 4)
        checkUnsigned((1 shl 29) + 1, -1)
    }

    @Test
    fun checkCompressedSignedIntegers() {
        // around 0
        checkSigned(-1, 1)
        checkSigned(1, 1)

        // around 1 byte limit
        checkSigned(-(1 shl 6), 1)
        checkSigned(-(1 shl 6) - 1, 2)
        checkSigned(-(1 shl 6) + 1, 1)
        checkSigned(1 shl 6, 2)
        checkSigned((1 shl 6) - 1, 1)
        checkSigned((1 shl 6) + 1, 2)

        // around 2 byte limit
        checkSigned(-(1 shl 13), 2)
        checkSigned(-(1 shl 13) - 1, 4)
        checkSigned(-(1 shl 13) + 1, 2)
        checkSigned(1 shl 13, 4)
        checkSigned((1 shl 13) - 1, 2)
        checkSigned((1 shl 13) + 1, 4)

        // around 4 byte limit
        checkSigned(-(1 shl 28), 4)
        checkSigned(-(1 shl 28) - 1, -1)
        checkSigned(-(1 shl 28) + 1, 4)
        checkSigned(1 shl 28, -1)
        checkSigned((1 shl 28) - 1, 4)
        checkSigned((1 shl 28) + 1, -1)
    }

    // === BlobReader primitives (subset of BlobReaderTests.cs) ===

    @Test
    fun properties_andPrimitiveReads() {
        val r = readerOf(0, 1, 0, 2)
        assertEquals(0, r.offset)
        assertEquals(4, r.remainingBytes)

        assertEquals(0, r.readByte().toInt())
        assertEquals(1, r.offset)
        assertEquals(3, r.remainingBytes)

        assertEquals(1, r.readInt16()) // LE: {1, 0} -> 0x0001
        assertEquals(3, r.offset)

        assertFailsWith<IllegalStateException> { r.readInt16() } // out of data
        assertEquals(3, r.offset)

        assertEquals(2, r.readByte().toInt())
        assertEquals(4, r.offset)
        assertEquals(0, r.remainingBytes)
    }

    @Test
    fun readWidePrimitives_littleEndian() {
        val r = readerOf(
            0x78, 0x56, 0x34, 0x12, // u32 0x12345678
            0xEF, 0xCD, // u16 0xCDEF
            0, 0, 0, 0, 0, 0, 0x40, 0x40, // double 32.0
        )
        assertEquals(0x12345678u, r.readUInt32())
        assertEquals(0xCDEF.toUShort(), r.readUInt16().toUShort())
        assertEquals(32.0, r.readDouble())
    }

    @Test
    fun readBoolean_nonZeroIsTrue() {
        val r = readerOf(1, 0xFF, 0, 2)
        assertTrue(r.readBoolean())
        assertTrue(r.readBoolean())
        assertFalse(r.readBoolean())
        assertTrue(r.readBoolean())
    }
}
