/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.inline.InlinerExpressionLocationHint
import org.jetbrains.kotlin.backend.jvm.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class PropertyReferenceInliningLowering(val context: JvmBackendContext) : IrElementTransformerVoidWithContext(), FileLoweringPass {
    override fun lower(irFile: IrFile) =
        irFile.transformChildrenVoid()

    override fun visitVariable(declaration: IrVariable): IrStatement {
        val initializer = declaration.initializer as? IrBlock
        if (declaration.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE && initializer?.origin is InlinerExpressionLocationHint) {
            val reference = initializer.statements.singleOrNull() as? IrPropertyReference ?: return super.visitVariable(declaration)
            currentFunction?.irElement?.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val owner = expression.symbol.owner
                    if (owner.name.asString() != "invoke" || (expression.dispatchReceiver as? IrGetValue)?.symbol != declaration.symbol) {
                        return super.visitCall(expression)
                    }

                    val getterCall = IrCallImpl.fromSymbolOwner(expression.startOffset, expression.endOffset, expression.type, reference.getter!!)

                    fun tryToGetArg(i: Int): IrExpression? {
                        if (i >= expression.valueArgumentsCount) return null
                        return expression.getValueArgument(i)
                    }

                    // TODO test for KProperty2
                    getterCall.dispatchReceiver = reference.dispatchReceiver ?: tryToGetArg(0)
                    getterCall.extensionReceiver = reference.extensionReceiver

                    return getterCall
                }
            })
            return IrCompositeImpl(declaration.startOffset, declaration.endOffset, declaration.type)
        }
        return super.visitVariable(declaration)
    }

//    override fun visitCall(expression: IrCall): IrExpression {
//        val owner = expression.symbol.owner
//        if (owner.name.asString() != "invoke") {
//            return super.visitCall(expression)
//        }
//
//        val variable = (expression.dispatchReceiver as? IrGetValue)?.symbol?.owner as? IrVariable ?: return super.visitCall(expression)
//        val dispatchReceiver = (variable.initializer as? IrBlock)?.statements?.singleOrNull() ?: return super.visitCall(expression)
//        if (dispatchReceiver !is IrPropertyReference) {
//            return super.visitCall(expression)
//        }
//
//        val getterCall = IrCallImpl.fromSymbolOwner(expression.startOffset, expression.endOffset, expression.type, dispatchReceiver.getter!!)
//        getterCall.dispatchReceiver = dispatchReceiver.dispatchReceiver?.deepCopyWithSymbols()
//        getterCall.extensionReceiver = dispatchReceiver.extensionReceiver?.deepCopyWithSymbols()
//
//        return getterCall
//    }
}
