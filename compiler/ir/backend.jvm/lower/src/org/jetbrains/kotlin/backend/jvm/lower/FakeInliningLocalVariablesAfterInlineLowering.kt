/*
 * Copyright 2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.*
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.inline.isAdaptedFunctionReference
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.ir.getAttributeBeforeInline
import org.jetbrains.kotlin.backend.jvm.ir.isInlineOnly
import org.jetbrains.kotlin.codegen.inline.INLINE_FUN_VAR_SUFFIX
import org.jetbrains.kotlin.codegen.inline.coroutines.FOR_INLINE_SUFFIX
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.createTmpVariable
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.getAllArgumentsWithIr
import org.jetbrains.kotlin.ir.util.originalFunction
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

internal val fakeInliningLocalVariablesAfterInlineLowering = makeIrFilePhase(
    ::FakeInliningLocalVariablesAfterInlineLowering,
    name = "FakeInliningLocalVariablesAfterInlineLowering",
    description = """Add fake locals to identify the range of inlined functions and lambdas. 
        |This lowering adds fake locals into already inlined blocks.""".trimMargin()
)

// TODO extract common code with FakeInliningLocalVariablesLowering
internal class FakeInliningLocalVariablesAfterInlineLowering(val context: JvmBackendContext) : IrElementVisitor<Unit, IrDeclaration?>, FileLoweringPass {
    private val inlinedStack = mutableListOf<IrInlinedFunctionBlock>()

//    private fun List<List<IrFunctionExpression>>.firstFlat(value: IrFunction): IrFunctionExpression {
//        this.forEach { innerList ->
//            innerList.firstOrNull { it.function == value }?.let { return it }
//        }
//        throw NoSuchElementException("Collection contains no element matching the predicate.")
//    }

    override fun lower(irFile: IrFile) {
        irFile.accept(this, null)
    }

    override fun visitElement(element: IrElement, data: IrDeclaration?) {
        val newData = if (element is IrDeclaration && element !is IrVariable) element else data
        element.acceptChildren(this, newData)
    }

//    override fun visitFunction(declaration: IrFunction, data: IrDeclaration?) {
//        if (declaration.origin == JvmLoweredDeclarationOrigin.INLINE_LAMBDA || declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) {
//            return
//        }
//        super.visitFunction(declaration, data)
//    }
//
//    override fun visitFunctionReference(expression: IrFunctionReference, data: IrDeclaration?) {
//        super.visitFunctionReference(expression, data)
//        if (expression.origin == JvmLoweredStatementOrigin.INLINE_LAMBDA) {
//            expression.symbol.owner.acceptChildren(this, expression.symbol.owner)
//        }
//    }

    override fun visitBlock(expression: IrBlock, data: IrDeclaration?) {
        when {
            expression is IrInlinedFunctionBlock && expression.isFunctionInlining() -> handleInlineFunction(expression, data)
            // TODO expression is IrInlinedFunctionBlock && expression.isLambdaInlining() -> handleInlineLambda(expression, data)
            else -> super.visitBlock(expression, data)
        }
    }

    private fun handleInlineFunction(expression: IrInlinedFunctionBlock, data: IrDeclaration?) {
        val declaration = expression.inlineDeclaration

        inlinedStack += expression
        super.visitBlock(expression, data)
        inlinedStack.removeLast()

        if (declaration is IrFunction && declaration.isInline && !declaration.origin.isSynthetic && declaration.body != null && !declaration.isInlineOnly()) {
            val currentFunctionName = context.defaultMethodSignatureMapper.mapFunctionName(declaration)
            val localName = "${JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION}$currentFunctionName"
            //declaration.addFakeLocalVariable(localName)
            with(context.createIrBuilder(data!!.symbol)) {
                val tmpVar =
                    scope.createTmpVariable(irInt(0), localName.removeSuffix(FOR_INLINE_SUFFIX), origin = IrDeclarationOrigin.DEFINED)
                // TODO maybe add in front of inline block
                expression.putStatementsInFrontOfInlinedFunction(listOf(tmpVar))
            }
        }

        expression.processLocalDeclarations()
    }

    private fun extractOriginalExpressionFor(function: IrFunction): IrAttributeContainer {
        for (marker in inlinedStack) {
            marker.inlineCall.getAllArgumentsWithIr().forEach {
                val actualArg = it.second ?: ((it.first.defaultValue?.expression?.attributeOwnerId as? IrBlock)?.statements?.firstOrNull() as? IrClass)?.attributeOwnerId as? IrExpression
                val function2 = if (function.name.asString() == "stub_for_ir_inlining") {
                    val value = (function.body!!.statements.single() as IrReturn).value
                    val lambda = (value as? IrTypeOperatorCall)?.argument ?: value
                    (lambda as IrFunctionAccessExpression).symbol.owner
                } else {
                    function
                }
                val original = ((function2 as? IrAttributeContainer)?.getAttributeBeforeInline() ?: function2.originalFunction)
                val extractedAnonymousFunction = actualArg?.tryToExtractAnonymousFunction()
                if (extractedAnonymousFunction == original) {
                    return actualArg.tryToExtractContainer()!!
                }
            }
        }

        throw AssertionError("Original expression not found")
    }

    private fun IrExpression.tryToExtractContainer(): IrAttributeContainer? {
        return when (val arg = this.attributeOwnerId) {
            is IrBlock -> arg.statements.lastOrNull()
                .takeIf { arg.origin == IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE } as? IrFunctionReference

            else -> arg
        }
    }

    private fun IrExpression.tryToExtractAnonymousFunction(): IrFunction? {
        return when (val arg = this.tryToExtractContainer()?.attributeOwnerId) {
            is IrFunctionExpression -> arg.function
            is IrFunctionReference -> arg.symbol.owner
//            is IrBlock -> (arg.statements.lastOrNull()
//                .takeIf { arg.origin == IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE } as? IrFunctionReference)
//                ?.tryToExtractAnonymousFunction()

            else -> null
        }
    }

    private fun getInlinedAt(originalExpression: IrElement): IrDeclaration {
        for (marker in inlinedStack) {
            marker.inlineCall.getAllArgumentsWithIr().forEach {
                val actualArg = (it.second?.attributeOwnerId ?: ((it.first.defaultValue?.expression?.attributeOwnerId as? IrBlock)?.statements?.firstOrNull() as? IrClass)?.attributeOwnerId) as? IrExpression
                val extractedAnonymousFunction = if (actualArg?.isAdaptedFunctionReference() == true) ((actualArg as IrBlock).statements.last() as IrFunctionReference).attributeOwnerId else actualArg
                if (extractedAnonymousFunction == originalExpression) {
                    return marker.inlineDeclaration
                }
            }
        }

        throw AssertionError("Original expression not found")
    }

    private fun handleInlineLambda(expression: IrInlinedFunctionBlock, data: IrDeclaration?) {
        inlinedStack += expression
        super.visitBlock(expression, data)
        val callee = TODO()//marker.inlinedAt as IrFunction
        val argument = TODO() //marker.originalExpression!!//extractOriginalExpressionFor(marker.callee) // marker.originalExpression!!

        // TODO
//        val actual = getInlinedAt(marker.originalExpression!!.attributeOwnerId)
//        assert(actual == marker.inlinedAt)

        inlinedStack.removeLast()

        //            val lambda = argument.function.symbol.owner
        val argumentToFunctionName = context.defaultMethodSignatureMapper.mapFunctionName(callee)
        val lambdaReferenceName = context.getLocalClassType(argument)!!.internalName.substringAfterLast("/")
        val localName = "${JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_ARGUMENT}-$argumentToFunctionName-$lambdaReferenceName"
        //            lambda.addFakeLocalVariable(localName)
        with(context.createIrBuilder(data!!.symbol)) {
            val tmpVar = scope.createTmpVariable(irInt(0), localName.removeSuffix(FOR_INLINE_SUFFIX), origin = IrDeclarationOrigin.DEFINED)
            expression.putStatementsInFrontOfInlinedFunction(listOf(tmpVar))
        }

        expression.processLocalDeclarations()
    }

    private fun IrInlinedFunctionBlock.processLocalDeclarations() {
        this.getAdditionalStatementsFromInlinedBlock().forEach {
            if (it is IrVariable && it.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE) {
                val varName = it.name.asString().substringAfterLast("_")
                it.name = Name.identifier((if (varName == SpecialNames.THIS.asString()) "this_" else varName) + INLINE_FUN_VAR_SUFFIX)
                it.origin = IrDeclarationOrigin.DEFINED
            }
        }

        this.getOriginalStatementsFromInlinedBlock().forEach {
            it.acceptVoid(object : IrElementVisitorVoid {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitVariable(declaration: IrVariable) {
                    val varName = declaration.name.asString()
                    declaration.name = when {
                        varName == SpecialNames.THIS.asString() -> {
                            Name.identifier("this_$INLINE_FUN_VAR_SUFFIX")
                        }

                        !varName.startsWith(JvmAbi.LOCAL_VARIABLE_NAME_PREFIX_INLINE_FUNCTION) -> {
                            Name.identifier(varName + INLINE_FUN_VAR_SUFFIX)
                        }

                        else -> declaration.name
                    }
                    super.visitVariable(declaration)
                }
            })
        }
    }
}
