/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.test.generators.GenerateCompilerTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("compiler/testData/codegen/script")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class ScriptCodegenTestGenerated extends AbstractScriptCodegenTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, TargetBackend.JVM, testDataFilePath);
    }

    @TestMetadata("adder.kts")
    public void testAdder() throws Exception {
        runTest("compiler/testData/codegen/script/adder.kts");
    }

    public void testAllFilesPresentInScript() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler/testData/codegen/script"), Pattern.compile("^(.+)\\.kts$"), null, TargetBackend.JVM, true);
    }

    @TestMetadata("classLiteralInsideFunction.kts")
    public void testClassLiteralInsideFunction() throws Exception {
        runTest("compiler/testData/codegen/script/classLiteralInsideFunction.kts");
    }

    @TestMetadata("destructuringDeclaration.kts")
    public void testDestructuringDeclaration() throws Exception {
        runTest("compiler/testData/codegen/script/destructuringDeclaration.kts");
    }

    @TestMetadata("destructuringDeclarationUnderscore.kts")
    public void testDestructuringDeclarationUnderscore() throws Exception {
        runTest("compiler/testData/codegen/script/destructuringDeclarationUnderscore.kts");
    }

    @TestMetadata("empty.kts")
    public void testEmpty() throws Exception {
        runTest("compiler/testData/codegen/script/empty.kts");
    }

    @TestMetadata("helloWorld.kts")
    public void testHelloWorld() throws Exception {
        runTest("compiler/testData/codegen/script/helloWorld.kts");
    }

    @TestMetadata("inline.kts")
    public void testInline() throws Exception {
        runTest("compiler/testData/codegen/script/inline.kts");
    }

    @TestMetadata("innerClass.kts")
    public void testInnerClass() throws Exception {
        runTest("compiler/testData/codegen/script/innerClass.kts");
    }

    @TestMetadata("kt20707.kts")
    public void testKt20707() throws Exception {
        runTest("compiler/testData/codegen/script/kt20707.kts");
    }

    @TestMetadata("kt22029.kts")
    public void testKt22029() throws Exception {
        runTest("compiler/testData/codegen/script/kt22029.kts");
    }

    @TestMetadata("kt48025.kts")
    public void testKt48025() throws Exception {
        runTest("compiler/testData/codegen/script/kt48025.kts");
    }

    @TestMetadata("localDelegatedProperty.kts")
    public void testLocalDelegatedProperty() throws Exception {
        runTest("compiler/testData/codegen/script/localDelegatedProperty.kts");
    }

    @TestMetadata("localDelegatedPropertyInLambda.kts")
    public void testLocalDelegatedPropertyInLambda() throws Exception {
        runTest("compiler/testData/codegen/script/localDelegatedPropertyInLambda.kts");
    }

    @TestMetadata("localDelegatedPropertyNoExplicitType.kts")
    public void testLocalDelegatedPropertyNoExplicitType() throws Exception {
        runTest("compiler/testData/codegen/script/localDelegatedPropertyNoExplicitType.kts");
    }

    @TestMetadata("localFunction.kts")
    public void testLocalFunction() throws Exception {
        runTest("compiler/testData/codegen/script/localFunction.kts");
    }

    @TestMetadata("outerCapture.kts")
    public void testOuterCapture() throws Exception {
        runTest("compiler/testData/codegen/script/outerCapture.kts");
    }

    @TestMetadata("parameter.kts")
    public void testParameter() throws Exception {
        runTest("compiler/testData/codegen/script/parameter.kts");
    }

    @TestMetadata("parameterArray.kts")
    public void testParameterArray() throws Exception {
        runTest("compiler/testData/codegen/script/parameterArray.kts");
    }

    @TestMetadata("parameterClosure.kts")
    public void testParameterClosure() throws Exception {
        runTest("compiler/testData/codegen/script/parameterClosure.kts");
    }

    @TestMetadata("parameterLong.kts")
    public void testParameterLong() throws Exception {
        runTest("compiler/testData/codegen/script/parameterLong.kts");
    }

    @TestMetadata("secondLevelFunction.kts")
    public void testSecondLevelFunction() throws Exception {
        runTest("compiler/testData/codegen/script/secondLevelFunction.kts");
    }

    @TestMetadata("secondLevelFunctionClosure.kts")
    public void testSecondLevelFunctionClosure() throws Exception {
        runTest("compiler/testData/codegen/script/secondLevelFunctionClosure.kts");
    }

    @TestMetadata("secondLevelVal.kts")
    public void testSecondLevelVal() throws Exception {
        runTest("compiler/testData/codegen/script/secondLevelVal.kts");
    }

    @TestMetadata("simpleClass.kts")
    public void testSimpleClass() throws Exception {
        runTest("compiler/testData/codegen/script/simpleClass.kts");
    }

    @TestMetadata("string.kts")
    public void testString() throws Exception {
        runTest("compiler/testData/codegen/script/string.kts");
    }

    @TestMetadata("topLevelFunction.kts")
    public void testTopLevelFunction() throws Exception {
        runTest("compiler/testData/codegen/script/topLevelFunction.kts");
    }

    @TestMetadata("topLevelFunctionClosure.kts")
    public void testTopLevelFunctionClosure() throws Exception {
        runTest("compiler/testData/codegen/script/topLevelFunctionClosure.kts");
    }

    @TestMetadata("topLevelLocalDelegatedProperty.kts")
    public void testTopLevelLocalDelegatedProperty() throws Exception {
        runTest("compiler/testData/codegen/script/topLevelLocalDelegatedProperty.kts");
    }

    @TestMetadata("topLevelPropertiesWithGetSet.kts")
    public void testTopLevelPropertiesWithGetSet() throws Exception {
        runTest("compiler/testData/codegen/script/topLevelPropertiesWithGetSet.kts");
    }

    @TestMetadata("topLevelProperty.kts")
    public void testTopLevelProperty() throws Exception {
        runTest("compiler/testData/codegen/script/topLevelProperty.kts");
    }

    @TestMetadata("topLevelPropertyWithProvideDelegate.kts")
    public void testTopLevelPropertyWithProvideDelegate() throws Exception {
        runTest("compiler/testData/codegen/script/topLevelPropertyWithProvideDelegate.kts");
    }

    @TestMetadata("topLevelTypealias.kts")
    public void testTopLevelTypealias() throws Exception {
        runTest("compiler/testData/codegen/script/topLevelTypealias.kts");
    }

    @TestMetadata("compiler/testData/codegen/script/scriptInstanceCapturing")
    @TestDataPath("$PROJECT_ROOT")
    @RunWith(JUnit3RunnerWithInners.class)
    public static class ScriptInstanceCapturing extends AbstractScriptCodegenTest {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTest, TargetBackend.JVM, testDataFilePath);
        }

        public void testAllFilesPresentInScriptInstanceCapturing() throws Exception {
            KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("compiler/testData/codegen/script/scriptInstanceCapturing"), Pattern.compile("^(.+)\\.kts$"), null, TargetBackend.JVM, true);
        }

        @TestMetadata("anonymousObjectCapturesProperty.kts")
        public void testAnonymousObjectCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/anonymousObjectCapturesProperty.kts");
        }

        @TestMetadata("classCapturesExtensionIndirect.kts")
        public void testClassCapturesExtensionIndirect() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/classCapturesExtensionIndirect.kts");
        }

        @TestMetadata("classCapturesExtensionIndirect2x.kts")
        public void testClassCapturesExtensionIndirect2x() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/classCapturesExtensionIndirect2x.kts");
        }

        @TestMetadata("classCapturesFunction.kts")
        public void testClassCapturesFunction() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/classCapturesFunction.kts");
        }

        @TestMetadata("classCapturesProperty.kts")
        public void testClassCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/classCapturesProperty.kts");
        }

        @TestMetadata("classCapturesPropertyInStringTemplate.kts")
        public void testClassCapturesPropertyInStringTemplate() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/classCapturesPropertyInStringTemplate.kts");
        }

        @TestMetadata("classCapturesPropertyIndirect.kts")
        public void testClassCapturesPropertyIndirect() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/classCapturesPropertyIndirect.kts");
        }

        @TestMetadata("classCapturesPropertyIndirect2x.kts")
        public void testClassCapturesPropertyIndirect2x() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/classCapturesPropertyIndirect2x.kts");
        }

        @TestMetadata("companionCapturesProperty.kts")
        public void testCompanionCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/companionCapturesProperty.kts");
        }

        @TestMetadata("enumCapturesProperty.kts")
        public void testEnumCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/enumCapturesProperty.kts");
        }

        @TestMetadata("enumEntryCapturesProperty.kts")
        public void testEnumEntryCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/enumEntryCapturesProperty.kts");
        }

        @TestMetadata("innerClassesHierarchyCaptureProperty.kts")
        public void testInnerClassesHierarchyCaptureProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/innerClassesHierarchyCaptureProperty.kts");
        }

        @TestMetadata("interfaceCapturesProperty.kts")
        public void testInterfaceCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/interfaceCapturesProperty.kts");
        }

        @TestMetadata("nestedAndOuterClassesCaptureProperty.kts")
        public void testNestedAndOuterClassesCaptureProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/nestedAndOuterClassesCaptureProperty.kts");
        }

        @TestMetadata("nestedClassCapturesProperty.kts")
        public void testNestedClassCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/nestedClassCapturesProperty.kts");
        }

        @TestMetadata("nestedInnerClassCapturesProperty.kts")
        public void testNestedInnerClassCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/nestedInnerClassCapturesProperty.kts");
        }

        @TestMetadata("nestedToObjectClassCapturesProperty.kts")
        public void testNestedToObjectClassCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/nestedToObjectClassCapturesProperty.kts");
        }

        @TestMetadata("objectCapturesProperty.kts")
        public void testObjectCapturesProperty() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/objectCapturesProperty.kts");
        }

        @TestMetadata("objectCapturesPropertyIndirect.kts")
        public void testObjectCapturesPropertyIndirect() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/objectCapturesPropertyIndirect.kts");
        }

        @TestMetadata("objectCapturesPropertyViaExtension.kts")
        public void testObjectCapturesPropertyViaExtension() throws Exception {
            runTest("compiler/testData/codegen/script/scriptInstanceCapturing/objectCapturesPropertyViaExtension.kts");
        }
    }
}
