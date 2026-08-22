// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// Ported from System.Reflection.Metadata (tests/Metadata/Ecma335/Encoding/
// BlobEncodersTests.cs).
// Origin: dotnet/runtime release/10.0, MIT License (c) .NET Foundation
// See adr/0009-srm-port-to-kotlin.md for porting conventions.
//
// Deviations:
//  - C# out-parameter overloads are tested through the Kotlin lambda-callback
//    overloads;
//  - ScalarEncoder.constant of an enum value is not covered: Kotlin enums are
//    not supported by BlobWriterImpl.writeConstant (callers pass the raw
//    underlying value instead).

package org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MAX_COMPRESSED_INTEGER_VALUE
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MAX_SIGNED_COMPRESSED_INTEGER_VALUE
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobWriterImpl.MIN_SIGNED_COMPRESSED_INTEGER_VALUE
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlobEncodersTests {
    private fun assertBytes(b: BlobBuilder, vararg expected: Int) {
        assertContentEquals(expected.map { it.toByte() }.toByteArray(), b.toArray())
    }

    @Test
    fun blobEncoder_FieldSignature() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)
        assertEquals(b, e.builder)

        val s = e.fieldSignature()
        assertBytes(b, 0x06)
        assertEquals(b, s.builder)
    }

    @Test
    fun blobEncoder_Field() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        var f = e.field()
        f.customModifiers()
            .addModifier(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), isOptional = true)
            .addModifier(MetadataTokens.typeDefinitionHandle(2).toEntityHandle(), isOptional = false)
        f.type(isByRef = true).objectType()
        assertBytes(b, 0x06, 0x20, 0x04, 0x1F, 0x08, 0x10, 0x1C)

        b.clear()
        f = e.field()
        f.customModifiers()
            .addModifier(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), isOptional = true)
            .addModifier(MetadataTokens.typeDefinitionHandle(2).toEntityHandle(), isOptional = false)
        f.type().int32()
        assertBytes(b, 0x06, 0x20, 0x04, 0x1F, 0x08, 0x08)
    }

    @Test
    fun blobEncoder_MethodSpecificationSignature() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        val s = e.methodSpecificationSignature(genericArgumentCount = 0)
        assertBytes(b, 0x0A, 0x00)
        assertEquals(b, s.builder)
        b.clear()

        e.methodSpecificationSignature(genericArgumentCount = 1234)
        assertBytes(b, 0x0A, 0x84, 0xD2)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.methodSpecificationSignature(genericArgumentCount = -1) }
        assertFailsWith<IllegalArgumentException> { e.methodSpecificationSignature(genericArgumentCount = 0xffff + 1) }
    }

    @Test
    fun blobEncoder_MethodSignature() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        var s = e.methodSignature()
        assertBytes(b, 0x00)
        assertEquals(b, s.builder)
        assertFalse(s.hasVarArgs)
        b.clear()

        s =
            e.methodSignature(
                convention = SignatureCallingConvention.STD_CALL,
                genericParameterCount = 1234,
                isInstanceMethod = true,
            )
        assertBytes(b, 0x32, 0x84, 0xD2)
        assertFalse(s.hasVarArgs)
        b.clear()

        s =
            e.methodSignature(
                convention = SignatureCallingConvention.VAR_ARGS,
                genericParameterCount = 1,
                isInstanceMethod = false,
            )
        assertBytes(b, 0x15, 0x01)
        assertTrue(s.hasVarArgs)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.methodSignature(genericParameterCount = -1) }
        assertFailsWith<IllegalArgumentException> { e.methodSignature(genericParameterCount = 0xffff + 1) }
    }

    @Test
    fun blobEncoder_PropertySignature() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        var s = e.propertySignature()
        assertBytes(b, 0x08)
        assertFalse(s.hasVarArgs)
        b.clear()

        s = e.propertySignature(isInstanceProperty = true)
        assertBytes(b, 0x28)
        assertFalse(s.hasVarArgs)
    }

    @Test
    fun blobEncoder_CustomAttributeSignature() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        e.customAttributeSignature(
            fixedArguments = { f -> assertEquals(b, f.builder) },
            namedArguments = { n -> assertEquals(b, n.builder) },
        )
        assertBytes(b, 0x01, 0x00)
    }

    @Test
    fun blobEncoder_LocalVariableSignature() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        e.localVariableSignature(variableCount = 0)
        assertBytes(b, 0x07, 0x00)
        b.clear()

        e.localVariableSignature(variableCount = 1000000)
        assertBytes(b, 0x07, 0xC0, 0x0F, 0x42, 0x40)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.localVariableSignature(-1) }
        assertFailsWith<IllegalArgumentException> { e.localVariableSignature(MAX_COMPRESSED_INTEGER_VALUE + 1) }
    }

    @Test
    fun blobEncoder_TypeSpecificationSignature() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        val s = e.typeSpecificationSignature()
        assertBytes(b)
        assertEquals(b, s.builder)
    }

    @Test
    fun blobEncoder_PermissionSetBlob() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        e.permissionSetBlob(attributeCount = 0)
        assertBytes(b, 0x2e, 0x00)
        b.clear()

        e.permissionSetBlob(attributeCount = 1000000)
        assertBytes(b, 0x2e, 0xC0, 0x0F, 0x42, 0x40)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.permissionSetBlob(-1) }
        assertFailsWith<IllegalArgumentException> { e.permissionSetBlob(MAX_COMPRESSED_INTEGER_VALUE + 1) }
    }

    @Test
    fun blobEncoder_PermissionSetArguments() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)

        e.permissionSetArguments(argumentCount = 0)
        assertBytes(b, 0x00)
        b.clear()

        e.permissionSetArguments(argumentCount = 1000000)
        assertBytes(b, 0xC0, 0x0F, 0x42, 0x40)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.permissionSetArguments(-1) }
        assertFailsWith<IllegalArgumentException> { e.permissionSetArguments(MAX_COMPRESSED_INTEGER_VALUE + 1) }
    }

    @Test
    fun methodSignatureEncoder_Parameters() {
        val b = BlobBuilder()
        val e = MethodSignatureEncoder(b, hasVarArgs = false)

        e.parameters(0, { }, { })
        assertBytes(b, 0x00)
        b.clear()

        e.parameters(1000000, { }, { })
        assertBytes(b, 0xC0, 0x0F, 0x42, 0x40)
        b.clear()

        e.parameters(
            10,
            returnType = { rt -> assertEquals(b, rt.builder) },
            parameters = { ps -> assertEquals(b, ps.builder) },
        )
        assertBytes(b, 0x0A)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.parameters(-1, { }, { }) }
        assertFailsWith<IllegalArgumentException> { e.parameters(MAX_COMPRESSED_INTEGER_VALUE + 1, { }, { }) }
    }

    @Test
    fun methodSignatureEncoder_Parameters_fullMethod() {
        val b = BlobBuilder()
        val e = BlobEncoder(b)
        e.methodSignature()
            .parameters(
                2,
                returnType = { rt -> rt.void() },
                parameters = { ps ->
                    ps.addParameter().type().string()
                    ps.addParameter().type().int32()
                },
            )
        // DEFAULT conv, 2 params, void ret, string, int32
        assertBytes(b, 0x00, 0x02, 0x01, 0x0E, 0x08)
    }

    @Test
    fun localVariablesEncoder_AddVariable() {
        val b = BlobBuilder()
        val e = LocalVariablesEncoder(b)

        val v = e.addVariable()
        assertBytes(b)
        v.type().int32()
        assertBytes(b, 0x08)
    }

    @Test
    fun localVariableTypeEncoder_Type() {
        val b = BlobBuilder()
        val e = LocalVariableTypeEncoder(b)

        e.type(isPinned = true, isByRef = true).int32()
        assertBytes(b, 0x45, 0x10, 0x08)

        b.clear()
        e.typedReference()
        assertBytes(b, 0x16)
    }

    @Test
    fun parameterTypeEncoder_Type() {
        val b = BlobBuilder()
        val e = ParameterTypeEncoder(b)

        e.type(isByRef = true).objectType()
        assertBytes(b, 0x10, 0x1C)

        b.clear()
        e.typedReference()
        assertBytes(b, 0x16)
    }

    @Test
    fun permissionSetEncoder_AddPermission() {
        val b = BlobBuilder()
        val e = PermissionSetEncoder(b)

        val args = BlobBuilder()
        args.writeByte(0xAB.toByte())

        e.addPermission("abc", args)
        // SerString("abc") + compressed count(1) + payload
        assertBytes(b, 0x03, 0x61, 0x62, 0x63, 0x01, 0xAB)

        b.clear()
        e.addPermission("a", byteArrayOf(1, 2))
        assertBytes(b, 0x01, 0x61, 0x02, 0x01, 0x02)
    }

    @Test
    fun genericTypeArgumentsEncoder_AddArgument() {
        val b = BlobBuilder()
        val e = GenericTypeArgumentsEncoder(b)

        e.addArgument().int32()
        e.addArgument().objectType()
        assertBytes(b, 0x08, 0x1C)
    }

    @Test
    fun fieldTypeEncoder_Variants() {
        val b = BlobBuilder()
        val e = FieldTypeEncoder(b)

        e.type(isByRef = false).int32()
        assertBytes(b, 0x08)

        b.clear()
        e.typedReference()
        assertBytes(b, 0x16)

        b.clear()
        e.customModifiers().addModifier(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), isOptional = false)
        assertBytes(b, 0x1F, 0x04)
    }

    @Test
    fun literalEncoder_Scalar_and_TaggedScalar() {
        val b = BlobBuilder()
        val e = LiteralEncoder(b)

        val s = e.scalar()
        assertBytes(b)
        b.clear()

        e.taggedScalar(
            type = { et -> assertEquals(b, et.builder) },
            scalar = { sc -> sc.int32Constant() },
        )

        b.clear()
        e.taggedScalar(
            type = { it.int32() },
            scalar = { it.constant(5) },
        )
        assertBytes(b, 0x08, 0x05, 0x00, 0x00, 0x00)
    }

    private fun ScalarEncoder.int32Constant() {
        constant(0)
    }

    @Test
    fun literalEncoder_TaggedVector() {
        val b = BlobBuilder()
        val e = LiteralEncoder(b)

        e.taggedVector(
            arrayType = { at ->
                assertEquals(b, at.builder)
                at.elementType().int32()
            },
            vector = { v ->
                v.count(2).apply {
                    addLiteral().scalar().constant(1)
                    addLiteral().scalar().constant(2)
                }
            },
        )
        // SZARRAY + I4 + count(2) + two int32 literals
        assertBytes(
            b,
            0x1D, 0x08,
            0x02, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00,
        )
    }

    @Test
    fun scalarEncoder_NullArray() {
        val b = BlobBuilder()
        val e = ScalarEncoder(b)

        e.nullArray()
        assertBytes(b, 0xff, 0xff, 0xff, 0xff)
    }

    @Test
    fun scalarEncoder_Constant() {
        val b = BlobBuilder()
        val e = ScalarEncoder(b)

        e.constant(null)
        assertBytes(b, 0xff)
        b.clear()

        e.constant("")
        assertBytes(b, 0x00)
        b.clear()

        e.constant("abc")
        assertBytes(b, 0x03, 0x61, 0x62, 0x63)
        b.clear()

        e.constant(true)
        assertBytes(b, 0x01)
        b.clear()

        e.constant(0x70) // enum values are passed by callers as raw ints
        assertBytes(b, 0x70, 0x00, 0x00, 0x00)
        b.clear()

        e.constant((0xAB).toByte())
        assertBytes(b, 0xAB)
        b.clear()

        e.constant((0x12).toByte())
        assertBytes(b, 0x12)
        b.clear()

        e.constant(0xABCD.toUShort())
        assertBytes(b, 0xCD, 0xAB)
        b.clear()

        e.constant(0x1234.toShort())
        assertBytes(b, 0x34, 0x12)
        b.clear()

        e.constant(0xABCD)
        assertBytes(b, 0xCD, 0xAB, 0x00, 0x00)
        b.clear()

        e.constant(0xABCD.toUInt())
        assertBytes(b, 0xCD, 0xAB, 0x00, 0x00)
        b.clear()

        e.constant(0x1122334455667788uL)
        assertBytes(b, 0x88, 0x77, 0x66, 0x55, 0x44, 0x33, 0x22, 0x11)
        b.clear()

        e.constant(0xAABBCCDDEEFF1122uL)
        assertBytes(b, 0x22, 0x11, 0xFF, 0xEE, 0xDD, 0xCC, 0xBB, 0xAA)
        b.clear()

        e.constant(0.1f)
        assertBytes(b, 0xCD, 0xCC, 0xCC, 0x3D)
        b.clear()

        e.constant(0.1)
        assertBytes(b, 0x9A, 0x99, 0x99, 0x99, 0x99, 0x99, 0xB9, 0x3F)
    }

    @Test
    fun scalarEncoder_SystemType() {
        val b = BlobBuilder()
        val e = ScalarEncoder(b)

        e.systemType(null)
        assertBytes(b, 0xff)
        b.clear()

        e.systemType("abc")
        assertBytes(b, 0x03, 0x61, 0x62, 0x63)
        b.clear()

        assertFailsWith<IllegalStateException> { e.systemType("") }
    }

    @Test
    fun vectorEncoder_Count() {
        val b = BlobBuilder()
        val e = VectorEncoder(b)

        e.count(0)
        assertBytes(b, 0x00, 0x00, 0x00, 0x00)
        b.clear()

        e.count(Int.MAX_VALUE)
        assertBytes(b, 0xFF, 0xFF, 0xFF, 0x7F)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.count(-1) }
    }

    @Test
    fun nameEncoder_Name() {
        val b = BlobBuilder()
        val e = NameEncoder(b)

        e.name("abc")
        assertBytes(b, 0x03, 0x61, 0x62, 0x63)
        b.clear()

        assertFailsWith<IllegalStateException> { e.name("") }
    }

    @Test
    fun customAttributeNamedArgumentsEncoder_Count() {
        val b = BlobBuilder()
        val e = CustomAttributeNamedArgumentsEncoder(b)

        e.count(0)
        assertBytes(b, 0x00, 0x00)
        b.clear()

        e.count(0xffff)
        assertBytes(b, 0xff, 0xff)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.count(-1) }
        assertFailsWith<IllegalArgumentException> { e.count(0xffff + 1) }
    }

    @Test
    fun namedArgumentsEncoder_AddArgument() {
        val b = BlobBuilder()
        val e = NamedArgumentsEncoder(b)

        e.addArgument(true, { }, { }, { })
        assertBytes(b, 0x53)
        b.clear()

        e.addArgument(false, { t -> assertEquals(b, t.builder) }, { n -> assertEquals(b, n.builder) }, { l -> assertEquals(b, l.builder) })
        assertBytes(b, 0x54)
    }

    @Test
    fun namedArgumentTypeEncoder_Variants() {
        val b = BlobBuilder()
        val e = NamedArgumentTypeEncoder(b)

        e.scalarType().int32()
        assertBytes(b, 0x08)
        b.clear()

        e.objectType()
        assertBytes(b, 0x51)
        b.clear()

        e.szArray().elementType().int32()
        assertBytes(b, 0x1D, 0x08)
        b.clear()

        e.szArray().objectArray()
        assertBytes(b, 0x1D, 0x51)
    }

    @Test
    fun customAttributeElementTypeEncoder_Primitives() {
        val b = BlobBuilder()
        val e = CustomAttributeElementTypeEncoder(b)

        e.boolean(); assertBytes(b, 0x02); b.clear()
        e.char(); assertBytes(b, 0x03); b.clear()
        e.sByte(); assertBytes(b, 0x04); b.clear()
        e.byte(); assertBytes(b, 0x05); b.clear()
        e.int16(); assertBytes(b, 0x06); b.clear()
        e.uint16(); assertBytes(b, 0x07); b.clear()
        e.int32(); assertBytes(b, 0x08); b.clear()
        e.uint32(); assertBytes(b, 0x09); b.clear()
        e.int64(); assertBytes(b, 0x0A); b.clear()
        e.uint64(); assertBytes(b, 0x0B); b.clear()
        e.single(); assertBytes(b, 0x0C); b.clear()
        e.double(); assertBytes(b, 0x0D); b.clear()
        e.string(); assertBytes(b, 0x0E); b.clear()
    }

    @Test
    fun customAttributeElementTypeEncoder_PrimitiveType() {
        val b = BlobBuilder()
        val e = CustomAttributeElementTypeEncoder(b)

        for (code in PrimitiveSerializationTypeCode.entries) {
            e.primitiveType(code)
        }
        // Boolean..String in enum declaration order:
        assertBytes(
            b,
            0x02, 0x05, 0x04, 0x03, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
        )
        // note: C# also tests an out-of-range raw value ((PrimitiveSerializationTypeCode)255);
        // Kotlin enums cannot represent it, so the rejection branch is exercised
        // through SignatureTypeEncoder.primitiveType(PrimitiveTypeCode.Void) below.
    }

    @Test
    fun customAttributeElementTypeEncoder_SystemType_and_Enum() {
        val b = BlobBuilder()
        val e = CustomAttributeElementTypeEncoder(b)

        e.systemType()
        assertBytes(b, 0x50)
        b.clear()

        e.enumType("abc")
        assertBytes(b, 0x55, 0x03, 0x61, 0x62, 0x63)
        b.clear()

        assertFailsWith<IllegalStateException> { e.enumType("") }
    }

    @Test
    fun signatureTypeEncoder_Primitives() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        e.boolean(); assertBytes(b, 0x02); b.clear()
        e.char(); assertBytes(b, 0x03); b.clear()
        e.sByte(); assertBytes(b, 0x04); b.clear()
        e.byte(); assertBytes(b, 0x05); b.clear()
        e.int16(); assertBytes(b, 0x06); b.clear()
        e.uint16(); assertBytes(b, 0x07); b.clear()
        e.int32(); assertBytes(b, 0x08); b.clear()
        e.uint32(); assertBytes(b, 0x09); b.clear()
        e.int64(); assertBytes(b, 0x0A); b.clear()
        e.uint64(); assertBytes(b, 0x0B); b.clear()
        e.single(); assertBytes(b, 0x0C); b.clear()
        e.double(); assertBytes(b, 0x0D); b.clear()
        e.string(); assertBytes(b, 0x0E); b.clear()
        e.typedReference(); assertBytes(b, 0x16); b.clear()
        e.intPtr(); assertBytes(b, 0x18); b.clear()
        e.uIntPtr(); assertBytes(b, 0x19); b.clear()
        e.objectType(); assertBytes(b, 0x1C); b.clear()
    }

    @Test
    fun signatureTypeEncoder_PrimitiveType() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        e.primitiveType(PrimitiveTypeCode.BOOLEAN)
        assertBytes(b, 0x02)
        b.clear()

        e.primitiveType(PrimitiveTypeCode.OBJECT)
        assertBytes(b, 0x1C)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.primitiveType(PrimitiveTypeCode.VOID) }
        assertEquals(0, b.count)
    }

    @Test
    fun signatureTypeEncoder_Array() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        e.array(
            elementType = { et -> assertEquals(b, et.builder); et.int32() },
            arrayShape = { shape -> shape.shape(rank = 2, sizes = intArrayOf(3, 4)) },
        )
        assertBytes(b, 0x14, 0x08, 0x02, 0x02, 0x03, 0x04, 0x02, 0x00, 0x00)
    }

    @Test
    fun signatureTypeEncoder_Type() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        e.type(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), isValueType = true)
        assertBytes(b, 0x11, 0x04)
        b.clear()

        e.type(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), isValueType = false)
        assertBytes(b, 0x12, 0x04)
        b.clear()

        e.type(MetadataTokens.typeReferenceHandle(1).toEntityHandle(), isValueType = false)
        assertBytes(b, 0x12, 0x05)
        b.clear()

        assertFailsWith<Exception> { e.type(EntityHandle(0u), isValueType = false) }
        assertEquals(0, b.count)
    }

    @Test
    fun signatureTypeEncoder_FunctionPointer() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        val m =
            e.functionPointer(
                SignatureCallingConvention.C_DECL,
                FunctionPointerAttributes.HAS_THIS,
                genericParameterCount = 1,
            )
        assertEquals(b, m.builder)
        assertBytes(b, 0x1B, 0x21, 0x01)
        b.clear()

        e.functionPointer(SignatureCallingConvention.DEFAULT, FunctionPointerAttributes.HAS_EXPLICIT_THIS, 0)
        assertBytes(b, 0x1B, 0x60)
        b.clear()

        e.functionPointer()
        assertBytes(b, 0x1B, 0x00)
        b.clear()

        // note: C# also rejects raw attribute values like (FunctionPointerAttributes)1000;
        // Kotlin enums cannot represent them, so that branch is not testable here.
        assertFailsWith<IllegalArgumentException> { e.functionPointer(genericParameterCount = -1) }
        assertFailsWith<IllegalArgumentException> { e.functionPointer(genericParameterCount = 0xffff + 1) }
        assertEquals(0, b.count)
    }

    @Test
    fun signatureTypeEncoder_GenericInstantiation() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        val m = e.genericInstantiation(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), 1, true)
        assertEquals(b, m.builder)
        assertBytes(b, 0x15, 0x11, 0x04, 0x01)
        b.clear()

        e.genericInstantiation(MetadataTokens.typeReferenceHandle(1).toEntityHandle(), 1, true)
        assertBytes(b, 0x15, 0x11, 0x05, 0x01)
        b.clear()

        e.genericInstantiation(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), 1000, false)
        assertBytes(b, 0x15, 0x12, 0x04, 0x83, 0xE8)
        b.clear()

        e.genericInstantiation(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), 0xffff, false)
        assertBytes(b, 0x15, 0x12, 0x04, 0xC0, 0x00, 0xFF, 0xFF)
        b.clear()

        assertFailsWith<IllegalArgumentException> {
            e.genericInstantiation(EntityHandle(0u), 1, isValueType = false)
        }
        assertFailsWith<IllegalArgumentException> {
            e.genericInstantiation(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), 0, true)
        }
        assertFailsWith<IllegalArgumentException> {
            e.genericInstantiation(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), -1, true)
        }
        assertEquals(0, b.count)
    }

    @Test
    fun signatureTypeEncoder_GenericTypeParameters() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        e.genericMethodTypeParameter(0)
        assertBytes(b, 0x1E, 0x00)
        b.clear()

        e.genericMethodTypeParameter(0xffff)
        assertBytes(b, 0x1E, 0xC0, 0x00, 0xFF, 0xFF)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.genericMethodTypeParameter(-1) }

        b.clear()
        e.genericTypeParameter(0)
        assertBytes(b, 0x13, 0x00)
        b.clear()

        e.genericTypeParameter(0xffff)
        assertBytes(b, 0x13, 0xC0, 0x00, 0xFF, 0xFF)
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.genericTypeParameter(-1) }
        assertEquals(0, b.count)
    }

    @Test
    fun signatureTypeEncoder_Pointer_SZArray_CustomModifiers() {
        val b = BlobBuilder()
        val e = SignatureTypeEncoder(b)

        val p = e.pointer()
        assertBytes(b, 0x0F)
        assertEquals(b, p.builder)

        b.clear()
        e.voidPointer()
        assertBytes(b, 0x0F, 0x01)

        b.clear()
        val a = e.szArray()
        assertBytes(b, 0x1D)
        assertEquals(b, a.builder)

        b.clear()
        val cm = e.customModifiers()
        assertBytes(b)
        assertEquals(b, cm.builder)
    }

    @Test
    fun customModifiersEncoder_AddModifier() {
        val b = BlobBuilder()
        val e = CustomModifiersEncoder(b)

        val a = e.addModifier(MetadataTokens.typeDefinitionHandle(1).toEntityHandle(), true)
        assertBytes(b, 0x20, 0x04)
        assertEquals(b, a.builder)
        b.clear()

        e.addModifier(MetadataTokens.typeReferenceHandle(1).toEntityHandle(), false)
        assertBytes(b, 0x1f, 0x05)
        b.clear()

        e.addModifier(MetadataTokens.typeSpecificationHandle(1).toEntityHandle(), false)
        assertBytes(b, 0x1f, 0x06)
        b.clear()

        assertFailsWith<IllegalStateException> { e.addModifier(EntityHandle(0u), true) }
        assertFailsWith<Exception> { e.addModifier(MetadataTokens.fieldDefinitionHandle(1).toEntityHandle(), true) }
        assertEquals(0, b.count)
    }

    @Test
    fun arrayShapeEncoder_Shape() {
        val b = BlobBuilder()
        val e = ArrayShapeEncoder(b)

        e.shape(0xffff, intArrayOf(), intArrayOf())
        assertBytes(b, 0xC0, 0x00, 0xFF, 0xFF, 0x00, 0x00)
        b.clear()

        e.shape(3, intArrayOf(0x0A), intArrayOf())
        assertBytes(b, 0x03, 0x01, 0x0A, 0x00)
        b.clear()

        e.shape(3, intArrayOf(0x0A, 0x0B), intArrayOf(0x02, 0x03))
        assertBytes(b, 0x03, 0x02, 0x0A, 0x0B, 0x02, 0x04, 0x06)
        b.clear()

        e.shape(3, intArrayOf(), intArrayOf(-2, -1))
        assertBytes(b, 0x03, 0x00, 0x02, 0x7D, 0x7F)
        b.clear()

        // null lowerBounds -> rank zeros
        e.shape(2, intArrayOf(1), null)
        assertBytes(b, 0x02, 0x01, 0x01, 0x02, 0x00, 0x00)
        b.clear()

        e.shape(3, intArrayOf(MAX_COMPRESSED_INTEGER_VALUE), intArrayOf(MIN_SIGNED_COMPRESSED_INTEGER_VALUE, MAX_SIGNED_COMPRESSED_INTEGER_VALUE))
        assertBytes(
            b,
            0x03,
            0x01, 0xDF, 0xFF, 0xFF, 0xFF,
            0x02, 0xC0, 0x00, 0x00, 0x01, 0xDF, 0xFF, 0xFF, 0xFE,
        )
        b.clear()

        assertFailsWith<IllegalArgumentException> { e.shape(0, intArrayOf(), intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { e.shape(-1, intArrayOf(), intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { e.shape(0xffff + 1, intArrayOf(), intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { e.shape(1, intArrayOf(1, 2, 3), intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { e.shape(1, intArrayOf(), intArrayOf(1, 2, 3)) }
        assertFailsWith<IllegalArgumentException> { e.shape(1, intArrayOf(-1), intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { e.shape(1, intArrayOf(MAX_COMPRESSED_INTEGER_VALUE + 1), intArrayOf()) }
        assertFailsWith<IllegalArgumentException> { e.shape(1, intArrayOf(), intArrayOf(MIN_SIGNED_COMPRESSED_INTEGER_VALUE - 1)) }
        assertFailsWith<IllegalArgumentException> { e.shape(1, intArrayOf(), intArrayOf(MAX_SIGNED_COMPRESSED_INTEGER_VALUE + 1)) }
    }

    @Test
    fun returnTypeEncoder_Variants() {
        val b = BlobBuilder()
        val e = ReturnTypeEncoder(b)

        val s = e.customModifiers()
        assertBytes(b)
        assertEquals(b, s.builder)
        b.clear()

        e.type(true)
        assertBytes(b, 0x10)
        b.clear()

        e.type(false)
        assertBytes(b)
        b.clear()

        e.typedReference()
        assertBytes(b, 0x16)
        b.clear()

        e.void()
        assertBytes(b, 0x01)
    }

    @Test
    fun parametersEncoder_AddParameter_StartVarArgs() {
        val b = BlobBuilder()
        val e = ParametersEncoder(b, hasVarArgs = true)

        e.addParameter().type().int32()
        val s = e.startVarArgs()
        assertBytes(b, 0x08, 0x41)
        assertFalse(s.hasVarArgs)

        assertFailsWith<IllegalStateException> { s.startVarArgs() }

        val noVarArgs = ParametersEncoder(BlobBuilder(), hasVarArgs = false)
        assertFailsWith<IllegalStateException> { noVarArgs.startVarArgs() }
    }

}
