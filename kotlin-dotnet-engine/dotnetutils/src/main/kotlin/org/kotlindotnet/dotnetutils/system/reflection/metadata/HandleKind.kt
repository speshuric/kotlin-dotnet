// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (HandleKind.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata

/**
 * Values are derived from [org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.HandleType]
 * (table indexes in the byte range; heap handles occupy 0x70..0x7C).
 * Note that the highest bit is reserved for the virtual bit on Handle.
 */
enum class HandleKind(val value: UByte) {
    MODULE_DEFINITION(0x00u),
    TYPE_REFERENCE(0x01u),
    TYPE_DEFINITION(0x02u),
    FIELD_DEFINITION(0x04u),
    METHOD_DEFINITION(0x06u),
    PARAMETER(0x08u),
    INTERFACE_IMPLEMENTATION(0x09u),
    MEMBER_REFERENCE(0x0Au),
    CONSTANT(0x0Bu),
    CUSTOM_ATTRIBUTE(0x0Cu),
    DECLARATIVE_SECURITY_ATTRIBUTE(0x0Eu),
    STANDALONE_SIGNATURE(0x11u),
    EVENT_DEFINITION(0x14u),
    PROPERTY_DEFINITION(0x17u),
    METHOD_IMPLEMENTATION(0x19u),
    MODULE_REFERENCE(0x1Au),
    TYPE_SPECIFICATION(0x1Bu),
    ASSEMBLY_DEFINITION(0x20u),
    ASSEMBLY_FILE(0x26u),
    ASSEMBLY_REFERENCE(0x23u),
    EXPORTED_TYPE(0x27u),
    GENERIC_PARAMETER(0x2Au),
    METHOD_SPECIFICATION(0x2Bu),
    GENERIC_PARAMETER_CONSTRAINT(0x2Cu),
    MANIFEST_RESOURCE(0x28u),

    // Debug handles
    DOCUMENT(0x30u),
    METHOD_DEBUG_INFORMATION(0x31u),
    LOCAL_SCOPE(0x32u),
    LOCAL_VARIABLE(0x33u),
    LOCAL_CONSTANT(0x34u),
    IMPORT_SCOPE(0x35u),
    CUSTOM_DEBUG_INFORMATION(0x37u),

    // Heap handles
    NAMESPACE_DEFINITION(0x7Cu),
    USER_STRING(0x70u),
    STRING(0x78u),
    BLOB(0x71u),
    GUID(0x72u);

    companion object {
        private val byValue: Map<UByte, HandleKind> = entries.associateBy { it.value }

        internal fun fromRaw(raw: UByte): HandleKind? = byValue[raw]
    }
}

/** Port of `HandleKindExtensions.IsHeapHandle`. */
internal fun HandleKind.isHeapHandle(): Boolean = this >= HandleKind.NAMESPACE_DEFINITION
