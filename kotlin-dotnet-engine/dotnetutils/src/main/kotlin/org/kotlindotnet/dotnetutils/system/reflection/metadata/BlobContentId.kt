// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (BlobContentId.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - System.Guid is replaced by kotlin.uuid.Uuid;
//  - the byte[] constructor is ported as [Companion.fromBytes];
//  - GetTimeBasedProvider takes the current time as a parameter
//    (pure-Kotlin stdlib has no wall-clock API without platform types).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.internal.Hash
import kotlin.uuid.Uuid

// Reads little-endian primitives; zero-extends into Long/UInt so the
// arithmetic below can assemble RFC-4122 msb/lsb without sign surprises.

private fun readUInt32LE(b: ByteArray, offset: Int): UInt =
    ((b[offset + 3].toUInt() and 0xFFu) shl 24) or ((b[offset + 2].toUInt() and 0xFFu) shl 16) or
        ((b[offset + 1].toUInt() and 0xFFu) shl 8) or (b[offset].toUInt() and 0xFFu)

private fun readUInt16LE(b: ByteArray, offset: Int): Int =
    ((b[offset + 1].toInt() and 0xFF) shl 8) or (b[offset].toInt() and 0xFF)

/** Packs [count] bytes starting at [offset] big-endian into a Long. */
private fun readBytesBE(b: ByteArray, offset: Int, count: Int): Long {
    var result = 0L
    for (i in offset until offset + count) {
        result = (result shl 8) or (b[i].toLong() and 0xFF)
    }
    return result
}

data class BlobContentId(
    val guid: Uuid,
    val stamp: UInt,
) {
    val isDefault: Boolean
        get() = guid == NIL_UUID && stamp == 0u

    override fun hashCode(): Int = Hash.combine(stamp, guid.hashCode())

    companion object {
        private val NIL_UUID = Uuid.NIL

        // SizeOfGuid (16) + sizeof(uint32 stamp)
        private const val SIZE: Int = 20

        /**
         * Port of the `BlobContentId(byte[] id)` constructor.
         *
         * Parses the .NET mixed-endian guid layout followed by a LE uint32 stamp;
         * mirrors BlobReader.ReadGuid/ReadUInt32 of the original Initialize().
         */
        fun fromBytes(id: ByteArray): BlobContentId {
            require(id.size == SIZE) { "unexpected array length, expected $SIZE" }

            // .NET mixed-endian guid layout: int32 a LE | int16 b LE | int16 c LE | 8 raw bytes
            val msb = (readUInt32LE(id, 0).toLong() shl 32) or
                (readUInt16LE(id, 4).toLong() shl 16) or
                readUInt16LE(id, 6).toLong()
            val lsb = readBytesBE(id, 8, 8)
            return BlobContentId(Uuid.fromLongs(msb, lsb), readUInt32LE(id, 16))
        }

        fun fromHash(hashCode: ByteArray): BlobContentId {
            val minHashSize = 20
            require(hashCode.size >= minHashSize) { "hash too short, expected at least $minHashSize bytes" }

            // extract guid components from input data:
            // a = h3..h0 packed as uint32, b = h5..h4, c = h7..h6 (little-endian reads)
            val a = readUInt32LE(hashCode, 0)
            var b = readUInt16LE(hashCode, 4)
            var c = readUInt16LE(hashCode, 6)

            // modify the guid data so it decodes to the form of a "random" guid ala rfc4122
            c = c and 0x0fff or (4 shl 12)
            val d = (hashCode[8].toInt() and 0x3f or (2 shl 6)).toByte()

            // assemble msb/lsb of the RFC-4122 layout: int32 a | int16 b | int16 c | bytes d..k
            val msb = (a.toLong() shl 32) or ((b.toLong() and 0xFFFF) shl 16) or (c.toLong() and 0xFFFF)
            var lsb = d.toLong() and 0xFF
            for (i in 9..15) {
                lsb = (lsb shl 8) or (hashCode[i].toLong() and 0xFF)
            }

            // compute a random-looking stamp from the remaining bits, with the upper bit set
            val stamp = 0x80000000u or readUInt32LE(hashCode, 16)

            return BlobContentId(Uuid.fromLongs(msb, lsb), stamp)
        }

        /**
         * Port of `GetTimeBasedProvider`: yields a random GUID and a timestamp
         * (seconds since Unix epoch, as in the PE Time/Date Stamp field).
         * Deterministic builds fill the stamp later from the hash of the full PE.
         */
        fun getTimeBasedProvider(nowUnixSeconds: () -> Long, randomUuid: () -> Uuid = { Uuid.random() }): (content: Iterable<Blob>) -> BlobContentId {
            return { _ -> BlobContentId(randomUuid(), nowUnixSeconds().toUInt()) }
        }
    }
}
