// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/CorFlags.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

/** COR20Flags. */
enum class CorFlags(val value: Int) {
    IL_ONLY(0x00000001),
    REQUIRES_32BIT(0x00000002),
    IL_LIBRARY(0x00000004),
    STRONG_NAME_SIGNED(0x00000008),
    NATIVE_ENTRY_POINT(0x00000010),
    TRACK_DEBUG_DATA(0x00010000),
    PREFERS_32BIT(0x00020000),
}
