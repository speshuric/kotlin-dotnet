// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Internal/Utilities/BitArithmetic.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.internal

internal object BitArithmetic {
    fun countBits(v: Int): Int = v.countOneBits()

    fun countBits(v: UInt): Int = v.countOneBits()

    fun countBits(v: ULong): Int = v.countOneBits()

    fun align(position: UInt, alignment: UInt): UInt {
        assert(alignment.countOneBits() == 1)

        val result = position and (alignment - 1u).inv()
        return if (result == position) result else result + alignment
    }

    fun align(position: Int, alignment: Int): Int {
        assert(position >= 0 && alignment > 0)
        assert(alignment.countOneBits() == 1)

        val result = position and (alignment - 1).inv()
        return if (result == position) result else result + alignment
    }
}
