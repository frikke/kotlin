/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.inline.InlinerExpressionLocationHint
import org.jetbrains.kotlin.backend.common.lower.parentsWithSelf
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.functionInliningPhase
import org.jetbrains.kotlin.backend.jvm.localDeclarationsPhase
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrInlineMarker
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal val removeDuplicatedInlinedLocalClasses = makeIrFilePhase(
    ::InlinedAnonymousElementsLowering,
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
class InlinedAnonymousElementsLowering(val context: JvmBackendContext) : IrElementTransformerVoid(), FileLoweringPass {
    private val inlineStack = mutableListOf<IrInlineMarker>()
    private val inlinedVarStack = mutableListOf<InlinerExpressionLocationHint>()

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid()
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.symbol == context.ir.symbols.singleArgumentInlineFunction) return expression
        return super.visitCall(expression)
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        if (expression.origin is InlinerExpressionLocationHint) {
            inlinedVarStack += expression.origin as InlinerExpressionLocationHint
            expression.transformChildrenVoid()
            inlinedVarStack.removeLast()
            return expression
        }

        if (expression.statements.isNotEmpty() && expression.statements.first() is IrInlineMarker) {
            val marker = expression.statements.first() as IrInlineMarker
            inlineStack += marker
            expression.transformChildrenVoid()
            inlineStack.removeLast()
            return expression
        }

        return super.visitBlock(expression)
    }

    // Basically we want to remove all anonymous classes after inline
    // Except for those that are required to present as a copy, see `isRequiredToBeDeclaredOnCallSite`
    override fun visitClass(declaration: IrClass): IrStatement {
        // After first two checks we are sure that class declaration is unchanged and is declared either on call site or on callee site
        if (inlineStack.isEmpty() || declaration.attributeOwnerIdBeforeInline != null || declaration.isRequiredToBeDeclaredOnCallSite()) {
            return super.visitClass(declaration)
        }

        return IrCompositeImpl(declaration.startOffset, declaration.endOffset, context.irBuiltIns.unitType)
    }

    override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
        expression.symbol.owner.accept(this, null)
        return super.visitFunctionReference(expression)
    }

    // This function return `true` if given declaration must present on call site.
    // Everything outside blocks with `InlinerExpressionLocationHint` origin can be dropped
    // Declarations inside these blocks must remain if it is not a default one
    // To understand that we can check parent of original declaration
    private fun IrAttributeContainer.isRequiredToBeDeclaredOnCallSite(): Boolean {
        if (inlinedVarStack.isEmpty()) return false
        val declaration = when (val original = this.attributeOwnerId) {
            is IrClass -> original
            is IrFunctionExpression -> original.function
            is IrFunctionReference -> return true // `inlinedVarStack.isEmpty()` ensures that this reference was declared on call site
            else -> return false
        }
        return declaration.parentsWithSelf.any { it == inlinedVarStack.last().inlineAtSymbol.owner }
    }
}