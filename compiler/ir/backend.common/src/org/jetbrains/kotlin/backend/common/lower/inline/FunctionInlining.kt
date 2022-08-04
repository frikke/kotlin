/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower.inline


import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.backend.common.ir.Symbols
import org.jetbrains.kotlin.backend.common.ir.isPure
import org.jetbrains.kotlin.backend.common.lower.InnerClassesSupport
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrInlineMarkerImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrReturnableBlockSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.OperatorNameConventions

fun IrValueParameter.isInlineParameter(type: IrType = this.type) =
    index >= 0 && !isNoinline && !type.isNullable() && (type.isFunction() || type.isSuspendFunction())

fun IrExpression.isAdaptedFunctionReference() =
    this is IrBlock && this.origin == IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE

interface InlineFunctionResolver {
    fun getFunctionDeclaration(symbol: IrFunctionSymbol): IrFunction
}

fun IrFunction.isTopLevelInPackage(name: String, packageName: String): Boolean {
    if (name != this.name.asString()) return false

    val containingDeclaration = parent as? IrPackageFragment ?: return false
    val packageFqName = containingDeclaration.fqName.asString()
    return packageName == packageFqName
}

fun IrFunction.isBuiltInSuspendCoroutineUninterceptedOrReturn(): Boolean =
    isTopLevelInPackage(
        "suspendCoroutineUninterceptedOrReturn",
        StandardNames.COROUTINES_INTRINSICS_PACKAGE_FQ_NAME.asString()
    )

open class DefaultInlineFunctionResolver(open val context: CommonBackendContext) : InlineFunctionResolver {
    override fun getFunctionDeclaration(symbol: IrFunctionSymbol): IrFunction {
        val function = symbol.owner
        // TODO: Remove these hacks when coroutine intrinsics are fixed.
        return when {
            function.isBuiltInSuspendCoroutineUninterceptedOrReturn() ->
                context.ir.symbols.suspendCoroutineUninterceptedOrReturn.owner

            symbol == context.ir.symbols.coroutineContextGetter ->
                context.ir.symbols.coroutineGetContext.owner

            else -> (symbol.owner as? IrSimpleFunction)?.resolveFakeOverride() ?: symbol.owner
        }
    }
}

class FunctionInlining(
    val context: CommonBackendContext,
    val inlineFunctionResolver: InlineFunctionResolver,
    val innerClassesSupport: InnerClassesSupport? = null,
    val insertAdditionalImplicitCasts: Boolean = false
) : IrElementTransformerVoidWithContext(), BodyLoweringPass {
    private val classesToRegenerate = mutableSetOf<IrAttributeContainer>()

    constructor(context: CommonBackendContext) : this(context, DefaultInlineFunctionResolver(context), null)
    constructor(context: CommonBackendContext, innerClassesSupport: InnerClassesSupport) : this(
        context,
        DefaultInlineFunctionResolver(context),
        innerClassesSupport
    )
    constructor(context: CommonBackendContext, innerClassesSupport: InnerClassesSupport?, insertAdditionalImplicitCasts: Boolean) : this(
        context,
        DefaultInlineFunctionResolver(context),
        innerClassesSupport,
        insertAdditionalImplicitCasts
    )

    private var containerScope: ScopeWithIr? = null

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        // TODO container: IrSymbolDeclaration
        containerScope = createScope(container as IrSymbolOwner)
        irBody.accept(this, null)
        containerScope = null

        irBody.patchDeclarationParents(container as? IrDeclarationParent ?: container.parent)
    }

    fun inline(irModule: IrModuleFragment) = irModule.accept(this, data = null)

    override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
        expression.transformChildrenVoid(this)
        val callee = when (expression) {
            is IrCall -> expression.symbol.owner
            is IrConstructorCall -> expression.symbol.owner
            else -> return expression
        }
        if (!callee.needsInlining)
            return expression
        if (Symbols.isLateinitIsInitializedPropertyGetter(callee.symbol))
            return expression
        if (Symbols.isTypeOfIntrinsic(callee.symbol))
            return expression

        val actualCallee = inlineFunctionResolver.getFunctionDeclaration(callee.symbol)
        if (actualCallee.body == null) {
            return expression
        }

        val parent = allScopes.map { it.irElement }.filterIsInstance<IrDeclarationParent>().lastOrNull()
            ?: allScopes.map { it.irElement }.filterIsInstance<IrDeclaration>().lastOrNull()?.parent
            ?: containerScope?.irElement as? IrDeclarationParent
            ?: (containerScope?.irElement as? IrDeclaration)?.parent

        val inliner = Inliner(expression, actualCallee, currentScope ?: containerScope!!, parent, context)
        return inliner.inline().apply {
            classesToRegenerate.forEach { it.setUpCorrectAttributeOwnerForInlinedElements() }
            classesToRegenerate.clear()
        }
    }

    private val IrFunction.needsInlining get() = this.isInline && !this.isExternal

    private fun IrElement.collectAllLocalClasses(): Set<IrAttributeContainer> {
        val classes = mutableSetOf<IrAttributeContainer>()
        this.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                classes += declaration
                return super.visitClass(declaration)
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression) {
                classes += expression
                return super.visitFunctionExpression(expression)
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                classes += expression
                return super.visitFunctionReference(expression)
            }
        }, null)
        return classes
    }

    private fun IrAttributeContainer.setUpCorrectAttributeOwnerForInlinedElements() {
        this.accept(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                if (element is IrAttributeContainer && element.attributeOwnerIdBeforeInline == null) {
                    element.attributeOwnerIdBeforeInline = element.attributeOwnerId.let { it.attributeOwnerIdBeforeInline ?: it }
                    element.attributeOwnerId = element
                }
                element.acceptChildrenVoid(this)
            }
        }, null)
    }

    private inner class Inliner(
        val callSite: IrFunctionAccessExpression,
        val callee: IrFunction,
        val currentScope: ScopeWithIr,
        val parent: IrDeclarationParent?,
        val context: CommonBackendContext
    ) {

        val copyIrElement = run {
            val typeParameters =
                if (callee is IrConstructor)
                    callee.parentAsClass.typeParameters
                else callee.typeParameters
            val typeArguments =
                (0 until callSite.typeArgumentsCount).associate {
                    typeParameters[it].symbol to callSite.getTypeArgument(it)
                }
            DeepCopyIrTreeWithSymbolsForInliner(typeArguments, parent)
        }

        val substituteMap = mutableMapOf<IrValueParameter, IrExpression>()

        fun inline() = inlineFunction(callSite, callee, true)

        private fun IrElement.copy(): IrElement {
            return copyIrElement.copy(this).apply {
                accept(object : IrElementVisitorVoid {
                    override fun visitElement(element: IrElement) {
                        if (element is IrAttributeContainer) {
                            element.attributeOwnerIdBeforeInline = null
                        }
                        element.acceptChildrenVoid(this)
                    }
                }, null)
            }
        }

        private fun IrFunction.isInlineOnly(): Boolean {
            val inlineOnlyName = FqName("kotlin.internal.InlineOnly")
            if (this is IrSimpleFunction && correspondingPropertySymbol != null) {
                return correspondingPropertySymbol!!.owner.hasAnnotation(inlineOnlyName)
            }
            return this.hasAnnotation(inlineOnlyName)
        }

        private fun inlineFunction(
            callSite: IrFunctionAccessExpression,
            callee: IrFunction,
            performRecursiveInline: Boolean
        ): IrReturnableBlock {
            val copiedCallee = (callee.copy() as IrFunction).apply {

                // It's a hack to overtake another hack in PIR. Due to PersistentIrFactory registers every created declaration
                // there also get temporary declaration like this which leads to some unclear behaviour. Since I am not aware
                // enough about PIR internals the simplest way seemed to me is to unregister temporary function. Hope it is going
                // to be removed ASAP along with registering every PIR declaration.

                parent = callee.parent
                if (performRecursiveInline) {
                    body?.transformChildrenVoid()
                    valueParameters.forEachIndexed { index, param ->
                        if (callSite.getValueArgument(index) == null) {
                            // Default values can recursively reference [callee] - transform only needed.
                            param.defaultValue = param.defaultValue?.transform(this@FunctionInlining, null)
                        }
                    }
                }
            }

            val evaluationStatements = evaluateArguments(callSite, copiedCallee)
            val statements = (copiedCallee.body as? IrBlockBody)?.statements
                ?: error("Body not found for function ${callee.render()}")

            val irReturnableBlockSymbol = IrReturnableBlockSymbolImpl()
            val endOffset = statements.lastOrNull()?.endOffset ?: callee.endOffset
            /* creates irBuilder appending to the end of the given returnable block: thus why we initialize
             * irBuilder with (..., endOffset, endOffset).
             */
            val irBuilder = context.createIrBuilder(irReturnableBlockSymbol, endOffset, endOffset)

            val transformer = ParameterSubstitutor()
            val newStatements = mutableListOf<IrStatement>()

            newStatements.addAll(evaluationStatements)
            val fileEntry = (parent as? IrDeclaration)?.fileOrNull?.fileEntry
            if (fileEntry != null && !callee.isInlineOnly()) {
                newStatements += IrInlineMarkerImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, callSite as IrCall, callee)
            }
            statements.mapTo(newStatements) { it.transform(transformer, data = null) as IrStatement }

            return IrReturnableBlockImpl(
                startOffset = callSite.startOffset,
                endOffset = callSite.endOffset,
                type = callSite.type,
                symbol = irReturnableBlockSymbol,
                origin = null,
                statements = newStatements,
                inlineFunctionSymbol = callee.symbol
            ).apply {
                transformChildrenVoid(object : IrElementTransformerVoid() {
                    override fun visitReturn(expression: IrReturn): IrExpression {
                        expression.transformChildrenVoid(this)

                        if (expression.returnTargetSymbol == copiedCallee.symbol) {
                            val expr =
                                if (insertAdditionalImplicitCasts)
                                    expression.value.implicitCastIfNeededTo(callSite.type)
                                else
                                    expression.value
                            return irBuilder.at(expression).irReturn(expr)
                        }
                        return expression
                    }
                })
                patchDeclarationParents(parent) // TODO: Why it is not enough to just run SetDeclarationsParentVisitor?
            }
        }

        //---------------------------------------------------------------------//

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

        private inner class ParameterSubstitutor : IrElementTransformerVoid() {
            private val containersStack = mutableListOf<IrAttributeContainer>()

            private fun saveDeclarationsFromStackIntoRegenerationPool() {
                containersStack.forEach { classesToRegenerate += it }
            }

            override fun visitClassReference(expression: IrClassReference): IrExpression {
                if (expression.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                return super.visitClassReference(expression)
            }

            override fun visitClass(declaration: IrClass): IrStatement {
                if (declaration.attributeOwnerId.attributeOwnerIdBeforeInline != null) classesToRegenerate += declaration
                containersStack += declaration
                if (declaration.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                return super.visitClass(declaration).apply { containersStack.removeLast() }
            }

            override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
                if (expression.attributeOwnerId.attributeOwnerIdBeforeInline != null) classesToRegenerate += expression
                containersStack += expression
                return super.visitFunctionExpression(expression).apply { containersStack.removeLast() }
            }

            override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
                if (expression.attributeOwnerId.attributeOwnerIdBeforeInline != null) classesToRegenerate += expression
                containersStack += expression
                return super.visitFunctionReference(expression).apply { containersStack.removeLast() }
            }

            override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
                if (expression.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()
                return super.visitTypeOperator(expression)
            }

            override fun visitGetValue(expression: IrGetValue): IrExpression {
                val newExpression = super.visitGetValue(expression) as IrGetValue
                if (newExpression.type.getClass()?.let { classesToRegenerate.contains(it) } == true) {
                    saveDeclarationsFromStackIntoRegenerationPool()
                }
                val argument = substituteMap[newExpression.symbol.owner] ?: return newExpression

                argument.transformChildrenVoid(this) // Default argument can contain subjects for substitution.

                var ret =
                    if (argument is IrGetValueWithoutLocation)
                        argument.withLocation(newExpression.startOffset, newExpression.endOffset)
                    else
                        (argument.copy() as IrExpression)

                if (insertAdditionalImplicitCasts)
                    ret = ret.implicitCastIfNeededTo(newExpression.type)
                saveDeclarationsFromStackIntoRegenerationPool()
                ((ret as? IrGetValue)?.symbol?.owner ?: ret).collectAllLocalClasses().forEach { classesToRegenerate += it }
                return ret
            }

            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.hasReifiedTypeParameters()) saveDeclarationsFromStackIntoRegenerationPool()

                if (!isLambdaCall(expression))
                    return super.visitCall(expression)

                val dispatchReceiver = expression.dispatchReceiver as? IrGetValue ?: return super.visitCall(expression)
                val functionArgument = substituteMap[dispatchReceiver.symbol.owner] ?: return super.visitCall(expression)
                if ((dispatchReceiver.symbol.owner as? IrValueParameter)?.isNoinline == true) return super.visitCall(expression)

                return when {
                    functionArgument is IrFunctionReference ->
                        inlineFunctionReference(expression, functionArgument, functionArgument.symbol.owner)

                    functionArgument.isAdaptedFunctionReference() ->
                        inlineAdaptedFunctionReference(expression, functionArgument as IrBlock)

                    functionArgument is IrFunctionExpression ->
                        inlineFunctionExpression(expression, functionArgument)

                    else ->
                        super.visitCall(expression)
                }.apply {
                    saveDeclarationsFromStackIntoRegenerationPool()
                    this.collectAllLocalClasses().forEach { classesToRegenerate += it }
                }
            }

            override fun visitInlineMarker(declaration: IrInlineMarker, data: Nothing?) = declaration

            fun inlineFunctionExpression(irCall: IrCall, irFunctionExpression: IrFunctionExpression): IrExpression {
                // Inline the lambda. Lambda parameters will be substituted with lambda arguments.
                val newExpression = inlineFunction(
                    irCall,
                    irFunctionExpression.function,
                    false
                )
                // Substitute lambda arguments with target function arguments.
                return newExpression.transform(this, null)
            }

            fun inlineAdaptedFunctionReference(irCall: IrCall, irBlock: IrBlock): IrExpression {
                val irFunction = irBlock.statements[0].let {
                    it.transformChildrenVoid(this)
                    copyIrElement.copy(it) as IrFunction
                }
                val irFunctionReference = irBlock.statements[1] as IrFunctionReference
                val inlinedFunctionReference = inlineFunctionReference(irCall, irFunctionReference, irFunction)
                return IrBlockImpl(
                    irCall.startOffset, irCall.endOffset,
                    inlinedFunctionReference.type, origin = null,
                    statements = listOf(irFunction, inlinedFunctionReference)
                )
            }

            fun inlineFunctionReference(
                irCall: IrCall,
                irFunctionReference: IrFunctionReference,
                inlinedFunction: IrFunction
            ): IrExpression {
                irFunctionReference.transformChildrenVoid(this)

                val function = irFunctionReference.symbol.owner
                val functionParameters = function.explicitParameters
                val boundFunctionParameters = irFunctionReference.getArgumentsWithIr()
                val unboundFunctionParameters = functionParameters - boundFunctionParameters.map { it.first }
                val boundFunctionParametersMap = boundFunctionParameters.associate { it.first to it.second }

                var unboundIndex = 0
                val unboundArgsSet = unboundFunctionParameters.toSet()
                val valueParameters = irCall.getArgumentsWithIr().drop(1) // Skip dispatch receiver.

                val superType = irFunctionReference.type as IrSimpleType
                val superTypeArgumentsMap = irCall.symbol.owner.parentAsClass.typeParameters.associate { typeParam ->
                    typeParam.symbol to superType.arguments[typeParam.index].typeOrNull!!
                }

                val immediateCall = with(irCall) {
                    when (inlinedFunction) {
                        is IrConstructor -> {
                            val classTypeParametersCount = inlinedFunction.parentAsClass.typeParameters.size
                            IrConstructorCallImpl.fromSymbolOwner(
                                startOffset,
                                endOffset,
                                inlinedFunction.returnType,
                                inlinedFunction.symbol,
                                classTypeParametersCount
                            )
                        }
                        is IrSimpleFunction ->
                            IrCallImpl(
                                startOffset,
                                endOffset,
                                inlinedFunction.returnType,
                                inlinedFunction.symbol,
                                inlinedFunction.typeParameters.size,
                                inlinedFunction.valueParameters.size
                            )
                        else ->
                            error("Unknown function kind : ${inlinedFunction.render()}")
                    }
                }.apply {
                    for (parameter in functionParameters) {
                        val argument =
                            if (parameter !in unboundArgsSet) {
                                val arg = boundFunctionParametersMap[parameter]!!
                                if (arg is IrGetValueWithoutLocation)
                                    arg.withLocation(irCall.startOffset, irCall.endOffset)
                                else arg.copy() as IrExpression
                            } else {
                                if (unboundIndex == valueParameters.size && parameter.defaultValue != null)
                                    parameter.defaultValue!!.expression.copy() as IrExpression
                                else if (!parameter.isVararg) {
                                    assert(unboundIndex < valueParameters.size) {
                                        "Attempt to use unbound parameter outside of the callee's value parameters"
                                    }
                                    valueParameters[unboundIndex++].second
                                } else {
                                    val elements = mutableListOf<IrVarargElement>()
                                    while (unboundIndex < valueParameters.size) {
                                        val (param, value) = valueParameters[unboundIndex++]
                                        val substitutedParamType = param.type.substitute(superTypeArgumentsMap)
                                        if (substitutedParamType == parameter.varargElementType!!)
                                            elements += value
                                        else
                                            elements += IrSpreadElementImpl(irCall.startOffset, irCall.endOffset, value)
                                    }
                                    IrVarargImpl(
                                        irCall.startOffset, irCall.endOffset,
                                        parameter.type,
                                        parameter.varargElementType!!,
                                        elements
                                    )
                                }
                            }
                        when (parameter) {
                            function.dispatchReceiverParameter ->
                                this.dispatchReceiver = argument.implicitCastIfNeededTo(inlinedFunction.dispatchReceiverParameter!!.type)

                            function.extensionReceiverParameter ->
                                this.extensionReceiver = argument.implicitCastIfNeededTo(inlinedFunction.extensionReceiverParameter!!.type)

                            else ->
                                putValueArgument(
                                    parameter.index,
                                    argument.implicitCastIfNeededTo(inlinedFunction.valueParameters[parameter.index].type)
                                )
                        }
                    }
                    assert(unboundIndex == valueParameters.size) { "Not all arguments of the callee are used" }
                    for (index in 0 until irFunctionReference.typeArgumentsCount)
                        putTypeArgument(index, irFunctionReference.getTypeArgument(index))
                }.implicitCastIfNeededTo(irCall.type)
                return super.visitExpression(immediateCall).transform(this@FunctionInlining, null)
            }

            override fun visitElement(element: IrElement) = element.accept(this, null)
        }

        private fun IrExpression.implicitCastIfNeededTo(type: IrType) =
            // No need to cast expressions of type nothing
            if (type == this.type || (insertAdditionalImplicitCasts && this.type == context.irBuiltIns.nothingType))
                this
            else
                IrTypeOperatorCallImpl(startOffset, endOffset, type, IrTypeOperator.IMPLICIT_CAST, type, this)

        private fun isLambdaCall(irCall: IrCall): Boolean {
            val callee = irCall.symbol.owner
            val dispatchReceiver = callee.dispatchReceiverParameter ?: return false
            assert(!dispatchReceiver.type.isKFunction())

            return (dispatchReceiver.type.isFunction() || dispatchReceiver.type.isSuspendFunction())
                    && callee.name == OperatorNameConventions.INVOKE
                    && irCall.dispatchReceiver is IrGetValue
        }

        private inner class ParameterToArgument(
            val parameter: IrValueParameter,
            val argumentExpression: IrExpression
        ) {

            val isInlinableLambdaArgument: Boolean
                get() = parameter.isInlineParameter() &&
                        (argumentExpression is IrFunctionReference
                                || argumentExpression is IrFunctionExpression
                                || argumentExpression.isAdaptedFunctionReference())

            val isImmutableVariableLoad: Boolean
                get() = argumentExpression.let { argument ->
                    argument is IrGetValue && !argument.symbol.owner.let { it is IrVariable && it.isVar }
                }
        }


        private fun ParameterToArgument.andAllOuterClasses(): List<ParameterToArgument> {
            val allParametersReplacements = mutableListOf(this)

            if (innerClassesSupport == null) return allParametersReplacements

            var currentThisSymbol = parameter.symbol
            var parameterClassDeclaration = parameter.type.classifierOrNull?.owner as? IrClass ?: return allParametersReplacements

            while (parameterClassDeclaration.isInner) {
                val outerClass = parameterClassDeclaration.parentAsClass
                val outerClassThis = outerClass.thisReceiver ?: error("${outerClass.name} has a null `thisReceiver` property")

                val parameterToArgument = ParameterToArgument(
                    parameter = outerClassThis,
                    argumentExpression = IrGetFieldImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        innerClassesSupport.getOuterThisField(parameterClassDeclaration).symbol,
                        outerClassThis.type,
                        IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, currentThisSymbol)
                    )
                )

                allParametersReplacements.add(parameterToArgument)

                currentThisSymbol = outerClassThis.symbol
                parameterClassDeclaration = outerClass
            }


            return allParametersReplacements
        }

        // callee might be a copied version of callsite.symbol.owner
        private fun buildParameterToArgument(callSite: IrFunctionAccessExpression, callee: IrFunction): List<ParameterToArgument> {

            val parameterToArgument = mutableListOf<ParameterToArgument>()

            if (callSite.dispatchReceiver != null && callee.dispatchReceiverParameter != null)
                parameterToArgument += ParameterToArgument(
                    parameter = callee.dispatchReceiverParameter!!,
                    argumentExpression = callSite.dispatchReceiver!!
                ).andAllOuterClasses()

            val valueArguments =
                callSite.symbol.owner.valueParameters.map { callSite.getValueArgument(it.index) }.toMutableList()

            if (callee.extensionReceiverParameter != null) {
                parameterToArgument += ParameterToArgument(
                    parameter = callee.extensionReceiverParameter!!,
                    argumentExpression = if (callSite.extensionReceiver != null) {
                        callSite.extensionReceiver!!
                    } else {
                        // Special case: lambda with receiver is called as usual lambda:
                        valueArguments.removeAt(0)!!
                    }
                )
            } else if (callSite.extensionReceiver != null) {
                // Special case: usual lambda is called as lambda with receiver:
                valueArguments.add(0, callSite.extensionReceiver!!)
            }

            val parametersWithDefaultToArgument = mutableListOf<ParameterToArgument>()
            for (parameter in callee.valueParameters) {
                val argument = valueArguments[parameter.index]
                when {
                    argument != null -> {
                        parameterToArgument += ParameterToArgument(
                            parameter = parameter,
                            argumentExpression = argument
                        )
                    }

                    // After ExpectDeclarationsRemoving pass default values from expect declarations
                    // are represented correctly in IR.
                    parameter.defaultValue != null -> {  // There is no argument - try default value.
                        parametersWithDefaultToArgument += ParameterToArgument(
                            parameter = parameter,
                            argumentExpression = parameter.defaultValue!!.expression
                        )
                    }

                    parameter.varargElementType != null -> {
                        val emptyArray = IrVarargImpl(
                            startOffset = callSite.startOffset,
                            endOffset = callSite.endOffset,
                            type = parameter.type,
                            varargElementType = parameter.varargElementType!!
                        )
                        parameterToArgument += ParameterToArgument(
                            parameter = parameter,
                            argumentExpression = emptyArray
                        )
                    }

                    else -> {
                        val message = "Incomplete expression: call to ${callee.render()} " +
                                "has no argument at index ${parameter.index}"
                        throw Error(message)
                    }
                }
            }
            // All arguments except default are evaluated at callsite,
            // but default arguments are evaluated inside callee.
            return parameterToArgument + parametersWithDefaultToArgument
        }

        //-------------------------------------------------------------------------//

        private fun evaluateArguments(functionReference: IrFunctionReference): List<IrStatement> {
            val arguments = functionReference.getArgumentsWithIr().map { ParameterToArgument(it.first, it.second) }
            val evaluationStatements = mutableListOf<IrStatement>()
            val substitutor = ParameterSubstitutor()
            val referenced = functionReference.symbol.owner
            arguments.forEach {
                val newArgument = if (it.isImmutableVariableLoad) {
                    it.argumentExpression.transform( // Arguments may reference the previous ones - substitute them.
                        substitutor,
                        data = null
                    )
                } else {
                    val newVariable =
                        currentScope.scope.createTemporaryVariable(
                            irExpression = it.argumentExpression.transform( // Arguments may reference the previous ones - substitute them.
                                substitutor,
                                data = null
                            ),
                            nameHint = callee.symbol.owner.name.asStringStripSpecialMarkers(),
                            isMutable = false
                        )

                    evaluationStatements.add(newVariable)

                    IrGetValueWithoutLocation(newVariable.symbol)
                }
                when (it.parameter) {
                    referenced.dispatchReceiverParameter -> functionReference.dispatchReceiver = newArgument
                    referenced.extensionReceiverParameter -> functionReference.extensionReceiver = newArgument
                    else -> functionReference.putValueArgument(it.parameter.index, newArgument)
                }
            }
            return evaluationStatements
        }

        private fun IrValueParameter.getOriginalParameter(): IrValueParameter {
            if (this.parent !is IrFunction) return this
            val original = (this.parent as IrFunction).originalFunction
            return listOf(original.dispatchReceiverParameter, original.extensionReceiverParameter, *original.valueParameters.toTypedArray())
                .single { it != null && it.name == this.name && it.startOffset == this.startOffset }!!
        }

        private fun evaluateArguments(callSite: IrFunctionAccessExpression, callee: IrFunction): List<IrStatement> {
            val arguments = buildParameterToArgument(callSite, callee)
            val evaluationStatements = mutableListOf<IrStatement>()
            val substitutor = ParameterSubstitutor()
            arguments.forEach { argument ->
                /*
                 * We need to create temporary variable for each argument except inlinable lambda arguments.
                 * For simplicity and to produce simpler IR we don't create temporaries for every immutable variable,
                 * not only for those referring to inlinable lambdas.
                 */
                if (argument.isInlinableLambdaArgument) {
                    substituteMap[argument.parameter] = argument.argumentExpression
                    (argument.argumentExpression as? IrFunctionReference)?.let { evaluationStatements += evaluateArguments(it) }

                    return@forEach
                }

                if (argument.isImmutableVariableLoad) {
                    substituteMap[argument.parameter] =
                        argument.argumentExpression.transform( // Arguments may reference the previous ones - substitute them.
                            substitutor,
                            data = null
                        )
                    return@forEach
                }

                // Arguments may reference the previous ones - substitute them.
                val variableInitializer = argument.argumentExpression.transform(substitutor, data = null)

                val argumentExtracted = !argument.argumentExpression.isPure(false, context = context)

                if (!argumentExtracted) {
                    // TODO add new parameter to lowering that will chose between inlining and copying
//                    substituteMap[argument.parameter] = variableInitializer
                    val newVariable =
                        currentScope.scope.createTemporaryVariable(
                            irExpression = variableInitializer,
                            nameHint = callee.symbol.owner.name.toString(),
                            isMutable = false,
                            irType = argument.parameter.getOriginalParameter().type
                        )

                    evaluationStatements.add(newVariable)
                    substituteMap[argument.parameter] = IrGetValueWithoutLocation(newVariable.symbol, InlinedArgument())
                } else {
                    val newVariable =
                        currentScope.scope.createTemporaryVariable(
                            irExpression = IrBlockImpl(
                                variableInitializer.startOffset,
                                variableInitializer.endOffset,
                                variableInitializer.type,
                                InlinerExpressionLocationHint((currentScope.irElement as IrSymbolOwner).symbol)
                            ).apply {
                                statements.add(variableInitializer)
                            },
                            nameHint = callee.symbol.owner.name.asStringStripSpecialMarkers(),
                            isMutable = false
                        )

                    evaluationStatements.add(newVariable)
                    substituteMap[argument.parameter] = IrGetValueWithoutLocation(newVariable.symbol)
                }
            }
            return evaluationStatements
        }
    }

    private class IrGetValueWithoutLocation(
        override val symbol: IrValueSymbol,
        override val origin: IrStatementOrigin? = null
    ) : IrGetValue() {
        override val startOffset: Int get() = UNDEFINED_OFFSET
        override val endOffset: Int get() = UNDEFINED_OFFSET

        override var type: IrType
            get() = symbol.owner.type
            set(value) {
                symbol.owner.type = value
            }

        override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D) =
            visitor.visitGetValue(this, data)

        fun withLocation(startOffset: Int, endOffset: Int) =
            IrGetValueImpl(startOffset, endOffset, type, symbol, origin)
    }
}

class InlinedArgument: IrStatementOrigin

class InlinerExpressionLocationHint(val inlineAtSymbol: IrSymbol) : IrStatementOrigin {
    override fun toString(): String =
        "(${this.javaClass.simpleName} : $functionNameOrDefaultToString @${functionFileOrNull?.fileEntry?.name})"

    private val functionFileOrNull: IrFile?
        get() = (inlineAtSymbol as? IrFunction)?.file

    private val functionNameOrDefaultToString: String
        get() = (inlineAtSymbol as? IrFunction)?.name?.asString() ?: inlineAtSymbol.toString()
}
