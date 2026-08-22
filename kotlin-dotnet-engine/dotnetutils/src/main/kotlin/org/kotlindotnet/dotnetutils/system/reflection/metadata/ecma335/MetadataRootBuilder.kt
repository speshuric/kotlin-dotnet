// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/MetadataRootBuilder.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: standalone-debug-metadata branch is cut (Portable PDB scope
// per ADR 0009); isStandaloneDebugMetadata is always false.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.internal.BlobUtilities
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

/**
 * Builder of a metadata root to be embedded in a Portable Executable image.
 *
 * Metadata root consists of a metadata header followed by metadata streams
 * (#~, #Strings, #US, #Guid and #Blob).
 */
class MetadataRootBuilder(
    private val tablesAndHeaps: MetadataBuilder,
    metadataVersion: String? = null,
    val suppressValidation: Boolean = false,
) {
    /** Metadata version string. */
    val metadataVersion: String

    private val serializedMetadata: SerializedMetadata

    /** Sizes of various metadata structures. */
    internal val sizes: MetadataSizes
        get() = serializedMetadata.sizes

    init {
        val version = metadataVersion ?: DEFAULT_METADATA_VERSION_STRING

        val metadataVersionByteCount = BlobUtilities.getUTF8ByteCount(version).first

        check(metadataVersionByteCount <= MetadataSizes.MAX_METADATA_VERSION_BYTE_COUNT) {
            "metadataVersion is too long"
        }

        this.metadataVersion = version
        serializedMetadata = tablesAndHeaps.getSerializedMetadata(
            MetadataBuilder.EMPTY_ROW_COUNTS,
            metadataVersionByteCount,
        )
    }

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
    }

    companion object {
        private const val DEFAULT_METADATA_VERSION_STRING = "v4.0.30319"
    }
}
