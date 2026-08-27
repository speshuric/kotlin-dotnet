package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addInterfaceImplementation
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode

/**
 * Finalizes the TypeDef table (the endContainerClass steps after body encoding):
 * synthesizing default ctors, computing the fieldList/methodList ranges as a
 * cascade from the end, and writing TypeDef rows with interface impls.
 *
 * The group order is fixed: &lt;Module&gt;, the container of top-level functions,
 * classes in declaration order — handle precomputation rests on it
 * ([PeIlEmitter]).
 */
internal object PeTypeDefWriter {

    private data class Group(
        val namespace: String?,
        val name: String?,
        val flags: UInt?,
        val baseTypeCil: String?,
        val fields: List<FieldRec>,
        val methods: List<MethodRec>,
    )

    /**
     * Step 1: synthesize a default .ctor (D3) for classes without constructors.
     * K2 always puts a primary ctor into the IR (observation P5), so this branch is
     * insurance against degenerate IR. Interfaces need no constructors
     * (in the CLR: only abstract/cctor).
     */
    fun synthesizeDefaultCtors(classes: List<ClassRec>) {
        for (cls in classes) {
            if (cls.flags and INTERFACE_FLAG != 0u) continue
            if (cls.methods.any { it.isCtor }) continue
            check(cls.methods.isEmpty()) {
                "class '${cls.name}' has methods but no constructor (unexpected K2 IR)"
            }
            cls.methods.add(synthesizeDefaultCtor(cls))
        }
    }

    /** D3: a default public `.ctor()` — `ldarg.0; call base::.ctor(); ret`. */
    private fun synthesizeDefaultCtor(cls: ClassRec): MethodRec {
        val rec = MethodRec(".ctor", "void", emptyList(), isEntrypoint = false, isInstance = true, isCtor = true)
        val base = cls.baseTypeCil ?: "object"
        val baseQualified =
            if (base.startsWith("[")) base else "[$SYSTEM_RUNTIME_ASSEMBLY]${MemberRefParser.toBclTypeName(base)}"
        rec.ops.add(LocalOp(LocalKind.LDARG, 0))
        rec.ops.add(CallOp(ILOpCode.CALL, "void instance $baseQualified::.ctor()"))
        rec.ops.add(InstOp(ILOpCode.RET))
        return rec
    }

    /**
     * Steps 6–7: group ranges (an empty group starts its range where the next one
     * does — a cascade from the end) and TypeDef rows: the mandatory
     * &lt;Module&gt;, then the container and classes (+ interface impls).
     */
    fun writeTypeDefinitions(
        metadata: MetadataBuilder,
        containerNamespace: String,
        containerClass: String,
        containerMethods: List<MethodRec>,
        classes: List<ClassRec>,
        totalMethodRows: Int,
        resolveBaseEntity: (baseTypeCil: String?) -> EntityHandle,
        resolveTypeEntity: (cilType: String) -> EntityHandle,
    ) {
        val groups = buildList {
            add(Group(null, "<Module>", null, null, emptyList(), emptyList()))
            add(
                Group(
                    containerNamespace.ifEmpty { null },
                    containerClass,
                    0x00100181u, // Public | Abstract | Sealed | BeforeFieldInit
                    "object",
                    emptyList(),
                    containerMethods,
                ),
            )
            classes.forEach {
                add(Group(it.namespace, it.name, it.flags, it.baseTypeCil, it.fields, it.methods))
            }
        }

        val totalFields = classes.sumOf { it.fields.size }
        var nextFieldStart = totalFields + 1
        var nextMethodStart = totalMethodRows + 1
        val starts = arrayOfNulls<Pair<Int, Int>>(groups.size) // (fieldList, methodList)
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            // In C# handles convert to EntityHandle implicitly; in Kotlin it is explicit.
            val fStart =
                g.fields.firstOrNull()?.let { MetadataTokens.getRowNumber(it.handle.toEntityHandle()) }
                    ?: nextFieldStart
            val mStart =
                g.methods.firstOrNull()?.let { MetadataTokens.getRowNumber(it.handle.toEntityHandle()) }
                    ?: nextMethodStart
            starts[i] = fStart to mStart
            nextFieldStart = fStart
            nextMethodStart = mStart
        }

        for ((i, g) in groups.withIndex()) {
            val (fieldList, methodList) = starts[i]!!
            if (i == 0) {
                metadata.addTypeDefinition(
                    attributes = 0u,
                    namespace = MetadataTokens.stringHandle(0),
                    name = metadata.getOrAddString("<Module>"),
                    baseType = EntityHandle.NIL,
                    fieldList = MetadataTokens.fieldDefinitionHandle(fieldList),
                    methodList = MetadataTokens.methodDefinitionHandle(methodList),
                )
                continue
            }
            val isInterface = g.flags != null && (g.flags and INTERFACE_FLAG) != 0u
            val handle = metadata.addTypeDefinition(
                attributes = g.flags!!,
                namespace = g.namespace?.let { metadata.getOrAddString(it) }
                    ?: MetadataTokens.stringHandle(0),
                name = metadata.getOrAddString(g.name!!),
                baseType = if (isInterface) EntityHandle.NIL else resolveBaseEntity(g.baseTypeCil),
                fieldList = MetadataTokens.fieldDefinitionHandle(fieldList),
                methodList = MetadataTokens.methodDefinitionHandle(methodList),
            )
            // Handles were precomputed in ownTypeHandle — any mismatch would break
            // every reference to own types.
            check(MetadataTokens.getRowNumber(handle.toEntityHandle()) == i + 1) {
                "TypeDef row mismatch: predicted ${i + 1}, got ${MetadataTokens.getRowNumber(handle.toEntityHandle())}"
            }
            if (i >= 2 && classes[i - 2].interfaces.isNotEmpty()) {
                for (iface in classes[i - 2].interfaces) {
                    metadata.addInterfaceImplementation(handle, resolveTypeEntity(iface))
                }
            }
        }
    }
}
