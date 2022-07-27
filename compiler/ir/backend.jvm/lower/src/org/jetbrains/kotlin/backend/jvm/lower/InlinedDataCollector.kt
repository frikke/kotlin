/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.inline.InlinedArgument
import org.jetbrains.kotlin.backend.common.lower.parents
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

class JvmInlinedDataCollector(val context: JvmBackendContext) : FileLoweringPass, IrElementVisitorVoid {
    private var count = 0

    private var innerInlinedCalls = 0
    override fun lower(irFile: IrFile) = irFile.acceptChildrenVoid(this)

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitContainerExpression(expression: IrContainerExpression) {
        if (expression !is IrReturnableBlock || expression.inlineFunctionSymbol == null) return super.visitContainerExpression(expression)

        innerInlinedCalls++
        return super.visitContainerExpression(expression).apply {
            innerInlinedCalls--
            expression.setUpAttributeOwnerIfToSelf()
        }
    }

    override fun visitClass(declaration: IrClass) {
        if (innerInlinedCalls > 0) {
            context.inlinedAnonymousClassToOriginal[declaration] = declaration.attributeOwnerId
//            context.putLocalClassType(declaration, Type.getObjectType("inlinedClass" + count++))
        }
        super.visitClass(declaration)
    }

//    override fun visitFunctionExpression(expression: IrFunctionExpression) {
//        if (innerInlinedCalls > 0) {
//            expression.setUpAttributeOwnerIfToSelf()
//        }
//        super.visitFunctionExpression(expression)
//    }
//
//    override fun visitFunctionReference(expression: IrFunctionReference) {
//        if (innerInlinedCalls > 0) {
//            expression.setUpAttributeOwnerIfToSelf()
//        }
//        super.visitFunctionReference(expression)
//    }

    private fun IrElement.setUpAttributeOwnerIfToSelf() {
//        (this as? IrAttributeContainer)?.let { attributeOwnerId = this }
        accept(object : IrElementVisitorVoid {
            val inlinedFunctionsStack = mutableListOf<IrFunctionSymbol>()

            override fun visitElement(element: IrElement) {
                if (element is IrAttributeContainer) {
                    element.attributeOwnerId = element
                }
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                if (!declaration.hasInlinedBlock() && !declaration.wasInlinedFromArgument()) {
                    return declaration.acceptChildrenVoid(this)
                }
                context.declarationsThatHasInlinedBlock += declaration
                super.visitClass(declaration)
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                if (!expression.hasInlinedBlock() && !expression.function.wasInlinedFromArgument()) {
                    return expression.function.acceptChildrenVoid(this)
                }
                context.declarationsThatHasInlinedBlock += expression // must create new declaration in backend
                super.visitFunctionExpression(expression)
            }

            override fun visitPropertyReference(expression: IrPropertyReference) {
                expression.acceptChildrenVoid(this)
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                if (!expression.symbol.owner.wasInlinedFromArgument()) {
                    return expression.symbol.owner.acceptChildrenVoid(this)
                }
                context.declarationsThatHasInlinedBlock += expression // must create new declaration in backend
                super.visitFunctionReference(expression)
            }

            override fun visitContainerExpression(expression: IrContainerExpression) {
                if (expression !is IrReturnableBlock || expression.inlineFunctionSymbol == null) return super.visitContainerExpression(expression)

                inlinedFunctionsStack += expression.inlineFunctionSymbol!!
                return super.visitContainerExpression(expression).apply {
                    inlinedFunctionsStack.removeLast()
                }
            }

            private fun IrDeclaration.wasInlinedFromArgument(): Boolean {
                return inlinedFunctionsStack.none { this.parents.contains(it.owner) }
            }
        }, null)
    }

    private fun IrElement.hasInlinedBlock(): Boolean {
        object : IrElementVisitorVoid {
            var hasInlinedBlock: Boolean = false

            override fun visitElement(element: IrElement) {
                if (hasInlinedBlock) return
                element.acceptChildrenVoid(this)
            }

            override fun visitContainerExpression(expression: IrContainerExpression) {
                hasInlinedBlock = hasInlinedBlock || (expression is IrReturnableBlock && expression.inlineFunctionSymbol != null)
                if (hasInlinedBlock) return
                super.visitContainerExpression(expression)
            }


            override fun visitGetValue(expression: IrGetValue) {
                hasInlinedBlock = hasInlinedBlock || expression.origin is InlinedArgument
                return super.visitGetValue(expression)
            }
        }.apply {
            this@hasInlinedBlock.accept(this, null)
            return this.hasInlinedBlock
        }
    }
}