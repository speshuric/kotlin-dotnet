package kotlin.dotnet.annotations

/**
 * Renames the annotated declaration for the .NET output
 * (analogous to @JsName on JS targets).
 *
 * Compile-time instruction for the kotlin-dotnet compiler plugin;
 * SOURCE retention on purpose - this name never reaches assembly
 * metadata as an attribute. Targets mirror @JsName.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class DotnetName(val name: String)
