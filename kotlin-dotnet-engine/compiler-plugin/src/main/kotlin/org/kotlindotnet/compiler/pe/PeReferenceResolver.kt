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
 * Resolution of instruction operands and the external-reference cache (J-01).
 *
 * Knows the parser ([MemberRefParser]) and the signatures ([PeSignatures]) but not
 * the full row model: access to own methods/types is injected via callbacks from
 * [PeIlEmitter] (see the constructor).
 *
 * Own methods (assembly == null in the ref) resolve directly into a MethodDef
 * (handles are preallocated); external ones go into a MemberRef over the TypeRef of
 * the given assembly. Own classes resolve DIRECTLY to a TypeDef (the row id is
 * precomputed: the row order is fixed — &lt;Module&gt;, container, classes); a
 * NIL-scope TypeRef is permitted by the spec and works on the net10 CLR
 * (TODO/PHASE-10.md §"Summary"), but no compiler uses it.
 */
internal class PeReferenceResolver(
    private val metadata: MetadataBuilder,
    /** Looks up an own method by the parsed ref (null → unknown-method). */
    private val findOwnMethod: (info: MemberRefParser.CallInfo) -> MethodRec?,
    /** Produces the list of known methods for diagnostics on unresolved refs. */
    private val describeOwnMethods: () -> String,
    /** The row id (1-based) of an own TypeDef by name, or null. */
    private val findOwnTypeDefRow: (t: MemberRefParser.TypeNameInfo) -> Int?,
) {
    private val assemblyRefs = HashMap<String, AssemblyReferenceHandle>()
    private val typeRefs = HashMap<String, TypeReferenceHandle>()

    /**
     * Resolves a call/callvirt/newobj ref into a token (MethodDef or MemberRef).
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

    /** Resolves a field-ref (`cilType [[Asm]]Ns.T::name`) into a MemberRef token. */
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
     * @param modes the DebuggingModes bit mask; csc /debug+ uses
     *        Default|IgnoreSymbolStoreSequencePoints|EnableEditAndContinue|
     *        DisableOptimizations = 0x0107; release builds do not emit the attribute.
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

    /** Resolves a type name into an EntityHandle (TypeDefOrRef); see the class documentation. */
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
                // A combination verified in HelloWorldImage (test 05-pe-hello):
                // version + PublicKeyToken provide binding on net10.
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

        // System.* base types always resolve through System.Runtime
        // (verified in HelloWorldImage); others go through the given assembly
        // or without a scope (same-module types).
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
        /** PublicKeyToken of the BCL assemblies (b03f5f7f11d50a3a). */
        val BCL_PUBLIC_KEY_TOKEN =
            byteArrayOf(
                0xB0.toByte(), 0x3F, 0x5F, 0x7F, 0x11, 0xD5.toByte(), 0x0A, 0x3A,
            )
    }
}
