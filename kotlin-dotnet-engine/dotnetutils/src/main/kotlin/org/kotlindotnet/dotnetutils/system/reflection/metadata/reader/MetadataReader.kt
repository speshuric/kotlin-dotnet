// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (MetadataReader.cs + Internal
// heaps/Tables.cs — minimal subset, see ADR 0011).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Deviations:
//  - ByteArray-only (no MemoryBlock/stream providers);
//  - only tables emitted by our writer are decoded (11 of ~45);
//  - no string caching/comparers; rows are materialized on access.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.reader

import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MemberReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TableIndex
import kotlin.uuid.Uuid

/** A TypeRef table row (II.22.31). */
data class TypeRefRow(
    val resolutionScope: EntityHandle,
    val namespace: String,
    val name: String,
)

/** A TypeDef table row (II.22.37). */
data class TypeDefRow(
    val flags: UInt,
    val namespace: String,
    val name: String,
    val extends: EntityHandle,
    val fieldList: FieldDefinitionHandle,
    val methodList: MethodDefinitionHandle,
)

/** A MethodDef table row (II.22.26). */
data class MethodRow(
    val rva: Int,
    val implFlags: Int,
    val flags: Int,
    val name: String,
    val signature: BlobHandle,
    val parameterList: ParameterHandle,
)

/** A MemberRef table row (II.22.25). */
data class MemberRefRow(
    val parent: EntityHandle,
    val name: String,
    val signature: BlobHandle,
)

data class ModuleInfo(
    val generation: Int,
    val name: String,
    val mvid: Uuid,
)

data class AssemblyInfo(
    val hashAlgorithm: UInt,
    val majorVersion: Int,
    val minorVersion: Int,
    val buildNumber: Int,
    val revisionNumber: Int,
    val flags: UInt,
    val publicKey: BlobHandle?,
    val name: String,
    val culture: String,
)

data class AssemblyRefInfo(
    val majorVersion: Int,
    val minorVersion: Int,
    val buildNumber: Int,
    val revisionNumber: Int,
    val flags: UInt,
    val publicKeyOrToken: BlobHandle?,
    val name: String,
    val culture: String,
    val hashValue: BlobHandle?,
)

/**
 * Minimal CLI metadata reader over an in-memory image
 * (ADR 0011). [imageOffset] is the offset of the metadata root (the
 * "BSJB" signature); pass 0 for a standalone metadata blob.
 */
class MetadataReader(
    private val image: ByteArray,
    imageOffset: Int = 0,
) {
    private val stringsOffset: Int
    private val userStringsOffset: Int
    private val guidOffset: Int
    private val blobOffset: Int

    private val heapSizesFlags: Int
    private val validMask: Long

    private val rowCounts = HashMap<TableIndex, Int>()
    private val tableOffsets = HashMap<TableIndex, Int>()

    init {
        require(image.size - imageOffset >= 16) { "metadata root is too small" }
        if (!isBsjb(image, imageOffset)) {
            throw IllegalStateException("Invalid metadata signature")
        }

        var p = imageOffset + 12
        val versionLength = readUInt32(p)
        p += 4 + versionLength
        p += 2 // flags
        val streamCount = readUInt16(p)
        p += 2

        var tables = -1
        var strings = -1
        var us = -1
        var guid = -1
        var blob = -1

        repeat(streamCount) {
            val offset = readUInt32(p)
            p += 8 // offset + size (the size is not needed by the minimal reader)
            val nameStart = p
            while (image[p] != 0.toByte()) p++
            val name = image.decodeToString(nameStart, p)
            p++
            p = (p + 3) and 3.inv()

            val abs = imageOffset + offset
            when {
                name == "#~" || name == "#-" -> tables = abs
                name == "#Strings" -> strings = abs
                name == "#US" -> us = abs
                name == "#GUID" -> guid = abs
                name == "#Blob" -> blob = abs
                else -> throw IllegalStateException("Unknown metadata stream: $name")
            }
        }

        check(tables >= 0 && strings >= 0 && guid >= 0 && blob >= 0) {
            "required metadata stream is missing"
        }
        stringsOffset = strings
        userStringsOffset = if (us >= 0) us else strings
        guidOffset = guid
        blobOffset = blob

        heapSizesFlags = u8(tables + 6)
        validMask = readInt64(tables + 8)
        // sortedMask at +16 — not used by the minimal reader
        var q = tables + 24
        for (t in 0 until 64) {
            if (validMask shr t and 1L != 0L) {
                rowCounts[TableIndex.entries.first { it.value == t }] = readUInt32(q)
                q += 4
            }
        }
        q = (q + 3) and 3.inv()
        for (t in TableIndex.entries) {
            val count = rowCounts[t] ?: continue
            tableOffsets[t] = q
            q += count * rowSize(t)
        }
    }

    private fun isBsjb(b: ByteArray, at: Int): Boolean =
        b[at] == 'B'.code.toByte() && b[at + 1] == 'S'.code.toByte() &&
            b[at + 2] == 'J'.code.toByte() && b[at + 3] == 'B'.code.toByte()

    // === low-level reads ===

    private fun u8(at: Int): Int = image[at].toInt() and 0xFF

    private fun readUInt16(at: Int): Int =
        ((image[at + 1].toInt() and 0xFF) shl 8) or (image[at].toInt() and 0xFF)

    private fun readUInt32(at: Int): Int =
        ((image[at + 3].toInt() and 0xFF) shl 24) or ((image[at + 2].toInt() and 0xFF) shl 16) or
            ((image[at + 1].toInt() and 0xFF) shl 8) or (image[at].toInt() and 0xFF)

    private fun readInt64(at: Int): Long =
        (readUInt32(at + 4).toLong() shl 32) or ((readUInt32(at).toLong() and 0xFFFFFFFFL))

    private fun readIndex(at: Int, width: Int): Int = if (width == 4) readUInt32(at) else readUInt16(at)

    // === index widths ===

    private fun sIdx() = if (heapSizesFlags and 0x01 != 0) 4 else 2
    private fun gIdx() = if (heapSizesFlags and 0x02 != 0) 4 else 2
    private fun bIdx() = if (heapSizesFlags and 0x04 != 0) 4 else 2

    private fun rowCount(t: TableIndex): Int = rowCounts[t] ?: 0

    private fun simpleIdxWidth(t: TableIndex): Int = if (rowCount(t) < 0xFFFF) 2 else 4

    private fun codedIdxWidth(bits: Int, vararg tables: TableIndex): Int =
        if ((tables.maxOfOrNull { rowCount(it) } ?: 0) < (1 shl (16 - bits))) 2 else 4

    private fun fIdx() = simpleIdxWidth(TableIndex.FIELD)
    private fun mIdx() = simpleIdxWidth(TableIndex.METHOD_DEF)
    private fun pIdx() = simpleIdxWidth(TableIndex.PARAM)
    // Bit count = ceil(log2(tag count)): ResolutionScope — 4 tags -> 2,
    // TypeDefOrRef — 3 tags -> 2, MemberRefParent — 5 tags -> 3 (II.24.2.6).
    private fun rsIdx() = codedIdxWidth(2, TableIndex.MODULE, TableIndex.MODULE_REF, TableIndex.ASSEMBLY_REF, TableIndex.TYPE_REF)
    private fun tdorIdx() = codedIdxWidth(2, TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.TYPE_SPEC)
    private fun mrpIdx() = codedIdxWidth(3, TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.MODULE_REF, TableIndex.METHOD_DEF, TableIndex.TYPE_SPEC)

    private fun rowSize(t: TableIndex): Int = when (t) {
        TableIndex.MODULE -> 2 + sIdx() + gIdx() * 3
        TableIndex.TYPE_REF -> rsIdx() + sIdx() * 2
        TableIndex.TYPE_DEF -> 4 + sIdx() * 2 + tdorIdx() + fIdx() + mIdx()
        TableIndex.FIELD -> 2 + sIdx() + bIdx()
        TableIndex.METHOD_DEF -> 4 + 2 + 2 + sIdx() + bIdx() + pIdx()
        TableIndex.PARAM -> 2 + 2 + sIdx()
        TableIndex.MEMBER_REF -> mrpIdx() + sIdx() + bIdx()
        TableIndex.STAND_ALONE_SIG -> bIdx()
        TableIndex.TYPE_SPEC -> bIdx()
        TableIndex.ASSEMBLY -> 4 + 2 * 4 + 4 + bIdx() + sIdx() * 2
        TableIndex.ASSEMBLY_REF -> 2 * 4 + 4 + bIdx() + sIdx() * 2 + bIdx()
        else -> throw IllegalStateException("Table not supported by minimal reader: $t")
    }

    // === heaps ===

    fun getString(handle: StringHandle): String {
        var p = stringsOffset + handle.getHeapOffset()
        val strStart = p
        while (image[p] != 0.toByte()) p++
        return image.decodeToString(strStart, p)
    }

    /** #US: compressed byte length + UTF-16LE chars + trailing flag byte. */
    fun getUserString(handle: UserStringHandle): String {
        val base = userStringsOffset + handle.getHeapOffset()
        val len = readCompressedIntegerAt(base)
        val charCount = len / 2
        val sb = StringBuilder(charCount)
        var p = base + compressedSize(base)
        repeat(charCount) {
            sb.append(readUInt16(p).toChar())
            p += 2
        }
        return sb.toString()
    }

    fun getBlobBytes(handle: BlobHandle): ByteArray {
        val base = blobOffset + handle.getHeapOffset()
        val len = readCompressedIntegerAt(base)
        val from = base + compressedSize(base)
        return image.copyOfRange(from, from + len)
    }

    fun getGuid(handle: GuidHandle): Uuid {
        require(!handle.isNil) { "nil guid handle" }
        val at = guidOffset + (handle.index - 1) * 16
        // .NET mixed-endian layout — exact mirror of BlobContentId.fromBytes:
        // int32 a (LE) | int16 b (LE) | int16 c (LE) | 8 raw bytes (BE)
        val msb = (readUInt32(at).toLong() shl 32) or
            (readUInt16(at + 4).toLong() shl 16) or
            readUInt16(at + 6).toLong()
        var lsb = 0L
        for (i in at + 8 until at + 16) lsb = (lsb shl 8) or (image[i].toLong() and 0xFF)
        return Uuid.fromLongs(msb, lsb)
    }

    private fun compressedSize(at: Int): Int =
        when {
            u8(at) and 0x80 == 0 -> 1
            u8(at) and 0xC0 == 0x80 -> 2
            else -> 4
        }

    private fun readCompressedIntegerAt(at: Int): Int {
        val br = org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobReader(image, at, image.size - at)
        return br.readCompressedInteger()
    }

    // === tables ===

    fun getRowCount(table: TableIndex): Int = rowCount(table)

    private fun rowStart(table: TableIndex, rowNumber: Int): Int {
        require(rowNumber in 1..rowCount(table)) { "row out of range: $rowNumber" }
        return tableOffsets.getValue(table) + (rowNumber - 1) * rowSize(table)
    }

    private fun decodeCoded(raw: Int, bits: Int, tagTables: List<TableIndex>): EntityHandle {
        val tag = raw and ((1 shl bits) - 1)
        val rowNumber = raw shr bits
        if (rowNumber == 0) return EntityHandle.NIL
        return MetadataTokens.entityHandle(tagTables[tag], rowNumber)
    }

    // --- Module ---

    fun moduleDefinition(): ModuleInfo? {
        if (rowCount(TableIndex.MODULE) == 0) return null
        val p = rowStart(TableIndex.MODULE, 1)
        val nameOff = readIndex(p + 2, sIdx())
        val mvidOff = readIndex(p + 2 + sIdx(), gIdx())
        return ModuleInfo(
            generation = readUInt16(p),
            name = getString(MetadataTokens.stringHandle(nameOff)),
            mvid = getGuid(MetadataTokens.guidHandle(mvidOff)),
        )
    }

    // --- TypeRef ---

    fun typeReference(rowNumber: Int): TypeRefRow {
        val p = rowStart(TableIndex.TYPE_REF, rowNumber)
        var q = p
        val scopeRaw = readIndex(q, rsIdx())
        q += rsIdx()
        val name = getString(MetadataTokens.stringHandle(readIndex(q, sIdx())))
        q += sIdx()
        val ns = getString(MetadataTokens.stringHandle(readIndex(q, sIdx())))
        val scope =
            decodeCoded(
                scopeRaw,
                2,
                listOf(TableIndex.MODULE, TableIndex.MODULE_REF, TableIndex.ASSEMBLY_REF, TableIndex.TYPE_REF),
            )
        return TypeRefRow(scope, ns, name)
    }

    // --- TypeDef ---

    fun typeDefinition(rowNumber: Int): TypeDefRow {
        val p = rowStart(TableIndex.TYPE_DEF, rowNumber)
        var q = p
        val flags = readUInt32(q).toUInt()
        q += 4
        val name = getString(MetadataTokens.stringHandle(readIndex(q, sIdx())))
        q += sIdx()
        val ns = getString(MetadataTokens.stringHandle(readIndex(q, sIdx())))
        q += sIdx()
        val extends = decodeCoded(
            readIndex(q, tdorIdx()), 2,
            listOf(TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.TYPE_SPEC),
        )
        q += tdorIdx()
        val fieldList = FieldDefinitionHandle.fromRowId(readIndex(q, fIdx()))
        q += fIdx()
        val methodList = MetadataTokens.methodDefinitionHandle(readIndex(q, mIdx()))
        return TypeDefRow(flags, ns, name, extends, fieldList, methodList)
    }

    // --- MethodDef ---

    fun methodDefinition(rowNumber: Int): MethodRow {
        val p = rowStart(TableIndex.METHOD_DEF, rowNumber)
        var q = p
        val rva = readUInt32(q)
        q += 4
        val implFlags = readUInt16(q)
        q += 2
        val flags = readUInt16(q)
        q += 2
        val name = getString(MetadataTokens.stringHandle(readIndex(q, sIdx())))
        q += sIdx()
        val sig = MetadataTokens.blobHandle(readIndex(q, bIdx()))
        q += bIdx()
        val paramList = MetadataTokens.parameterHandle(readIndex(q, pIdx()))
        return MethodRow(rva, implFlags, flags, name, sig, paramList)
    }

    // --- MemberRef ---

    fun memberReference(rowNumber: Int): MemberRefRow {
        val p = rowStart(TableIndex.MEMBER_REF, rowNumber)
        var q = p
        val parent = decodeCoded(
            readIndex(q, mrpIdx()), 3,
            listOf(
                TableIndex.TYPE_DEF, TableIndex.TYPE_REF, TableIndex.MODULE_REF,
                TableIndex.METHOD_DEF, TableIndex.TYPE_SPEC,
            ),
        )
        q += mrpIdx()
        val name = getString(MetadataTokens.stringHandle(readIndex(q, sIdx())))
        q += sIdx()
        val sig = MetadataTokens.blobHandle(readIndex(q, bIdx()))
        return MemberRefRow(parent, name, sig)
    }

    // --- StandAloneSig / TypeSpec ---

    fun standaloneSignature(rowNumber: Int): StandaloneSignatureHandle {
        val p = rowStart(TableIndex.STAND_ALONE_SIG, rowNumber)
        return MetadataTokens.standaloneSignatureHandle(rowNumber).also {
            @Suppress("UNUSED_EXPRESSION") p
        }
    }

    fun standaloneSignatureBlob(rowNumber: Int): BlobHandle {
        val p = rowStart(TableIndex.STAND_ALONE_SIG, rowNumber)
        return MetadataTokens.blobHandle(readIndex(p, bIdx()))
    }

    fun typeSpecification(rowNumber: Int): BlobHandle {
        val p = rowStart(TableIndex.TYPE_SPEC, rowNumber)
        return MetadataTokens.blobHandle(readIndex(p, bIdx()))
    }

    // --- Assembly ---

    fun assemblyDefinition(): AssemblyInfo? {
        if (rowCount(TableIndex.ASSEMBLY) == 0) return null
        val p = rowStart(TableIndex.ASSEMBLY, 1)
        var q = p
        val hashAlg = readUInt32(q).toUInt()
        q += 4
        val major = readUInt16(q); q += 2
        val minor = readUInt16(q); q += 2
        val build = readUInt16(q); q += 2
        val rev = readUInt16(q); q += 2
        val flags = readUInt32(q).toUInt(); q += 4
        val pubKey = MetadataTokens.blobHandle(readIndex(q, bIdx())); q += bIdx()
        val name = getString(MetadataTokens.stringHandle(readIndex(q, sIdx()))); q += sIdx()
        val culture = getString(MetadataTokens.stringHandle(readIndex(q, sIdx())))
        return AssemblyInfo(hashAlg, major, minor, build, rev, flags, pubKey.takeIf { !it.isNil }, name, culture)
    }

    // --- AssemblyRef ---

    fun assemblyReferenceCount(): Int = rowCount(TableIndex.ASSEMBLY_REF)

    fun assemblyReference(rowNumber: Int): AssemblyRefInfo {
        val p = rowStart(TableIndex.ASSEMBLY_REF, rowNumber)
        var q = p
        val major = readUInt16(q); q += 2
        val minor = readUInt16(q); q += 2
        val build = readUInt16(q); q += 2
        val rev = readUInt16(q); q += 2
        val flags = readUInt32(q).toUInt(); q += 4
        val token = MetadataTokens.blobHandle(readIndex(q, bIdx())); q += bIdx()
        val name = getString(MetadataTokens.stringHandle(readIndex(q, sIdx()))); q += sIdx()
        val culture = getString(MetadataTokens.stringHandle(readIndex(q, sIdx()))); q += sIdx()
        val hash = MetadataTokens.blobHandle(readIndex(q, bIdx()))
        return AssemblyRefInfo(major, minor, build, rev, flags, token.takeIf { !it.isNil }, name, culture, hash.takeIf { !it.isNil })
    }

    companion object {
        const val TABLE_STREAM_NAME = "#~"
        const val COMPRESSED_TABLE_STREAM_NAME = "#-"
    }
}
