// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Adapted from System.Reflection.Metadata tests (Metadata/HandleTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Iteration I0 scope note: facts that require typed handle structs
// (ModuleDefinitionHandle, StringHandle, ...) are deferred to the
// typed-handles iteration (see TODO/I-dotnetutils/plan).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TableIndex
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TokenTypeIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandleTests {

    @Test
    fun handleKindsMatchSpecAndDoNotChange() {
        // These are chosen to match their encoding in metadata tokens as specified by the CLI spec
        assertEquals(0x00, HandleKind.MODULE_DEFINITION.value.toInt())
        assertEquals(0x01, HandleKind.TYPE_REFERENCE.value.toInt())
        assertEquals(0x02, HandleKind.TYPE_DEFINITION.value.toInt())
        assertEquals(0x04, HandleKind.FIELD_DEFINITION.value.toInt())
        assertEquals(0x06, HandleKind.METHOD_DEFINITION.value.toInt())
        assertEquals(0x08, HandleKind.PARAMETER.value.toInt())
        assertEquals(0x09, HandleKind.INTERFACE_IMPLEMENTATION.value.toInt())
        assertEquals(0x0A, HandleKind.MEMBER_REFERENCE.value.toInt())
        assertEquals(0x0B, HandleKind.CONSTANT.value.toInt())
        assertEquals(0x0C, HandleKind.CUSTOM_ATTRIBUTE.value.toInt())
        assertEquals(0x0E, HandleKind.DECLARATIVE_SECURITY_ATTRIBUTE.value.toInt())
        assertEquals(0x11, HandleKind.STANDALONE_SIGNATURE.value.toInt())
        assertEquals(0x14, HandleKind.EVENT_DEFINITION.value.toInt())
        assertEquals(0x17, HandleKind.PROPERTY_DEFINITION.value.toInt())
        assertEquals(0x19, HandleKind.METHOD_IMPLEMENTATION.value.toInt())
        assertEquals(0x1A, HandleKind.MODULE_REFERENCE.value.toInt())
        assertEquals(0x1B, HandleKind.TYPE_SPECIFICATION.value.toInt())
        assertEquals(0x20, HandleKind.ASSEMBLY_DEFINITION.value.toInt())
        assertEquals(0x26, HandleKind.ASSEMBLY_FILE.value.toInt())
        assertEquals(0x23, HandleKind.ASSEMBLY_REFERENCE.value.toInt())
        assertEquals(0x27, HandleKind.EXPORTED_TYPE.value.toInt())
        assertEquals(0x2A, HandleKind.GENERIC_PARAMETER.value.toInt())
        assertEquals(0x2B, HandleKind.METHOD_SPECIFICATION.value.toInt())
        assertEquals(0x2C, HandleKind.GENERIC_PARAMETER_CONSTRAINT.value.toInt())
        assertEquals(0x28, HandleKind.MANIFEST_RESOURCE.value.toInt())
        assertEquals(0x70, HandleKind.USER_STRING.value.toInt())

        // These values were chosen arbitrarily, but must still never change
        assertEquals(0x71, HandleKind.BLOB.value.toInt())
        assertEquals(0x72, HandleKind.GUID.value.toInt())
        assertEquals(0x78, HandleKind.STRING.value.toInt())
        assertEquals(0x7C, HandleKind.NAMESPACE_DEFINITION.value.toInt())
    }

    @Test
    fun conversions_EntityHandles() {
        // Ported subset: row id extraction via EntityHandle directly
        // (typed-handle casts belong to the typed-handles iteration).
        assertEquals(
            1,
            EntityHandle(TokenTypeIds.MODULE or 1u).rowId,
        )
        assertEquals(
            0x00ffffff,
            EntityHandle(TokenTypeIds.MODULE or 0x00ffffffu).rowId,
        )
        assertEquals(
            1,
            EntityHandle(TokenTypeIds.ASSEMBLY_REF or 1u).rowId,
        )
        assertEquals(
            0x00ffffff,
            EntityHandle(TokenTypeIds.ASSEMBLY_REF or 0x00ffffffu).rowId,
        )
    }

    @Test
    fun isNil_andIsVirtual_onEntityHandles() {
        assertFalse(EntityHandle(TokenTypeIds.TYPE_DEF or 1u).isNil)
        assertTrue(EntityHandle(TokenTypeIds.TYPE_DEF or 0u).isNil)
        assertFalse(EntityHandle(TokenTypeIds.ASSEMBLY or 1u).isNil)
        assertTrue(EntityHandle(TokenTypeIds.ASSEMBLY or 0u).isNil)

        // virtual entity handles are never nil
        val virtual = EntityHandle(TokenTypeIds.VIRTUAL_BIT or TokenTypeIds.ASSEMBLY_REF or 1u)
        assertFalse(virtual.isNil)
        assertTrue(virtual.isVirtual)
    }

    @Test
    fun handleRoundTrip_viaFromVToken() {
        val vToken = TokenTypeIds.METHOD_DEF or 123u
        val entity = EntityHandle(vToken)
        val handle = entity.toHandle()

        assertEquals(entity, handle.toEntityHandle())
        assertEquals(HandleKind.METHOD_DEFINITION, handle.kind)
        assertEquals(123, handle.rowId)
        assertEquals(vToken, handle.entityHandleValue)
    }

    @Test
    fun handleKindHidesSpecialStringAndNamespaces() {
        for (virtualBit in intArrayOf(0, HandleType.VIRTUAL_BIT.toInt())) {
            for (i in 0..UByte.MAX_VALUE.toInt() / 2) {
                val handle = Handle((virtualBit or i).toUByte(), 0)
                assertTrue(handle.isNil xor handle.isVirtual)
                assertEquals(virtualBit != 0, handle.isVirtual)
                assertEquals(i.toUInt() shl TokenTypeIds.ROW_ID_BIT_COUNT, handle.entityHandleType)

                val definedKind = HandleKind.fromRaw(i.toUByte())
                if (definedKind == null && i !in stringKindRange()) {
                    // Undefined handle kinds cannot be surfaced as enum members
                    // (see deviation note on Handle.kind); reader-fidelity facts
                    // are deferred to the reader iteration.
                    continue
                }

                when (i) {
                    // String has two extra bits to represent its kind that are hidden from the handle type
                    HandleKind.STRING.value.toInt(),
                    HandleKind.STRING.value.toInt() + 1,
                    HandleKind.STRING.value.toInt() + 2,
                    HandleKind.STRING.value.toInt() + 3,
                    -> assertEquals(HandleKind.STRING, handle.kind)
                    // all other types surface token type directly.
                    else -> assertEquals(i, handle.kind.value.toInt())
                }
            }
        }
    }

    /** String kind occupies four consecutive byte values (HandleType.String..+3). */
    private fun stringKindRange(): IntRange =
        0x78..0x7B

    @Test
    fun compare_ordersNonVirtualBeforeVirtual() {
        val plain = EntityHandle(TokenTypeIds.TYPE_DEF or 5u)
        val virtual = EntityHandle(TokenTypeIds.VIRTUAL_BIT or TokenTypeIds.TYPE_DEF or 5u)
        assertTrue(EntityHandle.compare(plain, virtual) < 0)
        assertEquals(0, EntityHandle.compare(plain, plain))
    }
}
