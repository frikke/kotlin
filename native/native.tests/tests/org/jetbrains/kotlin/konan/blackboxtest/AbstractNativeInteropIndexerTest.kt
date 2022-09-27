/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.testFramework.TestDataFile
import org.jetbrains.kotlin.konan.blackboxtest.support.*
import org.jetbrains.kotlin.konan.blackboxtest.support.compilation.*
import org.jetbrains.kotlin.konan.blackboxtest.support.compilation.TestCompilationArtifact.*
import org.jetbrains.kotlin.konan.blackboxtest.support.settings.*
import org.jetbrains.kotlin.konan.blackboxtest.support.util.*
import org.junit.jupiter.api.Tag
import org.jetbrains.kotlin.konan.blackboxtest.support.runner.*
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals

abstract class AbstractNativeInteropIndexerFModulesTest : AbstractNativeInteropIndexerTest() {
    override val fmodules = true
}

abstract class AbstractNativeInteropIndexerNoFModulesTest : AbstractNativeInteropIndexerTest() {
    override val fmodules = false
}

@Tag("interop-indexer")
abstract class AbstractNativeInteropIndexerTest : AbstractNativeSimpleTest() {
    abstract val fmodules: Boolean

    @Synchronized
    protected fun runTest(@TestDataFile testPath: String) {
        val testPathFull = getAbsoluteFile(testPath)
        val testDataDir = testPathFull.parentFile.parentFile
        val includeFolder = testDataDir.resolve("include")
        val defFile = testPathFull.resolve("pod1.def")

        val testBuildDir = testRunSettings.get<SimpleTestDirectories>().testBuildDir
        val klibFile = testBuildDir.resolve("pod1.klib")

        val fmodulesArgs = if (fmodules) arrayOf("-compiler-option", "-fmodules") else arrayOf()
        val includeFrameworkArgs = if (testDataDir.name == "simple")
            arrayOf("-compiler-option", "-I${includeFolder.canonicalPath}")
        else
            arrayOf("-compiler-option", "-F${testDataDir.canonicalPath}")

        invokeCInterop(defFile, klibFile, includeFrameworkArgs + fmodulesArgs)

        val contents = invokeKLibContents(klibFile)

        val expectedOutput = testPathFull.resolve("contents.gold.txt").readText()
        assertEquals(StringUtilRt.convertLineSeparators(expectedOutput), StringUtilRt.convertLineSeparators(contents))
    }
}
