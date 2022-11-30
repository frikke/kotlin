/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.getDefaultAdditionalStatementsFromInlinedBlock
import org.jetbrains.kotlin.backend.common.ir.getNonDefaultAdditionalStatementsFromInlinedBlock
import org.jetbrains.kotlin.backend.common.ir.getOriginalStatementsFromInlinedBlock
import org.jetbrains.kotlin.backend.common.lower.LocalDeclarationsLowering
import org.jetbrains.kotlin.backend.common.lower.parents
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrInlineMarker
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.util.copyTypeAndValueArgumentsFrom
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.name.NameUtils

internal val removeDuplicatedInlinedLocalClasses = makeIrFilePhase(
    ::RemoveDuplicatedInlinedLocalClassesLowering,
    name = "RemoveDuplicatedInlinedLocalClasses",
    description = "Drop excess local classes that were copied by ir inliner",
    prerequisite = setOf(functionInliningPhase, localDeclarationsPhase)
)

// There are three types of inlined local classes:
// 1. MUST BE regenerated according to set of rules in AnonymousObjectTransformationInfo.
// They all have `attributeOwnerIdBeforeInline != null`.
// 2. MUST NOT BE regenerated and MUST BE CREATED only once because they are copied from call site.
// This lambda will not exist after inline, so we copy declaration into new temporary inline call `singleArgumentInlineFunction`.
// 3. MUST NOT BE created at all because will be created at callee site.
// This lowering drops declarations that correspond to second and third type.
class RemoveDuplicatedInlinedLocalClassesLowering(val context: JvmBackendContext) : IrElementTransformer<Boolean>, FileLoweringPass {
    private var insideInlineBlock = false
    private val visited = mutableSetOf<IrElement>()

    private var modifyTree: Boolean = true
    private val modified = mutableSetOf<IrClass>()

    private fun willBeModified(irClass: IrClass): Boolean {
        modifyTree = false
        irClass.parents.first { it !is IrFunction || it.origin != JvmLoweredDeclarationOrigin.INLINE_LAMBDA }
            .accept(this, false)
        return irClass in modified
    }

    override fun lower(irFile: IrFile) {
        irFile.transformChildren(this, false)
    }

    override fun visitCall(expression: IrCall, data: Boolean): IrElement {
        if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) return expression
        return super.visitCall(expression, data)
    }

    override fun visitBlock(expression: IrBlock, data: Boolean): IrExpression {
        if (expression.statements.firstOrNull() is IrInlineMarker) {
            val oldInsideInlineBlock = insideInlineBlock
            insideInlineBlock = true
            expression.getNonDefaultAdditionalStatementsFromInlinedBlock().forEach { it.transform(this, false) }
            expression.getDefaultAdditionalStatementsFromInlinedBlock().forEach { it.transform(this, true) }
            expression.getOriginalStatementsFromInlinedBlock().forEach { it.transform(this, true) }
            insideInlineBlock = oldInsideInlineBlock
            return expression
        }

        val oldClass = expression.statements.firstOrNull() as? IrClass
        val containsClass = expression.statements.firstOrNull() is IrClass
        val result = super.visitBlock(expression, data)
        if (containsClass && result is IrBlock && result.statements.firstOrNull() is IrComposite) {
            val lastStatement = result.statements.last()
            val constructorCall = (lastStatement as? IrConstructorCall)
                ?: (lastStatement as IrBlock).statements.last() as IrConstructorCall
            val constructorParent = constructorCall.symbol.owner.parentAsClass
            var originalConstructor = context.mapping.capturedConstructors.keys.filter {
                it.parentAsClass.attributeOwnerId == constructorParent.attributeOwnerId && it.parentAsClass != constructorParent
            }.singleOrNull {
                val remover = RemoveDuplicatedInlinedLocalClassesLowering(context)
                !remover.willBeModified(it.parentAsClass)
            }
            if (originalConstructor == null) {
                val container = oldClass!!.parents.first { it is IrFunction } as IrFunction
                LocalDeclarationsLowering(
                    context, NameUtils::sanitizeAsJavaIdentifier, JvmVisibilityPolicy(),
                    compatibilityModeForInlinedLocalDelegatedPropertyAccessors = true, forceFieldsForInlineCaptures = true
                ).lowerWithoutActualChange(container.body!!, container)

                originalConstructor = context.mapping.capturedConstructors.keys.filter {
                    it.parentAsClass.attributeOwnerId == constructorParent.attributeOwnerId && it.parentAsClass != constructorParent
                }.single {
                    val remover = RemoveDuplicatedInlinedLocalClassesLowering(context)
                    !remover.willBeModified(it.parentAsClass)
                }
//                TODO("call local lowering")
//                return result
            }
            val loweredConstructor = context.mapping.capturedConstructors[originalConstructor]!!
            val newConstructorCall = IrConstructorCallImpl.fromSymbolOwner(
                constructorCall.startOffset, constructorCall.endOffset,
                loweredConstructor.parentAsClass.defaultType, loweredConstructor.symbol, constructorCall.origin
            )
            newConstructorCall.copyTypeAndValueArgumentsFrom(constructorCall)

            if (lastStatement is IrConstructorCall) {
                result.statements[result.statements.lastIndex] = newConstructorCall
            } else if (lastStatement is IrBlock) {
                lastStatement.statements[lastStatement.statements.lastIndex] = newConstructorCall
            }
        }
        return result
    }

    // Basically we want to remove all anonymous classes after inline. Exceptions are:
    // 1. classes that must be regenerated (declaration.attributeOwnerIdBeforeInline != null)
    // 2. classes that are originally declared on call site or are default lambdas (data == true)
    override fun visitClass(declaration: IrClass, data: Boolean): IrStatement {
        if (!insideInlineBlock || declaration.attributeOwnerIdBeforeInline != null || !data) {
            return super.visitClass(declaration, data)
        }

        modified += declaration
        return if (modifyTree) {
            val constructor = declaration.primaryConstructor!!
            context.mapping.capturedConstructors[constructor] = null // actually will do `remove`
            context.mapping.capturedConstructors.keys.map { key -> // for each value
                context.mapping.capturedConstructors[key]?.let {
                    if (it == constructor) {
                        context.mapping.capturedConstructors[key] = null
                    }
                }
            }
            IrCompositeImpl(declaration.startOffset, declaration.endOffset, context.irBuiltIns.unitType)
        } else {
            super.visitClass(declaration, data)
        }
    }

    override fun visitFunctionReference(expression: IrFunctionReference, data: Boolean): IrElement {
        if (expression.symbol.owner in visited) return expression
        visited += expression.symbol.owner
        expression.symbol.owner.accept(this, data)
        return super.visitFunctionReference(expression, data)
    }
}
