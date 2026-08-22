// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata tests (Ecma335/Encoding/ControlFlowBuilderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Adaptations: C# InvalidOperationException/ArgumentNullException/
// ArgumentException map to IllegalStateException/IllegalArgumentException
// per the port's fail-fast convention (ADR 0009).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ControlFlowBuilderTests {

    private fun layoutLabels(il: InstructionEncoder): Array<LabelHandle> {
        val l1 = il.defineLabel()
        val l2 = il.defineLabel()
        val l3 = il.defineLabel()
        val l4 = il.defineLabel()
        val l5 = il.defineLabel()

        il.markLabel(l1)
        assertEquals(0, il.offset)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l2)
        assertEquals(1, il.offset)
        il.opCode(ILOpCode.NOP)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l3)
        assertEquals(3, il.offset)
        il.opCode(ILOpCode.NOP)
        il.opCode(ILOpCode.NOP)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l4)
        assertEquals(6, il.offset)
        il.opCode(ILOpCode.NOP)
        il.opCode(ILOpCode.NOP)
        il.opCode(ILOpCode.NOP)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l5)
        assertEquals(10, il.offset)

        return arrayOf(l1, l2, l3, l4, l5)
    }

    @Test
    fun addFinallyFaultFilterRegions() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val (l1, l2, l3, l4, l5) = layoutLabels(il)

        flow.addFaultRegion(l1, l2, l3, l4)
        flow.addFinallyRegion(l1, l2, l3, l4)
        flow.addFilterRegion(l1, l2, l3, l4, l5)

        val builder = BlobBuilder()
        builder.writeByte(0xff.toByte())
        flow.serializeExceptionTable(builder)

        assertContentEquals(
            byteArrayOf(
                0xFF.toByte(), 0x00, 0x00, 0x00, // padding
                0x01, // flag
                0x28, // size = 4 header + 3 * 12-byte small regions = 40
                0x00, 0x00,
                0x04, 0x00, 0x00, 0x00, 0x01, 0x03, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00,
                0x02, 0x00, 0x00, 0x00, 0x01, 0x03, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x01, 0x03, 0x00, 0x03, 0x0A, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun addCatchRegions() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val (l1, l2, l3, l4, _) = layoutLabels(il)

        flow.addCatchRegion(l1, l2, l3, l4, MetadataTokens.typeDefinitionHandle(1).toEntityHandle())
        flow.addCatchRegion(l1, l2, l3, l4, MetadataTokens.typeSpecificationHandle(2).toEntityHandle())
        flow.addCatchRegion(l1, l2, l3, l4, MetadataTokens.typeReferenceHandle(3).toEntityHandle())

        val builder = BlobBuilder()
        flow.serializeExceptionTable(builder)

        assertContentEquals(
            byteArrayOf(
                0x01, 0x28, 0x00, 0x00, // flag + size(0x28) + reserved

                0x00, 0x00, 0x00, 0x00, 0x01, 0x03, 0x00, 0x03, 0x01, 0x00, 0x00, 0x02,
                0x00, 0x00, 0x00, 0x00, 0x01, 0x03, 0x00, 0x03, 0x02, 0x00, 0x00, 0x1B,
                0x00, 0x00, 0x00, 0x00, 0x01, 0x03, 0x00, 0x03, 0x03, 0x00, 0x00, 0x01,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun addRegion_errors1() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)
        val ilx = InstructionEncoder(code, ControlFlowBuilder())

        val labels = layoutLabels(il)
        val l1 = labels[0]; val l2 = labels[1]; val l3 = labels[2]; val l4 = labels[3]; val l5 = labels[4]

        repeat(6) { ilx.defineLabel() }
        val lx = ilx.defineLabel()

        il.markLabel(l1)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l2)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l3)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l4)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l5)

        // invalid catch types:
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, l2, l3, l4, TypeDefinitionHandle(0).toEntityHandle()) }
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, l2, l3, l4, MetadataTokens.methodDefinitionHandle(1).toEntityHandle()) }

        // nil labels:
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(LabelHandle(0), l2, l3, l4, MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, LabelHandle(0), l3, l4, MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, l2, LabelHandle(0), l4, MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, l2, l3, LabelHandle(0), MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }

        // labels of another builder:
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(lx, l2, l3, l4, MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, lx, l3, l4, MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, l2, lx, l4, MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }
        assertFailsWith<IllegalStateException> { flow.addCatchRegion(l1, l2, l3, lx, MetadataTokens.typeReferenceHandle(1).toEntityHandle()) }
    }

    @Test
    fun addRegion_errors2_tryStartAfterTryEnd() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val (l1, l2, l3, l4, _) = layoutLabelsShort(il)

        val streamBuilder = BlobBuilder()
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        flow.addFaultRegion(l2, l1, l3, l4) // tryStart > tryEnd
        assertFailsWith<IllegalStateException> { encoder.addMethodBody(il) }
    }

    @Test
    fun addRegion_errors3_handlerStartAfterHandlerEnd() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val (l1, l2, l3, l4, _) = layoutLabelsShort(il)

        val streamBuilder = BlobBuilder()
        val encoder = MethodBodyStreamEncoder(streamBuilder)

        flow.addFaultRegion(l1, l2, l4, l3) // handlerStart > handlerEnd
        assertFailsWith<IllegalStateException> { encoder.addMethodBody(il) }
    }

    private fun layoutLabelsShort(il: InstructionEncoder): Array<LabelHandle> {
        val l1 = il.defineLabel()
        val l2 = il.defineLabel()
        val l3 = il.defineLabel()
        val l4 = il.defineLabel()

        il.markLabel(l1)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l2)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l3)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l4)

        return arrayOf(l1, l2, l3, l4)
    }

    @Test
    fun branch_shortInstruction_longDistance() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val l1 = il.defineLabel()
        il.branch(ILOpCode.BR_S, l1)
        repeat(100) { il.call(MetadataTokens.methodDefinitionHandle(1)) }
        il.markLabel(l1)
        il.opCode(ILOpCode.RET)

        val builder = BlobBuilder()
        val encoder = MethodBodyStreamEncoder(builder)
        assertFailsWith<IllegalStateException> { encoder.addMethodBody(il) }
    }

    @Test
    fun branch_shortInstruction_shortDistance() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val l1 = il.defineLabel()
        il.branch(ILOpCode.BR_S, l1)
        il.call(MetadataTokens.methodDefinitionHandle(1))
        il.markLabel(l1)
        il.opCode(ILOpCode.RET)

        val builder = BlobBuilder()
        MethodBodyStreamEncoder(builder).addMethodBody(il)

        assertContentEquals(
            byteArrayOf(
                0x22, // tiny header: codeSize=8... (0x22>>2)=8
                ILOpCode.BR_S.value.toByte(), 0x05,
                ILOpCode.CALL.value.toByte(), 0x01, 0x00, 0x00, 0x06,
                ILOpCode.RET.value.toByte(),
            ),
            builder.toArray(),
        )
    }

    @Test
    fun branch_longInstruction_shortDistance() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val l1 = il.defineLabel()
        il.branch(ILOpCode.BR, l1)
        il.call(MetadataTokens.methodDefinitionHandle(1))
        il.markLabel(l1)
        il.opCode(ILOpCode.RET)

        val builder = BlobBuilder()
        MethodBodyStreamEncoder(builder).addMethodBody(il)

        assertContentEquals(
            byteArrayOf(
                0x2E, // tiny header: codeSize=11
                ILOpCode.BR.value.toByte(), 0x05, 0x00, 0x00, 0x00,
                ILOpCode.CALL.value.toByte(), 0x01, 0x00, 0x00, 0x06,
                ILOpCode.RET.value.toByte(),
            ),
            builder.toArray(),
        )
    }

    @Test
    fun branch_longInstruction_longDistance() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val il = InstructionEncoder(code, flow)

        val l1 = il.defineLabel()
        il.branch(ILOpCode.BR, l1)

        repeat(256 / 5 + 1) { il.call(MetadataTokens.methodDefinitionHandle(1)) }

        il.markLabel(l1)
        il.opCode(ILOpCode.RET)

        val builder = BlobBuilder()
        MethodBodyStreamEncoder(builder).addMethodBody(il)

        val bytes = builder.toArray()

        // fat header: flags 0x3013 (fat + initLocals default), maxStack=8
        assertEquals(0x30, bytes[1].toInt() and 0xFF)
        assertEquals(8, bytes[2].toInt() and 0xFF)

        // branch operand fixed up to the label position:
        val brOperand = bytes[13].toInt() or (bytes[14].toInt() shl 8) or
            (bytes[15].toInt() shl 16) or (bytes[16].toInt() shl 24)
        assertTrue(brOperand > 0, "forward distance expected")

        // last instruction is ret:
        assertEquals(ILOpCode.RET.value.toByte(), bytes[bytes.size - 1])
        // total size: header(12) + br(5) + calls*6 + ret(1); calls = 52 -> 12+5+312+1=330? original: 52 calls
        assertEquals(12 + 5 + (256 / 5 + 1) * 5 + 1, bytes.size) // call = opcode + int32 token = 5 bytes
    }

    @Test
    fun switchInstruction() {
        val code = BlobBuilder()
        val il = InstructionEncoder(code, ControlFlowBuilder())

        val lStart = il.defineLabel()
        val l1 = il.defineLabel()
        val l2 = il.defineLabel()
        val l3 = il.defineLabel()

        il.opCode(ILOpCode.NOP)
        il.markLabel(lStart)
        val switchEncoder = il.switch(4)
        switchEncoder.branch(l1)
        switchEncoder.branch(l2)
        switchEncoder.branch(l3)
        switchEncoder.branch(lStart)

        il.markLabel(l1)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l2)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l3)
        il.opCode(ILOpCode.NOP)

        val builder = BlobBuilder()
        MethodBodyStreamEncoder(builder).addMethodBody(il)

        assertContentEquals(
            byteArrayOf(
                0x66, // tiny header: codeSize=25
                ILOpCode.NOP.value.toByte(),
                ILOpCode.SWITCH.value.toByte(), 0x04, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x00, 0x00,
                0x02, 0x00, 0x00, 0x00,
                0xEB.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                ILOpCode.NOP.value.toByte(),
                ILOpCode.NOP.value.toByte(),
                ILOpCode.NOP.value.toByte(),
            ),
            builder.toArray(),
        )
    }

    @Test
    fun clear() {
        val cfb = ControlFlowBuilder()

        val il1 = generateSampleIL(cfb)
        cfb.clear()
        val il2 = generateSampleIL(cfb)

        assertContentEquals(il1, il2)
    }

    private fun generateSampleIL(cfb: ControlFlowBuilder): ByteArray {
        val code = BlobBuilder()
        val il = InstructionEncoder(code, cfb)

        val l1 = il.defineLabel()
        val l2 = il.defineLabel()
        val l3 = il.defineLabel()
        val l4 = il.defineLabel()

        il.markLabel(l1)
        il.opCode(ILOpCode.NOP)
        il.branch(ILOpCode.BR_S, l1)
        il.markLabel(l2)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l3)
        il.opCode(ILOpCode.NOP)
        il.markLabel(l4)

        cfb.addCatchRegion(l1, l2, l3, l4, MetadataTokens.typeDefinitionHandle(1).toEntityHandle())

        val builder = BlobBuilder()
        MethodBodyStreamEncoder(builder).addMethodBody(il)

        return builder.toArray()
    }
}
