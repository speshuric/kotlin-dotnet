// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata
//   (Ecma335/Encoding/InstructionEncoder.cs,
//    Ecma335/Encoding/SwitchInstructionEncoder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MemberReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodSpecificationHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.getBranchOperandSize

/** Encodes instructions. */
class InstructionEncoder(
    /** Builder to write encoded instructions to. */
    val codeBuilder: BlobBuilder,
    /**
     * Builder tracking labels, branches and exception handlers.
     * If null the encoder doesn't support construction of control flow.
     */
    val controlFlowBuilder: ControlFlowBuilder? = null,
) {
    /** Offset of the next encoded instruction. */
    val offset: Int
        get() = codeBuilder.count

    /** Encodes the specified op-code. */
    fun opCode(code: ILOpCode) {
        controlFlowBuilder?.validateNotInSwitch()
        if (code.value <= 0xff) {
            codeBuilder.writeByte(code.value.toByte())
        } else {
            // Two-byte opcodes are written high-order byte first.
            codeBuilder.writeUInt16BE(code.value.toUShort())
        }
    }

    /** Encodes a token. */
    fun token(handle: EntityHandle) {
        token(MetadataTokens.getToken(handle))
    }

    /** Encodes a token. */
    fun token(token: Int) {
        controlFlowBuilder?.validateNotInSwitch()
        codeBuilder.writeInt32(token)
    }

    /** Encodes `ldstr` instruction and its operand. */
    fun loadString(handle: UserStringHandle) {
        opCode(ILOpCode.LDSTR)
        token(MetadataTokens.getToken(handle.toHandle()))
    }

    /** Encodes `call` instruction and its operand. */
    fun call(methodHandle: EntityHandle) {
        check(
            methodHandle.kind == org.kotlindotnet.dotnetutils.system.reflection.metadata.HandleKind.METHOD_DEFINITION ||
                methodHandle.kind == org.kotlindotnet.dotnetutils.system.reflection.metadata.HandleKind.METHOD_SPECIFICATION ||
                methodHandle.kind == org.kotlindotnet.dotnetutils.system.reflection.metadata.HandleKind.MEMBER_REFERENCE,
        ) { "unexpected method handle kind" }
        opCode(ILOpCode.CALL)
        token(methodHandle)
    }

    /** Encodes `call` instruction and its operand. */
    fun call(methodHandle: MethodDefinitionHandle) {
        opCode(ILOpCode.CALL)
        token(MetadataTokens.getToken(methodHandle.toEntityHandle()))
    }

    /** Encodes `call` instruction and its operand. */
    fun call(methodHandle: MethodSpecificationHandle) {
        opCode(ILOpCode.CALL)
        token(MetadataTokens.getToken(methodHandle.toEntityHandle()))
    }

    /** Encodes `call` instruction and its operand. */
    fun call(methodHandle: MemberReferenceHandle) {
        opCode(ILOpCode.CALL)
        token(MetadataTokens.getToken(methodHandle.toEntityHandle()))
    }

    /** Encodes `calli` instruction and its operand. */
    fun callIndirect(signature: StandaloneSignatureHandle) {
        opCode(ILOpCode.CALLI)
        token(MetadataTokens.getToken(signature.toEntityHandle()))
    }

    /** Encodes an int constant load instruction. */
    fun loadConstantI4(value: Int) {
        when (value) {
            -1 -> opCode(ILOpCode.LDC_I4_M1)
            0 -> opCode(ILOpCode.LDC_I4_0)
            1 -> opCode(ILOpCode.LDC_I4_1)
            2 -> opCode(ILOpCode.LDC_I4_2)
            3 -> opCode(ILOpCode.LDC_I4_3)
            4 -> opCode(ILOpCode.LDC_I4_4)
            5 -> opCode(ILOpCode.LDC_I4_5)
            6 -> opCode(ILOpCode.LDC_I4_6)
            7 -> opCode(ILOpCode.LDC_I4_7)
            8 -> opCode(ILOpCode.LDC_I4_8)
            else -> {
                if (value.toByte().toInt() == value) {
                    opCode(ILOpCode.LDC_I4_S)
                    codeBuilder.writeSByte(value.toByte())
                } else {
                    opCode(ILOpCode.LDC_I4)
                    codeBuilder.writeInt32(value)
                }
                return
            }
        }
    }

    /** Encodes a long constant load instruction. */
    fun loadConstantI8(value: Long) {
        opCode(ILOpCode.LDC_I8)
        codeBuilder.writeInt64(value)
    }

    /** Encodes a float constant load instruction. */
    fun loadConstantR4(value: Float) {
        opCode(ILOpCode.LDC_R4)
        codeBuilder.writeSingle(value)
    }

    /** Encodes a double constant load instruction. */
    fun loadConstantR8(value: Double) {
        opCode(ILOpCode.LDC_R8)
        codeBuilder.writeDouble(value)
    }

    /** Encodes a local variable load instruction. */
    fun loadLocal(slotIndex: Int) {
        when (slotIndex) {
            0 -> opCode(ILOpCode.LDLOC_0)
            1 -> opCode(ILOpCode.LDLOC_1)
            2 -> opCode(ILOpCode.LDLOC_2)
            3 -> opCode(ILOpCode.LDLOC_3)
            else -> {
                if (slotIndex.toUInt() <= UByte.MAX_VALUE.toUInt()) {
                    opCode(ILOpCode.LDLOC_S)
                    codeBuilder.writeByte(slotIndex.toByte())
                } else if (slotIndex > 0) {
                    opCode(ILOpCode.LDLOC)
                    codeBuilder.writeInt32(slotIndex)
                } else {
                    throw IllegalArgumentException("slotIndex out of range")
                }
            }
        }
    }

    /** Encodes a local variable store instruction. */
    fun storeLocal(slotIndex: Int) {
        when (slotIndex) {
            0 -> opCode(ILOpCode.STLOC_0)
            1 -> opCode(ILOpCode.STLOC_1)
            2 -> opCode(ILOpCode.STLOC_2)
            3 -> opCode(ILOpCode.STLOC_3)
            else -> {
                if (slotIndex.toUInt() <= UByte.MAX_VALUE.toUInt()) {
                    opCode(ILOpCode.STLOC_S)
                    codeBuilder.writeByte(slotIndex.toByte())
                } else if (slotIndex > 0) {
                    opCode(ILOpCode.STLOC)
                    codeBuilder.writeInt32(slotIndex)
                } else {
                    throw IllegalArgumentException("slotIndex out of range")
                }
            }
        }
    }

    /** Encodes a local variable address load instruction. */
    fun loadLocalAddress(slotIndex: Int) {
        if (slotIndex.toUInt() <= UByte.MAX_VALUE.toUInt()) {
            opCode(ILOpCode.LDLOCA_S)
            codeBuilder.writeByte(slotIndex.toByte())
        } else if (slotIndex > 0) {
            opCode(ILOpCode.LDLOCA)
            codeBuilder.writeInt32(slotIndex)
        } else {
            throw IllegalArgumentException("slotIndex out of range")
        }
    }

    /** Encodes an argument load instruction. */
    fun loadArgument(argumentIndex: Int) {
        when (argumentIndex) {
            0 -> opCode(ILOpCode.LDARG_0)
            1 -> opCode(ILOpCode.LDARG_1)
            2 -> opCode(ILOpCode.LDARG_2)
            3 -> opCode(ILOpCode.LDARG_3)
            else -> {
                if (argumentIndex.toUInt() <= UByte.MAX_VALUE.toUInt()) {
                    opCode(ILOpCode.LDARG_S)
                    codeBuilder.writeByte(argumentIndex.toByte())
                } else if (argumentIndex > 0) {
                    opCode(ILOpCode.LDARG)
                    codeBuilder.writeInt32(argumentIndex)
                } else {
                    throw IllegalArgumentException("argumentIndex out of range")
                }
            }
        }
    }

    /** Encodes an argument address load instruction. */
    fun loadArgumentAddress(argumentIndex: Int) {
        if (argumentIndex.toUInt() <= UByte.MAX_VALUE.toUInt()) {
            opCode(ILOpCode.LDARGA_S)
            codeBuilder.writeByte(argumentIndex.toByte())
        } else if (argumentIndex > 0) {
            opCode(ILOpCode.LDARGA)
            codeBuilder.writeInt32(argumentIndex)
        } else {
            throw IllegalArgumentException("argumentIndex out of range")
        }
    }

    /** Encodes an argument store instruction. */
    fun storeArgument(argumentIndex: Int) {
        if (argumentIndex.toUInt() <= UByte.MAX_VALUE.toUInt()) {
            opCode(ILOpCode.STARG_S)
            codeBuilder.writeByte(argumentIndex.toByte())
        } else if (argumentIndex > 0) {
            opCode(ILOpCode.STARG)
            codeBuilder.writeInt32(argumentIndex)
        } else {
            throw IllegalArgumentException("argumentIndex out of range")
        }
    }

    /**
     * Defines a label that can later be used to mark and refer to a location
     * in the instruction stream.
     */
    fun defineLabel(): LabelHandle = getBranchBuilder().addLabel()

    internal fun labelOperand(code: ILOpCode, label: LabelHandle, instructionEndDisplacement: Int, ilOffset: Int) {
        getBranchBuilder().addBranch(offset, label, instructionEndDisplacement, ilOffset, code)

        // -1 points into the middle of the branch instruction and is thus invalid:
        // we produce invalid IL so that unpached branches fail in an obvious way.
        if (instructionEndDisplacement == 1) {
            codeBuilder.writeSByte(-1)
        } else {
            codeBuilder.writeInt32(-1)
        }
    }

    /**
     * Encodes a branch instruction.
     *
     * @throws IllegalArgumentException when [code] is not a branch instruction.
     */
    fun branch(code: ILOpCode, label: LabelHandle) {
        // throws if code is not a branch:
        val operandSize = code.getBranchOperandSize()
        // We want the offset before we add the opcode.
        val ilOffset = offset

        opCode(code)
        labelOperand(code, label, operandSize, ilOffset)
    }

    /**
     * Starts encoding a switch instruction. Before using this encoder in any
     * other way, [SwitchInstructionEncoder.branch] must be called on the
     * returned value exactly [branchCount] times.
     */
    fun switch(branchCount: Int): SwitchInstructionEncoder {
        require(branchCount > 0) { "branchCount out of range" }
        val branchBuilder = getBranchBuilder()

        // We want the offset before we add the opcode.
        val ilOffset = offset

        opCode(ILOpCode.SWITCH)
        branchBuilder.remainingSwitchBranches = branchCount
        codeBuilder.writeUInt32(branchCount.toUInt())

        // Offset where the instruction will end: opcode byte + 4-byte count
        // + four bytes for each expected branch.
        val instructionEnd = offset + 4 * branchCount
        return SwitchInstructionEncoder(this, ilOffset, instructionEnd)
    }

    /** Associates the specified label with the current IL offset. */
    fun markLabel(label: LabelHandle) {
        getBranchBuilder().markLabel(offset, label)
    }

    private fun getBranchBuilder(): ControlFlowBuilder =
        checkNotNull(controlFlowBuilder) { "control flow builder is not available" }
}

/**
 * Encodes the branches of an IL `switch` instruction.
 *
 * See [InstructionEncoder.switch] for usage guidelines.
 */
class SwitchInstructionEncoder internal constructor(
    private val encoder: InstructionEncoder,
    private val ilOffset: Int,
    private val instructionEnd: Int,
) {
    /** Encodes a branch that is part of a switch instruction. */
    fun branch(label: LabelHandle) {
        encoder.controlFlowBuilder!!.switchBranchAdded()
        encoder.labelOperand(ILOpCode.SWITCH, label, instructionEnd - encoder.offset, ilOffset)
    }
}
