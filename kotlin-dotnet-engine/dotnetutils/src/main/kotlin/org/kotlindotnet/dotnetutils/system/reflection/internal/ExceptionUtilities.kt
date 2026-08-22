// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Internal/Utilities/ExceptionUtilities.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.internal

internal object ExceptionUtilities {
    /**
     * Port of `UnexpectedValue`: an error for values that should never occur
     * (e.g. unhandled enum members).
     */
    fun unexpectedValue(value: Any?): IllegalStateException =
        IllegalStateException("Unexpected value: '$value' of type '${value?.let { it::class.qualifiedName }}'")
}
