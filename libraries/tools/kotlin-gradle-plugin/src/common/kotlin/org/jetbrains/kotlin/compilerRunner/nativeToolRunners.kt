/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compilerRunner

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.build.report.metrics.BuildMetricsReporter
import org.jetbrains.kotlin.build.report.metrics.GradleBuildPerformanceMetric
import org.jetbrains.kotlin.build.report.metrics.GradleBuildTime
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.gradle.dsl.NativeCacheKind
import org.jetbrains.kotlin.gradle.dsl.NativeCacheOrchestration
import org.jetbrains.kotlin.gradle.internal.properties.NativeProperties
import org.jetbrains.kotlin.gradle.internal.properties.nativeProperties
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.useXcodeMessageStyle
import org.jetbrains.kotlin.gradle.targets.native.KonanPropertiesBuildService
import org.jetbrains.kotlin.gradle.utils.newInstance
import org.jetbrains.kotlin.konan.properties.resolvablePropertyString
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.DependencyDirectories
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.inject.Inject

internal fun Project.getKonanCacheKind(target: KonanTarget): NativeCacheKind =
    kotlinPropertiesProvider.getKonanCacheKind(target, KonanPropertiesBuildService.registerIfAbsent(this))

internal fun PropertiesProvider.getKonanCacheKind(
    target: KonanTarget,
    konanPropertiesBuildService: Provider<KonanPropertiesBuildService>
): NativeCacheKind {
    val commonCacheKind = nativeCacheKind
    val targetCacheKind = nativeCacheKindForTarget(target)
    return when {
        targetCacheKind != null -> targetCacheKind
        commonCacheKind != null -> commonCacheKind
        else -> konanPropertiesBuildService.get().defaultCacheKindForTarget(target)
    }
}

internal fun Project.getKonanCacheOrchestration(): NativeCacheOrchestration {
    return PropertiesProvider(this).nativeCacheOrchestration ?: NativeCacheOrchestration.Compiler
}

internal fun Project.isKonanIncrementalCompilationEnabled(): Boolean {
    return PropertiesProvider(this).incrementalNative ?: false
}

internal fun Project.getKonanParallelThreads(): Int {
    return PropertiesProvider(this).nativeParallelThreads ?: 4
}

private val Project.kotlinNativeCompilerJar: Provider<File>
    get() = nativeProperties.isUseEmbeddableCompilerJar
        .zip(nativeProperties.actualNativeHomeDirectory) { useJar, nativeHomeDir ->
            if (useJar) {
                nativeHomeDir.resolve("konan/lib/kotlin-native-compiler-embeddable.jar")
            } else {
                nativeHomeDir.resolve("konan/lib/kotlin-native.jar")
            }
        }

internal abstract class KotlinNativeToolRunner(
    protected val toolName: String,
    private val settings: Settings,
    metricsReporter: BuildMetricsReporter<GradleBuildTime, GradleBuildPerformanceMetric>,
    objectsFactory: ObjectFactory,
    execOperations: ExecOperations,
) : KotlinToolRunner(metricsReporter, objectsFactory, execOperations) {

    class Settings(
        val konanHome: String,
        val konanPropertiesFile: File,
        val useXcodeMessageStyle: Boolean,
        val jvmArgs: List<String>,
        val classpath: FileCollection,
        val konanDataDir: String?,
        val kotlinCompilerArgumentsLogLevel: Provider<KotlinCompilerArgumentsLogLevel>,
        val execEnvironmentBlacklist: Provider<Set<String>>
    ) {
        companion object {
            fun of(
                konanHome: String,
                konanDataDir: String?,
                project: Project,
            ) = Settings(
                konanHome = konanHome,
                konanPropertiesFile = project.file("${konanHome}/konan/konan.properties"),
                useXcodeMessageStyle = project.useXcodeMessageStyle.get(),
                jvmArgs = project.nativeProperties.jvmArgs.get(),
                classpath = project.files(project.kotlinNativeCompilerJar, "${konanHome}/konan/lib/trove4j.jar"),
                konanDataDir = konanDataDir,
                kotlinCompilerArgumentsLogLevel = project.kotlinPropertiesProvider.kotlinCompilerArgumentsLogLevel,
                execEnvironmentBlacklist = KonanPropertiesBuildService.registerIfAbsent(project).map { it.environmentBlacklist },
            )
        }
    }

    final override val displayName get() = toolName

    final override val mainClass get() = "org.jetbrains.kotlin.cli.utilities.MainKt"
    final override val daemonEntryPoint
        get() = if (!settings.useXcodeMessageStyle) "daemonMain" else "daemonMainWithXcodeRenderer"

    // We need to unset some environment variables which are set by XCode and may potentially affect the tool executed.
    final override val execEnvironmentBlacklist: Set<String>
        get() = settings.execEnvironmentBlacklist.get()

    final override val execSystemProperties by lazy {
        val messageRenderer = if (settings.useXcodeMessageStyle) MessageRenderer.XCODE_STYLE else MessageRenderer.GRADLE_STYLE
        mapOf(MessageRenderer.PROPERTY_KEY to messageRenderer.name)
    }

    final override val classpath get() = settings.classpath.files

    final override fun checkClasspath() =
        check(classpath.isNotEmpty()) {
            """
                Classpath of the tool is empty: $toolName
                Probably the '${NativeProperties.NATIVE_HOME.name}' project property contains an incorrect path.
                Please change it to the compiler root directory and rerun the build.
            """.trimIndent()
        }

    data class IsolatedClassLoaderCacheKey(val classpath: Set<File>)

    // TODO: can't we use this for other implementations too?
    final override val isolatedClassLoaderCacheKey get() = IsolatedClassLoaderCacheKey(classpath)

    override fun transformArgs(args: List<String>) = listOf(toolName) + args

    final override fun getCustomJvmArgs() = settings.jvmArgs

    final override fun run(args: List<String>) {
        super.run(args + extractArgsFromSettings())
    }

    protected open fun extractArgsFromSettings(): List<String> {
        return settings.konanDataDir?.let { listOf("-Xkonan-data-dir=$it") } ?: emptyList()
    }

    override val compilerArgumentsLogLevel: KotlinCompilerArgumentsLogLevel get() = settings.kotlinCompilerArgumentsLogLevel.get()
}

/** A common ancestor for all runners that run the cinterop tool. */
internal abstract class AbstractKotlinNativeCInteropRunner(
    toolName: String,
    private val settings: Settings,
    metricsReporter: BuildMetricsReporter<GradleBuildTime, GradleBuildPerformanceMetric>,
    objectsFactory: ObjectFactory,
    execOperations: ExecOperations,
) : KotlinNativeToolRunner(toolName, settings, metricsReporter, objectsFactory, execOperations) {

    override val mustRunViaExec get() = true

    override val execEnvironment by lazy {
        val result = mutableMapOf<String, String>()
        result.putAll(super.execEnvironment)
        result["LIBCLANG_DISABLE_CRASH_RECOVERY"] = "1"
        llvmExecutablesPath?.let {
            result["PATH"] = "$it;${System.getenv("PATH")}"
        }
        result
    }

    override fun extractArgsFromSettings(): List<String> {
        return settings.konanDataDir?.let { listOf("-Xkonan-data-dir", it) } ?: emptyList()
    }

    private val llvmExecutablesPath: String? by lazy {
        if (HostManager.host == KonanTarget.MINGW_X64) {
            // TODO: Read it from Platform properties when it is accessible.
            val konanProperties = Properties().apply {
                settings.konanPropertiesFile.inputStream().use(::load)
            }

            konanProperties.resolvablePropertyString("llvmHome.mingw_x64")?.let { toolchainDir ->
                DependencyDirectories.getDependenciesRoot(settings.konanDataDir)
                    .resolve("$toolchainDir/bin")
                    .absolutePath
            }
        } else
            null
    }
}

/** Kotlin/Native C-interop tool runner */
internal fun ObjectFactory.KotlinNativeCInteropRunner(
    settings: KotlinNativeToolRunner.Settings,
    metricsReporter: BuildMetricsReporter<GradleBuildTime, GradleBuildPerformanceMetric>,
): KotlinNativeCInteropRunner = newInstance(settings, metricsReporter)

internal abstract class KotlinNativeCInteropRunner @Inject constructor(
    settings: Settings,
    metricsReporter: BuildMetricsReporter<GradleBuildTime, GradleBuildPerformanceMetric>,
    objectsFactory: ObjectFactory,
    execOperations: ExecOperations,
) : AbstractKotlinNativeCInteropRunner("cinterop", settings, metricsReporter, objectsFactory, execOperations) {

    interface ExecutionContext {
        val runnerSettings: Settings
        val metricsReporter: BuildMetricsReporter<GradleBuildTime, GradleBuildPerformanceMetric>
        fun runWithContext(action: () -> Unit)
    }

    companion object {
        fun ExecutionContext.run(objectsFactory: ObjectFactory, args: List<String>) {
            val runner = objectsFactory.KotlinNativeCInteropRunner(runnerSettings, metricsReporter)
            runWithContext { runner.run(args) }
        }
    }
}

/** Kotlin/Native compiler runner */
internal fun ObjectFactory.KotlinNativeCompilerRunner(
    settings: KotlinNativeCompilerRunner.Settings,
    metricsReporter: BuildMetricsReporter<GradleBuildTime, GradleBuildPerformanceMetric>
): KotlinNativeCompilerRunner = newInstance(settings, metricsReporter)

internal abstract class KotlinNativeCompilerRunner @Inject constructor(
    private val settings: Settings,
    metricsReporter: BuildMetricsReporter<GradleBuildTime, GradleBuildPerformanceMetric>,
    objectsFactory: ObjectFactory,
    execOperations: ExecOperations,
) : KotlinNativeToolRunner("konanc", settings.parent, metricsReporter, objectsFactory, execOperations) {
    class Settings(
        val parent: KotlinNativeToolRunner.Settings,
        val disableKonanDaemon: Boolean,
    ) {
        companion object {
            fun of(konanHome: String, konanDataDir: String?, project: Project) = Settings(
                parent = KotlinNativeToolRunner.Settings.of(konanHome, konanDataDir, project),
                disableKonanDaemon = project.nativeProperties.forceDisableRunningInProcess.get(),
            )
        }
    }

    private val useArgFile get() = settings.disableKonanDaemon

    override val mustRunViaExec get() = settings.disableKonanDaemon

    override fun transformArgs(args: List<String>): List<String> {
        if (!useArgFile) return super.transformArgs(args)

        val argFile = Files.createTempFile(/* prefix = */ "kotlinc-native-args", /* suffix = */ ".lst").toFile().apply { deleteOnExit() }
        argFile.printWriter().use { w ->
            args.forEach { arg ->
                val escapedArg = arg
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                w.println("\"$escapedArg\"")
            }
        }

        return listOf(toolName, "@${argFile.absolutePath}")
    }
}
