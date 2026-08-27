package kotlin.io

/**
 * Console output shims.
 *
 * Signatures mirror upstream stdlib (common/src/kotlin/ioH.kt). Bodies are
 * placeholders: the compiler plugin emits the actual runtime calls from its
 * redirect registry (StdlibMapping); resolution will switch to reading
 * redirect annotations on these declarations instead of the registry once
 * they carry @kotlin.dotnet.annotations.DotnetName (each pointing at the
 * runtime target, e.g. Kotlin.Runtime.Print.println).
 */
public fun println(message: Any?) {
}

public fun println() {
}

public fun print(message: Any?) {
}
