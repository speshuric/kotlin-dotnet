// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (ReservedBlob.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

/**
 * Represents a handle and a corresponding blob on a metadata heap that was
 * reserved for future content update (upstream: public readonly struct).
 */
data class ReservedBlob<THandle>(
    val handle: THandle,
    val content: Blob,
) {
    /** Returns a [BlobWriter] to be used to update the content. */
    fun createWriter(): BlobWriter = BlobWriter(content)
}
