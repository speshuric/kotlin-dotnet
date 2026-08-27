package kotlin

/**
 * The type with only one value: the `Unit` object. This type corresponds
 * to the void type on .NET (see docs/type mapping in AGENTS.md).
 *
 * Skeleton version: identity semantics come from the defaults on Any,
 * only toString matches upstream behavior.
 */
public object Unit {
    override fun toString(): String = "kotlin.Unit"
}
