// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (BlobWriterImpl.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviation: WriteConstant supports primitive constant types only;
// enum constants must be unwrapped by the caller to their underlying
// primitive value.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

internal object BlobWriterImpl {
    const val SINGLE_BYTE_COMPRESSED_INTEGER_MAX_VALUE: Int = 0x7f
    const val TWO_BYTE_COMPRESSED_INTEGER_MAX_VALUE: Int = 0x3fff
    const val MAX_COMPRESSED_INTEGER_VALUE: Int = 0x1fffffff
    const val MIN_SIGNED_COMPRESSED_INTEGER_VALUE: Int = 0xF0000000.toInt()
    const val MAX_SIGNED_COMPRESSED_INTEGER_VALUE: Int = 0x0FFFFFFF

    fun getCompressedIntegerSize(value: Int): Int {
        assert(value <= MAX_COMPRESSED_INTEGER_VALUE)

        return when {
            value <= SINGLE_BYTE_COMPRESSED_INTEGER_MAX_VALUE -> 1
            value <= TWO_BYTE_COMPRESSED_INTEGER_MAX_VALUE -> 2
            else -> 4
        }
    }

    fun writeCompressedInteger(writer: BlobBuilder, value: UInt) {
        when {
            value <= SINGLE_BYTE_COMPRESSED_INTEGER_MAX_VALUE.toUInt() ->
                writer.writeByte(value.toByte())
            value <= TWO_BYTE_COMPRESSED_INTEGER_MAX_VALUE.toUInt() ->
                writer.writeUInt16BE((0x8000u or value).toUShort())
            value <= MAX_COMPRESSED_INTEGER_VALUE.toUInt() ->
                writer.writeUInt32BE(0xc0000000u or value)
            else -> throw IllegalArgumentException("value is too large for a compressed integer")
        }
    }

    fun writeCompressedSignedInteger(writer: BlobBuilder, value: Int) {
        val b6 = (1 shl 6) - 1
        val b13 = (1 shl 13) - 1
        val b28 = (1 shl 28) - 1

        // 0xffffffff for negative value, 0x00000000 for non-negative
        val signMask = value shr 31

        when {
            (value and b6.inv()) == (signMask and b6.inv()) -> {
                val n = ((value and b6) shl 1) or (signMask and 1)
                writer.writeByte(n.toByte())
            }
            (value and b13.inv()) == (signMask and b13.inv()) -> {
                val n = ((value and b13) shl 1) or (signMask and 1)
                writer.writeUInt16BE((0x8000 or n).toUShort())
            }
            (value and b28.inv()) == (signMask and b28.inv()) -> {
                val n = ((value and b28) shl 1) or (signMask and 1)
                writer.writeUInt32BE(0xc0000000u or n.toUInt())
            }
            else -> throw IllegalArgumentException("value cannot be represented as a compressed signed integer")
        }
    }

    /**
     * Writes a constant value (see ECMA-335 Partition II section 22.9).
     * Null encodes the nullref value for FieldInit.
     */
    fun writeConstant(builder: BlobBuilder, value: Any?) {
        with(builder) {
            writeConstant(value)
        }
    }
}
