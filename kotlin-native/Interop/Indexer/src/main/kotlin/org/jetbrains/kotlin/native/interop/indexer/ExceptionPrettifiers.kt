/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.native.interop.indexer

/**
 * Pattern matching machinery for making exceptions from
 * cinterop nicer.
 */
sealed interface ExceptionPrettifier {
    fun matches(exception: Exception): Boolean

    fun prettify(exception: Exception): Exception
}

/**
 * Trivial implementation that is useful as a terminal prettifier in a list: just rethrow exception if nobody knows
 * about it.
 */
object DumbExceptionPrettifier : ExceptionPrettifier {
    override fun matches(exception: Exception): Boolean = true

    override fun prettify(exception: Exception): Exception = exception
}

/**
 * Suggests to add `-compiler-option -fmodules` if the exception message hints about it.
 */
object ClangModulesDisabledPrettifier : ExceptionPrettifier {

    private val supportedPatterns = listOf(
            "use of '@import' when modules are disabled"
    )

    private val fmodulesHint = """
            It seems that library is using clang modules.
            Try adding `-compiler-option -fmodules` and run again. For example, in the case of cocoapods it would be 
            
            pod("PodName") {
               extraOpts += listOf("-compiler-option", "-fmodules")
            }
        """.trimIndent()

    override fun matches(exception: Exception): Boolean {
        return supportedPatterns.any { exception.message?.contains(it) == true }
    }

    override fun prettify(exception: Exception): Exception {
        return Exception(fmodulesHint)
    }
}

inline fun <T> withExceptionPrettifier(action: () -> T): T {
    return try {
        action()
    } catch (exception: Exception) {
        val prettifiers = listOf(
                ClangModulesDisabledPrettifier,
                DumbExceptionPrettifier
        )
        throw prettifiers.first { it.matches(exception) }.prettify(exception)
    }
}