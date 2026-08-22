// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Adapted from System.Reflection.Metadata tests (Ecma335/Encoding/).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class I5EncodingTests {

    @Test
    fun opCode_encoding_singleAndTwoByte() {
        val code = BlobBuilder()
        val encoder = InstructionEncoder(code)
        encoder.opCode(ILOpCode.RET)
        encoder.opCode(ILOpCode.LDFTN) // two-byte 0xFE06, high byte first
        assertContentEquals(byteArrayOf(0x2A, 0xFE.toByte(), 0x06), code.toArray())
    }

    @Test
    fun loadConstantI4_shortForms() {
        val code = BlobBuilder()
        val encoder = InstructionEncoder(code)
        encoder.loadConstantI4(0)
        encoder.loadConstantI4(-1)
        encoder.loadConstantI4(8)
        encoder.loadConstantI4(42) // sbyte form
        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDC_I4_0.value.toByte(),
                ILOpCode.LDC_I4_M1.value.toByte(),
                ILOpCode.LDC_I4_8.value.toByte(),
                ILOpCode.LDC_I4_S.value.toByte(), 42,
            ),
            code.toArray(),
        )
    }

    @Test
    fun loadConstantI4_longForm() {
        val code = BlobBuilder()
        val encoder = InstructionEncoder(code)
        encoder.loadConstantI4(1000)
        assertContentEquals(
            byteArrayOf(ILOpCode.LDC_I4.value.toByte(), 0xE8.toByte(), 0x03, 0x00, 0x00),
            code.toArray(),
        )
    }

    @Test
    fun branch_forwardReference() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val encoder = InstructionEncoder(code, flow)

        encoder.loadConstantI4(1)
        val label = encoder.defineLabel()
        encoder.branch(ILOpCode.BR_S, label)
        encoder.loadConstantI4(2)
        encoder.markLabel(label)
        encoder.opCode(ILOpCode.ADD)

        // stream: ldc.i4.1 | br.s | ldc.i4.2 | add
        // fixup: br.s opcode at 1, operand at 2; label marked at 4 => distance 4 - 3 = 1
        val out = BlobBuilder()
        flow.copyCodeAndFixupBranches(code, out)
        assertContentEquals(
            byteArrayOf(ILOpCode.LDC_I4_1.value.toByte(), ILOpCode.BR_S.value.toByte(), 0x01, ILOpCode.LDC_I4_2.value.toByte(), ILOpCode.ADD.value.toByte()),
            out.toArray(),
        )
    }

    @Test
    fun branch_backwardReference() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val encoder = InstructionEncoder(code, flow)

        val loop = encoder.defineLabel()
        encoder.markLabel(loop)
        encoder.loadConstantI4(1)
        encoder.branch(ILOpCode.BR, loop)

        // source order: ldc.i4.1 (at 0), br opcode (at 1), operand (at 2..5)
        // distance = label(0) - (2 + 4) = -6
        val out = BlobBuilder()
        flow.copyCodeAndFixupBranches(code, out)
        assertContentEquals(
            byteArrayOf(ILOpCode.LDC_I4_1.value.toByte(), ILOpCode.BR.value.toByte(), 0xFA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            out.toArray(),
        )
    }

    @Test
    fun shortBranchTooFar_throws() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val encoder = InstructionEncoder(code, flow)

        val far = encoder.defineLabel()
        encoder.branch(ILOpCode.BR_S, far)
        repeat(200) { encoder.loadConstantI4(7) } // > 127 bytes away
        encoder.markLabel(far)

        val out = BlobBuilder()
        assertFailsWith<IllegalStateException> { flow.copyCodeAndFixupBranches(code, out) }
    }

    @Test
    fun methodBodyStream_tinyBody() {
        val stream = BlobBuilder() // starts empty => aligned
        val bodyStream = MethodBodyStreamEncoder(stream)

        val il = BlobBuilder()
        val enc = InstructionEncoder(il)
        enc.loadConstantI4(42)
        enc.opCode(ILOpCode.RET)

        val offset = bodyStream.addMethodBody(enc, maxStack = 1, attributes = MethodBodyAttributes.NONE)

        assertEquals(0, offset)
        // ldc.i4.42 uses the short sbyte form: code = [ldc.i4.s, 42, ret] (3 bytes)
        // tiny header: (codeSize << 2) | 2 -> 0x0E
        assertContentEquals(
            byteArrayOf(0x0E, ILOpCode.LDC_I4_S.value.toByte(), 42, ILOpCode.RET.value.toByte()),
            stream.toArray(),
        )
    }

    @Test
    fun methodBodyStream_fatBodyWithExceptions() {
        val stream = BlobBuilder()
        val bodyStream = MethodBodyStreamEncoder(stream)

        val il = BlobBuilder()
        val flow = ControlFlowBuilder()
        val enc = InstructionEncoder(il, flow)

        // big code so tiny format is not used
        repeat(20) { enc.loadConstantI4(1000 + it) }
        val tryStart = enc.defineLabel()
        val tryEnd = enc.defineLabel()
        val handlerStart = enc.defineLabel()
        val handlerEnd = enc.defineLabel()
        enc.markLabel(tryStart)
        enc.opCode(ILOpCode.NOP)
        enc.markLabel(tryEnd)
        enc.markLabel(handlerStart)
        enc.opCode(ILOpCode.NOP)
        enc.markLabel(handlerEnd)
        flow.addFinallyRegion(tryStart, tryEnd, handlerStart, handlerEnd)

        val offset = bodyStream.addMethodBody(
            enc,
            maxStack = 1,
            attributes = MethodBodyAttributes.INIT_LOCALS,
        )

        assertTrue(offset >= 0 && offset % 4 == 0, "fat bodies must be 4-aligned")
        val bytes = stream.toArray()

        // fat flags: (3 << 12) | FatFormat(3) | MoreSections(8) | InitLocals(0x10) = 0x301B
        val flags = ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8))
        assertEquals(0x301B, flags)
    }

    @Test
    fun switchInstruction_branches() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val encoder = InstructionEncoder(code, flow)

        val l0 = encoder.defineLabel()
        val l1 = encoder.defineLabel()
        val sw = encoder.switch(2)
        sw.branch(l0)
        sw.branch(l1)
        encoder.markLabel(l0)
        encoder.opCode(ILOpCode.NOP)
        encoder.markLabel(l1)
        encoder.opCode(ILOpCode.NOP)

        val out = BlobBuilder()
        flow.copyCodeAndFixupBranches(code, out)
        val bytes = out.toArray()

        // layout: switch opcode(1) | count u32(4) | operand0 i32(4) | operand1 i32(4)
        // instruction end = 5 + 8 = 13; labels marked after all operands: l0=13, l1=14
        assertEquals(ILOpCode.SWITCH.value, bytes[0].toInt() and 0xFF)
        assertEquals(2, bytes[1].toInt() and 0xFF)
        fun le32(o: Int): Int = bytes[o].toInt() or (bytes[o+1].toInt() shl 8) or (bytes[o+2].toInt() shl 16) or (bytes[o+3].toInt() shl 24)
        assertEquals(0, le32(5))   // d0 = l0(13) - (5 + 8)
        assertEquals(1, le32(9))   // d1 = l1(14) - (9 + ... operand ends at 13)
        assertEquals(15, bytes.size)
    }

    @Test
    fun exceptionTable_smallFormat() {
        val builder = BlobBuilder()
        builder.writeInt32(0x11223344.toInt()) // some preceding data

        val encoder = ExceptionRegionEncoder.serializeTableHeader(builder, exceptionRegionCount = 1, hasSmallRegions = true)
        encoder.addFinally(tryOffset = 0, tryLength = 10, handlerOffset = 16, handlerLength = 8)

        val bytes = builder.toArray()
        // header at offset 4 (aligned): EH flag + size (4 + 12 = 16 = 0x10)
        assertEquals(0x01, bytes[4].toInt() and 0xFF)
        assertEquals(0x10, bytes[5].toInt() and 0xFF)
        // kind Finally = 2 (ushort LE)
        assertEquals(2, bytes[8].toInt() and 0xFF)
    }

    @Test
    fun exceptionRegion_addValidatesSmallBounds() {
        val builder = BlobBuilder()
        val encoder = ExceptionRegionEncoder.serializeTableHeader(builder, exceptionRegionCount = 1, hasSmallRegions = true)
        assertFailsWith<IllegalArgumentException> {
            encoder.addFinally(tryOffset = 0x10000, tryLength = 1, handlerOffset = 0, handlerLength = 1)
        }
    }
}
