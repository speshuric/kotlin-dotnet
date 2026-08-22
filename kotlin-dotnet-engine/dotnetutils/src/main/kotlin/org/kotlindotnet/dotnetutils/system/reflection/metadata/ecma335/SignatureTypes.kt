// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Signatures/Signature*.cs,
// SerializationTypeCode.cs, PrimitiveSerializationTypeCode.cs,
// PrimitiveTypeCode.cs, CustomAttributeNamedArgumentKind.cs and
// Ecma335/Encoding/FunctionPointerAttributes.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

/** Which signature a blob represents (low nibble of the header byte). */
enum class SignatureKind(val value: Int) {
    METHOD(0x0),
    FIELD(0x6),
    LOCAL_VARIABLES(0x7),
    PROPERTY(0x8),
    METHOD_SPECIFICATION(0xA),
}

/** Extra attributes encoded in the high nibble of the signature header byte. */
enum class SignatureAttributes(val value: Int) {
    NONE(0x00),
    GENERIC(0x10),
    INSTANCE(0x20),
    EXPLICIT_THIS(0x40),
}

enum class SignatureCallingConvention(val value: Int) {
    DEFAULT(0x0),
    C_DECL(0x1),
    STD_CALL(0x2),
    THIS_CALL(0x3),
    FAST_CALL(0x4),
    VAR_ARGS(0x5),
    UNMANAGED(0x9),
}

enum class SignatureTypeCode(val value: Int) {
    INVALID(0x00),
    VOID(0x01),
    BOOLEAN(0x02),
    CHAR(0x03),
    S_BYTE(0x04),
    BYTE(0x05),
    INT_16(0x06),
    UINT_16(0x07),
    INT_32(0x08),
    UINT_32(0x09),
    INT_64(0x0a),
    UINT_64(0x0b),
    SINGLE(0x0c),
    DOUBLE(0x0d),
    STRING(0x0e),
    POINTER(0x0f), // PTR <type>
    BY_REFERENCE(0x10), // BYREF <type>
    GENERIC_TYPE_PARAMETER(0x13), // VAR <number>
    ARRAY(0x14), // MDARRAY <type> <rank> <bcount> <bound1> ... <lbcount> <lb1> ...
    GENERIC_TYPE_INSTANCE(0x15), // GENERICINST <generic type> <argCnt> <arg1> ... <argn>
    TYPED_REFERENCE(0x16), // TYPEDREF (it takes no args)
    INT_PTR(0x18), // native integer size
    UINT_PTR(0x19), // native unsigned integer size
    FUNCTION_POINTER(0x1b), // FNPTR <complete sig>
    OBJECT(0x1c), // shortcut for System.Object
    SZ_ARRAY(0x1d), // single dimension zero lower bound array
    GENERIC_METHOD_PARAMETER(0x1e), // MVAR <number>
    REQUIRED_MODIFIER(0x1f), // CMOD_REQD <mdTypeRef/mdTypeDef>
    OPTIONAL_MODIFIER(0x20), // CMOD_OPT <mdTypeRef/mdTypeDef>
    TYPE_HANDLE(0x40), // CLASS | VALUETYPE <class Token>
    SENTINEL(0x41), // sentinel for varargs
    PINNED(0x45),
}

enum class SignatureTypeKind(val value: Int) {
    UNKNOWN(0x00),
    CLASS(0x12),
    VALUE_TYPE(0x11),
}

/** Type codes used in custom attribute blobs. */
enum class SerializationTypeCode(val value: Int) {
    INVALID(SignatureTypeCode.INVALID.value),
    BOOLEAN(SignatureTypeCode.BOOLEAN.value),
    CHAR(SignatureTypeCode.CHAR.value),
    S_BYTE(SignatureTypeCode.S_BYTE.value),
    BYTE(SignatureTypeCode.BYTE.value),
    SINGLE(SignatureTypeCode.SINGLE.value),
    DOUBLE(SignatureTypeCode.DOUBLE.value),
    STRING(SignatureTypeCode.STRING.value),
    INT_16(SignatureTypeCode.INT_16.value),
    UINT_16(SignatureTypeCode.UINT_16.value),
    INT_32(SignatureTypeCode.INT_32.value),
    UINT_32(SignatureTypeCode.UINT_32.value),
    INT_64(SignatureTypeCode.INT_64.value),
    UINT_64(SignatureTypeCode.UINT_64.value),
    SZ_ARRAY(SignatureTypeCode.SZ_ARRAY.value),
    TYPE(0x50),
    TAGGED_OBJECT(0x51),
    ENUM_VALUE(0x55),
}

/** Primitive subset of [SerializationTypeCode]. */
enum class PrimitiveSerializationTypeCode(val value: Int) {
    BOOLEAN(SignatureTypeCode.BOOLEAN.value),
    BYTE(SignatureTypeCode.BYTE.value),
    S_BYTE(SignatureTypeCode.S_BYTE.value),
    CHAR(SignatureTypeCode.CHAR.value),
    INT_16(SignatureTypeCode.INT_16.value),
    UINT_16(SignatureTypeCode.UINT_16.value),
    INT_32(SignatureTypeCode.INT_32.value),
    UINT_32(SignatureTypeCode.UINT_32.value),
    INT_64(SignatureTypeCode.INT_64.value),
    UINT_64(SignatureTypeCode.UINT_64.value),
    SINGLE(SignatureTypeCode.SINGLE.value),
    DOUBLE(SignatureTypeCode.DOUBLE.value),
    STRING(SignatureTypeCode.STRING.value),
}

/** Primitive subset of [SignatureTypeCode] (plus [PrimitiveTypeCode.VOID]). */
enum class PrimitiveTypeCode(val value: Int) {
    BOOLEAN(SignatureTypeCode.BOOLEAN.value),
    BYTE(SignatureTypeCode.BYTE.value),
    S_BYTE(SignatureTypeCode.S_BYTE.value),
    CHAR(SignatureTypeCode.CHAR.value),
    INT_16(SignatureTypeCode.INT_16.value),
    UINT_16(SignatureTypeCode.UINT_16.value),
    INT_32(SignatureTypeCode.INT_32.value),
    UINT_32(SignatureTypeCode.UINT_32.value),
    INT_64(SignatureTypeCode.INT_64.value),
    UINT_64(SignatureTypeCode.UINT_64.value),
    SINGLE(SignatureTypeCode.SINGLE.value),
    DOUBLE(SignatureTypeCode.DOUBLE.value),
    INT_PTR(SignatureTypeCode.INT_PTR.value),
    UINT_PTR(SignatureTypeCode.UINT_PTR.value),
    OBJECT(SignatureTypeCode.OBJECT.value),
    STRING(SignatureTypeCode.STRING.value),
    TYPED_REFERENCE(SignatureTypeCode.TYPED_REFERENCE.value),
    VOID(SignatureTypeCode.VOID.value),
}

/** Kind of a named argument in a custom attribute blob. */
enum class CustomAttributeNamedArgumentKind(val value: Int) {
    FIELD(0x53),
    PROPERTY(0x54),
}

/**
 * Attributes of a function pointer signature ([SignatureTypeEncoder.functionPointer]).
 *
 * Deviation: C# derives the values from [SignatureAttributes]; here they are
 * spelled out with identical numeric values.
 */
enum class FunctionPointerAttributes(val value: Int) {
    NONE(0x00),
    HAS_THIS(0x20), // SignatureAttributes.INSTANCE
    HAS_EXPLICIT_THIS(0x60), // INSTANCE or EXPLICIT_THIS
}
