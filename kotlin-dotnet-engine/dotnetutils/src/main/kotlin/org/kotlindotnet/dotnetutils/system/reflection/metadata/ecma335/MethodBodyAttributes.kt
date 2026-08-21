// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/Encoding/MethodBodyAttributes.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

/** Method body attributes. */
enum class MethodBodyAttributes(val value: Int) {
    /** No local memory initialization is performed. */
    NONE(0),

    /**
     * Zero-initialize any locals the method defines and dynamically allocated local memory.
     */
    INIT_LOCALS(1),
}
