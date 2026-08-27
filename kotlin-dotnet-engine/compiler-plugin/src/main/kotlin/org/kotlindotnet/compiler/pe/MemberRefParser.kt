package org.kotlindotnet.compiler.pe

/**
 * A pure parser of textual member-refs (stateless, knows nothing about the
 * metadata row model — strings in, data out).
 *
 * Call-ref format (Phase 10):
 *   `retCil [instance ][[Asm]]Ns.Type::name(paramCil, ...)`
 * The `instance ` marker sets HASTHIS in the MemberRef signature (instance methods,
 * constructors). Static calls carry no marker. NEWOBJ/CALLVIRT differ by opcode;
 * the ref is identical.
 *
 * Field-ref format ([IlOpcode.LDFLD]/[IlOpcode.STFLD]):
 *   `cilType [[Asm]]Ns.Type::fieldName`
 *
 * Type name format: `[Asm]Ns.Name`, `Ns.Name`, or `Name`
 * (a nil-scope means a type from the current module).
 */
internal object MemberRefParser {

    data class TypeNameInfo(
        val assembly: String?,
        val namespace: String,
        val typeName: String,
    )

    data class CallInfo(
        val retCil: String,
        val isInstance: Boolean,
        val assembly: String?,
        val namespace: String,
        val typeName: String,
        val methodName: String,
        val paramsCil: List<String>,
    )

    data class FieldRefInfo(
        /** The field's type as written in the original ref (assembly/namespace intact). */
        val ownerType: String,
        val cilType: String,
        val assembly: String?,
        val namespace: String,
        val typeName: String,
        val fieldName: String,
    )

    private val ASM_PREFIX = Regex("""^\[([^\]]+)\]\s*(.*)$""")
    private val CALL_MEMBER = Regex("""^(.+?)::(.+?)\s*\((.*)\)\s*$""")

    /** Parses a type name: `[Asm]Ns.Name`, `Ns.Name`, or `Name`. */
    fun parseTypeName(s: String): TypeNameInfo {
        var work = s.trim()
        val asmMatch = ASM_PREFIX.find(work)
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

    /**
     * Parses a textual member-ref of the form
     * `retType [instance ][[Asm]]Ns.Type::name(p1, p2)` (the format is defined by
     * StdlibResolver/DotnetIrVisitor — see the documentation of [PeIlEmitter]).
     */
    fun parseCallRef(ref: String): CallInfo {
        val work = ref.trim()

        val retAndRest = Regex("""^(\S+)\s+(.+)$""").find(work)
            ?: throw IllegalStateException("Cannot parse call ref: '$ref'")
        val retCil = retAndRest.groupValues[1]
        var rest = retAndRest.groupValues[2]

        // The HASTHIS marker follows the return type: "void instance Ns.C::m(...)".
        val isInstance = rest.startsWith("instance ")
        if (isInstance) rest = rest.removePrefix("instance ").trim()

        val asmMatch = ASM_PREFIX.find(rest)
        val assembly: String?
        if (asmMatch != null) {
            assembly = asmMatch.groupValues[1]
            rest = asmMatch.groupValues[2]
        } else {
            assembly = null
        }

        val sigMatch = CALL_MEMBER.find(rest)
            ?: throw IllegalStateException("Cannot parse call ref member: '$ref'")
        // Names may arrive CIL-escaped ('box') — the escaping matters only for
        // IL text; metadata gets the raw name.
        val methodName = sigMatch.groupValues[2].trim()
        val paramsRaw = sigMatch.groupValues[3].trim()

        val qualified = sigMatch.groupValues[1].trim()
        val dotIdx = qualified.lastIndexOf('.')
        val ns = if (dotIdx >= 0) qualified.substring(0, dotIdx) else ""
        val typeName = if (dotIdx >= 0) qualified.substring(dotIdx + 1) else qualified

        val paramsCil =
            if (paramsRaw.isEmpty()) emptyList() else paramsRaw.split(',').map { it.trim() }

        return CallInfo(retCil, isInstance, assembly, ns, typeName, methodName, paramsCil)
    }

    /** Parses a field-ref `cilType [[Asm]]Ns.Type::fieldName`. */
    fun parseFieldRef(ref: String): FieldRefInfo {
        val sp = ref.indexOf(' ')
        require(sp > 0) { "Cannot parse field ref: '$ref'" }
        val cilType = ref.substring(0, sp).trim()
        val member = ref.substring(sp + 1).trim()
        val idx = member.lastIndexOf("::")
        require(idx > 0) { "Cannot parse field ref member: '$ref'" }
        val typePart = member.substring(0, idx).trim()
        val fieldName = member.substring(idx + 2).trim()

        val t = parseTypeName(typePart)
        return FieldRefInfo(typePart, cilType, t.assembly, t.namespace, t.typeName, fieldName)
    }

    /**
     * Maps CIL primitive aliases onto BCL types for the synthesized
     * default ctor (the base type is given as an alias or a qualified name).
     */
    fun toBclTypeName(cilType: String): String =
        when (cilType) {
            "object" -> "System.Object"
            "string" -> "System.String"
            else -> cilType
        }
}
