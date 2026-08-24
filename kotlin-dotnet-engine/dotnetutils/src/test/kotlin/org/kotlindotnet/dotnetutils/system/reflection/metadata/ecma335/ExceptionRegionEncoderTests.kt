// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata tests (Ecma335/Encoding/ExceptionRegionEncoderTests.cs,
// Ecma335/Encoding/LabelHandleTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Adaptations:
//  - the "default(ExceptionRegionEncoder)" case of Add_Errors is not
//    portable (no default struct instances in Kotlin);
//  - C# InvalidOperationException/ArgumentNullException/ArgumentException
//    map to IllegalStateException/IllegalArgumentException per the port's
//    fail-fast convention (ADR 0009).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ExceptionRegionKind
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionRegionEncoderTests {

    @Test
    fun isSmallRegionCount() {
        assertTrue(ExceptionRegionEncoder.isSmallRegionCount(0))
        assertTrue(ExceptionRegionEncoder.isSmallRegionCount(20))
        assertFalse(ExceptionRegionEncoder.isSmallRegionCount(-1))
        assertFalse(ExceptionRegionEncoder.isSmallRegionCount(21))
        assertFalse(ExceptionRegionEncoder.isSmallRegionCount(Int.MIN_VALUE))
        assertFalse(ExceptionRegionEncoder.isSmallRegionCount(Int.MAX_VALUE))
    }

    @Test
    fun isSmallExceptionRegion() {
        assertTrue(ExceptionRegionEncoder.isSmallExceptionRegion(0, 0))
        assertTrue(ExceptionRegionEncoder.isSmallExceptionRegion(UShort.MAX_VALUE.toInt(), UByte.MAX_VALUE.toInt()))

        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(UShort.MAX_VALUE.toInt() + 1, UByte.MAX_VALUE.toInt()))
        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(UShort.MAX_VALUE.toInt(), UByte.MAX_VALUE.toInt() + 1))

        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(-1, 0))
        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(0, -1))
        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(Int.MIN_VALUE, Int.MIN_VALUE))
        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(Int.MAX_VALUE, Int.MIN_VALUE))
        assertFalse(ExceptionRegionEncoder.isSmallExceptionRegion(Int.MIN_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun serializeTableHeader() {
        val builder = BlobBuilder()

        builder.writeByte(0xff.toByte())
        ExceptionRegionEncoder.serializeTableHeader(
            builder,
            exceptionRegionCount = ExceptionRegionEncoder.MAX_SMALL_EXCEPTION_REGIONS,
            hasSmallRegions = true,
        )
        assertContentEquals(
            byteArrayOf(
                0xff.toByte(), 0x00, 0x00, 0x00, // padding
                0x01,                            // flags
                0xf4.toByte(),                   // size (4 + 20 * 12 = 244 = 0xF4)
                0x00, 0x00,
            ),
            builder.toArray(),
        )
        builder.clear()

        builder.writeByte(0xff.toByte())
        ExceptionRegionEncoder.serializeTableHeader(
            builder,
            exceptionRegionCount = ExceptionRegionEncoder.MAX_EXCEPTION_REGIONS,
            hasSmallRegions = false,
        )
        assertContentEquals(
            byteArrayOf(
                0xff.toByte(), 0x00, 0x00, 0x00, // padding
                0x41,                            // flags: EH table | fat format
                0xf4.toByte(), 0xff.toByte(), 0xff.toByte(), // size (24-bit)
            ),
            builder.toArray(),
        )
    }

    @Test
    fun add_small() {
        val builder = BlobBuilder()
        val encoder = ExceptionRegionEncoder.serializeTableHeader(builder, 1, hasSmallRegions = true)

        encoder.add(
            ExceptionRegionKind.CATCH, 1, 2, 4, 5,
            catchType = MetadataTokens.typeDefinitionHandle(1).toEntityHandle(),
        )

        assertContentEquals(
            byteArrayOf(
                0x00, 0x00,            // kind
                0x01, 0x00,            // try offset
                0x02,                  // try length
                0x04, 0x00,            // handler offset
                0x05,                  // handler length
                0x01, 0x00, 0x00, 0x02, // catch type
            ),
            builder.toArray().copyOfRange(4, 16),
        )
    }

    @Test
    fun add_small_filterFaultFinally() {
        val builder = BlobBuilder()
        val encoder = ExceptionRegionEncoder.serializeTableHeader(builder, 1, hasSmallRegions = true)

        encoder.add(
            ExceptionRegionKind.FILTER, 0xffff, 0xff, 0xffff, 0xff,
            filterOffset = Int.MAX_VALUE,
        )
        assertContentEquals(
            byteArrayOf(
                0x01, 0x00,            // kind
                0xff.toByte(), 0xff.toByte(), // try offset
                0xff.toByte(),         // try length
                0xff.toByte(), 0xff.toByte(), // handler offset
                0xff.toByte(),         // handler length
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f, // filter offset
            ),
            builder.toArray().copyOfRange(4, 16),
        )
        builder.clear()

        encoder.add(ExceptionRegionKind.FAULT, 0xffff, 0xff, 0xffff, 0xff)
        assertContentEquals(
            byteArrayOf(
                0x04, 0x00,
                0xff.toByte(), 0xff.toByte(),
                0xff.toByte(),
                0xff.toByte(), 0xff.toByte(),
                0xff.toByte(),
                0x00, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
        builder.clear()

        encoder.add(ExceptionRegionKind.FINALLY, 0, 0, 0, 0)
        assertContentEquals(
            byteArrayOf(
                0x02, 0x00,
                0x00, 0x00,
                0x00,
                0x00, 0x00,
                0x00,
                0x00, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun add_large() {
        val builder = BlobBuilder()
        val encoder = ExceptionRegionEncoder(builder, hasSmallFormat = false)

        encoder.add(
            ExceptionRegionKind.CATCH, 1, 2, 4, 5,
            catchType = MetadataTokens.typeDefinitionHandle(1).toEntityHandle(),
        )
        assertContentEquals(
            byteArrayOf(
                0x00, 0x00, 0x00, 0x00, // kind
                0x01, 0x00, 0x00, 0x00, // try offset
                0x02, 0x00, 0x00, 0x00, // try length
                0x04, 0x00, 0x00, 0x00, // handler offset
                0x05, 0x00, 0x00, 0x00, // handler length
                0x01, 0x00, 0x00, 0x02, // catch type
            ),
            builder.toArray(),
        )
        builder.clear()

        encoder.add(
            ExceptionRegionKind.FILTER, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE,
            filterOffset = Int.MAX_VALUE,
        )
        assertContentEquals(
            byteArrayOf(
                0x01, 0x00, 0x00, 0x00,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
            ),
            builder.toArray(),
        )
        builder.clear()

        encoder.add(ExceptionRegionKind.FAULT, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        assertContentEquals(
            byteArrayOf(
                0x04, 0x00, 0x00, 0x00,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f,
                0x00, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
        builder.clear()

        encoder.add(ExceptionRegionKind.FINALLY, 0, 0, 0, 0)
        assertContentEquals(
            byteArrayOf(
                0x02, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun add_errors() {
        val builder = BlobBuilder()
        val smallEncoder = ExceptionRegionEncoder(builder, hasSmallFormat = true)
        val fatEncoder = ExceptionRegionEncoder(builder, hasSmallFormat = false)

        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, -1, 2, 4, 5) }
        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, 1, -1, 4, 5) }
        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, 1, 2, -1, 5) }
        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, 1, 2, 4, -1) }

        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, 0x10000, 2, 4, 5) }
        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, 1, 0x100, 4, 5) }
        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, 1, 2, 0x10000, 5) }
        assertFailsWith<IllegalArgumentException> { smallEncoder.add(ExceptionRegionKind.FINALLY, 1, 2, 4, 0x100) }

        assertFailsWith<IllegalArgumentException> { fatEncoder.add(ExceptionRegionKind.FINALLY, -1, 2, 4, 5) }
        assertFailsWith<IllegalArgumentException> { fatEncoder.add(ExceptionRegionKind.FINALLY, 1, -1, 4, 5) }
        assertFailsWith<IllegalArgumentException> { fatEncoder.add(ExceptionRegionKind.FINALLY, 1, 2, -1, 5) }
        assertFailsWith<IllegalArgumentException> { fatEncoder.add(ExceptionRegionKind.FINALLY, 1, 2, 4, -1) }

        assertFailsWith<IllegalArgumentException> { fatEncoder.add(ExceptionRegionKind.FILTER, 1, 2, 4, 5, filterOffset = -1) }
        // note: an invalid ExceptionRegionKind value cannot be constructed in Kotlin (enum type system)
        assertFailsWith<IllegalStateException> { fatEncoder.add(ExceptionRegionKind.CATCH, 1, 2, 4, 5, catchType = EntityHandle(0u)) }

        // an ImportScopeHandle is not ported; a MethodDefinition handle serves as
        // the same "wrong kind" case:
        assertFailsWith<IllegalStateException> {
            fatEncoder.add(
                ExceptionRegionKind.CATCH, 1, 2, 4, 5,
                catchType = MetadataTokens.methodDefinitionHandle(1).toEntityHandle(),
            )
        }
    }
}

class LabelHandleTests {
    @Test
    fun equality() {
        val a1 = LabelHandle.fromId(1)
        val a2 = LabelHandle.fromId(2)
        val b1 = LabelHandle.fromId(1)

        assertTrue(a1 != a2)
        assertEquals(a1, b1)
        assertTrue(a1 == b1)
        assertTrue(a1.id == b1.id)
        assertFalse(a1 == a2)

        assertEquals(a1.hashCode(), b1.hashCode())
    }
}
