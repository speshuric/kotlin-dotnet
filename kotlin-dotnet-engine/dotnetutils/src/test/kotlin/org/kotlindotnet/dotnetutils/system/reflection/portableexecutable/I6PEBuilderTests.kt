// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (tests/PortableExecutable/PEBuilderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - PEReader/MetadataReader-dependent tests are deferred to M1;
//  - NativeResources verification parses the raw image with the
//    SectionHeader layout instead of PEReader.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import kotlin.uuid.Uuid
import org.kotlindotnet.dotnetutils.system.reflection.metadata.Blob
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobContentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class I6PEBuilderTests {
    private class TestResourceSectionBuilder : ResourceSectionBuilder() {
        override fun serialize(builder: org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder, location: SectionLocation) {
            builder.writeInt32(0x12345678)
            builder.writeInt32(location.pointerToRawData)
            builder.writeInt32(location.relativeVirtualAddress)
        }
    }

    private class BadResourceSectionBuilder : ResourceSectionBuilder() {
        override fun serialize(builder: org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder, location: SectionLocation) {
            throw NotImplementedError()
        }
    }

    @Test
    fun managedPEBuilderErrors() {
        val hdr = PEHeaderBuilder()
        val metadataBuilder = org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder()
        assertFailsWith<IllegalArgumentException> {
            ManagedPEBuilder(
                hdr,
                org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataRootBuilder(metadataBuilder),
                BlobBuilder(),
                strongNameSignatureSize = -1,
            )
        }
    }

    @Test
    fun nativeResources() {
        val ilBuilder = BlobBuilder()
        val metadataBuilder = org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder()

        val peBuilder =
            ManagedPEBuilder(
                PEHeaderBuilder.createLibraryHeader(),
                org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataRootBuilder(metadataBuilder),
                ilBuilder,
                nativeResources = TestResourceSectionBuilder(),
                deterministicIdProvider = { CONTENT_ID },
            )

        val peBlob = BlobBuilder()

        val contentId = peBuilder.serialize(peBlob)

        assertEquals(CONTENT_ID, contentId)

        // Parse the raw image: find ".rsrc" section header and verify its payload.
        val image = peBlob.toArray()
        val rsrcHeaderOffset = findRsrcSectionHeaderOffset(image)
        assertTrue(rsrcHeaderOffset >= 0, ".rsrc section header not found")

        val virtualSize = readInt32(image, rsrcHeaderOffset + 8)
        val virtualAddress = readInt32(image, rsrcHeaderOffset + 12)
        readInt32(image, rsrcHeaderOffset + 16) // sizeOfRawData
        val pointerToRawData = readInt32(image, rsrcHeaderOffset + 20)

        // Payload written by TestResourceSectionBuilder:
        assertEquals(12, virtualSize)
        assertEquals(0x12345678, readInt32(image, pointerToRawData))
        assertEquals(pointerToRawData, readInt32(image, pointerToRawData + 4))
        assertEquals(virtualAddress, readInt32(image, pointerToRawData + 8))
    }

    @Test
    fun nativeResourcesBadImpl() {
        val ilBuilder = BlobBuilder()
        val metadataBuilder = org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder()

        val peBuilder =
            ManagedPEBuilder(
                PEHeaderBuilder.createLibraryHeader(),
                org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataRootBuilder(metadataBuilder),
                ilBuilder,
                nativeResources = BadResourceSectionBuilder(),
                deterministicIdProvider = { CONTENT_ID },
            )

        val peBlob = BlobBuilder()

        assertFailsWith<NotImplementedError> { peBuilder.serialize(peBlob) }
    }

    @Test
    fun getContentToSignAllInOneBlob() {
        val builder = BlobBuilder(16)
        builder.writeBytes(1, 5)
        val snFixup = builder.reserveBytes(5)
        builder.writeBytes(2, 6)
        assertEquals(1, builder.getBlobs().count())

        assertEquals(
            listOf(
                "0: [0, 2)",
                "0: [4, 5)",
                "0: [10, 16)",
            ),
            getBlobRanges(builder, PEBuilder.getContentToSign(builder, 2, 4, snFixup)),
        )
    }

    @Test
    fun getContentToSignMultiBlobHeader() {
        val builder = BlobBuilder(16)
        builder.writeBytes(0, 16)
        builder.writeBytes(1, 16)
        builder.writeBytes(2, 16)
        builder.writeBytes(3, 16)
        builder.writeBytes(4, 2)
        val snFixup = builder.reserveBytes(1)
        builder.writeBytes(4, 13)
        builder.writeBytes(5, 10)
        assertEquals(6, builder.getBlobs().count())

        assertEquals(
            listOf(
                "0: [0, 16)",
                "1: [0, 16)",
                "2: [0, 1)",
                "4: [0, 2)",
                "4: [3, 16)",
                "5: [0, 10)",
            ),
            getBlobRanges(builder, PEBuilder.getContentToSign(builder, 33, 64, snFixup)),
        )
    }

    @Test
    fun getContentToSignHeaderAndFixupInDistinctBlobs() {
        val builder = BlobBuilder(16)
        builder.writeBytes(0, 16)
        builder.writeBytes(1, 16)
        builder.writeBytes(2, 16)
        builder.writeBytes(3, 16)
        val snFixup = builder.reserveBytes(16)
        builder.writeBytes(5, 16)
        builder.writeBytes(6, 1)
        assertEquals(7, builder.getBlobs().count())

        assertEquals(
            listOf(
                "0: [0, 1)",
                "0: [4, 16)",
                "1: [0, 16)",
                "2: [0, 16)",
                "3: [0, 16)",
                "4: [0, 0)",
                "4: [16, 16)",
                "5: [0, 16)",
                "6: [0, 1)",
            ),
            getBlobRanges(builder, PEBuilder.getContentToSign(builder, 1, 4, snFixup)),
        )
    }

    @Test
    fun getPrefixBlob() {
        val buffer = byteArrayOf(0, 1, 2, 3, 4, 5)

        // [0, 1, <2, 3>, 4, 5]
        var b = PEBuilder.getPrefixBlob(Blob(buffer, 0, 6), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(0, b.start)
        assertEquals(2, b.length)

        // [0, 1, <2, 3>, 4], 5
        b = PEBuilder.getPrefixBlob(Blob(buffer, 0, 5), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(0, b.start)
        assertEquals(2, b.length)

        // 0, [1, <2, 3>, 4], 5
        b = PEBuilder.getPrefixBlob(Blob(buffer, 1, 4), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(1, b.start)
        assertEquals(1, b.length)

        // 0, 1, [<2, 3>, 4], 5
        b = PEBuilder.getPrefixBlob(Blob(buffer, 2, 3), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(2, b.start)
        assertEquals(0, b.length)

        // 0, 1, [<2, 3>], 4, 5
        b = PEBuilder.getPrefixBlob(Blob(buffer, 2, 2), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(2, b.start)
        assertEquals(0, b.length)

        // 0, 1, [<2>], 3, 4, 5
        b = PEBuilder.getPrefixBlob(Blob(buffer, 2, 1), Blob(buffer, 2, 1))
        assertTrue(b.buffer === buffer)
        assertEquals(2, b.start)
        assertEquals(0, b.length)

        // 0, 1, [<>]2, 3, 4, 5
        b = PEBuilder.getPrefixBlob(Blob(buffer, 2, 0), Blob(buffer, 2, 0))
        assertTrue(b.buffer === buffer)
        assertEquals(2, b.start)
        assertEquals(0, b.length)
    }

    @Test
    fun getSuffixBlob() {
        val buffer = byteArrayOf(0, 1, 2, 3, 4, 5)

        // [0, 1, <2, 3>], 4, 5
        var b = PEBuilder.getSuffixBlob(Blob(buffer, 0, 4), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(4, b.start)
        assertEquals(0, b.length)

        // 0, [1, <2, 3>, 4], 5
        b = PEBuilder.getSuffixBlob(Blob(buffer, 1, 4), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(4, b.start)
        assertEquals(1, b.length)

        // 0, 1, [<2, 3>, 4, 5]
        b = PEBuilder.getSuffixBlob(Blob(buffer, 2, 4), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(4, b.start)
        assertEquals(2, b.length)

        // [0, 1, <2, 3>, 4, 5]
        b = PEBuilder.getSuffixBlob(Blob(buffer, 0, 6), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(4, b.start)
        assertEquals(2, b.length)

        // 0, 1, [<2, 3>], 4, 5
        b = PEBuilder.getSuffixBlob(Blob(buffer, 2, 2), Blob(buffer, 2, 2))
        assertTrue(b.buffer === buffer)
        assertEquals(4, b.start)
        assertEquals(0, b.length)

        // 0, 1, [<>]2, 3, 4, 5
        b = PEBuilder.getSuffixBlob(Blob(buffer, 2, 0), Blob(buffer, 2, 0))
        assertTrue(b.buffer === buffer)
        assertEquals(2, b.start)
        assertEquals(0, b.length)
    }

    // helpers

    private companion object {
        val GUID: Uuid = Uuid.parse("97f4dbd4-f6d1-4fad-91b3-1001f92068e5")
        val CONTENT_ID = BlobContentId(GUID, 0x04030201u)
    }

    private fun getBlobRanges(builder: BlobBuilder, blobs: List<Blob>): List<String> {
        val blobIndex = HashMap<ByteArray?, Int>()
        var i = 0
        for (blob in builder.getBlobs()) {
            blobIndex[blob.buffer] = i++
        }

        return blobs.map { blob -> "${blobIndex[blob.buffer]}: [${blob.start}, ${blob.start + blob.length})" }
    }

    private fun findRsrcSectionHeaderOffset(image: ByteArray): Int {
        val pattern = ".rsrc".encodeToByteArray()
        outer@ for (i in 0..image.size - pattern.size - 3) {
            for (j in pattern.indices) {
                if (image[i + j] != pattern[j]) continue@outer
            }
            if (image[i + pattern.size] == 0.toByte() && image[i + pattern.size + 1] == 0.toByte() &&
                image[i + pattern.size + 2] == 0.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    private fun readInt32(b: ByteArray, offset: Int): Int =
        ((b[offset + 3].toInt() and 0xFF) shl 24) or ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or (b[offset].toInt() and 0xFF)
}
