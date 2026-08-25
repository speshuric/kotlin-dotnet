package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.BlobEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.SignatureCallingConvention
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.SignatureTypeEncoder

/** Резолв CIL-имени типа в EntityHandle (TypeDefOrRef). */
internal typealias TypeEntityResolver = (cilType: String) -> EntityHandle

/**
 * Построение сигнатур из CIL-типового DSL (строки `int32`, `Ns.T`,
 * `[asm]Ns.T`). Знает о [MetadataBuilder]/BlobEncoder, но не о парсере
 * ссылок и модели строк: пользовательские типы приходят через
 * [typeEntityResolver] (инъекция, чтобы сигнатуры не зависели от
 * реестра типов).
 */
internal object PeSignatures {

    /** MethodDef/MemberRef-сигнатура метода. */
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

    /** Field-сигнатура. */
    fun fieldSignature(
        metadata: MetadataBuilder,
        resolveTypeEntity: TypeEntityResolver,
        cilType: String,
    ): BlobHandle {
        val b = BlobBuilder()
        applyCilType(BlobEncoder(b).fieldSignature(), cilType, resolveTypeEntity)
        return metadata.getOrAddBlob(b)
    }

    /** Применяет CIL-тип к кодировщику: примитивы напрямую, остальное — через resolver. */
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
                // Пользовательский тип ("Ns.Name", "[asm]Ns.Name") — reference type.
                enc.type(resolveTypeEntity(cilType), isValueType = false)
            }
        }
    }

    /**
     * BCL-тип для box/newarr по CIL-алиасу примитива; для не-примитивов —
     * ошибка (как раньше: box пользовательского типа не поддержан).
     */
    fun boxedTypeToken(cilType: String, resolveTypeEntity: TypeEntityResolver): EntityHandle {
        val (ns, name) = bclPrimitiveName(cilType)
            ?: throw IllegalStateException("PE backend: unsupported CIL type '$cilType'")
        return resolveTypeEntity("$ns.$name")
    }

    /** (namespace, name) BCL-типа примитива или null, если [cilType] — не алиас. */
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
