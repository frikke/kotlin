/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.copyAttributes
import org.jetbrains.kotlin.ir.util.copyParameterDeclarationsFrom
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.JvmNames
import org.jetbrains.kotlin.name.Name

val jvmExposeAnnotationPhase = makeIrFilePhase(
    ::JvmExposeAnnotationLowering,
    name = "JvmExpose declarations",
    description = "Lower declarations annotated with JvmExpose",
    prerequisite = setOf(jvmValueClassPhase),
)

private class JvmExposeAnnotationLowering(private val context: JvmBackendContext) : ClassLoweringPass {
//    fun createDeclarationForJava(source: IrSimpleFunction, replacement: IrSimpleFunction, mangledName: Name): IrSimpleFunction =
//        context.irFactory.buildFun {
//            updateFrom(source)
//            name = mangledName
//            returnType = source.returnType
//        }.apply {
//            copyParameterDeclarationsFrom(source)
//            annotations = source.annotations
//            parent = source.parent
//            // We need to ensure that this bridge has the same attribute owner as its static inline class replacement, since this
//            // is used in [CoroutineCodegen.isStaticInlineClassReplacementDelegatingCall] to identify the bridge and avoid generating
//            // a continuation class.
//            copyAttributes(source)
//        }

    override fun lower(irClass: IrClass) {
//        for (function in irClass.functions) {
//            if (!function.hasAnnotation(JvmNames.JVM_EXPOSE)) continue
//
//            val old = function.name
//            function.name = Name.identifier(old.identifier + JvmAbi.IMPL_SUFFIX_FOR_MANGLED_MEMBERS)
//
//            irClass.declarations += function.deepCopyWithSymbols().also {
//                it.name = old
//            }
//        }
    }
}
