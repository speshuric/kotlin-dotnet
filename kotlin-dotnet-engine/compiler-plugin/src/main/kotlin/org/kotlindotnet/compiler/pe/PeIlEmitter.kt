package org.kotlindotnet.compiler.pe

import org.kotlindotnet.compiler.il.IlEmitter
import org.kotlindotnet.compiler.il.IlOpcode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addDocument
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addMethodDebugInformation
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addLocalScope
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addLocalVariable
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.PdbBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.Characteristics
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.ManagedPEBuilder
import org.kotlindotnet.dotnetutils.system.reflection.portableexecutable.PEHeaderBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.AssemblyVersion
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MethodBodyStreamEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addMethodDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addAssembly
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addFieldDefinition
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addModule
import java.io.File
import kotlin.uuid.Uuid

/**
 * PE-бэкенд (ADR 0010): реализация [IlEmitter] — строит бинарную сборку
 * через dotnetutils (`MetadataBuilder` + `InstructionEncoder` +
 * `ManagedPEBuilder`). Здесь — контракт [IlEmitter], порядок вставки
 * строк и PE-сериализация; остальные ответственности выделены (J-01):
 * [PeRecords] — модель строк, [MemberRefParser] — парсинг текстовых
 * ссылок (формат — в его документации), [PeSignatures] — сигнатуры,
 * [PeBodyEncoder] — Op-буфер → IL-поток, [PeTypeDefWriter] — финализация
 * TypeDef, [PeReferenceResolver] — резолв операндов + кэши TypeRef/
 * AssemblyRef.
 *
 * Тела методов буферизуются ([Op]-списки в [MethodRec]) и кодируются
 * отложенно — в [endContainerClass]: к этому моменту известны все методы
 * файла, вызовы «вперёд» разрешаются в предвыделенные MethodDef-хэндлы
 * без второго прохода IR. Отклонения от kotlinc-JVM (Private-поля,
 * TypeDef вместо NIL-scope TypeRef для своих типов и т.д.) — ADR 0010,
 * TODO/PHASE-10.md §«Итоги».
 */
class PeIlEmitter(
    /** Debug build → assembly-level DebuggableAttribute(0x0107). */
    private val isDebug: Boolean = false,
    /** Source file path, used as the PDB Document entry. */
    private val sourceFile: String? = null,
) : IlEmitter {

    private val metadata = MetadataBuilder()
    private val ilStream = BlobBuilder()
    private val bodyStream = MethodBodyStreamEncoder(ilStream)

    /** Резолв операндов и кэш TypeRef/AssemblyRef (инъекция доступа к модели). */
    private val refs =
        PeReferenceResolver(
            metadata,
            findOwnMethod = ::findOwnMethod,
            describeOwnMethods = ::describeOwnMethods,
            findOwnTypeDefRow = ::findOwnTypeDefRow,
        )

    private val bodyEncoder =
        PeBodyEncoder(
            metadata,
            bodyStream,
            resolveCallTarget = refs::resolveCallTarget,
            resolveFieldRef = refs::fieldRefToken,
            resolveTypeEntity = refs::resolveTypeEntity,
        )

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

    /** MVID сборки в байтах — Id потока #Pdb. */
    private var mvidBytes: ByteArray? = null


    // === IlEmitter: assembly / module ===

    override fun assemblyHeader(assemblyName: String, withRuntime: Boolean) {
        check(!moduleInitialized) { "assemblyHeader called twice" }
        moduleInitialized = true

        metadata.addModule(
            generation = 0,
            moduleName = metadata.getOrAddString(assemblyName),
            mvid = Uuid.random().also { mvidBytes = it.toByteArray() }.let { metadata.getOrAddGuid(it) },
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

        // 1. Синтез дефолтных .ctor для классов без конструкторов.
        PeTypeDefWriter.synthesizeDefaultCtors(classes)

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

        // 3. Кодирование тел (+ захват sequence points).
        for (m in orderedMethods) {
            val r = bodyEncoder.encode(m)
            m.bodyOffset = r.bodyOffset
            m.seqPoints.clear(); m.seqPoints.addAll(r.sequencePoints)
        }

        // 4. FieldDef-строки (по классам, в порядке объявления классов).
        for (cls in classes) {
            for (f in cls.fields) {
                f.handle = metadata.addFieldDefinition(
                    attributes = 0x0001, // Private — kotlinc-JVM parity для бэкинг-полей
                    name = metadata.getOrAddString(f.name),
                    signature = PeSignatures.fieldSignature(metadata, refs::resolveTypeEntity, f.cilType),
                )
            }
        }

        // 5. MethodDef-строки.
        for (m in orderedMethods) {
            m.handle = metadata.addMethodDefinition(
                attributes = m.effectiveAttributes,
                implAttributes = 0x0000, // IL | Managed
                name = metadata.getOrAddString(m.name),
                signature = PeSignatures.buildSignature(
                    metadata, refs::resolveTypeEntity, m.retCil, m.paramsCil, isInstance = m.isInstance,
                ),
                bodyOffset = m.bodyOffset,
                parameterList = MetadataTokens.parameterHandle(0),
            )
            if (m.isEntrypoint && entrypoint == null) {
                entrypoint = m.handle
            }
        }

        // 6–7. Диапазоны групп и TypeDef-строки (+ interface impl).
        PeTypeDefWriter.writeTypeDefinitions(
            metadata = metadata,
            containerNamespace = containerNamespace,
            containerClass = containerClass,
            containerMethods = containerMethods,
            classes = classes,
            totalMethodRows = orderedMethods.size,
            resolveBaseEntity = refs::resolveBaseEntity,
            resolveTypeEntity = refs::resolveTypeEntity,
        )

        // 8. Assembly-level attributes: Debuggable for debug builds.
        if (isDebug) {
            refs.addAssemblyDebuggableAttribute(DEBUGGABLE_MODES_DEBUG)
        }
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
        cls.fields.add(FieldRec(cilType, name))
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
                name,
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

    private fun startMethod(rec: MethodRec) {
        labelIds.clear()
        nextLabelId = 0
        val owner = currentClass
        if (owner != null) owner.methods.add(rec) else containerMethods.add(rec)
        current = rec
    }

    override fun emitLocalsIfAny() {
        // no-op: locals are encoded in PeBodyEncoder
    }

    override fun endMethod() {
        // Тело уже забуферизовано; deferred-кодирование — в endContainerClass.
        current = null
    }

    override fun resetMethodState() {
        labelIds.clear()
        current = null
    }

    // === Locals / labels ===

    override fun declareLocal(cilType: String, name: String?): Int {
        val m = requireCurrent("declareLocal")
        m.locals.add(LocalRec(cilType, name))
        return m.locals.size - 1
    }

    override fun markSequencePoint(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) {
        requireCurrent("markSequencePoint").ops.add(SeqPointOp(startLine, startColumn, endLine, endColumn))
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

    /** Сериализует собранную сборку в PE-файл (.exe/.dll). */
    fun writeAssemblyTo(outputFile: File, isExe: Boolean) {
        check(moduleInitialized) { "nothing was emitted (assemblyHeader missing)" }
        check(current == null) { "method is still open" }
        check(!containerOpened) { "container class is still open" }

        PeImageWriter.serialize(
            metadata = metadata,
            ilStream = ilStream,
            entrypoint = entrypoint ?: MetadataTokens.methodDefinitionHandle(0),
            outputFile = outputFile,
            isExe = isExe,
        )
    }

    /**
     * Writes a sidecar portable PDB: one Document for the source,
     * MethodDebugInformation with sequence points, LocalScope/LocalVariable.
     * Call after [writeAssemblyTo] in debug mode.
     */
    fun writePortablePdbTo(outputFile: File) {
        check(moduleInitialized) { "nothing was emitted (assemblyHeader missing)" }
        val src = sourceFile ?: return
        val mvid = mvidBytes ?: throw IllegalStateException("assemblyHeader missing mvid")

        val pdbMetadata = MetadataBuilder()
        // Standalone PDB contains no Module/Assembly/TypeDef rows - only
        // debug tables and heaps.

        // Document: SHA-256 hash of the source file; language is a
        // project-defined Kotlin GUID.
        // Document.HashAlgorithm / Language GUIDs per Portable PDB spec
        // (dotnet/runtime docs/design/specs/PortablePdb-Metadata.md,
        // section "Document table rows"; local copy:
        // .sources/dotnet-runtime/docs/design/specs/PortablePdb-Metadata.md).
        val hashAlgorithmSha256 = "8829d00f-11b8-4213-878b-770e8597ac16"
        // Kotlin has no registered language GUID; spec allows arbitrary
        // values (readers interpret them freely) - project-defined constant.
        val languageKotlin = "6fa7c4e1-9c0b-4c2e-a1d3-5b7f900ab177"
        val sourceBytes = File(src).readBytes()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(sourceBytes)
        val hashBlob = BlobBuilder().also { it.writeBytes(digest) }
        val hashHandle = pdbMetadata.getOrAddBlob(hashBlob)
        val nameBlob = BlobBuilder().also { PePdbEncoder.encodeDocumentName(pdbMetadata, it, File(src).absolutePath) }
        val document = pdbMetadata.addDocument(
            name = pdbMetadata.getOrAddBlob(nameBlob),
            hashAlgorithm = pdbMetadata.getOrAddGuid(fixedGuid(hashAlgorithmSha256.toString())),
            hash = hashHandle,
            language = pdbMetadata.getOrAddGuid(fixedGuid(languageKotlin.toString())),
        )

        var firstVariableRowId = 1
        for ((methodRowId, rec) in methodsWithRows()) {
            if (rec.seqPoints.isEmpty() && rec.locals.none { it.name != null }) continue

            val meaningfulPoints = rec.seqPoints
                .filter { it.startLine != it.endLine || it.startColumn != it.endColumn }
                .distinctBy { it.offset }
            if (meaningfulPoints.isNotEmpty()) {
                val spBlob = BlobBuilder()
                PePdbEncoder.encodeSequencePoints(
                    spBlob,
                    meaningfulPoints.map {
                        PePdbEncoder.SequencePointRecord(it.offset, it.startLine, it.startColumn, it.endLine, it.endColumn)
                    },
                )
                pdbMetadata.addMethodDebugInformation(
                    document = document,
                    sequencePoints = pdbMetadata.getOrAddBlob(spBlob),
                )
            }

            val namedLocals = rec.locals.filter { it.name != null }
            if (namedLocals.isNotEmpty()) {
                val startVariable = firstVariableRowId
                namedLocals.forEachIndexed { slot, local ->
                    pdbMetadata.addLocalVariable(
                        attributes = 0,
                        index = rec.locals.indexOf(local),
                        name = pdbMetadata.getOrAddString(local.name!!),
                    )
                    firstVariableRowId++
                }
                val maxOffset = rec.seqPoints.maxOfOrNull { it.offset } ?: 0
                pdbMetadata.addLocalScope(
                    method = MetadataTokens.getRowNumber(rec.handle.toEntityHandle()),
                    importScope = 0,
                    variableList = startVariable,
                    constantList = firstVariableRowId,
                    startOffset = 0u,
                    length = (maxOffset + 1).toUInt(),
                )
            }
        }

        val ep = entrypoint
        val entrypointToken =
            if (ep == null) 0 else MetadataTokens.getToken(ep.toEntityHandle())

        // Standalone portable PDB → PE-образ (как в Roslyn): метаданные без IL.
        val image = PdbBuilder(pdbMetadata, mvid, entrypointToken).build()

        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(image.toArray())
    }

    /** Методы с предвыделенными row id (порядок MethodDef таблицы). */
    private fun methodsWithRows(): List<Pair<Int, MethodRec>> = buildList {
        var rowId = 1
        containerMethods.forEach { add(rowId++ to it) }
        classes.forEach { cls -> cls.methods.forEach { add(rowId++ to it) } }
    }

    private fun fixedGuid(hex: String): Uuid {
        val u = java.util.UUID.fromString(hex)
        return Uuid.fromLongs(u.mostSignificantBits, u.leastSignificantBits)
    }

    // === internals: hooks for PeReferenceResolver ===

    private fun findOwnMethod(info: MemberRefParser.CallInfo): MethodRec? {
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

    private fun describeOwnMethods(): String = buildString {
        append("container=${containerNamespace}.${containerClass}[")
        append(containerMethods.joinToString(",") { "${it.name}(${it.paramsCil.joinToString("/")})" })
        append("]")
        for (c in classes) {
            append(" class=${c.namespace}.${c.name}[")
            append(c.methods.joinToString(",") { "${it.name}(${it.paramsCil.joinToString("/")})" })
            append("]")
        }
    }

    /**
     * Row id своего класса по имени (`Ns.Name`, без скобочной сборки),
     * либо null. Row id предвычисляется: 1=<Module>, 2=контейнер,
     * классы — далее по порядку объявления.
     */
    private fun findOwnTypeDefRow(t: MemberRefParser.TypeNameInfo): Int? {
        if (t.assembly != null) return null
        val idx = classes.indexOfFirst { it.namespace == t.namespace && it.name == t.typeName }
        return if (idx >= 0) idx + 3 else null
    }

    private fun requireCurrent(what: String): MethodRec =
        current ?: throw IllegalStateException("Contract violation ($what): no open method")

    private fun requireLabel(name: String): Int =
        labelIds[name] ?: throw IllegalStateException("Unknown label '$name' (must be created via newLabel())")

    private fun mapOpcode(op: IlOpcode): ILOpCode =
        ILOpCode.entries.firstOrNull { it.name == op.name }
            ?: throw IllegalStateException("No ILOpCode counterpart for ${op.name}")

    private companion object {
        /**
         * System.Diagnostics.DebuggingModes для debug-сборки:
         * Default | IgnoreSymbolStoreSequencePoints | EnableEditAndContinue |
         * DisableOptimizations (= csc /debug+).
         */
        const val DEBUGGABLE_MODES_DEBUG: Int = 0x0001 or 0x0002 or 0x0004 or 0x0100
    }
}
