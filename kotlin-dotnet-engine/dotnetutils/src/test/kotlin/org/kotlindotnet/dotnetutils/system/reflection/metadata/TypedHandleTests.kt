// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Adapted from System.Reflection.Metadata tests (Metadata/HandleTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Iteration I1 scope: facts over the typed-handle structs.
// MethodDefToDebugInfo is cut (debug/PDB scope excluded by ADR 0009).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.StringKind
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypedHandleTests {

    // --- Conversions_Handles (subset) ---

    @Test
    fun conversions_EntityHandles_rowId() {
        assertEquals(1, ModuleDefinitionHandle.fromEntityHandle(EntityHandle(TokenTypeIds.MODULE or 1u)).rowId)
        assertEquals(
            0x00ffffff,
            ModuleDefinitionHandle.fromEntityHandle(EntityHandle(TokenTypeIds.MODULE or 0x00ffffffu)).rowId,
        )
        assertEquals(1, AssemblyDefinitionHandle.fromEntityHandle(EntityHandle(TokenTypeIds.ASSEMBLY or 1u)).rowId)
        assertEquals(1, InterfaceImplementationHandle.fromEntityHandle(EntityHandle(TokenTypeIds.INTERFACE_IMPL or 1u)).rowId)
        assertEquals(1, MethodDefinitionHandle.fromEntityHandle(EntityHandle(TokenTypeIds.METHOD_DEF or 1u)).rowId)
        assertEquals(1, TypeDefinitionHandle.fromEntityHandle(EntityHandle(TokenTypeIds.TYPE_DEF or 1u)).rowId)
        assertEquals(1, TypeReferenceHandle.fromEntityHandle(EntityHandle(TokenTypeIds.TYPE_REF or 1u)).rowId)
        assertEquals(1, MemberReferenceHandle.fromEntityHandle(EntityHandle(TokenTypeIds.MEMBER_REF or 1u)).rowId)
        assertEquals(1, FieldDefinitionHandle.fromEntityHandle(EntityHandle(TokenTypeIds.FIELD_DEF or 1u)).rowId)
        assertEquals(1, ParameterHandle.fromEntityHandle(EntityHandle(TokenTypeIds.PARAM_DEF or 1u)).rowId)
        assertEquals(1, GenericParameterHandle.fromEntityHandle(EntityHandle(TokenTypeIds.GENERIC_PARAM or 1u)).rowId)
        assertEquals(1, AssemblyReferenceHandle.fromEntityHandle(EntityHandle(TokenTypeIds.ASSEMBLY_REF or 1u)).rowId)
    }

    @Test
    fun conversions_Handles_rowIdAndHeapOffsets() {
        assertEquals(1, ModuleDefinitionHandle.fromHandle(Handle(HandleType.MODULE.toUByte(), 1)).rowId)
        assertEquals(0x00ffffff, ModuleDefinitionHandle.fromHandle(Handle(HandleType.MODULE.toUByte(), 0x00ffffff)).rowId)
        assertEquals(1, AssemblyDefinitionHandle.fromHandle(Handle(HandleType.ASSEMBLY.toUByte(), 1)).rowId)
        assertEquals(1, MethodDefinitionHandle.fromHandle(Handle(HandleType.METHOD_DEF.toUByte(), 1)).rowId)
        assertEquals(1, TypeReferenceHandle.fromHandle(Handle(HandleType.TYPE_REF.toUByte(), 1)).rowId)

        // heap handles
        assertEquals(1, UserStringHandle.fromHandle(Handle(HandleType.USER_STRING.toUByte(), 1)).getHeapOffset())
        assertEquals(0x00ffffff, UserStringHandle.fromHandle(Handle(HandleType.USER_STRING.toUByte(), 0x00ffffff)).getHeapOffset())
        assertEquals(1, GuidHandle.fromHandle(Handle(HandleType.GUID.toUByte(), 1)).index)
        assertEquals(0x1fffffff, GuidHandle.fromHandle(Handle(HandleType.GUID.toUByte(), 0x1fffffff)).index)
        assertEquals(1, NamespaceDefinitionHandle.fromHandle(Handle(HandleType.NAMESPACE.toUByte(), 1)).getHeapOffset())
        assertEquals(0x1fffffff, NamespaceDefinitionHandle.fromHandle(Handle(HandleType.NAMESPACE.toUByte(), 0x1fffffff)).getHeapOffset())
        assertEquals(1, StringHandle.fromHandle(Handle(HandleType.STRING.toUByte(), 1)).getHeapOffset())
        assertEquals(0x1fffffff, StringHandle.fromHandle(Handle(HandleType.STRING.toUByte(), 0x1fffffff)).getHeapOffset())
        assertEquals(1, BlobHandle.fromHandle(Handle(HandleType.BLOB.toUByte(), 1)).getHeapOffset())
        assertEquals(0x1fffffff, BlobHandle.fromHandle(Handle(HandleType.BLOB.toUByte(), 0x1fffffff)).getHeapOffset())
    }

    @Test
    fun conversions_VirtualHandles_failFast() {
        // C# expects InvalidCastException; our port fails fast with IllegalStateException.
        assertFailsWith<IllegalStateException> {
            ModuleDefinitionHandle.fromHandle(Handle((HandleType.VIRTUAL_BIT or HandleType.MODULE).toUByte(), 1))
        }
        assertFailsWith<IllegalStateException> {
            TypeDefinitionHandle.fromHandle(Handle((HandleType.VIRTUAL_BIT or HandleType.TYPE_DEF).toUByte(), 1))
        }
        assertFailsWith<IllegalStateException> {
            UserStringHandle.fromHandle(Handle((HandleType.VIRTUAL_BIT or HandleType.USER_STRING).toUByte(), 1))
        }
        assertFailsWith<IllegalStateException> {
            GuidHandle.fromHandle(Handle((HandleType.VIRTUAL_BIT or HandleType.GUID).toUByte(), 1))
        }
    }

    @Test
    fun wrongKindConversions_failFast() {
        assertFailsWith<IllegalStateException> {
            TypeDefinitionHandle.fromHandle(org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleDefinitionHandle.fromRowId(1).toHandle())
        }
        assertFailsWith<IllegalStateException> {
            BlobHandle.fromHandle(StringHandle.fromOffset(5).toHandle())
        }
    }

    // --- IsNil (subset) ---

    @Test
    fun isNil() {
        assertFalse(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle.fromRowId(1).isNil)
        assertTrue(org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle.fromRowId(0).isNil)
        assertFalse(org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle.fromRowId(1).isNil)
        assertTrue(org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle.fromRowId(0).isNil)
        assertFalse(org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle.fromRowId(1).isNil)
        assertTrue(org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle.fromRowId(0).isNil)

        val entity = EntityHandle(TokenTypeIds.TYPE_DEF or 1u)
        val nilEntity = EntityHandle(TokenTypeIds.TYPE_DEF or 0u)
        assertFalse(entity.isNil)
        assertTrue(nilEntity.isNil)

        // heaps:
        assertTrue(StringHandle.fromOffset(0).isNil)
        assertFalse(StringHandle.fromOffset(1).isNil)
        assertTrue(BlobHandle.fromOffset(0).isNil)
        assertTrue(UserStringHandle.fromOffset(0).isNil)
        assertTrue(GuidHandle.fromIndex(0).isNil)
    }

    // --- StringKinds ---

    @Test
    fun stringKinds() {
        val str = StringHandle.fromOffset(123)
        assertEquals(StringKind.PLAIN, str.stringKind)
        assertFalse(str.isVirtual)
        assertEquals(123, str.getHeapOffset())
        assertEquals(str, str.toHandle().let(StringHandle::fromHandle))
        assertEquals(0x78, str.toHandle().vType.toInt())
        assertEquals(123, str.toHandle().offset)

        val vstr = StringHandle.fromVirtualIndex(StringHandle.VirtualIndex.ATTRIBUTE_TARGETS)
        assertEquals(StringKind.VIRTUAL, vstr.stringKind)
        assertTrue(vstr.isVirtual)
        assertEquals(StringHandle.VirtualIndex.ATTRIBUTE_TARGETS.ordinal, vstr.getVirtualIndex().ordinal)
        assertEquals(vstr, vstr.toHandle().let(StringHandle::fromHandle))
        assertEquals(0xF8, vstr.toHandle().vType.toInt())

        val dot = StringHandle.fromOffset(123).withDotTermination()
        assertEquals(StringKind.DOT_TERMINATED, dot.stringKind)
        assertFalse(dot.isVirtual)
        assertEquals(123, dot.getHeapOffset())
        assertEquals(dot, dot.toHandle().let(StringHandle::fromHandle))
        assertEquals(0x79, dot.toHandle().vType.toInt())
        assertEquals(123, dot.toHandle().offset)

        val winrtPrefix = StringHandle.fromOffset(123).withWinRTPrefix()
        assertEquals(StringKind.WIN_RT_PREFIXED, winrtPrefix.stringKind)
        assertTrue(winrtPrefix.isVirtual)
        assertEquals(123, winrtPrefix.getHeapOffset())
        assertEquals(winrtPrefix, winrtPrefix.toHandle().let(StringHandle::fromHandle))
        assertEquals(0xF9, winrtPrefix.toHandle().vType.toInt())
        assertEquals(123, winrtPrefix.toHandle().offset)
    }

    // --- NamespaceKinds ---

    @Test
    fun namespaceKinds() {
        val full = NamespaceDefinitionHandle.fromFullNameOffset(123)
        assertFalse(full.isVirtual)
        assertEquals(123, full.getHeapOffset())
        assertEquals(full, full.toHandle().let(NamespaceDefinitionHandle::fromHandle))
        assertEquals(0x7C, full.toHandle().vType.toInt())
        assertEquals(123, full.toHandle().offset)

        val virtual = NamespaceDefinitionHandle.fromVirtualIndex(123u)
        assertTrue(virtual.isVirtual)
        assertEquals(virtual, virtual.toHandle().let(NamespaceDefinitionHandle::fromHandle))
        assertEquals(0xFC, virtual.toHandle().vType.toInt())
        assertEquals(123, virtual.toHandle().offset)

        val maxIdx = UInt.MAX_VALUE shr 3
        val virtual2 = NamespaceDefinitionHandle.fromVirtualIndex(maxIdx)
        assertTrue(virtual2.isVirtual)
        assertEquals(maxIdx.toInt(), virtual2.toHandle().offset)

        assertFailsWith<IllegalStateException> { NamespaceDefinitionHandle.fromVirtualIndex(maxIdx + 1u) }
    }

    // --- companion constants on Handle/EntityHandle ---

    @Test
    fun moduleAndAssemblyDefinitionConstants() {
        assertEquals(HandleKind.MODULE_DEFINITION, Handle.MODULE_DEFINITION.kind)
        assertEquals(1, Handle.MODULE_DEFINITION.rowId)
        assertEquals(HandleKind.ASSEMBLY_DEFINITION, EntityHandle.ASSEMBLY_DEFINITION.kind)
        assertEquals(1, EntityHandle.MODULE_DEFINITION.rowId)
    }
}
