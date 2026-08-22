// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Calibration checks for iteration I0 (enums + simple structs).
// Spot-checks against ECMA-335 / PE spec values and the C# original.

package org.kotlindotnet.dotnetutils.system.reflection

import org.kotlindotnet.dotnetutils.system.reflection.metadata.ExceptionRegion
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ExceptionRegionKind
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.TableIndex
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.Machine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PortableExecutableEnumsTests {

    @Test
    fun tableIndexes_matchSpec() {
        assertEquals(0x00, TableIndex.MODULE.value)
        assertEquals(0x01, TableIndex.TYPE_REF.value)
        assertEquals(0x02, TableIndex.TYPE_DEF.value)
        assertEquals(0x06, TableIndex.METHOD_DEF.value)
        assertEquals(0x11, TableIndex.STAND_ALONE_SIG.value)
        assertEquals(0x20, TableIndex.ASSEMBLY.value)
        assertEquals(0x23, TableIndex.ASSEMBLY_REF.value)
        assertEquals(0x2C, TableIndex.GENERIC_PARAM_CONSTRAINT.value)
        assertEquals(0x30, TableIndex.DOCUMENT.value)
        assertEquals(0x37, TableIndex.CUSTOM_DEBUG_INFORMATION.value)
    }

    @Test
    fun ilOpcodes_matchSpec() {
        assertEquals(218, ILOpCode.entries.size)
        assertEquals(0x00, ILOpCode.NOP.value)
        assertEquals(0x2A, ILOpCode.RET.value)
        assertEquals(0x72, ILOpCode.LDSTR.value)
        assertEquals(0x73, ILOpCode.NEWOBJ.value)
        assertEquals(0x6F, ILOpCode.CALLVIRT.value)
        assertEquals(0xFE01, ILOpCode.CEQ.value)
        assertEquals(0xFE1E, ILOpCode.READONLY.value)

        // round trip by raw value (single-byte and two-byte encodings)
        assertNotNull(ILOpCode.fromRaw(0x2A))
        assertEquals(ILOpCode.CALL, ILOpCode.fromRaw(0x28))
        assertEquals(ILOpCode.LDFTN, ILOpCode.fromRaw(0xFE06))
    }

    @Test
    fun machines_spotCheck() {
        assertEquals(30, Machine.entries.size)
        assertEquals(0x014C, Machine.I386.value)
        assertEquals(0x8664, Machine.AMD64.value)
        assertEquals(0xAA64, Machine.ARM64.value)
    }

    @Test
    fun exceptionRegion_filterAndCatch() {
        val filter = ExceptionRegion(
            kind = ExceptionRegionKind.FILTER,
            tryOffset = 10,
            tryLength = 20,
            handlerOffset = 40,
            handlerLength = 5,
            classTokenOrFilterOffset = 33,
        )
        assertEquals(33, filter.filterOffset)
        assertEquals(ExceptionRegionKind.FILTER, filter.kind)
        assertEquals(10, filter.tryOffset)
        assertEquals(20, filter.tryLength)
        assertEquals(40, filter.handlerOffset)
        assertEquals(5, filter.handlerLength)

        val catch = ExceptionRegion(
            kind = ExceptionRegionKind.CATCH,
            tryOffset = 0,
            tryLength = 1,
            handlerOffset = 2,
            handlerLength = 3,
            classTokenOrFilterOffset = 0x02000001,
        )
        assertEquals(0x02000001, catch.catchType.vToken.toInt())
        assertEquals(-1, catch.filterOffset)

        val finally = ExceptionRegion(
            kind = ExceptionRegionKind.FINALLY,
            tryOffset = 0,
            tryLength = 1,
            handlerOffset = 2,
            handlerLength = 3,
            classTokenOrFilterOffset = 99,
        )
        assertEquals(-1, finally.filterOffset)
        assertTrue(finally.catchType.isNil)
    }
}
