// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Smoke test: builds the hello-world PE image and writes it
// to build/hello-image/hello.exe for external verification (dotnet,
// dotnet-ildasm, C# harness in verifier/).

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelloWorldImageTests {
    @Test
    fun buildsRunnableImage() {
        val image = HelloWorldImage.buildImage()

        // DOS header magic and NT signature:
        assertEquals('M'.code.toByte(), image[0])
        assertEquals('Z'.code.toByte(), image[1])
        val ntHeaderOffset = readInt32(image, 0x3C)
        assertEquals(0x80, ntHeaderOffset)
        assertEquals(0x00004550u, readUInt32(image, ntHeaderOffset)) // "PE\0\0"

        // Deterministic output:
        assertContentEquals(image, HelloWorldImage.buildImage())

        // Write for external verification (dotnet / ildasm / verifier):
        val outDir = File(System.getProperty("user.dir"), "build/hello-image")
        outDir.mkdirs()
        File(outDir, "hello.exe").writeBytes(image)

        assertTrue(image.size > 512)
    }

    private fun readInt32(b: ByteArray, offset: Int): Int =
        ((b[offset + 3].toInt() and 0xFF) shl 24) or ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or (b[offset].toInt() and 0xFF)

    private fun readUInt32(b: ByteArray, offset: Int): UInt = readInt32(b, offset).toUInt()
}
