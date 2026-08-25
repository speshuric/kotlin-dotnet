package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addInterfaceImplementation
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode

/**
 * Финализация таблицы TypeDef (шаги endContainerClass после кодирования
 * тел): синтез дефолтных ctor'ов, вычисление диапазонов fieldList/
 * methodList каскадом с конца и запись TypeDef-строк с interface impl.
 *
 * Порядок групп фиксирован: &lt;Module&gt;, контейнер top-level функций,
 * классы в порядке объявления — на нём держится предвычисление хэндлов
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
     * Шаг 1: синтез дефолтного .ctor (D3) для классов без конструкторов.
     * K2 всегда кладёт primary ctor в IR (наблюдение P5), поэтому ветка —
     * страховка от дегенеративного IR. Интерфейсам конструкторы не нужны
     * (CLR: только abstract/cctor).
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

    /** D3: дефолтный публичный `.ctor()` — `ldarg.0; call base::.ctor(); ret`. */
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
     * Шаги 6–7: диапазоны групп (пустая группа начинает свой диапазон там
     * же, где следующая — каскад с конца) и TypeDef-строки: обязательный
     * &lt;Module&gt;, затем контейнер и классы (+ interface impl).
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
            // В C# хэндлы неявно конвертируются в EntityHandle; в Kotlin — явно.
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
            // Хэндлы предвычислены в ownTypeHandle — расхождение сломало бы
            // все ссылки на свои типы.
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
