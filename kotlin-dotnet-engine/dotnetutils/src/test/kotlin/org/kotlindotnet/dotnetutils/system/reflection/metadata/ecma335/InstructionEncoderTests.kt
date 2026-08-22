// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata tests (Ecma335/Encoding/InstructionEncoderTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
//
// Adaptations:
//  - ArgumentNullException for the codeBuilder parameter is enforced by
//    Kotlin's type system and therefore not tested;
//  - C# InvalidOperationException/ArgumentNullException map to
//    IllegalStateException/IllegalArgumentException per the port's
//    fail-fast convention (ADR 0009).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class InstructionEncoderTests {

    @Test
    fun ctor() {
        val code = BlobBuilder()
        val flow = ControlFlowBuilder()
        val ie = InstructionEncoder(code, flow)
        assertSame(code, ie.codeBuilder)
        assertSame(flow, ie.controlFlowBuilder)
        // null codeBuilder is impossible in Kotlin (non-null parameter type)
    }

    @Test
    fun opCode() {
        val builder = BlobBuilder()
        builder.writeByte(0xff.toByte())

        val il = InstructionEncoder(builder)
        assertEquals(1, il.offset)

        il.opCode(ILOpCode.ADD)
        assertEquals(2, il.offset)

        builder.writeByte(0xee.toByte())
        assertEquals(3, il.offset)

        il.opCode(ILOpCode.ARGLIST)
        assertEquals(5, il.offset)

        builder.writeByte(0xdd.toByte())
        assertEquals(6, il.offset)

        il.opCode(ILOpCode.READONLY)
        assertEquals(8, il.offset)

        builder.writeByte(0xcc.toByte())
        assertEquals(9, il.offset)

        assertContentEquals(
            byteArrayOf(
                0xFF.toByte(),
                0x58,
                0xEE.toByte(),
                0xFE.toByte(), 0x00,
                0xDD.toByte(),
                0xFE.toByte(), 0x1E,
                0xCC.toByte(),
            ),
            builder.toArray(),
        )
    }

    @Test
    fun tokenInstructions() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)
        assertEquals(0, il.offset)

        il.token(MetadataTokens.typeDefinitionHandle(0x123456).toEntityHandle())
        assertEquals(4, il.offset)

        il.token(0x7F7E7D7C)
        assertEquals(8, il.offset)

        il.loadString(MetadataTokens.userStringHandle(0x010203))
        assertEquals(13, il.offset)

        il.call(MetadataTokens.methodDefinitionHandle(0xA0A1A2))
        assertEquals(18, il.offset)

        il.call(MetadataTokens.methodSpecificationHandle(0xB0B1B2))
        assertEquals(23, il.offset)

        il.call(MetadataTokens.memberReferenceHandle(0xC0C1C2))
        assertEquals(28, il.offset)

        il.call(MetadataTokens.methodDefinitionHandle(0xD0D1D2).toEntityHandle())
        assertEquals(33, il.offset)

        il.call(MetadataTokens.methodSpecificationHandle(0xE0E1E2).toEntityHandle())
        assertEquals(38, il.offset)

        il.call(MetadataTokens.memberReferenceHandle(0xF0F1F2).toEntityHandle())
        assertEquals(43, il.offset)

        il.callIndirect(MetadataTokens.standaloneSignatureHandle(0x001122))
        assertEquals(48, il.offset)

        assertContentEquals(
            byteArrayOf(
                0x56, 0x34, 0x12, 0x02,
                0x7C, 0x7D, 0x7E, 0x7F,
                ILOpCode.LDSTR.value.toByte(), 0x03, 0x02, 0x01, 0x70,
                ILOpCode.CALL.value.toByte(), 0xA2.toByte(), 0xA1.toByte(), 0xA0.toByte(), 0x06,
                ILOpCode.CALL.value.toByte(), 0xB2.toByte(), 0xB1.toByte(), 0xB0.toByte(), 0x2B,
                ILOpCode.CALL.value.toByte(), 0xC2.toByte(), 0xC1.toByte(), 0xC0.toByte(), 0x0A,
                ILOpCode.CALL.value.toByte(), 0xD2.toByte(), 0xD1.toByte(), 0xD0.toByte(), 0x06,
                ILOpCode.CALL.value.toByte(), 0xE2.toByte(), 0xE1.toByte(), 0xE0.toByte(), 0x2B,
                ILOpCode.CALL.value.toByte(), 0xF2.toByte(), 0xF1.toByte(), 0xF0.toByte(), 0x0A,
                ILOpCode.CALLI.value.toByte(), 0x22, 0x11, 0x00, 0x11,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun loadConstantI4() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        for (i in -1..8) {
            il.loadConstantI4(i)
        }

        il.loadConstantI4(Byte.MIN_VALUE.toInt())
        il.loadConstantI4(Byte.MAX_VALUE.toInt())
        il.loadConstantI4(-2)
        il.loadConstantI4(10)
        il.loadConstantI4(Int.MIN_VALUE)
        il.loadConstantI4(Int.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDC_I4_M1.value.toByte(),
                ILOpCode.LDC_I4_0.value.toByte(),
                ILOpCode.LDC_I4_1.value.toByte(),
                ILOpCode.LDC_I4_2.value.toByte(),
                ILOpCode.LDC_I4_3.value.toByte(),
                ILOpCode.LDC_I4_4.value.toByte(),
                ILOpCode.LDC_I4_5.value.toByte(),
                ILOpCode.LDC_I4_6.value.toByte(),
                ILOpCode.LDC_I4_7.value.toByte(),
                ILOpCode.LDC_I4_8.value.toByte(),
                ILOpCode.LDC_I4_S.value.toByte(), 0x80.toByte(),
                ILOpCode.LDC_I4_S.value.toByte(), 0x7F,
                ILOpCode.LDC_I4_S.value.toByte(), 0xFE.toByte(),
                ILOpCode.LDC_I4_S.value.toByte(), 0x0A,
                ILOpCode.LDC_I4.value.toByte(), 0x00, 0x00, 0x00, 0x80.toByte(),
                ILOpCode.LDC_I4.value.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun loadConstantI8() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.loadConstantI8(0)
        il.loadConstantI8(Long.MIN_VALUE)
        il.loadConstantI8(Long.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDC_I8.value.toByte(), 0, 0, 0, 0, 0, 0, 0, 0,
                ILOpCode.LDC_I8.value.toByte(), 0, 0, 0, 0, 0, 0, 0, 0x80.toByte(),
                ILOpCode.LDC_I8.value.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun loadConstantR4() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.loadConstantR4(0.0f)
        il.loadConstantR4(Float.MAX_VALUE)
        il.loadConstantR4(-Float.MAX_VALUE)
        il.loadConstantR4(Float.NaN)
        il.loadConstantR4(Float.NEGATIVE_INFINITY)
        il.loadConstantR4(Float.POSITIVE_INFINITY)
        il.loadConstantR4(Float.MIN_VALUE) // smallest positive

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDC_R4.value.toByte(), 0x00, 0x00, 0x00, 0x00,
                ILOpCode.LDC_R4.value.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0x7F,
                ILOpCode.LDC_R4.value.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F, 0xFF.toByte(),
                ILOpCode.LDC_R4.value.toByte(), 0x00, 0x00, 0xC0.toByte(), 0x7F, // JVM NaN is positive quiet NaN
                ILOpCode.LDC_R4.value.toByte(), 0x00, 0x00, 0x80.toByte(), 0xFF.toByte(),
                ILOpCode.LDC_R4.value.toByte(), 0x00, 0x00, 0x80.toByte(), 0x7F,
                ILOpCode.LDC_R4.value.toByte(), 0x01, 0x00, 0x00, 0x00,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun loadConstantR8() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.loadConstantR8(0.0)
        il.loadConstantR8(Double.MAX_VALUE)
        il.loadConstantR8(-Double.MAX_VALUE)
        il.loadConstantR8(Double.NaN)
        il.loadConstantR8(Double.NEGATIVE_INFINITY)
        il.loadConstantR8(Double.POSITIVE_INFINITY)
        il.loadConstantR8(Double.MIN_VALUE) // smallest positive

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDC_R8.value.toByte(), 0, 0, 0, 0, 0, 0, 0, 0,
                ILOpCode.LDC_R8.value.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xEF.toByte(), 0x7F,
                ILOpCode.LDC_R8.value.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xEF.toByte(), 0xFF.toByte(),
                ILOpCode.LDC_R8.value.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF8.toByte(), 0x7F, // JVM NaN
                ILOpCode.LDC_R8.value.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0xFF.toByte(),
                ILOpCode.LDC_R8.value.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0.toByte(), 0x7F,
                ILOpCode.LDC_R8.value.toByte(), 0x01, 0, 0, 0, 0, 0, 0, 0,
            ),
            builder.toArray(),
        )
    }

    @Test
    fun loadLocal() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.loadLocal(0)
        il.loadLocal(1)
        il.loadLocal(2)
        il.loadLocal(3)
        il.loadLocal(4)
        il.loadLocal(UByte.MAX_VALUE.toInt())
        il.loadLocal(UByte.MAX_VALUE.toInt() + 1)
        il.loadLocal(Int.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDLOC_0.value.toByte(),
                ILOpCode.LDLOC_1.value.toByte(),
                ILOpCode.LDLOC_2.value.toByte(),
                ILOpCode.LDLOC_3.value.toByte(),
                ILOpCode.LDLOC_S.value.toByte(), 0x04,
                ILOpCode.LDLOC_S.value.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x0C, 0x00, 0x01, 0x00, 0x00,
                0xFE.toByte(), 0x0C, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )

        assertFailsWith<IllegalArgumentException> { il.loadLocal(-1) }
        assertFailsWith<IllegalArgumentException> { il.loadLocal(Int.MIN_VALUE) }
    }

    @Test
    fun storeLocal() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.storeLocal(0)
        il.storeLocal(1)
        il.storeLocal(2)
        il.storeLocal(3)
        il.storeLocal(4)
        il.storeLocal(UByte.MAX_VALUE.toInt())
        il.storeLocal(UByte.MAX_VALUE.toInt() + 1)
        il.storeLocal(Int.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.STLOC_0.value.toByte(),
                ILOpCode.STLOC_1.value.toByte(),
                ILOpCode.STLOC_2.value.toByte(),
                ILOpCode.STLOC_3.value.toByte(),
                ILOpCode.STLOC_S.value.toByte(), 0x04,
                ILOpCode.STLOC_S.value.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x0E, 0x00, 0x01, 0x00, 0x00,
                0xFE.toByte(), 0x0E, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )

        assertFailsWith<IllegalArgumentException> { il.storeLocal(-1) }
        assertFailsWith<IllegalArgumentException> { il.storeLocal(Int.MIN_VALUE) }
    }

    @Test
    fun loadLocalAddress() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.loadLocalAddress(0)
        il.loadLocalAddress(1)
        il.loadLocalAddress(2)
        il.loadLocalAddress(3)
        il.loadLocalAddress(4)
        il.loadLocalAddress(UByte.MAX_VALUE.toInt())
        il.loadLocalAddress(UByte.MAX_VALUE.toInt() + 1)
        il.loadLocalAddress(Int.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDLOCA_S.value.toByte(), 0x00,
                ILOpCode.LDLOCA_S.value.toByte(), 0x01,
                ILOpCode.LDLOCA_S.value.toByte(), 0x02,
                ILOpCode.LDLOCA_S.value.toByte(), 0x03,
                ILOpCode.LDLOCA_S.value.toByte(), 0x04,
                ILOpCode.LDLOCA_S.value.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x0D, 0x00, 0x01, 0x00, 0x00,
                0xFE.toByte(), 0x0D, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )

        assertFailsWith<IllegalArgumentException> { il.loadLocalAddress(-1) }
        assertFailsWith<IllegalArgumentException> { il.loadLocalAddress(Int.MIN_VALUE) }
    }

    @Test
    fun loadArgument() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.loadArgument(0)
        il.loadArgument(1)
        il.loadArgument(2)
        il.loadArgument(3)
        il.loadArgument(4)
        il.loadArgument(UByte.MAX_VALUE.toInt())
        il.loadArgument(UByte.MAX_VALUE.toInt() + 1)
        il.loadArgument(Int.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDARG_0.value.toByte(),
                ILOpCode.LDARG_1.value.toByte(),
                ILOpCode.LDARG_2.value.toByte(),
                ILOpCode.LDARG_3.value.toByte(),
                ILOpCode.LDARG_S.value.toByte(), 0x04,
                ILOpCode.LDARG_S.value.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x09, 0x00, 0x01, 0x00, 0x00,
                0xFE.toByte(), 0x09, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )

        assertFailsWith<IllegalArgumentException> { il.loadArgument(-1) }
        assertFailsWith<IllegalArgumentException> { il.loadArgument(Int.MIN_VALUE) }
    }

    @Test
    fun loadArgumentAddress() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.loadArgumentAddress(0)
        il.loadArgumentAddress(1)
        il.loadArgumentAddress(2)
        il.loadArgumentAddress(3)
        il.loadArgumentAddress(4)
        il.loadArgumentAddress(UByte.MAX_VALUE.toInt())
        il.loadArgumentAddress(UByte.MAX_VALUE.toInt() + 1)
        il.loadArgumentAddress(Int.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.LDARGA_S.value.toByte(), 0x00,
                ILOpCode.LDARGA_S.value.toByte(), 0x01,
                ILOpCode.LDARGA_S.value.toByte(), 0x02,
                ILOpCode.LDARGA_S.value.toByte(), 0x03,
                ILOpCode.LDARGA_S.value.toByte(), 0x04,
                ILOpCode.LDARGA_S.value.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x0A, 0x00, 0x01, 0x00, 0x00,
                0xFE.toByte(), 0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )

        assertFailsWith<IllegalArgumentException> { il.loadArgumentAddress(-1) }
        assertFailsWith<IllegalArgumentException> { il.loadArgumentAddress(Int.MIN_VALUE) }
    }

    @Test
    fun storeArgument() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)

        il.storeArgument(0)
        il.storeArgument(1)
        il.storeArgument(2)
        il.storeArgument(3)
        il.storeArgument(4)
        il.storeArgument(UByte.MAX_VALUE.toInt())
        il.storeArgument(UByte.MAX_VALUE.toInt() + 1)
        il.storeArgument(Int.MAX_VALUE)

        assertContentEquals(
            byteArrayOf(
                ILOpCode.STARG_S.value.toByte(), 0x00,
                ILOpCode.STARG_S.value.toByte(), 0x01,
                ILOpCode.STARG_S.value.toByte(), 0x02,
                ILOpCode.STARG_S.value.toByte(), 0x03,
                ILOpCode.STARG_S.value.toByte(), 0x04,
                ILOpCode.STARG_S.value.toByte(), 0xFF.toByte(),
                0xFE.toByte(), 0x0B, 0x00, 0x01, 0x00, 0x00,
                0xFE.toByte(), 0x0B, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F,
            ),
            builder.toArray(),
        )

        assertFailsWith<IllegalArgumentException> { il.storeArgument(-1) }
        assertFailsWith<IllegalArgumentException> { il.storeArgument(Int.MIN_VALUE) }
    }

    private val shortBranches = listOf(
        ILOpCode.BR_S, ILOpCode.BRFALSE_S, ILOpCode.BRTRUE_S, ILOpCode.BEQ_S,
        ILOpCode.BGE_S, ILOpCode.BGT_S, ILOpCode.BLE_S, ILOpCode.BLT_S,
        ILOpCode.BNE_UN_S, ILOpCode.BGE_UN_S, ILOpCode.BGT_UN_S, ILOpCode.BLE_UN_S,
        ILOpCode.BLT_UN_S, ILOpCode.LEAVE_S,
    )

    private val longBranches = listOf(
        ILOpCode.BR, ILOpCode.BRFALSE, ILOpCode.BRTRUE, ILOpCode.BEQ,
        ILOpCode.BGE, ILOpCode.BGT, ILOpCode.BLE, ILOpCode.BLT,
        ILOpCode.BNE_UN, ILOpCode.BGE_UN, ILOpCode.BGT_UN, ILOpCode.BLE_UN,
        ILOpCode.BLT_UN, ILOpCode.LEAVE,
    )

    @Test
    fun branch() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder, ControlFlowBuilder())
        val l = il.defineLabel()

        for (op in shortBranches) il.branch(op, l)
        for (op in longBranches) il.branch(op, l)

        val expected = ArrayList<Byte>()
        for (op in shortBranches) {
            expected.add(op.value.toByte())
            expected.add(0xff.toByte())
        }
        for (op in longBranches) {
            expected.add(op.value.toByte())
            expected.addAll(listOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()))
        }
        assertContentEquals(expected.toByteArray(), builder.toArray())
    }

    @Test
    fun switchInstruction() {
        val builder = BlobBuilder()
        val controlFlowBuilder = ControlFlowBuilder()
        val il = InstructionEncoder(builder, controlFlowBuilder)
        val l = il.defineLabel()
        val switchEncoder = il.switch(4)
        assertFailsWith<IllegalStateException> { il.opCode(ILOpCode.NOP) }
        assertFailsWith<IllegalStateException> { il.token(0) }
        assertFailsWith<IllegalStateException> { il.defineLabel() }
        assertFailsWith<IllegalStateException> { il.markLabel(l) }
        assertFailsWith<IllegalStateException> { controlFlowBuilder.addFinallyRegion(l, l, l, l) }
        assertFailsWith<IllegalStateException> { MethodBodyStreamEncoder(BlobBuilder()).addMethodBody(il) }
        switchEncoder.branch(l)
        switchEncoder.branch(l)
        switchEncoder.branch(l)
        switchEncoder.branch(l)
        assertFailsWith<IllegalStateException> { switchEncoder.branch(l) }

        assertContentEquals(
            byteArrayOf(
                ILOpCode.SWITCH.value.toByte(), 0x04, 0x00, 0x00, 0x00,
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            ),
            builder.toArray(),
        )
    }

    @Test
    fun branchAndLabel_errors() {
        val builder = BlobBuilder()
        val il = InstructionEncoder(builder)
        val ilcf1 = InstructionEncoder(builder, ControlFlowBuilder())
        val ilcf2 = InstructionEncoder(builder, ControlFlowBuilder())

        val l1 = ilcf1.defineLabel()
        ilcf2.defineLabel()
        val l2 = ilcf2.defineLabel()

        // encoder without a control-flow builder:
        assertFailsWith<IllegalStateException> { il.defineLabel() }
        assertFailsWith<IllegalStateException> { il.branch(ILOpCode.BR, LabelHandle(0)) }
        assertFailsWith<IllegalStateException> { il.markLabel(LabelHandle(0)) }

        // nil labels:
        assertFailsWith<IllegalStateException> { ilcf1.branch(ILOpCode.BR, LabelHandle(0)) }
        assertFailsWith<IllegalStateException> { ilcf1.markLabel(LabelHandle(0)) }
        // label of another builder:
        assertFailsWith<IllegalStateException> { ilcf1.branch(ILOpCode.BR, l2) }
        assertFailsWith<IllegalStateException> { ilcf1.markLabel(l2) }
        // not a branch op-code:
        assertFailsWith<IllegalArgumentException> { ilcf1.branch(ILOpCode.BOX, l1) }
    }

    @Test
    fun markLabel_lastMarkWins() {
        val code = BlobBuilder()
        val il = InstructionEncoder(code, ControlFlowBuilder())

        val l = il.defineLabel()

        il.branch(ILOpCode.BR_S, l)

        il.markLabel(l)
        il.opCode(ILOpCode.NOP)
        il.opCode(ILOpCode.NOP)

        il.markLabel(l)
        il.opCode(ILOpCode.RET)

        val builder = BlobBuilder()
        MethodBodyStreamEncoder(builder).addMethodBody(il)

        assertContentEquals(
            byteArrayOf(
                0x16, // tiny header: codeSize=5
                ILOpCode.BR_S.value.toByte(), 0x02,
                ILOpCode.NOP.value.toByte(),
                ILOpCode.NOP.value.toByte(),
                ILOpCode.RET.value.toByte(),
            ),
            builder.toArray(),
        )
    }
}
