/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.inline.InlinedFunctionReference
import org.jetbrains.kotlin.backend.common.phaser.makeIrModulePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.functionInliningPhase
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

internal val markNecessaryInlinedClassesAsRegenerated = makeIrModulePhase(
    ::MarkNecessaryInlinedClassesAsRegeneratedLowering,
    name = "MarkNecessaryInlinedClassesAsRegeneratedLowering",
    description = "Will scan all inlined functions and mark anonymous objects that must be later regenerated at backend",
    prerequisite = setOf(functionInliningPhase, createSeparateCallForInlinedLambdas)
)

class MarkNecessaryInlinedClassesAsRegeneratedLowering(val context: JvmBackendContext) : IrElementTransformerVoidWithContext(), FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid()
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        val newBlock = super.visitBlock(expression)

        if (newBlock.wasExplicitlyInlined()) {
            val mustBeRegenerated = newBlock.collectIrClassesThatMustBeRegenerated()
            mustBeRegenerated.forEach { it.setUpCorrectAttributeOwnerForInlinedElements() }
        }

        return newBlock
    }

    private fun IrExpression.wasExplicitlyInlined(): Boolean {
        return this is IrReturnableBlock && this.statements.firstOrNull() is IrInlineMarker &&
                inlineFunctionSymbol?.owner?.origin != IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) {
            return expression
        }
        return super.visitCall(expression)
    }

    private fun IrElement.collectIrClassesThatMustBeRegenerated(): Set<IrAttributeContainer> {
        val classesToRegenerate = mutableSetOf<IrAttributeContainer>()
        this.acceptVoid(object : IrElementVisitorVoid {
            private val containersStack = mutableListOf<IrAttributeContainer>()

            private fun saveDeclarationsFromStackIntoRegenerationPool() {
                containersStack.forEach { classesToRegenerate += it }
            }

            private fun IrAttributeContainer.saveIfRegenerated() {
                if (attributeOwnerId.attributeOwnerIdBeforeInline != null) {
                    classesToRegenerate += this
                }
            }

            override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)

            override fun visitClassReference(expression: IrClassReference) {
                if (expression.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                super.visitClassReference(expression)
            }

            override fun visitClass(declaration: IrClass) {
                declaration.saveIfRegenerated()
                containersStack += declaration
                if (declaration.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                super.visitClass(declaration)
                containersStack.removeLast()
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                expression.saveIfRegenerated()
                containersStack += expression
                super.visitFunctionExpression(expression)
                containersStack.removeLast()
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                expression.saveIfRegenerated()
                containersStack += expression
                super.visitFunctionReference(expression)
                containersStack.removeLast()
            }

            override fun visitTypeOperator(expression: IrTypeOperatorCall) {
                if (expression.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                super.visitTypeOperator(expression)
            }

            override fun visitGetValue(expression: IrGetValue) {
                super.visitGetValue(expression)
                if (expression.type.getClass()?.let { classesToRegenerate.contains(it) } == true) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) {
                    (expression.getValueArgument(0) as IrFunctionExpression).function.acceptVoid(this)
                    return
                }

                if (expression.hasReifiedTypeParameters() || expression.origin is InlinedFunctionReference) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
                super.visitCall(expression)
            }

            override fun visitContainerExpression(expression: IrContainerExpression) {
                super.visitContainerExpression(expression)
                if (expression !is IrReturnableBlock || expression.inlineFunctionSymbol?.owner?.origin != IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) {
                    return
                }
                saveDeclarationsFromStackIntoRegenerationPool()
            }
        })
        return classesToRegenerate
    }

    private fun IrAttributeContainer.hasReifiedTypeParameters(): Boolean {
        var hasReified = false

        fun IrType.recursiveWalkDown(visitor: IrElementVisitorVoid) {
            this@recursiveWalkDown.classifierOrNull?.owner?.acceptVoid(visitor)
            (this@recursiveWalkDown as? IrSimpleType)?.arguments?.forEach { it.typeOrNull?.recursiveWalkDown(visitor) }
        }

        this.attributeOwnerId.acceptVoid(object : IrElementVisitorVoid {
            private val visitedClasses = mutableSetOf<IrClass>()

            override fun visitElement(element: IrElement) {
                if (hasReified) return
                element.acceptChildrenVoid(this)
            }

            override fun visitTypeParameter(declaration: IrTypeParameter) {
                hasReified = hasReified || declaration.isReified
                super.visitTypeParameter(declaration)
            }

            override fun visitClass(declaration: IrClass) {
                if (!visitedClasses.add(declaration)) return
                declaration.superTypes.forEach { it.recursiveWalkDown(this) }
                super.visitClass(declaration)
            }

            override fun visitTypeOperator(expression: IrTypeOperatorCall) {
                expression.typeOperand.takeIf { it is IrSimpleType }?.recursiveWalkDown(this)
                super.visitTypeOperator(expression)
            }

            override fun visitCall(expression: IrCall) {
                (0 until expression.typeArgumentsCount).forEach {
                    expression.getTypeArgument(it)?.recursiveWalkDown(this)
                }
                super.visitCall(expression)
            }

            override fun visitClassReference(expression: IrClassReference) {
                expression.classType.recursiveWalkDown(this)
                super.visitClassReference(expression)
            }
        })
        return hasReified
    }

    private fun IrAttributeContainer.setUpCorrectAttributeOwnerForInlinedElements() {
        fun IrAttributeContainer.setUpCorrectAttributeOwnerForSingleElement() {
            this.attributeOwnerIdBeforeInline = this.attributeOwnerId.let { it.attributeOwnerIdBeforeInline ?: it }
            this.attributeOwnerId = this
        }

        setUpCorrectAttributeOwnerForSingleElement()
        this.acceptChildrenVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                if (element is IrAttributeContainer) element.setUpCorrectAttributeOwnerForSingleElement()
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                return
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                return
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                return
            }

            override fun visitPropertyReference(expression: IrPropertyReference) {
                return
            }
        })
    }
}