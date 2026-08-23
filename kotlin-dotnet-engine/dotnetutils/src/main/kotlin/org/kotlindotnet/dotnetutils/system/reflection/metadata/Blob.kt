// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Blob.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: ArraySegment<byte> GetBytes() is replaced by copy-based
// [toArray]/[copyTo] accessors (the module avoids exposing raw buffers).

package org.kotlindotnet.dotnetutils.system.reflection.metadata

/** Upstream: public readonly struct. Equality — по полям (как struct). */
data class Blob internal constructor(
    internal val buffer: ByteArray?,
    internal val start: Int,
    val length: Int,
) {
    val isDefault: Boolean
        get() = buffer == null

    /** Returns a copy of the blob content. */
    fun toArray(): ByteArray {
        checkNotNull(buffer) { "default blob" }
        return buffer.copyOfRange(start, start + length)
    }

    /** Copies the blob content into [destination] at [destinationOffset]. */
    fun copyInto(destination: ByteArray, destinationOffset: Int = 0) {
        buffer?.copyInto(destination, destinationOffset, start, start + length)
    }

    companion object {
        internal val DEFAULT = Blob(null, 0, 0)
    }
}
