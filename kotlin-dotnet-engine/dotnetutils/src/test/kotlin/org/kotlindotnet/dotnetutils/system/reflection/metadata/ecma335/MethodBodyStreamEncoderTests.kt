// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata tests (Ecma335/Encoding/MethodBodyStreamEncoderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Adaptations:
//  - ArgumentNullException cases enforced by Kotlin's type system;
//  - round-trip verification via MethodBodyBlock.Create is cut
//    (reader-side; deferred to the reader iteration / C# harness).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriter
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.getBranchOperandSize
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MethodBodyStreamEncoderTests {

    private fun writeFakeILWithBranches(builder: BlobBuilder, branchBuilder: ControlFlowBuilder, size: Int) {
        assertEquals(0, builder.count)

        val filling = 0x01.toByte()
        var ilOffset = 0
        for (branch in branchBuilder.branchList) {
            builder.writeBytes(filling, branch.ilOffset - ilOffset)

            assertEquals(branch.ilOffset, builder.count)
            builder.writeByte(branch.opCode.value.toByte())

            val operandSize = branch.opCode.getBranchOperandSize()
            if (operandSize == 1) {
                builder.writeSByte(-1)
            } else {
                builder.writeInt32(-1)
            }

            ilOffset = branch.ilOffset + 1 + operandSize
        }

        builder.writeBytes(filling, size - ilOffset)
        assertEquals(size, builder.count)
    }

    @Test
    fun addMethodBody_errors() {
        val streamBuilder = BlobBuilder()
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        val il = InstructionEncoder(BlobBuilder())

        // encoder overloads:
        assertFailsWith<IllegalArgumentException> { encoder.addMethodBody(il, maxStack = -1) }
        assertFailsWith<IllegalArgumentException> { encoder.addMethodBody(il, maxStack = UShort.MAX_VALUE.toInt() + 1) }

        // reserved-bytes overloads (positional to disambiguate):
        assertFailsWith<IllegalArgumentException> { encoder.addMethodBody(-1) }
        assertFailsWith<IllegalArgumentException> { encoder.addMethodBody(1, -1) }
        assertFailsWith<IllegalArgumentException> { encoder.addMethodBody(1, UShort.MAX_VALUE.toInt() + 1) }
        assertFailsWith<IllegalStateException> { encoder.addMethodBody(1, 8, -1) }
        assertFailsWith<IllegalStateException> { encoder.addMethodBody(1, 8, 699051) }
    }

    @Test
    fun addMethodBody_reserved_tiny1() {
        val streamBuilder = BlobBuilder(32)
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        streamBuilder.writeBytes(0x01.toByte(), 3)

        val body = encoder.addMethodBody(10)
        assertEquals(3, body.offset)

        assertEquals(4, body.instructions.start) // +1 byte for the header
        assertEquals(10, body.instructions.length)

        assertNull(body.exceptionRegions)

        BlobWriter(body.instructions).writeBytes(0x02.toByte(), 10)

        assertContentEquals(
            byteArrayOf(
                0x01, 0x01, 0x01,
                0x2A, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
            ),
            streamBuilder.toArray(),
        )
    }

    @Test
    fun addMethodBody_reserved_tiny_attributesIgnored() {
        val streamBuilder = BlobBuilder()
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        val body = encoder.addMethodBody(10, attributes = MethodBodyAttributes.NONE)
        BlobWriter(body.instructions).writeBytes(0x02.toByte(), 10)

        assertContentEquals(
            byteArrayOf(0x2A, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02),
            streamBuilder.toArray(),
        )
    }

    @Test
    fun addMethodBody_reserved_tiny_maxStackIgnored() {
        val streamBuilder = BlobBuilder()
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        val body = encoder.addMethodBody(10, maxStack = 7)
        BlobWriter(body.instructions).writeBytes(0x02.toByte(), 10)

        assertContentEquals(
            byteArrayOf(0x2A, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02),
            streamBuilder.toArray(),
        )
    }

    @Test
    fun addMethodBody_reserved_tiny_empty() {
        val streamBuilder = BlobBuilder()
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        val body = encoder.addMethodBody(0)
        assertEquals(0, body.offset)

        assertEquals(1, body.instructions.start) // +1 byte for the header
        assertEquals(0, body.instructions.length)

        assertNull(body.exceptionRegions)

        assertContentEquals(byteArrayOf(0x02), streamBuilder.toArray())
    }

    @Test
    fun addMethodBody_reserved_fat1() {
        val streamBuilder = BlobBuilder(32)
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        streamBuilder.writeBytes(0x01.toByte(), 3)

        val body = encoder.addMethodBody(10, maxStack = 9)
        assertEquals(4, body.offset) // 4B aligned

        assertEquals(16, body.instructions.start) // +12 bytes for the header
        assertEquals(10, body.instructions.length)

        assertNull(body.exceptionRegions)

        BlobWriter(body.instructions).writeBytes(0x02.toByte(), 10)

        assertContentEquals(
            byteArrayOf(
                0x01, 0x01, 0x01,
                0x00, // padding
                0x13, 0x30,
                0x09, 0x00, // max stack
                0x0A, 0x00, 0x00, 0x00, // code size
                0x00, 0x00, 0x00, 0x00, // local sig
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
            ),
            streamBuilder.toArray(),
        )
    }

    @Test
    fun addMethodBody_reserved_fat2_localSig() {
        val streamBuilder = BlobBuilder(32)
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        streamBuilder.writeBytes(0x01.toByte(), 3)

        val body = encoder.addMethodBody(
            10,
            localVariablesSignature = MetadataTokens.standaloneSignatureHandle(0xABCDEF),
        )
        assertEquals(4, body.offset) // 4B aligned

        assertEquals(16, body.instructions.start)
        assertEquals(10, body.instructions.length)

        assertNull(body.exceptionRegions)

        BlobWriter(body.instructions).writeBytes(0x02.toByte(), 10)

        assertContentEquals(
            byteArrayOf(
                0x01, 0x01, 0x01,
                0x00, // padding
                0x13, 0x30,
                0x08, 0x00, // max stack
                0x0A, 0x00, 0x00, 0x00, // code size
                0xEF.toByte(), 0xCD.toByte(), 0xAB.toByte(), 0x11, // local sig token 0x11ABCDEF
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,
            ),
            streamBuilder.toArray(),
        )
    }

    @Test
    fun addMethodBody_reserved_exceptions_fat1() {
        val streamBuilder = BlobBuilder(32)
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        streamBuilder.writeBytes(0x01.toByte(), 3)

        // note: the original uses a huge region count that exceeds the port's
        // supported maximum only in the *small* format check — here the count
        // fits fat format bounds (699050 <= MaxExceptionRegions), same as C#.
        val maxFatRegions = 699050
        val body = encoder.addMethodBody(10, exceptionRegionCount = maxFatRegions)
        assertEquals(4, body.offset) // 4B aligned

        assertEquals(16, body.instructions.start)
        assertEquals(10, body.instructions.length)

        assertSame(streamBuilder, body.exceptionRegions!!.builder)

        BlobWriter(body.instructions).writeBytes(0x02.toByte(), 10)

        assertContentEquals(
            byteArrayOf(
                0x01, 0x01, 0x01,
                0x00, // padding
                0x1B, 0x30, // flags: more sections
                0x08, 0x00, // max stack
                0x0A, 0x00, 0x00, 0x00, // code size
                0x00, 0x00, 0x00, 0x00, // local sig
                0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02,

                // exception table header (fat):
                0x00, 0x00,      // padding
                0x41,            // kind flags: EH table | fat format
                0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), // size of the table
            ),
            streamBuilder.toArray(),
        )
    }

    @Test
    fun tinyBody() {
        val streamBuilder = BlobBuilder()
        val codeBuilder = BlobBuilder()
        val flowBuilder = ControlFlowBuilder()

        val il = InstructionEncoder(codeBuilder, flowBuilder)

        codeBuilder.writeBytes(0x01.toByte(), 61)
        val l1 = il.defineLabel()
        il.markLabel(l1)

        assertEquals(listOf(61), flowBuilder.labelList)

        il.branch(ILOpCode.BR_S, l1)

        val brInfo = flowBuilder.branchList.single()
        assertEquals(61, brInfo.ilOffset)
        assertEquals(62, brInfo.operandOffset)
        assertEquals(l1, brInfo.label)
        assertEquals(ILOpCode.BR_S, brInfo.opCode)

        assertContentEquals(byteArrayOf(0x01, ILOpCode.BR_S.value.toByte(), 0xFF.toByte()), codeBuilder.toArray(60, 3))

        val streamEncoder = MethodBodyStreamEncoder(streamBuilder)
        streamEncoder.addMethodBody(
            il,
            maxStack = 2,
            attributes = MethodBodyAttributes.NONE,
        )

        val expected = ByteArray(64)
        expected[0] = 0xFE.toByte() // tiny header: codeSize=63 -> (63<<2)|2 = 254
        for (i in 1..61) expected[i] = 0x01
        expected[62] = ILOpCode.BR_S.value.toByte()
        expected[63] = 0xFE.toByte() // fixed-up branch distance -2
        assertContentEquals(expected, streamBuilder.toArray())
    }

    @Test
    fun fatBody() {
        val streamBuilder = BlobBuilder()
        val codeBuilder = BlobBuilder()
        val flowBuilder = ControlFlowBuilder()

        val il = InstructionEncoder(codeBuilder, flowBuilder)

        codeBuilder.writeBytes(0x01.toByte(), 62)
        val l1 = il.defineLabel()
        il.markLabel(l1)

        assertEquals(listOf(62), flowBuilder.labelList)

        il.branch(ILOpCode.BR_S, l1)

        val brInfo = flowBuilder.branchList.single()
        assertEquals(62, brInfo.ilOffset)
        assertEquals(63, brInfo.operandOffset)
        assertEquals(l1, brInfo.label)
        assertEquals(ILOpCode.BR_S, brInfo.opCode)

        assertContentEquals(
            byteArrayOf(0x01, 0x01, ILOpCode.BR_S.value.toByte(), 0xFF.toByte()),
            codeBuilder.toArray(60, 4),
        )

        val streamEncoder = MethodBodyStreamEncoder(streamBuilder)
        streamEncoder.addMethodBody(
            il,
            maxStack = 2,
            attributes = MethodBodyAttributes.NONE,
        )

        val bodyBytes = streamBuilder.toArray()

        // layout: fat header(12) + code(62 ones + br.s(2)) = 76
        assertEquals(76, bodyBytes.size)
        assertContentEquals(
            byteArrayOf(0x03, 0x30, 0x02, 0x00, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            bodyBytes.copyOfRange(0, 12),
        )
        assertEquals(0x01.toByte(), bodyBytes[73])
        assertEquals(ILOpCode.BR_S.value.toByte(), bodyBytes[74])
        assertEquals(0xFE.toByte(), bodyBytes[75]) // fixed-up short distance -2
    }

    @Test
    fun locAlloc() {
        val streamBuilder = BlobBuilder()

        val il = byteArrayOf(
            0x1A,                         // ldc.i4.0
            0xFE.toByte(), 0x0F,          // localloc
            0x28, 0x01, 0x00, 0x00, 0x06, // call 0x06000001
            0x2A,                         // ret
        )

        val streamEncoder = MethodBodyStreamEncoder(streamBuilder)
        val methodBody = streamEncoder.addMethodBody(
            il.size,
            maxStack = 2,
            attributes = MethodBodyAttributes.INIT_LOCALS,
            hasDynamicStackAllocation = true,
        )

        assertEquals(0, methodBody.offset)
        assertNull(methodBody.exceptionRegions)
        assertFalse(methodBody.exceptionRegions != null && methodBody.exceptionRegions.hasSmallFormat)

        BlobWriter(methodBody.instructions).writeBytes(il)

        assertContentEquals(
            byteArrayOf(
                0x13, 0x30,                   // flags and header size
                0x02, 0x00,                   // max stack
                0x09, 0x00, 0x00, 0x00,       // code size
                0x00, 0x00, 0x00, 0x00,       // local variable signature
                *il,
            ),
            streamBuilder.toArray(),
        )
    }

    private fun assertFalse(b: Boolean) {
        if (b) throw AssertionError("expected false")
    }

    @Test
    fun locAlloc_withInstructionEncoder() {
        val streamBuilder = BlobBuilder()
        val codeBuilder = BlobBuilder()
        val flowBuilder = ControlFlowBuilder()

        val il = InstructionEncoder(codeBuilder, flowBuilder)

        il.opCode(ILOpCode.LDC_I4_4)
        il.opCode(ILOpCode.LOCALLOC)
        il.call(MetadataTokens.methodDefinitionHandle(1))
        il.opCode(ILOpCode.RET)

        val streamEncoder = MethodBodyStreamEncoder(streamBuilder)
        streamEncoder.addMethodBody(
            il,
            maxStack = 2,
            attributes = MethodBodyAttributes.INIT_LOCALS,
            hasDynamicStackAllocation = true,
        )

        assertContentEquals(
            byteArrayOf(
                0x13, 0x30,                   // flags and header size
                0x02, 0x00,                   // max stack
                0x09, 0x00, 0x00, 0x00,       // code size
                0x00, 0x00, 0x00, 0x00,       // local variable signature
                0x1A,                         // ldc.i4.0
                0xFE.toByte(), 0x0F,          // localloc
                0x28, 0x01, 0x00, 0x00, 0x06, // call 0x06000001
                0x2A,                         // ret
            ),
            streamBuilder.toArray(),
        )
    }

    @Test
    fun branches_acrossBlobBoundaries() {
        val flowBuilder = ControlFlowBuilder()

        val l0 = flowBuilder.addLabel()
        val l64 = flowBuilder.addLabel()
        val l255 = flowBuilder.addLabel()

        flowBuilder.markLabel(0, l0)
        flowBuilder.markLabel(64, l64)
        flowBuilder.markLabel(255, l255)

        flowBuilder.addBranch(1, l255, 4, 0, ILOpCode.BGE)
        flowBuilder.addBranch(17, l0, 1, 16, ILOpCode.BGE_UN_S) // blob boundary
        flowBuilder.addBranch(34, l255, 4, 33, ILOpCode.BLE)    // blob boundary
        flowBuilder.addBranch(39, l0, 1, 38, ILOpCode.BLE_UN_S) // adjacent branches
        flowBuilder.addBranch(41, l255, 4, 40, ILOpCode.BLT)    // adjacent branches
        flowBuilder.addBranch(47, l64, 1, 46, ILOpCode.BLT_UN_S)
        flowBuilder.addBranch(255, l0, 4, 254, ILOpCode.BRFALSE) // long branch at the end

        val dstBuilder = BlobBuilder()
        val srcBuilder = BlobBuilder(capacity = 17)
        writeFakeILWithBranches(srcBuilder, flowBuilder, size = 259)

        flowBuilder.copyCodeAndFixupBranches(srcBuilder, dstBuilder)

        val dst = dstBuilder.toArray()

        // spot-check the fixed-up operands and total length:
        assertEquals(259, dst.size)

        fun le32(o: Int): Int =
            (dst[o].toInt() and 0xFF) or ((dst[o + 1].toInt() and 0xFF) shl 8) or
                ((dst[o + 2].toInt() and 0xFF) shl 16) or ((dst[o + 3].toInt() and 0xFF) shl 24)

        assertEquals(250, le32(1))  // Bge: label l255 at offset 255, operand ends at 1+4 => 255-5=250
        assertEquals(ILOpCode.BGE_UN_S.value.toByte(), dst[16]) // opcode
        assertEquals(-18, dst[17].toInt()) // short operand fixed up to label l0 (offset 0): 0 - 18
        assertEquals(ILOpCode.BRFALSE.value.toByte(), dst[254])
    }

    @Test
    fun branchErrors() {
        val codeBuilder = BlobBuilder()

        run {
            val il = InstructionEncoder(codeBuilder)
            assertFailsWith<IllegalStateException> { il.defineLabel() }
        }

        var foreign = LabelHandle.fromId(0)
        run {
            val other = InstructionEncoder(codeBuilder, ControlFlowBuilder())
            other.defineLabel()
            other.defineLabel()
            foreign = other.defineLabel()
        }

        val flowBuilder = ControlFlowBuilder()
        val il = InstructionEncoder(codeBuilder, flowBuilder)
        val l0 = il.defineLabel()

        assertFailsWith<IllegalArgumentException> { il.branch(ILOpCode.NOP, l0) } // not a branch
        assertFailsWith<IllegalStateException> { il.branch(ILOpCode.BR, LabelHandle.fromId(0)) } // nil
        assertFailsWith<IllegalStateException> { il.branch(ILOpCode.BR, foreign) } // other builder's label
    }

    @Test
    fun branchErrors_unmarkedLabel() {
        val streamBuilder = BlobBuilder()
        val codeBuilder = BlobBuilder()
        val flowBuilder = ControlFlowBuilder()

        val il = InstructionEncoder(codeBuilder, flowBuilder)
        val l = il.defineLabel()
        il.branch(ILOpCode.BR_S, l)
        il.opCode(ILOpCode.RET)

        assertFailsWith<IllegalStateException> { MethodBodyStreamEncoder(streamBuilder).addMethodBody(il) }
    }
}
