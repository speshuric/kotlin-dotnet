package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

/**
 * Кодирование элементов portable PDB, которых нет в write-path SRM
 * (апстримные аналоги живут в Roslyn: SequencePointListEncoder и
 * кодирование имени Document). Compressed integers — ECMA-335 II.23.2.
 */
internal object PePdbEncoder {

    /** Одна запись sequence point; строки/колонки 1-based. */
    data class SequencePointRecord(
        val offset: Int,
        val startLine: Int,
        val startColumn: Int,
        val endLine: Int,
        val endColumn: Int,
    )

    /** Blob sequence points для single-document метода (только записи). */
    fun encodeSequencePoints(builder: BlobBuilder, points: List<SequencePointRecord>) {
        for (sp in points) {
            writeCompressedUnsigned(builder, sp.offset)
            writeCompressedSigned(builder, sp.startLine)
            writeCompressedUnsigned(builder, sp.startColumn)
            writeCompressedSigned(builder, sp.endLine)
            writeCompressedUnsigned(builder, sp.endColumn)
        }
    }

    /** Имя Document: blob с compressed-length префиксом + UTF8. */
    fun encodeDocumentName(builder: BlobBuilder, path: String) {
        val bytes = path.toByteArray(Charsets.UTF_8)
        writeCompressedUnsigned(builder, bytes.size)
        builder.writeBytes(bytes)
    }

    private fun writeCompressedUnsigned(b: BlobBuilder, value: Int) {
        require(value >= 0) { "negative unsigned compressed integer" }
        when {
            value <= 0x7F -> b.writeByte(value.toByte())
            value <= 0x3FFF -> b.writeUInt16BE((0x8000 or value).toUShort())
            value <= 0x1FFFFFFF -> b.writeUInt32BE(0xC0000000u or value.toUInt())
            else -> throw IllegalArgumentException("value too large for compressed integer")
        }
    }

    private fun writeCompressedSigned(b: BlobBuilder, value: Int) {
        val b6 = (1 shl 6) - 1
        val b13 = (1 shl 13) - 1
        val b28 = (1 shl 28) - 1
        val signMask = value shr 31
        when {
            (value and b6.inv()) == (signMask and b6.inv()) ->
                b.writeByte((((value and b6) shl 1) or (signMask and 1)).toByte())
            (value and b13.inv()) == (signMask and b13.inv()) ->
                b.writeUInt16BE((0x8000 or (((value and b13) shl 1) or (signMask and 1))).toUShort())
            (value and b28.inv()) == (signMask and b28.inv()) ->
                b.writeUInt32BE(0xC0000000u or (((value and b28) shl 1) or (signMask and 1)).toUInt())
            else -> throw IllegalArgumentException("value cannot be represented as compressed signed integer")
        }
    }
}
