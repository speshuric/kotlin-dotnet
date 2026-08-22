// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (Ecma335/Encoding/BlobEncoders.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - C# `out`-parameter overloads are replaced by lambda-callback overloads
//    (the C# Action-based overloads); the encoders returned by simple factory
//    methods are kept as plain returns;
//  - methods named after Kotlin keywords are renamed: Enum() -> enumType(),
//    Object() -> objectType(), Array() -> array();
//  - ImmutableArray<int> parameters of ArrayShapeEncoder.shape() are IntArray?
//    (null means "default", i.e. all lower bounds 0).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl

/** Root of all signature/attribute blob encoders. */
class BlobEncoder(val builder: BlobBuilder) {
    /**
     * Encodes Field Signature blob, with additional support for encoding ref
     * fields, custom modifiers and typed references.
     */
    fun field(): FieldTypeEncoder {
        builder.writeByte(SignatureKind.FIELD.value.toByte())
        return FieldTypeEncoder(builder)
    }

    /**
     * Encodes Field Signature blob.
     *
     * To encode byref fields, custom modifiers or typed references use [field]
     * instead.
     */
    fun fieldSignature(): SignatureTypeEncoder = field().type(isByRef = false)

    /**
     * Encodes Method Specification Signature blob.
     *
     * @throws IllegalArgumentException [genericArgumentCount] is not in range [0, 0xffff].
     */
    fun methodSpecificationSignature(genericArgumentCount: Int): GenericTypeArgumentsEncoder {
        require(genericArgumentCount in 0..0xffff) { "genericArgumentCount out of range" }

        builder.writeByte(SignatureKind.METHOD_SPECIFICATION.value.toByte())
        builder.writeCompressedInteger(genericArgumentCount)

        return GenericTypeArgumentsEncoder(builder)
    }

    /**
     * Encodes Method Signature blob.
     *
     * @throws IllegalArgumentException [genericParameterCount] is not in range [0, 0xffff].
     */
    fun methodSignature(
        convention: SignatureCallingConvention = SignatureCallingConvention.DEFAULT,
        genericParameterCount: Int = 0,
        isInstanceMethod: Boolean = false,
    ): MethodSignatureEncoder {
        require(genericParameterCount in 0..0xffff) { "genericParameterCount out of range" }

        val attributes =
            (if (genericParameterCount != 0) SignatureAttributes.GENERIC.value else 0) or
                (if (isInstanceMethod) SignatureAttributes.INSTANCE.value else 0)

        builder.writeByte(
            SignatureHeader(SignatureKind.METHOD, convention, attributes).rawValue,
        )

        if (genericParameterCount != 0) {
            builder.writeCompressedInteger(genericParameterCount)
        }

        return MethodSignatureEncoder(builder, hasVarArgs = convention == SignatureCallingConvention.VAR_ARGS)
    }

    /** Encodes Property Signature blob. */
    fun propertySignature(isInstanceProperty: Boolean = false): MethodSignatureEncoder {
        builder.writeByte(
            SignatureHeader(
                SignatureKind.PROPERTY,
                SignatureCallingConvention.DEFAULT,
                if (isInstanceProperty) SignatureAttributes.INSTANCE.value else 0,
            ).rawValue,
        )
        return MethodSignatureEncoder(builder, hasVarArgs = false)
    }

    /**
     * Encodes Custom Attribute Signature blob. The callbacks must encode fixed
     * arguments first and named arguments second, in the order they appear in
     * the parameter list.
     */
    fun customAttributeSignature(
        fixedArguments: (FixedArgumentsEncoder) -> Unit,
        namedArguments: (CustomAttributeNamedArgumentsEncoder) -> Unit,
    ) {
        builder.writeUInt16(0x0001.toUShort())

        fixedArguments(FixedArgumentsEncoder(builder))
        namedArguments(CustomAttributeNamedArgumentsEncoder(builder))
    }

    /**
     * Encodes Local Variable Signature.
     *
     * @throws IllegalArgumentException [variableCount] is not in range [0, 0x1fffffff].
     */
    fun localVariableSignature(variableCount: Int): LocalVariablesEncoder {
        require(variableCount in 0..BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE) { "variableCount out of range" }

        builder.writeByte(SignatureKind.LOCAL_VARIABLES.value.toByte())
        builder.writeCompressedInteger(variableCount)
        return LocalVariablesEncoder(builder)
    }

    /**
     * Encodes Type Specification Signature.
     *
     * @return Type encoder of the structured type represented by the Type
     *   Specification (it shall not encode a primitive type).
     */
    fun typeSpecificationSignature(): SignatureTypeEncoder = SignatureTypeEncoder(builder)

    /**
     * Encodes a Permission Set blob.
     *
     * @throws IllegalArgumentException [attributeCount] is not in range [0, 0x1fffffff].
     */
    fun permissionSetBlob(attributeCount: Int): PermissionSetEncoder {
        require(attributeCount in 0..BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE) { "attributeCount out of range" }

        builder.writeByte('.'.code.toByte())
        builder.writeCompressedInteger(attributeCount)
        return PermissionSetEncoder(builder)
    }

    /**
     * Encodes Permission Set arguments.
     *
     * @throws IllegalArgumentException [argumentCount] is not in range [0, 0x1fffffff].
     */
    fun permissionSetArguments(argumentCount: Int): NamedArgumentsEncoder {
        require(argumentCount in 0..BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE) { "argumentCount out of range" }

        builder.writeCompressedInteger(argumentCount)
        return NamedArgumentsEncoder(builder)
    }
}

class MethodSignatureEncoder(
    val builder: BlobBuilder,
    val hasVarArgs: Boolean,
) {
    /**
     * Encodes return type and parameters. The [returnType] callback must run
     * before [parameters].
     *
     * @throws IllegalArgumentException [parameterCount] is out of compressed-integer range.
     */
    fun parameters(
        parameterCount: Int,
        returnType: (ReturnTypeEncoder) -> Unit,
        parameters: (ParametersEncoder) -> Unit,
    ) {
        require(parameterCount in 0..BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE) { "parameterCount out of range" }

        builder.writeCompressedInteger(parameterCount)

        returnType(ReturnTypeEncoder(builder))
        parameters(ParametersEncoder(builder, hasVarArgs = hasVarArgs))
    }
}

class LocalVariablesEncoder(val builder: BlobBuilder) {
    fun addVariable(): LocalVariableTypeEncoder = LocalVariableTypeEncoder(builder)
}

class LocalVariableTypeEncoder(val builder: BlobBuilder) {
    fun customModifiers(): CustomModifiersEncoder = CustomModifiersEncoder(builder)

    fun type(isByRef: Boolean = false, isPinned: Boolean = false): SignatureTypeEncoder {
        if (isPinned) {
            builder.writeByte(SignatureTypeCode.PINNED.value.toByte())
        }

        if (isByRef) {
            builder.writeByte(SignatureTypeCode.BY_REFERENCE.value.toByte())
        }

        return SignatureTypeEncoder(builder)
    }

    fun typedReference() {
        builder.writeByte(SignatureTypeCode.TYPED_REFERENCE.value.toByte())
    }
}

class ParameterTypeEncoder(val builder: BlobBuilder) {
    fun customModifiers(): CustomModifiersEncoder = CustomModifiersEncoder(builder)

    fun type(isByRef: Boolean = false): SignatureTypeEncoder {
        if (isByRef) {
            builder.writeByte(SignatureTypeCode.BY_REFERENCE.value.toByte())
        }

        return SignatureTypeEncoder(builder)
    }

    fun typedReference() {
        builder.writeByte(SignatureTypeCode.TYPED_REFERENCE.value.toByte())
    }
}

class PermissionSetEncoder(val builder: BlobBuilder) {
    fun addPermission(typeName: String, encodedArguments: ByteArray): PermissionSetEncoder {
        require(encodedArguments.size <= BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE) { "encodedArguments too large" }

        builder.writeSerializedString(typeName)
        builder.writeCompressedInteger(encodedArguments.size)
        builder.writeBytes(encodedArguments)
        return this
    }

    fun addPermission(typeName: String, encodedArguments: BlobBuilder): PermissionSetEncoder {
        require(encodedArguments.count <= BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE) { "encodedArguments too large" }

        builder.writeSerializedString(typeName)
        builder.writeCompressedInteger(encodedArguments.count)
        encodedArguments.writeContentTo(builder)
        return this
    }
}

class GenericTypeArgumentsEncoder(val builder: BlobBuilder) {
    fun addArgument(): SignatureTypeEncoder = SignatureTypeEncoder(builder)
}

class FieldTypeEncoder(val builder: BlobBuilder) {
    fun customModifiers(): CustomModifiersEncoder = CustomModifiersEncoder(builder)

    fun type(isByRef: Boolean = false): SignatureTypeEncoder {
        if (isByRef) {
            builder.writeByte(SignatureTypeCode.BY_REFERENCE.value.toByte())
        }

        return SignatureTypeEncoder(builder)
    }

    fun typedReference() {
        builder.writeByte(SignatureTypeCode.TYPED_REFERENCE.value.toByte())
    }
}

class FixedArgumentsEncoder(val builder: BlobBuilder) {
    fun addArgument(): LiteralEncoder = LiteralEncoder(builder)
}

class LiteralEncoder(val builder: BlobBuilder) {
    fun vector(): VectorEncoder = VectorEncoder(builder)

    /** Encodes the type and the items of a vector literal; [arrayType] runs before [vector]. */
    fun taggedVector(
        arrayType: (CustomAttributeArrayTypeEncoder) -> Unit,
        vector: (VectorEncoder) -> Unit,
    ) {
        arrayType(CustomAttributeArrayTypeEncoder(builder))
        vector(VectorEncoder(builder))
    }

    /** Encodes a scalar literal. */
    fun scalar(): ScalarEncoder = ScalarEncoder(builder)

    /** Encodes the type and the value of a literal; [type] runs before [scalar]. */
    fun taggedScalar(
        type: (CustomAttributeElementTypeEncoder) -> Unit,
        scalar: (ScalarEncoder) -> Unit,
    ) {
        type(CustomAttributeElementTypeEncoder(builder))
        scalar(ScalarEncoder(builder))
    }
}

class ScalarEncoder(val builder: BlobBuilder) {
    /** Encodes `null` literal of type Array. */
    fun nullArray() {
        builder.writeInt32(-1)
    }

    /**
     * Encodes constant literal: Boolean/Byte/Short/UShort/Int/UInt/Long/ULong/
     * Float/Double/Char constants, String (encoded as SerString), or an enum
     * value encoded as its underlying integer.
     */
    fun constant(value: Any?) {
        if (value == null || value is String) {
            string(value as? String)
        } else {
            builder.writeConstant(value)
        }
    }

    /** Encodes literal of type Type (possibly null). */
    fun systemType(serializedTypeName: String?) {
        check(serializedTypeName == null || serializedTypeName.isNotEmpty()) { "serializedTypeName is empty" }

        string(serializedTypeName)
    }

    private fun string(value: String?) {
        builder.writeSerializedString(value)
    }
}

class LiteralsEncoder(val builder: BlobBuilder) {
    fun addLiteral(): LiteralEncoder = LiteralEncoder(builder)
}

class VectorEncoder(val builder: BlobBuilder) {
    fun count(count: Int): LiteralsEncoder {
        require(count >= 0) { "count out of range" }

        builder.writeUInt32(count.toUInt())
        return LiteralsEncoder(builder)
    }
}

class NameEncoder(val builder: BlobBuilder) {
    fun name(name: String) {
        check(name.isNotEmpty()) { "name is empty" }

        builder.writeSerializedString(name)
    }
}

class CustomAttributeNamedArgumentsEncoder(val builder: BlobBuilder) {
    fun count(count: Int): NamedArgumentsEncoder {
        require(count in 0..0xffff) { "count out of range" }

        builder.writeUInt16(count.toUShort())
        return NamedArgumentsEncoder(builder)
    }
}

class NamedArgumentsEncoder(val builder: BlobBuilder) {
    /**
     * Encodes a named argument (field or property). The callbacks must run in
     * order: [type], then [name], then [literal].
     */
    fun addArgument(
        isField: Boolean,
        type: (NamedArgumentTypeEncoder) -> Unit,
        name: (NameEncoder) -> Unit,
        literal: (LiteralEncoder) -> Unit,
    ) {
        builder.writeByte(
            (if (isField) CustomAttributeNamedArgumentKind.FIELD.value else CustomAttributeNamedArgumentKind.PROPERTY.value)
                .toByte(),
        )
        type(NamedArgumentTypeEncoder(builder))
        name(NameEncoder(builder))
        literal(LiteralEncoder(builder))
    }
}

class NamedArgumentTypeEncoder(val builder: BlobBuilder) {
    fun scalarType(): CustomAttributeElementTypeEncoder = CustomAttributeElementTypeEncoder(builder)

    fun objectType() {
        builder.writeByte(SerializationTypeCode.TAGGED_OBJECT.value.toByte())
    }

    fun szArray(): CustomAttributeArrayTypeEncoder = CustomAttributeArrayTypeEncoder(builder)
}

class CustomAttributeArrayTypeEncoder(val builder: BlobBuilder) {
    fun objectArray() {
        builder.writeByte(SerializationTypeCode.SZ_ARRAY.value.toByte())
        builder.writeByte(SerializationTypeCode.TAGGED_OBJECT.value.toByte())
    }

    fun elementType(): CustomAttributeElementTypeEncoder {
        builder.writeByte(SerializationTypeCode.SZ_ARRAY.value.toByte())
        return CustomAttributeElementTypeEncoder(builder)
    }
}

class CustomAttributeElementTypeEncoder(val builder: BlobBuilder) {
    private fun writeTypeCode(value: SerializationTypeCode) {
        builder.writeByte(value.value.toByte())
    }

    fun boolean() = writeTypeCode(SerializationTypeCode.BOOLEAN)
    fun char() = writeTypeCode(SerializationTypeCode.CHAR)
    fun sByte() = writeTypeCode(SerializationTypeCode.S_BYTE)
    fun byte() = writeTypeCode(SerializationTypeCode.BYTE)
    fun int16() = writeTypeCode(SerializationTypeCode.INT_16)
    fun uint16() = writeTypeCode(SerializationTypeCode.UINT_16)
    fun int32() = writeTypeCode(SerializationTypeCode.INT_32)
    fun uint32() = writeTypeCode(SerializationTypeCode.UINT_32)
    fun int64() = writeTypeCode(SerializationTypeCode.INT_64)
    fun uint64() = writeTypeCode(SerializationTypeCode.UINT_64)
    fun single() = writeTypeCode(SerializationTypeCode.SINGLE)
    fun double() = writeTypeCode(SerializationTypeCode.DOUBLE)
    fun string() = writeTypeCode(SerializationTypeCode.STRING)

    fun primitiveType(type: PrimitiveSerializationTypeCode) {
        when (type) {
            PrimitiveSerializationTypeCode.BOOLEAN,
            PrimitiveSerializationTypeCode.BYTE,
            PrimitiveSerializationTypeCode.S_BYTE,
            PrimitiveSerializationTypeCode.CHAR,
            PrimitiveSerializationTypeCode.INT_16,
            PrimitiveSerializationTypeCode.UINT_16,
            PrimitiveSerializationTypeCode.INT_32,
            PrimitiveSerializationTypeCode.UINT_32,
            PrimitiveSerializationTypeCode.INT_64,
            PrimitiveSerializationTypeCode.UINT_64,
            PrimitiveSerializationTypeCode.SINGLE,
            PrimitiveSerializationTypeCode.DOUBLE,
            PrimitiveSerializationTypeCode.STRING,
            -> writeTypeCode(SerializationTypeCode.entries.first { it.value == type.value })

            else -> throw IllegalArgumentException("type out of range")
        }
    }

    fun systemType() {
        writeTypeCode(SerializationTypeCode.TYPE)
    }

    fun enumType(enumTypeName: String) {
        check(enumTypeName.isNotEmpty()) { "enumTypeName is empty" }

        writeTypeCode(SerializationTypeCode.ENUM_VALUE)
        builder.writeSerializedString(enumTypeName)
    }
}

/** Encodes a type in a signature. */
class SignatureTypeEncoder(val builder: BlobBuilder) {
    private fun writeTypeCode(value: SignatureTypeCode) {
        builder.writeByte(value.value.toByte())
    }

    private fun classOrValue(isValueType: Boolean) {
        builder.writeByte(
            (if (isValueType) SignatureTypeKind.VALUE_TYPE.value else SignatureTypeKind.CLASS.value).toByte(),
        )
    }

    fun boolean() = writeTypeCode(SignatureTypeCode.BOOLEAN)
    fun char() = writeTypeCode(SignatureTypeCode.CHAR)
    fun sByte() = writeTypeCode(SignatureTypeCode.S_BYTE)
    fun byte() = writeTypeCode(SignatureTypeCode.BYTE)
    fun int16() = writeTypeCode(SignatureTypeCode.INT_16)
    fun uint16() = writeTypeCode(SignatureTypeCode.UINT_16)
    fun int32() = writeTypeCode(SignatureTypeCode.INT_32)
    fun uint32() = writeTypeCode(SignatureTypeCode.UINT_32)
    fun int64() = writeTypeCode(SignatureTypeCode.INT_64)
    fun uint64() = writeTypeCode(SignatureTypeCode.UINT_64)
    fun single() = writeTypeCode(SignatureTypeCode.SINGLE)
    fun double() = writeTypeCode(SignatureTypeCode.DOUBLE)
    fun string() = writeTypeCode(SignatureTypeCode.STRING)
    fun typedReference() = writeTypeCode(SignatureTypeCode.TYPED_REFERENCE)
    fun intPtr() = writeTypeCode(SignatureTypeCode.INT_PTR)
    fun uIntPtr() = writeTypeCode(SignatureTypeCode.UINT_PTR)
    fun objectType() = writeTypeCode(SignatureTypeCode.OBJECT)

    /**
     * Encodes a primitive type.
     *
     * @throws IllegalArgumentException [type] is not valid in this context (e.g. VOID).
     */
    fun primitiveType(type: PrimitiveTypeCode) {
        when (type) {
            PrimitiveTypeCode.BOOLEAN,
            PrimitiveTypeCode.BYTE,
            PrimitiveTypeCode.S_BYTE,
            PrimitiveTypeCode.CHAR,
            PrimitiveTypeCode.INT_16,
            PrimitiveTypeCode.UINT_16,
            PrimitiveTypeCode.INT_32,
            PrimitiveTypeCode.UINT_32,
            PrimitiveTypeCode.INT_64,
            PrimitiveTypeCode.UINT_64,
            PrimitiveTypeCode.SINGLE,
            PrimitiveTypeCode.DOUBLE,
            PrimitiveTypeCode.TYPED_REFERENCE,
            PrimitiveTypeCode.INT_PTR,
            PrimitiveTypeCode.UINT_PTR,
            PrimitiveTypeCode.STRING,
            PrimitiveTypeCode.OBJECT,
            -> builder.writeByte(type.value.toByte())

            else -> throw IllegalArgumentException("type out of range")
        }
    }

    /** Starts encoding an array type; [elementType] runs before [arrayShape]. */
    fun array(
        elementType: (SignatureTypeEncoder) -> Unit,
        arrayShape: (ArrayShapeEncoder) -> Unit,
    ) {
        builder.writeByte(SignatureTypeCode.ARRAY.value.toByte())
        elementType(this)
        arrayShape(ArrayShapeEncoder(builder))
    }

    /**
     * Encodes a reference to a type ([org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle] or
     * [org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeReferenceHandle]).
     *
     * @throws IllegalArgumentException [type] doesn't have the expected handle kind.
     */
    fun type(type: EntityHandle, isValueType: Boolean) {
        // Get the coded index before we start writing anything (might throw):
        // Note: TypeSpec is not allowed here.
        val codedIndex = CodedIndex.typeDefOrRef(type)

        classOrValue(isValueType)
        builder.writeCompressedInteger(codedIndex)
    }

    /**
     * Starts encoding a function pointer signature.
     *
     * @throws IllegalArgumentException [attributes] is invalid.
     * @throws IllegalArgumentException [genericParameterCount] is not in range [0, 0xffff].
     */
    fun functionPointer(
        convention: SignatureCallingConvention = SignatureCallingConvention.DEFAULT,
        attributes: FunctionPointerAttributes = FunctionPointerAttributes.NONE,
        genericParameterCount: Int = 0,
    ): MethodSignatureEncoder {
        // Spec:
        // The EXPLICITTHIS (0x40) bit can be set only in signatures for function pointers.
        // If EXPLICITTHIS (0x40) in the signature is set, then HASTHIS (0x20) shall also be set.
        check(
            attributes == FunctionPointerAttributes.NONE ||
                attributes == FunctionPointerAttributes.HAS_THIS ||
                attributes == FunctionPointerAttributes.HAS_EXPLICIT_THIS,
        ) { "invalid signature attributes" }

        require(genericParameterCount in 0..0xffff) { "genericParameterCount out of range" }

        builder.writeByte(SignatureTypeCode.FUNCTION_POINTER.value.toByte())
        builder.writeByte(
            SignatureHeader(SignatureKind.METHOD, convention, attributes.value).rawValue,
        )

        if (genericParameterCount != 0) {
            builder.writeCompressedInteger(genericParameterCount)
        }

        return MethodSignatureEncoder(builder, hasVarArgs = convention == SignatureCallingConvention.VAR_ARGS)
    }

    /**
     * Starts encoding a generic instantiation signature.
     *
     * @throws IllegalArgumentException [genericArgumentCount] is not in range [1, 0xffff].
     */
    fun genericInstantiation(
        genericType: EntityHandle,
        genericArgumentCount: Int,
        isValueType: Boolean,
    ): GenericTypeArgumentsEncoder {
        require(genericArgumentCount - 1 in 0..0xffff - 1) { "genericArgumentCount out of range" }

        // Get the coded index before we start writing anything (might throw).
        val codedIndex = CodedIndex.typeDefOrRef(genericType)

        builder.writeByte(SignatureTypeCode.GENERIC_TYPE_INSTANCE.value.toByte())
        classOrValue(isValueType)
        builder.writeCompressedInteger(codedIndex)
        builder.writeCompressedInteger(genericArgumentCount)
        return GenericTypeArgumentsEncoder(builder)
    }

    /** Encodes a reference to type parameter of a containing generic method. */
    fun genericMethodTypeParameter(parameterIndex: Int) {
        require(parameterIndex in 0..0xffff) { "parameterIndex out of range" }

        builder.writeByte(SignatureTypeCode.GENERIC_METHOD_PARAMETER.value.toByte())
        builder.writeCompressedInteger(parameterIndex)
    }

    /** Encodes a reference to type parameter of a containing generic type. */
    fun genericTypeParameter(parameterIndex: Int) {
        require(parameterIndex in 0..0xffff) { "parameterIndex out of range" }

        builder.writeByte(SignatureTypeCode.GENERIC_TYPE_PARAMETER.value.toByte())
        builder.writeCompressedInteger(parameterIndex)
    }

    /** Starts encoding a pointer signature. */
    fun pointer(): SignatureTypeEncoder {
        builder.writeByte(SignatureTypeCode.POINTER.value.toByte())
        return this
    }

    /** Encodes `void*`. */
    fun voidPointer() {
        builder.writeByte(SignatureTypeCode.POINTER.value.toByte())
        builder.writeByte(SignatureTypeCode.VOID.value.toByte())
    }

    /** Starts encoding an SZ array (vector) signature. */
    fun szArray(): SignatureTypeEncoder {
        builder.writeByte(SignatureTypeCode.SZ_ARRAY.value.toByte())
        return this
    }

    /** Starts encoding a signature of a type with custom modifiers. */
    fun customModifiers(): CustomModifiersEncoder = CustomModifiersEncoder(builder)
}

class CustomModifiersEncoder(val builder: BlobBuilder) {
    /**
     * Encodes a custom modifier.
     *
     * @param type TypeDefinitionHandle, TypeReferenceHandle or TypeSpecificationHandle.
     * @param isOptional Is optional modifier.
     * @return Encoder of subsequent modifiers.
     * @throws IllegalArgumentException [type] is nil or of an unexpected kind.
     */
    fun addModifier(type: EntityHandle, isOptional: Boolean): CustomModifiersEncoder {
        check(!type.isNil) { "type handle is nil" }

        // Get the coded index before we start writing anything (might throw):
        val codedIndex = CodedIndex.typeDefOrRefOrSpec(type)

        if (isOptional) {
            builder.writeByte(SignatureTypeCode.OPTIONAL_MODIFIER.value.toByte())
        } else {
            builder.writeByte(SignatureTypeCode.REQUIRED_MODIFIER.value.toByte())
        }

        builder.writeCompressedInteger(codedIndex)
        return this
    }
}

class ArrayShapeEncoder(val builder: BlobBuilder) {
    /**
     * Encodes array shape.
     *
     * @param rank The number of dimensions in the array (shall be 1 or more).
     * @param sizes Dimension sizes. May be shorter than [rank] but not longer.
     * @param lowerBounds Dimension lower bounds, or null to set all lower
     *   bounds to 0. May be shorter than [rank] but not longer.
     */
    fun shape(rank: Int, sizes: IntArray, lowerBounds: IntArray? = null) {
        // The CLR supports <64 dimensions; more than 0xffff crashes tools (ildasm).
        require(rank - 1 in 0..0xffff - 1) { "rank out of range" }

        // rank
        builder.writeCompressedInteger(rank)

        // sizes
        require(sizes.size <= rank) { "sizes longer than rank" }

        builder.writeCompressedInteger(sizes.size)
        for (size in sizes) {
            builder.writeCompressedInteger(size)
        }

        // lower bounds
        if (lowerBounds == null) {
            builder.writeCompressedInteger(rank)
            for (i in 0 until rank) {
                builder.writeCompressedSignedInteger(0)
            }
        } else {
            require(lowerBounds.size <= rank) { "lowerBounds longer than rank" }

            builder.writeCompressedInteger(lowerBounds.size)
            for (lowerBound in lowerBounds) {
                builder.writeCompressedSignedInteger(lowerBound)
            }
        }
    }
}

class ReturnTypeEncoder(val builder: BlobBuilder) {
    fun customModifiers(): CustomModifiersEncoder = CustomModifiersEncoder(builder)

    fun type(isByRef: Boolean = false): SignatureTypeEncoder {
        if (isByRef) {
            builder.writeByte(SignatureTypeCode.BY_REFERENCE.value.toByte())
        }

        return SignatureTypeEncoder(builder)
    }

    fun typedReference() {
        builder.writeByte(SignatureTypeCode.TYPED_REFERENCE.value.toByte())
    }

    fun void() {
        builder.writeByte(SignatureTypeCode.VOID.value.toByte())
    }
}

class ParametersEncoder(val builder: BlobBuilder, val hasVarArgs: Boolean = false) {
    fun addParameter(): ParameterTypeEncoder = ParameterTypeEncoder(builder)

    fun startVarArgs(): ParametersEncoder {
        check(hasVarArgs) { "signature does not have varargs" }

        builder.writeByte(SignatureTypeCode.SENTINEL.value.toByte())
        return ParametersEncoder(builder, hasVarArgs = false)
    }
}
