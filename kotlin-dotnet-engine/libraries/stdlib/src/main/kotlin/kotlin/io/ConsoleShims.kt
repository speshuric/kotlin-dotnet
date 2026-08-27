package kotlin.io

/**
 * Stage-A skeleton shims for console output.
 *
 * Signatures mirror upstream stdlib (common/src/kotlin/ioH.kt). Bodies
 * are placeholders: in stage A these declarations only provide the API
 * surface while redirects are performed by the compiler plugin registry
 * (StdlibMapping); in stage B the annotated shims replace the registry
 * (each will carry @kotlin.dotnet.annotations.DotnetName with its runtime
 * target, e.g. Kotlin.Runtime.Print.println).
 */
@Deprecated("skeleton: compiled by kotlinc only; not yet wired into user compilation")
public fun println(message: Any?) {
}

@Deprecated("skeleton: compiled by kotlinc only; not yet wired into user compilation")
public fun println() {
}

@Deprecated("skeleton: compiled by kotlinc only; not yet wired into user compilation")
public fun print(message: Any?) {
}
