// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Test fixture: emits a runnable "hello world" managed EXE image
// using only this module (MetadataBuilder + MethodBodyStreamEncoder +
// ManagedPEBuilder). See adr/0009-srm-port-to-kotlin.md and
// TODO/I-dotnetutils/REMAINING-ROADMAP.md §B.2.
//
// Deviations:
//  - BlobEncoders (I7) are not ported yet, so method signature blobs
//    are hand-assembled from ECMA-335 element type codes;
//  - deterministic content id is fixed so rebuilds are byte-stable.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import kotlin.uuid.Uuid
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobContentId
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
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
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeReference
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.Characteristics
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.CorFlags
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.ManagedPEBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.PEHeaderBuilder

/** Emits a minimal "hello world" console EXE image entirely in Kotlin. */
object HelloWorldImage {
    const val HELLO_TEXT: String = "Hello from Kotlin-built PE!"

    // Deterministic content id for reproducible builds/tests.
    val CONTENT_ID_GUID: Uuid = Uuid.parse("4b5ea1a7-9a2f-4f0e-8d3c-6f1e2a9b0c41")
    const val CONTENT_ID_STAMP: UInt = 0x5F3759DFu

    /**
     * Builds the full PE image bytes of a console application whose entry
     * point prints [HELLO_TEXT] via System.Console::WriteLine(string).
     */
    fun buildImage(): ByteArray {
        val metadata = MetadataBuilder()

        // Module row (row 1 is mandatory):
        metadata.addModule(
            0,
            metadata.getOrAddString("m1-hello"),
            metadata.getOrAddGuid(CONTENT_ID_GUID),
            GuidHandle.fromIndex(0),
            GuidHandle.fromIndex(0),
        )

        // Assembly row:
        metadata.addAssembly(
            metadata.getOrAddString("HelloImage"),
            AssemblyVersion(1, 0, 0, 0),
            StringHandle.fromRaw(0u),
            BlobHandle.fromOffset(0),
            0,
            0u,
        )

        // Public key tokens of System.Runtime / System.Console (BCL assemblies are strongly named).
        val bclToken = byteArrayOf(0xB0.toByte(), 0x3F.toByte(), 0x5F.toByte(), 0x7F.toByte(), 0x11, 0xD5.toByte(), 0x0A.toByte(), 0x3A.toByte())

        // Assembly references (.NET Base Class Libraries):
        val systemRuntimeRef =
            metadata.addAssemblyReference(
                name = metadata.getOrAddString("System.Runtime"),
                version = AssemblyVersion(8, 0, 0, 0),
                culture = StringHandle.fromRaw(0u),
                publicKeyOrToken = metadata.getOrAddBlob(bclToken),
                flags = 0u,
                hashValue = BlobHandle.fromOffset(0),
            )
        @Suppress("UNUSED_VARIABLE")
        val systemConsoleRef =
            metadata.addAssemblyReference(
                name = metadata.getOrAddString("System.Console"),
                version = AssemblyVersion(8, 0, 0, 0),
                culture = StringHandle.fromRaw(0u),
                publicKeyOrToken = metadata.getOrAddBlob(bclToken),
                flags = 0u,
                hashValue = BlobHandle.fromOffset(0),
            )
        // TypeRef System.Object [System.Runtime] (base class):
        val objectTypeRef =
            metadata.addTypeReference(
                systemRuntimeRef.toEntityHandle(),
                metadata.getOrAddString("System"),
                metadata.getOrAddString("Object"),
            )
        val consoleTypeRef =
            metadata.addTypeReference(
                systemConsoleRef.toEntityHandle(),
                metadata.getOrAddString("System"),
                metadata.getOrAddString("Console"),
            )

        // MemberRef System.Console::WriteLine(string).
        // Hand-built method signature blob (BlobEncoder comes in I7):
        //   DEFAULT calling convention (0x00), 1 parameter,
        //   void return (ELEMENT_TYPE_VOID = 0x01), string param (0x0E).
        val writeLineMemberRef =
            metadata.addMemberReference(
                consoleTypeRef.toEntityHandle(),
                metadata.getOrAddString("WriteLine"),
                metadata.getOrAddBlob(byteArrayOf(0x00, 0x01, 0x01, 0x0E)),
            )

        // IL code of Program::Main:
        val ilBuilder = BlobBuilder()
        val bodyStream = MethodBodyStreamEncoder(ilBuilder)
        val code = BlobBuilder()
        val il = InstructionEncoder(code)

        il.loadString(metadata.getOrAddUserString(HELLO_TEXT))
        il.call(writeLineMemberRef)
        il.opCode(ILOpCode.RET)

        val mainBodyOffset = bodyStream.addMethodBody(il)

        // MethodDef Program::Main (static void).
        // Hand-built signature: DEFAULT conv, 0 parameters, void return.
        val mainSigBlob = metadata.getOrAddBlob(byteArrayOf(0x00, 0x00, 0x01))
        val mainMethodDef =
            metadata.addMethodDefinition(
                0x0096, // Public | Static (0x0010!) | HideBySig
                0x0000, // IL | Managed
                metadata.getOrAddString("Main"),
                mainSigBlob,
                mainBodyOffset,
                org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle.fromRowId(0),
            )

        // TypeDefs: <Module> (mandatory row 1) then Program.
        metadata.addTypeDefinition(
            0u,
            StringHandle.fromRaw(0u),
            metadata.getOrAddString("<Module>"),
            EntityHandle(0u),
            MetadataTokens.fieldDefinitionHandle(1),
            mainMethodDef,
        )
        metadata.addTypeDefinition(
            0x00100001u, // Public | AutoLayout | AnsiClass | Class
            StringHandle.fromRaw(0u), // no namespace
            metadata.getOrAddString("Program"),
            objectTypeRef.toEntityHandle(),
            MetadataTokens.fieldDefinitionHandle(1),
            mainMethodDef,
        )

        val peHeader = PEHeaderBuilder(imageCharacteristics = Characteristics.EXECUTABLE_IMAGE.value)

        val peBuilder =
            ManagedPEBuilder(
                header = peHeader,
                metadataRootBuilder = MetadataRootBuilder(metadata),
                ilStream = ilBuilder,
                entryPoint = mainMethodDef,
                flags = CorFlags.IL_ONLY.value,
                deterministicIdProvider = { BlobContentId(CONTENT_ID_GUID, CONTENT_ID_STAMP) },
            )

        val peBlob = BlobBuilder()
        peBuilder.serialize(peBlob)
        return peBlob.toArray()
    }
}
