// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (BlobReader.cs — subset).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md and adr/0011 for scope.
//
// Deviations: read-only subset over ByteArray (no MemoryBlock); only the
// primitives needed by the minimal reader.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

/**
 * Reader over a byte slice [buffer] in `[start, start + length)`.
 * Inverse of [BlobWriter]/[BlobBuilder] encodings.
 */
class BlobReader(
    private val buffer: ByteArray,
    private val start: Int,
    val length: Int,
) {
    var offset: Int = 0
        private set

    private fun assertAvailable(count: Int) {
        if (length - offset < count) {
            throw IllegalStateException("Blob reader is out of data")
        }
    }

    val remainingBytes: Int
        get() = length - offset

    private fun u8(): Int {
        assertAvailable(1)
        return buffer[start + offset++].toInt() and 0xFF
    }

    private fun s8(): Byte {
        assertAvailable(1)
        return buffer[start + offset++]
    }

    fun readByte(): Byte = s8()

    /** SRM semantics: any non-zero byte is true. */
    fun readBoolean(): Boolean = s8() != 0.toByte()

    fun readUInt16(): Int {
        assertAvailable(2)
        val o = start + offset
        offset += 2
        return ((buffer[o + 1].toInt() and 0xFF) shl 8) or (buffer[o].toInt() and 0xFF)
    }

    fun readInt16(): Short = readUInt16().toShort()

    fun readUInt32(): UInt {
        assertAvailable(4)
        val o = start + offset
        offset += 4
        return ((buffer[o + 3].toUInt() and 0xFFu) shl 24) or
            ((buffer[o + 2].toUInt() and 0xFFu) shl 16) or
            ((buffer[o + 1].toUInt() and 0xFFu) shl 8) or
            (buffer[o].toUInt() and 0xFFu)
    }

    fun readInt32(): Int = readUInt32().toInt()

    fun readUInt64(): ULong {
        val lo = readUInt32()
        val hi = readUInt32()
        return (hi.toULong() shl 32) or lo.toULong()
    }

    fun readInt64(): Long = readUInt64().toLong()

    fun readSingle(): Float =
        Float.fromBits(readInt32())

    fun readDouble(): Double =
        Double.fromBits(readInt64())

    /** ECMA-335 II.23.2 compressed unsigned integer. */
    fun readCompressedInteger(): Int {
        val first = u8()
        when {
            first and 0x80 == 0 -> return first

            // 10xxxxxx: two bytes without the leading two bits:
            first and 0xC0 == 0x80 -> return ((first and 0x3F) shl 8) or u8()

            else -> {
                // 110xxxxx: four bytes without the leading three bits.
                // No compressed integer can lead with 3 bits set:
                if (first and 0x20 != 0) throw IllegalArgumentException("invalid compressed integer")
                val result = ((first and 0x1F) shl 24) or
                    (u8() shl 16) or
                    (u8() shl 8) or
                    u8()
                return result
            }
        }
    }

    /**
     * ECMA-335 II.23.2 compressed signed integer.
     * Inverse of [BlobWriterImpl.writeCompressedSignedInteger]:
     * the value is shifted left by 1, with the sign in the lowest bit.
     */
    fun readCompressedSignedInteger(): Int {
        // Exact mirror of SRM (MemoryBlock.PeekCompressedInteger +
        // BlobReader.TryReadCompressedSignedInteger): the raw value is an
        // integer whose prefix byte is already stripped; its LSB carries the
        // sign; decoding is raw >> 1 sign-extended to the field width.
        val first = u8()

        val raw: Int
        val bytesRead: Int

        when {
            first and 0x80 == 0 -> {
                bytesRead = 1
                raw = first
            }
            first and 0x40 == 0 -> {
                assertAvailable(1)
                bytesRead = 2
                raw = ((first and 0x3F) shl 8) or u8()
            }
            else -> {
                if (first and 0x20 != 0) throw IllegalArgumentException("invalid compressed signed integer")
                assertAvailable(3)
                bytesRead = 4
                raw = ((first and 0x1F) shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
            }
        }

        val signExtend = raw and 0x1 != 0
        var value = raw shr 1
        if (signExtend) {
            value = when (bytesRead) {
                1 -> value or 0xFFFFFFC0.toInt()
                2 -> value or 0xFFFFE000.toInt()
                else -> value or 0xF0000000.toInt()
            }
        }
        return value
    }

    fun readBytes(byteCount: Int): ByteArray {
        assertAvailable(byteCount)
        val result = buffer.copyOfRange(start + offset, start + offset + byteCount)
        offset += byteCount
        return result
    }

    /** UTF-8 string of [byteCount] bytes. */
    fun readUTF8(byteCount: Int): String {
        assertAvailable(byteCount)
        val s = buffer.decodeToString(start + offset, start + offset + byteCount)
        offset += byteCount
        return s
    }

    /** UTF-16LE string of [charCount]*2 bytes. */
    fun readUTF16(charCount: Int): String {
        assertAvailable(charCount * 2)
        val sb = StringBuilder(charCount)
        repeat(charCount) {
            sb.append(readUInt16().toChar())
        }
        return sb.toString()
    }

    companion object {
        const val MAX_COMPRESSED_INTEGER_VALUE = 0x1fffffff
    }
}
