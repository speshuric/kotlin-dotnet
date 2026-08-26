package org.kotlindotnet.compiler.pe

import org.kotlindotnet.dotnetutils.system.reflection.metadata.BlobBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.EntityHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ILOpCode
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.StandaloneSignatureHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.BlobEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.ControlFlowBuilder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.InstructionEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.LabelHandle
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MetadataTokens
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.MethodBodyStreamEncoder
import org.kotlindotnet.dotnetutils.system.reflection.metadata.ecma335.addStandaloneSignature

/** Буферизованная инструкция тела метода. */
internal sealed interface Op

internal class InstOp(val code: ILOpCode) : Op

/** call/callvirt/newobj: код хранится явно (раньше newobj кодировался как call — баг). */
internal class CallOp(val code: ILOpCode, val ref: String) : Op

/** ldfld/stfld: операнд — текстовый field-ref. */
internal class FieldOp(val code: ILOpCode, val ref: String) : Op
internal class LdStrOp(val value: String) : Op
internal class BranchOp(val code: ILOpCode, val labelId: Int) : Op
internal class MarkOp(val labelId: Int) : Op
internal class TypeOp(val code: ILOpCode, val cilType: String) : Op
internal class LdcI4Op(val value: Int) : Op
internal class LdcI8Op(val value: Long) : Op
internal class LdcR4Op(val value: Float) : Op
internal class LdcR8Op(val value: Double) : Op

/** Pseudo-op: sequence point mark (offset of the next real instruction). */
internal class SeqPointOp(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
) : Op
internal enum class LocalKind { LDARG, LDLOC, STLOC }
internal class LocalOp(val kind: LocalKind, val index: Int) : Op

/**
 * Кодирование тел методов: [Op]-буфер MethodRec → IL-поток
 * ([MethodBodyStreamEncoder]). Резолв операндов (call/field/type) и
 * строки — через инъекцию, чтобы энкодер не знал ни о реестре ссылок,
 * ни о модели целиком.
 */
internal class PeBodyEncoder(
    private val metadata: MetadataBuilder,
    private val bodyStream: MethodBodyStreamEncoder,
    /** call/callvirt/newobj ref → токен (MethodDef или MemberRef). */
    private val resolveCallTarget: (String) -> EntityHandle,
    /** field-ref → MemberRef-токен. */
    private val resolveFieldRef: (String) -> EntityHandle,
    /** CIL-имя типа → TypeDefOrRef (для box/newarr). */
    private val resolveTypeEntity: (String) -> EntityHandle,
) {
    data class Result(val bodyOffset: Int, val sequencePoints: List<SequencePoint>)

    /** Encodes body [m]; returns its stream offset and captured sequence points. */
    fun encode(m: MethodRec): Result {
        val codeBuilder = BlobBuilder()
        val flow = ControlFlowBuilder()
        val encoder = InstructionEncoder(codeBuilder, flow)
        val handles = HashMap<Int, LabelHandle>()

        fun labelHandle(id: Int): LabelHandle = handles.getOrPut(id) { encoder.defineLabel() }

        val capturedSeqPoints = ArrayList<SequencePoint>()

        for (op in m.ops) {
            when (op) {
                is SeqPointOp -> {
                    // Offset of the next real instruction == current stream position.
                    capturedSeqPoints.add(
                        SequencePoint(codeBuilder.count, op.startLine, op.startColumn, op.endLine, op.endColumn),
                    )
                }
                is InstOp -> encoder.opCode(op.code)
                is LdStrOp -> encoder.loadString(metadata.getOrAddUserString(op.value))
                is BranchOp -> encoder.branch(op.code, labelHandle(op.labelId))
                is MarkOp -> encoder.markLabel(labelHandle(op.labelId))
                is CallOp -> {
                    encoder.opCode(op.code)
                    encoder.token(resolveCallTarget(op.ref))
                }
                is FieldOp -> {
                    encoder.opCode(op.code)
                    encoder.token(resolveFieldRef(op.ref))
                }
                is TypeOp -> {
                    // IL-порядок: сначала байт опкода, затем токен-операнд.
                    encoder.opCode(op.code)
                    encoder.token(PeSignatures.boxedTypeToken(op.cilType, resolveTypeEntity))
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
                    PeSignatures.applyCilType(lv.addVariable().type(), t.cilType, resolveTypeEntity)
                }
                metadata.addStandaloneSignature(metadata.getOrAddBlob(b))
            }

        val bodyOffset = bodyStream.addMethodBody(encoder, localVariablesSignature = localsSig)
        return Result(bodyOffset, capturedSeqPoints)
    }

}
