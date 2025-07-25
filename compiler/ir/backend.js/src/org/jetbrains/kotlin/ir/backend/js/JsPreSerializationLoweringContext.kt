/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import org.jetbrains.kotlin.backend.common.PreSerializationLoweringContext
import org.jetbrains.kotlin.backend.common.ir.KlibSharedVariablesManager
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.impl.IrDynamicTypeImpl
import org.jetbrains.kotlin.types.Variance

class JsPreSerializationLoweringContext(
    irBuiltIns: IrBuiltIns,
    configuration: CompilerConfiguration,
    diagnosticReporter: DiagnosticReporter,
) : PreSerializationLoweringContext(irBuiltIns, configuration, diagnosticReporter) {
    val dynamicType: IrDynamicType = IrDynamicTypeImpl(emptyList(), Variance.INVARIANT)
    val intrinsics: JsIntrinsics by lazy { JsIntrinsics(irBuiltIns, configuration) }

    override val symbols: JsSymbols by lazy {
        JsSymbols(irBuiltIns, irFactory.stageController, intrinsics)
    }

    override val sharedVariablesManager by lazy { KlibSharedVariablesManager(symbols) }

    override val allowExternalInlining: Boolean
        get() = true
}
