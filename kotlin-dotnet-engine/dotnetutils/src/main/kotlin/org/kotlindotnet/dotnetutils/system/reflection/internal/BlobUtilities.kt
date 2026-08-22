// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Internal/Utilities/BlobUtilities.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - little/big-endian writes are implemented manually over ByteArray
//    (Unsafe.WriteUnaligned is not portable);
//  - WriteDecimal is omitted: System.Decimal has no kotlin-stdlib
//    analog; the ECMA-335 II.23.4 layout is documented at
//    [SIZE_OF_SERIALIZED_DECIMAL], the writer lands together with a
//    Kotlin decimal struct.

package org.kotlindotnet.dotnetutils.system.reflection.internal

import kotlin.uuid.Uuid

internal object BlobUtilities {
    /** Extracts byte number [index] of [value] (0 = least significant). */
    private fun byteAt(value: UInt, index: Int): Byte = ((value shr (8 * index)) and 0xFFu).toByte()

    /** Extracts byte number [index] of [value] (0 = least significant). */
    private fun byteAt(value: ULong, index: Int): Byte = ((value shr (8 * index)) and 0xFFu).toByte()

    fun writeBytes(buffer: ByteArray, start: Int, value: Byte, byteCount: Int) {
        for (i in 0 until byteCount) {
            buffer[start + i] = value
        }
    }

    fun writeDouble(buffer: ByteArray, start: Int, value: Double) {
        writeUInt64(buffer, start, value.toRawBits().toULong())
    }

    fun writeSingle(buffer: ByteArray, start: Int, value: Float) {
        writeUInt32(buffer, start, value.toRawBits().toUInt())
    }

    fun writeByte(buffer: ByteArray, start: Int, value: Byte) {
        buffer[start] = value
    }

    fun writeUInt16(buffer: ByteArray, start: Int, value: UShort) {
        val v = value.toUInt()
        buffer[start] = byteAt(v, 0)
        buffer[start + 1] = byteAt(v, 1)
    }

    fun writeUInt16BE(buffer: ByteArray, start: Int, value: UShort) {
        val v = value.toUInt()
        buffer[start] = byteAt(v, 1)
        buffer[start + 1] = byteAt(v, 0)
    }

    fun writeUInt32(buffer: ByteArray, start: Int, value: UInt) {
        for (i in 0 until 4) {
            buffer[start + i] = byteAt(value, i)
        }
    }

    fun writeUInt32BE(buffer: ByteArray, start: Int, value: UInt) {
        for (i in 0 until 4) {
            buffer[start + i] = byteAt(value, 3 - i)
        }
    }

    fun writeUInt64(buffer: ByteArray, start: Int, value: ULong) {
        for (i in 0 until 8) {
            buffer[start + i] = byteAt(value, i)
        }
    }

    /**
     * Size of an ECMA-335 II.23.4 serialized constant decimal:
     * one scale/sign byte followed by three little-endian uint32 words
     * of the 96-bit mantissa (low, mid, high) — 13 bytes total.
     *
     * The writer itself is omitted until a pure-Kotlin decimal analog
     * of `System.Decimal` is introduced (needed for custom attribute
     * constants); see deviation notes in module sources.
     */
    const val SIZE_OF_SERIALIZED_DECIMAL: Int = 1 + 3 * 4

    const val SIZE_OF_GUID: Int = 16

    /**
     * Writes a GUID in the .NET mixed-endian layout:
     * `int32 LE | int16 LE | int16 LE | 8 raw bytes`.
     *
     * [kotlin.uuid.Uuid] follows RFC-4122 big-endian msb/lsb layout;
     * [Uuid.toByteArray] yields `[msb(8, BE)][lsb(8, BE)]`, so bytes are
     * reordered explicitly here (analysis/02 §E).
     */
    fun writeGuid(buffer: ByteArray, start: Int, value: Uuid) {
        // RFC-4122 big-endian layout: b[0..7] = most significant bits, b[8..15] = lsb.
        val b = value.toByteArray()

        // int32 a (time_low), written little-endian
        buffer[start + 0] = b[3]
        buffer[start + 1] = b[2]
        buffer[start + 2] = b[1]
        buffer[start + 3] = b[0]

        // int16 b (time_mid), written little-endian
        buffer[start + 4] = b[5]
        buffer[start + 5] = b[4]

        // int16 c (version + time_hi), written little-endian
        buffer[start + 6] = b[7]
        buffer[start + 7] = b[6]

        // remaining 8 bytes raw
        for (i in 8 until SIZE_OF_GUID) {
            buffer[start + i] = b[i]
        }
    }

    /**
     * Writes UTF-8 encoded characters into [buffer].
     *
     * Port of the pointer-based `WriteUTF8`; operates on a substring window
     * of [str]. [byteCount] must be exactly the number produced by
     * [getUTF8ByteCount] for the same window.
     */
    fun writeUTF8(
        buffer: ByteArray,
        start: Int,
        str: String,
        charIndex: Int,
        charCount: Int,
        byteCount: Int,
        allowUnpairedSurrogates: Boolean,
    ) {
        assert(byteCount >= charCount)
        val replacementCharacter = '\uFFFD'
        var ptr = start
        val strEnd = charIndex + charCount

        if (byteCount == charCount) {
            // ASCII fast path
            for (i in charIndex until strEnd) {
                val c = str[i].code
                assert(c <= 0x7f)
                buffer[ptr++] = c.toByte()
            }
        } else {
            var i = charIndex
            while (i < strEnd) {
                var c = str[i++].code

                when {
                    c < 0x80 -> {
                        buffer[ptr++] = c.toByte()
                        continue
                    }
                    c < 0x800 -> {
                        buffer[ptr] = (((c shr 6) and 0x1F) or 0xC0).toByte()
                        buffer[ptr + 1] = ((c and 0x3F) or 0x80).toByte()
                        ptr += 2
                        continue
                    }
                    isSurrogateChar(c) && isHighSurrogateChar(c) && i < strEnd && isLowSurrogateChar(str[i].code) -> {
                        // surrogate pair
                        val highSurrogate = c
                        val lowSurrogate = str[i++].code
                        val codepoint =
                            (((highSurrogate - 0xd800) shl 10) + lowSurrogate - 0xdc00) + 0x10000
                        buffer[ptr] = (((codepoint shr 18) and 0x7) or 0xF0).toByte()
                        buffer[ptr + 1] = (((codepoint shr 12) and 0x3F) or 0x80).toByte()
                        buffer[ptr + 2] = (((codepoint shr 6) and 0x3F) or 0x80).toByte()
                        buffer[ptr + 3] = ((codepoint and 0x3F) or 0x80).toByte()
                        ptr += 4
                        continue
                    }
                    else -> {
                        // Unpaired high/low surrogate (regular chars >= 0x800
                        // must not be replaced — см. SRM WriteUTF8).
                        if (!allowUnpairedSurrogates && isSurrogateChar(c)) {
                            c = replacementCharacter.code
                        }
                        buffer[ptr] = (((c shr 12) and 0xF) or 0xE0).toByte()
                        buffer[ptr + 1] = (((c shr 6) and 0x3F) or 0x80).toByte()
                        buffer[ptr + 2] = ((c and 0x3F) or 0x80).toByte()
                        ptr += 3
                    }
                }
            }
        }
    }

    /** Counts UTF-8 bytes for the whole string. Returns (byteCount, charsConsumed). */
    fun getUTF8ByteCount(str: String): Pair<Int, Int> = getUTF8ByteCount(str, 0, str.length, Int.MAX_VALUE)

    /**
     * Counts UTF-8 bytes for at most [charCount] chars starting at [charIndex],
     * stopping before exceeding [byteLimit].
     * Returns the byte count and the number of chars consumed.
     *
     * Port of `GetUTF8ByteCount(char*, int, int, out char*)`.
     */
    fun getUTF8ByteCount(str: String, charIndex: Int, charCount: Int, byteLimit: Int = Int.MAX_VALUE): Pair<Int, Int> {
        val end = charIndex + charCount
        var i = charIndex
        var byteCount = 0
        while (i < end) {
            val characterSize: Int
            val c = str[i++].code
            when {
                c < 0x80 -> characterSize = 1
                c < 0x800 -> characterSize = 2
                isHighSurrogateChar(c) && i < end && isLowSurrogateChar(str[i].code) -> {
                    // surrogate pair:
                    characterSize = 4
                    i++
                }
                else -> characterSize = 3
            }

            if (byteCount + characterSize > byteLimit) {
                i -= if (characterSize < 4) 1 else 2
                break
            }

            byteCount += characterSize
        }

        return byteCount to (i - charIndex)
    }

    fun isSurrogateChar(c: Int): Boolean = (c - 0xD800).toUInt() <= (0xDFFF - 0xD800).toUInt()

    fun isHighSurrogateChar(c: Int): Boolean = (c - 0xD800).toUInt() <= (0xDBFF - 0xD800).toUInt()

    fun isLowSurrogateChar(c: Int): Boolean = (c - 0xDC00).toUInt() <= (0xDFFF - 0xDC00).toUInt()

    fun validateRange(bufferLength: Int, start: Int, byteCount: Int, byteCountParameterName: String) {
        require(start >= 0 && start <= bufferLength) { "start out of range" }
        require(byteCount >= 0 && byteCount <= bufferLength - start) { "$byteCountParameterName out of range" }
    }

    fun getUserStringByteLength(characterCount: Int): Int = characterCount * 2 + 1

    /**
     * ECMA-335 II.24.2.4:
     * This final byte holds the value 1 if and only if any UTF16 character within
     * the string has any bit set in its top byte, or its low byte is any of the following:
     * 0x01-0x08, 0x0E-0x1F, 0x27, 0x2D, 0x7F. Otherwise, it holds 0.
     * The 1 signifies Unicode characters that require handling beyond that normally
     * provided for 8-bit encoding sets.
     */
    fun getUserStringTrailingByte(str: String): Byte {
        for (ch in str) {
            if (ch.code >= 0x7F) {
                return 1
            }

            when (ch.code) {
                in 0x1..0x8, in 0xE..0x1F, 0x27, 0x2D -> return 1
            }
        }

        return 0
    }
}
