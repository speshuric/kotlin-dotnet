// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (IL/ExceptionRegionKind.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

enum class ExceptionRegionKind(val value: Int) {
    CATCH(0),
    FILTER(1),
    FINALLY(2),
    FAULT(4),
}
