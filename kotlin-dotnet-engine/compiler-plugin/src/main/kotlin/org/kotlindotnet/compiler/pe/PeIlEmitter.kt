package org.kotlindotnet.compiler.pe

import org.kotlindotnet.compiler.il.IlEmitter
import org.kotlindotnet.compiler.il.IlOpcode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.MethodDefinitionHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StringHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.TypeReferenceHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle
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
 * (TypeRef/MemberRef или MethodDef для вызовов внутри контейнерного класса).
 * Формат строк полностью под нашим контролем (StdlibResolver /
 * DotnetIrVisitor.emitUserTopLevelCall), поэтому разбор детерминирован.
 *
 * Тела методов буферизуются и кодируются отложенно — в
 * [endContainerClass]: к этому моменту известны все методы файла, и
 * вызовы «вперёд» разрешаются в MethodDef-хэндлы без второго прохода IR.
 *
 * Отклонения от текстового пути (см. ADR 0010):
 * - default `.ctor` абстрактного sealed-класса не эмитится;
 * - base type — `[System.Runtime]System.Object` (вместо mscorlib);
 * - строки идут в #US-кучу (без CIL-экранирования).
 */
class PeIlEmitter : IlEmitter {

    private val metadata = MetadataBuilder()
    private val ilStream = BlobBuilder()
    private val bodyStream = MethodBodyStreamEncoder(ilStream)

    private var moduleInitialized = false
    private var containerNamespace = ""
    private var containerClass = ""

    private val methods = mutableListOf<MethodRec>()
    private var current: MethodRec? = null
    private var entrypoint: MethodDefinitionHandle? = null

    private val labelIds = HashMap<String, Int>()
    private var nextLabelId = 0

    private val assemblyRefs = HashMap<String, org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle>()
    private val typeRefs = HashMap<String, TypeReferenceHandle>()

    private class MethodRec(
        val name: String,
        val retCil: String,
        val paramsCil: List<String>,
        val isEntrypoint: Boolean,
    ) {
        val ops = mutableListOf<Op>()
        val locals = mutableListOf<String>()
        var handle: MethodDefinitionHandle = MetadataTokens.methodDefinitionHandle(0)
        var bodyOffset = -1
    }

    private sealed interface Op
    private class InstOp(val code: ILOpCode) : Op
    private class CallOp(val ref: String) : Op
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

    // === Container class ===

    override fun beginContainerClass(namespace: String, className: String) {
        containerNamespace = namespace
        containerClass = className
    }

    override fun endContainerClass() {
        // 1. Preallocate MethodDef handles: row ids follow insertion order,
        //    so forward user-calls can be resolved before bodies are encoded.
        methods.forEachIndexed { i, m -> m.handle = MetadataTokens.methodDefinitionHandle(i + 1) }

        // 2. Encode bodies (resolves own-method calls via preallocated handles).
        for (m in methods) {
            m.bodyOffset = encodeBody(m)
            m.handle =
                metadata.addMethodDefinition(
                    attributes = 0x0096, // Public | Static | HideBySig
                    implAttributes = 0x0000, // IL | Managed
                    name = metadata.getOrAddString(m.name),
                    signature = buildSignature(m.retCil, m.paramsCil),
                    bodyOffset = m.bodyOffset,
                    parameterList = MetadataTokens.parameterHandle(0),
                )
            if (m.isEntrypoint && entrypoint == null) {
                entrypoint = m.handle
            }
        }

        // 3. Type definitions: mandatory <Module> row first, then the
        //    top-level functions container (public abstract sealed auto ansi
        //    beforefieldinit — parity with the ilasm path).
        val methodList = methods.firstOrNull()?.handle ?: MetadataTokens.methodDefinitionHandle(1)
        metadata.addTypeDefinition(
            attributes = 0u,
            namespace = MetadataTokens.stringHandle(0),
            name = metadata.getOrAddString("<Module>"),
            baseType = EntityHandle.NIL,
            fieldList = MetadataTokens.fieldDefinitionHandle(1),
            methodList = methodList,
        )
        metadata.addTypeDefinition(
            attributes = 0x00100181u, // Public | Abstract | Sealed | BeforeFieldInit
            namespace = if (containerNamespace.isEmpty()) MetadataTokens.stringHandle(0) else metadata.getOrAddString(containerNamespace),
            name = metadata.getOrAddString(containerClass),
            baseType = systemObjectRef().toEntityHandle(),
            fieldList = MetadataTokens.fieldDefinitionHandle(1),
            methodList = methodList,
        )
    }

    // === Static method ===

    override fun beginStaticMethod(
        name: String,
        returnType: String,
        params: List<Pair<String, String>>,
        isEntrypoint: Boolean,
    ) {
        labelIds.clear()
        nextLabelId = 0
        val rec = MethodRec(name, returnType, params.map { it.second }, isEntrypoint)
        methods.add(rec)
        current = rec
    }

    override fun emitLocalsIfAny() {
        // no-op: locals are encoded in encodeBody
    }

    override fun endStaticMethod() {
        // Тело уже забуферизовано в methods; deferred-кодирование — в
        // endContainerClass.
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
                m.ops.add(CallOp(operands.single()))
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
                is CallOp -> encoder.call(resolveCallTarget(op.ref))
                is TypeOp -> {
                    encoder.token(boxedTypeToken(op.cilType))
                    encoder.opCode(op.code)
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

    private fun buildSignature(retCil: String, paramsCil: List<String>): BlobHandle {
        val b = BlobBuilder()
        BlobEncoder(b).methodSignature(
            convention = SignatureCallingConvention.DEFAULT,
            genericParameterCount = 0,
            isInstanceMethod = false,
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
            else -> throw IllegalStateException("PE backend: unsupported CIL type '$cilType'")
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
        return typeRef("System.Runtime", ns, name).toEntityHandle()
    }

    // --- references ---

    private data class CallInfo(
        val retCil: String,
        val assembly: String?,
        val namespace: String,
        val typeName: String,
        val methodName: String,
        val paramsCil: List<String>,
    )

    /**
     * Разбирает текстовый member-ref формата
     * `retType [Asm]Ns.Type::name(p1, p2)` (формат задаётся
     * StdlibResolver/DotnetIrVisitor — см. классовую документацию).
     */
    private fun parseCallRef(ref: String): CallInfo {
        val retAndRest = Regex("""^\s*(\S+)\s+(.+)$""").find(ref)
            ?: throw IllegalStateException("Cannot parse call ref: '$ref'")
        val retCil = retAndRest.groupValues[1]
        var rest = retAndRest.groupValues[2]

        val asmMatch = Regex("""^\[([^\]]+)\]\s*(.*)$""").find(rest)
        val assembly: String?
        if (asmMatch != null) {
            assembly = asmMatch.groupValues[1]
            rest = asmMatch.groupValues[2]
        } else {
            assembly = null
        }

        val sigMatch = Regex("""^(.+?)::([A-Za-z_][\w.$<>]*)\s*\((.*)\)\s*$""").find(rest)
            ?: throw IllegalStateException("Cannot parse call ref member: '$ref'")
        val qualified = sigMatch.groupValues[1].trim()
        val methodName = sigMatch.groupValues[2]
        val paramsRaw = sigMatch.groupValues[3].trim()

        val dotIdx = qualified.lastIndexOf('.')
        val ns = if (dotIdx >= 0) qualified.substring(0, dotIdx) else ""
        val typeName = if (dotIdx >= 0) qualified.substring(dotIdx + 1) else qualified

        val paramsCil =
            if (paramsRaw.isEmpty()) emptyList() else paramsRaw.split(',').map { it.trim() }

        return CallInfo(retCil, assembly, ns, typeName, methodName, paramsCil)
    }

    private fun resolveCallTarget(ref: String): EntityHandle {
        val info = parseCallRef(ref)

        // Вызов собственной top-level функции: тот же файл (контейнерный
        // класс), без скобочной сборки. Хэндлы уже предвыделены в
        // endContainerClass.
        if (info.assembly == null &&
            info.namespace == containerNamespace &&
            info.typeName == containerClass
        ) {
            val target = methods.firstOrNull { it.name == info.methodName }
                ?: throw IllegalStateException("Call to unknown own method '${info.methodName}'")
            return target.handle.toEntityHandle()
        }

        val parent = typeRef(info.assembly ?: "", info.namespace, info.typeName).toEntityHandle()
        return metadata.addMemberReference(
            parent = parent,
            name = metadata.getOrAddString(info.methodName),
            signature = buildSignature(info.retCil, info.paramsCil),
        ).toEntityHandle()
    }

    private fun assemblyRef(name: String): org.kotlindotnet.dotnetutils.system.reflection.metadata.AssemblyReferenceHandle {
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
        const val SYSTEM_RUNTIME_ASSEMBLY = "System.Runtime"

        /** PublicKeyToken BCL-сборок (b03f5f7f11d50a3a). */
        val BCL_PUBLIC_KEY_TOKEN =
            byteArrayOf(
                0xB0.toByte(), 0x3F, 0x5F, 0x7F, 0x11, 0xD5.toByte(), 0x0A, 0x3A,
            )
    }
}
