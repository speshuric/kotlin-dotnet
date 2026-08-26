// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/MetadataBuilder.Tables.cs — building half).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - Add* methods take Int flags instead of BCL enum types;
//  - System.Version is replaced by [AssemblyVersion];
//  - debug-table Add* methods are cut (Portable PDB scope per ADR 0009).
//  - Type-system Add* are split into separate file from serialization
//    extensions to keep files manageable (Kotlin has no partial classes).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyFileHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.DocumentHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.LocalScopeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.LocalVariableHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ConstantHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.CustomAttributeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.DeclarativeSecurityAttributeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EventDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ExportedTypeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GenericParameterConstraintHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GenericParameterHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.InterfaceImplementationHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ManifestResourceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MemberReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodImplementationHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodSpecificationHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ModuleReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ParameterHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.PropertyDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeSpecificationHandle

// ---------------------------------------------------------------------------
// Capacity
// ---------------------------------------------------------------------------

fun MetadataBuilder.setTableCapacity(table: TableIndex, rowCount: Int) {
    require(rowCount >= 0) { "rowCount out of range" }
    when (table) {
        TableIndex.MODULE, TableIndex.ASSEMBLY -> { } // max row count is 1
        TableIndex.TYPE_REF -> typeRefTable.ensureCapacity(rowCount)
        TableIndex.TYPE_DEF -> typeDefTable.ensureCapacity(rowCount)
        TableIndex.FIELD -> fieldTable.ensureCapacity(rowCount)
        TableIndex.METHOD_DEF -> methodDefTable.ensureCapacity(rowCount)
        TableIndex.PARAM -> paramTable.ensureCapacity(rowCount)
        TableIndex.INTERFACE_IMPL -> interfaceImplTable.ensureCapacity(rowCount)
        TableIndex.MEMBER_REF -> memberRefTable.ensureCapacity(rowCount)
        TableIndex.CONSTANT -> constantTable.ensureCapacity(rowCount)
        TableIndex.CUSTOM_ATTRIBUTE -> customAttributeTable.ensureCapacity(rowCount)
        TableIndex.FIELD_MARSHAL -> fieldMarshalTable.ensureCapacity(rowCount)
        TableIndex.DECL_SECURITY -> declSecurityTable.ensureCapacity(rowCount)
        TableIndex.CLASS_LAYOUT -> classLayoutTable.ensureCapacity(rowCount)
        TableIndex.FIELD_LAYOUT -> fieldLayoutTable.ensureCapacity(rowCount)
        TableIndex.STAND_ALONE_SIG -> standAloneSigTable.ensureCapacity(rowCount)
        TableIndex.EVENT_MAP -> eventMapTable.ensureCapacity(rowCount)
        TableIndex.EVENT -> eventTable.ensureCapacity(rowCount)
        TableIndex.PROPERTY_MAP -> propertyMapTable.ensureCapacity(rowCount)
        TableIndex.PROPERTY -> propertyTable.ensureCapacity(rowCount)
        TableIndex.METHOD_SEMANTICS -> methodSemanticsTable.ensureCapacity(rowCount)
        TableIndex.METHOD_IMPL -> methodImplTable.ensureCapacity(rowCount)
        TableIndex.MODULE_REF -> moduleRefTable.ensureCapacity(rowCount)
        TableIndex.TYPE_SPEC -> typeSpecTable.ensureCapacity(rowCount)
        TableIndex.IMPL_MAP -> implMapTable.ensureCapacity(rowCount)
        TableIndex.FIELD_RVA -> fieldRvaTable.ensureCapacity(rowCount)
        TableIndex.ENC_LOG, TableIndex.ENC_MAP -> { }
        TableIndex.ASSEMBLY_REF -> assemblyRefTable.ensureCapacity(rowCount)
        TableIndex.FILE -> fileTable.ensureCapacity(rowCount)
        TableIndex.EXPORTED_TYPE -> exportedTypeTable.ensureCapacity(rowCount)
        TableIndex.MANIFEST_RESOURCE -> manifestResourceTable.ensureCapacity(rowCount)
        TableIndex.NESTED_CLASS -> nestedClassTable.ensureCapacity(rowCount)
        TableIndex.GENERIC_PARAM -> genericParamTable.ensureCapacity(rowCount)
        TableIndex.METHOD_SPEC -> methodSpecTable.ensureCapacity(rowCount)
        TableIndex.GENERIC_PARAM_CONSTRAINT -> genericParamConstraintTable.ensureCapacity(rowCount)
        // tables not serialized:
        TableIndex.ASSEMBLY_OS, TableIndex.ASSEMBLY_PROCESSOR,
        TableIndex.ASSEMBLY_REF_OS, TableIndex.ASSEMBLY_REF_PROCESSOR,
        TableIndex.EVENT_PTR, TableIndex.FIELD_PTR,
        TableIndex.METHOD_PTR, TableIndex.PARAM_PTR, TableIndex.PROPERTY_PTR,
        -> { }
        // debug tables (cut):
        TableIndex.DOCUMENT, TableIndex.METHOD_DEBUG_INFORMATION,
        TableIndex.LOCAL_SCOPE, TableIndex.LOCAL_VARIABLE,
        TableIndex.LOCAL_CONSTANT, TableIndex.IMPORT_SCOPE,
        TableIndex.STATE_MACHINE_METHOD, TableIndex.CUSTOM_DEBUG_INFORMATION,
        -> { }
    }
}

private fun <T> MutableList<T>.ensureCapacity(capacity: Int) {
    if (capacity > this.size) {
        this.ensureCapacity(capacity)
    }
}

// ---------------------------------------------------------------------------
// Row counts
// ---------------------------------------------------------------------------

fun MetadataBuilder.getRowCount(table: TableIndex): Int = when (table) {
    TableIndex.ASSEMBLY -> if (assemblyRow != null) 1 else 0
    TableIndex.ASSEMBLY_REF -> assemblyRefTable.size
    TableIndex.CLASS_LAYOUT -> classLayoutTable.size
    TableIndex.CONSTANT -> constantTable.size
    TableIndex.CUSTOM_ATTRIBUTE -> customAttributeTable.size
    TableIndex.DECL_SECURITY -> declSecurityTable.size
    TableIndex.ENC_LOG -> 0 // EnC tables are not populated by this port
    TableIndex.ENC_MAP -> 0
    TableIndex.EVENT_MAP -> eventMapTable.size
    TableIndex.EVENT -> eventTable.size
    TableIndex.EXPORTED_TYPE -> exportedTypeTable.size
    TableIndex.FIELD_LAYOUT -> fieldLayoutTable.size
    TableIndex.FIELD_MARSHAL -> fieldMarshalTable.size
    TableIndex.FIELD_RVA -> fieldRvaTable.size
    TableIndex.FIELD -> fieldTable.size
    TableIndex.FILE -> fileTable.size
    TableIndex.GENERIC_PARAM_CONSTRAINT -> genericParamConstraintTable.size
    TableIndex.GENERIC_PARAM -> genericParamTable.size
    TableIndex.IMPL_MAP -> implMapTable.size
    TableIndex.INTERFACE_IMPL -> interfaceImplTable.size
    TableIndex.MANIFEST_RESOURCE -> manifestResourceTable.size
    TableIndex.MEMBER_REF -> memberRefTable.size
    TableIndex.METHOD_IMPL -> methodImplTable.size
    TableIndex.METHOD_SEMANTICS -> methodSemanticsTable.size
    TableIndex.METHOD_SPEC -> methodSpecTable.size
    TableIndex.METHOD_DEF -> methodDefTable.size
    TableIndex.MODULE_REF -> moduleRefTable.size
    TableIndex.MODULE -> if (moduleRow != null) 1 else 0
    TableIndex.NESTED_CLASS -> nestedClassTable.size
    TableIndex.PARAM -> paramTable.size
    TableIndex.PROPERTY_MAP -> propertyMapTable.size
    TableIndex.PROPERTY -> propertyTable.size
    TableIndex.STAND_ALONE_SIG -> standAloneSigTable.size
    TableIndex.TYPE_DEF -> typeDefTable.size
    TableIndex.TYPE_REF -> typeRefTable.size
    TableIndex.TYPE_SPEC -> typeSpecTable.size
    TableIndex.DOCUMENT -> documentTable.size
    TableIndex.METHOD_DEBUG_INFORMATION -> methodDebugInformationTable.size
    TableIndex.LOCAL_SCOPE -> localScopeTable.size
    TableIndex.LOCAL_VARIABLE -> localVariableTable.size
    // debug tables (cut):
    TableIndex.DOCUMENT, TableIndex.METHOD_DEBUG_INFORMATION,
    TableIndex.LOCAL_SCOPE, TableIndex.LOCAL_VARIABLE,
    TableIndex.LOCAL_CONSTANT, TableIndex.IMPORT_SCOPE,
    TableIndex.STATE_MACHINE_METHOD, TableIndex.CUSTOM_DEBUG_INFORMATION,
    -> 0
    // tables not serialized:
    TableIndex.ASSEMBLY_OS, TableIndex.ASSEMBLY_PROCESSOR,
    TableIndex.ASSEMBLY_REF_OS, TableIndex.ASSEMBLY_REF_PROCESSOR,
    TableIndex.EVENT_PTR, TableIndex.FIELD_PTR,
    TableIndex.METHOD_PTR, TableIndex.PARAM_PTR, TableIndex.PROPERTY_PTR,
    -> 0
}

fun MetadataBuilder.getRowCounts(): IntArray {
    val rowCounts = IntArray(MetadataTokens.TABLE_COUNT)

    rowCounts[TableIndex.ASSEMBLY.value] = if (assemblyRow != null) 1 else 0
    rowCounts[TableIndex.ASSEMBLY_REF.value] = assemblyRefTable.size
    rowCounts[TableIndex.CLASS_LAYOUT.value] = classLayoutTable.size
    rowCounts[TableIndex.CONSTANT.value] = constantTable.size
    rowCounts[TableIndex.CUSTOM_ATTRIBUTE.value] = customAttributeTable.size
    rowCounts[TableIndex.DECL_SECURITY.value] = declSecurityTable.size
    rowCounts[TableIndex.ENC_LOG.value] = 0
    rowCounts[TableIndex.ENC_MAP.value] = 0
    rowCounts[TableIndex.EVENT_MAP.value] = eventMapTable.size
    rowCounts[TableIndex.EVENT.value] = eventTable.size
    rowCounts[TableIndex.EXPORTED_TYPE.value] = exportedTypeTable.size
    rowCounts[TableIndex.FIELD_LAYOUT.value] = fieldLayoutTable.size
    rowCounts[TableIndex.FIELD_MARSHAL.value] = fieldMarshalTable.size
    rowCounts[TableIndex.FIELD_RVA.value] = fieldRvaTable.size
    rowCounts[TableIndex.FIELD.value] = fieldTable.size
    rowCounts[TableIndex.FILE.value] = fileTable.size
    rowCounts[TableIndex.GENERIC_PARAM_CONSTRAINT.value] = genericParamConstraintTable.size
    rowCounts[TableIndex.GENERIC_PARAM.value] = genericParamTable.size
    rowCounts[TableIndex.IMPL_MAP.value] = implMapTable.size
    rowCounts[TableIndex.INTERFACE_IMPL.value] = interfaceImplTable.size
    rowCounts[TableIndex.MANIFEST_RESOURCE.value] = manifestResourceTable.size
    rowCounts[TableIndex.MEMBER_REF.value] = memberRefTable.size
    rowCounts[TableIndex.METHOD_IMPL.value] = methodImplTable.size
    rowCounts[TableIndex.METHOD_SEMANTICS.value] = methodSemanticsTable.size
    rowCounts[TableIndex.METHOD_SPEC.value] = methodSpecTable.size
    rowCounts[TableIndex.METHOD_DEF.value] = methodDefTable.size
    rowCounts[TableIndex.MODULE_REF.value] = moduleRefTable.size
    rowCounts[TableIndex.MODULE.value] = if (moduleRow != null) 1 else 0
    rowCounts[TableIndex.NESTED_CLASS.value] = nestedClassTable.size
    rowCounts[TableIndex.PARAM.value] = paramTable.size
    rowCounts[TableIndex.PROPERTY_MAP.value] = propertyMapTable.size
    rowCounts[TableIndex.PROPERTY.value] = propertyTable.size
    rowCounts[TableIndex.STAND_ALONE_SIG.value] = standAloneSigTable.size
    rowCounts[TableIndex.TYPE_DEF.value] = typeDefTable.size
    rowCounts[TableIndex.TYPE_REF.value] = typeRefTable.size
    rowCounts[TableIndex.TYPE_SPEC.value] = typeSpecTable.size
    rowCounts[TableIndex.DOCUMENT.value] = documentTable.size
    rowCounts[TableIndex.METHOD_DEBUG_INFORMATION.value] = methodDebugInformationTable.size
    rowCounts[TableIndex.LOCAL_SCOPE.value] = localScopeTable.size
    rowCounts[TableIndex.LOCAL_VARIABLE.value] = localVariableTable.size

    return rowCounts
}

fun MetadataBuilder.addDocument(
    name: BlobHandle,
    hashAlgorithm: GuidHandle,
    hash: BlobHandle,
    language: GuidHandle,
): DocumentHandle {
    documentTable.add(DocumentRow(name, hashAlgorithm, hash, language))
    return DocumentHandle.fromRowId(documentTable.size)
}

fun MetadataBuilder.addMethodDebugInformation(
    document: DocumentHandle,
    sequencePoints: BlobHandle,
) {
    methodDebugInformationTable.add(
        MethodDebugInformationRow(if (document.isNil) 0 else document.rowId, sequencePoints),
    )
}

fun MetadataBuilder.addLocalScope(
    method: Int,
    importScope: Int,
    variableList: Int,
    constantList: Int,
    startOffset: UInt,
    length: UInt,
): LocalScopeHandle {
    localScopeTable.add(LocalScopeRow(method, importScope, variableList, constantList, startOffset, length))
    return LocalScopeHandle.fromRowId(localScopeTable.size)
}

fun MetadataBuilder.addLocalVariable(attributes: Int, index: Int, name: StringHandle): LocalVariableHandle {
    require(attributes.toUInt() <= UShort.MAX_VALUE.toUInt()) { "attributes out of range" }
    require(index.toUInt() <= UShort.MAX_VALUE.toUInt()) { "index out of range" }
    localVariableTable.add(LocalVariableRow(attributes.toShort(), index.toShort(), name))
    return LocalVariableHandle.fromRowId(localVariableTable.size)
}


// ---------------------------------------------------------------------------
// Building
// ---------------------------------------------------------------------------

fun MetadataBuilder.addModule(
    generation: Int,
    moduleName: StringHandle,
    mvid: GuidHandle,
    encId: GuidHandle,
    encBaseId: GuidHandle,
): ModuleDefinitionHandle {
    require(generation.toUInt() <= UShort.MAX_VALUE) { "generation out of range" }
    check(moduleRow == null) { "module already added" }

    moduleRow = ModuleRow(
        generation = generation.toShort(),
        name = moduleName,
        moduleVersionId = mvid,
        encId = encId,
        encBaseId = encBaseId,
    )

    return ModuleDefinitionHandle.fromRowId(1)
}

fun MetadataBuilder.addAssembly(
    name: StringHandle,
    version: AssemblyVersion,
    culture: StringHandle,
    publicKey: BlobHandle,
    flags: Int,
    hashAlgorithm: UInt,
): AssemblyDefinitionHandle {
    check(assemblyRow == null) { "assembly already added" }

    assemblyRow = AssemblyRow(
        hashAlgorithm = hashAlgorithm,
        version = version,
        flags = flags,
        assemblyKey = publicKey,
        assemblyName = name,
        assemblyCulture = culture,
    )

    return AssemblyDefinitionHandle.fromRowId(1)
}

fun MetadataBuilder.addAssemblyReference(
    name: StringHandle,
    version: AssemblyVersion,
    culture: StringHandle,
    publicKeyOrToken: BlobHandle,
    flags: UInt,
    hashValue: BlobHandle,
): AssemblyReferenceHandle {
    assemblyRefTable.add(
        AssemblyRefTableRow(
            version = version,
            publicKeyToken = publicKeyOrToken,
            name = name,
            culture = culture,
            flags = flags,
            hashValue = hashValue,
        ),
    )
    return AssemblyReferenceHandle.fromRowId(assemblyRefTable.size)
}

fun MetadataBuilder.addTypeDefinition(
    attributes: UInt,
    namespace: StringHandle,
    name: StringHandle,
    baseType: EntityHandle,
    fieldList: FieldDefinitionHandle,
    methodList: MethodDefinitionHandle,
): TypeDefinitionHandle {
    typeDefTable.add(
        TypeDefRow(
            flags = attributes,
            name = name,
            namespace = namespace,
            extends = if (baseType.isNil) 0 else CodedIndex.typeDefOrRefOrSpec(baseType),
            fieldList = fieldList.rowId,
            methodList = methodList.rowId,
        ),
    )
    return TypeDefinitionHandle.fromRowId(typeDefTable.size)
}

fun MetadataBuilder.addTypeLayout(type: TypeDefinitionHandle, packingSize: Short, size: UInt) {
    classLayoutTable.add(ClassLayoutRow(packingSize, size, type.rowId))
}

fun MetadataBuilder.addInterfaceImplementation(
    type: TypeDefinitionHandle,
    implementedInterface: EntityHandle,
): InterfaceImplementationHandle {
    interfaceImplTable.add(
        InterfaceImplRow(type.rowId, CodedIndex.typeDefOrRefOrSpec(implementedInterface)),
    )
    return InterfaceImplementationHandle.fromRowId(interfaceImplTable.size)
}

fun MetadataBuilder.addNestedType(type: TypeDefinitionHandle, enclosingType: TypeDefinitionHandle) {
    nestedClassTable.add(NestedClassRow(type.rowId, enclosingType.rowId))
}

fun MetadataBuilder.addTypeReference(
    resolutionScope: EntityHandle,
    namespace: StringHandle,
    name: StringHandle,
): TypeReferenceHandle {
    typeRefTable.add(
        TypeRefRow(
            resolutionScope = if (resolutionScope.isNil) 0 else CodedIndex.resolutionScope(resolutionScope),
            name = name,
            namespace = namespace,
        ),
    )
    return TypeReferenceHandle.fromRowId(typeRefTable.size)
}

fun MetadataBuilder.addTypeSpecification(signature: BlobHandle): TypeSpecificationHandle {
    typeSpecTable.add(TypeSpecRow(signature))
    return TypeSpecificationHandle.fromRowId(typeSpecTable.size)
}

fun MetadataBuilder.addStandaloneSignature(signature: BlobHandle): StandaloneSignatureHandle {
    standAloneSigTable.add(StandaloneSigRow(signature))
    return StandaloneSignatureHandle.fromRowId(standAloneSigTable.size)
}

fun MetadataBuilder.addProperty(attributes: Int, name: StringHandle, signature: BlobHandle): PropertyDefinitionHandle {
    propertyTable.add(PropertyRow(attributes.toShort(), name, signature))
    return PropertyDefinitionHandle.fromRowId(propertyTable.size)
}

fun MetadataBuilder.addPropertyMap(declaringType: TypeDefinitionHandle, propertyList: PropertyDefinitionHandle) {
    propertyMapTable.add(PropertyMapRow(declaringType.rowId, propertyList.rowId))
}

fun MetadataBuilder.addEvent(attributes: Int, name: StringHandle, type: EntityHandle): EventDefinitionHandle {
    eventTable.add(EventRow(attributes.toShort(), name, CodedIndex.typeDefOrRefOrSpec(type)))
    return EventDefinitionHandle.fromRowId(eventTable.size)
}

fun MetadataBuilder.addEventMap(declaringType: TypeDefinitionHandle, eventList: EventDefinitionHandle) {
    eventMapTable.add(EventMapRow(declaringType.rowId, eventList.rowId))
}

fun MetadataBuilder.addConstant(parent: EntityHandle, value: Any?): ConstantHandle {
    val parentCodedIndex = CodedIndex.hasConstant(parent)

    constantTableNeedsSorting = constantTableNeedsSorting || (parentCodedIndex < constantTableLastParent)
    constantTableLastParent = parentCodedIndex

    constantTable.add(
        ConstantRow(
            type = MetadataWriterUtilities.getConstantTypeCode(value),
            parent = parentCodedIndex,
            value = getOrAddConstantBlob(value),
        ),
    )
    return ConstantHandle.fromRowId(constantTable.size)
}

fun MetadataBuilder.addMethodSemantics(
    association: EntityHandle,
    semantics: Int,
    methodDefinition: MethodDefinitionHandle,
) {
    val associationCodedIndex = CodedIndex.hasSemantics(association)

    methodSemanticsTableNeedsSorting =
        methodSemanticsTableNeedsSorting || (associationCodedIndex < methodSemanticsTableLastAssociation)
    methodSemanticsTableLastAssociation = associationCodedIndex

    methodSemanticsTable.add(
        MethodSemanticsRow(semantics.toShort(), methodDefinition.rowId, associationCodedIndex),
    )
}

fun MetadataBuilder.addCustomAttribute(
    parent: EntityHandle,
    constructor: EntityHandle,
    value: BlobHandle,
): CustomAttributeHandle {
    val parentCodedIndex = CodedIndex.hasCustomAttribute(parent)

    customAttributeTableNeedsSorting =
        customAttributeTableNeedsSorting || (parentCodedIndex < customAttributeTableLastParent)
    customAttributeTableLastParent = parentCodedIndex

    customAttributeTable.add(
        CustomAttributeRow(parentCodedIndex, CodedIndex.customAttributeType(constructor), value),
    )
    return CustomAttributeHandle.fromRowId(customAttributeTable.size)
}

fun MetadataBuilder.addMethodSpecification(method: EntityHandle, instantiation: BlobHandle): MethodSpecificationHandle {
    methodSpecTable.add(MethodSpecRow(CodedIndex.methodDefOrRef(method), instantiation))
    return MethodSpecificationHandle.fromRowId(methodSpecTable.size)
}

fun MetadataBuilder.addModuleReference(moduleName: StringHandle): ModuleReferenceHandle {
    moduleRefTable.add(ModuleRefRow(moduleName))
    return ModuleReferenceHandle.fromRowId(moduleRefTable.size)
}

fun MetadataBuilder.addParameter(attributes: Int, name: StringHandle, sequenceNumber: Int): ParameterHandle {
    require(sequenceNumber.toUInt() <= UShort.MAX_VALUE) { "sequenceNumber out of range" }
    paramTable.add(ParamRow(attributes.toShort(), sequenceNumber.toShort(), name))
    return ParameterHandle.fromRowId(paramTable.size)
}

fun MetadataBuilder.addGenericParameter(
    parent: EntityHandle,
    attributes: Int,
    name: StringHandle,
    index: Int,
): GenericParameterHandle {
    require(index.toUInt() <= UShort.MAX_VALUE) { "index out of range" }
    genericParamTable.add(
        GenericParamRow(index.toShort(), attributes.toShort(), CodedIndex.typeOrMethodDef(parent), name),
    )
    return GenericParameterHandle.fromRowId(genericParamTable.size)
}

fun MetadataBuilder.addGenericParameterConstraint(
    genericParameter: GenericParameterHandle,
    constraint: EntityHandle,
): GenericParameterConstraintHandle {
    genericParamConstraintTable.add(
        GenericParamConstraintRow(genericParameter.rowId, CodedIndex.typeDefOrRefOrSpec(constraint)),
    )
    return GenericParameterConstraintHandle.fromRowId(genericParamConstraintTable.size)
}

fun MetadataBuilder.addFieldDefinition(
    attributes: Int,
    name: StringHandle,
    signature: BlobHandle,
): FieldDefinitionHandle {
    fieldTable.add(FieldDefRow(attributes.toShort(), name, signature))
    return FieldDefinitionHandle.fromRowId(fieldTable.size)
}

fun MetadataBuilder.addFieldLayout(field: FieldDefinitionHandle, offset: Int) {
    fieldLayoutTable.add(FieldLayoutRow(offset, field.rowId))
}

fun MetadataBuilder.addMarshallingDescriptor(parent: EntityHandle, descriptor: BlobHandle) {
    val codedIndex = CodedIndex.hasFieldMarshal(parent)
    fieldMarshalTableNeedsSorting =
        fieldMarshalTableNeedsSorting || (codedIndex < fieldMarshalTableLastParent)
    fieldMarshalTableLastParent = codedIndex
    fieldMarshalTable.add(FieldMarshalRow(codedIndex, descriptor))
}

fun MetadataBuilder.addFieldRelativeVirtualAddress(field: FieldDefinitionHandle, offset: Int) {
    require(offset >= 0) { "offset out of range" }
    fieldRvaTable.add(FieldRvaRow(offset, field.rowId))
}

fun MetadataBuilder.addMethodDefinition(
    attributes: Int,
    implAttributes: Int,
    name: StringHandle,
    signature: BlobHandle,
    bodyOffset: Int,
    parameterList: ParameterHandle,
): MethodDefinitionHandle {
    require(bodyOffset >= -1) { "bodyOffset out of range" }
    methodDefTable.add(
        MethodRow(
            bodyOffset = bodyOffset,
            implFlags = implAttributes.toShort(),
            flags = attributes.toShort(),
            name = name,
            signature = signature,
            paramList = parameterList.rowId,
        ),
    )
    return MethodDefinitionHandle.fromRowId(methodDefTable.size)
}

fun MetadataBuilder.addMethodImport(
    method: MethodDefinitionHandle,
    attributes: Int,
    name: StringHandle,
    module: ModuleReferenceHandle,
) {
    implMapTable.add(
        ImplMapRow(
            mappingFlags = attributes.toShort(),
            memberForwarded = CodedIndex.memberForwarded(method.toEntityHandle()),
            importName = name,
            importScope = module.rowId,
        ),
    )
}

fun MetadataBuilder.addMethodImplementation(
    type: TypeDefinitionHandle,
    methodBody: EntityHandle,
    methodDeclaration: EntityHandle,
): MethodImplementationHandle {
    methodImplTable.add(
        MethodImplRow(type.rowId, CodedIndex.methodDefOrRef(methodBody), CodedIndex.methodDefOrRef(methodDeclaration)),
    )
    return MethodImplementationHandle.fromRowId(methodImplTable.size)
}

fun MetadataBuilder.addMemberReference(
    parent: EntityHandle,
    name: StringHandle,
    signature: BlobHandle,
): MemberReferenceHandle {
    memberRefTable.add(MemberRefRow(CodedIndex.memberRefParent(parent), name, signature))
    return MemberReferenceHandle.fromRowId(memberRefTable.size)
}

fun MetadataBuilder.addManifestResource(
    attributes: UInt,
    name: StringHandle,
    implementation: EntityHandle,
    offset: UInt,
): ManifestResourceHandle {
    manifestResourceTable.add(
        ManifestResourceRow(
            offset = offset,
            flags = attributes,
            name = name,
            implementation = if (implementation.isNil) 0 else CodedIndex.implementation(implementation),
        ),
    )
    return ManifestResourceHandle.fromRowId(manifestResourceTable.size)
}

fun MetadataBuilder.addAssemblyFile(
    name: StringHandle,
    hashValue: BlobHandle,
    containsMetadata: Boolean,
): AssemblyFileHandle {
    fileTable.add(FileTableRow(if (containsMetadata) 0u else 1u, name, hashValue))
    return AssemblyFileHandle.fromRowId(fileTable.size)
}

fun MetadataBuilder.addExportedType(
    attributes: UInt,
    namespace: StringHandle,
    name: StringHandle,
    implementation: EntityHandle,
    typeDefinitionId: Int,
): ExportedTypeHandle {
    exportedTypeTable.add(
        ExportedTypeRow(
            flags = attributes,
            typeDefId = typeDefinitionId,
            typeName = name,
            typeNamespace = namespace,
            implementation = CodedIndex.implementation(implementation),
        ),
    )
    return ExportedTypeHandle.fromRowId(exportedTypeTable.size)
}

fun MetadataBuilder.addDeclarativeSecurityAttribute(
    parent: EntityHandle,
    action: Int,
    permissionSet: BlobHandle,
): DeclarativeSecurityAttributeHandle {
    val parentCodedIndex = CodedIndex.hasDeclSecurity(parent)
    declSecurityTableNeedsSorting =
        declSecurityTableNeedsSorting || (parentCodedIndex < declSecurityTableLastParent)
    declSecurityTableLastParent = parentCodedIndex
    declSecurityTable.add(DeclSecurityRow(action.toShort(), parentCodedIndex, permissionSet))
    return DeclarativeSecurityAttributeHandle.fromRowId(declSecurityTable.size)
}

// ---------------------------------------------------------------------------
// Validation (auto-sorted tables are not validated here; they sort at
// serialization time — see MetadataBuilderSerializer.kt)
// ---------------------------------------------------------------------------

fun MetadataBuilder.validateOrder() {
    validateSorted("ClassLayout", classLayoutTable) { it.parent }
    validateSorted("FieldLayout", fieldLayoutTable) { it.field }
    validateSorted("FieldRva", fieldRvaTable) { it.field }
    validateSorted("ImplMap", implMapTable) { it.memberForwarded }
    validateSortedStrictly("InterfaceImpl", interfaceImplTable) { it.clazz }
    validateSortedStrictly("MethodImpl", methodImplTable) { it.clazz }
    validateSorted("NestedClass", nestedClassTable) { it.nestedClass }
    validateGenericParamTable()
    validateSortedStrictly("GenericParamConstraint", genericParamConstraintTable) { it.owner }
}

private fun <T> MetadataBuilder.validateSorted(
    tableName: String,
    table: List<T>,
    selector: (T) -> Int,
) {
    for (i in 1 until table.size) {
        if (selector(table[i - 1]) >= selector(table[i])) {
            throw IllegalStateException("metadata table '$tableName' is not sorted")
        }
    }
}

private fun <T> MetadataBuilder.validateSortedStrictly(
    tableName: String,
    table: List<T>,
    selector: (T) -> Int,
) {
    for (i in 1 until table.size) {
        if (selector(table[i - 1]) > selector(table[i])) {
            throw IllegalStateException("metadata table '$tableName' is not sorted")
        }
    }
}

private fun MetadataBuilder.validateGenericParamTable() {
    if (genericParamTable.isEmpty()) return
    var previous = genericParamTable[0]
    for (i in 1 until genericParamTable.size) {
        val current = genericParamTable[i]
        if (current.owner > previous.owner) continue
        if (previous.owner == current.owner && current.number > previous.number) continue
        throw IllegalStateException("metadata table 'GenericParam' is not sorted")
    }
}
