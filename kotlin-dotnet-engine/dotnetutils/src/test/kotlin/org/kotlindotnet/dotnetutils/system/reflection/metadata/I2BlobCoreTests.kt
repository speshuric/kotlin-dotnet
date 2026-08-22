// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Adapted from System.Reflection.Metadata tests (BlobTests.cs,
// BlobUtilitiesTests.cs, BlobContentIdTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.internal.BlobUtilities
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class I2BlobCoreTests {

    // --- CompressUnsignedIntegersFromSpecExamples ---

    private fun checkUnsignedSpec(bytes: ByteArray, value: Int) {
        val writer = BlobWriter(4)
        writer.writeCompressedInteger(value)
        assertContentEquals(bytes, writer.toArray(), "BlobWriter")

        val builder = BlobBuilder()
        builder.writeCompressedInteger(value)
        assertContentEquals(bytes, builder.toArray(), "BlobBuilder")
    }

    @Test
    fun compressUnsignedIntegersFromSpecExamples() {
        // These examples are straight from the CLI spec.
        checkUnsignedSpec(byteArrayOf(0x00), 0)
        checkUnsignedSpec(byteArrayOf(0x03), 0x03)
        checkUnsignedSpec(byteArrayOf(0x7f), 0x7F)
        checkUnsignedSpec(byteArrayOf(0x80.toByte(), 0x80.toByte()), 0x80)
        checkUnsignedSpec(byteArrayOf(0xAE.toByte(), 0x57), 0x2E57)
        checkUnsignedSpec(byteArrayOf(0xBF.toByte(), 0xFF.toByte()), 0x3FFF)
        checkUnsignedSpec(
            byteArrayOf(0xC0.toByte(), 0x00, 0x40, 0x00),
            0x4000,
        )
        checkUnsignedSpec(
            byteArrayOf(0xDF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            0x1FFFFFFF,
        )

        val writer = BlobWriter(4)
        val builder = BlobBuilder()
        assertFailsWith<IllegalArgumentException> { writer.writeCompressedInteger(-1) }
        assertFailsWith<IllegalArgumentException> { writer.writeCompressedInteger(BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE + 1) }
        assertFailsWith<IllegalArgumentException> { builder.writeCompressedInteger(-1) }
        assertFailsWith<IllegalArgumentException> { builder.writeCompressedInteger(BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE + 1) }
    }

    private fun checkSignedSpec(bytes: ByteArray, value: Int) {
        val writer = BlobWriter(4)
        writer.writeCompressedSignedInteger(value)
        assertContentEquals(bytes, writer.toArray(), "BlobWriter")

        val builder = BlobBuilder()
        builder.writeCompressedSignedInteger(value)
        assertContentEquals(bytes, builder.toArray(), "BlobBuilder")
    }

    @Test
    fun compressSignedIntegersFromSpecExamples() {
        // These examples are straight from the CLI spec.
        checkSignedSpec(byteArrayOf(0x00), 0)
        checkSignedSpec(byteArrayOf(0x02), 1)
        checkSignedSpec(byteArrayOf(0x06), 3)
        checkSignedSpec(byteArrayOf(0x7f), -1)
        checkSignedSpec(byteArrayOf(0x7b), -3)
        checkSignedSpec(byteArrayOf(0x80.toByte(), 0x80.toByte()), 64)
        checkSignedSpec(byteArrayOf(0x01), -64)
        checkSignedSpec(byteArrayOf(0xC0.toByte(), 0x00, 0x40, 0x00), 8192)
        checkSignedSpec(byteArrayOf(0x80.toByte(), 0x01), -8192)
        checkSignedSpec(byteArrayOf(0xDF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte()), 268435455)
        checkSignedSpec(byteArrayOf(0xC0.toByte(), 0x00, 0x00, 0x01), -268435456)

        val writer = BlobWriter(4)
        val builder = BlobBuilder()
        assertFailsWith<IllegalArgumentException> { writer.writeCompressedSignedInteger(BlobWriterImpl.MIN_SIGNED_COMPRESSED_INTEGER_VALUE - 1) }
        assertFailsWith<IllegalArgumentException> { writer.writeCompressedSignedInteger(BlobWriterImpl.MAX_SIGNED_COMPRESSED_INTEGER_VALUE + 1) }
        assertFailsWith<IllegalArgumentException> { builder.writeCompressedSignedInteger(BlobWriterImpl.MIN_SIGNED_COMPRESSED_INTEGER_VALUE - 1) }
        assertFailsWith<IllegalArgumentException> { builder.writeCompressedSignedInteger(BlobWriterImpl.MAX_SIGNED_COMPRESSED_INTEGER_VALUE + 1) }
    }

    @Test
    fun compressedIntegerSize() {
        assertEquals(1, BlobWriterImpl.getCompressedIntegerSize(0x00))
        assertEquals(1, BlobWriterImpl.getCompressedIntegerSize(0x7F))
        assertEquals(2, BlobWriterImpl.getCompressedIntegerSize(0x80))
        assertEquals(2, BlobWriterImpl.getCompressedIntegerSize(0x3FFF))
        assertEquals(4, BlobWriterImpl.getCompressedIntegerSize(0x4000))
        assertEquals(4, BlobWriterImpl.getCompressedIntegerSize(0x1FFFFFFF))
    }

    // --- WritePrimitive (adapted: DateTime/Decimal are cut) ---

    @Test
    fun writePrimitives() {
        val writer = BlobBuilder(17)

        writer.writeUInt32(0x11223344u)
        writer.writeUInt16(0x5566u.toUShort())
        writer.writeByte(0x77)
        writer.writeUInt64(0x8899aabbccddeeffu)
        writer.writeInt32(-1)
        writer.writeInt16(-2)
        writer.writeSByte(0xfd.toByte())
        writer.writeBoolean(true)
        writer.writeBoolean(false)
        writer.writeInt64(0xfedcba0987654321uL.toLong())
        writer.writeDouble(Double.NaN)
        writer.writeSingle(Float.NEGATIVE_INFINITY)

        assertContentEquals(
            byteArrayOf(
                0x44, 0x33, 0x22, 0x11,
                0x66, 0x55,
                0x77,
                0xff.toByte(), 0xee.toByte(), 0xdd.toByte(), 0xcc.toByte(),
                0xbb.toByte(), 0xaa.toByte(), 0x99.toByte(), 0x88.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xfe.toByte(), 0xff.toByte(),
                0xfd.toByte(),
                0x01,
                0x00,
                0x21, 0x43, 0x65, 0x87.toByte(), 0x09, 0xBA.toByte(), 0xDC.toByte(), 0xFE.toByte(),
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF8.toByte(), 0x7F, // double NaN (LE)
                0x00, 0x00, 0x80.toByte(), 0xFF.toByte(), // single -Inf (LE)
            ),
            writer.toArray(),
        )
    }

    // --- Guid layout (analysis/02 §E golden vector) ---

    @Test
    fun writeGuid_mixedEndianLayout() {
        // Guid "01020304-0506-0708-090A-0B0C0D0E0F10"
        val msb = 0x0102030405060708L
        val lsb = 0x090A0B0C0D0E0F10uL.toLong()
        val uuid = Uuid.fromLongs(msb, lsb)

        val buffer = ByteArray(16)
        BlobUtilities.writeGuid(buffer, 0, uuid)

        // .NET Guid.ToByteArray() for this value:
        assertContentEquals(
            byteArrayOf(
                0x04, 0x03, 0x02, 0x01, 0x06, 0x05, 0x08, 0x07,
                0x09, 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(),
                0x0E.toByte(), 0x0F.toByte(), 0x10,
            ),
            buffer,
        )
    }

    // --- UTF-8 / UTF-16 / user strings ---

    @Test
    fun writeUtf8_encodings() {
        fun utf8(s: String, allowUnpaired: Boolean = true): ByteArray {
            val b = BlobBuilder()
            b.writeUTF8(s, allowUnpaired)
            return b.toArray()
        }

        assertContentEquals(byteArrayOf(0x61, 0x62, 0x63), utf8("abc"))
        assertContentEquals(byteArrayOf(0xC3.toByte(), 0xA9.toByte()), utf8("\u00E9"))
        assertContentEquals(byteArrayOf(0xE2.toByte(), 0x82.toByte(), 0xAC.toByte()), utf8("\u20AC"))

        // surrogate pair (U+1F600)
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()), utf8("\uD83D\uDE00"))

        // unpaired high surrogate: kept as-is when allowed, replaced otherwise
        assertContentEquals(byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()), utf8("\uD800", allowUnpaired = true))
        assertContentEquals(
            byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()), // U+FFFD
            utf8("\uD800", allowUnpaired = false),
        )
    }

    @Test
    fun utf8ByteCount_matchesWrittenBytes() {
        for (s in listOf("abc", "\u00E9\u20AC", "\uD83D\uDE00x", "")) {
            assertEquals(BlobUtilities.getUTF8ByteCount(s).first, s.toByteArray(Charsets.UTF_8).size)
        }
    }

    @Test
    fun writeUtf16_littleEndianBytes() {
        val b = BlobBuilder()
        b.writeUTF16("a\u1234")
        assertContentEquals(byteArrayOf(0x61, 0x00, 0x34, 0x12), b.toArray())
    }

    @Test
    fun serializedString_prefixesSize() {
        val b = BlobBuilder()
        b.writeSerializedString(null)
        assertContentEquals(byteArrayOf(0xFF.toByte()), b.toArray())

        val b2 = BlobBuilder()
        b2.writeSerializedString("ab")
        assertContentEquals(byteArrayOf(0x02, 0x61, 0x62), b2.toArray())
    }

    @Test
    fun userString_trailingByteCases() {
        assertEquals(0, BlobUtilities.getUserStringTrailingByte("abc"))
        assertEquals(1, BlobUtilities.getUserStringTrailingByte("a\u0001"))
        assertEquals(1, BlobUtilities.getUserStringTrailingByte("\u00E9")) // >= 0x7F
        assertEquals(1, BlobUtilities.getUserStringTrailingByte("\u007F"))
        assertEquals(7, BlobUtilities.getUserStringByteLength(3))

        val b = BlobBuilder()
        b.writeUserString("ab")
        // length (2*2+1=5), 'a','b' UTF-16LE, trailing 0
        assertContentEquals(
            byteArrayOf(0x05, 0x61, 0x00, 0x62, 0x00, 0x00),
            b.toArray(),
        )
    }

    // --- BlobBuilder structure ---

    @Test
    fun builder_chunksAndCount() {
        val builder = BlobBuilder(capacity = 16)
        val data = ByteArray(100) { it.toByte() }
        builder.writeBytes(data)
        assertEquals(100, builder.count)
        assertTrue(builder.getChunks().count() > 1, "expected multiple chunks")
        assertContentEquals(data, builder.toArray())

        val single = BlobBuilder()
        single.writeBytes(data)
        assertTrue(builder.contentEquals(single))
        single.writeByte(0)
        assertFalse(builder.contentEquals(single))
    }

    @Test
    fun builder_linkPrefixAndSuffix() {
        val ab = BlobBuilder()
        ab.writeBytes(byteArrayOf(0x41, 0x42)) // "AB"
        val cd = BlobBuilder()
        cd.writeBytes(byteArrayOf(0x43, 0x44)) // "CD"

        cd.linkPrefix(ab)
        assertContentEquals(byteArrayOf(0x41, 0x42, 0x43, 0x44), cd.toArray())
        assertEquals(4, cd.count)

        val ef = BlobBuilder()
        ef.writeBytes(byteArrayOf(0x45, 0x46)) // "EF"
        val gh = BlobBuilder()
        gh.writeBytes(byteArrayOf(0x47, 0x48)) // "GH"
        ef.linkSuffix(gh)
        assertContentEquals(byteArrayOf(0x45, 0x46, 0x47, 0x48), ef.toArray())

        // blobs enumeration covers full content
        val total = cd.getBlobs().sumOf { it.length }
        assertEquals(4, total)
    }

    @Test
    fun builder_clear_resetsState() {
        val builder = BlobBuilder(64)
        builder.writeUInt32(0xDEADBEEFu)
        assertEquals(4, builder.count)
        builder.clear()
        assertEquals(0, builder.count)
        builder.writeByte(1)
        assertContentEquals(byteArrayOf(0x01), builder.toArray())
    }

    @Test
    fun reserveBytes_returnsWritableBlob() {
        val builder = BlobBuilder()
        val blob = builder.reserveBytes(4)
        assertFalse(blob.isDefault)
        assertEquals(4, blob.length)
        val target = ByteArray(4)
        blob.copyInto(target)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), target)
        assertEquals(4, builder.count)
    }

    // --- BlobWriter basics ---

    @Test
    fun writer_offsetLengthToArrayClear() {
        val writer = BlobWriter(8)
        assertEquals(0, writer.offset)
        assertEquals(8, writer.length)
        assertEquals(8, writer.remainingBytes)

        writer.writeUInt32(0x11223344u)
        assertEquals(4, writer.offset)
        assertEquals(4, writer.remainingBytes)

        writer.offset = 0
        writer.writeUInt32(0x55667788u)
        assertContentEquals(byteArrayOf(0x88.toByte(), 0x77, 0x66, 0x55), writer.toArray())

        writer.clear()
        assertEquals(0, writer.offset)
    }

    @Test
    fun blobWriter_fromReservedBlob() {
        val builder = BlobBuilder()
        builder.writeInt32(0)
        val reserved = builder.reserveBytes(4)
        val writer = ReservedBlob(Unit, reserved).createWriter()
        writer.writeUInt32(0xAABBCCDDu)
        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0xDD.toByte(), 0xCC.toByte(), 0xBB.toByte(), 0xAA.toByte()),
            builder.toArray(),
        )
    }

    // --- BlobContentId ---

    @Test
    fun contentId_fromHash_goldenVector() {
        val hash = ByteArray(20) { it.toByte() }
        val id = BlobContentId.fromHash(hash)

        // a=0x03020100, b=0x0504, c=0x0706 -> version 4 => 0x4706, variant bits on d => 0x88.toByte()
        val msb = 0x0302010005044706uL.toLong()
        val lsb = 0x88090A0B0C0D0E0FuL.toLong() // d' carries RFC variant bits (0x88)
        assertEquals(Uuid.fromLongs(msb, lsb), id.guid)
        assertEquals(0x93121110u, id.stamp)
        assertFalse(id.isDefault)
    }

    @Test
    fun contentId_fromBytes_roundTrip() {
        val id = BlobContentId.fromHash(ByteArray(20) { (it * 7).toByte() })

        val bytes = ByteArray(20)
        BlobUtilities.writeGuid(bytes, 0, id.guid)
        val stamp = id.stamp
        bytes[16] = (stamp and 0xFFu).toByte()
        bytes[17] = ((stamp shr 8) and 0xFFu).toByte()
        bytes[18] = ((stamp shr 16) and 0xFFu).toByte()
        bytes[19] = ((stamp shr 24) and 0xFFu).toByte()

        assertEquals(id, BlobContentId.fromBytes(bytes))
    }

    @Test
    fun contentId_timeBasedProvider() {
        val provider = BlobContentId.getTimeBasedProvider(nowUnixSeconds = { 1700000000L })
        val id = provider(emptyList())
        assertEquals(1700000000u * 1u, id.stamp)
        assertFalse(id.guid == Uuid.NIL)
    }

    @Test
    fun contentId_isDefault() {
        val nil = BlobContentId(Uuid.NIL, 0u)
        assertTrue(nil.isDefault)
        assertFalse(BlobContentId(Uuid.NIL, 1u).isDefault)
    }
}
