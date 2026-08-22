// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Adapted from System.Reflection.Metadata tests (Ecma335/MetadataBuilderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class I4MetadataBuilderTests {

    private fun newBuilder(): MetadataBuilder = MetadataBuilder()

    @Test
    fun emptyMetadata_rowCounts() {
        val mb = newBuilder()
        val counts = mb.getRowCounts()
        assertEquals(64, counts.size)
        counts.forEach { assertEquals(0, it) }
    }

    @Test
    fun addModule_rowCount() {
        val mb = newBuilder()
        mb.addModule(0, mb.getOrAddString("test"), GuidHandle(0), GuidHandle(0), GuidHandle(0))
        assertEquals(1, mb.getRowCount(TableIndex.MODULE))
        assertEquals(0, mb.getRowCount(TableIndex.ASSEMBLY))
    }

    @Test
    fun addAssembly_rowCount() {
        val mb = newBuilder()
        mb.addAssembly(mb.getOrAddString("MyAsm"), AssemblyVersion(1, 0, 0, 0), mb.getOrAddString(""), BlobHandle(0), 0, 0u)
        assertEquals(1, mb.getRowCount(TableIndex.ASSEMBLY))
    }

    @Test
    fun getOrAddString_dedup() {
        val mb = newBuilder()
        val h1 = mb.getOrAddString("hello")
        val h2 = mb.getOrAddString("hello")
        val h3 = mb.getOrAddString("world")
        assertEquals(h1, h2)
        assertFalse(h1 == h3)
        assertEquals(h1.getWriterVirtualIndex(), 1)
        assertEquals(h3.getWriterVirtualIndex(), 2)
    }

    @Test
    fun getOrAddUserString_dedup() {
        val mb = newBuilder()
        val h1 = mb.getOrAddUserString("hello")
        val h2 = mb.getOrAddUserString("hello")
        assertEquals(h1, h2)
        assertTrue(h1.getHeapOffset() > 0) // at least the 0-byte prefix
    }

    @Test
    fun getOrAddBlob_dedup() {
        val mb = newBuilder()
        val h1 = mb.getOrAddBlob(byteArrayOf(0x01, 0x02))
        val h2 = mb.getOrAddBlob(byteArrayOf(0x01, 0x02))
        val h3 = mb.getOrAddBlob(byteArrayOf(0x03))
        assertEquals(h1, h2)
        assertFalse(h1 == h3)
    }

    @Test
    fun getOrAddGuid_dedup() {
        val mb = newBuilder()
        val g = Uuid.random()
        val h1 = mb.getOrAddGuid(g)
        val h2 = mb.getOrAddGuid(g)
        assertEquals(h1, h2)
        assertEquals(1, h2.index) // first guid is at index 1
    }

    @Test
    fun getOrAddGuid_nilReturnsZero() {
        val mb = newBuilder()
        val h = mb.getOrAddGuid(Uuid.NIL)
        assertTrue(h.isNil)
    }

    @Test
    fun addTypeDefinition_rowCount() {
        val mb = newBuilder()
        val name = mb.getOrAddString("Foo")
        val ns = mb.getOrAddString("Test")
        val typeHandle = mb.addTypeDefinition(0u, ns, name, EntityHandle(0u), mb.addFieldDefinition(0, name, BlobHandle(0)), mb.addMethodDefinition(0, 0, name, BlobHandle(0), -1, mb.addParameter(0, name, 0)))
        assertEquals(1, mb.getRowCount(TableIndex.TYPE_DEF))
        assertEquals(1, mb.getRowCount(TableIndex.FIELD))
        assertEquals(1, mb.getRowCount(TableIndex.METHOD_DEF))
        assertEquals(1, mb.getRowCount(TableIndex.PARAM))
        assertEquals(1, typeHandle.rowId)
    }

    @Test
    fun serializeMetadataRoot_minimalAssembly() {
        val mb = newBuilder()
        mb.addModule(0, mb.getOrAddString("test"), GuidHandle(0), GuidHandle(0), GuidHandle(0))
        mb.addAssembly(mb.getOrAddString("MyAsm"), AssemblyVersion(1, 0, 0, 0), mb.getOrAddString(""), BlobHandle(0), 0, 0u)

        val rootBuilder = MetadataRootBuilder(mb)
        val writer = BlobBuilder()
        rootBuilder.serialize(writer, methodBodyStreamRva = 0, mappedFieldDataStreamRva = 0)

        val bytes = writer.toArray()
        assertTrue(bytes.size > 0)

        // metadata signature 0x424A5342 (BSJB) little-endian
        assertEquals(0x42, bytes[0].toInt() and 0xFF)
        assertEquals(0x53, bytes[1].toInt() and 0xFF)
        assertEquals(0x4A, bytes[2].toInt() and 0xFF)
        assertEquals(0x42, bytes[3].toInt() and 0xFF)

        // major version = 1 (little-endian uint16)
        assertEquals(1, bytes[4].toInt() and 0xFF)
        assertEquals(0, bytes[5].toInt() and 0xFF)
        // minor version = 1
        assertEquals(1, bytes[6].toInt() and 0xFF)
        assertEquals(0, bytes[7].toInt() and 0xFF)

        // metadata version string "v4.0.30319" at offset 16
        val versionStr = "v4.0.30319"
        val versionBytes = versionStr.toByteArray(Charsets.UTF_8)
        for (i in versionBytes.indices) {
            assertEquals(versionBytes[i], bytes[16 + i], "version byte at $i")
        }
    }

    @Test
    fun validateOrder_emptyPasses() {
        val mb = newBuilder()
        mb.validateOrder() // should not throw
    }
}
