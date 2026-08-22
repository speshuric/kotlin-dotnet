// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Round-trip tests: writer (MetadataBuilder/ManagedPEBuilder) → reader
// (MetadataReader/PEReader). See ADR 0011.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.reader

import kotlin.uuid.Uuid
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobReader
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MIN_SIGNED_COMPRESSED_INTEGER_VALUE
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MAX_SIGNED_COMPRESSED_INTEGER_VALUE
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.AssemblyVersion
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.InstructionEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataRootBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MethodBodyStreamEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addAssembly
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addAssemblyReference
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addMemberReference
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addMethodDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addModule
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addStandaloneSignature
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeReference
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.Characteristics
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.CorFlags
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.ManagedPEBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.PEHeaderBuilder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RoundTripTests {
    // === BlobReader: compressed integers ===

    private fun rtCompressedInt(value: Int): Int {
        val b = BlobBuilder()
        b.writeCompressedInteger(value)
        return BlobReader(b.toArray(), 0, b.count).readCompressedInteger()
    }

    @Test
    fun blobReader_compressedUnsignedIntegers() {
        val values = listOf(0, 1, 127, 128, 0x3FFF, 0x4000, 0x1FFFFFFF, MAX_COMPRESSED_INTEGER_VALUE)
        for (v in values) {
            assertEquals(v, rtCompressedInt(v), "value=$v")
        }
    }

    private fun rtSigned(value: Int): Int {
        val b = BlobBuilder()
        b.writeCompressedSignedInteger(value)
        return BlobReader(b.toArray(), 0, b.count).readCompressedSignedInteger()
    }

    @Test
    fun blobReader_compressedSignedIntegers() {
        // допустимый диапазон writer'а: значение в 28 битах со знаком в LSB
        val values = listOf(
            0, 63, -64, 64, -65, 8191, -8192, 134217727, -134217728,
        )
        for (v in values.distinct()) {
            assertEquals(v, rtSigned(v), "value=$v")
        }
    }

    // === metadata-level round trip ===

    /** Строит метаданные мини-сборки и возвращает (builder, mvid, userStringHandle). */
    private class Built(
        val bytes: ByteArray,
        val mvid: Uuid,
        val helloText: String,
    )

    private fun buildMinimalMetadata(): Built {
        val metadata = MetadataBuilder()
        val mvid = Uuid.parse("4b5ea1a7-9a2f-4f0e-8d3c-6f1e2a9b0c41")
        metadata.addModule(0, metadata.getOrAddString("rt-test"), metadata.getOrAddGuid(mvid), GuidHandle(0), GuidHandle(0))
        metadata.addAssembly(metadata.getOrAddString("RtAsm"), AssemblyVersion(1, 2, 3, 4), MetadataTokens.stringHandle(0), BlobHandle(0), 0, 0u)

        val bclToken = byteArrayOf(0xB0.toByte(), 0x3F, 0x5F, 0x7F, 0x11, 0xD5.toByte(), 0x0A, 0x3A.toByte())
        val systemRuntime =
            metadata.addAssemblyReference(
                metadata.getOrAddString("System.Runtime"), AssemblyVersion(8, 0, 0, 0),
                MetadataTokens.stringHandle(0), metadata.getOrAddBlob(bclToken), 0u, BlobHandle(0),
            )
        val objectTypeRef =
            metadata.addTypeReference(systemRuntime.toEntityHandle(), metadata.getOrAddString("System"), metadata.getOrAddString("Object"))

        val mainSig = metadata.getOrAddBlob(byteArrayOf(0x00, 0x00, 0x01)) // static void ()
        val main =
            metadata.addMethodDefinition(0x0096, 0x0000, metadata.getOrAddString("Main"), mainSig, -1, ParameterHandle(0))

        metadata.addTypeDefinition(
            0u, MetadataTokens.stringHandle(0), metadata.getOrAddString("<Module>"),
            EntityHandle.NIL, MetadataTokens.fieldDefinitionHandle(1), main,
        )
        metadata.addTypeDefinition(
            0x00100001u, MetadataTokens.stringHandle(0), metadata.getOrAddString("Program"),
            objectTypeRef.toEntityHandle(), MetadataTokens.fieldDefinitionHandle(1), main,
        )

        val blob = BlobBuilder()
        MetadataRootBuilder(metadata).serialize(blob, methodBodyStreamRva = 0, mappedFieldDataStreamRva = 0)
        return Built(blob.toArray(), mvid, "")
    }

    @Test
    fun metadataRoundTrip_rowsAndHeaps() {
        val built = buildMinimalMetadata()
        val reader = MetadataReader(built.bytes, imageOffset = 0)

        // Module:
        val module = reader.moduleDefinition()
        assertEquals("rt-test", module!!.name)
        assertEquals(0, module.generation)
        assertEquals(built.mvid, module.mvid)

        // Assembly:
        val asm = reader.assemblyDefinition()
        assertEquals("RtAsm", asm!!.name)
        assertEquals(1, asm.majorVersion)
        assertEquals(2, asm.minorVersion)
        assertEquals(3, asm.buildNumber)
        assertEquals(4, asm.revisionNumber)
        assertEquals(null, asm.publicKey)
        assertEquals("", asm.culture)

        // TypeDefs:
        val moduleRow = reader.typeDefinition(1)
        assertEquals("<Module>", moduleRow.name)
        assertEquals(EntityHandle.NIL, moduleRow.extends)

        val program = reader.typeDefinition(2)
        assertEquals("Program", program.name)
        assertEquals("", program.namespace)
        assertEquals(0x00100001u, program.flags)
        // extends -> TypeRef row 1:
        assertEquals(MetadataTokens.typeReferenceHandle(1).toEntityHandle(), program.extends)
        assertEquals(mainHandle(), program.methodList)
    }

    private fun mainHandle() = MetadataTokens.methodDefinitionHandle(1)

    @Test
    fun metadataRoundTrip_typeRefAndHeaps() {
        val built = buildMinimalMetadata()
        val reader = MetadataReader(built.bytes, 0)

        assertEquals(1, reader.getRowCount(org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TableIndex.TYPE_REF))
        val obj = reader.typeReference(1)
        assertEquals("System", obj.namespace)
        assertEquals("Object", obj.name)
        // scope -> AssemblyRef #1:
        assertEquals(MetadataTokens.assemblyReferenceHandle(1).toEntityHandle(), obj.resolutionScope)

        val asmRef = reader.assemblyReference(1)
        assertEquals("System.Runtime", asmRef.name)
        assertEquals(8, asmRef.majorVersion)
        assertContentEquals(byteArrayOf(0xB0.toByte(), 0x3F, 0x5F, 0x7F, 0x11, 0xD5.toByte(), 0x0A, 0x3A.toByte()), reader.getBlobBytes(asmRef.publicKeyOrToken!!))
    }

    // === PE-level round trip: method body ===

    @Test
    fun peRoundTrip_methodBodyIl() {
        val ilStream = BlobBuilder()
        val bodies = MethodBodyStreamEncoder(ilStream)
        val code = BlobBuilder()
        val il = InstructionEncoder(code)

        val metadata = MetadataBuilder()
        val hello = metadata.getOrAddUserString("Hello from Kotlin-built PE!")

        il.loadString(hello)
        il.opCode(ILOpCode.RET)
        val bodyOffset = bodies.addMethodBody(il)

        metadata.addModule(0, metadata.getOrAddString("pe-rt"), metadata.getOrAddGuid(Uuid.random()), GuidHandle(0), GuidHandle(0))

        val mainSig = metadata.getOrAddBlob(byteArrayOf(0x00, 0x00, 0x01))
        val main = metadata.addMethodDefinition(0x0096, 0x0000, metadata.getOrAddString("Main"), mainSig, bodyOffset, ParameterHandle(0))
        metadata.addTypeDefinition(
            0u, MetadataTokens.stringHandle(0), metadata.getOrAddString("<Module>"),
            EntityHandle.NIL, MetadataTokens.fieldDefinitionHandle(1), main,
        )

        val header = PEHeaderBuilder(imageCharacteristics = Characteristics.EXECUTABLE_IMAGE.value)
        val peBuilder =
            ManagedPEBuilder(
                header = header,
                metadataRootBuilder = MetadataRootBuilder(metadata),
                ilStream = ilStream,
            )
        val peBlob = BlobBuilder()
        peBuilder.serialize(peBlob)
        val image = peBlob.toArray()

        val per = PEReader(image)
        val reader = MetadataReader(image, per.metadataRootOffset)

        val method = reader.methodDefinition(1)
        assertEquals("Main", method.name)

        val body = per.methodBody(method.rva)
        // ldstr token + ret: тело из одного ldstr (5 байт) + ret (1 байт) = 6
        assertEquals(-1, body.maxStack) // tiny header не хранит maxstack
        val ilBytes = body.getILReader(image).readBytes(body.codeSize)
        assertEquals(0x72, ilBytes[0].toInt() and 0xFF)
        // последний байт — RET (0x2A):
        assertEquals(0x2A, ilBytes[ilBytes.size - 1].toInt() and 0xFF)

        // строка из #US читается обратно:
        val usToken = ((ilBytes[1].toInt() and 0xFF) or ((ilBytes[2].toInt() and 0xFF) shl 8) or
            ((ilBytes[3].toInt() and 0xFF) shl 16) or ((ilBytes[4].toInt() and 0xFF) shl 24)) and 0x00FFFFFF
        val text = reader.getUserString(org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle(usToken))
        assertEquals("Hello from Kotlin-built PE!", text)
    }
}
