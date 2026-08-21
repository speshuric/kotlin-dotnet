// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/HeapIndex.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

enum class HeapIndex {
    USER_STRING,
    STRING,
    BLOB,
    GUID,
}

internal val HEAP_INDEX_COUNT: Int = HeapIndex.entries.size
