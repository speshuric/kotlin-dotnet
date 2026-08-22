// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/CodedIndex.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - HasCustomDebugInformation is cut (Portable PDB scope, ADR 0009);
//  - the per-kind switch of the original is ported as a lookup map
//    (kind -> tag), which keeps each family down to a table + one
//    lookup function.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.HandleKind

object CodedIndex {

    /** Calculates a HasCustomAttribute coded index for the specified handle. */
    fun hasCustomAttribute(handle: EntityHandle): Int =
        (handle.rowId shl HAS_CUSTOM_ATTRIBUTE_BIT_COUNT) or toHasCustomAttribute(handle.kind).id

    private const val HAS_CUSTOM_ATTRIBUTE_BIT_COUNT: Int = 5

    private val hasCustomAttributeByKind: Map<HandleKind, HasCustomAttribute> = mapOf(
        HandleKind.METHOD_DEFINITION to HasCustomAttribute.METHOD_DEF,
        HandleKind.FIELD_DEFINITION to HasCustomAttribute.FIELD,
        HandleKind.TYPE_REFERENCE to HasCustomAttribute.TYPE_REF,
        HandleKind.TYPE_DEFINITION to HasCustomAttribute.TYPE_DEF,
        HandleKind.PARAMETER to HasCustomAttribute.PARAM,
        HandleKind.INTERFACE_IMPLEMENTATION to HasCustomAttribute.INTERFACE_IMPL,
        HandleKind.MEMBER_REFERENCE to HasCustomAttribute.MEMBER_REF,
        HandleKind.MODULE_DEFINITION to HasCustomAttribute.MODULE,
        HandleKind.DECLARATIVE_SECURITY_ATTRIBUTE to HasCustomAttribute.DECL_SECURITY,
        HandleKind.PROPERTY_DEFINITION to HasCustomAttribute.PROPERTY,
        HandleKind.EVENT_DEFINITION to HasCustomAttribute.EVENT,
        HandleKind.STANDALONE_SIGNATURE to HasCustomAttribute.STAND_ALONE_SIG,
        HandleKind.MODULE_REFERENCE to HasCustomAttribute.MODULE_REF,
        HandleKind.TYPE_SPECIFICATION to HasCustomAttribute.TYPE_SPEC,
        HandleKind.ASSEMBLY_DEFINITION to HasCustomAttribute.ASSEMBLY,
        HandleKind.ASSEMBLY_REFERENCE to HasCustomAttribute.ASSEMBLY_REF,
        HandleKind.ASSEMBLY_FILE to HasCustomAttribute.FILE,
        HandleKind.EXPORTED_TYPE to HasCustomAttribute.EXPORTED_TYPE,
        HandleKind.MANIFEST_RESOURCE to HasCustomAttribute.MANIFEST_RESOURCE,
        HandleKind.GENERIC_PARAMETER to HasCustomAttribute.GENERIC_PARAM,
        HandleKind.GENERIC_PARAMETER_CONSTRAINT to HasCustomAttribute.GENERIC_PARAM_CONSTRAINT,
        HandleKind.METHOD_SPECIFICATION to HasCustomAttribute.METHOD_SPEC,
    )

    private fun toHasCustomAttribute(kind: HandleKind): HasCustomAttribute =
        requireNotNull(hasCustomAttributeByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a HasConstant coded index for the specified handle. */
    fun hasConstant(handle: EntityHandle): Int =
        (handle.rowId shl HAS_CONSTANT_BIT_COUNT) or toHasConstant(handle.kind).id

    private const val HAS_CONSTANT_BIT_COUNT: Int = 2

    private val hasConstantByKind: Map<HandleKind, HasConstant> = mapOf(
        HandleKind.FIELD_DEFINITION to HasConstant.FIELD,
        HandleKind.PARAMETER to HasConstant.PARAM,
        HandleKind.PROPERTY_DEFINITION to HasConstant.PROPERTY,
    )

    private fun toHasConstant(kind: HandleKind): HasConstant =
        requireNotNull(hasConstantByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a CustomAttributeType coded index for the specified handle. */
    fun customAttributeType(handle: EntityHandle): Int =
        (handle.rowId shl CUSTOM_ATTRIBUTE_TYPE_BIT_COUNT) or toCustomAttributeType(handle.kind).id

    private const val CUSTOM_ATTRIBUTE_TYPE_BIT_COUNT: Int = 3

    private val customAttributeTypeByKind: Map<HandleKind, CustomAttributeType> = mapOf(
        HandleKind.METHOD_DEFINITION to CustomAttributeType.METHOD_DEF,
        HandleKind.MEMBER_REFERENCE to CustomAttributeType.MEMBER_REF,
    )

    private fun toCustomAttributeType(kind: HandleKind): CustomAttributeType =
        requireNotNull(customAttributeTypeByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a HasDeclSecurity coded index for the specified handle. */
    fun hasDeclSecurity(handle: EntityHandle): Int =
        (handle.rowId shl HAS_DECL_SECURITY_BIT_COUNT) or toHasDeclSecurity(handle.kind).id

    private const val HAS_DECL_SECURITY_BIT_COUNT: Int = 2

    private val hasDeclSecurityByKind: Map<HandleKind, HasDeclSecurity> = mapOf(
        HandleKind.TYPE_DEFINITION to HasDeclSecurity.TYPE_DEF,
        HandleKind.METHOD_DEFINITION to HasDeclSecurity.METHOD_DEF,
        HandleKind.ASSEMBLY_DEFINITION to HasDeclSecurity.ASSEMBLY,
    )

    private fun toHasDeclSecurity(kind: HandleKind): HasDeclSecurity =
        requireNotNull(hasDeclSecurityByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a HasFieldMarshal coded index for the specified handle. */
    fun hasFieldMarshal(handle: EntityHandle): Int =
        (handle.rowId shl HAS_FIELD_MARSHAL_BIT_COUNT) or toHasFieldMarshal(handle.kind).id

    private const val HAS_FIELD_MARSHAL_BIT_COUNT: Int = 1

    private val hasFieldMarshalByKind: Map<HandleKind, HasFieldMarshal> = mapOf(
        HandleKind.FIELD_DEFINITION to HasFieldMarshal.FIELD,
        HandleKind.PARAMETER to HasFieldMarshal.PARAM,
    )

    private fun toHasFieldMarshal(kind: HandleKind): HasFieldMarshal =
        requireNotNull(hasFieldMarshalByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a HasSemantics coded index for the specified handle. */
    fun hasSemantics(handle: EntityHandle): Int =
        (handle.rowId shl HAS_SEMANTICS_BIT_COUNT) or toHasSemantics(handle.kind).id

    private const val HAS_SEMANTICS_BIT_COUNT: Int = 1

    private val hasSemanticsByKind: Map<HandleKind, HasSemantics> = mapOf(
        HandleKind.EVENT_DEFINITION to HasSemantics.EVENT,
        HandleKind.PROPERTY_DEFINITION to HasSemantics.PROPERTY,
    )

    private fun toHasSemantics(kind: HandleKind): HasSemantics =
        requireNotNull(hasSemanticsByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a Implementation coded index for the specified handle. */
    fun implementation(handle: EntityHandle): Int =
        (handle.rowId shl IMPLEMENTATION_BIT_COUNT) or toImplementation(handle.kind).id

    private const val IMPLEMENTATION_BIT_COUNT: Int = 2

    private val implementationByKind: Map<HandleKind, Implementation> = mapOf(
        HandleKind.ASSEMBLY_FILE to Implementation.FILE,
        HandleKind.ASSEMBLY_REFERENCE to Implementation.ASSEMBLY_REF,
        HandleKind.EXPORTED_TYPE to Implementation.EXPORTED_TYPE,
    )

    private fun toImplementation(kind: HandleKind): Implementation =
        requireNotNull(implementationByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a MemberForwarded coded index for the specified handle. */
    fun memberForwarded(handle: EntityHandle): Int =
        (handle.rowId shl MEMBER_FORWARDED_BIT_COUNT) or toMemberForwarded(handle.kind).id

    private const val MEMBER_FORWARDED_BIT_COUNT: Int = 1

    private val memberForwardedByKind: Map<HandleKind, MemberForwarded> = mapOf(
        HandleKind.FIELD_DEFINITION to MemberForwarded.FIELD,
        HandleKind.METHOD_DEFINITION to MemberForwarded.METHOD_DEF,
    )

    private fun toMemberForwarded(kind: HandleKind): MemberForwarded =
        requireNotNull(memberForwardedByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a MemberRefParent coded index for the specified handle. */
    fun memberRefParent(handle: EntityHandle): Int =
        (handle.rowId shl MEMBER_REF_PARENT_BIT_COUNT) or toMemberRefParent(handle.kind).id

    private const val MEMBER_REF_PARENT_BIT_COUNT: Int = 3

    private val memberRefParentByKind: Map<HandleKind, MemberRefParent> = mapOf(
        HandleKind.TYPE_DEFINITION to MemberRefParent.TYPE_DEF,
        HandleKind.TYPE_REFERENCE to MemberRefParent.TYPE_REF,
        HandleKind.MODULE_REFERENCE to MemberRefParent.MODULE_REF,
        HandleKind.METHOD_DEFINITION to MemberRefParent.METHOD_DEF,
        HandleKind.TYPE_SPECIFICATION to MemberRefParent.TYPE_SPEC,
    )

    private fun toMemberRefParent(kind: HandleKind): MemberRefParent =
        requireNotNull(memberRefParentByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a MethodDefOrRef coded index for the specified handle. */
    fun methodDefOrRef(handle: EntityHandle): Int =
        (handle.rowId shl METHOD_DEF_OR_REF_BIT_COUNT) or toMethodDefOrRef(handle.kind).id

    private const val METHOD_DEF_OR_REF_BIT_COUNT: Int = 1

    private val methodDefOrRefByKind: Map<HandleKind, MethodDefOrRef> = mapOf(
        HandleKind.METHOD_DEFINITION to MethodDefOrRef.METHOD_DEF,
        HandleKind.MEMBER_REFERENCE to MethodDefOrRef.MEMBER_REF,
    )

    private fun toMethodDefOrRef(kind: HandleKind): MethodDefOrRef =
        requireNotNull(methodDefOrRefByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a ResolutionScope coded index for the specified handle. */
    fun resolutionScope(handle: EntityHandle): Int =
        (handle.rowId shl RESOLUTION_SCOPE_BIT_COUNT) or toResolutionScope(handle.kind).id

    private const val RESOLUTION_SCOPE_BIT_COUNT: Int = 2

    private val resolutionScopeByKind: Map<HandleKind, ResolutionScope> = mapOf(
        HandleKind.MODULE_DEFINITION to ResolutionScope.MODULE,
        HandleKind.MODULE_REFERENCE to ResolutionScope.MODULE_REF,
        HandleKind.ASSEMBLY_REFERENCE to ResolutionScope.ASSEMBLY_REF,
        HandleKind.TYPE_REFERENCE to ResolutionScope.TYPE_REF,
    )

    private fun toResolutionScope(kind: HandleKind): ResolutionScope =
        requireNotNull(resolutionScopeByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a TypeDefOrRef coded index for the specified handle. */
    fun typeDefOrRef(handle: EntityHandle): Int =
        (handle.rowId shl TYPE_DEF_OR_REF_BIT_COUNT) or toTypeDefOrRef(handle.kind).id

    private const val TYPE_DEF_OR_REF_BIT_COUNT: Int = 2

    private val typeDefOrRefByKind: Map<HandleKind, TypeDefOrRef> = mapOf(
        HandleKind.TYPE_DEFINITION to TypeDefOrRef.TYPE_DEF,
        HandleKind.TYPE_REFERENCE to TypeDefOrRef.TYPE_REF,
    )

    private fun toTypeDefOrRef(kind: HandleKind): TypeDefOrRef =
        requireNotNull(typeDefOrRefByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a TypeDefOrRefOrSpec coded index for the specified handle. */
    fun typeDefOrRefOrSpec(handle: EntityHandle): Int =
        (handle.rowId shl TYPE_DEF_OR_REF_OR_SPEC_BIT_COUNT) or toTypeDefOrRefOrSpec(handle.kind).id

    private const val TYPE_DEF_OR_REF_OR_SPEC_BIT_COUNT: Int = 2

    private val typeDefOrRefOrSpecByKind: Map<HandleKind, TypeDefOrRefOrSpec> = mapOf(
        HandleKind.TYPE_DEFINITION to TypeDefOrRefOrSpec.TYPE_DEF,
        HandleKind.TYPE_REFERENCE to TypeDefOrRefOrSpec.TYPE_REF,
        HandleKind.TYPE_SPECIFICATION to TypeDefOrRefOrSpec.TYPE_SPEC,
    )

    private fun toTypeDefOrRefOrSpec(kind: HandleKind): TypeDefOrRefOrSpec =
        requireNotNull(typeDefOrRefOrSpecByKind[kind]) { "unexpected handle kind: $kind" }

    /** Calculates a TypeOrMethodDef coded index for the specified handle. */
    fun typeOrMethodDef(handle: EntityHandle): Int =
        (handle.rowId shl TYPE_OR_METHOD_DEF_BIT_COUNT) or toTypeOrMethodDef(handle.kind).id

    private const val TYPE_OR_METHOD_DEF_BIT_COUNT: Int = 1

    private val typeOrMethodDefByKind: Map<HandleKind, TypeOrMethodDef> = mapOf(
        HandleKind.TYPE_DEFINITION to TypeOrMethodDef.TYPE_DEF,
        HandleKind.METHOD_DEFINITION to TypeOrMethodDef.METHOD_DEF,
    )

    private fun toTypeOrMethodDef(kind: HandleKind): TypeOrMethodDef =
        requireNotNull(typeOrMethodDefByKind[kind]) { "unexpected handle kind: $kind" }

    /** Tag values of the ECMA-335 HasCustomAttribute coded index. */
    private enum class HasCustomAttribute(val id: Int) {
        METHOD_DEF(0),
        FIELD(1),
        TYPE_REF(2),
        TYPE_DEF(3),
        PARAM(4),
        INTERFACE_IMPL(5),
        MEMBER_REF(6),
        MODULE(7),
        DECL_SECURITY(8),
        PROPERTY(9),
        EVENT(10),
        STAND_ALONE_SIG(11),
        MODULE_REF(12),
        TYPE_SPEC(13),
        ASSEMBLY(14),
        ASSEMBLY_REF(15),
        FILE(16),
        EXPORTED_TYPE(17),
        MANIFEST_RESOURCE(18),
        GENERIC_PARAM(19),
        GENERIC_PARAM_CONSTRAINT(20),
        METHOD_SPEC(21),
    }

    /** Tag values of the ECMA-335 HasConstant coded index. */
    private enum class HasConstant(val id: Int) {
        FIELD(0),
        PARAM(1),
        PROPERTY(2),
    }

    /** Tag values of the ECMA-335 CustomAttributeType coded index. */
    private enum class CustomAttributeType(val id: Int) {
        METHOD_DEF(2),
        MEMBER_REF(3),
    }

    /** Tag values of the ECMA-335 HasDeclSecurity coded index. */
    private enum class HasDeclSecurity(val id: Int) {
        TYPE_DEF(0),
        METHOD_DEF(1),
        ASSEMBLY(2),
    }

    /** Tag values of the ECMA-335 HasFieldMarshal coded index. */
    private enum class HasFieldMarshal(val id: Int) {
        FIELD(0),
        PARAM(1),
    }

    /** Tag values of the ECMA-335 HasSemantics coded index. */
    private enum class HasSemantics(val id: Int) {
        EVENT(0),
        PROPERTY(1),
    }

    /** Tag values of the ECMA-335 Implementation coded index. */
    private enum class Implementation(val id: Int) {
        FILE(0),
        ASSEMBLY_REF(1),
        EXPORTED_TYPE(2),
    }

    /** Tag values of the ECMA-335 MemberForwarded coded index. */
    private enum class MemberForwarded(val id: Int) {
        FIELD(0),
        METHOD_DEF(1),
    }

    /** Tag values of the ECMA-335 MemberRefParent coded index. */
    private enum class MemberRefParent(val id: Int) {
        TYPE_DEF(0),
        TYPE_REF(1),
        MODULE_REF(2),
        METHOD_DEF(3),
        TYPE_SPEC(4),
    }

    /** Tag values of the ECMA-335 MethodDefOrRef coded index. */
    private enum class MethodDefOrRef(val id: Int) {
        METHOD_DEF(0),
        MEMBER_REF(1),
    }

    /** Tag values of the ECMA-335 ResolutionScope coded index. */
    private enum class ResolutionScope(val id: Int) {
        MODULE(0),
        MODULE_REF(1),
        ASSEMBLY_REF(2),
        TYPE_REF(3),
    }

    /** Tag values of the ECMA-335 TypeDefOrRef coded index. */
    private enum class TypeDefOrRef(val id: Int) {
        TYPE_DEF(0),
        TYPE_REF(1),
    }

    /** Tag values of the ECMA-335 TypeDefOrRefOrSpec coded index. */
    private enum class TypeDefOrRefOrSpec(val id: Int) {
        TYPE_DEF(0),
        TYPE_REF(1),
        TYPE_SPEC(2),
    }

    /** Tag values of the ECMA-335 TypeOrMethodDef coded index. */
    private enum class TypeOrMethodDef(val id: Int) {
        TYPE_DEF(0),
        METHOD_DEF(1),
    }
}
