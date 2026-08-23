package org.kotlindotnet.compiler.pe

import org.kotlindotnet.compiler.il.IlEmitter
import org.kotlindotnet.compiler.il.IlOpcode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.FieldDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.AssemblyVersion
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.BlobEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.ControlFlowBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.InstructionEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.LabelHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataRootBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MethodBodyStreamEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.SignatureCallingConvention
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.SignatureTypeEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addAssembly
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addAssemblyReference
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addFieldDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addInterfaceImplementation
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addMemberReference
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addMethodDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addModule
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addStandaloneSignature
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addTypeReference
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.Characteristics
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.CorFlags
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.ManagedPEBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.PEHeaderBuilder
import java.io.File
import kotlin.uuid.Uuid

/**
 * PE-бэкенд (ADR 0010): реализация [IlEmitter], которая вместо IL-текста
 * строит бинарную сборку через модуль dotnetutils
 * (`MetadataBuilder` + `InstructionEncoder` + `ManagedPEBuilder`).
 *
 * Текстовые member-ref'ы, которые visitor передаёт в
 * [opcode]`(`[`IlOpcode.CALL`]`, "<sig>")`, парсятся здесь в метаданные
 * (TypeRef/MemberRef или MethodDef для вызовов внутри этой же сборки).
 * Формат строк полностью под нашим контролем (StdlibResolver /
 * DotnetIrVisitor), поэтому разбор детерминирован.
 *
 * Формат call-ref (Phase 10):
 *   `retCil [instance ][[Asm]]Ns.Type::name(paramCil, ...)`
 * Маркер `instance ` задаёт HASTHIS в сигнатуре MemberRef (instance-методы,
 * конструкторы). Статические вызовы маркера не имеют. NEWOBJ/CALLVIRT
 * различаются опкодом в [opcode], ref одинаков.
 *
 * Формат field-ref ([IlOpcode.LDFLD]/[IlOpcode.STFLD]):
 *   `cilType [[Asm]]Ns.Type::fieldName`
 *
 * Тела методов буферизуются и кодируются отложенно — в
 * [endContainerClass]: к этому моменту известны все методы файла
 * (контейнер top-level функций + пользовательские классы), и вызовы
 * «вперёд» разрешаются в предвыделенные MethodDef-хэндлы без второго
 * прохода IR. Диапазоны fieldList/methodList каждого типа вычисляются
 * каскадом с конца (пустая группа наследует начало следующей).
 *
 * Отклонения от kotlinc-JVM / текстового пути (ADR 0010, Phase 10):
 * - бэкинг-поля эмитятся Private (как в котлиновском IR), а не Public;
 * - типы своей сборки (базовые классы, интерфейсы, родители MemberRef)
 *   ссылаются напрямую на TypeDef: хэндлы предвычислены, т.к. строки
 *   создаются в фиксированном порядке (&lt;Module&gt;, контейнер, классы);
 *   NIL-scope TypeRef тоже работает (проверено), но неканоничен;
 * - флаги методов без virtual/newslot-нюансов kotlinc (для callvirt не
 *   критично; open/override см. visitor);
 * - default `.ctor` абстрактного sealed-класса не эмитится;
 * - строки идут в #US-кучу (без CIL-экранирования).
 */
class PeIlEmitter : IlEmitter {

    private val metadata = MetadataBuilder()
    private val ilStream = BlobBuilder()
    private val bodyStream = MethodBodyStreamEncoder(ilStream)

    private var moduleInitialized = false

    private var containerNamespace = ""
    private var containerClass = ""
    private var containerOpened = false

    /** Методы контейнерного класса (top-level функции файла). */
    private val containerMethods = mutableListOf<MethodRec>()

    /** Пользовательские классы файла в порядке объявления. */
    private val classes = mutableListOf<ClassRec>()

    private var currentClass: ClassRec? = null
    private var current: MethodRec? = null
    private var entrypoint: MethodDefinitionHandle? = null

    private val labelIds = HashMap<String, Int>()
    private var nextLabelId = 0

    private val assemblyRefs = HashMap<String, AssemblyReferenceHandle>()
    private val typeRefs = HashMap<String, TypeReferenceHandle>()

    private class FieldRec(val cilType: String, val name: String) {
        var handle: FieldDefinitionHandle = MetadataTokens.fieldDefinitionHandle(0)
    }

    private class MethodRec(
        val name: String,
        val retCil: String,
        val paramsCil: List<String>,
        val isEntrypoint: Boolean,
        /** Instance-метод или ctor (HASTHIS в сигнатуре, arg0 = this). */
        val isInstance: Boolean,
        val isCtor: Boolean,
        /** Явные MethodAttributes (open/override); null → по [attributes]. */
        val attributesOverride: UInt? = null,
    ) {
        /** MethodAttributes по умолчанию: ctor `0x1886`, instance `0x0086`, static `0x0096`. */
        val attributes: Int =
            when {
                isCtor -> 0x1886 // Public | HideBySig | SpecialName | RTSpecialName
                isInstance -> 0x0086 // Public | HideBySig
                else -> 0x0096 // Public | Static | HideBySig
            }

        val effectiveAttributes: Int =
            attributesOverride?.toInt() ?: attributes

        val ops = mutableListOf<Op>()
        val locals = mutableListOf<String>()
        var handle: MethodDefinitionHandle = MetadataTokens.methodDefinitionHandle(0)
        var bodyOffset = -1
    }

    private class ClassRec(
        val namespace: String,
        val name: String,
        val flags: UInt,
        val baseTypeCil: String?,
        val interfaces: List<String>,
    ) {
        val fields = mutableListOf<FieldRec>()
        val methods = mutableListOf<MethodRec>()
    }

    private sealed interface Op
    private class InstOp(val code: ILOpCode) : Op

    /** call/callvirt/newobj: код хранится явно (раньше newobj кодировался как call — баг). */
    private class CallOp(val code: ILOpCode, val ref: String) : Op

    /** ldfld/stfld: операнд — текстовый field-ref. */
    private class FieldOp(val code: ILOpCode, val ref: String) : Op
    private class LdStrOp(val value: String) : Op
    private class BranchOp(val code: ILOpCode, val labelId: Int) : Op
    private class MarkOp(val labelId: Int) : Op
    private class TypeOp(val code: ILOpCode, val cilType: String) : Op
    private class LdcI4Op(val value: Int) : Op
    private class LdcI8Op(val value: Long) : Op
    private class LdcR4Op(val value: Float) : Op
    private class LdcR8Op(val value: Double) : Op
    private enum class LocalKind { LDARG, LDLOC, STLOC }
    private class LocalOp(val kind: LocalKind, val index: Int) : Op

    // === IlEmitter: assembly / module ===

    override fun assemblyHeader(assemblyName: String, withRuntime: Boolean) {
        check(!moduleInitialized) { "assemblyHeader called twice" }
        moduleInitialized = true

        metadata.addModule(
            generation = 0,
            moduleName = metadata.getOrAddString(assemblyName),
            mvid = metadata.getOrAddGuid(Uuid.random()),
            encId = MetadataTokens.guidHandle(0),
            encBaseId = MetadataTokens.guidHandle(0),
        )
        metadata.addAssembly(
            name = metadata.getOrAddString(assemblyName),
            version = AssemblyVersion(0, 0, 0, 0),
            culture = MetadataTokens.stringHandle(0),
            publicKey = MetadataTokens.blobHandle(0),
            flags = 0,
            hashAlgorithm = 0u,
        )
    }

    // === Container class (top-level functions) ===

    override fun beginContainerClass(namespace: String, className: String) {
        containerNamespace = namespace
        containerClass = className
        containerOpened = true
    }

    override fun endContainerClass() {
        check(containerOpened) { "endContainerClass without beginContainerClass" }
        containerOpened = false

        // 1. Синтез дефолтного .ctor (D3): K2 всегда кладёт primary ctor в IR
        //    (наблюдение P5), поэтому ветка — страховка от дегенеративного IR.
        //    Интерфейсам конструкторы не нужны (CLR: только abstract/cctor).
        for (cls in classes) {
            if (cls.flags and INTERFACE_FLAG != 0u) continue
            if (cls.methods.any { it.isCtor }) continue
            check(cls.methods.isEmpty()) {
                "class '${cls.name}' has methods but no constructor (unexpected K2 IR)"
            }
            cls.methods.add(synthesizeDefaultCtor(cls))
        }

        // 2. Порядок строк MethodDef == порядок групп: контейнер, затем классы.
        val orderedMethods = buildList {
            addAll(containerMethods)
            classes.forEach { addAll(it.methods) }
        }

        // Предвыделение MethodDef-хэндлов: row id следует порядку вставки,
        // поэтому вызовы «вперёд» разрешаются до кодирования тел.
        orderedMethods.forEachIndexed { i, m ->
            m.handle = MetadataTokens.methodDefinitionHandle(i + 1)
        }

        // 3. Кодирование тел.
        for (m in orderedMethods) {
            m.bodyOffset = encodeBody(m)
        }

        // 4. FieldDef-строки (по классам, в порядке объявления классов).
        for (cls in classes) {
            for (f in cls.fields) {
                f.handle = metadata.addFieldDefinition(
                    attributes = 0x0001, // Private — kotlinc-JVM parity для бэкинг-полей
                    name = metadata.getOrAddString(f.name),
                    signature = fieldSignature(f.cilType),
                )
            }
        }

        // 5. MethodDef-строки.
        for (m in orderedMethods) {
            m.handle = metadata.addMethodDefinition(
                attributes = m.effectiveAttributes,
                implAttributes = 0x0000, // IL | Managed
                name = metadata.getOrAddString(m.name),
                signature = buildSignature(m.retCil, m.paramsCil, isInstance = m.isInstance),
                bodyOffset = m.bodyOffset,
                parameterList = MetadataTokens.parameterHandle(0),
            )
            if (m.isEntrypoint && entrypoint == null) {
                entrypoint = m.handle
            }
        }

        // 6. Вычисление диапазонов групп каскадом с конца: пустая группа
        //    начинает свой диапазон там же, где следующая (пустой диапазон).
        data class Group(
            val namespace: String?,
            val name: String?,
            val flags: UInt?,
            val baseTypeCil: String?,
            val fields: List<FieldRec>,
            val methods: List<MethodRec>,
        )

        val totalFields = classes.sumOf { it.fields.size }
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
                )
            )
            classes.forEach {
                add(Group(it.namespace, it.name, it.flags, it.baseTypeCil, it.fields, it.methods))
            }
        }

        var nextFieldStart = totalFields + 1
        var nextMethodStart = orderedMethods.size + 1
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

        // 7. TypeDef-строки: обязательный <Module>, затем контейнер и классы.
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

    /**
     * D3: дефолтный публичный `.ctor()` — `ldarg.0; call base::.ctor(); ret`.
     */
    private fun synthesizeDefaultCtor(cls: ClassRec): MethodRec {
        val rec = MethodRec(".ctor", "void", emptyList(), isEntrypoint = false, isInstance = true, isCtor = true)
        val base = cls.baseTypeCil ?: "object"
        val baseQualified = if (base.startsWith("[")) base else "[$SYSTEM_RUNTIME_ASSEMBLY]${toBclTypeName(base)}"
        rec.ops.add(LocalOp(LocalKind.LDARG, 0))
        rec.ops.add(CallOp(ILOpCode.CALL, "void instance $baseQualified::.ctor()"))
        rec.ops.add(InstOp(ILOpCode.RET))
        return rec
    }

    // === User classes (Phase 10) ===

    override fun beginClass(
        namespace: String,
        name: String,
        flags: UInt,
        baseTypeCil: String?,
        interfaces: List<String>,
    ) {
        check(currentClass == null) { "nested beginClass ('$name') is not supported" }
        val cls = ClassRec(namespace, name, flags, baseTypeCil, interfaces)
        classes.add(cls)
        currentClass = cls
    }

    override fun endClass() {
        if (currentClass == null) {
            throw IllegalStateException("Contract violation (endClass): no open class")
        }
        currentClass = null
    }

    override fun declareField(cilType: String, name: String, isStatic: Boolean): Int {
        val cls = currentClass
            ?: throw IllegalStateException("Contract violation (declareField): no open class")
        check(!isStatic) { "static fields are not supported until Phase 13" }
        cls.fields.add(FieldRec(cilType, name.removeSurrounding("'")))
        return cls.fields.size - 1
    }

    // === Methods ===

    override fun beginMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isStatic: Boolean,
        isEntrypoint: Boolean,
        attributesOverride: UInt?,
    ) {
        startMethod(
            MethodRec(
                name.removeSurrounding("'"),
                returnType,
                params.map { it.first },
                isEntrypoint,
                isInstance = !isStatic,
                isCtor = false,
                attributesOverride = attributesOverride,
            )
        )
    }

    override fun beginConstructor(params: List<Pair<String, String>>) {
        startMethod(
            MethodRec(".ctor", "void", params.map { it.first }, isEntrypoint = false, isInstance = true, isCtor = true)
        )
    }

    override fun beginStaticMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isEntrypoint: Boolean,
    ) = beginMethod(name, returnType, params, isStatic = true, isEntrypoint = isEntrypoint)

    private fun startMethod(rec: MethodRec) {
        labelIds.clear()
        nextLabelId = 0
        val owner = currentClass
        if (owner != null) owner.methods.add(rec) else containerMethods.add(rec)
        current = rec
    }

    override fun emitLocalsIfAny() {
        // no-op: locals are encoded in encodeBody
    }

    override fun endStaticMethod() {
        // Тело уже забуферизовано; deferred-кодирование — в endContainerClass.
        current = null
    }

    override fun resetMethodState() {
        labelIds.clear()
        current = null
    }

    // === Locals / labels ===

    override fun declareLocal(cilType: String): Int {
        val m = requireCurrent("declareLocal")
        m.locals.add(cilType)
        return m.locals.size - 1
    }

    override fun newLabel(): String {
        val id = nextLabelId++
        val name = "L$id"
        labelIds[name] = id
        return name
    }

    override fun label(name: String) {
        requireCurrent("label").ops.add(MarkOp(requireLabel(name)))
    }

    // === Instructions ===

    override fun opcode(op: IlOpcode, vararg operands: String) {
        val m = requireCurrent("opcode")
        when (op) {
            IlOpcode.CALL, IlOpcode.CALLVIRT, IlOpcode.NEWOBJ ->
                m.ops.add(CallOp(mapOpcode(op), operands.single()))
            IlOpcode.LDFLD, IlOpcode.STFLD ->
                m.ops.add(FieldOp(mapOpcode(op), operands.single()))
            IlOpcode.LDSTR ->
                m.ops.add(LdStrOp(operands.single()))
            IlOpcode.BOX, IlOpcode.NEWARR ->
                m.ops.add(TypeOp(mapOpcode(op), operands.single()))
            IlOpcode.BR, IlOpcode.BRTRUE, IlOpcode.BRFALSE ->
                m.ops.add(BranchOp(mapOpcode(op), requireLabel(operands.single())))
            else -> m.ops.add(InstOp(mapOpcode(op)))
        }
    }

    override fun ldcI4(value: Int) {
        requireCurrent("ldcI4").ops.add(LdcI4Op(value))
    }
    override fun ldcI8(value: Long) {
        requireCurrent("ldcI8").ops.add(LdcI8Op(value))
    }
    override fun ldcR4(value: Float) {
        requireCurrent("ldcR4").ops.add(LdcR4Op(value))
    }
    override fun ldcR8(value: Double) {
        requireCurrent("ldcR8").ops.add(LdcR8Op(value))
    }
    override fun ldarg(index: Int) {
        requireCurrent("ldarg").ops.add(LocalOp(LocalKind.LDARG, index))
    }
    override fun ldloc(index: Int) {
        requireCurrent("ldloc").ops.add(LocalOp(LocalKind.LDLOC, index))
    }
    override fun stloc(index: Int) {
        requireCurrent("stloc").ops.add(LocalOp(LocalKind.STLOC, index))
    }

    override fun ldstr(s: String) {
        requireCurrent("ldstr").ops.add(LdStrOp(s))
    }

    override fun br(label: String) {
        requireCurrent("br").ops.add(BranchOp(ILOpCode.BR, requireLabel(label)))
    }
    override fun brtrue(label: String) {
        requireCurrent("brtrue").ops.add(BranchOp(ILOpCode.BRTRUE, requireLabel(label)))
    }
    override fun brfalse(label: String) {
        requireCurrent("brfalse").ops.add(BranchOp(ILOpCode.BRFALSE, requireLabel(label)))
    }

    override fun box(cilType: String) {
        requireCurrent("box").ops.add(TypeOp(ILOpCode.BOX, cilType))
    }
    override fun pop() {
        requireCurrent("pop").ops.add(InstOp(ILOpCode.POP))
    }
    override fun dup() {
        requireCurrent("dup").ops.add(InstOp(ILOpCode.DUP))
    }
    override fun newarr(cilType: String) {
        requireCurrent("newarr").ops.add(TypeOp(ILOpCode.NEWARR, cilType))
    }
    override fun stelemRef() {
        requireCurrent("stelemRef").ops.add(InstOp(ILOpCode.STELEM_REF))
    }

    // === Result ===

    override fun text(): String =
        throw UnsupportedOperationException(
            "PeIlEmitter produces a binary assembly; use writeAssemblyTo() instead of text()"
        )

    /** Сериализует собранную сборку в PE-файл (.exe/.dll). */
    fun writeAssemblyTo(outputFile: File, isExe: Boolean) {
        check(moduleInitialized) { "nothing was emitted (assemblyHeader missing)" }
        check(current == null) { "method is still open" }
        check(!containerOpened) { "container class is still open" }

        val imageCharacteristics =
            Characteristics.EXECUTABLE_IMAGE.value or
                (if (!isExe) Characteristics.DLL.value else 0)

        val header = PEHeaderBuilder(imageCharacteristics = imageCharacteristics)
        val peBuilder =
            ManagedPEBuilder(
                header = header,
                metadataRootBuilder = MetadataRootBuilder(metadata),
                ilStream = ilStream,
                entryPoint = entrypoint ?: MetadataTokens.methodDefinitionHandle(0),
                flags = CorFlags.IL_ONLY.value,
            )

        val peBlob = BlobBuilder()
        peBuilder.serialize(peBlob)

        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(peBlob.toArray())
    }

    // === internals ===

    private fun requireCurrent(what: String): MethodRec =
        current ?: throw IllegalStateException("Contract violation ($what): no open method")

    private fun requireLabel(name: String): Int =
        labelIds[name] ?: throw IllegalStateException("Unknown label '$name' (must be created via newLabel())")

    private fun mapOpcode(op: IlOpcode): ILOpCode =
        ILOpCode.entries.firstOrNull { it.name == op.name }
            ?: throw IllegalStateException("No ILOpCode counterpart for ${op.name}")

    private fun encodeBody(m: MethodRec): Int {
        val codeBuilder = BlobBuilder()
        val flow = ControlFlowBuilder()
        val encoder = InstructionEncoder(codeBuilder, flow)
        val handles = HashMap<Int, LabelHandle>()

        fun labelHandle(id: Int): LabelHandle = handles.getOrPut(id) { encoder.defineLabel() }

        for (op in m.ops) {
            when (op) {
                is InstOp -> encoder.opCode(op.code)
                is LdStrOp -> encoder.loadString(metadata.getOrAddUserString(op.value))
                is BranchOp -> encoder.branch(op.code, labelHandle(op.labelId))
                is MarkOp -> encoder.markLabel(labelHandle(op.labelId))
                is CallOp -> {
                    encoder.opCode(op.code)
                    encoder.token(resolveCallTarget(op.ref))
                }
                is FieldOp -> {
                    encoder.opCode(op.code)
                    encoder.token(fieldRefToken(op.ref))
                }
                is TypeOp -> {
                    // IL-порядок: сначала байт опкода, затем токен-операнд.
                    encoder.opCode(op.code)
                    encoder.token(boxedTypeToken(op.cilType))
                }
                is LdcI4Op -> encoder.loadConstantI4(op.value)
                is LdcI8Op -> encoder.loadConstantI8(op.value)
                is LdcR4Op -> encoder.loadConstantR4(op.value)
                is LdcR8Op -> encoder.loadConstantR8(op.value)
                is LocalOp ->
                    when (op.kind) {
                        LocalKind.LDARG -> encoder.loadArgument(op.index)
                        LocalKind.LDLOC -> encoder.loadLocal(op.index)
                        LocalKind.STLOC -> encoder.storeLocal(op.index)
                    }
            }
        }

        val localsSig: StandaloneSignatureHandle =
            if (m.locals.isEmpty()) {
                MetadataTokens.standaloneSignatureHandle(0)
            } else {
                val b = BlobBuilder()
                val lv = BlobEncoder(b).localVariableSignature(m.locals.size)
                for (t in m.locals) {
                    applyCilType(lv.addVariable().type(), t)
                }
                metadata.addStandaloneSignature(metadata.getOrAddBlob(b))
            }

        return bodyStream.addMethodBody(encoder, localVariablesSignature = localsSig)
    }

    // --- signatures ---

    private fun buildSignature(retCil: String, paramsCil: List<String>, isInstance: Boolean = false): BlobHandle {
        val b = BlobBuilder()
        BlobEncoder(b).methodSignature(
            convention = SignatureCallingConvention.DEFAULT,
            genericParameterCount = 0,
            isInstanceMethod = isInstance,
        ).parameters(
            paramsCil.size,
            returnType = { rt ->
                if (retCil == "void") rt.void() else applyCilType(rt.type(), retCil)
            },
            parameters = { ps ->
                for (p in paramsCil) {
                    applyCilType(ps.addParameter().type(), p)
                }
            },
        )
        return metadata.getOrAddBlob(b)
    }

    private fun fieldSignature(cilType: String): BlobHandle {
        val b = BlobBuilder()
        applyCilType(BlobEncoder(b).fieldSignature(), cilType)
        return metadata.getOrAddBlob(b)
    }

    private fun applyCilType(enc: SignatureTypeEncoder, cilType: String) {
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

    private fun boxedTypeToken(cilType: String): EntityHandle {
        val (ns, name) = when (cilType) {
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
            else -> throw IllegalStateException("PE backend: unsupported CIL type '$cilType'")
        }
        return typeRef(SYSTEM_RUNTIME_ASSEMBLY, ns, name).toEntityHandle()
    }

    // --- references ---

    private data class TypeNameInfo(
        val assembly: String?,
        val namespace: String,
        val typeName: String,
    )

    /**
     * Разбирает имя типа: `[Asm]Ns.Name`, `Ns.Name` или `Name`
     * (nil-scope ⇒ тип из текущего модуля).
     */
    private fun parseTypeName(s: String): TypeNameInfo {
        var work = s.trim().removeSurrounding("'")
        val asmMatch = Regex("""^\[([^\]]+)\]\s*(.*)$""").find(work)
        val assembly: String?
        if (asmMatch != null) {
            assembly = asmMatch.groupValues[1]
            work = asmMatch.groupValues[2]
        } else {
            assembly = null
        }
        work = work.trim()
        val dotIdx = work.lastIndexOf('.')
        val ns = if (dotIdx >= 0) work.substring(0, dotIdx) else ""
        val name = if (dotIdx >= 0) work.substring(dotIdx + 1) else work
        return TypeNameInfo(assembly, ns, name)
    }

    private data class CallInfo(
        val retCil: String,
        val isInstance: Boolean,
        val assembly: String?,
        val namespace: String,
        val typeName: String,
        val methodName: String,
        val paramsCil: List<String>,
    )

    /**
     * Разбирает текстовый member-ref формата
     * `retType [instance ][[Asm]]Ns.Type::name(p1, p2)` (формат задаётся
     * StdlibResolver/DotnetIrVisitor — см. классовую документацию).
     */
    private fun parseCallRef(ref: String): CallInfo {
        val work = ref.trim()

        val retAndRest = Regex("""^(\S+)\s+(.+)$""").find(work)
            ?: throw IllegalStateException("Cannot parse call ref: '$ref'")
        val retCil = retAndRest.groupValues[1]
        var rest = retAndRest.groupValues[2]

        // Маркер HASTHIS стоит после типа возврата: "void instance Ns.C::m(...)".
        val isInstance = rest.startsWith("instance ")
        if (isInstance) rest = rest.removePrefix("instance ").trim()

        val asmMatch = Regex("""^\[([^\]]+)\]\s*(.*)$""").find(rest)
        val assembly: String?
        if (asmMatch != null) {
            assembly = asmMatch.groupValues[1]
            rest = asmMatch.groupValues[2]
        } else {
            assembly = null
        }

        val sigMatch = Regex("""^(.+?)::(.+?)\s*\((.*)\)\s*$""").find(rest)
            ?: throw IllegalStateException("Cannot parse call ref member: '$ref'")
        // Имена могут приходить в CIL-экранировании ('box') — оно нужно
        // только ilasm-тексту; в метаданных пишем сырое имя.
        val methodName = sigMatch.groupValues[2].trim().removeSurrounding("'")
        val paramsRaw = sigMatch.groupValues[3].trim()

        val qualified = sigMatch.groupValues[1].trim().removeSurrounding("'")
        val dotIdx = qualified.lastIndexOf('.')
        val ns = if (dotIdx >= 0) qualified.substring(0, dotIdx) else ""
        val typeName = if (dotIdx >= 0) qualified.substring(dotIdx + 1) else qualified

        val paramsCil =
            if (paramsRaw.isEmpty()) emptyList() else paramsRaw.split(',').map { it.trim() }

        return CallInfo(retCil, isInstance, assembly, ns, typeName, methodName, paramsCil)
    }

    /**
     * Разрешает call/callvirt/newobj-ref в токен.
     *
     * Свои методы (контейнер + классы этой сборки, assembly == null)
     * разрешаются напрямую в MethodDef (хэндлы предвыделены);
     * внешние — в MemberRef поверх TypeRef указанной сборки.
     */
    private fun resolveCallTarget(ref: String): EntityHandle {
        val info = parseCallRef(ref)

        if (info.assembly == null) {
            findOwnMethod(info)?.let { return it.handle.toEntityHandle() }
            val known = buildString {
                append("container=${containerNamespace}.${containerClass}[")
                append(containerMethods.joinToString(",") { "${it.name}(${it.paramsCil.joinToString("/")})" })
                append("]")
                for (c in classes) {
                    append(" class=${c.namespace}.${c.name}[")
                    append(c.methods.joinToString(",") { "${it.name}(${it.paramsCil.joinToString("/")})" })
                    append("]")
                }
            }
            throw IllegalStateException("Call to unknown same-module method: '$ref'; known: $known")
        }

        val parent = typeRef(info.assembly, info.namespace, info.typeName).toEntityHandle()
        return metadata.addMemberReference(
            parent = parent,
            name = metadata.getOrAddString(info.methodName),
            signature = buildSignature(info.retCil, info.paramsCil, isInstance = info.isInstance),
        ).toEntityHandle()
    }

    private fun findOwnMethod(info: CallInfo): MethodRec? {
        fun match(list: List<MethodRec>): MethodRec? =
            list.firstOrNull { it.name == info.methodName && it.paramsCil == info.paramsCil }
        match(containerMethods)?.let { return it }
        for (cls in classes) {
            if (cls.namespace == info.namespace && cls.name == info.typeName) {
                match(cls.methods)?.let { return it }
            }
        }
        return null
    }

    /**
     * Разрешает имя типа в EntityHandle (TypeDefOrRef).
     *
     * Свои классы — ПРЯМО TypeDef: хэндлы предвычислены (порядок строк
     * фиксирован: &lt;Module&gt;, контейнер, классы), что канонично для
     * csc/ilasm и не требует отдельных TypeRef-строк. NIL-scope TypeRef
     * для типов текущего модуля спецификацией допустим и эмпирически
     * работает на net10 CLR (проверено экспериментом, см.
     * TODO/PHASE-10.md §«Итоги»), но компиляторами не используется —
     * выбран неканоничный вариант не был.
     * Внешние типы — TypeRef указанной сборки.
     */
    private fun resolveTypeEntity(cilType: String): EntityHandle {
        ownTypeHandle(cilType)?.let { return it.toEntityHandle() }
        val t = parseTypeName(cilType)
        val asm = t.assembly ?: SYSTEM_RUNTIME_ASSEMBLY
        return typeRef(asm, t.namespace, t.typeName).toEntityHandle()
    }

    /**
     * TypeDef-хэндл своего класса по имени (`Ns.Name`, без скобочной сборки),
     * либо null. Row id предвычисляется: 1=&lt;Module&gt;, 2=контейнер,
     * классы — далее по порядку объявления.
     */
    private fun ownTypeHandle(cilType: String): TypeDefinitionHandle? {
        val t = parseTypeName(cilType)
        if (t.assembly != null) return null
        val idx = classes.indexOfFirst { it.namespace == t.namespace && it.name == t.typeName }
        return if (idx >= 0) MetadataTokens.typeDefinitionHandle(idx + 3) else null
    }

    private fun resolveBaseEntity(baseTypeCil: String?): EntityHandle {
        if (baseTypeCil == null || baseTypeCil == "object") {
            return systemObjectRef().toEntityHandle()
        }
        return resolveTypeEntity(baseTypeCil)
    }

    private fun fieldRefToken(ref: String): EntityHandle {
        val sp = ref.indexOf(' ')
        require(sp > 0) { "Cannot parse field ref: '$ref'" }
        val cilType = ref.substring(0, sp).trim()
        val member = ref.substring(sp + 1).trim()
        val idx = member.lastIndexOf("::")
        require(idx > 0) { "Cannot parse field ref member: '$ref'" }
        val typePart = member.substring(0, idx).trim()
        val fieldName = member.substring(idx + 2).trim().removeSurrounding("'")

        val parent = resolveTypeEntity(typePart)
        val b = BlobBuilder()
        applyCilType(BlobEncoder(b).fieldSignature(), cilType)
        return metadata.addMemberReference(
            parent = parent,
            name = metadata.getOrAddString(fieldName),
            signature = metadata.getOrAddBlob(b),
        ).toEntityHandle()
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

    /**
     * Мэппинг CIL-алиасов примитивов на BCL-типы для синтезированного
     * дефолтного ctor'а (базовый тип задан алиасом или квалифицированным именем).
     */
    private fun toBclTypeName(cilType: String): String =
        when (cilType) {
            "object" -> "System.Object"
            "string" -> "System.String"
            else -> cilType
        }

    private companion object {
        const val SYSTEM_RUNTIME_ASSEMBLY = "System.Runtime"

        /** TypeAttributes.Interface (используется для extends=nil у интерфейсов). */
        const val INTERFACE_FLAG: UInt = 0x0020u

        /** PublicKeyToken BCL-сборок (b03f5f7f11d50a3a). */
        val BCL_PUBLIC_KEY_TOKEN =
            byteArrayOf(
                0xB0.toByte(), 0x3F, 0x5F, 0x7F, 0x11, 0xD5.toByte(), 0x0A, 0x3A,
            )
    }
}
