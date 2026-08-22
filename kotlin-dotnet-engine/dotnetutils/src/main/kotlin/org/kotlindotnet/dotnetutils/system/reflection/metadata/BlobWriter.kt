// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (BlobWriter.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations (analysis/03 §6):
//  - Stream-based member is cut (WriteBytes(Stream, int));
//  - ImmutableArray members are cut (use ByteArray overloads);
//  - WriteDateTime is cut (debug-directory scope).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.internal.BlobUtilities
import org.kotlindotnet.dotnetutils.system.reflection.internal.BitArithmetic
import kotlin.uuid.Uuid

private const val SINGLE_BYTE_MAX = 0x7f
private const val TWO_BYTE_MAX = 0x3fff
private const val MAX_COMPRESSED_INTEGER_VALUE = 0x1fffffff

class BlobWriter {
    // writable slice:
    private val buffer: ByteArray?
    private val start: Int
    private val end: Int // exclusive

    // position in buffer relative to the beginning of the array:
    private var position: Int

    constructor(size: Int) : this(ByteArray(size))

    constructor(buffer: ByteArray) : this(buffer, 0, buffer.size)

    constructor(blob: Blob) {
        val b = requireNotNull(blob.buffer) { "default blob" }
        buffer = b
        start = blob.start
        end = blob.start + blob.length
        position = start
    }

    constructor(buffer: ByteArray, start: Int, count: Int) {
        assert(count >= 0)
        assert(count <= buffer.size - start)
        this.buffer = buffer
        this.start = start
        position = start
        end = start + count
    }

    internal val isDefault: Boolean
        get() = buffer == null

    /**
     * Compares the current content of this writer with another one.
     */
    fun contentEquals(other: BlobWriter): Boolean {
        val len = length
        if (len != other.length) return false
        val buf = checkNotNull(buffer)
        val otherBuf = checkNotNull(other.buffer)
        for (i in 0 until len) {
            if (buf[start + i] != otherBuf[other.start + i]) return false
        }
        return true
    }

    var offset: Int
        get() = position - start
        set(value) {
            if (value < 0 || start > end - value) {
                throw IllegalArgumentException("value out of range")
            }
            position = start + value
        }

    val length: Int
        get() = end - start

    val remainingBytes: Int
        get() = end - position

    val blob: Blob
        get() = Blob(checkNotNull(buffer), start, length)

    fun toArray(): ByteArray = toArray(0, offset)

    fun toArray(start: Int, byteCount: Int): ByteArray {
        val buf = checkNotNull(buffer)
        BlobUtilities.validateRange(length, start, byteCount, "byteCount")
        return buf.copyOfRange(this.start + start, this.start + start + byteCount)
    }

    private fun advance(value: Int): Int {
        assert(value >= 0)
        val pos = position
        if (pos > end - value) {
            throw IndexOutOfBoundsException("blob writer out of bounds")
        }
        position = pos + value
        return pos
    }

    fun writeBytes(value: Byte, byteCount: Int) {
        require(byteCount >= 0) { "byteCount out of range" }
        val pos = advance(byteCount)
        checkNotNull(buffer).fill(value, pos, pos + byteCount)
    }

    internal fun writeBytesUnchecked(sourceBuffer: ByteArray, sourceStart: Int, byteCount: Int) {
        val pos = advance(byteCount)
        sourceBuffer.copyInto(checkNotNull(buffer), pos, sourceStart, sourceStart + byteCount)
    }

    fun writeBytes(sourceBuffer: ByteArray, start: Int, byteCount: Int) {
        BlobUtilities.validateRange(sourceBuffer.size, start, byteCount, "byteCount")
        writeBytesUnchecked(sourceBuffer, start, byteCount)
    }

    fun writeBytes(sourceBuffer: ByteArray) {
        writeBytes(sourceBuffer, 0, sourceBuffer.size)
    }

    fun writeBytes(source: BlobBuilder) {
        source.writeContentTo(this)
    }

    fun padTo(offsetValue: Int) {
        writeZeroBytes(offsetValue - offset)
    }

    fun align(alignment: Int) {
        val currentOffset = offset
        writeZeroBytes(BitArithmetic.align(currentOffset, alignment) - currentOffset)
    }

    private fun writeZeroBytes(byteCount: Int) {
        require(byteCount >= 0) { "byteCount out of range" }
        val pos = advance(byteCount)
        checkNotNull(buffer).fill(0, pos, pos + byteCount)
    }

    fun writeBoolean(value: Boolean) {
        writeByte(if (value) 1 else 0)
    }

    fun writeByte(value: Byte) {
        val pos = advance(1)
        checkNotNull(buffer)[pos] = value
    }

    fun writeSByte(value: Byte) {
        writeByte(value)
    }

    fun writeDouble(value: Double) {
        val pos = advance(8)
        BlobUtilities.writeDouble(checkNotNull(buffer), pos, value)
    }

    fun writeSingle(value: Float) {
        val pos = advance(4)
        BlobUtilities.writeSingle(checkNotNull(buffer), pos, value)
    }

    fun writeInt16(value: Short) {
        writeUInt16(value.toUShort())
    }

    fun writeUInt16(value: UShort) {
        val pos = advance(2)
        BlobUtilities.writeUInt16(checkNotNull(buffer), pos, value)
    }

    fun writeInt16BE(value: Short) {
        writeUInt16BE(value.toUShort())
    }

    fun writeUInt16BE(value: UShort) {
        val pos = advance(2)
        BlobUtilities.writeUInt16BE(checkNotNull(buffer), pos, value)
    }

    fun writeInt32BE(value: Int) {
        writeUInt32BE(value.toUInt())
    }

    fun writeUInt32BE(value: UInt) {
        val pos = advance(4)
        BlobUtilities.writeUInt32BE(checkNotNull(buffer), pos, value)
    }

    fun writeInt32(value: Int) {
        writeUInt32(value.toUInt())
    }

    fun writeUInt32(value: UInt) {
        val pos = advance(4)
        BlobUtilities.writeUInt32(checkNotNull(buffer), pos, value)
    }

    fun writeInt64(value: Long) {
        writeUInt64(value.toULong())
    }

    fun writeUInt64(value: ULong) {
        val pos = advance(8)
        BlobUtilities.writeUInt64(checkNotNull(buffer), pos, value)
    }

    fun writeGuid(value: Uuid) {
        val pos = advance(BlobUtilities.SIZE_OF_GUID)
        BlobUtilities.writeGuid(checkNotNull(buffer), pos, value)
    }

    /**
     * Writes a reference to a heap (heap offset) or a table (row number).
     *
     * @param reference Heap offset or table row number.
     * @param isSmall True to encode the reference as 16-bit integer, false as 32-bit.
     */
    fun writeReference(reference: Int, isSmall: Boolean) {
        // This code is a very hot path, hence we don't check that the reference fits 2B.
        if (isSmall) {
            writeUInt16(reference.toUShort())
        } else {
            writeInt32(reference)
        }
    }

    /** Writes UTF-16 (little-endian) encoded string at the current position. */
    fun writeUTF16(value: String) {
        for (c in value) {
            writeUInt16(c.code.toUShort())
        }
    }

    /** Writes UTF-16 (little-endian) encoded chars at the current position. */
    fun writeUTF16(value: CharArray) {
        for (c in value) {
            writeUInt16(c.code.toUShort())
        }
    }

    /**
     * Writes string in SerString format (see ECMA-335-II 23.3 Custom attributes).
     *
     * The string is UTF-8 encoded and prefixed by its size in bytes.
     * Null string is represented as a single byte 0xFF.
     */
    fun writeSerializedString(str: String?) {
        if (str == null) {
            writeByte(0xff.toByte())
            return
        }
        writeUTF8Internal(str, allowUnpairedSurrogates = true, prependSize = true)
    }

    /**
     * Writes string in User String (#US) heap format (see ECMA-335-II 24.2.4).
     */
    fun writeUserString(value: String) {
        writeCompressedInteger(BlobUtilities.getUserStringByteLength(value.length))
        writeUTF16(value)
        writeByte(BlobUtilities.getUserStringTrailingByte(value))
    }

    /**
     * Writes UTF-8 encoded string at the current position.
     */
    fun writeUTF8(value: String, allowUnpairedSurrogates: Boolean = true) {
        writeUTF8Internal(value, allowUnpairedSurrogates, prependSize = false)
    }

    private fun writeUTF8Internal(str: String, allowUnpairedSurrogates: Boolean, prependSize: Boolean) {
        val byteCount = BlobUtilities.getUTF8ByteCount(str).first
        if (prependSize) {
            writeCompressedInteger(byteCount)
        }
        val startOffset = advance(byteCount)
        BlobUtilities.writeUTF8(
            checkNotNull(buffer),
            startOffset,
            str,
            charIndex = 0,
            charCount = str.length,
            byteCount = byteCount,
            allowUnpairedSurrogates = allowUnpairedSurrogates,
        )
    }

    /**
     * Implements compressed signed integer encoding as defined by ECMA-335-II chapter 23.2.
     */
    fun writeCompressedSignedInteger(value: Int) {
        val b6 = (1 shl 6) - 1
        val b13 = (1 shl 13) - 1
        val b28 = (1 shl 28) - 1
        val signMask = value shr 31

        when {
            (value and b6.inv()) == (signMask and b6.inv()) -> {
                val n = ((value and b6) shl 1) or (signMask and 1)
                writeByte(n.toByte())
            }
            (value and b13.inv()) == (signMask and b13.inv()) -> {
                val n = ((value and b13) shl 1) or (signMask and 1)
                writeUInt16BE((0x8000 or n).toUShort())
            }
            (value and b28.inv()) == (signMask and b28.inv()) -> {
                val n = ((value and b28) shl 1) or (signMask and 1)
                writeUInt32BE(0xc0000000u or n.toUInt())
            }
            else -> throw IllegalArgumentException("value cannot be represented as a compressed signed integer")
        }
    }

    /**
     * Implements compressed unsigned integer encoding as defined by ECMA-335-II chapter 23.2.
     */
    fun writeCompressedInteger(value: Int) {
        val u = value.toUInt()
        when {
            u <= SINGLE_BYTE_MAX.toUInt() ->
                writeByte(u.toByte())
            u <= TWO_BYTE_MAX.toUInt() ->
                writeUInt16BE((0x8000u or u).toUShort())
            u <= MAX_COMPRESSED_INTEGER_VALUE.toUInt() ->
                writeUInt32BE(0xc0000000u or u)
            else -> throw IllegalArgumentException("value is too large for a compressed integer")
        }
    }

    /**
     * Writes a constant value (see ECMA-335 Partition II section 22.9) at the current position.
     */
    fun writeConstant(value: Any?) {
        when (value) {
            null -> writeUInt32(0u)
            is Boolean -> writeBoolean(value)
            is Int -> writeInt32(value)
            is String -> writeUTF16(value)
            is Byte -> writeByte(value)
            is Char -> writeUInt16(value.code.toUShort())
            is Double -> writeDouble(value)
            is Short -> writeInt16(value)
            is Long -> writeInt64(value)
            is Float -> writeSingle(value)
            is UShort -> writeUInt16(value)
            is UInt -> writeUInt32(value)
            is ULong -> writeUInt64(value)
            else -> throw IllegalArgumentException("invalid constant value of type ${value::class.qualifiedName}")
        }
    }

    fun clear() {
        position = start
    }

}

