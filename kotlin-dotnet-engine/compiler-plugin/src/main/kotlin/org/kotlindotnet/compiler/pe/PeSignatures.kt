package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.BlobEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.SignatureCallingConvention
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.SignatureTypeEncoder

/** Resolves a CIL type name into an EntityHandle (TypeDefOrRef). */
internal typealias TypeEntityResolver = (cilType: String) -> EntityHandle

/**
 * Builds signatures from the CIL type DSL (the strings `int32`, `Ns.T`,
 * `[asm]Ns.T`). Knows about [MetadataBuilder]/BlobEncoder but not about the ref
 * parser or the row model: user-defined types arrive via [typeEntityResolver]
 * (injected so that signatures do not depend on the type registry).
 */
internal object PeSignatures {

    /** A MethodDef/MemberRef method signature. */
    fun buildSignature(
        metadata: MetadataBuilder,
        resolveTypeEntity: TypeEntityResolver,
        retCil: String,
        paramsCil: List<String>,
        isInstance: Boolean = false,
    ): BlobHandle {
        val b = BlobBuilder()
        BlobEncoder(b).methodSignature(
            convention = SignatureCallingConvention.DEFAULT,
            genericParameterCount = 0,
            isInstanceMethod = isInstance,
        ).parameters(
            paramsCil.size,
            returnType = { rt ->
                if (retCil == "void") rt.void() else applyCilType(rt.type(), retCil, resolveTypeEntity)
            },
            parameters = { ps ->
                for (p in paramsCil) {
                    applyCilType(ps.addParameter().type(), p, resolveTypeEntity)
                }
            },
        )
        return metadata.getOrAddBlob(b)
    }

    /** A field signature. */
    fun fieldSignature(
        metadata: MetadataBuilder,
        resolveTypeEntity: TypeEntityResolver,
        cilType: String,
    ): BlobHandle {
        val b = BlobBuilder()
        applyCilType(BlobEncoder(b).fieldSignature(), cilType, resolveTypeEntity)
        return metadata.getOrAddBlob(b)
    }

    /** Applies a CIL type to the encoder: primitives directly, everything else through the resolver. */
    fun applyCilType(
        enc: SignatureTypeEncoder,
        cilType: String,
        resolveTypeEntity: TypeEntityResolver,
    ) {
        when (cilType) {
            "bool" -> enc.boolean()
            "char" -> enc.char()
            "int8" -> enc.sByte()
            "uint8" -> enc.byte()
            "int16" -> enc.int16()
            "uint16" -> enc.uint16()
            "int32" -> enc.int32()
            "uint32" -> enc.uint32()
            "int64" -> enc.int64()
            "uint64" -> enc.uint64()
            "float32" -> enc.single()
            "float64" -> enc.double()
            "string" -> enc.string()
            "object" -> enc.objectType()
            else -> {
                // A user-defined type ("Ns.Name", "[asm]Ns.Name") — a reference type.
                enc.type(resolveTypeEntity(cilType), isValueType = false)
            }
        }
    }

    /**
     * The BCL type for box/newarr from a CIL primitive alias; non-primitives
     * are an error (as before: boxing a user-defined type is unsupported).
     */
    fun boxedTypeToken(cilType: String, resolveTypeEntity: TypeEntityResolver): EntityHandle {
        val (ns, name) = bclPrimitiveName(cilType)
            ?: throw IllegalStateException("PE backend: unsupported CIL type '$cilType'")
        return resolveTypeEntity("$ns.$name")
    }

    /** The (namespace, name) of a primitive's BCL type, or null if [cilType] is not an alias. */
    private fun bclPrimitiveName(cilType: String): Pair<String, String>? =
        when (cilType) {
            "bool" -> "System" to "Boolean"
            "char" -> "System" to "Char"
            "int8" -> "System" to "SByte"
            "uint8" -> "System" to "Byte"
            "int16" -> "System" to "Int16"
            "uint16" -> "System" to "UInt16"
            "int32" -> "System" to "Int32"
            "uint32" -> "System" to "UInt32"
            "int64" -> "System" to "Int64"
            "uint64" -> "System" to "UInt64"
            "float32" -> "System" to "Single"
            "float64" -> "System" to "Double"
            "object" -> "System" to "Object"
            "string" -> "System" to "String"
            else -> null
        }
}
