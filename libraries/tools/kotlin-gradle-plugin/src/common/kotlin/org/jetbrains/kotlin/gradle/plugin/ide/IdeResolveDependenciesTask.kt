/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.ide

import com.google.gson.*
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ArtifactResult
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependencyCoordinates
import org.jetbrains.kotlin.gradle.plugin.KotlinProjectSetupAction
import org.jetbrains.kotlin.gradle.plugin.diagnostics.checkers.KmpPartiallyResolvedDependenciesChecker
import org.jetbrains.kotlin.gradle.plugin.diagnostics.checkers.KmpPartiallyResolvedDependenciesCheckerProjectsEvaluated
import org.jetbrains.kotlin.gradle.plugin.diagnostics.checkers.locateOrRegisterPartiallyResolvedDependenciesCheckerTask
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import org.jetbrains.kotlin.gradle.utils.appendLine
import org.jetbrains.kotlin.tooling.core.Extras
import java.io.File
import java.lang.reflect.Type

internal val IdeResolveDependenciesTaskSetupAction = KotlinProjectSetupAction {
    locateOrRegisterIdeResolveDependenciesTask()
}

internal fun Project.locateOrRegisterIdeResolveDependenciesTask(): TaskProvider<IdeResolveDependenciesTask> {
    return locateOrRegisterTask("resolveIdeDependencies") { task ->
        task.description = "Debugging/Diagnosing task that will resolve dependencies for the IDE"
        task.group = "ide"
        task.notCompatibleWithConfigurationCache("Just a debugging util")
        task.kotlinIdeMultiplatformImport.value(project.kotlinIdeMultiplatformImport).finalizeValue()
        // Fixes circular dependency on eager tasks initialization
        task.kotlinIdeMultiplatformImport.get().addDependencyOnResolvers(task)
    }
}

/**
 * Task intended to be use for debugging/diagnosing purposes.
 * This will invoke the [IdeMultiplatformImport] to resolve all dependencies (like the IDE would).
 * Outputs are written as json and protobufs
 */
@DisableCachingByDefault(because = "Used for debugging/diagnostic purpose.")
internal abstract class IdeResolveDependenciesTask : DefaultTask() {
    private val outputDirectory = project.layout.buildDirectory.dir("ide/dependencies")
    private val kotlinExtension = project.kotlinExtension
    private val kotlinIdeMultiplatformImportStatistics = project.kotlinIdeMultiplatformImportStatistics
    private val gsonFileAdapter = FileAdapter(project)

    @get:Internal
    internal abstract val kotlinIdeMultiplatformImport: Property<IdeMultiplatformImport>

    @TaskAction
    fun resolveDependencies() {
        val outputDirectory = outputDirectory.get().asFile
        outputDirectory.deleteRecursively()
        val gson = GsonBuilder().setStrictness(Strictness.LENIENT).setPrettyPrinting()
            .registerTypeHierarchyAdapter(IdeDependencyResolver::class.java, IdeDependencyResolverAdapter)
            .registerTypeHierarchyAdapter(Extras::class.java, ExtrasAdapter)
            .registerTypeHierarchyAdapter(IdeaKotlinDependencyCoordinates::class.java, IdeaKotlinDependencyCoordinatesAdapter)
            .registerTypeHierarchyAdapter(ArtifactResult::class.java, ToStringAdapter)
            .registerTypeAdapter(File::class.java, gsonFileAdapter)
            .create()

        kotlinExtension.sourceSets.forEach { sourceSet ->
            val dependencies = kotlinIdeMultiplatformImport.get().resolveDependencies(sourceSet)
            val jsonOutput = outputDirectory.resolve("json/${sourceSet.name}.json")
            jsonOutput.parentFile.mkdirs()
            jsonOutput.writeText(gson.toJson(dependencies))

            kotlinIdeMultiplatformImport.get().serialize(dependencies).forEachIndexed { index, proto ->
                val protoOutput = outputDirectory.resolve("proto/${sourceSet.name}/$index.bin")
                protoOutput.parentFile.mkdirs()
                protoOutput.writeBytes(proto)
            }
        }

        kotlinIdeMultiplatformImportStatistics.let { statistics ->
            val timeStatisticsFile = outputDirectory.resolve("times.txt")
            timeStatisticsFile.writeText(buildString {
                statistics.getExecutionTimes().forEach { (clazz, time) ->
                    appendLine("${clazz.name} $time.ms")
                }
            })
        }
    }

    private object IdeDependencyResolverAdapter : JsonSerializer<IdeDependencyResolver> {
        override fun serialize(src: IdeDependencyResolver, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.javaClass.name)
        }
    }

    private object IdeaKotlinDependencyCoordinatesAdapter : JsonSerializer<IdeaKotlinDependencyCoordinates> {
        override fun serialize(src: IdeaKotlinDependencyCoordinates, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src.toString())
        }
    }

    private object ExtrasAdapter : JsonSerializer<Extras> {
        override fun serialize(src: Extras, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonObject().apply {
                src.entries.forEach { entry ->
                    val valueElement = runCatching { context.serialize(entry.value) }.getOrElse { JsonPrimitive(entry.value.toString()) }
                    add(entry.key.stableString, valueElement)
                }
            }
        }
    }

    private class FileAdapter(private val project: Project) : JsonSerializer<File> {
        override fun serialize(src: File, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return if (src.startsWith(project.projectDir)) {
                JsonPrimitive(src.relativeTo(project.projectDir).path)
            } else if (src.startsWith(project.rootDir)) {
                JsonPrimitive(src.relativeTo(project.rootDir).path)
            } else {
                JsonPrimitive(src.path)
            }
        }
    }

    object ToStringAdapter : JsonSerializer<Any> {
        override fun serialize(src: Any, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src.toString())
        }
    }
}