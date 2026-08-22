// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/SerializedMetadataHeaps.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

internal class SerializedMetadata(
    val sizes: MetadataSizes,
    val stringHeap: BlobBuilder,
    /** Virtual writer index -> heap offset for the #String heap. */
    val stringMap: IntArray,
)
