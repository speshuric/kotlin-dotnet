// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (IL/ILOpCodeExtensions.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

/** Returns true if the specified op-code is a branch to a label. */
fun ILOpCode.isBranch(): Boolean =
    when (this) {
        ILOpCode.BR,
        ILOpCode.BR_S,
        ILOpCode.BRTRUE,
        ILOpCode.BRTRUE_S,
        ILOpCode.BRFALSE,
        ILOpCode.BRFALSE_S,
        ILOpCode.BEQ,
        ILOpCode.BEQ_S,
        ILOpCode.BNE_UN,
        ILOpCode.BNE_UN_S,
        ILOpCode.BGE,
        ILOpCode.BGE_S,
        ILOpCode.BGE_UN,
        ILOpCode.BGE_UN_S,
        ILOpCode.BGT,
        ILOpCode.BGT_S,
        ILOpCode.BGT_UN,
        ILOpCode.BGT_UN_S,
        ILOpCode.BLE,
        ILOpCode.BLE_S,
        ILOpCode.BLE_UN,
        ILOpCode.BLE_UN_S,
        ILOpCode.BLT,
        ILOpCode.BLT_S,
        ILOpCode.BLT_UN,
        ILOpCode.BLT_UN_S,
        ILOpCode.LEAVE,
        ILOpCode.LEAVE_S,
        -> true
        else -> false
    }

/**
 * Calculates the size of the specified branch instruction operand:
 * 1 for a short branch, 4 for a long branch.
 *
 * @throws IllegalArgumentException when [this] is not a branch op-code.
 */
fun ILOpCode.getBranchOperandSize(): Int =
    when (this) {
        ILOpCode.BR_S,
        ILOpCode.BRFALSE_S,
        ILOpCode.BRTRUE_S,
        ILOpCode.BEQ_S,
        ILOpCode.BGE_S,
        ILOpCode.BGT_S,
        ILOpCode.BLE_S,
        ILOpCode.BLT_S,
        ILOpCode.BNE_UN_S,
        ILOpCode.BGE_UN_S,
        ILOpCode.BGT_UN_S,
        ILOpCode.BLE_UN_S,
        ILOpCode.BLT_UN_S,
        ILOpCode.LEAVE_S,
        -> 1

        ILOpCode.BR,
        ILOpCode.BRFALSE,
        ILOpCode.BRTRUE,
        ILOpCode.BEQ,
        ILOpCode.BGE,
        ILOpCode.BGT,
        ILOpCode.BLE,
        ILOpCode.BLT,
        ILOpCode.BNE_UN,
        ILOpCode.BGE_UN,
        ILOpCode.BGT_UN,
        ILOpCode.BLE_UN,
        ILOpCode.BLT_UN,
        ILOpCode.LEAVE,
        -> 4

        else -> throw IllegalArgumentException("unexpected op-code: $this")
    }

/**
 * Gets the short form of the specified branch op-code.
 *
 * @throws IllegalArgumentException when [this] is not a branch op-code.
 */
fun ILOpCode.getShortBranch(): ILOpCode =
    when (this) {
        ILOpCode.BR -> ILOpCode.BR_S
        ILOpCode.BRFALSE -> ILOpCode.BRFALSE_S
        ILOpCode.BRTRUE -> ILOpCode.BRTRUE_S
        ILOpCode.BEQ -> ILOpCode.BEQ_S
        ILOpCode.BGE -> ILOpCode.BGE_S
        ILOpCode.BGT -> ILOpCode.BGT_S
        ILOpCode.BLE -> ILOpCode.BLE_S
        ILOpCode.BLT -> ILOpCode.BLT_S
        ILOpCode.BNE_UN -> ILOpCode.BNE_UN_S
        ILOpCode.BGE_UN -> ILOpCode.BGE_UN_S
        ILOpCode.BGT_UN -> ILOpCode.BGT_UN_S
        ILOpCode.BLE_UN -> ILOpCode.BLE_UN_S
        ILOpCode.BLT_UN -> ILOpCode.BLT_UN_S
        ILOpCode.LEAVE -> ILOpCode.LEAVE_S

        ILOpCode.BR_S,
        ILOpCode.BRFALSE_S,
        ILOpCode.BRTRUE_S,
        ILOpCode.BEQ_S,
        ILOpCode.BGE_S,
        ILOpCode.BGT_S,
        ILOpCode.BLE_S,
        ILOpCode.BLT_S,
        ILOpCode.BNE_UN_S,
        ILOpCode.BGE_UN_S,
        ILOpCode.BGT_UN_S,
        ILOpCode.BLE_UN_S,
        ILOpCode.BLT_UN_S,
        ILOpCode.LEAVE_S,
        -> this

        else -> throw IllegalArgumentException("unexpected op-code: $this")
    }

/**
 * Gets the long form of the specified branch op-code.
 *
 * @throws IllegalArgumentException when [this] is not a branch op-code.
 */
fun ILOpCode.getLongBranch(): ILOpCode =
    when (this) {
        ILOpCode.BR_S -> ILOpCode.BR
        ILOpCode.BRFALSE_S -> ILOpCode.BRFALSE
        ILOpCode.BRTRUE_S -> ILOpCode.BRTRUE
        ILOpCode.BEQ_S -> ILOpCode.BEQ
        ILOpCode.BGE_S -> ILOpCode.BGE
        ILOpCode.BGT_S -> ILOpCode.BGT
        ILOpCode.BLE_S -> ILOpCode.BLE
        ILOpCode.BLT_S -> ILOpCode.BLT
        ILOpCode.BNE_UN_S -> ILOpCode.BNE_UN
        ILOpCode.BGE_UN_S -> ILOpCode.BGE_UN
        ILOpCode.BGT_UN_S -> ILOpCode.BGT_UN
        ILOpCode.BLE_UN_S -> ILOpCode.BLE_UN
        ILOpCode.BLT_UN_S -> ILOpCode.BLT_UN
        ILOpCode.LEAVE_S -> ILOpCode.LEAVE

        ILOpCode.BR,
        ILOpCode.BRFALSE,
        ILOpCode.BRTRUE,
        ILOpCode.BEQ,
        ILOpCode.BGE,
        ILOpCode.BGT,
        ILOpCode.BLE,
        ILOpCode.BLT,
        ILOpCode.BNE_UN,
        ILOpCode.BGE_UN,
        ILOpCode.BGT_UN,
        ILOpCode.BLE_UN,
        ILOpCode.BLT_UN,
        ILOpCode.LEAVE,
        -> this

        else -> throw IllegalArgumentException("unexpected op-code: $this")
    }
