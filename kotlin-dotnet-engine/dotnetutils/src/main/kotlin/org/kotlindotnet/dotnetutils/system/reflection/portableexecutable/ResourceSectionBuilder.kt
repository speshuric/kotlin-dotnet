// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (PortableExecutable/ResourceSectionBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - C# `protected internal` maps to Kotlin `internal abstract` so the PE
//    builder can invoke it (module-wide visibility).

package org.kotlindotnet.dotnetutils.system.reflection.portableexecutable

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

/**
 * Base class for PE resource section builder. Implement to provide serialization logic for native resources.
 */
abstract class ResourceSectionBuilder {
    internal abstract fun serialize(builder: BlobBuilder, location: SectionLocation)
}
