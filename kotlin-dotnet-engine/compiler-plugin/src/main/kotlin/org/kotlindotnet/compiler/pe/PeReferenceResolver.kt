package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.AssemblyVersion
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.BlobEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addCustomAttribute
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.SignatureCallingConvention
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addAssemblyReference
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addMemberReference
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeReference

/**
 * Резолв операндов инструкций и кэш внешних ссылок (J-01).
 *
 * Знает парсер ([MemberRefParser]) и сигнатуры ([PeSignatures]), но не
 * модель строк целиком: доступ к своим методам/типам инъецируется
 * колбэками из [PeIlEmitter] (см. конструктор).
 *
 * Свои методы (assembly == null в ref) разрешаются напрямую в MethodDef
 * (хэндлы предвыделены); внешние — в MemberRef поверх TypeRef указанной
 * сборки. Свои классы — ПРЯМО TypeDef (row id предвычислен: порядок строк
 * фиксирован — &lt;Module&gt;, контейнер, классы); NIL-scope TypeRef
 * спецификацией допустим и работает на net10 CLR (TODO/PHASE-10.md
 * §«Итоги»), но компиляторами не используется.
 */
internal class PeReferenceResolver(
    private val metadata: MetadataBuilder,
    /** Поиск своего метода по разобранному ref (null → unknown-method). */
    private val findOwnMethod: (info: MemberRefParser.CallInfo) -> MethodRec?,
    /** Диагностика списка известных методов для ошибки unresolved. */
    private val describeOwnMethods: () -> String,
    /** Row id (1-based) своего TypeDef по имени или null. */
    private val findOwnTypeDefRow: (t: MemberRefParser.TypeNameInfo) -> Int?,
) {
    private val assemblyRefs = HashMap<String, AssemblyReferenceHandle>()
    private val typeRefs = HashMap<String, TypeReferenceHandle>()

    /**
     * Разрешает call/callvirt/newobj-ref в токен (MethodDef или MemberRef).
     */
    fun resolveCallTarget(ref: String): EntityHandle {
        val info = MemberRefParser.parseCallRef(ref)

        if (info.assembly == null) {
            findOwnMethod(info)?.let { return it.handle.toEntityHandle() }
            throw IllegalStateException(
                "Call to unknown same-module method: '$ref'; known: ${describeOwnMethods()}",
            )
        }

        val parent = typeRef(info.assembly, info.namespace, info.typeName).toEntityHandle()
        return metadata.addMemberReference(
            parent = parent,
            name = metadata.getOrAddString(info.methodName),
            signature = PeSignatures.buildSignature(
                metadata, ::resolveTypeEntity, info.retCil, info.paramsCil, isInstance = info.isInstance,
            ),
        ).toEntityHandle()
    }

    /** Разрешает field-ref (`cilType [[Asm]]Ns.T::name`) в MemberRef-токен. */
    fun fieldRefToken(ref: String): EntityHandle {
        val f = MemberRefParser.parseFieldRef(ref)
        val b = BlobBuilder()
        PeSignatures.applyCilType(BlobEncoder(b).fieldSignature(), f.cilType, ::resolveTypeEntity)
        return metadata.addMemberReference(
            parent = resolveTypeEntity(f.ownerType),
            name = metadata.getOrAddString(f.fieldName),
            signature = metadata.getOrAddBlob(b),
        ).toEntityHandle()
    }

    /**
     * Assembly-level [DebuggableAttribute] (System.Diagnostics).
     *
     * @param modes битовая маска DebuggingModes; csc /debug+ использует
     *        Default|IgnoreSymbolStoreSequencePoints|EnableEditAndContinue|
     *        DisableOptimizations = 0x0107, release-сборки атрибут не ставят.
     */
    fun addAssemblyDebuggableAttribute(modes: Int) {
        val attrType = typeRef(SYSTEM_RUNTIME_ASSEMBLY, "System.Diagnostics", "DebuggableAttribute")
        val modesType = typeRef(SYSTEM_RUNTIME_ASSEMBLY, "System.Diagnostics", "DebuggingModes")
        val sig = BlobBuilder()
        BlobEncoder(sig).methodSignature(
            convention = SignatureCallingConvention.DEFAULT,
            genericParameterCount = 0,
            isInstanceMethod = true,
        ).parameters(
            1,
            returnType = { it.void() },
            parameters = { ps ->
                ps.addParameter().type().type(modesType.toEntityHandle(), isValueType = true)
            },
        )
        val ctor =
            metadata.addMemberReference(
                parent = attrType.toEntityHandle(),
                name = metadata.getOrAddString(".ctor"),
                signature = metadata.getOrAddBlob(sig),
            )
        // CustomAttribute value: prolog (0x0001), int32 arg, 0 named args.
        val value = BlobBuilder()
        value.writeInt16(1)
        value.writeInt32(modes)
        value.writeInt16(0)
        metadata.addCustomAttribute(
            MetadataTokens.assemblyDefinitionHandle(1).toEntityHandle(),
            ctor.toEntityHandle(),
            metadata.getOrAddBlob(value),
        )
    }

    /** Разрешает имя типа в EntityHandle (TypeDefOrRef), см. классовую документацию. */
    fun resolveTypeEntity(cilType: String): EntityHandle {
        findOwnTypeDefRow(MemberRefParser.parseTypeName(cilType))?.let {
            return MetadataTokens.typeDefinitionHandle(it).toEntityHandle()
        }
        val t = MemberRefParser.parseTypeName(cilType)
        val asm = t.assembly ?: SYSTEM_RUNTIME_ASSEMBLY
        return typeRef(asm, t.namespace, t.typeName).toEntityHandle()
    }

    fun resolveBaseEntity(baseTypeCil: String?): EntityHandle {
        if (baseTypeCil == null || baseTypeCil == "object") {
            return systemObjectRef().toEntityHandle()
        }
        return resolveTypeEntity(baseTypeCil)
    }

    private fun assemblyRef(name: String): AssemblyReferenceHandle {
        assemblyRefs[name]?.let { return it }
        val ref =
            if (name == SYSTEM_RUNTIME_ASSEMBLY) {
                // Проверенная связка из HelloWorldImage (тест 05-pe-hello):
                // версия + PublicKeyToken обеспечивают привязку на net10.
                metadata.addAssemblyReference(
                    name = metadata.getOrAddString(name),
                    version = AssemblyVersion(8, 0, 0, 0),
                    culture = MetadataTokens.stringHandle(0),
                    publicKeyOrToken = metadata.getOrAddBlob(BCL_PUBLIC_KEY_TOKEN),
                    flags = 0u,
                    hashValue = MetadataTokens.blobHandle(0),
                )
            } else {
                metadata.addAssemblyReference(
                    name = metadata.getOrAddString(name),
                    version = AssemblyVersion(0, 0, 0, 0),
                    culture = MetadataTokens.stringHandle(0),
                    publicKeyOrToken = MetadataTokens.blobHandle(0),
                    flags = 0u,
                    hashValue = MetadataTokens.blobHandle(0),
                )
            }
        assemblyRefs[name] = ref
        return ref
    }

    private fun typeRef(assemblyName: String, namespace: String, typeName: String): TypeReferenceHandle {
        val key = "$assemblyName|$namespace|$typeName"
        typeRefs[key]?.let { return it }

        // System.* базовые типы всегда резолвим через System.Runtime
        // (проверено в HelloWorldImage); прочие — через указанную сборку
        // либо без scope (same-module типы).
        val scope =
            if (assemblyName.isEmpty()) {
                EntityHandle.NIL
            } else {
                assemblyRef(assemblyName).toEntityHandle()
            }

        val ref =
            metadata.addTypeReference(
                resolutionScope = scope,
                namespace = if (namespace.isEmpty()) MetadataTokens.stringHandle(0) else metadata.getOrAddString(namespace),
                name = metadata.getOrAddString(typeName),
            )
        typeRefs[key] = ref
        return ref
    }

    private fun systemObjectRef(): TypeReferenceHandle = typeRef(SYSTEM_RUNTIME_ASSEMBLY, "System", "Object")

    private companion object {
        /** PublicKeyToken BCL-сборок (b03f5f7f11d50a3a). */
        val BCL_PUBLIC_KEY_TOKEN =
            byteArrayOf(
                0xB0.toByte(), 0x3F, 0x5F, 0x7F, 0x11, 0xD5.toByte(), 0x0A, 0x3A,
            )
    }
}
