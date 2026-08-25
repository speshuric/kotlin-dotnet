package org.kotlindotnet.compiler.pe

/**
 * Чистый парсер текстовых member-ref'ов (без состояния, без знаний о
 * модели строк метаданных — только строки на входе и данные на выходе).
 *
 * Формат call-ref (Phase 10):
 *   `retCil [instance ][[Asm]]Ns.Type::name(paramCil, ...)`
 * Маркер `instance ` задаёт HASTHIS в сигнатуре MemberRef (instance-методы,
 * конструкторы). Статические вызовы маркера не имеют. NEWOBJ/CALLVIRT
 * различаются опкодом, ref одинаков.
 *
 * Формат field-ref ([IlOpcode.LDFLD]/[IlOpcode.STFLD]):
 *   `cilType [[Asm]]Ns.Type::fieldName`
 *
 * Формат имени типа: `[Asm]Ns.Name`, `Ns.Name` или `Name`
 * (nil-scope ⇒ тип из текущего модуля).
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
        /** Тип поля как в исходном ref (сборка/namespace на месте). */
        val ownerType: String,
        val cilType: String,
        val assembly: String?,
        val namespace: String,
        val typeName: String,
        val fieldName: String,
    )

    private val ASM_PREFIX = Regex("""^\[([^\]]+)\]\s*(.*)$""")
    private val CALL_MEMBER = Regex("""^(.+?)::(.+?)\s*\((.*)\)\s*$""")

    /** Разбирает имя типа: `[Asm]Ns.Name`, `Ns.Name` или `Name`. */
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
     * Разбирает текстовый member-ref формата
     * `retType [instance ][[Asm]]Ns.Type::name(p1, p2)` (формат задаётся
     * StdlibResolver/DotnetIrVisitor — см. документацию [PeIlEmitter]).
     */
    fun parseCallRef(ref: String): CallInfo {
        val work = ref.trim()

        val retAndRest = Regex("""^(\S+)\s+(.+)$""").find(work)
            ?: throw IllegalStateException("Cannot parse call ref: '$ref'")
        val retCil = retAndRest.groupValues[1]
        var rest = retAndRest.groupValues[2]

        // Маркер HASTHIS стоит после типа возврата: "void instance Ns.C::m(...)".
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
        // Имена могут приходить в CIL-экранировании ('box') — оно нужно
        // только IL-тексту; в метаданных пишем сырое имя.
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

    /** Разбирает field-ref `cilType [[Asm]]Ns.Type::fieldName`. */
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
     * Мэппинг CIL-алиасов примитивов на BCL-типы для синтезированного
     * дефолтного ctor'а (базовый тип задан алиасом или квалифицированным именем).
     */
    fun toBclTypeName(cilType: String): String =
        when (cilType) {
            "object" -> "System.Object"
            "string" -> "System.String"
            else -> cilType
        }
}
