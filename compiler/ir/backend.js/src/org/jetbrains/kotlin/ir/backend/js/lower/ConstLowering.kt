/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isUnsigned
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.js.config.compileLongAsBigint

class ConstTransformer(private val context: JsIrBackendContext) : IrElementTransformerVoid() {
    private fun <C> lowerConst(
        expression: IrConst,
        irClass: IrClassSymbol,
        carrierFactory: (Int, Int, IrType, C) -> IrExpression,
        vararg args: C
    ): IrExpression {
        val constructor = irClass.constructors.single { it.owner.isPrimary }
        val argType = constructor.owner.parameters[0].type

        return IrConstructorCallImpl.fromSymbolOwner(
            expression.startOffset,
            expression.endOffset,
            irClass.defaultType,
            constructor
        ).apply {
            for (i in args.indices) {
                arguments[i] = carrierFactory(startOffset, endOffset, argType, args[i])
            }
        }
    }

    private fun createLong(expression: IrConst, v: Long): IrExpression {
        if (context.configuration.compileLongAsBigint) {
            // We don't just return `expression` because `expression.type` may be `ULong`,
            // which will confuse the autoboxing lowering downstream.
            return IrConstImpl.long(expression.startOffset, expression.endOffset, context.irBuiltIns.longType, v)
        }
        return lowerConst(expression, context.intrinsics.longClassSymbol, IrConstImpl.Companion::int, v.toInt(), (v shr 32).toInt())
    }

    override fun visitConst(expression: IrConst): IrExpression {
        with(context.intrinsics) {
            if (expression.type.isUnsigned() && expression.kind != IrConstKind.Null) {
                return when (expression.type.classifierOrNull) {
                    uByteClassSymbol -> lowerConst(
                        expression,
                        uByteClassSymbol,
                        IrConstImpl.Companion::byte,
                        expression.value as Byte
                    )

                    uShortClassSymbol -> lowerConst(
                        expression,
                        uShortClassSymbol,
                        IrConstImpl.Companion::short,
                        expression.value as Short
                    )

                    uIntClassSymbol -> lowerConst(
                        expression,
                        uIntClassSymbol,
                        IrConstImpl.Companion::int,
                        expression.value as Int
                    )

                    uLongClassSymbol -> lowerConst(
                        expression,
                        uLongClassSymbol,
                        { _, _, _, v -> createLong(expression, v) },
                        expression.value as Long
                    )

                    else -> compilationException("Unknown unsigned type", expression)
                }
            }
            return when {
                expression.kind is IrConstKind.Char ->
                    lowerConst(expression, charClassSymbol, IrConstImpl.Companion::int, (expression.value as Char).code)

                expression.kind is IrConstKind.Long ->
                    createLong(expression, expression.value as Long)

                else -> super.visitConst(expression)
            }
        }
    }
}

/**
 * Wraps `Long` and `Char` constants into constructor invocation.
 */
class ConstLowering(private val context: JsIrBackendContext) : BodyLoweringPass {
    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildrenVoid(ConstTransformer(context))
    }
}
