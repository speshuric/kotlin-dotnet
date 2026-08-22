// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata
//   (Ecma335/Encoding/ExceptionRegionEncoder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ExceptionRegionKind
import org.kotlindotnet.dotnetutils.system.reflection.metadata.HandleKind

class ExceptionRegionEncoder internal constructor(
    val builder: BlobBuilder,
    val hasSmallFormat: Boolean,
) {
    companion object {
        private const val TABLE_HEADER_SIZE = 4

        private const val SMALL_REGION_SIZE =
            2 + // Flags
            2 + // TryOffset
            1 + // TryLength
            2 + // HandlerOffset
            1 + // HandlerLength
            4   // ClassToken | FilterOffset

        private const val FAT_REGION_SIZE =
            4 + 4 + 4 + 4 + 4 + 4

        private const val THREE_BYTES_MAX_VALUE = 0xffffff
        internal const val MAX_SMALL_EXCEPTION_REGIONS = (0xff - TABLE_HEADER_SIZE) / SMALL_REGION_SIZE // byte is unsigned in metadata
        internal const val MAX_EXCEPTION_REGIONS = (THREE_BYTES_MAX_VALUE - TABLE_HEADER_SIZE) / FAT_REGION_SIZE

        private const val EH_TABLE_FLAG = 0x01
        private const val FAT_FORMAT_FLAG = 0x40

        /** Returns true if the number of exception regions fits small format. */
        fun isSmallRegionCount(exceptionRegionCount: Int): Boolean =
            exceptionRegionCount.toUInt() <= MAX_SMALL_EXCEPTION_REGIONS.toUInt()

        /** Returns true if the region fits small format. */
        fun isSmallExceptionRegion(startOffset: Int, length: Int): Boolean =
            startOffset.toUInt() <= UShort.MAX_VALUE.toUInt() && length.toUInt() <= UByte.MAX_VALUE.toUInt()

        internal fun isSmallExceptionRegionFromBounds(startOffset: Int, endOffset: Int): Boolean =
            isSmallExceptionRegion(startOffset, endOffset - startOffset)

        internal fun getExceptionTableSize(exceptionRegionCount: Int, isSmallFormat: Boolean): Int =
            TABLE_HEADER_SIZE + exceptionRegionCount * (if (isSmallFormat) SMALL_REGION_SIZE else FAT_REGION_SIZE)

        internal fun isExceptionRegionCountInBounds(exceptionRegionCount: Int): Boolean =
            exceptionRegionCount.toUInt() <= MAX_EXCEPTION_REGIONS.toUInt()

        internal fun isValidCatchTypeHandle(catchType: EntityHandle): Boolean =
            !catchType.isNil &&
                (
                    catchType.kind == HandleKind.TYPE_DEFINITION ||
                        catchType.kind == HandleKind.TYPE_SPECIFICATION ||
                        catchType.kind == HandleKind.TYPE_REFERENCE
                    )

        internal fun serializeTableHeader(builder: BlobBuilder, exceptionRegionCount: Int, hasSmallRegions: Boolean): ExceptionRegionEncoder {
            assert(exceptionRegionCount > 0)
            require(exceptionRegionCount > 0)

            val hasSmallFormat = hasSmallRegions && isSmallRegionCount(exceptionRegionCount)
            val dataSize = getExceptionTableSize(exceptionRegionCount, hasSmallFormat)

            builder.align(4)
            if (hasSmallFormat) {
                builder.writeByte(EH_TABLE_FLAG.toByte())
                builder.writeByte(dataSize.toByte())
                builder.writeInt16(0)
            } else {
                assert(dataSize <= 0x00ffffff)
                builder.writeByte((EH_TABLE_FLAG or FAT_FORMAT_FLAG).toByte())
                builder.writeByte(dataSize.toByte())
                writerWriteUInt16High(builder, dataSize)
            }

            return ExceptionRegionEncoder(builder, hasSmallFormat)
        }

        private fun writerWriteUInt16High(builder: BlobBuilder, dataSize: Int) {
            builder.writeUInt16((dataSize shr 8).toUShort())
        }
    }

    /**
     * Adds a finally clause.
     */
    fun addFinally(tryOffset: Int, tryLength: Int, handlerOffset: Int, handlerLength: Int): ExceptionRegionEncoder =
        add(ExceptionRegionKind.FINALLY, tryOffset, tryLength, handlerOffset, handlerLength)

    /**
     * Adds a fault clause.
     */
    fun addFault(tryOffset: Int, tryLength: Int, handlerOffset: Int, handlerLength: Int): ExceptionRegionEncoder =
        add(ExceptionRegionKind.FAULT, tryOffset, tryLength, handlerOffset, handlerLength)

    /**
     * Adds a catch clause.
     */
    fun addCatch(
        tryOffset: Int,
        tryLength: Int,
        handlerOffset: Int,
        handlerLength: Int,
        catchType: EntityHandle,
    ): ExceptionRegionEncoder =
        add(ExceptionRegionKind.CATCH, tryOffset, tryLength, handlerOffset, handlerLength, catchType)

    /**
     * Adds a filter clause.
     */
    fun addFilter(
        tryOffset: Int,
        tryLength: Int,
        handlerOffset: Int,
        handlerLength: Int,
        filterOffset: Int,
    ): ExceptionRegionEncoder =
        add(ExceptionRegionKind.FILTER, tryOffset, tryLength, handlerOffset, handlerLength, filterOffset = filterOffset)

    /**
     * Adds an exception clause.
     *
     * @param catchType Type handle for [ExceptionRegionKind.CATCH], nil otherwise.
     * @param filterOffset Offset of the filter block for [ExceptionRegionKind.FILTER], 0 otherwise.
     */
    fun add(
        kind: ExceptionRegionKind,
        tryOffset: Int,
        tryLength: Int,
        handlerOffset: Int,
        handlerLength: Int,
        catchType: EntityHandle = EntityHandle(0u),
        filterOffset: Int = 0,
    ): ExceptionRegionEncoder {
        if (hasSmallFormat) {
            if (tryOffset.toUShort().toInt() != tryOffset) throw IllegalArgumentException("tryOffset out of range")
            if (tryLength and 0xFF != tryLength) throw IllegalArgumentException("tryLength out of range")
            if (handlerOffset.toUShort().toInt() != handlerOffset) throw IllegalArgumentException("handlerOffset out of range")
            if (handlerLength and 0xFF != handlerLength) throw IllegalArgumentException("handlerLength out of range")
        } else {
            require(tryOffset >= 0) { "tryOffset out of range" }
            require(tryLength >= 0) { "tryLength out of range" }
            require(handlerOffset >= 0) { "handlerOffset out of range" }
            require(handlerLength >= 0) { "handlerLength out of range" }
        }

        val catchTokenOrOffset: Int = when (kind) {
            ExceptionRegionKind.CATCH -> {
                check(isValidCatchTypeHandle(catchType)) { "unexpected catch type handle" }
                MetadataTokens.getToken(catchType)
            }
            ExceptionRegionKind.FILTER -> {
                require(filterOffset >= 0) { "filterOffset out of range" }
                filterOffset
            }
            ExceptionRegionKind.FINALLY, ExceptionRegionKind.FAULT -> 0
        }

        addUnchecked(kind, tryOffset, tryLength, handlerOffset, handlerLength, catchTokenOrOffset)
        return this
    }

    internal fun addUnchecked(
        kind: ExceptionRegionKind,
        tryOffset: Int,
        tryLength: Int,
        handlerOffset: Int,
        handlerLength: Int,
        catchTokenOrOffset: Int,
    ) {
        if (hasSmallFormat) {
            builder.writeUInt16(kind.value.toUShort())
            builder.writeUInt16(tryOffset.toUShort())
            builder.writeByte(tryLength.toByte())
            builder.writeUInt16(handlerOffset.toUShort())
            builder.writeByte(handlerLength.toByte())
        } else {
            builder.writeInt32(kind.value)
            builder.writeInt32(tryOffset)
            builder.writeInt32(tryLength)
            builder.writeInt32(handlerOffset)
            builder.writeInt32(handlerLength)
        }

        builder.writeInt32(catchTokenOrOffset)
    }
}
