/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bitcode

import kotlinBuildProperties
import org.gradle.api.*
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.ExecClang
import org.jetbrains.kotlin.cpp.*
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.target.SanitizerKind
import org.jetbrains.kotlin.konan.target.TargetDomainObjectContainer
import org.jetbrains.kotlin.konan.target.TargetWithSanitizer
import org.jetbrains.kotlin.testing.native.GoogleTestExtension
import org.jetbrains.kotlin.utils.capitalized
import javax.inject.Inject

private fun String.snakeCaseToUpperCamelCase() = split('_').joinToString(separator = "") { it.capitalized }

private fun fullTaskName(name: String, targetName: String, sanitizer: SanitizerKind?) = "${targetName}${name.snakeCaseToUpperCamelCase()}${sanitizer.taskSuffix}"

private val SanitizerKind?.taskSuffix
    get() = when (this) {
        null -> ""
        SanitizerKind.ADDRESS -> "_ASAN"
        SanitizerKind.THREAD -> "_TSAN"
    }

private val SanitizerKind?.dirSuffix
    get() = when (this) {
        null -> ""
        SanitizerKind.ADDRESS -> "-asan"
        SanitizerKind.THREAD -> "-tsan"
    }

private val SanitizerKind?.description
    get() = when (this) {
        null -> ""
        SanitizerKind.ADDRESS -> " with ASAN"
        SanitizerKind.THREAD -> " with TSAN"
    }

private abstract class RunGTestSemaphore : BuildService<BuildServiceParameters.None>
private abstract class CompileTestsSemaphore : BuildService<BuildServiceParameters.None>

open class CompileToBitcodeExtension @Inject constructor(val project: Project) : TargetDomainObjectContainer<CompileToBitcodeExtension.Target>(project) {
    init {
        this.factory = { target ->
            project.objects.newInstance<Target>(this, target)
        }
    }

    val compileElementsBitcode by project.configurations.creating {
        description = "LLVM bitcode of C and C++ modules"
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(USAGE))
        }
    }

    // TODO: These should be set by the plugin users.
    private val DEFAULT_CPP_FLAGS = listOfNotNull(
            "-gdwarf-2".takeIf { project.kotlinBuildProperties.getBoolean("kotlin.native.isNativeRuntimeDebugInfoEnabled", false) },
            "-std=c++17",
            "-Werror",
            "-O2",
            "-fno-aligned-allocation", // TODO: Remove when all targets support aligned allocation in C++ runtime.
            "-Wall",
            "-Wextra",
            "-Wno-unused-parameter",  // False positives with polymorphic functions.
    )

    private val targetList = with(project) {
        provider { (rootProject.project(":kotlin-native").property("targetList") as? List<*>)?.filterIsInstance<String>() ?: emptyList() } // TODO: Can we make it better?
    }

    private val allTestsTasks by lazy {
        val name = project.name.capitalized
        targetList.get().associateBy(keySelector = { it }, valueTransform = {
            project.tasks.register("${it}${name}Tests") {
                description = "Runs all $name tests for $it"
                group = VERIFICATION_TASK_GROUP
            }
        })
    }

    abstract class Module @Inject constructor(
            private val owner: CompileToBitcodeExtension,
            private val name: String,
            private val _target: TargetWithSanitizer,
    ) : Named {
        val target by _target::target
        val sanitizer by _target::sanitizer

        override fun getName() = name

        private val project by owner::project

        // TODO: Modules should not have a single outputGroup. Each module should have several source sets: main, test support, tests, ...
        abstract val outputGroup: Property<String>
        abstract val srcRoot: DirectoryProperty
        abstract val outputFile: RegularFileProperty
        abstract val outputDirectory: DirectoryProperty
        abstract val compiler: Property<String>
        abstract val linkerArgs: ListProperty<String>
        abstract val compilerArgs: ListProperty<String>
        abstract val headersDirs: ConfigurableFileCollection
        abstract val inputFiles: ConfigurableFileTree
        abstract val compilerWorkingDirectory: DirectoryProperty
        abstract val dependencies: ListProperty<TaskProvider<*>>
        protected abstract val onlyIf: ListProperty<Spec<in Module>>

        fun onlyIf(spec: Spec<in Module>) {
            this.onlyIf.add(spec)
        }

        val task = project.tasks.register<CompileToBitcode>(fullTaskName(name, target.name, sanitizer), _target).apply {
            configure {
                outputGroup.finalizeValue()

                this.outputFile.set(this@Module.outputFile)
                this.outputDirectory.set(this@Module.outputDirectory)
                this.compiler.set(this@Module.compiler)
                this.linkerArgs.set(this@Module.linkerArgs)
                this.compilerArgs.set(this@Module.compilerArgs)
                this.headersDirs.from(this@Module.headersDirs)
                this.inputFiles.from(this@Module.inputFiles.dir)
                this.inputFiles.setIncludes(this@Module.inputFiles.includes)
                this.inputFiles.setExcludes(this@Module.inputFiles.excludes)
                this.compilerWorkingDirectory.set(this@Module.compilerWorkingDirectory)
                when (outputGroup.get()) {
                    "test" -> this.group = VERIFICATION_BUILD_TASK_GROUP
                    "main" -> this.group = BUILD_TASK_GROUP
                }
                this.description = "Compiles '${this@Module.name}' to bitcode for $_target"
                // TODO: Should depend only on the toolchain needed to build for the _target
                dependsOn(":kotlin-native:dependencies:update")
                dependsOn(this@Module.dependencies)

                onlyIf {
                    this@Module.onlyIf.get().all { spec ->
                        spec.isSatisfiedBy(this@Module)
                    }
                }
            }
        }
    }

    abstract class TestsGroup @Inject constructor(
            private val _target: TargetWithSanitizer,
    ) {
        val target by _target::target
        val sanitizer by _target::sanitizer
        abstract val testedModules: ListProperty<String>
        abstract val testSupportModules: ListProperty<String>
        abstract val dependentModules: ListProperty<String>
        abstract val testFrameworkModules: ListProperty<String>
        abstract val testLauncherModule: Property<String>
    }

    abstract class Target @Inject constructor(
            private val owner: CompileToBitcodeExtension,
            private val _target: TargetWithSanitizer,
    ) {
        val target by _target::target
        val sanitizer by _target::sanitizer

        private val project by owner::project

        private val compilationDatabase = project.extensions.getByType<CompilationDatabaseExtension>()
        private val execClang = project.extensions.getByType<ExecClang>()
        private val platformManager = project.extensions.getByType<PlatformManager>()

        // googleTestExtension is only used if testsGroup is used.
        private val googleTestExtension by lazy { project.extensions.getByType<GoogleTestExtension>() }

        // A shared service used to limit parallel execution of test binaries.
        private val runGTestSemaphore = project.gradle.sharedServices.registerIfAbsent("runGTestSemaphore", RunGTestSemaphore::class.java) {
            // Probably can be made configurable if test reporting moves away from simple gtest stdout dumping.
            maxParallelUsages.set(1)
        }

        // TODO: remove when tests compilation does not consume so much memory.
        private val compileTestsSemaphore = project.gradle.sharedServices.registerIfAbsent("compileTestsSemaphore", CompileTestsSemaphore::class.java) {
            maxParallelUsages.set(5)
        }

        private val compileElementsBitcode = owner.compileElementsBitcode.outgoing.variants.create("$_target") {
            attributes {
                attribute(TargetWithSanitizer.TARGET_ATTRIBUTE, _target)
            }
        }

        private fun addToCompdb(compileTask: CompileToBitcode) {
            compilationDatabase.target(_target) {
                entry {
                    val args = listOf(execClang.resolveExecutable(compileTask.compiler.get())) + compileTask.compilerFlags.get() + execClang.clangArgsForCppRuntime(target.name)
                    directory.set(compileTask.compilerWorkingDirectory)
                    files.setFrom(compileTask.inputFiles)
                    arguments.set(args)
                    // Only the location of output file matters, compdb does not depend on the compilation result.
                    output.set(compileTask.outputFile.locationOnly.map { it.asFile.absolutePath })
                }
                // Compile task depends on the toolchain (including headers) and on the source code (e.g. googletest).
                // compdb task should also have these dependencies. This way the generated database will point to the
                // code that actually exists.
                // TODO: Probably module should know dependencies on its own.
                task.configure {
                    dependsOn(compileTask.dependsOn)
                }
            }
        }

        private val modules: PolymorphicDomainObjectContainer<Module> = project.objects.polymorphicDomainObjectContainer(Module::class.java).apply {
            registerFactory(Module::class.java) { name ->
                project.objects.newInstance<Module>(owner, name, _target)
            }
        }

        fun module(
                name: String,
                action: Action<in Module>,
        ): Module {
            val module = modules.create<Module>(name) {
                this.srcRoot.convention(project.layout.projectDirectory.dir("src/$name"))
                this.outputGroup.convention("main")
                this.outputFile.convention(this.outputGroup.flatMap { project.layout.buildDirectory.file("bitcode/$it/$target${sanitizer.dirSuffix}/$name.bc") })
                this.outputDirectory.convention(this.outputGroup.flatMap { project.layout.buildDirectory.dir("bitcode/$it/$target${sanitizer.dirSuffix}/$name") })
                this.compiler.convention("clang++")
                this.compilerArgs.set(owner.DEFAULT_CPP_FLAGS)
                this.inputFiles.from(this.srcRoot.dir("cpp"))
                this.inputFiles.include("**/*.cpp", "**/*.mm")
                this.inputFiles.exclude("**/*Test.cpp", "**/*TestSupport.cpp", "**/*Test.mm", "**/*TestSupport.mm")
                this.headersDirs.from(this.srcRoot.dir("cpp"))
                this.compilerWorkingDirectory.set(project.layout.projectDirectory.dir("src"))
                action.execute(this)
                outputGroup.finalizeValue()
            }
            addToCompdb(module.task.get()) // TODO: Do not force task configuration.
            if (module.outputGroup.get() == "main") {
                compileElementsBitcode.artifact(module.task)
                // TODO: This seems to go against gradle conventions. So, each module should probably be in
                //       a gradle project of its own. Current project should be used for grouping (i.e. reexporting all
                //       compileElementsBitcode from subprojects under a single umbrella configuration) and integration testing.
                project.configurations.maybeCreate("${name}CompileElementsBitcode").apply {
                    description = "LLVM bitcode of $name module"
                    isCanBeConsumed = true
                    isCanBeResolved = false
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(USAGE))
                        attribute(MODULE_ATTRIBUTE, name)
                    }
                    outgoing {
                        variants {
                            create("$_target") {
                                attributes {
                                    attribute(TargetWithSanitizer.TARGET_ATTRIBUTE, _target)
                                }
                                artifact(module.task)
                            }
                        }
                    }
                }
            }
            return module
        }

        fun module(name: String): Provider<Module> = modules.named(name)

        fun testsGroup(
                testTaskName: String,
                action: Action<in TestsGroup>,
        ) {
            val testsGroup = project.objects.newInstance(TestsGroup::class.java, _target).apply {
                testFrameworkModules.convention(listOf("googletest", "googlemock"))
                testLauncherModule.convention("test_support")
                action.execute(this)
            }
            val target = testsGroup.target
            val sanitizer = testsGroup.sanitizer
            val testName = fullTaskName(testTaskName, target.name, sanitizer)
            val testedModules = testsGroup.testedModules.get().map { this.module(it).get() }
            val testedModulesTestTasks = testedModules.mapNotNull { module ->
                val name = "${fullTaskName(module.name, target.name, sanitizer)}TestBitcode"
                val task = project.tasks.findByName(name) as? CompileToBitcode
                        ?: project.tasks.create(name, CompileToBitcode::class.java, _target).apply {
                            this.outputFile.convention(project.layout.buildDirectory.file("bitcode/test/$target${sanitizer.dirSuffix}/${module.name}Tests.bc"))
                            this.outputDirectory.convention(project.layout.buildDirectory.dir("bitcode/test/$target${sanitizer.dirSuffix}/${module.name}Tests"))
                            this.compiler.convention("clang++")
                            this.compilerArgs.set(module.compilerArgs)
                            this.inputFiles.from(module.inputFiles.dir)
                            this.inputFiles.include("**/*Test.cpp", "**/*Test.mm")
                            this.headersDirs.setFrom(module.headersDirs)
                            this.headersDirs.from(googleTestExtension.headersDirs)
                            this.compilerWorkingDirectory.set(module.compilerWorkingDirectory)
                            this.group = VERIFICATION_BUILD_TASK_GROUP
                            this.description = "Compiles '${module.name}' tests to bitcode for $_target"

                            // TODO: Should depend only on the toolchain needed to build for the _target
                            dependsOn(":kotlin-native:dependencies:update")
                            dependsOn("downloadGoogleTest")

                            addToCompdb(this)
                        }
                task.takeUnless { t -> t.inputFiles.isEmpty }
            }
            val testSupportModules = testsGroup.testSupportModules.get().map { this.module(it).get() }
            val testedAndTestSupportModulesTestSupportTasks = (testedModules + testSupportModules).mapNotNull { module ->
                val name = "${fullTaskName(module.name, target.name, sanitizer)}TestSupportBitcode"
                val task = project.tasks.findByName(name) as? CompileToBitcode
                        ?: project.tasks.create(name, CompileToBitcode::class.java, _target).apply {
                            this.outputFile.convention(project.layout.buildDirectory.file("bitcode/test/$target${sanitizer.dirSuffix}/${module.name}TestSupport.bc"))
                            this.outputDirectory.convention(project.layout.buildDirectory.dir("bitcode/test/$target${sanitizer.dirSuffix}/${module.name}TestSupport"))
                            this.compiler.convention("clang++")
                            this.compilerArgs.set(module.compilerArgs)
                            this.inputFiles.from(module.inputFiles.dir)
                            this.inputFiles.include("**/*TestSupport.cpp", "**/*TestSupport.mm")
                            this.headersDirs.setFrom(module.headersDirs)
                            this.headersDirs.from(googleTestExtension.headersDirs)
                            this.compilerWorkingDirectory.set(module.compilerWorkingDirectory)
                            this.group = VERIFICATION_BUILD_TASK_GROUP
                            this.description = "Compiles '${module.name}' test support to bitcode for $_target"

                            // TODO: Should depend only on the toolchain needed to build for the _target
                            dependsOn(":kotlin-native:dependencies:update")
                            dependsOn("downloadGoogleTest")

                            addToCompdb(this)
                        }
                task.takeUnless { t -> t.inputFiles.isEmpty }
            }

            val compileTask = project.tasks.register<CompileToExecutable>("${testName}Compile") {
                description = "Compile tests group '$testTaskName' for $target${sanitizer.description}"
                group = VERIFICATION_BUILD_TASK_GROUP
                this.target.set(target)
                this.sanitizer.set(sanitizer)
                this.outputFile.set(project.layout.buildDirectory.file("bin/test/${target}/$testName.${target.family.exeSuffix}"))
                this.llvmLinkFirstStageOutputFile.set(project.layout.buildDirectory.file("bitcode/test/$target/$testName-firstStage.bc"))
                this.llvmLinkOutputFile.set(project.layout.buildDirectory.file("bitcode/test/$target/$testName.bc"))
                this.compilerOutputFile.set(project.layout.buildDirectory.file("obj/$target/$testName.o"))
                this.mimallocEnabled.set(testsGroup.dependentModules.get().any { it.contains("mimalloc") })
                this.mainFile.set(testsGroup.testLauncherModule.get().let { module(it).flatMap { it.task.flatMap { it.outputFile } } })
                // Include main sources from every dependency.
                this.inputFiles.from(testedModules.map { it.task })
                this.inputFiles.from(testSupportModules.map { it.task })
                this.inputFiles.from(testsGroup.dependentModules.get().map { module(it).flatMap { it.task } })
                this.inputFiles.from(testsGroup.testFrameworkModules.get().map { module(it).flatMap { it.task } })
                // Include test support sources from tested and test support dependencies.
                this.inputFiles.from(testedAndTestSupportModulesTestSupportTasks)
                // Include test sources from tested dependencies.
                this.inputFiles.from(testedModulesTestTasks)
                // Limit parallelism.
                usesService(compileTestsSemaphore)
            }

            val runTask = project.tasks.register<RunGTest>(testName) {
                description = "Runs tests group '$testTaskName' for $target${sanitizer.description}"
                group = VERIFICATION_TASK_GROUP
                this.testName.set(testName)
                executable.set(compileTask.flatMap { it.outputFile })
                dependsOn(compileTask)
                reportFileUnprocessed.set(project.layout.buildDirectory.file("testReports/$testName/report.xml"))
                reportFile.set(project.layout.buildDirectory.file("testReports/$testName/report-with-prefixes.xml"))
                filter.set(project.findProperty("gtest_filter") as? String)
                tsanSuppressionsFile.set(project.layout.projectDirectory.file("tsan_suppressions.txt"))
                this.target.set(target)

                usesService(runGTestSemaphore)
            }

            owner.allTestsTasks[target.name]!!.configure {
                dependsOn(runTask)
            }
        }
    }

    companion object {
        const val BUILD_TASK_GROUP = LifecycleBasePlugin.BUILD_GROUP
        const val VERIFICATION_TASK_GROUP = LifecycleBasePlugin.VERIFICATION_GROUP
        const val VERIFICATION_BUILD_TASK_GROUP = "verification build"

        @JvmField
        val USAGE = "llvm-bitcode"

        @JvmField
        val MODULE_ATTRIBUTE = Attribute.of("org.jetbrains.kotlin.bitcode.module", String::class.java)
    }
}

/**
 * Compiling C and C++ modules into LLVM bitcode.
 *
 * Creates [CompileToBitcodeExtension] extension named `bitcode`.
 *
 * Creates the following [configurations][org.gradle.api.artifacts.Configuration]:
 * * `compileElementsBitcode` - like `apiElements` (sort of) from java plugin, or `{variant}LinkElements` from C++ plugin. Contains bitcode produced by implementation-part of the modules.
 * * `{module}CompileElementsBitcode` - like `compileElementsBitcode` but for a single `module`.
 *
 * Each of the defined configuration has [Usage attribute][Usage] set to [CompileToBitcodeExtension.USAGE]. Module-specific configurations
 * additionally have a [CompileToBitcodeExtension.MODULE_ATTRIBUTE] set to the module name.
 * Each `*ElementsBitcode` configuration has variants with [TargetWithSanitizer.TARGET_ATTRIBUTE] values.
 *
 * @see CompileToBitcodeExtension extension that this plugin creates.
 */
open class CompileToBitcodePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply<CompilationDatabasePlugin>()
        project.apply<GitClangFormatPlugin>()
        project.dependencies.attributesSchema {
            attribute(TargetWithSanitizer.TARGET_ATTRIBUTE)
            attribute(CompileToBitcodeExtension.MODULE_ATTRIBUTE)
        }
        project.extensions.create<CompileToBitcodeExtension>("bitcode", project)
    }
}