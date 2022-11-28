plugins {
    `maven-publish`
    kotlin("multiplatform")
}

group = "org.jetbrains.kotlinx"
val deployVersion = findProperty("kotlinxBrowserDeployVersion") as String?
version = deployVersion ?: "0.1-SNAPSHOT"

kotlin {
    js(IR) {
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                api(kotlinStdlib("js"))
            }
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