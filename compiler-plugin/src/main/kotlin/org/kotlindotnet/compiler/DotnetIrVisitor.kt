package org.kotlindotnet.compiler

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.visitors.IrVisitor

/**
 * Посетитель IR-дерева, эмитящий IL-текст.
 *
 * PoC: покрывает только `IrFile → IrSimpleFunction(top-level) →
 * IrBlockBody → IrReturn → IrCall(PLUS) → IrGetValue`.
 * Остальные узлы → TODO throw NotImplementedError.
 *
 * Возвращает Unit: IlEmitter накапливает состояние внутри себя.
 */
class DotnetIrVisitor(
    private val emitter: IlEmitter
) : IrVisitor<Unit, Nothing?>() {

    override fun visitElement(element: IrElement, data: Nothing?) {
        TODO("IR element not supported in PoC: ${element::class.simpleName}")
    }

    override fun visitFile(declaration: IrFile, data: Nothing?) {
        val ns = NameMapper.namespace(declaration)
        val cls = NameMapper.containerClass(declaration)
        val asm = NameMapper.assemblyName(declaration)

        emitter.assemblyHeader(asm)
        emitter.beginContainerClass(ns, cls)
        for (decl in declaration.declarations) {
            if (decl !is IrSimpleFunction) continue
            emitSimpleFunction(decl, data)
        }
        emitter.endContainerClass()
    }

    private fun emitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        if (declaration.dispatchReceiverParameter != null) {
            TODO("Instance functions not supported in PoC")
        }

        val name = NameMapper.methodName(declaration)
        val retType = TypeMapper.mapType(declaration.returnType)
        val params = declaration.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { p: IrValueParameter ->
                TypeMapper.mapType(p.type) to p.name.asString()
            }

        emitter.beginStaticMethod(name, retType, params)
        val body = declaration.body
            ?: error("Function $name has no body")
        emitBody(body, data)
        emitter.endStaticMethod()
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?) {
        emitSimpleFunction(declaration, data)
    }

    private fun emitBody(body: IrElement, data: Nothing?) {
        if (body is IrBlockBody) {
            for (stmt in body.statements) {
                emitStatement(stmt, data)
            }
        } else {
            emitStatement(body, data)
        }
    }

    private fun emitStatement(e: IrElement, data: Nothing?) {
        when (e) {
            is IrReturn -> emitReturn(e, data)
            is IrCall -> emitExpr(e, data)
            is IrGetValue -> emitExpr(e, data)
            is IrConst -> emitExpr(e, data)
            else -> e.accept(this, data)
        }
    }

    private fun emitExpr(e: IrExpression, data: Nothing?) {
        when (e) {
            is IrCall -> emitCall(e, data)
            is IrGetValue -> emitGetValue(e)
            is IrConst -> emitConst(e)
            else -> e.accept(this, data)
        }
    }

    private fun emitReturn(ret: IrReturn, data: Nothing?) {
        emitExpr(ret.value, data)
        emitter.line("ret")
    }

    private fun emitCall(call: IrCall, data: Nothing?) {
        call.arguments.forEach { a ->
            if (a != null) emitExpr(a, data)
        }
        val origin = call.origin?.debugName
        when (origin) {
            "PLUS" -> emitter.line("add")
            "MINUS" -> emitter.line("sub")
            "MUL" -> emitter.line("mul")
            "DIV" -> emitter.line("div")
            "REM" -> emitter.line("rem")
            else -> TODO("Call origin '$origin' not supported in PoC")
        }
    }

    private fun emitGetValue(get: IrGetValue) {
        val owner = get.symbol.owner
        when (owner) {
            is IrValueParameter -> {
                emitter.line("ldarg.${owner.indexInParameters}")
            }
            else -> TODO("GetValue of ${owner::class.simpleName} not supported in PoC")
        }
    }

    private fun emitConst(c: IrConst) {
        val v = c.value
        when (v) {
            is Int -> {
                if (v in -1..8) {
                    emitter.line("ldc.i4.$v")
                } else {
                    emitter.line("ldc.i4 $v")
                }
            }
            else -> TODO("Const of ${v?.javaClass?.simpleName} not supported in PoC")
        }
    }
}
