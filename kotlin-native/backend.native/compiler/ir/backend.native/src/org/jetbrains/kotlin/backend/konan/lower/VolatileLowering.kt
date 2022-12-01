/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.DefaultDelegateFactory
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.getOrPut
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.backend.konan.*
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.NativeMapping
import org.jetbrains.kotlin.backend.konan.ir.buildSimpleAnnotation
import org.jetbrains.kotlin.backend.konan.llvm.IntrinsicType
import org.jetbrains.kotlin.backend.konan.llvm.tryGetIntrinsicType
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.*

object IR_DECLARATION_ORIGIN_VOLATILE : IrDeclarationOriginImpl("VOLATILE")

internal class VolatileLowering(val context: Context) : FileLoweringPass {
    private val symbols = context.ir.symbols
    private val irBuiltins = context.irBuiltIns
    private fun IrBuilderWithScope.irByteToBool(expression: IrExpression) = irCall(symbols.areEqualByValue[PrimitiveBinaryType.BYTE]!!).apply {
        putValueArgument(0, expression)
        putValueArgument(1, irByte(1))
    }
    private fun IrBuilderWithScope.irBoolToByte(expression: IrExpression) = irWhen(irBuiltins.byteType, listOf(
        irBranch(expression, irByte(1)),
        irElseBranch(irByte(0))
    ))
    val convertedBooleanFields = mutableSetOf<IrFieldSymbol>()
    fun IrField.requiresBooleanConversion() = (type == irBuiltins.booleanType && hasAnnotation(KonanFqNames.volatile)) || symbol in convertedBooleanFields

    private fun buildIntrinsicFunction(irField: IrField, intrinsicType: IntrinsicType, builder: IrSimpleFunction.() -> Unit) = context.irFactory.buildFun {
        isExternal = true
        origin = IR_DECLARATION_ORIGIN_VOLATILE
        name = Name.special("<${intrinsicType.name.decapitalizeSmart()}-${irField.name}>")
        startOffset = irField.startOffset
        endOffset = irField.endOffset
    }.apply {
        val parentClass = irField.parents.filterIsInstance<IrClass>().first()
        parent = parentClass
        addDispatchReceiver {
            startOffset = irField.startOffset
            endOffset = irField.endOffset
            type = parentClass.defaultType
        }
        builder()
        annotations += buildSimpleAnnotation(context.irBuiltIns,
                SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
                context.ir.symbols.typedIntrinsic.owner, intrinsicType.name)
    }

    private fun buildCasFunction(irField: IrField, intrinsicType: IntrinsicType, functionReturnType: IrType) =
            buildIntrinsicFunction(irField, intrinsicType ) {
                returnType = functionReturnType
                addValueParameter {
                    startOffset = irField.startOffset
                    endOffset = irField.endOffset
                    name = Name.identifier("expectedValue")
                    type = irField.type
                }
                addValueParameter {
                    startOffset = irField.startOffset
                    endOffset = irField.endOffset
                    name = Name.identifier("newValue")
                    type = irField.type
                }
            }

    private fun buildAtomicRWMFunction(irField: IrField, intrinsicType: IntrinsicType) =
            buildIntrinsicFunction(irField, intrinsicType) {
                returnType = irField.type
                addValueParameter {
                    startOffset = irField.startOffset
                    endOffset = irField.endOffset
                    name = Name.identifier("value")
                    type = irField.type
                }
            }


    private inline fun IrField.atomicFunction(type: NativeMapping.AtomicFunctionType, builder : () -> IrSimpleFunction): IrSimpleFunction {
        val key = NativeMapping.AtomicFunctionKey(this, type)
        return context.mapping.volatileFieldToAtomicFunction.getOrPut(key) {
            builder().also {
                context.mapping.functionToVolatileField[it] = this@atomicFunction
            }
        }
    }

    fun IrField.compareAndSetFunction() = atomicFunction(NativeMapping.AtomicFunctionType.COMPARE_AND_SET) {
        buildCasFunction(this, IntrinsicType.COMPARE_AND_SET, context.irBuiltIns.booleanType)
    }
    fun IrField.compareAndSwapFunction() = atomicFunction(NativeMapping.AtomicFunctionType.COMPARE_AND_SWAP) {
        buildCasFunction(this, IntrinsicType.COMPARE_AND_SWAP, type)
    }
    fun IrField.getAndSetFunction() = atomicFunction(NativeMapping.AtomicFunctionType.GET_AND_SET) {
        buildAtomicRWMFunction(this, IntrinsicType.GET_AND_SET)
    }
    fun IrField.getAndAddFunction() = atomicFunction(NativeMapping.AtomicFunctionType.GET_AND_ADD) {
        buildAtomicRWMFunction(this, IntrinsicType.GET_AND_ADD)
    }

    private fun IrField.isInteger() = type == context.irBuiltIns.intType ||
            type == context.irBuiltIns.longType ||
            type == context.irBuiltIns.shortType ||
            type == context.irBuiltIns.byteType

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(object : IrBuildingTransformer(context) {
            override fun visitClass(declaration: IrClass): IrStatement {
                declaration.transformChildrenVoid()
                declaration.declarations.transformFlat {
                    when {
                        it !is IrProperty -> null
                        it.backingField?.hasAnnotation(KonanFqNames.volatile) != true -> null
                        else -> {
                            val field = it.backingField!!
                            if (field.type.binaryTypeIsReference() && context.memoryModel != MemoryModel.EXPERIMENTAL) {
                                it.annotations = it.annotations.filterNot { it.symbol.owner.parentAsClass.hasEqualFqName(KonanFqNames.volatile) }
                                null
                            } else {
                                listOfNotNull(it,
                                        field.compareAndSetFunction(),
                                        field.compareAndSwapFunction(),
                                        field.getAndSetFunction(),
                                        if (field.isInteger()) field.getAndAddFunction() else null
                                )
                            }
                        }
                    }
                }
                return declaration
            }

            override fun visitField(declaration: IrField): IrStatement {
                if (declaration.type == irBuiltins.booleanType && declaration.hasAnnotation(KonanFqNames.volatile)) {
                    convertedBooleanFields.add(declaration.symbol)
                    declaration.type = irBuiltins.byteType
                }
                return super.visitField(declaration)
            }


            private fun unsupported(message: String) = builder.irCall(context.ir.symbols.throwIllegalArgumentExceptionWithMessage).apply {
                putValueArgument(0, builder.irString(message))
            }

            override fun visitGetField(expression: IrGetField): IrExpression {
                super.visitGetField(expression)
                return if (expression.symbol.owner.requiresBooleanConversion()) {
                    builder.at(expression).irByteToBool(expression.apply { type = irBuiltins.byteType })
                } else {
                    expression
                }
            }

            override fun visitSetField(expression: IrSetField): IrExpression {
                super.visitSetField(expression)
                return if (expression.symbol.owner.requiresBooleanConversion()) {
                    expression.apply { value = builder.at(value).irBoolToByte(value) }
                } else {
                    expression
                }
            }

            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid(this)
                val intrinsicType = tryGetIntrinsicType(expression)
                when (intrinsicType) {
                    IntrinsicType.COMPARE_AND_SET_FIELD -> {}
                    IntrinsicType.COMPARE_AND_SWAP_FIELD -> {}
                    IntrinsicType.GET_AND_SET_FIELD -> {}
                    IntrinsicType.GET_AND_ADD_FIELD -> {}
                    else -> return expression
                }
                builder.at(expression)
                val reference = expression.getValueArgument(0) as? IrPropertyReference
                        ?: return unsupported("Only compile-time known IrProperties supported for $intrinsicType")
                val property = reference.symbol.owner
                val backingField = property.backingField
                if (backingField?.type?.binaryTypeIsReference() == true && context.memoryModel != MemoryModel.EXPERIMENTAL) {
                    return unsupported("Only primitives are supported for $intrinsicType with legacy memory model")
                }
                if (backingField?.hasAnnotation(KonanFqNames.volatile) != true) {
                    return unsupported("Only volatile properties are supported for $intrinsicType")
                }
                val function = when (intrinsicType) {
                    IntrinsicType.COMPARE_AND_SET_FIELD -> backingField.compareAndSetFunction()
                    IntrinsicType.COMPARE_AND_SWAP_FIELD -> backingField.compareAndSwapFunction()
                    IntrinsicType.GET_AND_SET_FIELD -> backingField.getAndSetFunction()
                    IntrinsicType.GET_AND_ADD_FIELD -> backingField.getAndAddFunction()
                    else -> error("Unknown intrinsic $intrinsicType")
                }
                return builder.irCall(function).apply {
                    dispatchReceiver = expression.extensionReceiver
                    putValueArgument(0, expression.getValueArgument(1))
                    if (intrinsicType == IntrinsicType.COMPARE_AND_SET_FIELD || intrinsicType == IntrinsicType.COMPARE_AND_SWAP_FIELD) {
                        putValueArgument(1, expression.getValueArgument(2))
                    }
                }.let {
                    if (backingField.requiresBooleanConversion()) {
                        for (arg in 0 until it.valueArgumentsCount) {
                            it.putValueArgument(arg, builder.irBoolToByte(it.getValueArgument(arg)!!))
                        }
                        if (intrinsicType == IntrinsicType.COMPARE_AND_SWAP_FIELD || intrinsicType == IntrinsicType.GET_AND_SET_FIELD) {
                            builder.irByteToBool(it)
                        } else {
                            it
                        }
                    } else {
                        it
                    }
                }
            }
        })
    }
}