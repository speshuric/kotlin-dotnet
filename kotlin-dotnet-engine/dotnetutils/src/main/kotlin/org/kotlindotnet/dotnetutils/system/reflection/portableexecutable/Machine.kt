// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/Machine.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

enum class Machine(val value: Int) {
    UNKNOWN(0x0000),
    I386(0x014c),
    WCE_MIPS_V2(0x0169),
    ALPHA(0x0184),
    SH3(0x01a2),
    SH3_DSP(0x01a3),
    SH3_E(0x01a4),
    SH4(0x01a6),
    SH5(0x01a8),
    ARM(0x01c0),
    THUMB(0x01c2),
    ARM_THUMB2(0x01c4),
    AM33(0x01d3),
    POWER_PC(0x01f0),
    POWER_PCFP(0x01f1),
    IA64(0x0200),
    MIPS16(0x0266),
    ALPHA64(0x0284),
    MIPS_FPU(0x0366),
    MIPS_FPU16(0x0466),
    TRICORE(0x0520),
    EBC(0x0ebc),
    AMD64(0x8664),
    M32_R(0x9041),
    ARM64(0xaa64),
    LOONG_ARCH32(0x6232),
    LOONG_ARCH64(0x6264),
    RISC_V32(0x5032),
    RISC_V64(0x5064),
    RISC_V128(0x5128);

    companion object {
        private val byValue: Map<Int, Machine> = entries.associateBy { it.value }

        fun fromRaw(raw: Int): Machine? = byValue[raw]
    }
}
