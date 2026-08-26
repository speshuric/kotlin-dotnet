package org.kotlindotnet.compiler.pe

/**
 * A method debug sequence point captured while encoding a body.
 *
 * @property offset IL offset within the method body.
 * @property startLine 1-based source line where the range starts.
 * @property startColumn 1-based source column where the range starts.
 * @property endLine 1-based source line where the range ends.
 * @property endColumn 1-based source column where the range ends.
 */
data class SequencePoint(
    val offset: Int,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)
