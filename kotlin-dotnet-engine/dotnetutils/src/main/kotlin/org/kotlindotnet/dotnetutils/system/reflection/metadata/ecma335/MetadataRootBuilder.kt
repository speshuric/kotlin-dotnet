// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/MetadataRootBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: EnC branches are cut. Standalone (#Pdb) layout is reachable
// only via the internal constructor used by PdbBuilder (upstream keeps
// the same split: PdbBuilder owns the extra #Pdb stream).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.internal.BlobUtilities
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

/**
 * Builder of a metadata root to be embedded in a Portable Executable image.
 *
 * Metadata root consists of a metadata header followed by metadata streams
 * (#~, #Strings, #US, #Guid and #Blob).
 */
class MetadataRootBuilder private constructor(
    private val tablesAndHeaps: MetadataBuilder,
    metadataVersion: String?,
    val suppressValidation: Boolean,
    /** Non-null → standalone portable PDB ("PDB\u0000" + #Pdb stream). */
    private val standalonePdbId: ByteArray?,
    private val standalonePdbEntryPointToken: Int,
) {
    /** Metadata version string. */
    val metadataVersion: String

    private val serializedMetadata: SerializedMetadata

    /** Sizes of various metadata structures. */
    internal val sizes: MetadataSizes
        get() = serializedMetadata.sizes

    init {
        val isStandalonePdb = standalonePdbId != null
        if (isStandalonePdb) require(standalonePdbId.size == 16) { "id must be a 16-byte GUID" }
        val version = metadataVersion ?: if (isStandalonePdb) "PDB v1.0" else DEFAULT_METADATA_VERSION_STRING

        val metadataVersionByteCount = BlobUtilities.getUTF8ByteCount(version).first

        check(metadataVersionByteCount <= MetadataSizes.MAX_METADATA_VERSION_BYTE_COUNT) {
            "metadataVersion is too long"
        }

        this.metadataVersion = version
        serializedMetadata = tablesAndHeaps.getSerializedMetadata(
            MetadataBuilder.EMPTY_ROW_COUNTS,
            metadataVersionByteCount,
            isStandalonePdb,
        )
    }

    /** Апстримный публичный ctor (regular assembly). */
    constructor(
        tablesAndHeaps: MetadataBuilder,
        metadataVersion: String? = null,
        suppressValidation: Boolean = false,
    ) : this(tablesAndHeaps, metadataVersion, suppressValidation, null, 0)

    /** Для [PdbBuilder]: standalone portable PDB. */
    internal constructor(
        tablesAndHeaps: MetadataBuilder,
        pdbId: ByteArray,
        entryPointToken: Int,
    ) : this(tablesAndHeaps, null, false, pdbId, entryPointToken)

    /**
     * Serializes metadata root content into the given [BlobBuilder].
     *
     * @param methodBodyStreamRva The RVA of the start of the method body
     *   stream (used for MethodDef RVA fixup).
     * @param mappedFieldDataStreamRva The RVA of the start of the field init
     *   data stream (used for FieldRVA fixup).
     */
    fun serialize(
        builder: BlobBuilder,
        methodBodyStreamRva: Int,
        mappedFieldDataStreamRva: Int,
    ) {
        require(methodBodyStreamRva >= 0) { "methodBodyStreamRva out of range" }
        require(mappedFieldDataStreamRva >= 0) { "mappedFieldDataStreamRva out of range" }

        if (!suppressValidation) {
            tablesAndHeaps.validateOrder()
        }

        // header
        tablesAndHeaps.serializeMetadataHeader(builder, metadataVersion, serializedMetadata.sizes)

        // #~ stream
        tablesAndHeaps.serializeMetadataTables(
            builder,
            serializedMetadata.sizes,
            serializedMetadata.stringMap,
            methodBodyStreamRva,
            mappedFieldDataStreamRva,
        )

        // #Strings, #US, #Guid, #Blob streams
        tablesAndHeaps.writeHeapsTo(builder, serializedMetadata.stringHeap)

        standalonePdbId?.let { id ->
            // #Pdf stream per Portable PDB spec:
            // Id (MVID, 16 bytes), EntryPtTok (4), ReferencedTypeSystemTokens (64x00).
            builder.writeBytes(id)
            builder.writeInt32(standalonePdbEntryPointToken)
            repeat(MetadataSizes.PDB_STREAM_SIZE - 20) { builder.writeByte(0.toByte()) }
        }
    }

    companion object {
        private const val DEFAULT_METADATA_VERSION_STRING = "v4.0.30319"
    }
}
