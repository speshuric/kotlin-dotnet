// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/MetadataBuilder.Tables.cs row structs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - C# struct rows are ported as internal data classes;
//  - System.Version is replaced by [AssemblyVersion];
//  - enum-typed fields carry raw Int values (the BCL enums are outside
//    the port scope; the compiler emits raw flag values);
//  - debug table rows are cut (Portable PDB scope per ADR 0009).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.GuidHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle

/** Analog of `System.Version` for assembly tables (major/minor/build/revision). */
data class AssemblyVersion(
    val major: Int,
    val minor: Int,
    val build: Int = 0,
    val revision: Int = 0,
)

internal class ModuleRow(
    val generation: Short,
    val name: StringHandle,
    val moduleVersionId: GuidHandle,
    val encId: GuidHandle,
    val encBaseId: GuidHandle,
)

internal class AssemblyRefTableRow(
    val version: AssemblyVersion,
    val publicKeyToken: BlobHandle,
    val name: StringHandle,
    val culture: StringHandle,
    val flags: UInt,
    val hashValue: BlobHandle,
)

internal class AssemblyRow(
    val hashAlgorithm: UInt,
    val version: AssemblyVersion,
    val flags: Int,
    val assemblyKey: BlobHandle,
    val assemblyName: StringHandle,
    val assemblyCulture: StringHandle,
)

internal class ClassLayoutRow(val packingSize: Short, val classSize: UInt, val parent: Int)
internal class ConstantRow(val type: Byte, val parent: Int, val value: BlobHandle)
internal class CustomAttributeRow(val parent: Int, val type: Int, val value: BlobHandle)
internal class DeclSecurityRow(val action: Short, val parent: Int, val permissionSet: BlobHandle)
internal class EventRow(val eventFlags: Short, val name: StringHandle, val eventType: Int)
internal class EventMapRow(val parent: Int, val eventList: Int)
internal class ExportedTypeRow(
    val flags: UInt,
    val typeDefId: Int,
    val typeName: StringHandle,
    val typeNamespace: StringHandle,
    val implementation: Int,
)
internal class FieldLayoutRow(val offset: Int, val field: Int)
internal class FieldMarshalRow(val parent: Int, val nativeType: BlobHandle)
internal class FieldRvaRow(val offset: Int, val field: Int)
internal class FieldDefRow(val flags: Short, val name: StringHandle, val signature: BlobHandle)
internal class FileTableRow(val flags: UInt, val fileName: StringHandle, val hashValue: BlobHandle)
internal class GenericParamConstraintRow(val owner: Int, val constraint: Int)
internal class GenericParamRow(val number: Short, val flags: Short, val owner: Int, val name: StringHandle)
internal class ImplMapRow(
    val mappingFlags: Short,
    val memberForwarded: Int,
    val importName: StringHandle,
    val importScope: Int,
)
internal class InterfaceImplRow(val clazz: Int, val interface_: Int)
internal class ManifestResourceRow(
    val offset: UInt,
    val flags: UInt,
    val name: StringHandle,
    val implementation: Int,
)
internal class MemberRefRow(val clazz: Int, val name: StringHandle, val signature: BlobHandle)
internal class MethodImplRow(val clazz: Int, val methodBody: Int, val methodDecl: Int)
internal class MethodSemanticsRow(val semantic: Short, val method: Int, val association: Int)
internal class MethodSpecRow(val method: Int, val instantiation: BlobHandle)
internal class MethodRow(
    val bodyOffset: Int,
    val implFlags: Short,
    val flags: Short,
    val name: StringHandle,
    val signature: BlobHandle,
    val paramList: Int,
)
internal class ModuleRefRow(val name: StringHandle)
internal class NestedClassRow(val nestedClass: Int, val enclosingClass: Int)
internal class ParamRow(val flags: Short, val sequence: Short, val name: StringHandle)
internal class PropertyMapRow(val parent: Int, val propertyList: Int)
internal class PropertyRow(val propFlags: Short, val name: StringHandle, val type: BlobHandle)
internal class TypeDefRow(
    val flags: UInt,
    val name: StringHandle,
    val namespace: StringHandle,
    val extends: Int,
    val fieldList: Int,
    val methodList: Int,
)
internal class TypeRefRow(val resolutionScope: Int, val name: StringHandle, val namespace: StringHandle)
internal class TypeSpecRow(val signature: BlobHandle)
internal class StandaloneSigRow(val signature: BlobHandle)
