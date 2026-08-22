// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Internal/Utilities/Hash.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.internal

internal object Hash {
    // C# arithmetic is unchecked; Kotlin Int multiplication wraps identically.
    fun combine(newKey: Int, currentKey: Int): Int = currentKey * 0xA5555529.toInt() + newKey

    fun combine(newKey: UInt, currentKey: Int): Int = currentKey * 0xA5555529.toInt() + newKey.toInt()

    fun combine(newKeyPart: Boolean, currentKey: Int): Int =
        combine(if (newKeyPart) 1 else 0, currentKey)

    /** The offset bias value used in the FNV-1a algorithm. */
    const val FNV_OFFSET_BIAS: Int = 2166136261.toInt()

    /** The generative factor used in the FNV-1a algorithm. */
    const val FNV_PRIME: Int = 16777619

    /**
     * Computes the FNV-1a hash of a sequence of bytes.
     * See http://en.wikipedia.org/wiki/Fowler%E2%80%94Noll%E2%80%94Vo_hash_function
     */
    fun getFNVHashCode(data: ByteArray, start: Int = 0, count: Int = data.size - start): Int {
        var hashCode = FNV_OFFSET_BIAS
        for (i in start until start + count) {
            hashCode = (hashCode xor (data[i].toInt() and 0xFF)) * FNV_PRIME
        }
        return hashCode
    }
}
