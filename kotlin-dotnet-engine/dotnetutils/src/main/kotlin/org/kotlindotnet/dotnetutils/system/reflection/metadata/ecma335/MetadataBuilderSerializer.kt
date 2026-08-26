// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata
//   (Ecma335/MetadataBuilder.Tables.cs — serialization half).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations: debug-table serializers are cut (Portable PDB scope per ADR
// 0009). The EnC-delta branch in SerializeTablesHeader is removed
// (IsCompressed is always true in this port).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder

internal fun MetadataBuilder.serializeMetadataTables(
    writer: BlobBuilder,
    metadataSizes: MetadataSizes,
    stringMap: IntArray,
    methodBodyStreamRva: Int,
    mappedFieldDataStreamRva: Int,
) {
    val startPosition = writer.count

    serializeTablesHeader(writer, metadataSizes)

    if (metadataSizes.isPresent(TableIndex.MODULE)) serializeModuleTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.TYPE_REF)) serializeTypeRefTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.TYPE_DEF)) serializeTypeDefTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.FIELD)) serializeFieldTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.METHOD_DEF)) serializeMethodDefTable(writer, stringMap, metadataSizes, methodBodyStreamRva)
    if (metadataSizes.isPresent(TableIndex.PARAM)) serializeParamTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.INTERFACE_IMPL)) serializeInterfaceImplTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.MEMBER_REF)) serializeMemberRefTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.CONSTANT)) serializeConstantTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.CUSTOM_ATTRIBUTE)) serializeCustomAttributeTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.FIELD_MARSHAL)) serializeFieldMarshalTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.DECL_SECURITY)) serializeDeclSecurityTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.CLASS_LAYOUT)) serializeClassLayoutTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.FIELD_LAYOUT)) serializeFieldLayoutTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.STAND_ALONE_SIG)) serializeStandAloneSigTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.EVENT_MAP)) serializeEventMapTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.EVENT)) serializeEventTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.PROPERTY_MAP)) serializePropertyMapTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.PROPERTY)) serializePropertyTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.METHOD_SEMANTICS)) serializeMethodSemanticsTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.METHOD_IMPL)) serializeMethodImplTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.MODULE_REF)) serializeModuleRefTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.TYPE_SPEC)) serializeTypeSpecTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.IMPL_MAP)) serializeImplMapTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.FIELD_RVA)) serializeFieldRvaTable(writer, metadataSizes, mappedFieldDataStreamRva)
    if (metadataSizes.isPresent(TableIndex.ASSEMBLY)) serializeAssemblyTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.ASSEMBLY_REF)) serializeAssemblyRefTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.FILE)) serializeFileTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.EXPORTED_TYPE)) serializeExportedTypeTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.MANIFEST_RESOURCE)) serializeManifestResourceTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.DOCUMENT)) serializeDocumentTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.METHOD_DEBUG_INFORMATION)) serializeMethodDebugInformationTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.LOCAL_SCOPE)) serializeLocalScopeTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.LOCAL_VARIABLE)) serializeLocalVariableTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.NESTED_CLASS)) serializeNestedClassTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.GENERIC_PARAM)) serializeGenericParamTable(writer, stringMap, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.METHOD_SPEC)) serializeMethodSpecTable(writer, metadataSizes)
    if (metadataSizes.isPresent(TableIndex.GENERIC_PARAM_CONSTRAINT)) serializeGenericParamConstraintTable(writer, metadataSizes)

    writer.writeByte(0.toByte())
    writer.align(4)

    if (metadataSizes.metadataTableStreamSize != writer.count - startPosition)
        throw IllegalStateException(
            "tbl written=${writer.count - startPosition} calc=${metadataSizes.metadataTableStreamSize} " +
                "present=${metadataSizes.presentTablesMask.toString(16)}",
        )
}

private fun MetadataBuilder.serializeTablesHeader(writer: BlobBuilder, metadataSizes: MetadataSizes) {
    val startPosition = writer.count

    var heapSizes = 0
    if (!metadataSizes.stringReferenceIsSmall) heapSizes = heapSizes or HeapSizeFlag.STRING_HEAP_LARGE
    if (!metadataSizes.guidReferenceIsSmall) heapSizes = heapSizes or HeapSizeFlag.GUID_HEAP_LARGE
    if (!metadataSizes.blobReferenceIsSmall) heapSizes = heapSizes or HeapSizeFlag.BLOB_HEAP_LARGE

    // Standalone PDB: declare sortedness only for debug tables actually
    // present (csc reference marks LocalScope and CustomDebugInformation).
    val sortedTables =
        if (metadataSizes.isStandalonePdb) {
            listOf(TableIndex.LOCAL_SCOPE, TableIndex.CUSTOM_DEBUG_INFORMATION)
                .filter { metadataSizes.isPresent(it) }
                .fold(0uL) { acc, t -> acc or (1uL shl t.value) }
        } else {
            MetadataSizes.SORTED_TYPE_SYSTEM_TABLES
        }

    writer.writeUInt32(0u) // reserved
    writer.writeByte(MetadataBuilder.METADATA_FORMAT_MAJOR_VERSION)
    writer.writeByte(MetadataBuilder.METADATA_FORMAT_MINOR_VERSION)
    writer.writeByte(heapSizes.toByte())
    writer.writeByte(1.toByte()) // reserved
    writer.writeUInt64(metadataSizes.presentTablesMask)
    writer.writeUInt64(sortedTables)
    MetadataWriterUtilities.serializeRowCounts(writer, metadataSizes.rowCounts)

    assert(metadataSizes.calculateTableStreamHeaderSize() == writer.count - startPosition)
}

// --- per-table serializers ---

internal fun MetadataBuilder.serializeModuleTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    moduleRow?.let { row ->
        writer.writeUInt16(row.generation.toUShort())
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(row.moduleVersionId), sizes.guidReferenceIsSmall)
        writer.writeReference(serializeHandle(row.encId), sizes.guidReferenceIsSmall)
        writer.writeReference(serializeHandle(row.encBaseId), sizes.guidReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeTypeRefTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in typeRefTable) {
        writer.writeReference(row.resolutionScope, sizes.resolutionScopeCodedIndexIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.namespace), sizes.stringReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeTypeDefTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in typeDefTable) {
        writer.writeUInt32(row.flags)
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.namespace), sizes.stringReferenceIsSmall)
        writer.writeReference(row.extends, sizes.typeDefOrRefCodedIndexIsSmall)
        writer.writeReference(row.fieldList, sizes.fieldDefReferenceIsSmall)
        writer.writeReference(row.methodList, sizes.methodDefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeFieldTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in fieldTable) {
        writer.writeUInt16(row.flags.toUShort())
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(row.signature), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeMethodDefTable(
    writer: BlobBuilder,
    stringMap: IntArray,
    sizes: MetadataSizes,
    methodBodyStreamRva: Int,
) {
    for (row in methodDefTable) {
        if (row.bodyOffset == -1) {
            writer.writeUInt32(0u)
        } else {
            writer.writeInt32(methodBodyStreamRva + row.bodyOffset)
        }
        writer.writeUInt16(row.implFlags.toUShort())
        writer.writeUInt16(row.flags.toUShort())
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(row.signature), sizes.blobReferenceIsSmall)
        writer.writeReference(row.paramList, sizes.parameterReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeParamTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in paramTable) {
        writer.writeUInt16(row.flags.toUShort())
        writer.writeUInt16(row.sequence.toUShort())
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeInterfaceImplTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in interfaceImplTable) {
        writer.writeReference(row.clazz, sizes.typeDefReferenceIsSmall)
        writer.writeReference(row.interface_, sizes.typeDefOrRefCodedIndexIsSmall)
    }
}

internal fun MetadataBuilder.serializeMemberRefTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in memberRefTable) {
        writer.writeReference(row.clazz, sizes.memberRefParentCodedIndexIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(row.signature), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeConstantTable(writer: BlobBuilder, sizes: MetadataSizes) {
    val ordered = if (constantTableNeedsSorting) {
        constantTable.sortedBy { it.parent }
    } else {
        constantTable
    }

    for (row in ordered) {
        writer.writeByte(row.type)
        writer.writeByte(0.toByte())
        writer.writeReference(row.parent, sizes.hasConstantCodedIndexIsSmall)
        writer.writeReference(serializeHandle(row.value), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeCustomAttributeTable(writer: BlobBuilder, sizes: MetadataSizes) {
    val ordered = if (customAttributeTableNeedsSorting) {
        customAttributeTable.sortedBy { it.parent }
    } else {
        customAttributeTable
    }

    for (row in ordered) {
        writer.writeReference(row.parent, sizes.hasCustomAttributeCodedIndexIsSmall)
        writer.writeReference(row.type, sizes.customAttributeTypeCodedIndexIsSmall)
        writer.writeReference(serializeHandle(row.value), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeFieldMarshalTable(writer: BlobBuilder, sizes: MetadataSizes) {
    val ordered = if (fieldMarshalTableNeedsSorting) {
        fieldMarshalTable.sortedBy { it.parent }
    } else {
        fieldMarshalTable
    }

    for (row in ordered) {
        writer.writeReference(row.parent, sizes.hasFieldMarshalCodedIndexIsSmall)
        writer.writeReference(serializeHandle(row.nativeType), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeDeclSecurityTable(writer: BlobBuilder, sizes: MetadataSizes) {
    val ordered = if (declSecurityTableNeedsSorting) {
        declSecurityTable.sortedBy { it.parent }
    } else {
        declSecurityTable
    }

    for (row in ordered) {
        writer.writeUInt16(row.action.toUShort())
        writer.writeReference(row.parent, sizes.declSecurityCodedIndexIsSmall)
        writer.writeReference(serializeHandle(row.permissionSet), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeClassLayoutTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in classLayoutTable) {
        writer.writeUInt16(row.packingSize.toUShort())
        writer.writeUInt32(row.classSize)
        writer.writeReference(row.parent, sizes.typeDefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeFieldLayoutTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in fieldLayoutTable) {
        writer.writeInt32(row.offset)
        writer.writeReference(row.field, sizes.fieldDefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeStandAloneSigTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in standAloneSigTable) {
        writer.writeReference(serializeHandle(row.signature), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeEventMapTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in eventMapTable) {
        writer.writeReference(row.parent, sizes.typeDefReferenceIsSmall)
        writer.writeReference(row.eventList, sizes.eventDefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeEventTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in eventTable) {
        writer.writeUInt16(row.eventFlags.toUShort())
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(row.eventType, sizes.typeDefOrRefCodedIndexIsSmall)
    }
}

internal fun MetadataBuilder.serializePropertyMapTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in propertyMapTable) {
        writer.writeReference(row.parent, sizes.typeDefReferenceIsSmall)
        writer.writeReference(row.propertyList, sizes.propertyDefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializePropertyTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in propertyTable) {
        writer.writeUInt16(row.propFlags.toUShort())
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(row.type), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeMethodSemanticsTable(writer: BlobBuilder, sizes: MetadataSizes) {
    val ordered = if (methodSemanticsTableNeedsSorting) {
        methodSemanticsTable.sortedBy { it.association }
    } else {
        methodSemanticsTable
    }

    for (row in ordered) {
        writer.writeUInt16(row.semantic.toUShort())
        writer.writeReference(row.method, sizes.methodDefReferenceIsSmall)
        writer.writeReference(row.association, sizes.hasSemanticsCodedIndexIsSmall)
    }
}

internal fun MetadataBuilder.serializeMethodImplTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in methodImplTable) {
        writer.writeReference(row.clazz, sizes.typeDefReferenceIsSmall)
        writer.writeReference(row.methodBody, sizes.methodDefOrRefCodedIndexIsSmall)
        writer.writeReference(row.methodDecl, sizes.methodDefOrRefCodedIndexIsSmall)
    }
}

internal fun MetadataBuilder.serializeModuleRefTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in moduleRefTable) {
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeTypeSpecTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in typeSpecTable) {
        writer.writeReference(serializeHandle(row.signature), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeImplMapTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in implMapTable) {
        writer.writeUInt16(row.mappingFlags.toUShort())
        writer.writeReference(row.memberForwarded, sizes.memberForwardedCodedIndexIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.importName), sizes.stringReferenceIsSmall)
        writer.writeReference(row.importScope, sizes.moduleRefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeFieldRvaTable(writer: BlobBuilder, sizes: MetadataSizes, mappedFieldDataStreamRva: Int) {
    for (row in fieldRvaTable) {
        writer.writeInt32(mappedFieldDataStreamRva + row.offset)
        writer.writeReference(row.field, sizes.fieldDefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeAssemblyTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    assemblyRow?.let { row ->
        writer.writeUInt32(row.hashAlgorithm)
        writer.writeUInt16(row.version.major.toUShort())
        writer.writeUInt16(row.version.minor.toUShort())
        writer.writeUInt16(row.version.build.toUShort())
        writer.writeUInt16(row.version.revision.toUShort())
        writer.writeUInt32(row.flags.toUInt())
        writer.writeReference(serializeHandle(row.assemblyKey), sizes.blobReferenceIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.assemblyName), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.assemblyCulture), sizes.stringReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeAssemblyRefTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in assemblyRefTable) {
        writer.writeUInt16(row.version.major.toUShort())
        writer.writeUInt16(row.version.minor.toUShort())
        writer.writeUInt16(row.version.build.toUShort())
        writer.writeUInt16(row.version.revision.toUShort())
        writer.writeUInt32(row.flags)
        writer.writeReference(serializeHandle(row.publicKeyToken), sizes.blobReferenceIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.culture), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(row.hashValue), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeFileTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in fileTable) {
        writer.writeUInt32(row.flags)
        writer.writeReference(serializeHandle(stringMap, row.fileName), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(row.hashValue), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeExportedTypeTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in exportedTypeTable) {
        writer.writeUInt32(row.flags)
        writer.writeInt32(row.typeDefId)
        writer.writeReference(serializeHandle(stringMap, row.typeName), sizes.stringReferenceIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.typeNamespace), sizes.stringReferenceIsSmall)
        writer.writeReference(row.implementation, sizes.implementationCodedIndexIsSmall)
    }
}

internal fun MetadataBuilder.serializeManifestResourceTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in manifestResourceTable) {
        writer.writeUInt32(row.offset)
        writer.writeUInt32(row.flags)
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
        writer.writeReference(row.implementation, sizes.implementationCodedIndexIsSmall)
    }
}

internal fun MetadataBuilder.serializeNestedClassTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in nestedClassTable) {
        writer.writeReference(row.nestedClass, sizes.typeDefReferenceIsSmall)
        writer.writeReference(row.enclosingClass, sizes.typeDefReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeGenericParamTable(writer: BlobBuilder, stringMap: IntArray, sizes: MetadataSizes) {
    for (row in genericParamTable) {
        writer.writeUInt16(row.number.toUShort())
        writer.writeUInt16(row.flags.toUShort())
        writer.writeReference(row.owner, sizes.typeOrMethodDefCodedIndexIsSmall)
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeMethodSpecTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in methodSpecTable) {
        writer.writeReference(row.method, sizes.methodDefOrRefCodedIndexIsSmall)
        writer.writeReference(serializeHandle(row.instantiation), sizes.blobReferenceIsSmall)
    }
}

internal fun MetadataBuilder.serializeGenericParamConstraintTable(writer: BlobBuilder, sizes: MetadataSizes) {
    for (row in genericParamConstraintTable) {
        writer.writeReference(row.owner, sizes.genericParamReferenceIsSmall)
        writer.writeReference(row.constraint, sizes.typeDefOrRefCodedIndexIsSmall)
    }
}
private fun MetadataBuilder.serializeDocumentTable(
    writer: BlobBuilder,
    stringMap: IntArray,
    sizes: MetadataSizes,
) {
    for (row in documentTable) {
        writer.writeReference(serializeHandle(row.name), sizes.blobReferenceIsSmall)
        writer.writeReference(serializeHandle(row.hashAlgorithm), sizes.guidReferenceIsSmall)
        writer.writeReference(serializeHandle(row.hash), sizes.blobReferenceIsSmall)
        writer.writeReference(serializeHandle(row.language), sizes.guidReferenceIsSmall)
    }
}

private fun MetadataBuilder.serializeMethodDebugInformationTable(
    writer: BlobBuilder,
    sizes: MetadataSizes,
) {
    for (row in methodDebugInformationTable) {
        writer.writeReference(row.document, sizes.documentReferenceIsSmall)
        writer.writeReference(serializeHandle(row.sequencePoints), sizes.blobReferenceIsSmall)
    }
}

private fun MetadataBuilder.serializeLocalScopeTable(
    writer: BlobBuilder,
    sizes: MetadataSizes,
) {
    for (row in localScopeTable) {
        writer.writeReference(row.method, sizes.methodDefReferenceIsSmall)
        writer.writeReference(row.importScope, sizes.importScopeReferenceIsSmall)
        writer.writeReference(row.variableList, sizes.localVariableReferenceIsSmall)
        writer.writeReference(row.constantList, sizes.localConstantReferenceIsSmall)
        writer.writeUInt32(row.startOffset)
        writer.writeUInt32(row.length)
    }
}

private fun MetadataBuilder.serializeLocalVariableTable(
    writer: BlobBuilder,
    stringMap: IntArray,
    sizes: MetadataSizes,
) {
    for (row in localVariableTable) {
        writer.writeUInt16(row.attributes.toUShort())
        writer.writeUInt16(row.index.toUShort())
        writer.writeReference(serializeHandle(stringMap, row.name), sizes.stringReferenceIsSmall)
    }
}

