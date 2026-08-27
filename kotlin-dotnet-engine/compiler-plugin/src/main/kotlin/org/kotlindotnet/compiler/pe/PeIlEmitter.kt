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
 * PE backend (ADR 0010): an [IlEmitter] implementation that builds a binary
 * assembly via dotnetutils (`MetadataBuilder` + `InstructionEncoder` +
 * `ManagedPEBuilder`). This class owns the [IlEmitter] contract, the row
 * insertion order, and PE serialization; the other responsibilities are split
 * out (J-01): [PeRecords] — the row model, [MemberRefParser] — parsing of text
 * references (the format is documented there), [PeSignatures] — signatures,
 * [PeBodyEncoder] — Op buffer → IL stream, [PeTypeDefWriter] — TypeDef
 * finalization, [PeReferenceResolver] — operand resolution + TypeRef/
 * AssemblyRef caches.
 *
 * Method bodies are buffered ([Op] lists in [MethodRec]) and encoded lazily in
 * [endContainerClass]: by that point every method of the file is known, and
 * "forward" calls resolve into preallocated MethodDef handles without a second
 * IR pass. Deviations from kotlinc-JVM (Private fields, TypeDef instead of the
 * NIL-scope TypeRef for own types, etc.) are covered by ADR 0010 and
 * TODO/PHASE-10.md §"Summary".
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

    /** Operand resolution and the TypeRef/AssemblyRef cache (model access injected). */
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

    /** Methods of the container class (the file's top-level functions). */
    private val containerMethods = mutableListOf<MethodRec>()

    /** The file's user-defined classes in declaration order. */
    private val classes = mutableListOf<ClassRec>()

    private var currentClass: ClassRec? = null
    private var current: MethodRec? = null
    private var entrypoint: MethodDefinitionHandle? = null

    private val labelIds = HashMap<String, Int>()
    private var nextLabelId = 0

    /** The assembly MVID as bytes — the Id of the #Pdb stream. */
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

        // 1. Synthesize default .ctors for classes without constructors.
        PeTypeDefWriter.synthesizeDefaultCtors(classes)

        // 2. MethodDef row order == group order: the container first, then classes.
        val orderedMethods = buildList {
            addAll(containerMethods)
            classes.forEach { addAll(it.methods) }
        }

        // Preallocate MethodDef handles: the row id follows insertion order,
        // so "forward" calls resolve before body encoding.
        orderedMethods.forEachIndexed { i, m ->
            m.handle = MetadataTokens.methodDefinitionHandle(i + 1)
        }

        // 3. Encode bodies (+ capture sequence points).
        for (m in orderedMethods) {
            val r = bodyEncoder.encode(m)
            m.bodyOffset = r.bodyOffset
            m.seqPoints.clear(); m.seqPoints.addAll(r.sequencePoints)
        }

        // 4. FieldDef rows (per class, in class declaration order).
        for (cls in classes) {
            for (f in cls.fields) {
                f.handle = metadata.addFieldDefinition(
                    attributes = 0x0001, // Private — kotlinc-JVM parity for backing fields
                    name = metadata.getOrAddString(f.name),
                    signature = PeSignatures.fieldSignature(metadata, refs::resolveTypeEntity, f.cilType),
                )
            }
        }

        // 5. MethodDef rows.
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

        // 6–7. Group ranges and TypeDef rows (+ interface impl).
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
        // The body is already buffered; deferred encoding happens in endContainerClass.
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

    /** Serializes the assembled assembly into a PE file (.exe/.dll). */
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

        // Standalone portable PDB → a PE image (as in Roslyn): metadata without IL.
        val image = PdbBuilder(pdbMetadata, mvid, entrypointToken).build()

        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(image.toArray())
    }

    /** Methods with preallocated row ids (the MethodDef table order). */
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
     * The row id of an own class by name (`Ns.Name`, without the bracketed
     * assembly), or null. The row id is precomputed: 1=<Module>, 2=the container,
     * then classes in declaration order.
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
         * System.Diagnostics.DebuggingModes for a debug build:
         * Default | IgnoreSymbolStoreSequencePoints | EnableEditAndContinue |
         * DisableOptimizations (= csc /debug+).
         */
        const val DEBUGGABLE_MODES_DEBUG: Int = 0x0001 or 0x0002 or 0x0004 or 0x0100
    }
}
