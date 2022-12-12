/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bitcode

import kotlinBuildProperties
import org.gradle.api.*
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.file.*
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

    abstract class SourceSet @Inject constructor(
            private val owner: CompileToBitcodeExtension,
            private val module: Module,
            private val name: String,
            private val _target: TargetWithSanitizer,
    ) : Named {
        val target by _target::target
        val sanitizer by _target::sanitizer

        override fun getName() = name

        private val project by owner::project

        abstract val outputFile: RegularFileProperty
        abstract val outputDirectory: DirectoryProperty
        abstract val headersDirs: ConfigurableFileCollection
        abstract val inputFiles: ConfigurableFileTree
        abstract val dependencies: ListProperty<TaskProvider<*>>
        protected abstract val onlyIf: ListProperty<Spec<in SourceSet>>

        fun onlyIf(spec: Spec<in SourceSet>) {
            this.onlyIf.add(spec)
        }

        private fun computeOnlyIf() = onlyIf.get().all { spec ->
            spec.isSatisfiedBy(this)
        }

        private val taskName = when(name) {
            "main" -> fullTaskName(module.name, target.name, sanitizer)
            "testSupport" -> fullTaskName(module.name, target.name, sanitizer) + "TestSupportBitcode"
            "test" -> fullTaskName(module.name, target.name, sanitizer) + "TestBitcode"
            else -> TODO()
        }

        val task = project.tasks.register<CompileToBitcode>(taskName, _target).apply {
            configure {
                this.outputFile.set(this@SourceSet.outputFile)
                this.outputDirectory.set(this@SourceSet.outputDirectory)
                this.compiler.set(module.compiler)
                this.linkerArgs.set(module.linkerArgs)
                this.compilerArgs.set(module.compilerArgs)
                this.headersDirs.from(this@SourceSet.headersDirs)
                this.inputFiles.from(this@SourceSet.inputFiles.dir)
                this.inputFiles.setIncludes(this@SourceSet.inputFiles.includes)
                this.inputFiles.setExcludes(this@SourceSet.inputFiles.excludes)
                this.compilerWorkingDirectory.set(module.compilerWorkingDirectory)
                // TODO: Should depend only on the toolchain needed to build for the _target
                dependsOn(":kotlin-native:dependencies:update")
                dependsOn(this@SourceSet.dependencies)
                onlyIf {
                    computeOnlyIf()
                }
            }
        }
    }

    abstract class Module @Inject constructor(
            private val owner: CompileToBitcodeExtension,
            private val name: String,
            private val _target: TargetWithSanitizer,
            private val compileElementsBitcode: ConfigurationVariant,
    ) : Named {
        abstract class SourceSets @Inject constructor(
                private val module: Module,
                private val container: ExtensiblePolymorphicDomainObjectContainer<SourceSet>
        ) : ExtensiblePolymorphicDomainObjectContainer<SourceSet> by container {
            private val project by module::project

            private val compilationDatabase = project.extensions.getByType<CompilationDatabaseExtension>()
            private val execClang = project.extensions.getByType<ExecClang>()

            // googleTestExtension is only used if testSupport or tests are used.
            private val googleTestExtension by lazy { project.extensions.getByType<GoogleTestExtension>() }

            private fun addToCompdb(compileTask: CompileToBitcode) {
                compilationDatabase.target(module._target) {
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

            val main
                get() = named("main")

            fun main(action: Action<in SourceSet>) = create("main") {
                this.outputFile.convention(project.layout.buildDirectory.file("bitcode/$name/$target${sanitizer.dirSuffix}/${module.name}.bc"))
                this.outputDirectory.convention(project.layout.buildDirectory.dir("bitcode/$name/$target${sanitizer.dirSuffix}/${module.name}"))
                this.inputFiles.from(module.srcRoot.dir("cpp"))
                this.inputFiles.include("**/*.cpp", "**/*.mm")
                this.inputFiles.exclude("**/*Test.cpp", "**/*TestSupport.cpp", "**/*Test.mm", "**/*TestSupport.mm")
                this.headersDirs.setFrom(module.headersDirs)
                dependencies.set(module.dependencies)
                onlyIf {
                    module.computeOnlyIf()
                }
                task.configure {
                    this.group = BUILD_TASK_GROUP
                    this.description = "Compiles '${module.name}' to bitcode for ${module._target}"
                }
                action.execute(this)
                addToCompdb(task.get()) // TODO: Do not force task configuration.
                module.compileElementsBitcode.artifact(task)
                // TODO: This seems to go against gradle conventions. So, each module should probably be in
                //       a gradle project of its own. Current project should be used for grouping (i.e. reexporting all
                //       compileElementsBitcode from subprojects under a single umbrella configuration) and integration testing.
                project.configurations.maybeCreate("${module.name}CompileElementsBitcode").apply {
                    description = "LLVM bitcode of ${module.name} module"
                    isCanBeConsumed = true
                    isCanBeResolved = false
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(USAGE))
                        attribute(MODULE_ATTRIBUTE, module.name)
                    }
                    outgoing {
                        variants {
                            create("${module._target}") {
                                attributes {
                                    attribute(TargetWithSanitizer.TARGET_ATTRIBUTE, module._target)
                                }
                                artifact(task)
                            }
                        }
                    }
                }
            }

            val testSupport
                get() = named("testSupport")

            fun testSupport(action: Action<in SourceSet>) = create("testSupport") {
                this.outputFile.convention(project.layout.buildDirectory.file("bitcode/$name/$target${sanitizer.dirSuffix}/${module.name}TestSupport.bc"))
                this.outputDirectory.convention(project.layout.buildDirectory.dir("bitcode/$name/$target${sanitizer.dirSuffix}/${module.name}TestSupport"))
                this.inputFiles.from(module.srcRoot.dir("cpp"))
                this.inputFiles.include("**/*TestSupport.cpp", "**/*TestSupport.mm")
                this.headersDirs.setFrom(module.headersDirs)
                this.headersDirs.from(googleTestExtension.headersDirs)
                dependencies.set(module.dependencies)
                // TODO: Must generally depend on googletest module headers which must itself depend on sources being present.
                dependencies.add(project.tasks.named("downloadGoogleTest"))
                onlyIf {
                    module.computeOnlyIf()
                }
                task.configure {
                    this.group = VERIFICATION_BUILD_TASK_GROUP
                    this.description = "Compiles '${module.name}' test support to bitcode for ${module._target}"
                }
                action.execute(this)
                addToCompdb(task.get()) // TODO: Do not force task configuration.
            }

            val test
                get() = named("test")

            fun test(action: Action<in SourceSet>) = create("test") {
                this.outputFile.convention(project.layout.buildDirectory.file("bitcode/$name/$target${sanitizer.dirSuffix}/${module.name}Tests.bc"))
                this.outputDirectory.convention(project.layout.buildDirectory.dir("bitcode/$name/$target${sanitizer.dirSuffix}/${module.name}Tests"))
                this.inputFiles.from(module.srcRoot.dir("cpp"))
                this.inputFiles.include("**/*Test.cpp", "**/*Test.mm")
                this.headersDirs.setFrom(module.headersDirs)
                this.headersDirs.from(googleTestExtension.headersDirs)
                dependencies.set(module.dependencies)
                // TODO: Must generally depend on googletest module headers which must itself depend on sources being present.
                dependencies.add(project.tasks.named("downloadGoogleTest"))
                onlyIf {
                    module.computeOnlyIf()
                }
                task.configure {
                    this.group = VERIFICATION_BUILD_TASK_GROUP
                    this.description = "Compiles '${module.name}' tests to bitcode for ${module._target}"
                }
                action.execute(this)
                addToCompdb(task.get()) // TODO: Do not force task configuration.
            }
        }

        val target by _target::target
        val sanitizer by _target::sanitizer

        override fun getName() = name

        private val project by owner::project

        abstract val srcRoot: DirectoryProperty
        // TODO: This is actually API dependency. Make it so.
        abstract val headersDirs: ConfigurableFileCollection
        abstract val compiler: Property<String>
        abstract val linkerArgs: ListProperty<String>
        abstract val compilerArgs: ListProperty<String>
        abstract val compilerWorkingDirectory: DirectoryProperty
        abstract val dependencies: ListProperty<TaskProvider<*>>
        protected abstract val onlyIf: ListProperty<Spec<in Module>>

        fun onlyIf(spec: Spec<in Module>) {
            this.onlyIf.add(spec)
        }

        private fun computeOnlyIf() = onlyIf.get().all { spec ->
            spec.isSatisfiedBy(this)
        }

        val sourceSets by lazy {
            project.objects.newInstance<SourceSets>(this, project.objects.polymorphicDomainObjectContainer(SourceSet::class.java))
        }

        fun sourceSets(action: Action<in SourceSets>) = sourceSets.apply {
            action.execute(this)
        }

        init {
            sourceSets.registerFactory(SourceSet::class.java) { name ->
                project.objects.newInstance<SourceSet>(owner, this, name, _target)
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


        private val platformManager = project.extensions.getByType<PlatformManager>()

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

        private val modules: PolymorphicDomainObjectContainer<Module> = project.objects.polymorphicDomainObjectContainer(Module::class.java).apply {
            registerFactory(Module::class.java) { name ->
                project.objects.newInstance<Module>(owner, name, _target, compileElementsBitcode)
            }
        }

        fun module(
                name: String,
                action: Action<in Module>,
        ) = modules.create<Module>(name) {
            this.srcRoot.convention(project.layout.projectDirectory.dir("src/$name"))
            this.headersDirs.from(this.srcRoot.dir("cpp"))
            this.compiler.convention("clang++")
            this.compilerArgs.set(owner.DEFAULT_CPP_FLAGS)
            this.compilerWorkingDirectory.set(project.layout.projectDirectory.dir("src"))
            action.execute(this)
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

                // TODO: This should be solved by depending on configurations.
                fun outputOrNull(moduleName: String, sourceSet: String): RegularFile? {
                    return module(moduleName).get().sourceSets.findByName(sourceSet)?.task?.get()?.outputFile?.get()
                }

                // testLauncherModule must provide testSupport source set with the test launcher.
                this.mainFile.set(testsGroup.testLauncherModule.get().let { outputOrNull(it, "testSupport")!! })
                // Include main sources from every dependency.
                this.inputFiles.from(testsGroup.testedModules.get().mapNotNull { outputOrNull(it, "main") })
                this.inputFiles.from(testsGroup.testSupportModules.get().mapNotNull { outputOrNull(it, "main") })
                this.inputFiles.from(testsGroup.dependentModules.get().mapNotNull { outputOrNull(it, "main") })
                this.inputFiles.from(testsGroup.testFrameworkModules.get().mapNotNull { outputOrNull(it, "main") })
                // Include test support sources from tested and test support dependencies.
                this.inputFiles.from(testsGroup.testedModules.get().mapNotNull { outputOrNull(it, "testSupport") })
                this.inputFiles.from(testsGroup.testSupportModules.get().mapNotNull { outputOrNull(it, "testSupport") })
                // Include test sources from tested dependencies.
                this.inputFiles.from(testsGroup.testedModules.get().mapNotNull { outputOrNull(it, "tests") })
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