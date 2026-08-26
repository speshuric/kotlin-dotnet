package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.kotlinFqName

/**
 * Registry of translation-relevant annotations read from IR declarations.
 *
 * Every IR declaration implements [IrAnnotationContainer]; each entry in
 * its annotations list is an [IrConstructorCall]. The annotated class is
 * reached through the constructor symbol's parent (see [AnnotationReader]
 * resolution note); we match it by fully qualified name and extract
 * constant arguments (string values arrive as [IrConst] with kind String).
 *
 * Until our annotations live in their final package (stdlib skeleton step
 * of the stdlib plan), test code declares them without a package; matching
 * therefore also accepts a bare simple name via [recognizedFqName]. Once
 * the skeleton ships, tighten this to exact-fqname-only.
 *
 * Scope: these annotations are compile-time instructions for the plugin,
 * not metadata. They are NOT emitted into the resulting assembly; general
 * custom-attribute emission is a separate concern (name-mapping plan,
 * layer 0 infrastructure).
 */
object StdlibAnnotations {

    /**
     * Renames the annotated declaration for .NET output (analogous to
     * @JsName on JS targets). Value: target name string.
     */
    const val DOTNET_NAME: String = "kotlin.dotnet.annotations.DotnetName"

    /**
     * Marks a file whose top-level declarations are emitted with their
     * identifiers converted from lowerCamelCase to PascalCase.
     */
    const val KOTLIN_TO_PASCAL_CASE: String = "kotlin.dotnet.annotations.KotlinToPascalCase"

    /**
     * Counterpart of [KOTLIN_TO_PASCAL_CASE]: the file was converted from
     * PascalCase back to Kotlin camelCase.
     */
    const val DOTNET_FROM_PASCAL_CASE: String = "kotlin.dotnet.annotations.DotnetFromPascalCase"

    /** All registered fq-names the plugin recognizes (for dump filtering). */
    val KNOWN: Set<String> = setOf(DOTNET_NAME, KOTLIN_TO_PASCAL_CASE, DOTNET_FROM_PASCAL_CASE)

    /** Canonical registered name for a seen annotation fq-name, or null. */
    fun recognizedFqName(fqName: String): String? =
        if (fqName in KNOWN) fqName else BY_SIMPLE_NAME[fqName.substringAfterLast('.')]

    private val BY_SIMPLE_NAME: Map<String, String> =
        KNOWN.associateBy { it.substringAfterLast('.') }
}

/**
 * Extracts annotation facts from an IR annotation container.
 *
 * Resolution note (this compiler version): an annotation entry is an
 * [IrConstructorCall] WITHOUT a typeRef accessor. Upstream resolves the
 * annotation class through the constructor symbol, e.g.
 * isAnnotationWithEqualFqName() walks symbol -> owner (IrConstructor) ->
 * parent (the annotation IrClass). We do the same and then compare
 * fully-qualified/simple names against the registry.
 *
 * File-level (@file:) annotations live on IrFile, which also implements
 * [IrAnnotationContainer]; the same access path covers both cases.
 */
object AnnotationReader {

    /**
     * Canonical names of plugin-recognized annotations present on the given
     * container, in declaration order. Unknown annotations are ignored.
     */
    fun knownFqNames(container: IrAnnotationContainer): List<String> =
        knownEntries(container).map { it.first }

    /**
     * Same as [knownFqNames], but keeps the whole constructor call so the
     * caller may inspect arguments (e.g. read @DotnetName's value).
     */
    fun knownEntries(container: IrAnnotationContainer): List<Pair<String, IrConstructorCall>> {
        val result = mutableListOf<Pair<String, IrConstructorCall>>()
        for (annotation in container.annotations) {
            val seenFqName = annotation.seenAnnotationFqName() ?: continue
            val canonical = StdlibAnnotations.recognizedFqName(seenFqName) ?: continue
            result.add(canonical to annotation)
        }
        return result
    }

    /**
     * Reads @DotnetName's value from the given container, or null when
     * absent or malformed (value not a string literal / empty). Malformed
     * occurrences are reported via [onMalformed]; callers decide the
     * strictness policy separately.
     */
    fun dotnetName(
        container: IrAnnotationContainer,
        onMalformed: (reason: String) -> Unit = {},
    ): String? {
        val entry = knownEntries(container).firstOrNull { it.first == StdlibAnnotations.DOTNET_NAME }
            ?: return null
        val value = argumentString(entry.second) ?: run {
            onMalformed("@DotnetName value is not a string literal")
            return null
        }
        if (value.isEmpty()) {
            onMalformed("@DotnetName value is empty")
            return null
        }
        return value
    }

    /** One line per recognized annotation: name plus scalar value if any. */
    fun summarize(container: IrAnnotationContainer): List<String> =
        knownEntries(container).map { (canonical, call) ->
            when (canonical) {
                StdlibAnnotations.DOTNET_NAME ->
                    argumentString(call)?.let { "$canonical(\"$it\")" } ?: "$canonical(?)"
                else -> canonical
            }
        }

    /**
     * Fully qualified name of the annotation class behind this entry.
     * Unbound symbols cannot be resolved and yield null (never thrown):
     * declarations coming from sources are always bound, the unbound case
     * exists only for deserialized fragments which we do not read here.
     */
    private fun IrConstructorCall.seenAnnotationFqName(): String? {
        val ctor = runCatching { symbol.owner }.getOrNull() as? IrConstructor ?: return null
        val annotationClass = ctor.parent as? IrClass ?: return null
        return runCatching { annotationClass.kotlinFqName.asString() }.getOrNull()
    }

    // In this compiler version IrConst is NOT generic: value is Any?, kind
    // selects the tag. A string argument arrives as IrConst with kind String.
    private fun argumentString(call: IrConstructorCall): String? {
        val arg = call.arguments.firstOrNull() as? IrConst ?: return null
        if (arg.kind != IrConstKind.String) return null
        return arg.value as? String
    }
}
