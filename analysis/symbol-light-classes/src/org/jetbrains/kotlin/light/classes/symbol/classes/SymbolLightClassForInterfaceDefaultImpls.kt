/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.classes

import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.light.classes.symbol.modifierLists.SymbolLightClassModifierList
import org.jetbrains.kotlin.load.java.JvmAbi

internal class SymbolLightClassForInterfaceDefaultImpls(private val containingClass: SymbolLightInterfaceClass) :
    SymbolLightInterfaceClass(containingClass.kotlinOrigin, containingClass.ktModule) {
    override fun getQualifiedName(): String? = containingClass.qualifiedName?.let { it + ".${JvmAbi.DEFAULT_IMPLS_CLASS_NAME}" }

    override fun getName() = JvmAbi.DEFAULT_IMPLS_CLASS_NAME
    override fun getParent() = containingClass

    override fun copy() = SymbolLightClassForInterfaceDefaultImpls(containingClass)

    override fun getTypeParameterList(): PsiTypeParameterList? = null
    override fun getTypeParameters(): Array<PsiTypeParameter> = emptyArray()

    private val _modifierList: PsiModifierList? by lazy {
        val lazyModifiers = lazyOf(setOf(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL))
        val lazyAnnotations = lazyOf(emptyList<PsiAnnotation>())
        SymbolLightClassModifierList(this, lazyModifiers, lazyAnnotations)
    }

    override fun getModifierList(): PsiModifierList? = _modifierList

    override fun isInterface(): Boolean = false
    override fun isDeprecated(): Boolean = false
    override fun isAnnotationType(): Boolean = false
    override fun isEnum(): Boolean = false
    override fun hasTypeParameters(): Boolean = false
    override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = false
    override fun getExtendsList(): PsiReferenceList? = null

    @Throws(IncorrectOperationException::class)
    override fun setName(name: String): PsiElement {
        throw IncorrectOperationException("Impossible to rename DefaultImpls")
    }

    override fun getContainingClass() = containingClass

    override fun getOwnInnerClasses() = emptyList<PsiClass>()
}
