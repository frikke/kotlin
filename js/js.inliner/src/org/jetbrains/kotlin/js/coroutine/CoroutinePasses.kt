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

package org.jetbrains.kotlin.js.coroutine

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.*
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils
import org.jetbrains.kotlin.utils.singletonOrEmptyList

fun JsNode.collectNodesToSplit(): Set<JsNode> {
    val nodes = mutableSetOf<JsNode>()

    val visitor = object : RecursiveJsVisitor() {
        var childrenInSet = false

        override fun visitInvocation(invocation: JsInvocation) {
            super.visitInvocation(invocation)
            if (invocation.isSuspend) {
                nodes += invocation
                childrenInSet = true
            }
        }

        override fun visitReturn(x: JsReturn) {
            super.visitReturn(x)
            nodes += x
            childrenInSet = true
        }

        override fun visitBreak(x: JsBreak) {
            super.visitBreak(x)

            // It's a simplification
            // TODO: don't split break and continue statements when possible
            nodes += x
            childrenInSet = true
        }

        override fun visitContinue(x: JsContinue) {
            super.visitContinue(x)
            nodes += x
            childrenInSet = true
        }

        override fun visitElement(node: JsNode) {
            val oldChildrenInSet = childrenInSet
            childrenInSet = false

            node.acceptChildren(this)

            if (childrenInSet) {
                nodes += node
            }
            else {
                childrenInSet = oldChildrenInSet
            }
        }
    }

    visitor.accept(this)
    visitor.accept(this)

    return nodes
}

fun List<CoroutineBlock>.replaceCoroutineFlowStatements(context: CoroutineTransformationContext, program: JsProgram) {
    val blockIndexes = withIndex().associate { (index, block) -> Pair(block, index) }

    val blockReplacementVisitor = object : JsVisitorWithContextImpl() {
        override fun endVisit(x: JsDebugger, ctx: JsContext<in JsStatement>) {
            val target = x.targetBlock
            if (target != null) {
                val lhs = JsNameRef(context.stateFieldName, JsLiteral.THIS)
                val rhs = program.getNumberLiteral(blockIndexes[target]!!)
                ctx.replaceMe(JsExpressionStatement(JsAstUtils.assignment(lhs, rhs)).apply {
                    targetBlock = true
                })
            }

            val exceptionTarget = x.targetExceptionBlock
            if (exceptionTarget != null) {
                val lhs = JsNameRef(context.exceptionStateName, JsLiteral.THIS)
                val rhs = program.getNumberLiteral(blockIndexes[exceptionTarget]!!)
                ctx.replaceMe(JsExpressionStatement(JsAstUtils.assignment(lhs, rhs)).apply {
                    targetExceptionBlock = true
                })
            }

            val finallyPath = x.finallyPath
            if (finallyPath != null) {
                if (finallyPath.isNotEmpty()) {
                    val lhs = JsNameRef(context.finallyPathFieldName, JsLiteral.THIS)
                    val rhs = JsArrayLiteral(finallyPath.map { program.getNumberLiteral(blockIndexes[it]!!) })
                    ctx.replaceMe(JsExpressionStatement(JsAstUtils.assignment(lhs, rhs)).apply {
                        this.finallyPath = true
                    })
                }
                else {
                    ctx.removeMe()
                }
            }
        }
    }
    return forEach { blockReplacementVisitor.accept(it.jsBlock) }
}

fun CoroutineBlock.buildGraph(globalCatchBlock: CoroutineBlock?): Map<CoroutineBlock, Set<CoroutineBlock>> {
    // That's a little more than DFS due to need of tracking finally paths

    val visitedBlocks = mutableSetOf<CoroutineBlock>()
    val graph = mutableMapOf<CoroutineBlock, MutableSet<CoroutineBlock>>()

    fun visitBlock(block: CoroutineBlock) {
        if (block in visitedBlocks) return

        for (finallyPath in block.collectFinallyPaths()) {
            for ((finallySource, finallyTarget) in (listOf(block) + finallyPath).zip(finallyPath)) {
                if (graph.getOrPut(finallySource) { mutableSetOf() }.add(finallyTarget)) {
                    visitedBlocks -= finallySource
                }
            }
        }

        visitedBlocks += block

        val successors = graph.getOrPut(block) { mutableSetOf() }
        successors += block.collectTargetBlocks()
        if (block == this && globalCatchBlock != null) {
            successors += globalCatchBlock
        }
        successors.forEach(::visitBlock)
    }

    visitBlock(this)

    return graph
}

private fun CoroutineBlock.collectTargetBlocks(): Set<CoroutineBlock> {
    val targetBlocks = mutableSetOf<CoroutineBlock>()
    jsBlock.accept(object : RecursiveJsVisitor() {
        override fun visitDebugger(x: JsDebugger) {
            targetBlocks += x.targetExceptionBlock.singletonOrEmptyList() + x.targetBlock.singletonOrEmptyList()
        }
    })
    return targetBlocks
}

private fun CoroutineBlock.collectFinallyPaths(): List<List<CoroutineBlock>> {
    val finallyPaths = mutableListOf<List<CoroutineBlock>>()
    jsBlock.accept(object : RecursiveJsVisitor() {
        override fun visitDebugger(x: JsDebugger) {
            x.finallyPath?.let { finallyPaths += it }
        }
    })
    return finallyPaths
}

fun JsBlock.replaceLocalVariables(scope: JsScope, context: CoroutineTransformationContext, localVariables: Set<JsName>) {
    val visitor = object : JsVisitorWithContextImpl() {
        override fun endVisit(x: JsNameRef, ctx: JsContext<in JsNode>) {
            when {
                x.coroutineController -> {
                    ctx.replaceMe(JsNameRef(context.controllerFieldName, x.qualifier).apply {
                        sideEffects = SideEffectKind.PURE
                    })
                }

                x.coroutineResult -> {
                    ctx.replaceMe(JsNameRef(context.resultFieldName, x.qualifier).apply {
                        sideEffects = SideEffectKind.DEPENDS_ON_STATE
                    })
                }

                x.qualifier == null && x.name in localVariables -> {
                    val fieldName = scope.getFieldName(x.name!!)
                    ctx.replaceMe(JsNameRef(fieldName, JsLiteral.THIS))
                }
            }
        }

        override fun endVisit(x: JsVars, ctx: JsContext<in JsStatement>) {
            val declaredNames = x.vars.map { it.name }
            val totalCount = declaredNames.size
            val localVarCount = declaredNames.count()

            when {
                totalCount == localVarCount -> {
                    val assignments = x.vars.mapNotNull {
                        val fieldName = scope.getFieldName(it.name)
                        val initExpression = it.initExpression
                        if (initExpression != null) {
                            JsAstUtils.assignment(JsNameRef(fieldName, JsLiteral.THIS), it.initExpression)
                        }
                        else {
                            null
                        }
                    }
                    if (assignments.isNotEmpty()) {
                        ctx.replaceMe(JsExpressionStatement(JsAstUtils.newSequence(assignments)))
                    }
                    else {
                        ctx.removeMe()
                    }
                }
                localVarCount > 0 -> {
                    for (declaration in x.vars) {
                        if (declaration.name in localVariables) {
                            val fieldName = scope.getFieldName(declaration.name)
                            val assignment = JsAstUtils.assignment(JsNameRef(fieldName, JsLiteral.THIS), declaration.initExpression)
                            ctx.addPrevious(assignment.makeStmt())
                        }
                        else {
                            ctx.addPrevious(JsVars(declaration))
                        }
                    }
                    ctx.removeMe()
                }
            }
            super.endVisit(x, ctx)
        }
    }
    visitor.accept(this)
}

fun JsScope.getFieldName(variableName: JsName) = declareName("local\$${variableName.ident}")