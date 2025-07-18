/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.jvm.compiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import kotlin.collections.CollectionsKt;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.analyzer.AnalysisResult;
import org.jetbrains.kotlin.cli.common.output.OutputUtilsKt;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.codegen.GenerationUtils;
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime;
import org.jetbrains.kotlin.codegen.state.GenerationState;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.JVMConfigurationKeys;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.jetbrains.kotlin.descriptors.ModuleDescriptor;
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor;
import org.jetbrains.kotlin.jvm.compiler.javac.JavacRegistrarForTests;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil;
import org.jetbrains.kotlin.test.*;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.utils.ExceptionUtilsKt;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.jetbrains.kotlin.checkers.ThirdPartyAnnotationPathsKt.FOREIGN_JDK8_ANNOTATIONS_SOURCES_PATH;
import static org.jetbrains.kotlin.codegen.forTestCompile.TestCompilePaths.KOTLIN_THIRDPARTY_JAVA8_ANNOTATIONS_PATH;

public class LoadDescriptorUtil {
    @NotNull
    public static final FqName TEST_PACKAGE_FQNAME = FqName.topLevel(Name.identifier("test"));

    private LoadDescriptorUtil() {
    }

    @NotNull
    public static ModuleDescriptor compileKotlinToDirAndGetModule(
            @NotNull List<File> kotlinFiles, @NotNull File outDir, @NotNull KotlinCoreEnvironment environment
    ) {
        GenerationState state = GenerationUtils.compileFiles(createKtFiles(kotlinFiles, environment), environment);
        OutputUtilsKt.writeAllTo(state.getFactory(), outDir);
        return state.getModule();
    }

    @NotNull
    public static Pair<PackageViewDescriptor, BindingContext> loadTestPackageAndBindingContextFromJavaRoot(
            @NotNull File javaRoot,
            @NotNull Disposable disposable,
            @NotNull TestJdkKind testJdkKind,
            @NotNull ConfigurationKind configurationKind,
            boolean isBinaryRoot,
            boolean usePsiClassReading,
            boolean useJavacWrapper,
            boolean withForeignAnnotations,
            @Nullable LanguageVersionSettings explicitLanguageVersionSettings
    ) {
        return loadTestPackageAndBindingContextFromJavaRoot(
                javaRoot,
                disposable,
                testJdkKind,
                configurationKind,
                isBinaryRoot,
                usePsiClassReading,
                useJavacWrapper,
                withForeignAnnotations,
                explicitLanguageVersionSettings,
                Collections.emptyList(),
                (configuration) -> {}
        );
    }

    @NotNull
    public static Pair<PackageViewDescriptor, BindingContext> loadTestPackageAndBindingContextFromJavaRoot(
            @NotNull File javaRoot,
            @NotNull Disposable disposable,
            @NotNull TestJdkKind testJdkKind,
            @NotNull ConfigurationKind configurationKind,
            boolean isBinaryRoot,
            boolean usePsiClassReading,
            boolean useJavacWrapper,
            boolean withForeignAnnotations,
            @Nullable LanguageVersionSettings explicitLanguageVersionSettings,
            @NotNull List<File> additionalClasspath,
            @NotNull Consumer<KotlinCoreEnvironment> configureEnvironment
    ) {
        List<File> javaBinaryRoots = new ArrayList<>();
        // TODO: use the same additional binary roots as those were used for compilation
        if (withForeignAnnotations) {
            String foreignAnnotationsPath =
                    System.getProperty(KOTLIN_THIRDPARTY_JAVA8_ANNOTATIONS_PATH, FOREIGN_JDK8_ANNOTATIONS_SOURCES_PATH);
            javaBinaryRoots.add(MockLibraryUtilExt.compileJavaFilesLibraryToJar(foreignAnnotationsPath, "foreign-annotations"));
        }
        javaBinaryRoots.add(KtTestUtil.getAnnotationsJar());
        javaBinaryRoots.add(ForTestCompileRuntime.jvmAnnotationsForTests());
        javaBinaryRoots.addAll(additionalClasspath);

        List<File> javaSourceRoots = new ArrayList<>();
        javaSourceRoots.add(ForTestCompileRuntime.transformTestDataPath("compiler/testData/loadJava/include"));
        if (isBinaryRoot) {
            javaBinaryRoots.add(javaRoot);
        }
        else {
            javaSourceRoots.add(javaRoot);
        }
        CompilerConfiguration configuration =
                KotlinTestUtils.newConfiguration(configurationKind, testJdkKind, javaBinaryRoots, javaSourceRoots);
        configuration.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, usePsiClassReading);
        configuration.put(JVMConfigurationKeys.USE_JAVAC, useJavacWrapper);
        if (explicitLanguageVersionSettings != null) {
            configuration.put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, explicitLanguageVersionSettings);
        }
        KotlinCoreEnvironment environment =
                KotlinCoreEnvironment.createForTests(disposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES);
        configureEnvironment.accept(environment);
        if (useJavacWrapper) {
            JavacRegistrarForTests.INSTANCE.registerJavac(environment);
        }
        @SuppressWarnings("deprecation")
        AnalysisResult analysisResult = JvmResolveUtil.analyze(environment);

        PackageViewDescriptor packageView = analysisResult.getModuleDescriptor().getPackage(TEST_PACKAGE_FQNAME);
        return Pair.create(packageView, analysisResult.getBindingContext());
    }

    public static void compileJavaWithAnnotationsJar(
            @NotNull Collection<File> javaFiles,
            @NotNull File outDir,
            @NotNull List<String> additionalArgs,
            @Nullable File customJdkHomeForJavac,
            boolean useJetbrainsAnnotationsWithTypeUse
    ) throws IOException {
        List<String> args = new ArrayList<>(Arrays.asList(
                "-sourcepath", ForTestCompileRuntime.transformTestDataPath("compiler/testData/loadJava/include").getPath(),
                "-d", outDir.getPath())
        );

        List<File> classpath = new ArrayList<>();

        classpath.add(ForTestCompileRuntime.runtimeJarForTests());
        if (useJetbrainsAnnotationsWithTypeUse) {
            classpath.add(MockLibraryUtilExt.compileJavaFilesLibraryToJar(
                    System.getProperty(KOTLIN_THIRDPARTY_JAVA8_ANNOTATIONS_PATH, FOREIGN_JDK8_ANNOTATIONS_SOURCES_PATH),
                    "foreign-annotations"
            ));
        }
        classpath.add(KtTestUtil.getAnnotationsJar());

        for (File test : javaFiles) {
            String content = FilesKt.readText(test, Charsets.UTF_8);

            args.addAll(InTextDirectivesUtils.findListWithPrefixes(content, "JAVAC_OPTIONS:"));

            if (InTextDirectivesUtils.isDirectiveDefined(content, "WITH_KOTLIN_JVM_ANNOTATIONS")) {
                classpath.add(ForTestCompileRuntime.jvmAnnotationsForTests());
            }
        }

        args.add("-classpath");
        args.add(classpath.stream().map(File::getPath).collect(Collectors.joining(File.pathSeparator)));

        args.addAll(additionalArgs);

        JvmCompilationUtils.compileJavaFiles(javaFiles, args, customJdkHomeForJavac).assertSuccessful();
    }

    @NotNull
    private static List<KtFile> createKtFiles(@NotNull List<File> kotlinFiles, @NotNull KotlinCoreEnvironment environment) {
        return CollectionsKt.map(kotlinFiles, kotlinFile -> {
            try {
                return KtTestUtil.createFile(kotlinFile.getName(), FileUtil.loadFile(kotlinFile, true), environment.getProject());
            }
            catch (IOException e) {
                throw ExceptionUtilsKt.rethrow(e);
            }
        });
    }
}
