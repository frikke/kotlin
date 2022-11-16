import plugins.configureDefaultPublishing

plugins {
    `maven-publish`
    kotlin("multiplatform")
}

group = "org.jetbrains.kotlinx"

kotlin {
    js(BOTH) {
        val main by compilations.getting
        main.dependencies {
            api(project(":kotlin-stdlib-js"))
        }
    }
}

suppressYarnAndNpmForAssemble()

tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += listOf(
        "-Xallow-kotlin-package",
        "-opt-in=kotlin.ExperimentalMultiplatform",
        "-opt-in=kotlin.contracts.ExperimentalContracts"
    )
    kotlinOptions.allWarningsAsErrors = true
}

configureDefaultPublishing()