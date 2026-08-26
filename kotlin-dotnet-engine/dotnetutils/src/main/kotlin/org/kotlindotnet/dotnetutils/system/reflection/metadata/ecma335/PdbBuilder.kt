// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/PdbBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: sourceLink blob and the deterministic-id path are cut;
// entryPointToken is a constructor parameter.
//
// Note: a portable PDB is a RAW metadata root (BSJB) without any PE
// wrapping - verified against csc output (PEReader rejects it as PE).


package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

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

    /** Собирает standalone PDB: сырой корень метаданных (без PE-обёртки). */
    fun build(): BlobBuilder {
        val root = MetadataRootBuilder(metadata, id, entryPointToken)
        val builder = BlobBuilder()
        root.serialize(builder, methodBodyStreamRva = 0, mappedFieldDataStreamRva = 0)
        check(builder.count == root.sizes.metadataSize) {
            "pdb size mismatch: written=${builder.count} expected=${root.sizes.metadataSize}"
        }
        return builder
    }
}
