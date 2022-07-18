/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.flattenStringConcatenationPhase
import org.jetbrains.kotlin.backend.common.lower.loops.forLoopsPhase
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.org.objectweb.asm.Type

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
        return super.visitContainerExpression(expression).apply { innerInlinedCalls-- }
    }

    override fun visitClass(declaration: IrClass) {
        if (innerInlinedCalls > 0) {
            context.inlinedAnonymousClassToOriginal[declaration] = declaration.attributeOwnerId
            declaration.setUpAttributeOwnerIfToSelf()
            context.putLocalClassType(declaration, Type.getObjectType("inlinedClass" + count++))
        }
        super.visitClass(declaration)
    }

    private fun IrElement.setUpAttributeOwnerIfToSelf() {
        (this as? IrAttributeContainer)?.let { attributeOwnerId = this }
//        accept(object : IrElementVisitorVoid {
//            override fun visitElement(element: IrElement) {
//                if (element is IrAttributeContainer) {
//                    element.attributeOwnerId = element
//                }
//                element.acceptChildrenVoid(this)
//            }
//        }, null)
    }
}