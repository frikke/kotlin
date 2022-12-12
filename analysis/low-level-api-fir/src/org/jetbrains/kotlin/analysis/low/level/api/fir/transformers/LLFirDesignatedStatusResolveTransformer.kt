/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirPhaseRunner
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.FirDesignationWithFile
import org.jetbrains.kotlin.analysis.low.level.api.fir.transformers.LLFirLazyTransformer.Companion.updatePhaseDeep
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkDeclarationStatusIsResolved
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkPhase
import org.jetbrains.kotlin.fir.FirElementWithResolvePhase
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.FirStatusResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.StatusComputationSession
import org.jetbrains.kotlin.fir.visitors.transformSingle

/**
 * Transform designation into STATUS phase. Affects only for designation, target declaration, it's children and dependents
 */
internal class LLFirDesignatedStatusResolveTransformer(
    private val designation: FirDesignationWithFile,
    private val session: FirSession,
    private val scopeSession: ScopeSession,
) : LLFirLazyTransformer {
    private inner class FirDesignatedStatusResolveTransformerForIDE(
        private val designationIterator: Iterator<FirElementWithResolvePhase>
    ) : FirStatusResolveTransformer(session, scopeSession, StatusComputationSession()) {

        private var isInsideTargetDeclaration = false


        override fun transformClass(klass: FirClass, data: FirResolvedDeclarationStatus?): FirStatement {
            return storeClass(klass) {
                klass.typeParameters.forEach { it.transformSingle(this, data = null) }
                if (isInsideTargetDeclaration) {
                    transformDeclarationContent(klass, data)
                } else {
                    for (member in klass.declarations) {
                        if (member !is FirClassLikeDeclaration) {
                            member.transformSingle(this, data)
                        }
                    }
                    moveNextDeclaration()
                }
                klass
            } as FirStatement
        }

        fun moveNextDeclaration() {
            if (!designationIterator.hasNext()) {
                isInsideTargetDeclaration = true
                designation.target.transform<FirDeclaration, _>(this, data = null)
                return
            }
            when (val nextElement = designationIterator.next()) {
                is FirClass -> {
                    transformClassContent(nextElement, data = null)
                }
                else -> {
                    error("Unexpected declaration in designation: ${nextElement::class.qualifiedName}")
                }
            }
        }
    }


    override fun transformDeclaration(phaseRunner: LLFirPhaseRunner) {
        if (designation.target.resolvePhase >= FirResolvePhase.STATUS) return
        designation.target.checkPhase(FirResolvePhase.TYPES)

        val designationIterator = designation.toSequence(includeTarget = false).iterator()
        val transformer = FirDesignatedStatusResolveTransformerForIDE(designationIterator)
        phaseRunner.runPhaseWithCustomResolve(FirResolvePhase.STATUS) {
            transformer.moveNextDeclaration()
        }

        updatePhaseDeep(designation.target, FirResolvePhase.STATUS)
        checkIsResolved(designation.target)
    }

    override fun checkIsResolved(target: FirElementWithResolvePhase) {
        if (target !is FirAnonymousInitializer) {
            target.checkPhase(FirResolvePhase.STATUS)
        }
        if (target is FirMemberDeclaration) {
            checkDeclarationStatusIsResolved(target)
        }
        checkNestedDeclarationsAreResolved(target)
    }
}
