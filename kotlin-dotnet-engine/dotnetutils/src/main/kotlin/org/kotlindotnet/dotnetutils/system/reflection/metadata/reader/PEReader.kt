// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/PEReader.cs,
// PEHeaders.cs, IL/MethodBodyBlock.cs — minimal subset, see ADR 0011).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Deviations:
//  - ByteArray-only; no memory-mapping/stream options;
//  - parses only what the minimal reader needs: DOS/PE signature,
//    COFF section count/table, optional header magic + data directory
//    #14 (COR20), COR header fields of interest, section table;
//  - MethodBody decodes tiny/fat headers and exception regions only.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.reader

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobReader
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens

/** Разобранные заголовки PE-образа (минимум для чтения метаданных). */
class PEReader(val image: ByteArray) {
    /** Файловое смещение корня метаданных ("BSJB"). */
    val metadataRootOffset: Int

    private val sections = ArrayList<Section>(4)

    private data class Section(val rva: Int, val rawSize: Int, val rawPtr: Int)

    init {
        require(image.size >= 0x40) { "image too small for DOS header" }
        if (image[0] != 'M'.code.toByte() || image[1] != 'Z'.code.toByte()) {
            throw IllegalStateException("Missing DOS signature 'MZ'")
        }

        val peOffset = i32(0x3C)
        if (u32(peOffset) != 0x00004550) { // "PE\0\0"
            throw IllegalStateException("Missing PE signature")
        }

        val coffAt = peOffset + 4
        val numberOfSections = u16(coffAt + 2)
        val sizeOfOptionalHeader = u16(coffAt + 16)

        val optAt = coffAt + 20
        val optMagic = u16(optAt)
        check(optMagic == 0x010B || optMagic == 0x020B) { "Unknown optional header magic" }
        val ddOffset = optAt + if (optMagic == 0x020B) 112 else 96

        val corRva = u32(ddOffset + 14 * 8)

        val sectAt = optAt + sizeOfOptionalHeader
        repeat(numberOfSections) { i ->
            val s = sectAt + 40 * i
            sections += Section(
                rva = u32(s + 12),
                rawSize = u32(s + 16),
                rawPtr = u32(s + 20),
            )
        }

        val corHeaderFileOffset = requireNotNull(rvaToOffset(corRva)) { "COR20 directory RVA not mapped" }
        metadataRootOffset = requireNotNull(rvaToOffset(u32(corHeaderFileOffset + 8))) {
            "metadata directory RVA not mapped"
        }
    }

    private fun u8(at: Int): Int = image[at].toInt() and 0xFF

    private fun u16(at: Int): Int =
        ((image[at + 1].toInt() and 0xFF) shl 8) or (image[at].toInt() and 0xFF)

    private fun u32(at: Int): Int =
        ((image[at + 3].toInt() and 0xFF) shl 24) or ((image[at + 2].toInt() and 0xFF) shl 16) or
            ((image[at + 1].toInt() and 0xFF) shl 8) or (image[at].toInt() and 0xFF)

    private fun i32(at: Int): Int = u32(at)

    /** Транслирует RVA в файловое смещение или null, если RVA не замаплен. */
    fun rvaToOffset(rva: Int): Int? {
        for ((rva0, size, ptr) in sections) {
            if (rva >= rva0 && rva < rva0 + size) {
                return ptr + (rva - rva0)
            }
        }
        return null
    }

    /**
     * Читает тело метода по RVA ([MethodRow.rva]): tiny/fat header,
     * байты IL, локальная сигнатура, exception regions.
     */
    fun methodBody(rva: Int): MethodBody {
        val at = requireNotNull(rvaToOffset(rva)) { "method body RVA not mapped" }
        val first = u8(at)

        if (first and 0x03 == 0x02) {
            // tiny header: размер кода в старших 6 битах первого байта
            val codeSize = first shr 2
            return MethodBody(
                maxStack = -1,
                codeOffset = at + 1,
                codeSize = codeSize,
                localVariablesSignature = MetadataTokens.standaloneSignatureHandle(0),
                exceptionRegions = emptyList(),
            )
        }

        // fat header:
        require(first and 0x03 == 0x03) { "Invalid method body flags" }
        val flagsSize = u16(at)
        val headerSize = (flagsSize shr 12) * 4
        val moreSects = flagsSize and 0x08 != 0
        val maxStack = u16(at + 2)
        val codeSize = u32(at + 4)
        val localSigToken = u32(at + 8)

        val codeOffset = at + headerSize
        val regions =
            if (moreSects) decodeExceptionRegions(codeOffset + codeSize) else emptyList()

        return MethodBody(
            maxStack = maxStack,
            codeOffset = codeOffset,
            codeSize = codeSize,
            localVariablesSignature = StandaloneSignatureHandle.fromRowId(localSigToken and 0x00FFFFFF),
            exceptionRegions = regions,
        )
    }

    /// II.25.4.5: выровненный на 4 байта блок после кода;
    /// small clause — 12 байт, fat clause — 24 байта.
    private fun decodeExceptionRegions(afterCodeOffset: Int): List<ExceptionRegionInfo> {
        var sect = (afterCodeOffset + 3) and 3.inv()
        val kind = u8(sect)
        check(kind and 0x40 == 0 || kind and 0x40 != 0) // формат всегда один из двух
        val dataSize = u8(sect + 1) * 4
        val isFat = kind and 0x40 != 0

        var q = sect + 4
        val end = sect + dataSize
        val result = ArrayList<ExceptionRegionInfo>()
        while (q < end) {
            if (isFat) {
                result += ExceptionRegionInfo(
                    kind = u32(q),
                    tryOffset = u32(q + 4),
                    tryLength = u32(q + 8),
                    handlerOffset = u32(q + 12),
                    handlerLength = u32(q + 16),
                    classTokenOrFilterOffset = u32(q + 20),
                )
                q += 24
            } else {
                result += ExceptionRegionInfo(
                    kind = u16(q),
                    tryOffset = u16(q + 2),
                    tryLength = u8(q + 4),
                    handlerOffset = u16(q + 5),
                    handlerLength = u8(q + 7),
                    classTokenOrFilterOffset = u32(q + 8),
                )
                q += 12
            }
        }
        return result
    }
}

/** Декодированное тело метода (ADR 0011). */
class MethodBody internal constructor(
    val maxStack: Int,
    val codeOffset: Int,
    val codeSize: Int,
    val localVariablesSignature: StandaloneSignatureHandle,
    val exceptionRegions: List<ExceptionRegionInfo>,
) {
    fun getILReader(image: ByteArray): BlobReader = BlobReader(image, codeOffset, codeSize)
}

/** Exception clause из таблицы II.25.4.5 (small или fat формат). */
data class ExceptionRegionInfo(
    val kind: Int,
    val tryOffset: Int,
    val tryLength: Int,
    val handlerOffset: Int,
    val handlerLength: Int,
    val classTokenOrFilterOffset: Int,
)
