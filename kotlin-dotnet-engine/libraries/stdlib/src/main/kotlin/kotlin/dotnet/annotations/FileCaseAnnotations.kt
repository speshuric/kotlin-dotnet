package kotlin.dotnet.annotations

/**
 * Marks a file whose top-level declarations are emitted with their
 * identifiers converted from lowerCamelCase to PascalCase (file-level
 * mapping switch of the name-mapping track).
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class KotlinToPascalCase

/**
 * Counterpart of [KotlinToPascalCase]: the file was converted from
 * PascalCase back to Kotlin lowerCamelCase.
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class DotnetFromPascalCase
