// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/BlobDictionary.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviation: the value is stored as (ByteArray content, BlobHandle) pair
// instead of a KeyValuePair over ImmutableArray<byte>.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.internal.Hash
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle

internal class BlobDictionary(capacity: Int = 0) {
    internal class Entry(val content: ByteArray, val handle: BlobHandle)

    // content hash -> entry; collisions resolved by probing with an LCG
    // (constants from https://github.com/imneme/pcg-c)
    private val dictionary = HashMap<Int, Entry>(capacity)

    val size: Int
        get() = dictionary.size

    fun entries(): Collection<Entry> = dictionary.values

    /**
     * Returns the existing [BlobHandle] for [key], or inserts ([content],
     * [value]) and returns [value] with `exists = false`.
     */
    fun getOrAdd(key: ByteArray, content: ByteArray, value: BlobHandle): Pair<BlobHandle, Boolean> {
        var dictionaryKey = Hash.getFNVHashCode(key)
        while (true) {
            val entry = dictionary[dictionaryKey]
            if (entry == null || entry.content.contentEquals(key)) {
                return if (entry != null) {
                    entry.handle to true
                } else {
                    dictionary[dictionaryKey] = Entry(content.copyOf(), value)
                    value to false
                }
            }
            dictionaryKey = getNextDictionaryKey(dictionaryKey)
        }
    }

    companion object {
        private fun getNextDictionaryKey(dictionaryKey: Int): Int =
            dictionaryKey * 0x2C9D0A95 + 0xAC5BE1E5.toInt()
    }
}
