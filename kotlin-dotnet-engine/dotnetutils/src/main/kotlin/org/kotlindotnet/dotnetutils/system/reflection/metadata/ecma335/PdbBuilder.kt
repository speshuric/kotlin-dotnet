// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/PdbBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: sourceLink blob and the deterministic-id path are cut;
// build() returns a ready PE image (Roslyn wraps the metadata blob
// externally; here the wrapper lives here so the internal standalone
// layout never leaks); entryPointToken is a constructor parameter.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.Characteristics
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.ManagedPEBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.PEHeaderBuilder

/**
 * Builder of standalone portable PDB image bytes ("PDB\u0000" metadata
 * root + #Pdb stream).
 *
 * @param metadata metadata containing only debug tables (Document,
 *   MethodDebugInformation, LocalScope, LocalVariable) and heaps.
 * @param id MVID of the owning assembly (16 bytes) — #Pdb stream Id.
 * @param entryPointToken EntryPointToken of the owning assembly.
 */
class PdbBuilder(
    private val metadata: MetadataBuilder,
    private val id: ByteArray,
    private val entryPointToken: Int = 0,
) {
    init {
        require(id.size == 16) { "id must be a 16-byte GUID" }
    }

    /** Собирает standalone PDB как PE-образ (единственная секция = метаданные). */
    fun build(): BlobBuilder {
        val root = MetadataRootBuilder(metadata, id, entryPointToken)

        val peBuilder = ManagedPEBuilder(
            header = PEHeaderBuilder(
                imageCharacteristics = Characteristics.EXECUTABLE_IMAGE.value or Characteristics.DLL.value,
            ),
            metadataRootBuilder = root,
            ilStream = BlobBuilder(),
        )
        val image = BlobBuilder()
        peBuilder.serialize(image)
        return image
    }
}
