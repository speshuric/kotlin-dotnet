// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/MetadataTokens.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - reader-context overloads (GetRowNumber/GetHeapOffset/GetToken with
//    MetadataReader) and MapVirtualHandleRowId are cut: virtual handles
//    and the reader are outside the port scope;
//  - debug factories (DocumentHandle, LocalScopeHandle, ...) are cut
//    (Portable PDB scope per ADR 0009);
//  - TryGetTableIndex/TryGetHeapIndex out-parameters are ported as
//    nullable returns.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.Handle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.HandleKind
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.UserStringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyFileHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ConstantHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.CustomAttributeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.DeclarativeSecurityAttributeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EventDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ExportedTypeHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GenericParameterConstraintHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GenericParameterHandle
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
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeSpecificationHandle

object MetadataTokens {
    /** Maximum number of tables that can be present in Ecma335 metadata. */
    const val TABLE_COUNT: Int = 64 // headers use 64-bit table masks

    /** Maximum number of heaps that can be present in Ecma335 metadata. */
    val HEAP_COUNT: Int = HEAP_INDEX_COUNT

    /**
     * Returns the row number of a metadata table entry that corresponds
     * to the specified handle.
     *
     * One-based row number, or -1 if the handle can only be interpreted
     * in the context of a specific reader/builder.
     */
    fun getRowNumber(handle: EntityHandle): Int = if (handle.isVirtual) -1 else handle.rowId

    /**
     * Returns the offset of metadata heap data that corresponds to the
     * specified handle.
     *
     * Zero-based offset, or -1 for virtual handles.
     */
    fun getHeapOffset(handle: Handle): Int {
        check(handle.isHeapHandle) { "heap handle required" }
        return if (handle.isVirtual) -1 else handle.offset
    }

    fun getHeapOffset(handle: BlobHandle): Int = if (handle.isNil) 0 else handle.getHeapOffset()

    /** 1-based index into the #Guid heap (an array of 16-byte GUIDs). */
    fun getHeapOffset(handle: GuidHandle): Int = handle.index

    fun getHeapOffset(handle: UserStringHandle): Int = handle.getHeapOffset()

    fun getHeapOffset(handle: StringHandle): Int = if (handle.isVirtual) -1 else handle.getHeapOffset()

    /**
     * Returns the metadata token of the specified entity handle,
     * or 0 if it can only be interpreted in a reader/builder context.
     */
    fun getToken(handle: EntityHandle): Int = if (handle.isVirtual) 0 else handle.token

    /**
     * Returns the metadata token of the specified handle.
     *
     * A token can only be retrieved for a metadata table handle or a heap
     * handle of type [HandleKind.USER_STRING].
     */
    fun getToken(handle: Handle): Int {
        check(handle.isEntityOrUserStringHandle) { "entity or user string handle required" }
        return if (handle.isVirtual) 0 else handle.token
    }

    /**
     * Gets the [TableIndex] of the table corresponding to the specified
     * [HandleKind], or null when the kind has no table.
     */
    fun tryGetTableIndex(type: HandleKind): TableIndex? {
        // We don't have a HandleKind for PropertyMap, EventMap, MethodSemantics,
        // *Ptr, AssemblyOS, etc. tables, but one can get one by creating a handle
        // from a token and reading its Kind.
        val raw = type.value.toLong()
        return if (raw < TABLE_COUNT && ((1uL shl type.value.toInt()) and TableMask.ALL_TABLES) != 0uL) {
            TableIndex.entries.firstOrNull { it.value == type.value.toInt() }
        } else {
            null
        }
    }

    /**
     * Gets the [HeapIndex] of the heap corresponding to the specified
     * [HandleKind], or null otherwise.
     */
    fun tryGetHeapIndex(type: HandleKind): HeapIndex? =
        when (type) {
            HandleKind.USER_STRING -> HeapIndex.USER_STRING
            HandleKind.STRING, HandleKind.NAMESPACE_DEFINITION -> HeapIndex.STRING
            HandleKind.BLOB -> HeapIndex.BLOB
            HandleKind.GUID -> HeapIndex.GUID
            else -> null
        }

    // --- Handle factories ---

    /**
     * Creates a [Handle] from a token value.
     *
     * The token must encode a metadata table entity or an offset in the
     * user string heap.
     */
    fun handle(token: Int): Handle {
        check(TokenTypeIds.isEntityOrUserStringToken(token.toUInt())) { "invalid metadata token" }
        return Handle.fromVToken(token.toUInt())
    }

    /**
     * Creates an [EntityHandle] from a token value.
     */
    fun entityHandle(token: Int): EntityHandle {
        check(TokenTypeIds.isEntityToken(token.toUInt())) { "invalid metadata entity token" }
        return EntityHandle(token.toUInt())
    }

    /** Creates an [EntityHandle] from a table index and row number. */
    fun entityHandle(tableIndex: TableIndex, rowNumber: Int): EntityHandle =
        handle(tableIndex, rowNumber)

    /** Creates an [EntityHandle] from a table index and row number. */
    fun handle(tableIndex: TableIndex, rowNumber: Int): EntityHandle {
        val token = (tableIndex.value shl TokenTypeIds.ROW_ID_BIT_COUNT) or rowNumber
        check(TokenTypeIds.isEntityOrUserStringToken(token.toUInt())) { "table index out of range" }
        return EntityHandle(token.toUInt())
    }

    private fun toRowId(rowNumber: Int): Int = rowNumber and TokenTypeIds.RID_MASK.toInt()

    fun interfaceImplementationHandle(rowNumber: Int): InterfaceImplementationHandle =
        InterfaceImplementationHandle.fromRowId(toRowId(rowNumber))

    fun methodDefinitionHandle(rowNumber: Int): MethodDefinitionHandle =
        MethodDefinitionHandle.fromRowId(toRowId(rowNumber))

    fun methodImplementationHandle(rowNumber: Int): MethodImplementationHandle =
        MethodImplementationHandle.fromRowId(toRowId(rowNumber))

    fun methodSpecificationHandle(rowNumber: Int): MethodSpecificationHandle =
        MethodSpecificationHandle.fromRowId(toRowId(rowNumber))

    fun typeDefinitionHandle(rowNumber: Int): TypeDefinitionHandle =
        TypeDefinitionHandle.fromRowId(toRowId(rowNumber))

    fun exportedTypeHandle(rowNumber: Int): ExportedTypeHandle =
        ExportedTypeHandle.fromRowId(toRowId(rowNumber))

    fun typeReferenceHandle(rowNumber: Int): TypeReferenceHandle =
        TypeReferenceHandle.fromRowId(toRowId(rowNumber))

    fun typeSpecificationHandle(rowNumber: Int): TypeSpecificationHandle =
        TypeSpecificationHandle.fromRowId(toRowId(rowNumber))

    fun memberReferenceHandle(rowNumber: Int): MemberReferenceHandle =
        MemberReferenceHandle.fromRowId(toRowId(rowNumber))

    fun fieldDefinitionHandle(rowNumber: Int): FieldDefinitionHandle =
        FieldDefinitionHandle.fromRowId(toRowId(rowNumber))

    fun eventDefinitionHandle(rowNumber: Int): EventDefinitionHandle =
        EventDefinitionHandle.fromRowId(toRowId(rowNumber))

    fun propertyDefinitionHandle(rowNumber: Int): PropertyDefinitionHandle =
        PropertyDefinitionHandle.fromRowId(toRowId(rowNumber))

    fun assemblyDefinitionHandle(rowNumber: Int): AssemblyDefinitionHandle =
        AssemblyDefinitionHandle.fromRowId(toRowId(rowNumber))

    fun standaloneSignatureHandle(rowNumber: Int): StandaloneSignatureHandle =
        StandaloneSignatureHandle.fromRowId(toRowId(rowNumber))

    fun parameterHandle(rowNumber: Int): ParameterHandle =
        ParameterHandle.fromRowId(toRowId(rowNumber))

    fun genericParameterHandle(rowNumber: Int): GenericParameterHandle =
        GenericParameterHandle.fromRowId(toRowId(rowNumber))

    fun genericParameterConstraintHandle(rowNumber: Int): GenericParameterConstraintHandle =
        GenericParameterConstraintHandle.fromRowId(toRowId(rowNumber))

    fun moduleReferenceHandle(rowNumber: Int): ModuleReferenceHandle =
        ModuleReferenceHandle.fromRowId(toRowId(rowNumber))

    fun assemblyReferenceHandle(rowNumber: Int): AssemblyReferenceHandle =
        AssemblyReferenceHandle.fromRowId(toRowId(rowNumber))

    fun customAttributeHandle(rowNumber: Int): CustomAttributeHandle =
        CustomAttributeHandle.fromRowId(toRowId(rowNumber))

    fun declarativeSecurityAttributeHandle(rowNumber: Int): DeclarativeSecurityAttributeHandle =
        DeclarativeSecurityAttributeHandle.fromRowId(toRowId(rowNumber))

    fun constantHandle(rowNumber: Int): ConstantHandle =
        ConstantHandle.fromRowId(toRowId(rowNumber))

    fun manifestResourceHandle(rowNumber: Int): ManifestResourceHandle =
        ManifestResourceHandle.fromRowId(toRowId(rowNumber))

    fun assemblyFileHandle(rowNumber: Int): AssemblyFileHandle =
        AssemblyFileHandle.fromRowId(toRowId(rowNumber))

    // heaps

    fun userStringHandle(offset: Int): UserStringHandle =
        UserStringHandle.fromOffset(offset and TokenTypeIds.RID_MASK.toInt())

    fun stringHandle(offset: Int): StringHandle = StringHandle.fromOffset(offset)

    fun blobHandle(offset: Int): BlobHandle = BlobHandle.fromOffset(offset)

    fun guidHandle(index: Int): GuidHandle = GuidHandle.fromIndex(index)
}
