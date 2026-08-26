package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder

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

    /**
     * Sequence points blob for a single-document method.
     *
     * Format mirrors the SRM reader (`SequencePointCollection.Enumerator`,
     * portable PDB spec): header `compressed(localSignatureRid = 0)`;
     * then per record: `compressed(offset)` (absolute for the first,
     * delta afterwards), `compressed(deltaLines)`, then
     * `compressed(deltaColumns)` unsigned when `deltaLines == 0`,
     * signed otherwise; for the first non-hidden record the absolute
     * `compressed(startLine)` / `compressed(startColumn)` follow, later
     * records use signed deltas from the previous start position.
     */
    fun encodeSequencePoints(builder: BlobBuilder, points: List<SequencePointRecord>) {
        // Several IR nodes may start at the same IL offset; the format
        // encodes a zero offset delta as a document switch, so only the
        // first point per offset is kept. Zero-range points (start == end)
        // are dropped: the reader treats them as hidden and skips their
        // start fields, which breaks alignment.
        val unique = points
            .filter { it.startLine != it.endLine || it.startColumn != it.endColumn }
            .distinctBy { it.offset }
        writeCompressedUnsigned(builder, 0) // local signature rid

        var previousOffset = 0
        var previousStartLine = -1
        var previousStartColumn = 0

        unique.forEachIndexed { index, sp ->
            val deltaOffset = if (index == 0) sp.offset else sp.offset - previousOffset
            writeCompressedUnsigned(builder, deltaOffset)

            val deltaLines = sp.endLine - sp.startLine
            val deltaColumns = sp.endColumn - sp.startColumn
            writeCompressedUnsigned(builder, deltaLines)
            if (deltaLines == 0) {
                writeCompressedUnsigned(builder, deltaColumns)
            } else {
                writeCompressedSigned(builder, deltaColumns)
            }

            if (previousStartLine < 0) {
                writeCompressedUnsigned(builder, sp.startLine)
                writeCompressedUnsigned(builder, sp.startColumn)
            } else {
                writeCompressedSigned(builder, sp.startLine - previousStartLine)
                writeCompressedSigned(builder, sp.startColumn - previousStartColumn)
            }

            previousOffset = sp.offset
            previousStartLine = sp.startLine
            previousStartColumn = sp.startColumn
        }
    }

    /**
     * Document name (SRM `BlobHeap.GetDocumentName`, PPDB spec):
     * first byte is the ASCII directory separator ('/'), followed by one
     * compressed handle per path segment; each handle points to a sub-blob
     * of the #Blob heap holding the UTF8 segment text. Absolute paths
     * start with an empty root segment.
     */
    fun encodeDocumentName(metadata: MetadataBuilder, builder: BlobBuilder, path: String) {
        builder.writeByte('/'.code.toByte())
        for (segment in path.split('/')) {
            val segmentBlob = BlobBuilder().also { it.writeBytes(segment.toByteArray(Charsets.UTF_8)) }
            val handle = metadata.getOrAddBlob(segmentBlob)
            writeCompressedUnsigned(builder, handle.getHeapOffset())
        }
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
