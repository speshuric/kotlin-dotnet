// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (tests/Metadata/BlobTests.cs —
// facts not covered by BlobCoreTests.kt).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Deviations:
//  - ToImmutableArray* facts are covered by toArray* (no ImmutableArray
//    in pure Kotlin);
//  - Stream-based overloads (WriteContentTo(Stream), TryWriteBytes,
//    ProperStreamRead/PrematureEndOfStream, byte* writes) are cut;
//  - Pooled builder facts are cut (pooling is a non-goal, ADR 0009);
//  - WritePrimitive golden omits DateTime/Decimal (not supported by the
//    pure-Kotlin writer);
//  - WriteUTF8_Substring is skipped: no public start/length overload;
//  - LinkEmptySuffixAndPrefixShouldFreeThem is skipped: chunk freeing is
//    protected/internal.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MAX_SIGNED_COMPRESSED_INTEGER_VALUE
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MIN_SIGNED_COMPRESSED_INTEGER_VALUE
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BlobBuilderTests {
    private fun bytes(vararg v: Int): ByteArray = v.map { it.toByte() }.toByteArray()

    private fun assertBytes(b: BlobBuilder, vararg v: Int) {
        assertContentEquals(bytes(*v), b.toArray())
    }

    // === ctor ===

    @Test
    fun ctor_chunkCapacityDefaults() {
        assertEquals(256, BlobBuilder().let { it.chunkCapacity })
        assertEquals(BlobBuilder.MIN_CHUNK_SIZE, BlobBuilder(0).chunkCapacity)
        assertEquals(10001, BlobBuilder(10001).chunkCapacity)
    }

    @Test
    fun ctor_errors() {
        assertFailsWith<IllegalArgumentException> { BlobBuilder(-1) }
    }

    @Test
    fun countClear() {
        val b = BlobBuilder()
        assertEquals(0, b.count)
        b.writeByte(1)
        assertEquals(1, b.count)
        b.writeInt32(4)
        assertEquals(5, b.count)
        b.clear()
        assertEquals(0, b.count)
        b.writeInt64(1)
        assertEquals(8, b.count)
        assertBytes(b, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    }

    @Test
    fun contentEquals_matrix() {
        val b = BlobBuilder()
        assertTrue(b.contentEquals(b))
        assertFalse(b.contentEquals(null))

        fun check(left: List<Int>, right: List<Int>) {
            fun toB(a: List<Int>) = a.map { x -> x.toByte() }.toByteArray()
            val b1 = BlobBuilder(0).also { it.writeBytes(toB(left)) }
            val b2 = BlobBuilder(0).also { it.writeBytes(toB(right)) }
            assertEquals(left == right, b1.contentEquals(b2))
        }

        check(emptyList(), emptyList())
        check(listOf(1), emptyList())
        check(emptyList(), listOf(1))
        check(listOf(1), listOf(1))

        val seq16 = (0..15).toList()
        val seq17 = (0..16).toList()
        val seq33 = (0..32).toList()
        val seq34 = (0..33).toList()

        check(seq16, seq16)
        check(seq16, seq17)
        check(seq17, seq16)
        check(seq17, seq17)
        check(seq33, seq33)
        check(seq34, seq33)
        check(seq33, seq34)

        val diffAt15a = (listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 99)) + (0..16).toList()
        val diffAt15b = (0..15).toList() + (0..16).toList()
        check(diffAt15a, diffAt15b)
        check(diffAt15b, diffAt15a)

        val diffAt31a = (0..15).toList() + (0..14).toList() + listOf(99) + (16..17).toList()
        val diffAt31b = (0..33).toList()
        check(diffAt31a, diffAt31b)
        check(diffAt31b, diffAt31a)
    }

    @Test
    fun getBlobs_chunkStructure() {
        val b = BlobBuilder(16)
        b.writeBytes(1, 100)

        var blobs = b.getBlobs().toList()
        assertEquals(2, blobs.size)
        assertEquals(16, blobs[0].length)
        assertEquals(100 - 16, blobs[1].length)

        b.writeByte(1)
        blobs = b.getBlobs().toList()
        assertEquals(3, blobs.size)
        assertEquals(16, blobs[0].length)
        assertEquals(100 - 16, blobs[1].length)
        assertEquals(1, blobs[2].length)

        b.clear()
        blobs = b.getBlobs().toList()
        assertEquals(1, blobs.size)
        assertEquals(0, blobs[0].length)
    }

    @Test
    fun getChunks_destructingEnum() {
        for (j in 1 until 5) {
            val b = BlobBuilder(16)
            for (i in 0 until j) {
                b.writeBytes(i.toByte(), 16)
            }

            var n = 0
            for (chunk in b.getChunks()) n++
            assertEquals(j, n)

            val chunks = HashSet<BlobBuilder>()
            for (chunk in b.getChunks()) {
                chunks.add(chunk)
                chunk.clearChunk()
            }
            assertEquals(j, chunks.size)
        }
    }

    // === ToArray(start, count) ===

    @Test
    fun toArray1() {
        val b = BlobBuilder(16)
        assertContentEquals(byteArrayOf(), b.toArray(0, 0))

        for (i in 0 until 13) b.writeByte(i.toByte())
        b.writeUInt32(0xAABBCCDDu)

        assertBytes(b, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0xDD, 0xCC, 0xBB, 0xAA)
        assertContentEquals(byteArrayOf(), b.toArray(0, 0))
        assertContentEquals(bytes(0x00), b.toArray(0, 1))
        assertContentEquals(bytes(0x01), b.toArray(1, 1))
        assertContentEquals(byteArrayOf(), b.toArray(14, 0))
        assertContentEquals(byteArrayOf(), b.toArray(15, 0))
        assertContentEquals(byteArrayOf(), b.toArray(16, 0))
        assertContentEquals(byteArrayOf(), b.toArray(17, 0))
        assertContentEquals(bytes(0xDD), b.toArray(13, 1))
        assertContentEquals(bytes(0xCC), b.toArray(14, 1))
        assertContentEquals(bytes(0xBB), b.toArray(15, 1))
        assertContentEquals(bytes(0xAA), b.toArray(16, 1))
        assertContentEquals(bytes(0xDD, 0xCC), b.toArray(13, 2))
        assertContentEquals(bytes(0xCC, 0xBB), b.toArray(14, 2))
        assertContentEquals(bytes(0xBB, 0xAA), b.toArray(15, 2))
        assertContentEquals(bytes(0xDD, 0xCC, 0xBB), b.toArray(13, 3))
        assertContentEquals(bytes(0xCC, 0xBB, 0xAA), b.toArray(14, 3))
        assertContentEquals(bytes(0xDD, 0xCC, 0xBB, 0xAA), b.toArray(13, 4))
    }

    @Test
    fun toArray2_multiChunk() {
        val b = BlobBuilder(16)
        assertContentEquals(byteArrayOf(), b.toArray(0, 0))

        for (i in 0 until 34) b.writeByte(i.toByte())

        assertEquals(34, b.count)
        assertContentEquals(
            bytes(
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
                0x20, 0x21,
            ),
            b.toArray(),
        )
        assertContentEquals(
            bytes(0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21),
            b.toArray(0x0E, 20),
        )
        assertContentEquals(bytes(0x0E), b.toArray(0x0E, 1))
        assertContentEquals(bytes(0x0E, 0x0F), b.toArray(0x0E, 2))
        assertContentEquals(bytes(0x0E, 0x0F, 0x10), b.toArray(0x0E, 3))
        assertContentEquals(bytes(0x0E, 0x0F, 0x10, 0x11), b.toArray(0x0E, 4))
        assertContentEquals(bytes(0x1E), b.toArray(0x1E, 1))
        assertContentEquals(bytes(0x1E, 0x1F), b.toArray(0x1E, 2))
        assertContentEquals(bytes(0x1E, 0x1F, 0x20), b.toArray(0x1E, 3))
        assertContentEquals(bytes(0x1E, 0x1F, 0x20, 0x21), b.toArray(0x1E, 4))
    }

    @Test
    fun toArray_errors() {
        val b = BlobBuilder(16)
        b.writeByte(1)
        assertFailsWith<IllegalArgumentException> { b.toArray(-1, 0) }
        assertFailsWith<IllegalArgumentException> { b.toArray(0, -1) }
        assertFailsWith<IllegalArgumentException> { b.toArray(0, 2) }
        assertFailsWith<IllegalArgumentException> { b.toArray(1, 1) }
    }

    // === WriteContentTo ===

    @Test
    fun writeContentTo_blobWriter_and_builder() {
        val b1 = BlobBuilder(16)
        repeat(20) { i -> b1.writeByte(i.toByte()) }

        val w = BlobWriter(256)
        b1.writeContentTo(w)
        assertBytes(b1, *IntArray(20) { it })

        b1.writeByte(0xFF.toByte())
        b1.writeContentTo(w)
        assertEquals(41, w.toArray().size)
        assertEquals(0xFF.toByte(), w.toArray()[40])

        val b2 = BlobBuilder(256)
        b1.clear()
        repeat(20) { i -> b1.writeByte(i.toByte()) }
        b1.writeContentTo(b2)
        assertContentEquals((0 until 20).map { it.toByte() }.toByteArray(), b2.toArray())

        b1.writeByte(0xFF.toByte())
        b1.writeContentTo(b2)
        assertEquals(41, b2.count)
    }

    // === LinkSuffix / LinkPrefix / Link ===

    @Test
    fun linkSuffix1() {
        val b1 = BlobBuilder(16).apply { writeByte(1); writeByte(2); writeByte(3) }
        val b2 = BlobBuilder(16).apply { writeByte(4) }
        b1.linkSuffix(b2)
        assertBytes(b1, 1, 2, 3, 4)
        assertEquals(4, b1.count)
        assertEquals(1, b2.count)

        val b3 = BlobBuilder(16).apply { writeByte(5) }
        val b4 = BlobBuilder(16).apply { writeByte(6) }
        b3.linkSuffix(b4)
        b1.linkSuffix(b3)
        assertBytes(b1, 1, 2, 3, 4, 5, 6)
        assertEquals(6, b1.count)
        assertEquals(2, b3.count)
        assertEquals(1, b4.count)
    }

    @Test
    fun linkSuffix2_multiChunk() {
        val b1 = BlobBuilder(16).apply { writeBytes(1, 16) }
        val b2 = BlobBuilder(16).apply { writeBytes(2, 16) }
        b1.linkSuffix(b2)
        assertBytes(b1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2)
        assertEquals(32, b1.count)
        assertEquals(16, b2.count)
    }

    @Test
    fun linkSuffix_emptyCases() {
        val e1 = BlobBuilder(16)
        val f1 = BlobBuilder(16).apply { writeByte(2) }
        e1.linkSuffix(f1)
        assertBytes(e1, 0x02)
        assertEquals(1, e1.count)
        assertEquals(1, f1.count)

        val e2 = BlobBuilder(16)
        val f2 = BlobBuilder(16)
        e2.linkSuffix(f2)
        assertContentEquals(byteArrayOf(), e2.toArray())
        assertEquals(0, e2.count)
        assertEquals(0, f2.count)
    }

    @Test
    fun linkPrefix1() {
        val b1 = BlobBuilder(16).apply { writeByte(1); writeByte(2); writeByte(3) }
        val b2 = BlobBuilder(16).apply { writeByte(4) }
        b1.linkPrefix(b2)
        assertBytes(b1, 4, 1, 2, 3)
        assertEquals(4, b1.count)
        assertEquals(1, b2.count)
    }

    @Test
    fun link_linkedBuildersThrowOnUse() {
        val b1 = BlobBuilder(16).apply { writeByte(1) }
        val b2 = BlobBuilder(16).apply { writeByte(2) }
        val b3 = BlobBuilder(16).apply { writeByte(3) }
        val b4 = BlobBuilder(16).apply { writeByte(4) }
        val b5 = BlobBuilder(16).apply { writeByte(5) }

        b2.linkPrefix(b1)
        assertBytes(b2, 1, 2)
        assertFailsWith<IllegalStateException> { b1.toArray() }
        assertFailsWith<IllegalStateException> { b2.linkPrefix(b1) }
        assertFailsWith<IllegalStateException> { b1.writeByte(0xFF.toByte()) }
        assertFailsWith<IllegalStateException> { b1.getBlobs() }
        assertFailsWith<IllegalStateException> { b1.contentEquals(b1) }
        assertFailsWith<IllegalStateException> { b1.writeUTF16("str") }

        b2.linkSuffix(b3)
        assertBytes(b2, 1, 2, 3)
        assertFailsWith<IllegalStateException> { b3.linkPrefix(b5) }

        b2.linkPrefix(b4)
        assertBytes(b2, 4, 1, 2, 3)
        assertFailsWith<IllegalStateException> { b4.linkPrefix(b5) }

        b2.linkSuffix(b5)
        assertBytes(b2, 4, 1, 2, 3, 5)
    }

    // === ReserveBytes ===

    @Test
    fun reserveBytes_writers() {
        val b = BlobBuilder(16)
        val writer0 = BlobWriter(b.reserveBytes(0))
        val writer1 = BlobWriter(b.reserveBytes(1))
        val writer2 = BlobWriter(b.reserveBytes(2))
        assertEquals(3, b.count)
        assertBytes(b, 0, 0, 0)

        assertEquals(0, writer0.length)
        assertEquals(0, writer0.remainingBytes)

        writer1.writeBoolean(true)
        assertEquals(1, writer1.length)
        assertEquals(0, writer1.remainingBytes)

        writer2.writeByte(1)
        assertEquals(2, writer2.length)
        assertEquals(1, writer2.remainingBytes)
    }

    @Test
    fun reserveBytes_singleChunkForLargeReservation() {
        val b = BlobBuilder(16)
        val writer = BlobWriter(b.reserveBytes(17))
        writer.writeBytes(1, 17)

        val blobs = b.getBlobs().toList()
        assertEquals(1, blobs.size)
        assertContentEquals(bytes(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1), blobs[0].toArray())
    }

    // === Compressed integers via BOTH writer and builder ===

    private fun testCompressedUnsigned(expected: IntArray, value: Int) {
        val w = BlobWriter(4)
        w.writeCompressedInteger(value)
        assertContentEquals(bytes(*expected), w.toArray())

        val b = BlobBuilder()
        b.writeCompressedInteger(value)
        assertContentEquals(bytes(*expected), b.toArray())
    }

    private fun testCompressedSigned(expected: IntArray, value: Int) {
        val w = BlobWriter(4)
        w.writeCompressedSignedInteger(value)
        assertContentEquals(bytes(*expected), w.toArray())

        val b = BlobBuilder()
        b.writeCompressedSignedInteger(value)
        assertContentEquals(bytes(*expected), b.toArray())
    }

    @Test
    fun compressUnsigned_specExamples_bothPaths() {
        testCompressedUnsigned(intArrayOf(0x00), 0)
        testCompressedUnsigned(intArrayOf(0x03), 0x03)
        testCompressedUnsigned(intArrayOf(0x7F), 0x7F)
        testCompressedUnsigned(intArrayOf(0x80, 0x80), 0x80)
        testCompressedUnsigned(intArrayOf(0xAE, 0x57), 0x2E57)
        testCompressedUnsigned(intArrayOf(0xBF, 0xFF), 0x3FFF)
        testCompressedUnsigned(intArrayOf(0xC0, 0x00, 0x40, 0x00), 0x4000)
        testCompressedUnsigned(intArrayOf(0xDF, 0xFF, 0xFF, 0xFF), 0x1FFFFFFF)

        assertFailsWith<IllegalArgumentException> { BlobWriter(4).writeCompressedInteger(-1) }
        assertFailsWith<IllegalArgumentException> { BlobWriter(4).writeCompressedInteger(MAX_COMPRESSED_INTEGER_VALUE + 1) }
        assertFailsWith<IllegalArgumentException> { BlobBuilder().writeCompressedInteger(-1) }
        assertFailsWith<IllegalArgumentException> { BlobBuilder().writeCompressedInteger(MAX_COMPRESSED_INTEGER_VALUE + 1) }
    }

    @Test
    fun compressSigned_specExamples_bothPaths() {
        testCompressedSigned(intArrayOf(0x00), 0)
        testCompressedSigned(intArrayOf(0x02), 1)
        testCompressedSigned(intArrayOf(0x06), 3)
        testCompressedSigned(intArrayOf(0x7F), -1)
        testCompressedSigned(intArrayOf(0x7B), -3)
        testCompressedSigned(intArrayOf(0x80, 0x80), 64)
        testCompressedSigned(intArrayOf(0x01), -64)
        testCompressedSigned(intArrayOf(0xC0, 0x00, 0x40, 0x00), 8192)
        testCompressedSigned(intArrayOf(0x80, 0x01), -8192)
        testCompressedSigned(intArrayOf(0xDF, 0xFF, 0xFF, 0xFE), 268435455)
        testCompressedSigned(intArrayOf(0xC0, 0x00, 0x00, 0x01), -268435456)

        assertFailsWith<IllegalArgumentException> { BlobWriter(4).writeCompressedSignedInteger(MIN_SIGNED_COMPRESSED_INTEGER_VALUE - 1) }
        assertFailsWith<IllegalArgumentException> { BlobWriter(4).writeCompressedSignedInteger(MAX_SIGNED_COMPRESSED_INTEGER_VALUE + 1) }
        assertFailsWith<IllegalArgumentException> { BlobBuilder().writeCompressedSignedInteger(MIN_SIGNED_COMPRESSED_INTEGER_VALUE - 1) }
        assertFailsWith<IllegalArgumentException> { BlobBuilder().writeCompressedSignedInteger(MAX_SIGNED_COMPRESSED_INTEGER_VALUE + 1) }
    }

    // === WritePrimitive (golden, без DateTime/Decimal) ===

    @Test
    fun writePrimitive_golden() {
        val w = BlobBuilder(17)
        w.writeUInt32(0x11223344u)
        w.writeUInt16(0x5566.toUShort())
        w.writeByte(0x77.toByte())
        w.writeUInt64(0x8899AABBCCDDEEFFuL)
        w.writeInt32(-1)
        w.writeInt16(-2)
        w.writeSByte((-3).toByte())
        w.writeBoolean(true)
        w.writeBoolean(false)
        w.writeInt64(0xFEDCBA0987654321uL.toLong())

        val guid = kotlin.uuid.Uuid.parse("01020304-0506-0708-090a-0b0c0d0e0f10")
        // .NET Guid.ToByteArray() layout (mixed-endian) built manually:
        val guidBytes = guid.toLongs { msb, lsb ->
            ByteArray(16).also { b ->
                fun putInt32LE(off: Int, v: Long) {
                    b[off] = v.toByte(); b[off + 1] = (v shr 8).toByte()
                    b[off + 2] = (v shr 16).toByte(); b[off + 3] = (v shr 24).toByte()
                }
                fun putInt16LE(off: Int, v: Long) {
                    b[off] = v.toByte(); b[off + 1] = (v shr 8).toByte()
                }
                // .NET mixed-endian: int32 LE | int16 LE | int16 LE | 8 raw bytes
                putInt32LE(0, msb ushr 32)
                putInt16LE(4, (msb shr 16) and 0xFFFF)
                putInt16LE(6, msb and 0xFFFF)
                for (i in 0 until 8) {
                    b[8 + i] = ((lsb shr ((7 - i) * 8)) and 0xFF).toByte()
                }
            }
        }
        w.writeBytes(guidBytes)
        w.writeGuid(guid)

        assertContentEquals(
            bytes(
                0x44, 0x33, 0x22, 0x11,
                0x66, 0x55,
                0x77,
                0xFF, 0xEE, 0xDD, 0xCC, 0xBB, 0xAA, 0x99, 0x88,
                0xFF, 0xFF, 0xFF, 0xFF,
                0xFE, 0xFF,
                0xFD,
                0x01,
                0x00,
                0x21, 0x43, 0x65, 0x87, 0x09, 0xBA, 0xDC, 0xFE,
                0x04, 0x03, 0x02, 0x01, 0x06, 0x05, 0x08, 0x07, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                0x04, 0x03, 0x02, 0x01, 0x06, 0x05, 0x08, 0x07, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
            ),
            w.toArray(),
        )
    }

    // === WriteBytes ===

    @Test
    fun writeBytes_overloads() {
        val w = BlobBuilder(4)
        w.writeBytes(byteArrayOf(1, 2, 3, 4))
        w.writeBytes(byteArrayOf())
        w.writeBytes(byteArrayOf(), 0, 0)
        w.writeBytes(byteArrayOf(5, 6, 7, 8))
        w.writeBytes(byteArrayOf(9))
        w.writeBytes(byteArrayOf(0x0A), 0, 0)
        w.writeBytes(byteArrayOf(0x0B), 0, 1)
        w.writeBytes(byteArrayOf(0x0C), 1, 0)
        w.writeBytes(byteArrayOf(0x0D, 0x0E), 1, 1)

        assertBytes(w, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0B, 0x0E)
    }

    @Test
    fun writeBytes_fillPattern() {
        val w = BlobBuilder(4)
        w.writeBytes(0xFF.toByte(), 0)
        w.writeBytes(1, 4)
        w.writeBytes(0xFF.toByte(), 0)
        w.writeBytes(2, 10)
        w.writeBytes(0xFF.toByte(), 0)
        w.writeBytes(3, 1)

        assertBytes(w, 0x01, 0x01, 0x01, 0x01, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x03)
    }

    @Test
    fun padAndAlign() {
        val w = BlobBuilder(4)
        w.writeByte(0x01)
        w.padTo(2)
        w.writeByte(0x02)
        w.align(4)
        w.align(4)

        w.writeByte(0x03)
        w.align(4)

        w.writeByte(0x04)
        w.writeByte(0x05)
        w.align(8)

        w.writeByte(0x06)
        w.align(2)
        w.align(1)

        assertBytes(w, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00)
    }

    // === UTF16 / SerializedString / UTF8 ===

    @Test
    fun writeUTF16_surrogatesPassThrough() {
        val w = BlobBuilder(4)
        w.writeUTF16("")
        w.writeUTF16("a")
        w.writeUTF16("")
        w.writeUTF16("\uD800")
        w.writeUTF16("\uDC00")
        w.writeUTF16("\uD800\uDC00")
        w.writeUTF16("\uDC00\uD800")
        w.writeUTF16("\u1234")

        assertBytes(
            w,
            0x61, 0x00,
            0x00, 0xD8,
            0x00, 0xDC,
            0x00, 0xD8, 0x00, 0xDC,
            0x00, 0xDC, 0x00, 0xD8,
            0x34, 0x12,
        )
    }

    @Test
    fun writeSerializedString_nullAndSurrogates() {
        val w = BlobBuilder(4)
        w.writeSerializedString("")
        w.writeSerializedString("a")
        w.writeSerializedString(null)
        w.writeSerializedString("")
        w.writeSerializedString("\uD800")
        w.writeSerializedString("\uDC00")
        w.writeSerializedString("\uD800\uDC00")
        w.writeSerializedString("\uDC00\uD800")
        w.writeSerializedString("\u1234")

        assertBytes(
            w,
            0x00,
            0x01, 0x61,
            0xFF,
            0x00,
            0x03, 0xED, 0xA0, 0x80,
            0x03, 0xED, 0xB0, 0x80,
            0x04, 0xF0, 0x90, 0x80, 0x80,
            0x06, 0xED, 0xB0, 0x80, 0xED, 0xA0, 0x80,
            0x03, 0xE1, 0x88, 0xB4,
        )
    }

    @Test
    fun writeUTF8_regularBmpChar_neverReplaced() {
        // Регрессия: валидный символ >= 0x800 не является суррогатом и
        // не должен заменяться на U+FFFD ни при каком значении флага.
        val w = BlobBuilder(4)
        w.writeUTF8("\u1234", allowUnpairedSurrogates = false)
        assertBytes(w, 0xE1, 0x88, 0xB4)

        w.clear()
        w.writeUTF8("\u1234", allowUnpairedSurrogates = true)
        assertBytes(w, 0xE1, 0x88, 0xB4)
    }

    @Test
    fun writeUTF8_allowUnpairedSurrogates_golden() {
        val w = BlobBuilder(4)
        w.writeUTF8("a", allowUnpairedSurrogates = true)
        w.writeUTF8("", allowUnpairedSurrogates = true)
        w.writeUTF8("bc", allowUnpairedSurrogates = true)
        w.writeUTF8("d", allowUnpairedSurrogates = true)
        w.writeUTF8("", allowUnpairedSurrogates = true)
        w.writeUTF8("\u0000\u0080\u1234", allowUnpairedSurrogates = true)
        w.writeUTF8("\u0000\uD800", allowUnpairedSurrogates = true)
        w.writeUTF8("\u0000\uDC00", allowUnpairedSurrogates = true)
        w.writeUTF8("\u0000\uD800\uDC00", allowUnpairedSurrogates = true)
        w.writeUTF8("\u0000\uDC00\uD800", allowUnpairedSurrogates = true)

        assertBytes(
            w,
            'a'.code,
            'b'.code, 'c'.code,
            'd'.code,
            0x00, 0xC2, 0x80, 0xE1, 0x88, 0xB4,
            0x00, 0xED, 0xA0, 0x80,
            0x00, 0xED, 0xB0, 0x80,
            0x00, 0xF0, 0x90, 0x80, 0x80,
            0x00, 0xED, 0xB0, 0x80, 0xED, 0xA0, 0x80,
        )
    }

    @Test
    fun writeUTF8_replaceUnpairedSurrogates_golden() {
        val w = BlobBuilder(4)
        w.writeUTF8("a", allowUnpairedSurrogates = false)
        w.writeUTF8("", allowUnpairedSurrogates = false)
        w.writeUTF8("bc", allowUnpairedSurrogates = false)
        w.writeUTF8("d", allowUnpairedSurrogates = false)
        w.writeUTF8("", allowUnpairedSurrogates = false)
        w.writeUTF8("\u0000\u0080\u1234", allowUnpairedSurrogates = false)
        w.writeUTF8("\u0000\uD800", allowUnpairedSurrogates = false)
        w.writeUTF8("\u0000\uDC00", allowUnpairedSurrogates = false)
        w.writeUTF8("\u0000\uD800\uDC00", allowUnpairedSurrogates = false)
        w.writeUTF8("\u0000\uDC00\uD800", allowUnpairedSurrogates = false)

        assertBytes(
            w,
            'a'.code,
            'b'.code, 'c'.code,
            'd'.code,
            0x00, 0xC2, 0x80, 0xE1, 0x88, 0xB4,
            0x00, 0xEF, 0xBF, 0xBD,
            0x00, 0xEF, 0xBF, 0xBD,
            0x00, 0xF0, 0x90, 0x80, 0x80,
            0x00, 0xEF, 0xBF, 0xBD, 0xEF, 0xBF, 0xBD,
        )
    }

    @Test
    fun emptyWrites_areNoOps() {
        val w = BlobBuilder(4)
        w.writeBytes(byteArrayOf())
        w.writeSerializedString(null) // writes 0xff — не empty write, проверяем отдельно ниже
        w.clear()

        w.writeUTF16("")
        assertContentEquals(byteArrayOf(), w.toArray())
    }
}
