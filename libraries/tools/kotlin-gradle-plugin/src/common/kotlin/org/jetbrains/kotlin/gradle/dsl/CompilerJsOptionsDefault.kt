// DO NOT EDIT MANUALLY!
// Generated by org/jetbrains/kotlin/generators/arguments/GenerateGradleOptions.kt
// To regenerate run 'generateGradleOptions' task
@file:Suppress("RemoveRedundantQualifierName", "Deprecation", "DuplicatedCode")

package org.jetbrains.kotlin.gradle.dsl

internal abstract class CompilerJsOptionsDefault @javax.inject.Inject constructor(
    objectFactory: org.gradle.api.model.ObjectFactory
) : org.jetbrains.kotlin.gradle.dsl.CompilerCommonOptionsDefault(objectFactory), org.jetbrains.kotlin.gradle.dsl.CompilerJsOptions {

    override val friendModulesDisabled: org.gradle.api.provider.Property<kotlin.Boolean> =
        objectFactory.property(kotlin.Boolean::class.java).convention(false)

    override val main: org.gradle.api.provider.Property<org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode> =
        objectFactory.property(org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode::class.java).convention(org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode.CALL)

    override val metaInfo: org.gradle.api.provider.Property<kotlin.Boolean> =
        objectFactory.property(kotlin.Boolean::class.java).convention(true)

    override val moduleKind: org.gradle.api.provider.Property<org.jetbrains.kotlin.gradle.dsl.JsModuleKind> =
        objectFactory.property(org.jetbrains.kotlin.gradle.dsl.JsModuleKind::class.java).convention(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_PLAIN)

    override val noStdlib: org.gradle.api.provider.Property<kotlin.Boolean> =
        objectFactory.property(kotlin.Boolean::class.java).convention(true)

    @Deprecated(message = "Use task 'outputFileProperty' to specify location", level = DeprecationLevel.WARNING)
    override val outputFile: org.gradle.api.provider.Property<kotlin.String> =
        objectFactory.property(kotlin.String::class.java)

    override val sourceMap: org.gradle.api.provider.Property<kotlin.Boolean> =
        objectFactory.property(kotlin.Boolean::class.java).convention(false)

    override val sourceMapEmbedSources: org.gradle.api.provider.Property<org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode> =
        objectFactory.property(org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode::class.java)

    override val sourceMapPrefix: org.gradle.api.provider.Property<kotlin.String> =
        objectFactory.property(kotlin.String::class.java)

    override val target: org.gradle.api.provider.Property<kotlin.String> =
        objectFactory.property(kotlin.String::class.java).convention("v5")

    override val typedArrays: org.gradle.api.provider.Property<kotlin.Boolean> =
        objectFactory.property(kotlin.Boolean::class.java).convention(true)

    internal fun fillCompilerArguments(args: org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments) {
        super.toCompilerArguments(args)
        args.friendModulesDisabled = friendModulesDisabled.get()
        args.main = main.get().mode
        args.metaInfo = metaInfo.get()
        args.moduleKind = moduleKind.get().kind
        args.noStdlib = noStdlib.get()
        args.outputFile = outputFile.orNull
        args.sourceMap = sourceMap.get()
        args.sourceMapEmbedSources = sourceMapEmbedSources.orNull?.mode
        args.sourceMapPrefix = sourceMapPrefix.orNull
        args.target = target.get()
        args.typedArrays = typedArrays.get()
    }

    internal fun fillDefaultValues(args: org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments) {
        super.fillDefaultValues(args)
        args.friendModulesDisabled = false
        args.main = org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode.CALL.mode
        args.metaInfo = true
        args.moduleKind = org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_PLAIN.kind
        args.noStdlib = true
        args.outputFile = null
        args.sourceMap = false
        args.sourceMapEmbedSources = null
        args.sourceMapPrefix = null
        args.target = "v5"
        args.typedArrays = true
    }
}
