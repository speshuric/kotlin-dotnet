// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (BlobBuilder.cs, BlobBuilder.Enumerators.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations (analysis/03 §6):
//  - Stream-based members are cut (WriteContentTo(Stream), TryWriteBytes);
//  - ImmutableArray members are cut (use [toArray]);
//  - WriteDateTime/WriteDecimal are cut: no DateTime/decimal analogs
//    in pure Kotlin stdlib yet (see BlobUtilities.SIZE_OF_SERIALIZED_DECIMAL).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.internal.BlobUtilities
import org.kotlindotnet.dotnetutils.system.reflection.internal.BitArithmetic
import kotlin.uuid.Uuid

/**
 * The implementation is akin to StringBuilder.
 *
 * The differences:
 * - BlobBuilder allows efficient sequential write of the built content;
 * - BlobBuilder allows for chunk allocation customization
 *   (override [allocateChunk]).
 */
open class BlobBuilder(capacity: Int = DEFAULT_CHUNK_SIZE) {
    // Builders are linked like so:
    //
    // [1:first]->[2]->[3:last]<-[4:head]
    //     ^_______________|
    //
    // In this case the content represented is a sequence (1,2,3,4).
    // This structure optimizes for append write operations and sequential
    // enumeration from the start of the chain. Data can only be written to
    // the head node. Other nodes are "frozen".
    private var nextOrPrevious: BlobBuilder = this

    private val firstChunk: BlobBuilder
        get() = nextOrPrevious.nextOrPrevious

    // The sum of lengths of all preceding chunks (not including the current chunk),
    // or a difference between original buffer length of a builder that was linked
    // as a suffix to another builder, and the current length of the buffer
    // (note that the buffers are swapped when suffix linking).
    private var previousLengthOrFrozenSuffixLengthDelta: Int = 0

    private var buffer: ByteArray = ByteArray(maxOf(MIN_CHUNK_SIZE, capacity))

    // The length of data in the buffer in lower 31 bits.
    // Head: highest bit is 0, length may be 0.
    // Non-head: highest bit is 1, lower 31 bits are not all 0.
    private var lengthFlags: UInt = 0u

    internal val isHead: Boolean
        get() = (lengthFlags and IS_FROZEN_MASK) == 0u

    private val length: Int
        get() = (lengthFlags and IS_FROZEN_MASK.inv()).toInt()

    private val frozenLength: UInt
        get() = lengthFlags or IS_FROZEN_MASK

    init {
        require(capacity >= 0) { "capacity out of range" }
    }

    protected open fun allocateChunk(minimalSize: Int): BlobBuilder =
        BlobBuilder(maxOf(buffer.size, minimalSize))

    protected open fun freeChunk() {
        // nop
    }

    fun clear() {
        check(isHead) { "builder is already linked" }

        // Swap buffer with the first chunk.
        val first = firstChunk
        if (first !== this) {
            val firstBuffer = first.buffer
            first.lengthFlags = frozenLength
            first.buffer = buffer
            buffer = firstBuffer
        }

        for (chunk in getChunks()) {
            if (chunk !== this) {
                chunk.clearAndFreeChunk()
            }
        }

        clearChunk()
    }

    protected fun free() {
        clear()
        freeChunk()
    }

    internal fun clearChunk() {
        lengthFlags = 0u
        previousLengthOrFrozenSuffixLengthDelta = 0
        nextOrPrevious = this
    }

    val count: Int
        get() = previousLengthOrFrozenSuffixLengthDelta + length

    private var previousLength: Int
        get() {
            assert(isHead)
            return previousLengthOrFrozenSuffixLengthDelta
        }
        set(value) {
            assert(isHead)
            previousLengthOrFrozenSuffixLengthDelta = value
        }

    protected val freeBytes: Int
        get() = buffer.size - length

    // internal for testing
    protected val chunkCapacity: Int
        get() = buffer.size

    // internal for testing
    internal fun getChunks(): Iterable<BlobBuilder> {
        check(isHead) { "builder is already linked" }
        return Iterable {
            object : Iterator<BlobBuilder> {
                val e = ChunksEnumerator.of(this@BlobBuilder)
                var consumed: Boolean = false

                override fun hasNext(): Boolean {
                    if (!consumed) consumed = e.moveNext()
                    return consumed
                }

                override fun next(): BlobBuilder {
                    if (!hasNext()) throw NoSuchElementException()
                    consumed = false
                    return e.current
                }
            }
        }
    }

    /** Returns a sequence of all blobs that represent the content of the builder. */
    fun getBlobs(): Iterable<Blob> {
        check(isHead) { "builder is already linked" }
        return Iterable { BlobsIterator(this) }
    }

    /**
     * Compares the current content of this builder with another one.
     */
    fun contentEquals(other: BlobBuilder?): Boolean {
        check(isHead) { "builder is already linked" }

        if (this === other) return true
        if (other == null) return false

        check(other.isHead) { "builder is already linked" }
        if (count != other.count) return false

        var leftStart = 0
        var rightStart = 0
        val leftEnumerator = ChunksEnumerator.of(this)
        val rightEnumerator = ChunksEnumerator.of(other)
        var leftContinues = leftEnumerator.moveNext()
        var rightContinues = rightEnumerator.moveNext()

        while (leftContinues && rightContinues) {
            val left = leftEnumerator.current
            val right = rightEnumerator.current

            // one of the enumerators always stays on its chunk boundary
            // (port of the original Debug.Assert)
            assert(leftStart == 0 || rightStart == 0)

            val minLength = minOf(left.length - leftStart, right.length - rightStart)
            for (i in 0 until minLength) {
                if (left.buffer[leftStart + i] != right.buffer[rightStart + i]) {
                    return false
                }
            }

            leftStart += minLength
            rightStart += minLength

            if (leftStart == left.length) {
                leftContinues = leftEnumerator.moveNext()
                leftStart = 0
            }

            if (rightStart == right.length) {
                rightContinues = rightEnumerator.moveNext()
                rightStart = 0
            }
        }

        return leftContinues == rightContinues
    }

    fun toArray(): ByteArray = toArray(0, count)

    fun toArray(start: Int, byteCount: Int): ByteArray {
        BlobUtilities.validateRange(count, start, byteCount, "byteCount")

        val result = ByteArray(byteCount)
        var chunkStart = 0
        var bufferStart = start
        val bufferEnd = start + byteCount
        for (chunk in getChunks()) {
            val chunkEnd = chunkStart + chunk.length
            assert(bufferStart >= chunkStart)

            if (chunkEnd > bufferStart) {
                val bytesToCopy = minOf(bufferEnd, chunkEnd) - bufferStart
                assert(bytesToCopy >= 0)
                chunk.buffer.copyInto(result, bufferStart - start, bufferStart - chunkStart, bufferStart - chunkStart + bytesToCopy)
                bufferStart += bytesToCopy
                if (bufferStart == bufferEnd) break
            }

            chunkStart = chunkEnd
        }

        assert(bufferStart == bufferEnd)
        return result
    }

    fun writeContentTo(destination: BlobWriter) {
        check(!destination.isDefault) { "destination is default" }
        for (chunk in getChunks()) {
            destination.writeBytesUnchecked(buffer, 0, chunk.length)
        }
    }

    fun writeContentTo(destination: BlobBuilder) {
        for (chunk in getChunks()) {
            destination.writeBytes(chunk.buffer, 0, chunk.length)
        }
    }

    fun linkPrefix(prefix: BlobBuilder) {
        check(prefix.isHead && isHead) { "builder is already linked" }

        // avoid chaining empty chunks:
        if (prefix.count == 0) {
            prefix.clearAndFreeChunk()
            return
        }

        previousLength += prefix.count

        // prefix is not a head anymore:
        prefix.lengthFlags = prefix.frozenLength

        val first = firstChunk
        val prefixFirst = prefix.firstChunk
        val last = nextOrPrevious
        val prefixLast = prefix.nextOrPrevious

        nextOrPrevious = if (last !== this) last else prefix
        prefix.nextOrPrevious =
            if (first !== this) first else if (prefixFirst !== prefix) prefixFirst else prefix

        if (last !== this) {
            last.nextOrPrevious = if (prefixFirst !== prefix) prefixFirst else prefix
        }

        if (prefixLast !== prefix) {
            prefixLast.nextOrPrevious = prefix
        }

        assert(checkInvariants())
    }

    fun linkSuffix(suffix: BlobBuilder) {
        check(isHead && suffix.isHead) { "builder is already linked" }

        // avoid chaining empty chunks:
        if (suffix.count == 0) {
            suffix.clearAndFreeChunk()
            return
        }

        val isEmpty = count == 0

        // swap buffers of the heads:
        val suffixBuffer = suffix.buffer
        val suffixLengthFlags = suffix.lengthFlags
        val suffixPreviousLength = suffix.previousLength
        val oldSuffixLength = suffix.length
        suffix.buffer = buffer
        suffix.lengthFlags = frozenLength // suffix is not a head anymore
        buffer = suffixBuffer
        lengthFlags = suffixLengthFlags

        previousLength += suffix.length + suffixPreviousLength

        // Update the previous length of the suffix so that
        // suffix.count = suffix.previousLength + suffix.length doesn't change.
        // Note that the resulting previous length might be negative.
        // The value is not used, other than for calculating Count.
        suffix.previousLengthOrFrozenSuffixLengthDelta = suffixPreviousLength + oldSuffixLength - suffix.length

        if (!isEmpty) {
            val first = firstChunk
            val suffixFirst = suffix.firstChunk
            val last = nextOrPrevious
            val suffixLast = suffix.nextOrPrevious

            nextOrPrevious = suffixLast
            suffix.nextOrPrevious =
                if (suffixFirst !== suffix) suffixFirst else if (first !== this) first else suffix

            if (last !== this) {
                last.nextOrPrevious = suffix
            }

            if (suffixLast !== suffix) {
                suffixLast.nextOrPrevious = if (first !== this) first else suffix
            }
        }
    }

    private fun addLength(value: Int) {
        lengthFlags += value.toUInt()
    }

    /**
     * Port of the DEBUG-only `CheckInvariants`. Evaluated only when JVM
     * assertions are enabled (`-ea`); returns true so it can be used
     * inside [assert].
     */
    private fun checkInvariants(): Boolean {
        if (length < 0 || length > buffer.size) return false

        if (!isHead) return true

        // head chunk: sum of all chunk lengths must equal Count
        if (previousLengthOrFrozenSuffixLengthDelta < 0) return false
        var total = 0
        for (chunk in getChunks()) {
            if (!(chunk.isHead || chunk.length > 0)) return false
            total += chunk.length
        }
        return total == count
    }

    private fun expand(newLength: Int) {
        // May happen only if the derived class attempts to write to a builder
        // that is not last, or if a builder prepended to another one is not discarded.
        check(isHead) { "builder is already linked" }

        val newChunk = allocateChunk(maxOf(newLength, MIN_CHUNK_SIZE))
        check(newChunk.chunkCapacity >= newLength) {
            "the overridden allocator did not provide large enough buffer"
        }

        val newBuffer = newChunk.buffer

        if (lengthFlags == 0u) {
            // If the first write into an empty buffer needs more space than the buffer provides, swap the buffers.
            newChunk.buffer = buffer
            buffer = newBuffer
        } else {
            // Otherwise append the new buffer.
            val last = nextOrPrevious
            val first = firstChunk

            if (last === this) {
                // single chunk in the chain
                nextOrPrevious = newChunk
            } else {
                newChunk.nextOrPrevious = first
                last.nextOrPrevious = newChunk
                nextOrPrevious = newChunk
            }

            newChunk.buffer = buffer
            newChunk.lengthFlags = frozenLength
            newChunk.previousLengthOrFrozenSuffixLengthDelta = previousLength

            buffer = newBuffer
            previousLength += length
            lengthFlags = 0u
        }

        assert(checkInvariants())
    }

    /**
     * Reserves a contiguous block of bytes.
     */
    fun reserveBytes(byteCount: Int): Blob {
        require(byteCount >= 0) { "byteCount out of range" }
        val start = reserveBytesImpl(byteCount)
        return Blob(buffer, start, byteCount)
    }

    private fun reserveBytesImpl(byteCount: Int): Int {
        assert(byteCount >= 0)

        // If write is attempted to a frozen builder we fall back
        // to expand where an exception is thrown.
        // Note: the comparison is done in Long to replicate C# uint/int
        // comparison semantics when buffer.size - byteCount is negative.
        var result = lengthFlags
        if (result.toLong() > buffer.size - byteCount) {
            expand(byteCount)
            result = 0u
        }

        lengthFlags = result + byteCount.toUInt()
        return result.toInt()
    }

    private fun reserveBytesPrimitive(byteCount: Int): Int {
        assert(byteCount <= MIN_CHUNK_SIZE)
        return reserveBytesImpl(byteCount)
    }
    fun writeBytes(value: Byte, byteCount: Int) {
        require(byteCount >= 0) { "byteCount out of range" }
        check(isHead) { "builder is already linked" }

        val bytesToCurrent = minOf(freeBytes, byteCount)
        buffer.fill(value, length, length + bytesToCurrent)
        addLength(bytesToCurrent)

        val remaining = byteCount - bytesToCurrent
        if (remaining > 0) {
            expand(remaining)
            buffer.fill(value, 0, remaining)
            addLength(remaining)
        }
    }

    internal fun writeBytesUnchecked(sourceBuffer: ByteArray, sourceStart: Int, byteCount: Int) {
        val bytesToCurrent = minOf(freeBytes, byteCount)
        sourceBuffer.copyInto(buffer, length, sourceStart, sourceStart + bytesToCurrent)
        addLength(bytesToCurrent)

        val remaining = byteCount - bytesToCurrent
        if (remaining > 0) {
            expand(remaining)
            sourceBuffer.copyInto(buffer, 0, sourceStart + bytesToCurrent, sourceStart + byteCount)
            addLength(remaining)
        }
    }

    fun writeBytes(sourceBuffer: ByteArray, start: Int, byteCount: Int) {
        BlobUtilities.validateRange(sourceBuffer.size, start, byteCount, "byteCount")
        check(isHead) { "builder is already linked" }
        writeBytesUnchecked(sourceBuffer, start, byteCount)
    }

    fun writeBytes(sourceBuffer: ByteArray) {
        writeBytes(sourceBuffer, 0, sourceBuffer.size)
    }

    fun padTo(position: Int) {
        writeZeroBytes(position - count)
    }

    fun align(alignment: Int) {
        val position = count
        writeZeroBytes(BitArithmetic.align(position, alignment) - position)
    }

    private fun writeZeroBytes(byteCount: Int) {
        require(byteCount >= 0) { "byteCount out of range" }
        check(isHead) { "builder is already linked" }

        val bytesToCurrent = minOf(freeBytes, byteCount)
        buffer.fill(0, length, length + bytesToCurrent)
        addLength(bytesToCurrent)

        val remaining = byteCount - bytesToCurrent
        if (remaining > 0) {
            expand(remaining)
            buffer.fill(0, 0, remaining)
            addLength(remaining)
        }
    }

    fun writeBoolean(value: Boolean) {
        writeByte(if (value) 1 else 0)
    }

    fun writeByte(value: Byte) {
        val start = reserveBytesPrimitive(1)
        buffer[start] = value
    }

    fun writeSByte(value: Byte) {
        writeByte(value)
    }

    fun writeDouble(value: Double) {
        val start = reserveBytesPrimitive(8)
        BlobUtilities.writeDouble(buffer, start, value)
    }

    fun writeSingle(value: Float) {
        val start = reserveBytesPrimitive(4)
        BlobUtilities.writeSingle(buffer, start, value)
    }

    fun writeInt16(value: Short) {
        writeUInt16(value.toUShort())
    }

    fun writeUInt16(value: UShort) {
        val start = reserveBytesPrimitive(2)
        BlobUtilities.writeUInt16(buffer, start, value)
    }

    fun writeInt16BE(value: Short) {
        writeUInt16BE(value.toUShort())
    }

    fun writeUInt16BE(value: UShort) {
        val start = reserveBytesPrimitive(2)
        BlobUtilities.writeUInt16BE(buffer, start, value)
    }

    fun writeInt32BE(value: Int) {
        writeUInt32BE(value.toUInt())
    }

    fun writeUInt32BE(value: UInt) {
        val start = reserveBytesPrimitive(4)
        BlobUtilities.writeUInt32BE(buffer, start, value)
    }

    fun writeInt32(value: Int) {
        writeUInt32(value.toUInt())
    }

    fun writeUInt32(value: UInt) {
        val start = reserveBytesPrimitive(4)
        BlobUtilities.writeUInt32(buffer, start, value)
    }

    fun writeInt64(value: Long) {
        writeUInt64(value.toULong())
    }

    fun writeUInt64(value: ULong) {
        val start = reserveBytesPrimitive(8)
        BlobUtilities.writeUInt64(buffer, start, value)
    }

    fun writeGuid(value: Uuid) {
        val start = reserveBytesPrimitive(BlobUtilities.SIZE_OF_GUID)
        BlobUtilities.writeGuid(buffer, start, value)
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
        check(isHead) { "builder is already linked" }
        for (c in value) {
            writeUInt16(c.code.toUShort())
        }
    }

    /** Writes UTF-16 (little-endian) encoded chars at the current position. */
    fun writeUTF16(value: CharArray) {
        check(isHead) { "builder is already linked" }
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
    fun writeSerializedString(value: String?) {
        if (value == null) {
            writeByte(0xff.toByte())
            return
        }

        writeUTF8Internal(value, 0, value.length, allowUnpairedSurrogates = true, prependSize = true)
    }

    /**
     * Writes string in User String (#US) heap format (see ECMA-335-II 24.2.4):
     * UTF-16 encoded, prefixed by its size in bytes and followed by the trailing byte.
     */
    fun writeUserString(value: String) {
        writeCompressedInteger(BlobUtilities.getUserStringByteLength(value.length))
        writeUTF16(value)
        writeByte(BlobUtilities.getUserStringTrailingByte(value))
    }

    /**
     * Writes UTF-8 encoded string at the current position.
     *
     * @param allowUnpairedSurrogates True to encode unpaired surrogates as specified,
     * otherwise replace them with U+FFFD character.
     */
    fun writeUTF8(value: String, allowUnpairedSurrogates: Boolean = true) {
        writeUTF8Internal(value, 0, value.length, allowUnpairedSurrogates, prependSize = false)
    }

    private fun writeUTF8Internal(str: String, start: Int, charLength: Int, allowUnpairedSurrogates: Boolean, prependSize: Boolean) {
        assert(start >= 0)
        assert(charLength >= 0)
        assert(start + charLength <= str.length)
        check(isHead) { "builder is already linked" }

        // the max size of compressed int is 4B:
        val byteLimit = freeBytes - if (prependSize) 4 else 0

        val (bytesToCurrent, charsToCurrent) =
            BlobUtilities.getUTF8ByteCount(str, start, charLength, byteLimit)
        val charsToNext = charLength - charsToCurrent
        val nextIndex = start + charsToCurrent
        val bytesToNext = BlobUtilities.getUTF8ByteCount(str, nextIndex, charsToNext).first

        if (prependSize) {
            writeCompressedInteger(bytesToCurrent + bytesToNext)
        }

        BlobUtilities.writeUTF8(buffer, length, str, start, charsToCurrent, bytesToCurrent, allowUnpairedSurrogates)
        addLength(bytesToCurrent)

        if (bytesToNext > 0) {
            expand(bytesToNext)
            BlobUtilities.writeUTF8(buffer, 0, str, nextIndex, charsToNext, bytesToNext, allowUnpairedSurrogates)
            addLength(bytesToNext)
        }
    }

    /**
     * Implements compressed signed integer encoding as defined by ECMA-335-II chapter 23.2.
     */
    fun writeCompressedSignedInteger(value: Int) {
        BlobWriterImpl.writeCompressedSignedInteger(this, value)
    }

    /**
     * Implements compressed unsigned integer encoding as defined by ECMA-335-II chapter 23.2.
     */
    fun writeCompressedInteger(value: Int) {
        BlobWriterImpl.writeCompressedInteger(this, value.toUInt())
    }

    /**
     * Writes a constant value (see ECMA-335 Partition II section 22.9) at the current position.
     */
    fun writeConstant(value: Any?) {
        BlobWriterImpl.writeConstant(this, value)
    }

    private fun clearAndFreeChunk() {
        clearChunk()
        freeChunk()
    }

    /**
     * Port of the C# `Chunks` struct-enumerator: unlike kotlin [Iterator],
     * [current] remains valid (and unchanged) between [moveNext] calls while
     * a chunk is being consumed partially — this is what `ContentEquals`
     * relies on.
     */
    private class ChunksEnumerator private constructor(
        private val head: BlobBuilder,
    ) {
        private var next: BlobBuilder = head.firstChunk
        private var currentOpt: BlobBuilder? = null

        val current: BlobBuilder
            get() = currentOpt!!

        fun moveNext(): Boolean {
            if (currentOpt === head) return false
            if (currentOpt === head.nextOrPrevious) {
                currentOpt = head
            } else {
                currentOpt = next
                next = next.nextOrPrevious
            }
            return true
        }

        companion object {
            internal fun of(builder: BlobBuilder): ChunksEnumerator {
                check(builder.isHead) { "builder is already linked" }
                return ChunksEnumerator(builder)
            }
        }
    }

    private inner class BlobsIterator(builder: BlobBuilder) : Iterator<Blob> {
        private val chunks = ChunksEnumerator.of(builder)
        private var consumed: Boolean = false

        override fun hasNext(): Boolean {
            if (!consumed) consumed = chunks.moveNext()
            return consumed
        }

        override fun next(): Blob {
            if (!hasNext()) throw NoSuchElementException()
            consumed = false
            val current = chunks.current
            return Blob(current.buffer, 0, current.length)
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE: Int = 256

        // Must be at least the size of the largest primitive type we write atomically (Guid).
        const val MIN_CHUNK_SIZE: Int = 16

        private val IS_FROZEN_MASK: UInt = 0x80000000u
    }
}
