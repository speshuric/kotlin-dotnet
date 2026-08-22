// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata tests (Ecma335/MetadataBuilderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Adaptations:
//  - ArgumentNullException for non-null Kotlin parameters is enforced by
//    the type system (GetOrAddErrors, null Version cases);
//  - GetRowCount errors for raw invalid TableIndex values cannot be
//    reproduced (Kotlin enums are closed);
//  - EnC (AddEncLogEntry/AddEncMapEntry) and debug-table facts
//    (AddDocument/AddLocalScope/etc.) are cut per ADR 0009;
//  - GetOrAddDocumentName1/2 belong to the Portable PDB scope (cut);
//  - InvalidOperationException/ArgumentOutOfRangeException/
//    ImageFormatLimitationException map to IllegalStateException/
//    IllegalArgumentException per the fail-fast convention.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.CustomAttributeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val TEST_GUID: Uuid = Uuid.parse("d39f3559-476a-4d1e-b6d2-88e66395230b")

class MetadataBuilderTests {

    // --- Ctor_Errors ---

    @Test
    fun ctor_errors() {
        assertFailsWith<IllegalArgumentException> { MetadataBuilder(-1) }
        assertFailsWith<IllegalArgumentException> { MetadataBuilder(0, -1) }
        assertFailsWith<IllegalArgumentException> { MetadataBuilder(0, 0, -1) }
        assertFailsWith<IllegalArgumentException> { MetadataBuilder(0, 0, 0, -1) }
        assertFailsWith<IllegalArgumentException> { MetadataBuilder(0, 0, 0, 1) } // guid offset must be multiple of 16

        MetadataBuilder(userStringHeapStartOffset = 0x00fffffe) // ok
        assertFailsWith<IllegalArgumentException> { MetadataBuilder(userStringHeapStartOffset = 0x00ffffff) }
    }

    // --- Add (row counts for every table; EnC/debug rows cut per ADR 0009) ---

    @Test
    fun add() {
        val builder = MetadataBuilder()

        builder.addModule(0, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.MODULE))

        builder.addAssembly(StringHandle(0u), AssemblyVersion(0, 0, 0, 0), StringHandle(0u), BlobHandle(0), 0, 0u)
        assertEquals(1, builder.getRowCount(TableIndex.ASSEMBLY))

        val assemblyReference = builder.addAssemblyReference(
            StringHandle(0u), AssemblyVersion(0, 0, 0, 0), StringHandle(0u), BlobHandle(0), 0u, BlobHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.ASSEMBLY_REF))
        assertEquals(1, MetadataTokens.getRowNumber(assemblyReference.toEntityHandle()))

        val typeDefinition = builder.addTypeDefinition(
            0u, StringHandle(0u), StringHandle(0u), EntityHandle(0u),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(0),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.TYPE_DEF))
        assertEquals(1, MetadataTokens.getRowNumber(typeDefinition.toEntityHandle()))

        builder.addTypeLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0), 0, 0u)
        assertEquals(1, builder.getRowCount(TableIndex.CLASS_LAYOUT))

        builder.addInterfaceImplementation(MetadataTokens.typeDefinitionHandle(1), MetadataTokens.typeDefinitionHandle(1).toEntityHandle())
        assertEquals(1, builder.getRowCount(TableIndex.INTERFACE_IMPL))

        builder.addNestedType(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.NESTED_CLASS))

        val typeReference = builder.addTypeReference(EntityHandle.MODULE_DEFINITION, StringHandle(0u), StringHandle(0u))
        assertEquals(1, MetadataTokens.getRowNumber(typeReference.toEntityHandle()))
        assertEquals(1, builder.getRowCount(TableIndex.TYPE_REF))

        builder.addTypeSpecification(BlobHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.TYPE_SPEC))

        builder.addStandaloneSignature(BlobHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.STAND_ALONE_SIG))

        builder.addProperty(0, StringHandle(0u), BlobHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.PROPERTY))

        builder.addPropertyMap(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.PropertyDefinitionHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.PROPERTY_MAP))

        builder.addEvent(0, StringHandle(0u), MetadataTokens.typeDefinitionHandle(1).toEntityHandle())
        assertEquals(1, builder.getRowCount(TableIndex.EVENT))

        builder.addEventMap(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.EventDefinitionHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.EVENT_MAP))

        builder.addConstant(MetadataTokens.fieldDefinitionHandle(1).toEntityHandle(), null)
        assertEquals(1, builder.getRowCount(TableIndex.CONSTANT))

        builder.addMethodSemantics(
            MetadataTokens.eventDefinitionHandle(1).toEntityHandle(), 0,
            org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.METHOD_SEMANTICS))

        builder.addCustomAttribute(
            MetadataTokens.typeDefinitionHandle(1).toEntityHandle(),
            MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), BlobHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.CUSTOM_ATTRIBUTE))

        builder.addMethodSpecification(MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), BlobHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.METHOD_SPEC))

        builder.addModuleReference(StringHandle(0u))
        assertEquals(1, builder.getRowCount(TableIndex.MODULE_REF))

        builder.addParameter(0, StringHandle(0u), 0)
        assertEquals(1, builder.getRowCount(TableIndex.PARAM))

        val genericParameter = builder.addGenericParameter(
            MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), 0, StringHandle(0u), 0,
        )
        assertEquals(1, builder.getRowCount(TableIndex.GENERIC_PARAM))
        assertEquals(1, MetadataTokens.getRowNumber(genericParameter.toEntityHandle()))

        builder.addGenericParameterConstraint(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.GenericParameterHandle(0),
            MetadataTokens.typeDefinitionHandle(1).toEntityHandle(),
        )
        assertEquals(1, builder.getRowCount(TableIndex.GENERIC_PARAM_CONSTRAINT))

        builder.addFieldDefinition(0, StringHandle(0u), BlobHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.FIELD))

        builder.addFieldLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(0), 0)
        assertEquals(1, builder.getRowCount(TableIndex.FIELD_LAYOUT))

        builder.addMarshallingDescriptor(MetadataTokens.fieldDefinitionHandle(1).toEntityHandle(), BlobHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.FIELD_MARSHAL))

        builder.addFieldRelativeVirtualAddress(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(0), 0,
        )
        assertEquals(1, builder.getRowCount(TableIndex.FIELD_RVA))

        val methodDefinition = builder.addMethodDefinition(
            0, 0, StringHandle(0u), BlobHandle(0), 0,
            org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.METHOD_DEF))
        assertEquals(1, MetadataTokens.getRowNumber(methodDefinition.toEntityHandle()))

        builder.addMethodImport(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(1), 0, StringHandle(0u),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleReferenceHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.IMPL_MAP))

        builder.addMethodImplementation(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0),
            MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), MetadataTokens.methodDefinitionHandle(1).toEntityHandle(),
        )
        assertEquals(1, builder.getRowCount(TableIndex.METHOD_IMPL))

        val memberReference = builder.addMemberReference(
            MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), StringHandle(0u), BlobHandle(0),
        )
        assertEquals(1, builder.getRowCount(TableIndex.MEMBER_REF))
        assertEquals(1, MetadataTokens.getRowNumber(memberReference.toEntityHandle()))

        builder.addManifestResource(0u, StringHandle(0u), MetadataTokens.assemblyFileHandle(1).toEntityHandle(), 0u)
        assertEquals(1, builder.getRowCount(TableIndex.MANIFEST_RESOURCE))

        builder.addAssemblyFile(StringHandle(0u), BlobHandle(0), false)
        assertEquals(1, builder.getRowCount(TableIndex.FILE))

        builder.addExportedType(0u, StringHandle(0u), StringHandle(0u), MetadataTokens.assemblyFileHandle(1).toEntityHandle(), 0)
        assertEquals(1, builder.getRowCount(TableIndex.EXPORTED_TYPE))

        builder.addDeclarativeSecurityAttribute(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), 0, BlobHandle(0))
        assertEquals(1, builder.getRowCount(TableIndex.DECL_SECURITY))

        // EnC entries are cut per ADR 0009 (original asserts EncLog/EncMap here)

        assertEquals(0, builder.getRowCount(TableIndex.ASSEMBLY_OS))
        assertEquals(0, builder.getRowCount(TableIndex.ASSEMBLY_PROCESSOR))
        assertEquals(0, builder.getRowCount(TableIndex.ASSEMBLY_REF_OS))
        assertEquals(0, builder.getRowCount(TableIndex.ASSEMBLY_REF_PROCESSOR))
        assertEquals(0, builder.getRowCount(TableIndex.EVENT_PTR))
        assertEquals(0, builder.getRowCount(TableIndex.FIELD_PTR))
        assertEquals(0, builder.getRowCount(TableIndex.METHOD_PTR))
        assertEquals(0, builder.getRowCount(TableIndex.PARAM_PTR))
        assertEquals(0, builder.getRowCount(TableIndex.PROPERTY_PTR))

        val rowCounts = builder.getRowCounts()
        assertEquals(MetadataTokens.TABLE_COUNT, rowCounts.size)
        for (tableIndex in TableIndex.entries) {
            assertEquals(builder.getRowCount(tableIndex), rowCounts[tableIndex.value], tableIndex.name)
        }
    }

    // --- Add_ArgumentErrors (minimal validation parity) ---

    private val badHandleKind: EntityHandle = CustomAttributeHandle.fromRowId(1).toEntityHandle()

    @Test
    fun add_argumentErrors() {
        val builder = MetadataBuilder()

        assertFailsWith<IllegalArgumentException> {
            builder.addTypeDefinition(0u, StringHandle(0u), StringHandle(0u), badHandleKind,
                org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(0),
                org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addInterfaceImplementation(
                org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0), badHandleKind)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addTypeReference(badHandleKind, StringHandle(0u), StringHandle(0u))
        }
        assertFailsWith<IllegalArgumentException> { builder.addEvent(0, StringHandle(0u), badHandleKind) }
        assertFailsWith<IllegalArgumentException> { builder.addConstant(badHandleKind, 0) }
        assertFailsWith<IllegalArgumentException> {
            builder.addMethodSemantics(badHandleKind, 0,
                org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addCustomAttribute(badHandleKind, MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), BlobHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addCustomAttribute(
                MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), badHandleKind, BlobHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addMethodSpecification(badHandleKind, BlobHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addGenericParameter(badHandleKind, 0, StringHandle(0u), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addGenericParameterConstraint(
                org.kotlindotnet.dotnetutils.system.reflection.metadata.GenericParameterHandle(0), badHandleKind)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addMarshallingDescriptor(badHandleKind, BlobHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addMethodImplementation(
                org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0),
                badHandleKind, org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0).toEntityHandle())
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addMemberReference(badHandleKind, StringHandle(0u), BlobHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addManifestResource(0u, StringHandle(0u), badHandleKind, 0u)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addExportedType(0u, StringHandle(0u), StringHandle(0u), badHandleKind, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addDeclarativeSecurityAttribute(badHandleKind, 0, BlobHandle(0))
        }

        assertFailsWith<IllegalArgumentException> {
            builder.addModule(-1, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addModule(UShort.MAX_VALUE.toInt() + 1, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))
        }
        assertFailsWith<IllegalArgumentException> { builder.addParameter(0, StringHandle(0u), -1) }
        assertFailsWith<IllegalArgumentException> {
            builder.addGenericParameter(
                org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle(), 0, StringHandle(0u), -1)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addFieldRelativeVirtualAddress(
                org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(0), -1)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.addMethodDefinition(
                0, 0, StringHandle(0u), BlobHandle(0), -2,
                org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle(0))
        }
    }

    // --- MultipleModuleAssemblyEntries ---

    @Test
    fun multipleModuleAssemblyEntries() {
        val builder = MetadataBuilder()

        builder.addAssembly(StringHandle(0u), AssemblyVersion(0, 0, 0, 0), StringHandle(0u), BlobHandle(0), 0, 0u)
        builder.addModule(0, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))

        assertFailsWith<IllegalStateException> {
            builder.addAssembly(StringHandle(0u), AssemblyVersion(0, 0, 0, 0), StringHandle(0u), BlobHandle(0), 0, 0u)
        }
        assertFailsWith<IllegalStateException> {
            builder.addModule(0, StringHandle(0u), GuidHandle(0), GuidHandle(0), GuidHandle(0))
        }
    }

    // --- Add_BadValues (flags are silently truncated, minimal validation) ---

    @Test
    fun add_badValues() {
        val builder = MetadataBuilder()

        builder.addAssembly(StringHandle(0u), AssemblyVersion(0, 0, 0, 0), StringHandle(0u), BlobHandle(0), -1, (-1).toUInt())
        builder.addAssemblyReference(StringHandle(0u), AssemblyVersion(0, 0, 0, 0), StringHandle(0u), BlobHandle(0), (-1).toUInt(), BlobHandle(0))
        builder.addTypeDefinition(
            (-1).toUInt(), StringHandle(0u), StringHandle(0u),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle(),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(0),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0),
        )
        builder.addProperty(-1, StringHandle(0u), BlobHandle(0))
        builder.addEvent(-1, StringHandle(0u), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle())
        builder.addMethodSemantics(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.EventDefinitionHandle(0).toEntityHandle(), -1,
            org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0),
        )
        builder.addParameter(-1, StringHandle(0u), 0)
        builder.addGenericParameter(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle(), -1, StringHandle(0u), 0)
        builder.addFieldDefinition(-1, StringHandle(0u), BlobHandle(0))
        builder.addMethodDefinition(
            -1, -1, StringHandle(0u), BlobHandle(0), -1,
            org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle(0),
        )
        builder.addMethodImport(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle(0), -1, StringHandle(0u),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleReferenceHandle(0),
        )
        builder.addManifestResource((-1).toUInt(), StringHandle(0u), org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyFileHandle(0).toEntityHandle(), 0u)
        builder.addExportedType(
            (-1).toUInt(), StringHandle(0u), StringHandle(0u),
            org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyFileHandle(0).toEntityHandle(), 0,
        )
        builder.addDeclarativeSecurityAttribute(
            org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle(), -1, BlobHandle(0))
    }

    // --- Heaps_Empty ---

    @Test
    fun heaps_empty() {
        val mdBuilder = MetadataBuilder()
        val serialized = mdBuilder.getSerializedMetadata(MetadataBuilder.EMPTY_ROW_COUNTS, 12)

        val builder = BlobBuilder()
        mdBuilder.writeHeapsTo(builder, serialized.stringHeap)

        assertContentEquals(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x00, // #String
                0x00, 0x00, 0x00, 0x00, // #US
                // #Guid (empty)
                0x00, 0x00, 0x00, 0x00, // #Blob
            ),
            builder.toArray(),
        )
    }

    // --- Heaps ---

    @Test
    fun heaps() {
        val mdBuilder = MetadataBuilder()

        val g0 = mdBuilder.getOrAddGuid(Uuid.NIL)
        assertTrue(g0.isNil)
        assertEquals(0, g0.index)

        val g1 = mdBuilder.getOrAddGuid(TEST_GUID)
        assertEquals(1, g1.index)

        val s0 = mdBuilder.getOrAddString("")
        assertFalse(s0.isVirtual)
        assertEquals(0, s0.getWriterVirtualIndex())

        val s1 = mdBuilder.getOrAddString("foo")
        assertTrue(s1.isVirtual)
        assertEquals(1, s1.getWriterVirtualIndex())

        val us0 = mdBuilder.getOrAddUserString("")
        assertEquals(1, us0.getHeapOffset())

        val us1 = mdBuilder.getOrAddUserString("bar")
        assertEquals(3, us1.getHeapOffset())

        val b0 = mdBuilder.getOrAddBlob(byteArrayOf())
        assertEquals(0, b0.getHeapOffset())

        val b1 = mdBuilder.getOrAddBlob(byteArrayOf(1, 2))
        assertEquals(1, b1.getHeapOffset())

        val serialized = mdBuilder.getSerializedMetadata(MetadataBuilder.EMPTY_ROW_COUNTS, 12)

        assertEquals(0, mdBuilder.serializeHandle(g0))
        assertEquals(1, mdBuilder.serializeHandle(g1))
        assertEquals(0, mdBuilder.serializeHandle(serialized.stringMap, s0))
        assertEquals(1, mdBuilder.serializeHandle(serialized.stringMap, s1))
        assertEquals(1, mdBuilder.serializeHandle(us0))
        assertEquals(3, mdBuilder.serializeHandle(us1))
        assertEquals(0, mdBuilder.serializeHandle(b0))
        assertEquals(1, mdBuilder.serializeHandle(b1))

        val heaps = BlobBuilder()
        mdBuilder.writeHeapsTo(heaps, serialized.stringHeap)

        assertContentEquals(
            byteArrayOf(
                // #String
                0x00,
                0x66, 0x6F, 0x6F, 0x00,
                0x00, 0x00, 0x00,
                // #US
                0x00,
                0x01, 0x00,
                0x07, 0x62, 0x00, 0x61, 0x00, 0x72, 0x00, 0x00,
                0x00,
                // #Guid
                0x59.toByte(), 0x35, 0x9F.toByte(), 0xD3.toByte(), 0x6A, 0x47, 0x1E, 0x4D, 0xB6.toByte(), 0xD2.toByte(), 0x88.toByte(), 0xE6.toByte(), 0x63, 0x95.toByte(), 0x23, 0x0B,
                // #Blob
                0x00, 0x02, 0x01, 0x02,
            ),
            heaps.toArray(),
        )
    }

    // --- Heaps_StartOffsets ---

    @Test
    fun heaps_startOffsets() {
        val mdBuilder = MetadataBuilder(
            userStringHeapStartOffset = 0x10,
            stringHeapStartOffset = 0x20,
            blobHeapStartOffset = 0x30,
            guidHeapStartOffset = 0x40,
        )

        val g = mdBuilder.getOrAddGuid(TEST_GUID)
        assertEquals(5, g.index)

        val s0 = mdBuilder.getOrAddString("")
        assertFalse(s0.isVirtual)
        assertEquals(0, s0.getWriterVirtualIndex())

        val s1 = mdBuilder.getOrAddString("foo")
        assertTrue(s1.isVirtual)
        assertEquals(1, s1.getWriterVirtualIndex())

        val us0 = mdBuilder.getOrAddUserString("")
        assertEquals(0x11, us0.getHeapOffset())

        val us1 = mdBuilder.getOrAddUserString("bar")
        assertEquals(0x13, us1.getHeapOffset())

        val b0 = mdBuilder.getOrAddBlob(byteArrayOf())
        assertEquals(0, b0.getHeapOffset())

        val b1 = mdBuilder.getOrAddBlob(byteArrayOf(1, 2))
        assertEquals(0x31, b1.getHeapOffset())

        val serialized = mdBuilder.getSerializedMetadata(MetadataBuilder.EMPTY_ROW_COUNTS, 12)

        assertEquals(5, mdBuilder.serializeHandle(g))
        assertEquals(0, mdBuilder.serializeHandle(serialized.stringMap, s0))
        assertEquals(0x21, mdBuilder.serializeHandle(serialized.stringMap, s1))
        assertEquals(0x11, mdBuilder.serializeHandle(us0))
        assertEquals(0x13, mdBuilder.serializeHandle(us1))
        assertEquals(0, mdBuilder.serializeHandle(b0))
        assertEquals(0x31, mdBuilder.serializeHandle(b1))

        val heaps = BlobBuilder()
        mdBuilder.writeHeapsTo(heaps, serialized.stringHeap)

        // #String(8) + #US(12) + #Guid(64 zeros + 16 guid) + #Blob(4) = 104
        val expected = ByteArray(104)
        // #String
        expected[0] = 0x00
        expected[1] = 0x66; expected[2] = 0x6F; expected[3] = 0x6F; expected[4] = 0x00
        // (padding 5..7 zero)
        // #US
        expected[8] = 0x00
        expected[9] = 0x01; expected[10] = 0x00
        expected[11] = 0x07; expected[12] = 0x62; expected[13] = 0x00; expected[14] = 0x61
        expected[15] = 0x00; expected[16] = 0x72; expected[17] = 0x00; expected[18] = 0x00
        expected[19] = 0x00
        // #Guid: 64 bytes of zeros (guidHeapStartOffset=0x40), then the GUID bytes
        val guidBytes = ByteArray(16).also { out -> BlobUtilitiesForTests.writeGuid(out, 0, TEST_GUID) }
        System.arraycopy(guidBytes, 0, expected, 20 + 0x40, 16)
        // #Blob at offset 20+80=100
        expected[100] = 0x00; expected[101] = 0x02; expected[102] = 0x01; expected[103] = 0x02

        assertContentEquals(expected, heaps.toArray())
    }

    // --- Heaps_Reserve ---

    @Test
    fun heaps_reserve() {
        val mdBuilder = MetadataBuilder()

        val guid = mdBuilder.reserveGuid()
        val us = mdBuilder.reserveUserString(3)

        assertEquals(MetadataTokens.guidHandle(1), guid.handle)
        assertEquals(MetadataTokens.userStringHandle(1), us.handle)

        val serialized = mdBuilder.getSerializedMetadata(MetadataBuilder.EMPTY_ROW_COUNTS, 12)

        val builder = BlobBuilder()
        mdBuilder.writeHeapsTo(builder, serialized.stringHeap)

        assertContentEquals(
            byteArrayOf(
                // #String
                0x00, 0x00, 0x00, 0x00,
                // #US (1 prefix + 1 compressed-len + 7 reserved + 3 pad)
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                // #Guid
                *ByteArray(16),
                // #Blob
                0x00, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )

        guid.createWriter().writeGuid(TEST_GUID)
        us.createWriter().writeUserString("bar")

        assertContentEquals(
            byteArrayOf(
                // #String
                0x00, 0x00, 0x00, 0x00,
                // #US
                0x00, 0x07, 0x62, 0x00, 0x61, 0x00, 0x72, 0x00, 0x00, 0x00, 0x00, 0x00,
                // #Guid
                0x59.toByte(), 0x35, 0x9F.toByte(), 0xD3.toByte(), 0x6A, 0x47, 0x1E, 0x4D, 0xB6.toByte(), 0xD2.toByte(), 0x88.toByte(), 0xE6.toByte(), 0x63, 0x95.toByte(), 0x23, 0x0B,
                // #Blob
                0x00, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
    }

    // --- HeapOverflow_UserString (ActiveIssue roslyn#9852 upstream; our port enforces limit) ---

    @Test
    fun heapOverflow_userString() {
        val veryLargeString = "x".repeat(0x00fffff0 / 2)

        val builder1 = MetadataBuilder()
        assertEquals(0x70000001, MetadataTokens.getToken(builder1.getOrAddUserString(veryLargeString).toHandle()))
        assertEquals(0x70fffff6, MetadataTokens.getToken(builder1.getOrAddUserString(veryLargeString + "z").toHandle()))
        assertFailsWith<IllegalStateException> { builder1.getOrAddUserString("12") }

        val builder2 = MetadataBuilder()
        assertEquals(0x70000001, MetadataTokens.getToken(builder2.getOrAddUserString("123").toHandle()))
        assertEquals(0x70000009, MetadataTokens.getToken(builder2.getOrAddUserString(veryLargeString).toHandle()))
        assertEquals(0x70fffffe, MetadataTokens.getToken(builder2.getOrAddUserString("4").toHandle()))

        val builder3 = MetadataBuilder(userStringHeapStartOffset = 0x00fffffe)
        assertEquals(0x70ffffff, MetadataTokens.getToken(builder3.getOrAddUserString("1").toHandle()))

        val builder4 = MetadataBuilder(userStringHeapStartOffset = 0x00fffff7)
        assertEquals(0x70fffff8, MetadataTokens.getToken(builder4.getOrAddUserString("1").toHandle()))
        assertEquals(0x70fffffc, MetadataTokens.getToken(builder4.getOrAddUserString("2").toHandle()))
        assertFailsWith<IllegalStateException> { builder4.getOrAddUserString("3") } // hits the limit exactly

        val builder5 = MetadataBuilder(userStringHeapStartOffset = 0x00fffff8)
        assertEquals(0x70fffff9, MetadataTokens.getToken(builder5.getOrAddUserString("1").toHandle()))
        assertEquals(0x70fffffd, MetadataTokens.getToken(builder5.getOrAddUserString("2").toHandle()))
    }

    // --- SetCapacity ---

    @Test
    fun setCapacity() {
        val builder = MetadataBuilder()

        builder.getOrAddString("11111")
        builder.getOrAddGuid(Uuid.random())
        builder.getOrAddBlobUTF8("2222")
        builder.getOrAddUserString("3333")

        repeat(3) {
            builder.addMethodDefinition(
                0, 0, StringHandle(0u), BlobHandle(0), 0,
                org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle(0))
        }

        builder.setTableCapacity(TableIndex.METHOD_DEF, 0)
        builder.setTableCapacity(TableIndex.METHOD_DEF, 1)
        builder.setTableCapacity(TableIndex.METHOD_DEF, 1000)
        // negative rowCount / invalid TableIndex are impossible in Kotlin

        builder.setCapacity(HeapIndex.STRING, 3)
        builder.setCapacity(HeapIndex.STRING, 1000)
        builder.setCapacity(HeapIndex.BLOB, 3)
        builder.setCapacity(HeapIndex.BLOB, 1000)
        builder.setCapacity(HeapIndex.GUID, 3)
        builder.setCapacity(HeapIndex.GUID, 1000)
        builder.setCapacity(HeapIndex.USER_STRING, 3)
        builder.setCapacity(HeapIndex.USER_STRING, 1000)
        builder.setCapacity(HeapIndex.STRING, 0)
        assertFailsWith<IllegalArgumentException> { builder.setCapacity(HeapIndex.STRING, -1) }
    }

    // --- Validate* tables ---

    @Test
    fun validateClassLayoutTable() {
        var builder = MetadataBuilder()
        builder.addTypeLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(2), 1, 1u)
        builder.addTypeLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(1), 1, 1u)
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addTypeLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(1), 1, 1u)
        builder.addTypeLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(1), 1, 1u)
        assertFailsWith<IllegalStateException> { builder.validateOrder() }
    }

    @Test
    fun validateFieldLayoutTable() {
        var builder = MetadataBuilder()
        builder.addFieldLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(2), 1)
        builder.addFieldLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(1), 1)
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addFieldLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(1), 1)
        builder.addFieldLayout(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(1), 1)
        assertFailsWith<IllegalStateException> { builder.validateOrder() }
    }

    @Test
    fun validateFieldRvaTable() {
        var builder = MetadataBuilder()
        builder.addFieldRelativeVirtualAddress(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(2), 1)
        builder.addFieldRelativeVirtualAddress(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(1), 1)
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addFieldRelativeVirtualAddress(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(1), 1)
        builder.addFieldRelativeVirtualAddress(org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle(1), 1)
        assertFailsWith<IllegalStateException> { builder.validateOrder() }
    }

    @Test
    fun validateGenericParamTable() {
        fun build(owner1: EntityHandle, owner2: EntityHandle, index1: Int = 0, index2: Int = 0): MetadataBuilder {
            val b = MetadataBuilder()
            b.addGenericParameter(owner1, 0, StringHandle(0u), index1)
            b.addGenericParameter(owner2, 0, StringHandle(0u), index2)
            return b
        }

        val td = { i: Int -> MetadataTokens.typeDefinitionHandle(i).toEntityHandle() }
        val md = { i: Int -> MetadataTokens.methodDefinitionHandle(i).toEntityHandle() }

        assertFailsWith<IllegalStateException> { build(td(2), td(1)).validateOrder() }
        assertFailsWith<IllegalStateException> { build(md(2), md(1)).validateOrder() }
        assertFailsWith<IllegalStateException> { build(td(2), md(1)).validateOrder() }
        assertFailsWith<IllegalStateException> { build(md(2), td(1)).validateOrder() }
        assertFailsWith<IllegalStateException> { build(td(1), td(1)).validateOrder() }
        assertFailsWith<IllegalStateException> { build(td(1), td(1), index1 = 1, index2 = 0).validateOrder() }
        assertFailsWith<IllegalStateException> { build(md(1), td(1)).validateOrder() }
    }

    @Test
    fun validateGenericParamConstraintTable() {
        var builder = MetadataBuilder()
        builder.addGenericParameterConstraint(MetadataTokens.genericParameterHandle(2), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle())
        builder.addGenericParameterConstraint(MetadataTokens.genericParameterHandle(1), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle())
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addGenericParameterConstraint(MetadataTokens.genericParameterHandle(1), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle())
        builder.addGenericParameterConstraint(MetadataTokens.genericParameterHandle(1), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0).toEntityHandle())
        builder.validateOrder() // ok
    }

    @Test
    fun validateImplMapTable() {
        var builder = MetadataBuilder()
        builder.addMethodImport(MetadataTokens.methodDefinitionHandle(2), 0, StringHandle(0u), org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleReferenceHandle(0))
        builder.addMethodImport(MetadataTokens.methodDefinitionHandle(1), 0, StringHandle(0u), org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleReferenceHandle(0))
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addMethodImport(MetadataTokens.methodDefinitionHandle(1), 0, StringHandle(0u), org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleReferenceHandle(0))
        builder.addMethodImport(MetadataTokens.methodDefinitionHandle(1), 0, StringHandle(0u), org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleReferenceHandle(0))
        assertFailsWith<IllegalStateException> { builder.validateOrder() }
    }

    @Test
    fun validateInterfaceImplTable() {
        var builder = MetadataBuilder()
        builder.addInterfaceImplementation(MetadataTokens.typeDefinitionHandle(2), MetadataTokens.typeDefinitionHandle(1).toEntityHandle())
        builder.addInterfaceImplementation(MetadataTokens.typeDefinitionHandle(1), MetadataTokens.typeDefinitionHandle(1).toEntityHandle())
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addInterfaceImplementation(MetadataTokens.typeDefinitionHandle(1), MetadataTokens.typeDefinitionHandle(2).toEntityHandle())
        builder.addInterfaceImplementation(MetadataTokens.typeDefinitionHandle(1), MetadataTokens.typeDefinitionHandle(1).toEntityHandle())
        builder.validateOrder() // ok
    }

    @Test
    fun validateMethodImplTable() {
        var builder = MetadataBuilder()
        builder.addMethodImplementation(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(2), MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), MetadataTokens.methodDefinitionHandle(1).toEntityHandle())
        builder.addMethodImplementation(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(1), MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), MetadataTokens.methodDefinitionHandle(1).toEntityHandle())
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addMethodImplementation(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(1), MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), MetadataTokens.methodDefinitionHandle(1).toEntityHandle())
        builder.addMethodImplementation(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(1), MetadataTokens.methodDefinitionHandle(1).toEntityHandle(), MetadataTokens.methodDefinitionHandle(1).toEntityHandle())
        builder.validateOrder() // ok
    }

    @Test
    fun validateNestedClassTable() {
        var builder = MetadataBuilder()
        builder.addNestedType(MetadataTokens.typeDefinitionHandle(2), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0))
        builder.addNestedType(MetadataTokens.typeDefinitionHandle(1), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0))
        assertFailsWith<IllegalStateException> { builder.validateOrder() }

        builder = MetadataBuilder()
        builder.addNestedType(MetadataTokens.typeDefinitionHandle(1), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0))
        builder.addNestedType(MetadataTokens.typeDefinitionHandle(1), org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle(0))
        assertFailsWith<IllegalStateException> { builder.validateOrder() }
    }


}

/** Test-local helper mirroring [org.kotlindotnet.dotnetutils.system.reflection.internal.BlobUtilities.writeGuid]. */
private object BlobUtilitiesForTests {
    fun writeGuid(buffer: ByteArray, start: Int, value: Uuid) {
        val b = value.toByteArray()
        buffer[start + 0] = b[3]; buffer[start + 1] = b[2]; buffer[start + 2] = b[1]; buffer[start + 3] = b[0]
        buffer[start + 4] = b[5]; buffer[start + 5] = b[4]
        buffer[start + 6] = b[7]; buffer[start + 7] = b[6]
        for (i in 8 until 16) buffer[start + i] = b[i]
    }
}
