/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.types.IrType

class IrBlockImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    override val origin: IrStatementOrigin? = null,
) : IrBlock() {
    constructor(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        origin: IrStatementOrigin?,
        statements: List<IrStatement>
    ) : this(startOffset, endOffset, type, origin) {
        this.statements.addAll(statements)
    }
}

fun IrBlockImpl.addIfNotNull(statement: IrStatement?) {
    if (statement != null) statements.add(statement)
}

fun IrBlockImpl.inlineStatement(statement: IrStatement) {
    if (statement is IrBlock) {
        statements.addAll(statement.statements)
    } else {
        statements.add(statement)
    }
}

class IrReturnableBlockImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    override val symbol: IrReturnableBlockSymbol,
    override val origin: IrStatementOrigin? = null,
    override val inlineFunctionSymbol: IrFunctionSymbol? = null
) : IrReturnableBlock() {
    @ObsoleteDescriptorBasedAPI
    override val descriptor: FunctionDescriptor
        get() = symbol.descriptor

    constructor(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        symbol: IrReturnableBlockSymbol,
        origin: IrStatementOrigin?,
        statements: List<IrStatement>,
        inlineFunctionSymbol: IrFunctionSymbol? = null
    ) : this(startOffset, endOffset, type, symbol, origin, inlineFunctionSymbol) {
        this.statements.addAll(statements)
    }

    init {
        symbol.bind(this)
    }
}

class IrInlinedFunctionBlockImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    override val inlineCall: IrFunctionAccessExpression,
    override val inlinedElement: IrElement,
    override val inlineFunctionSymbol: IrFunctionSymbol,
    override val origin: IrStatementOrigin? = null,
) : IrInlinedFunctionBlock() {
    private val IrFunction.needsInlining get() = this.isInline && !this.isExternal

//    override val inlineFunctionSymbol: IrFunctionSymbol
//        get() = when (inlinedElement) {
//            is IrFunction -> inlinedElement.symbol
//            is IrFunctionExpression -> inlinedElement.function.symbol
//            is IrFunctionReference -> when {
//                inlinedElement.symbol.owner.needsInlining -> inlinedElement.symbol
//                else -> wrapInStubFunction(inlineCall, inlinedElement)
//            }
//            is IrPropertyReference -> wrapInStubFunction(inlineCall, inlinedElement)
//            else -> throw AssertionError("Not supported ir element for inlining ${inlinedElement.dump()}")
//        }

    constructor(
        startOffset: Int,
        endOffset: Int,
        type: IrType,
        inlineCall: IrFunctionAccessExpression,
        inlinedElement: IrElement,
        inlineFunctionSymbol: IrFunctionSymbol,
        origin: IrStatementOrigin?,
        statements: List<IrStatement>,
    ) : this(startOffset, endOffset, type, inlineCall, inlinedElement, inlineFunctionSymbol, origin) {
        this.statements.addAll(statements)
    }

//    constructor(
//        startOffset: Int,
//        endOffset: Int,
//        type: IrType,
//        inlineCall: IrFunctionAccessExpression,
//        inlinedElement: IrCallableReference<*>,
//        origin: IrStatementOrigin?
//    ) : this(startOffset, endOffset, type, inlineCall, inlinedElement, origin) {
//        inlineFunctionSymbol = inlineCall.symbol
//        this.statements.addAll(statements)
//    }

//    private fun wrapInStubFunction(
//        inlinedCall: IrExpression, reference: IrCallableReference<*>
//    ): IrFunctionSymbol {
//        // Note: This function is not exist in tree. It is appeared only in `IrInlinedFunctionBlock` as intermediate callee.
//        return IrFactoryImpl.buildFun {
//            startOffset = inlinedCall.startOffset
//            endOffset = inlinedCall.endOffset
//            name = Name.identifier("stub_for_ir_inlining")
//            visibility = DescriptorVisibilities.LOCAL
//            returnType = inlinedCall.type
//        }.apply {
//            body = IrFactoryImpl.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET).apply {
//                val statement = if (reference is IrPropertyReference && reference.field != null) {
//                    val field = reference.field!!.owner
//                    val boundReceiver = reference.dispatchReceiver ?: reference.extensionReceiver
//                    val fieldReceiver = if (field.isStatic) null else boundReceiver
//                    IrGetFieldImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, field.symbol, field.type, fieldReceiver)
//                } else {
//                    // Here return type must be `nothing`, but this expression is not supposed to be used, so it can be anything
//                    IrReturnImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, inlinedCall.type, symbol, inlinedCall)
//                }
//                statements += statement
//            }
//            // TODO
////            parent = callee.parent
//        }.symbol
//    }
}
