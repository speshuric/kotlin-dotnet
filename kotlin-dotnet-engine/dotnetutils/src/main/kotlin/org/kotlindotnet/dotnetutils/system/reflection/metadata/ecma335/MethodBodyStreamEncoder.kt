// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata
//   (Ecma335/Encoding/MethodBodyStreamEncoder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.Blob
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle

/** Encodes method body stream. */
class MethodBodyStreamEncoder(val builder: BlobBuilder) {
    init {
        // Fat methods are 4-byte aligned (ECMA-335 II.25.4.5).
        require(builder.count % 4 == 0) { "builder must be 4-byte aligned" }
    }

    /**
     * Encodes a method body and adds it to the method body stream.
     *
     * @return The offset of the encoded body within the method body stream.
     */
    fun addMethodBody(
        codeSize: Int,
        maxStack: Int = 8,
        exceptionRegionCount: Int = 0,
        hasSmallExceptionRegions: Boolean = true,
        localVariablesSignature: StandaloneSignatureHandle = StandaloneSignatureHandle(0),
        attributes: MethodBodyAttributes = MethodBodyAttributes.INIT_LOCALS,
        hasDynamicStackAllocation: Boolean = false,
    ): MethodBody {
        require(codeSize >= 0) { "codeSize out of range" }
        require(maxStack.toUInt() <= UShort.MAX_VALUE.toUInt()) { "maxStack out of range" }
        check(ExceptionRegionEncoder.isExceptionRegionCountInBounds(exceptionRegionCount)) {
            "exceptionRegionCount out of range"
        }

        val bodyOffset = serializeHeader(
            codeSize,
            maxStack,
            exceptionRegionCount,
            attributes,
            localVariablesSignature,
            hasDynamicStackAllocation,
        )
        val instructions = builder.reserveBytes(codeSize)

        val regionEncoder =
            if (exceptionRegionCount > 0) {
                ExceptionRegionEncoder.serializeTableHeader(builder, exceptionRegionCount, hasSmallExceptionRegions)
            } else {
                null
            }

        return MethodBody(bodyOffset, instructions, regionEncoder)
    }

    /** Encoded method body descriptor. */
    class MethodBody internal constructor(
        /** Offset of the encoded method body in the method body stream. */
        val offset: Int,
        /** Blob reserved for instructions. */
        val instructions: Blob,
        /** Use to encode exception regions to the method body; null if none declared. */
        val exceptionRegions: ExceptionRegionEncoder?,
    )

    /**
     * Encodes a method body from an instruction encoder and adds it to the
     * method body stream.
     *
     * @return The offset of the encoded body within the method body stream.
     */
    fun addMethodBody(
        instructionEncoder: InstructionEncoder,
        maxStack: Int = 8,
        localVariablesSignature: StandaloneSignatureHandle = StandaloneSignatureHandle(0),
        attributes: MethodBodyAttributes = MethodBodyAttributes.INIT_LOCALS,
        hasDynamicStackAllocation: Boolean = false,
    ): Int {
        require(maxStack.toUInt() <= UShort.MAX_VALUE.toUInt()) { "maxStack out of range" }

        // The branch fixup expects branch operands to be contiguous in the code
        // builder — guaranteed when emitting through InstructionEncoder.
        val codeBuilder = instructionEncoder.codeBuilder
        val flowBuilder = instructionEncoder.controlFlowBuilder

        val exceptionRegionCount = flowBuilder?.exceptionHandlerCount ?: 0
        check(ExceptionRegionEncoder.isExceptionRegionCountInBounds(exceptionRegionCount)) {
            "too many exception regions"
        }

        flowBuilder?.validateNotInSwitch()

        // A tiny method with InitLocals and localloc needs a fat header;
        // the caller must declare hasDynamicStackAllocation explicitly
        // (see dotnet/runtime#24948 for rationale).
        val bodyOffset = serializeHeader(
            codeBuilder.count,
            maxStack,
            exceptionRegionCount,
            attributes,
            localVariablesSignature,
            hasDynamicStackAllocation,
        )

        if ((flowBuilder?.branchCount ?: 0) > 0) {
            flowBuilder!!.copyCodeAndFixupBranches(codeBuilder, builder)
        } else {
            codeBuilder.writeContentTo(builder)
        }

        flowBuilder?.serializeExceptionTable(builder)

        return bodyOffset
    }

    private fun serializeHeader(
        codeSize: Int,
        maxStack: Int,
        exceptionRegionCount: Int,
        attributes: MethodBodyAttributes,
        localVariablesSignature: StandaloneSignatureHandle,
        hasDynamicStackAllocation: Boolean,
    ): Int {
        val TINY_FORMAT = 2
        val FAT_FORMAT = 3
        val MORE_SECTIONS = 8
        val INIT_LOCALS_FLAG = 0x10

        val initLocals = (attributes.value and MethodBodyAttributes.INIT_LOCALS.value) != 0

        val isTiny = codeSize < 64 &&
            maxStack <= 8 &&
            localVariablesSignature.isNil && (!hasDynamicStackAllocation || !initLocals) &&
            exceptionRegionCount == 0

        val offset: Int
        if (isTiny) {
            offset = builder.count
            builder.writeByte(((codeSize shl 2) or TINY_FORMAT).toByte())
        } else {
            builder.align(4)

            offset = builder.count

            var flags = (3 shl 12) or FAT_FORMAT
            if (exceptionRegionCount > 0) {
                flags = flags or MORE_SECTIONS
            }
            if (initLocals) {
                flags = flags or INIT_LOCALS_FLAG
            }

            builder.writeUInt16((attributes.value or flags).toUShort())
            builder.writeUInt16(maxStack.toUShort())
            builder.writeInt32(codeSize)
            builder.writeInt32(if (localVariablesSignature.isNil) 0 else MetadataTokens.getToken(localVariablesSignature.toEntityHandle()))
        }

        return offset
    }
}
