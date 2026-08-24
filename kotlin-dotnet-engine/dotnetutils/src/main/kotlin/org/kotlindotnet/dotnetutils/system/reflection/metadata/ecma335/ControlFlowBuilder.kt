// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata
//   (Ecma335/Encoding/ControlFlowBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ExceptionRegionKind
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode

class ControlFlowBuilder {
    /** Port of the internal `BranchInfo` struct. */
    internal data class BranchInfo(
        val operandOffset: Int,
        val label: LabelHandle,
        // Label offsets are calculated from the end of the instruction that
        // contains them: 1 on short branches, 4 on long branches, more on switch.
        private val instructionEndDisplacement: Int,
        val ilOffset: Int,
        val opCode: ILOpCode,
    ) {
        val isShortBranch: Boolean
            get() = instructionEndDisplacement == 1

        val operandSize: Int
            get() = minOf(instructionEndDisplacement, 4)

        fun getBranchDistance(labels: List<Int>): Int {
            val labelTargetOffset = labels[label.id - 1]
            check(labelTargetOffset >= 0) { "label ${label.id} was not marked" }

            val distance = labelTargetOffset - (operandOffset + instructionEndDisplacement)

            if (isShortBranch && distance.toByte().toInt() != distance) {
                throw IllegalStateException(
                    "distance between branch $opCode (at IL offset $ilOffset) and its label is too big for a short branch: $distance",
                )
            }

            return distance
        }

        companion object {
            /** Sentinel used after the last real branch has been processed. */
            internal val END = BranchInfo(Int.MAX_VALUE, LabelHandle.fromId(0), 0, 0, ILOpCode.NOP)
        }
    }

    /** Port of the internal `ExceptionHandlerInfo` struct. */
    internal data class ExceptionHandlerInfo(
        val kind: ExceptionRegionKind,
        val tryStart: LabelHandle,
        val tryEnd: LabelHandle,
        val handlerStart: LabelHandle,
        val handlerEnd: LabelHandle,
        val filterStart: LabelHandle,
        val catchType: EntityHandle,
    )

    private val branches = ArrayList<BranchInfo>()
    private val labels = ArrayList<Int>()
    private var lazyExceptionHandlers: MutableList<ExceptionHandlerInfo>? = null

    var remainingSwitchBranches: Int = 0
        internal set

    fun clear() {
        branches.clear()
        labels.clear()
        lazyExceptionHandlers?.clear()
        remainingSwitchBranches = 0
    }

    internal fun addLabel(): LabelHandle {
        validateNotInSwitch()
        labels.add(-1)
        return LabelHandle.fromId(labels.size)
    }

    internal fun addBranch(operandOffset: Int, label: LabelHandle, instructionEndDisplacement: Int, ilOffset: Int, opCode: ILOpCode) {
        assert(operandOffset >= 0)
        assert(branches.isEmpty() || operandOffset > branches.last().operandOffset)
        validateLabel(label)
        branches.add(BranchInfo(operandOffset, label, instructionEndDisplacement, ilOffset, opCode))
    }

    internal fun markLabel(ilOffset: Int, label: LabelHandle) {
        assert(ilOffset >= 0)
        validateNotInSwitch()
        validateLabel(label)
        labels[label.id - 1] = ilOffset
    }

    internal fun getLabelOffsetChecked(label: LabelHandle): Int {
        val offset = labels[label.id - 1]
        check(offset >= 0) { "label ${label.id} was not marked" }
        return offset
    }

    private fun validateLabel(label: LabelHandle) {
        check(!label.isNil) { "label has default value" }
        check(label.id <= labels.size) { "label does not belong to this builder" }
    }

    fun addFinallyRegion(tryStart: LabelHandle, tryEnd: LabelHandle, handlerStart: LabelHandle, handlerEnd: LabelHandle) =
        addExceptionRegion(ExceptionRegionKind.FINALLY, tryStart, tryEnd, handlerStart, handlerEnd)

    fun addFaultRegion(tryStart: LabelHandle, tryEnd: LabelHandle, handlerStart: LabelHandle, handlerEnd: LabelHandle) =
        addExceptionRegion(ExceptionRegionKind.FAULT, tryStart, tryEnd, handlerStart, handlerEnd)

    fun addCatchRegion(
        tryStart: LabelHandle,
        tryEnd: LabelHandle,
        handlerStart: LabelHandle,
        handlerEnd: LabelHandle,
        catchType: EntityHandle,
    ) {
        check(ExceptionRegionEncoder.isValidCatchTypeHandle(catchType)) { "unexpected catch type handle" }
        addExceptionRegion(ExceptionRegionKind.CATCH, tryStart, tryEnd, handlerStart, handlerEnd, catchType = catchType)
    }

    fun addFilterRegion(
        tryStart: LabelHandle,
        tryEnd: LabelHandle,
        handlerStart: LabelHandle,
        handlerEnd: LabelHandle,
        filterStart: LabelHandle,
    ) {
        validateLabel(filterStart)
        addExceptionRegion(ExceptionRegionKind.FILTER, tryStart, tryEnd, handlerStart, handlerEnd, filterStart = filterStart)
    }

    private fun addExceptionRegion(
        kind: ExceptionRegionKind,
        tryStart: LabelHandle,
        tryEnd: LabelHandle,
        handlerStart: LabelHandle,
        handlerEnd: LabelHandle,
        filterStart: LabelHandle = LabelHandle.fromId(0),
        catchType: EntityHandle = EntityHandle(0u),
    ) {
        validateLabel(tryStart)
        validateLabel(tryEnd)
        validateLabel(handlerStart)
        validateLabel(handlerEnd)
        validateNotInSwitch()

        (lazyExceptionHandlers ?: ArrayList<ExceptionHandlerInfo>().also { lazyExceptionHandlers = it })
            .add(ExceptionHandlerInfo(kind, tryStart, tryEnd, handlerStart, handlerEnd, filterStart, catchType))
    }

    // internal for testing:
    internal val branchList: List<BranchInfo> get() = branches
    internal val labelList: List<Int> get() = labels
    internal val branchCount: Int get() = branches.size
    internal val exceptionHandlerCount: Int get() = lazyExceptionHandlers?.size ?: 0

    internal fun validateNotInSwitch() {
        check(remainingSwitchBranches <= 0) { "too few branches emitted for the switch instruction" }
    }

    internal fun switchBranchAdded() {
        check(remainingSwitchBranches != 0) { "too many branches emitted for the switch instruction" }
        remainingSwitchBranches--
    }

    internal fun copyCodeAndFixupBranches(srcBuilder: BlobBuilder, dstBuilder: BlobBuilder) {
        var branch = branches[0]
        var branchIndex = 0

        // offset within the source builder
        var srcOffset = 0

        // current offset within the current source blob
        var srcBlobOffset = 0

        for (srcBlob in srcBuilder.getBlobs()) {
            val buffer = srcBlob.buffer ?: continue

            while (true) {
                // copy bytes preceding the next branch, or till the end of the blob:
                val chunkSize = minOf(branch.operandOffset - srcOffset, srcBlob.length - srcBlobOffset)
                dstBuilder.writeBytes(buffer, srcBlobOffset, chunkSize)
                srcOffset += chunkSize
                srcBlobOffset += chunkSize

                // there is no branch left in the blob:
                if (srcBlobOffset == srcBlob.length) {
                    srcBlobOffset = 0
                    break
                }

                val operandSize = branch.operandSize
                val isShortInstruction = branch.isShortBranch

                val branchDistance = branch.getBranchDistance(labels)

                // write branch operand:
                if (isShortInstruction) {
                    dstBuilder.writeSByte(branchDistance.toByte())
                } else {
                    dstBuilder.writeInt32(branchDistance)
                }

                srcOffset += operandSize

                // next branch:
                branchIndex++
                branch = if (branchIndex == branches.size) {
                    // processed all branches: the rest of the IL stream is copied verbatim
                    BranchInfo.END
                } else {
                    branches[branchIndex]
                }

                // the branch starts at the very end and its operand is in the next blob:
                if (srcBlobOffset == srcBlob.length - 1) {
                    srcBlobOffset = operandSize
                    break
                }

                // skip fake branch operand:
                srcBlobOffset += operandSize
            }
        }
    }

    internal fun serializeExceptionTable(builder: BlobBuilder) {
        val handlers = lazyExceptionHandlers
        if (handlers.isNullOrEmpty()) {
            return
        }

        val regionEncoder = ExceptionRegionEncoder.serializeTableHeader(
            builder,
            handlers.size,
            hasSmallExceptionRegions(handlers),
        )

        for (handler in handlers) {
            // labels have been validated when added; they might not have been marked though
            val tryStart = getLabelOffsetChecked(handler.tryStart)
            val tryEnd = getLabelOffsetChecked(handler.tryEnd)
            val handlerStart = getLabelOffsetChecked(handler.handlerStart)
            val handlerEnd = getLabelOffsetChecked(handler.handlerEnd)

            check(tryStart <= tryEnd) { "invalid exception region bounds: $tryStart..$tryEnd" }
            check(handlerStart <= handlerEnd) { "invalid exception region bounds: $handlerStart..$handlerEnd" }

            val catchTokenOrOffset = when (handler.kind) {
                ExceptionRegionKind.CATCH -> MetadataTokens.getToken(handler.catchType)
                ExceptionRegionKind.FILTER -> getLabelOffsetChecked(handler.filterStart)
                else -> 0
            }

            regionEncoder.addUnchecked(
                handler.kind,
                tryStart,
                tryEnd - tryStart,
                handlerStart,
                handlerEnd - handlerStart,
                catchTokenOrOffset,
            )
        }
    }

    private fun hasSmallExceptionRegions(handlers: List<ExceptionHandlerInfo>): Boolean {
        if (!ExceptionRegionEncoder.isSmallRegionCount(handlers.size)) {
            return false
        }

        for (handler in handlers) {
            if (!ExceptionRegionEncoder.isSmallExceptionRegionFromBounds(
                    getLabelOffsetChecked(handler.tryStart),
                    getLabelOffsetChecked(handler.tryEnd),
                ) ||
                !ExceptionRegionEncoder.isSmallExceptionRegionFromBounds(
                    getLabelOffsetChecked(handler.handlerStart),
                    getLabelOffsetChecked(handler.handlerEnd),
                )
            ) {
                return false
            }
        }

        return true
    }
}
