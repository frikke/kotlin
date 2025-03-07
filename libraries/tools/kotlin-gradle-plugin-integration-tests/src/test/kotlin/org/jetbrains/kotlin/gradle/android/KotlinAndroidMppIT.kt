/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.android

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnostics
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.testbase.TestVersions.AGP.AGP_70
import org.jetbrains.kotlin.gradle.testbase.TestVersions.AGP.AGP_71
import org.jetbrains.kotlin.gradle.testbase.TestVersions.Gradle.G_7_1
import org.jetbrains.kotlin.gradle.testbase.TestVersions.Gradle.G_7_2
import org.jetbrains.kotlin.gradle.tooling.BuildKotlinToolingMetadataTask
import org.jetbrains.kotlin.gradle.util.AGPVersion
import org.jetbrains.kotlin.gradle.util.replaceText
import org.jetbrains.kotlin.gradle.util.testResolveAllConfigurations
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.appendText
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.streams.toList
import kotlin.test.*

@DisplayName("kotlin-android with mpp")
@AndroidGradlePluginTests
@GradleTestVersions(minVersion = G_7_1)
@AndroidTestVersions(minVersion = AGP_70)
class KotlinAndroidMppIT : KGPBaseTest() {
    @DisplayName("KT-50736: whenEvaluated waits for AGP being applied later")
    @GradleAndroidTest
    fun testAfterEvaluateOrdering(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "AndroidProject",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            subProject("Lib").buildGradle.writeText(
                //language=Gradle
                """
                buildscript {
                    repositories {
                        mavenLocal()
                        google()
                        gradlePluginPortal()
                    }
                    dependencies {
                        classpath "com.android.tools.build:gradle:${'$'}android_tools_version"
                        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}kotlin_version"
                    }
                }
        
                plugins {
                    id 'org.jetbrains.kotlin.multiplatform'
                }
        
                class MyAction implements kotlin.jvm.functions.Function1<Project, Void> {
                    Void invoke (Project p) {
                        println("compilations: " + p.kotlin.targets.getByName("android").compilations.names)
                    }
                }
        
                org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginKt.whenEvaluated(project, new MyAction ())
        
                apply plugin : "android-library"
        
                android {
                    compileSdkVersion 22
                    namespace 'org.jetbrains.kotlin.gradle.test.android.libalfa'
                }
        
                kotlin { android("android") { } }
                """.trimIndent()
            )

            build("help") {
                val reportedCompilations = output.lines()
                    .single { it.contains("compilations: ") }
                    .substringAfter("compilations: ")
                    .removeSurrounding("[", "]")
                    .split(", ")
                    .toSet()
                assertEquals(
                    setOf("debug", "debugAndroidTest", "debugUnitTest", "release", "releaseUnitTest"),
                    reportedCompilations
                )
            }
        }
    }

    @DisplayName("KotlinToolingMetadataArtifact is bundled into apk")
    @GradleAndroidTest
    fun testKotlinToolingMetadataBundle(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "kotlinToolingMetadataAndroid",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            build("assembleDebug") {
                assertTasksAreNotInTaskGraph(":${BuildKotlinToolingMetadataTask.defaultTaskName}")

                val debugApk = projectPath.resolve("build/outputs/apk/debug/project-debug.apk")
                assertFileExists(debugApk)
                ZipFile(debugApk.toFile()).use { zip ->
                    assertNull(zip.getEntry("kotlin-tooling-metadata.json"), "Expected metadata *not* being packaged into debug apk")
                }
            }

            build("assembleRelease") {
                assertTasksExecuted(":${BuildKotlinToolingMetadataTask.defaultTaskName}")
                val releaseApk = projectPath.resolve("build/outputs/apk/release/project-release-unsigned.apk")

                assertFileExists(releaseApk)
                ZipFile(releaseApk.toFile()).use { zip ->
                    assertNotNull(zip.getEntry("kotlin-tooling-metadata.json"), "Expected metadata being packaged into release apk")
                }
            }
        }
    }

    @AndroidTestVersions(minVersion = AGP_71)
    @GradleTestVersions(minVersion = G_7_2)
    @DisplayName("mpp source sets are registered in AGP")
    @GradleAndroidTest
    fun testAndroidMppSourceSets(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "new-mpp-android-source-sets",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            // AbstractReportTask#generate() task action was removed in Gradle 6.8+,
            // that SourceSetTask is using: https://github.com/gradle/gradle/commit/4dac91ab87ea33ee8689d2a62b691b119198e7c7
            // leading to the issue that ":sourceSets" task is always in 'UP-TO-DATE' state.
            // Skipping this check until the test will start using AGP 7.0-alpha03+
            // AGP 4.x is not compatible with Gradle 7.0, so just skip when the Gradle version is lower than 7.0
            if (gradleVersion >= GradleVersion.version("7.0")) {
                build("sourceSets") {
                    fun assertOutputContainsOsIndependent(expectedString: String) {
                        assertOutputContains(expectedString.replace("/", File.separator))
                    }
                    assertOutputContainsOsIndependent("Android resources: [lib/src/main/res, lib/src/androidMain/res]")
                    assertOutputContainsOsIndependent("Assets: [lib/src/main/assets, lib/src/androidMain/assets]")
                    assertOutputContainsOsIndependent("AIDL sources: [lib/src/main/aidl, lib/src/androidMain/aidl]")
                    assertOutputContainsOsIndependent("RenderScript sources: [lib/src/main/rs, lib/src/androidMain/rs]")
                    assertOutputContainsOsIndependent("JNI sources: [lib/src/main/jni, lib/src/androidMain/jni]")
                    assertOutputContainsOsIndependent("JNI libraries: [lib/src/main/jniLibs, lib/src/androidMain/jniLibs]")
                    assertOutputContainsOsIndependent("Java-style resources: [lib/src/main/resources, lib/src/androidMain/resources]")

                    assertOutputContainsOsIndependent("Android resources: [lib/src/androidTestDebug/res, lib/src/androidInstrumentedTestDebug/res]")
                    assertOutputContainsOsIndependent("Assets: [lib/src/androidTestDebug/assets, lib/src/androidInstrumentedTestDebug/assets]")
                    assertOutputContainsOsIndependent("AIDL sources: [lib/src/androidTestDebug/aidl, lib/src/androidInstrumentedTestDebug/aidl]")
                    assertOutputContainsOsIndependent("RenderScript sources: [lib/src/androidTestDebug/rs, lib/src/androidInstrumentedTestDebug/rs]")
                    assertOutputContainsOsIndependent("JNI sources: [lib/src/androidTestDebug/jni, lib/src/androidInstrumentedTestDebug/jni]")
                    assertOutputContainsOsIndependent("JNI libraries: [lib/src/androidTestDebug/jniLibs, lib/src/androidInstrumentedTestDebug/jniLibs]")
                    assertOutputContainsOsIndependent("Java-style resources: [lib/src/androidTestDebug/resources, lib/src/androidInstrumentedTestDebug/resources]")

                    assertOutputContainsOsIndependent("Java-style resources: [lib/betaSrc/paidBeta/resources, lib/src/androidPaidBeta/resources]")
                    assertOutputContainsOsIndependent("Java-style resources: [lib/betaSrc/paidBetaDebug/resources, lib/src/androidPaidBetaDebug/resources]")
                    assertOutputContainsOsIndependent("Java-style resources: [lib/betaSrc/paidBetaRelease/resources, lib/src/androidPaidBetaRelease/resources]")

                    assertOutputContainsOsIndependent("Java-style resources: [lib/betaSrc/freeBeta/resources, lib/src/androidFreeBeta/resources]")
                    assertOutputContainsOsIndependent("Java-style resources: [lib/betaSrc/freeBetaDebug/resources, lib/src/androidFreeBetaDebug/resources]")
                    assertOutputContainsOsIndependent("Java-style resources: [lib/betaSrc/freeBetaRelease/resources, lib/src/androidFreeBetaRelease/resources]")
                }
            }

            buildAndFail("testFreeBetaDebug") {
                assertOutputContains("CommonTest > fail FAILED")
                assertOutputContains("TestKotlin > fail FAILED")
                assertOutputContains("AndroidTestKotlin > fail FAILED")
                assertOutputContains("TestJava > fail FAILED")
            }

            build("assemble")

            buildAndFail("connectedAndroidTest") {
                assertOutputContains("No connected devices!")
            }
        }
    }

    @AndroidTestVersions(minVersion = AGP_70)
    @GradleTestVersions(minVersion = G_7_2)
    @DisplayName("android mpp lib flavors publication can be configured")
    @GradleAndroidTest
    fun testMppAndroidLibFlavorsPublication(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        val androidSourcesElementsAttributes = arrayOf(
            "org.gradle.category" to "documentation",
            "org.gradle.dependency.bundling" to "external",
            "org.gradle.docstype" to "sources",
            "org.gradle.libraryelements" to "jar",
            "org.gradle.usage" to "java-runtime",
            "org.jetbrains.kotlin.platform.type" to "androidJvm",

            // user-specific attributes that added manually in build script
            "com.example.compilation" to "release",
            "com.example.target" to "androidLib",
        )

        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            val groupDir = subProject("lib").projectPath.resolve("build/repo/com/example")
            build("publish") {
                assertDirectoryExists(groupDir.resolve("lib-jvmlib"))
                assertDirectoryExists(groupDir.resolve("lib-jslib"))
                assertDirectoryExists(groupDir.resolve("lib-androidlib"))
                assertDirectoryExists(groupDir.resolve("lib-androidlib-debug"))
            }
            groupDir.deleteRecursively()

            // Choose a single variant to publish, check that it's there:
            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """
                    
                kotlin.android('androidLib').publishLibraryVariants = ['release']
                """.trimIndent()
            )
            build("publish") {
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.aar"))
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0-sources.jar"))
                assertGradleVariant(
                    groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.module"),
                    "releaseSourcesElements-published"
                ) {
                    assertAttributesEquals(*androidSourcesElementsAttributes)
                }
                assertFileInProjectNotExists("$groupDir/lib-androidlib-debug")
            }
            groupDir.deleteRecursively()

            // Enable publishing for all Android variants:
            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """

                kotlin.android('androidLib') { publishAllLibraryVariants() }
                """.trimIndent()
            )
            build("publish") {
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.aar"))
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0-sources.jar"))
                assertGradleVariant(
                    groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.module"),
                    "releaseSourcesElements-published"
                ) {
                    assertAttributesEquals(*androidSourcesElementsAttributes)
                }
                assertFileExists(groupDir.resolve("lib-androidlib-debug/1.0/lib-androidlib-debug-1.0.aar"))
                assertFileExists(groupDir.resolve("lib-androidlib-debug/1.0/lib-androidlib-debug-1.0-sources.jar"))
                assertGradleVariant(
                    groupDir.resolve("lib-androidlib-debug/1.0/lib-androidlib-debug-1.0.module"),
                    "debugSourcesElements-published"
                ) {
                    assertAttributesEquals(
                        *androidSourcesElementsAttributes,
                        "com.example.compilation" to "debug",
                        "com.android.build.api.attributes.BuildTypeAttr" to "debug"
                    )
                }
            }
            groupDir.deleteRecursively()

            // Then group the variants by flavor and check that only one publication is created:
            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """

                kotlin.android('androidLib').publishLibraryVariantsGroupedByFlavor = true
                """.trimIndent()
            )
            build("publish") {
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.aar"))
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0-sources.jar"))
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0-debug.aar"))
                assertFileExists(groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0-debug-sources.jar"))
                assertGradleVariant(
                    groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.module"),
                    "releaseSourcesElements-published"
                ) {
                    assertAttributesEquals(*androidSourcesElementsAttributes)
                }
                assertGradleVariant(
                    groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.module"),
                    "debugSourcesElements-published"
                ) {
                    assertAttributesEquals(
                        *androidSourcesElementsAttributes,
                        "com.example.compilation" to "debug",
                        "com.android.build.api.attributes.BuildTypeAttr" to "debug"
                    )
                }
            }
            groupDir.deleteRecursively()

            // Add one flavor dimension with two flavors, check that the flavors produce grouped publications:
            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """

                android { flavorDimensions('foo'); productFlavors { fooBar { dimension 'foo' }; fooBaz { dimension 'foo' } } }                    
                """.trimIndent()
            )
            build("publish") {
                listOf("fooBar", "fooBaz").forEach { flavorName ->
                    val flavor = flavorName.lowercase()

                    val flavorAttributes = if (AGPVersion.fromString(agpVersion) > AGPVersion.v7_0_0) {
                        arrayOf(
                            "foo" to flavorName,
                            "com.android.build.api.attributes.ProductFlavor:foo" to flavorName
                        )
                    } else {
                        arrayOf(
                            "foo" to flavorName
                        )
                    }

                    assertFileExists(groupDir.resolve("lib-androidlib-$flavor/1.0/lib-androidlib-$flavor-1.0.aar"))
                    assertFileExists(groupDir.resolve("lib-androidlib-$flavor/1.0/lib-androidlib-$flavor-1.0-sources.jar"))
                    assertFileExists(groupDir.resolve("lib-androidlib-$flavor/1.0/lib-androidlib-$flavor-1.0-debug.aar"))
                    assertFileExists(groupDir.resolve("lib-androidlib-$flavor/1.0/lib-androidlib-$flavor-1.0-debug-sources.jar"))
                    assertGradleVariant(
                        groupDir.resolve("lib-androidlib-$flavor/1.0/lib-androidlib-$flavor-1.0.module"),
                        "${flavorName}ReleaseSourcesElements-published"
                    ) {
                        assertAttributesEquals(
                            *androidSourcesElementsAttributes,
                            *flavorAttributes,
                            "com.example.compilation" to "${flavorName}Release",
                        )
                    }
                    assertGradleVariant(
                        groupDir.resolve("lib-androidlib-$flavor/1.0/lib-androidlib-$flavor-1.0.module"),
                        "${flavorName}DebugSourcesElements-published"
                    ) {
                        assertAttributesEquals(
                            *androidSourcesElementsAttributes,
                            *flavorAttributes,
                            "com.example.compilation" to "${flavorName}Debug",
                            "com.android.build.api.attributes.BuildTypeAttr" to "debug"
                        )
                    }
                }
            }
            groupDir.deleteRecursively()

            // Disable the grouping and check that all the variants are published under separate artifactIds:
            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """
                    
                kotlin.android('androidLib') { publishLibraryVariantsGroupedByFlavor = false }    
                """.trimIndent()
            )
            build("publish") {
                listOf("fooBar", "fooBaz").forEach { flavorName ->
                    val flavor = flavorName.lowercase()

                    val flavorAttributes = if (AGPVersion.fromString(agpVersion) > AGPVersion.v7_0_0) {
                        arrayOf(
                            "foo" to flavorName,
                            "com.android.build.api.attributes.ProductFlavor:foo" to flavorName
                        )
                    } else {
                        arrayOf(
                            "foo" to flavorName
                        )
                    }

                    listOf("-debug", "").forEach { buildType ->
                        assertFileExists(groupDir.resolve("lib-androidlib-$flavor$buildType/1.0/lib-androidlib-$flavor$buildType-1.0.aar"))
                        assertFileExists(groupDir.resolve("lib-androidlib-$flavor$buildType/1.0/lib-androidlib-$flavor$buildType-1.0-sources.jar"))
                    }

                    assertGradleVariant(
                        groupDir.resolve("lib-androidlib-$flavor/1.0/lib-androidlib-$flavor-1.0.module"),
                        "${flavorName}ReleaseSourcesElements-published"
                    ) {
                        assertAttributesEquals(
                            *androidSourcesElementsAttributes,
                            *flavorAttributes,
                            "com.example.compilation" to "${flavorName}Release",
                        )
                    }

                    assertGradleVariant(
                        groupDir.resolve("lib-androidlib-$flavor-debug/1.0/lib-androidlib-$flavor-debug-1.0.module"),
                        "${flavorName}DebugSourcesElements-published"
                    ) {
                        assertAttributesEquals(
                            *androidSourcesElementsAttributes,
                            *flavorAttributes,
                            "com.example.compilation" to "${flavorName}Debug",
                            "com.android.build.api.attributes.BuildTypeAttr" to "debug"
                        )
                    }
                }
            }
        }
    }

    @DisplayName("Sources publication can be disabled")
    @GradleAndroidTest
    fun testDisableSourcesPublication(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk
    ) {
        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """
                    
                    kotlin.android('androidLib') {
                        withSourcesJar(false)
                        publishLibraryVariants = ['release']
                    }
                """.trimIndent()
            )

            val groupDir = subProject("lib").projectPath.resolve("build/repo/com/example")
            build("publish") {
                val sourcesJarFile = groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0-sources.jar").toFile()
                if (sourcesJarFile.exists()) fail("Release sources jar should not be published")

                val gradleMetadataFileContent = groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.module").readText()
                if (gradleMetadataFileContent.contains("releaseSourcesElements-published")) {
                    fail("'releaseSourcesElements-published' variant should not be published")
                }
            }
        }
    }

    @DisplayName("android mpp lib dependencies are properly rewritten")
    @GradleAndroidTest
    fun testMppAndroidLibDependenciesRewriting(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            // Convert the 'app' project to a library, publish two flavors without metadata,
            // check that the dependencies in the POMs are correctly rewritten:
            val appGroupDir = subProject("app").projectPath.resolve("build/repo/com/example")

            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """
                
                android { flavorDimensions('foo'); productFlavors { fooBar { dimension 'foo' }; fooBaz { dimension 'foo' } } }
                """.trimIndent()
            )

            subProject("app").buildGradle.modify {
                it.replace("com.android.application", "com.android.library")
                    .replace("applicationId", "//") +
                        //language=Gradle
                        """
    
                        apply plugin: 'maven-publish'
                        publishing { repositories { maven { url = uri("${'$'}buildDir/repo") } } }
                        kotlin.android('androidApp') { publishAllLibraryVariants() }
                        android { flavorDimensions('foo'); productFlavors { fooBar { dimension 'foo' }; fooBaz { dimension 'foo' } } }
                        """.trimIndent()
            }
            build("publish") {
                listOf("foobar", "foobaz").forEach { flavor ->
                    listOf("-debug", "").forEach { buildType ->
                        assertFileExists(appGroupDir.resolve("app-androidapp-$flavor$buildType/1.0/app-androidapp-$flavor$buildType-1.0.aar"))
                        assertFileExists(appGroupDir.resolve("app-androidapp-$flavor$buildType/1.0/app-androidapp-$flavor$buildType-1.0-sources.jar"))
                        val pomText =
                            appGroupDir.resolve("app-androidapp-$flavor$buildType/1.0/app-androidapp-$flavor$buildType-1.0.pom").readText()
                                .replace("\\s+".toRegex(), "")
                        assertContains(
                            pomText,
                            "<artifactId>lib-androidlib-$flavor$buildType</artifactId><version>1.0</version><scope>runtime</scope>"
                        )
                    }
                }
            }
            appGroupDir.deleteRecursively()

            // Also check that api and runtimeOnly MPP dependencies get correctly published with the appropriate scope, KT-29476:
            subProject("app").buildGradle.modify {
                it.replace("implementation project(':lib')", "api project(':lib')") +
                        //language=Gradle
                        """

                        kotlin.sourceSets.commonMain.dependencies {
                            runtimeOnly(kotlin('reflect'))
                        }
                        """.trimIndent()
            }
            build("publish") {
                listOf("foobar", "foobaz").forEach { flavor ->
                    listOf("-debug", "").forEach { buildType ->
                        val pomText =
                            appGroupDir.resolve("app-androidapp-$flavor$buildType/1.0/app-androidapp-$flavor$buildType-1.0.pom").readText()
                                .replace("\\s+".toRegex(), "")
                        assertContains(
                            pomText,
                            "<artifactId>lib-androidlib-$flavor$buildType</artifactId><version>1.0</version><scope>compile</scope>"
                        )
                        assertContains(
                            pomText,
                            "<artifactId>kotlin-reflect</artifactId><version>${buildOptions.kotlinVersion}</version><scope>runtime</scope>"
                        )
                    }
                }
            }
        }
    }

    @DisplayName("android app can depend on mpp lib")
    @GradleAndroidTest
    fun testAndroidWithNewMppApp(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            build("assemble", "compileDebugUnitTestJavaWithJavac", "printCompilerPluginOptions") {
                // KT-30784
                assertOutputDoesNotContain("API 'variant.getPackageLibrary()' is obsolete and has been replaced")

                assertOutputContains("KT-29964 OK") // Output from lib/build.gradle

                assertTasksExecuted(
                    ":lib:compileDebugKotlinAndroidLib",
                    ":lib:compileReleaseKotlinAndroidLib",
                    ":lib:compileKotlinJvmLib",
                    ":lib:compileKotlinJsLib",
                    ":lib:compileCommonMainKotlinMetadata",
                    ":app:compileDebugKotlinAndroidApp",
                    ":app:compileReleaseKotlinAndroidApp",
                    ":app:compileKotlinJvmApp",
                    ":app:compileKotlinJsApp",
                    ":app:compileCommonMainKotlinMetadata",
                    ":lib:compileDebugUnitTestJavaWithJavac",
                    ":app:compileDebugUnitTestJavaWithJavac"
                )

                listOf("debug", "release").forEach { variant ->
                    assertFileInProjectExists("lib/build/tmp/kotlin-classes/$variant/com/example/lib/ExpectedLibClass.class")
                    assertFileInProjectExists("lib/build/tmp/kotlin-classes/$variant/com/example/lib/CommonLibClass.class")
                    assertFileInProjectExists("lib/build/tmp/kotlin-classes/$variant/com/example/lib/AndroidLibClass.class")

                    assertFileInProjectExists("app/build/tmp/kotlin-classes/$variant/com/example/app/AKt.class")
                    assertFileInProjectExists("app/build/tmp/kotlin-classes/$variant/com/example/app/KtUsageKt.class")
                }
            }
        }
    }

    @DisplayName("KT-29343: mpp source set dependencies are propagated to android tests")
    @GradleAndroidTest
    fun testAndroidMppProductionDependenciesInTests(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            // Test the fix for KT-29343
            subProject("lib").buildGradle.appendText(
                //language=Gradle
                """

                kotlin.sourceSets {
                    commonMain {
                        dependencies {
                            implementation kotlin("stdlib-common")
                        }
                    }
                    androidLibDebug {
                        dependencies {
                            implementation kotlin("reflect")
                        }
                    }
                    androidLibRelease {
                        dependencies {
                            implementation kotlin("test-junit")
                        }
                    }
                }
                """.trimIndent()
            )

            val kotlinVersion = buildOptions.kotlinVersion
            testResolveAllConfigurations("lib") { _, buildResult ->
                // androidLibDebug:
                buildResult.assertOutputContains(">> :lib:debugCompileClasspath --> kotlin-reflect-$kotlinVersion.jar")
                buildResult.assertOutputDoesNotContain(">> :lib:releaseCompileClasspath --> kotlin-reflect-$kotlinVersion.jar")
                buildResult.assertOutputContains(">> :lib:debugAndroidTestCompileClasspath --> kotlin-reflect-$kotlinVersion.jar")
                buildResult.assertOutputContains(">> :lib:debugUnitTestCompileClasspath --> kotlin-reflect-$kotlinVersion.jar")
                buildResult.assertOutputDoesNotContain(">> :lib:releaseUnitTestCompileClasspath --> kotlin-reflect-$kotlinVersion.jar")

                // androidLibRelease:
                buildResult.assertOutputDoesNotContain(">> :lib:debugCompileClasspath --> kotlin-test-junit-$kotlinVersion.jar")
                buildResult.assertOutputContains(">> :lib:releaseCompileClasspath --> kotlin-test-junit-$kotlinVersion.jar")
                buildResult.assertOutputDoesNotContain(">> :lib:debugAndroidTestCompileClasspath --> kotlin-test-junit-$kotlinVersion.jar")
                buildResult.assertOutputDoesNotContain(">> :lib:debugUnitTestCompileClasspath --> kotlin-test-junit-$kotlinVersion.jar")
                buildResult.assertOutputContains(">> :lib:releaseUnitTestCompileClasspath --> kotlin-test-junit-$kotlinVersion.jar")
            }
        }
    }

    @AndroidTestVersions(minVersion = AGP_70)
    @GradleTestVersions(minVersion = G_7_2)
    @DisplayName("KT-27714: custom attributes are copied to android compilation configurations")
    @GradleAndroidTest
    fun testCustomAttributesInAndroidTargets(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            val libBuildScript = subProject("lib").buildGradle
            val appBuildScript = subProject("app").buildGradle

            // Enable publishing for all Android variants:
            libBuildScript.appendText(
                //language=Gradle
                """

                kotlin.android('androidLib') { publishAllLibraryVariants() }
                """.trimIndent()
            )

            val groupDir = subProject("lib").projectPath.resolve("build/repo/com/example")

            build("publish") {
                // Also check that custom user-specified attributes are written in all Android modules metadata:
                assertFileContains(
                    groupDir.resolve("lib-androidlib/1.0/lib-androidlib-1.0.module"),
                    "\"com.example.target\": \"androidLib\"",
                    "\"com.example.compilation\": \"release\""
                )
                assertFileContains(
                    groupDir.resolve("lib-androidlib-debug/1.0/lib-androidlib-debug-1.0.module"),
                    "\"com.example.target\": \"androidLib\"",
                    "\"com.example.compilation\": \"debug\""
                )
            }
            groupDir.deleteRecursively()

            // Check that the consumer side uses custom attributes specified in the target and compilations:
            val appBuildScriptBackup = appBuildScript.readText()

            libBuildScript.appendText(
                //language=Gradle
                """

                kotlin.targets.all { 
                    attributes.attribute(
                        Attribute.of("com.example.target", String),
                        targetName
                    )
                }
                """.trimIndent()
            )
            appBuildScript.appendText(
                //language=Gradle
                """

                kotlin.targets.androidApp.attributes.attribute(
                    Attribute.of("com.example.target", String),
                    "notAndroidLib"
                )
                """.trimIndent()
            )

            buildAndFail(":app:compileDebugKotlinAndroidApp") {
                // dependency resolution should fail
                assertOutputContainsAny(
                    "Required com.example.target 'notAndroidLib'",
                    "attribute 'com.example.target' with value 'notAndroidLib'",
                )
            }

            libBuildScript.writeText(
                appBuildScriptBackup +
                        //language=Gradle
                        """
                            
                        android {
                            namespace 'app.example.com.lib'
                        }
                        kotlin.targets.all {
                            compilations.all {
                                attributes.attribute(
                                    Attribute.of("com.example.compilation", String),
                                    targetName + compilationName.capitalize()
                                )
                            }
                        }
                        """.trimIndent()
            )
            appBuildScript.writeText(
                appBuildScriptBackup +
                        //language=Gradle
                        """
                            
                        android {
                            namespace 'app.example.com.app_sample'
                        }
                        kotlin.targets.androidApp.compilations.all {
                            attributes.attribute(
                                Attribute.of("com.example.compilation", String),
                                "notDebug"
                            )
                        }
                        """.trimIndent()
            )

            buildAndFail(":app:compileDebugKotlinAndroidApp") {
                assertOutputContainsAny(
                    "Required com.example.compilation 'notDebug'",
                    "attribute 'com.example.compilation' with value 'notDebug'",
                )
            }
        }
    }

    @DisplayName("KT-27170: android lint works with dependency on non-android mpp project")
    @GradleAndroidTest
    fun testLintInAndroidProjectsDependingOnMppWithoutAndroid(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        disabledOnWindowsWhenAgpVersionIsLowerThan(agpVersion, "7.4.0", "Lint leaves opened file descriptors")
        project(
            "AndroidProject",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            includeOtherProjectAsSubmodule(pathPrefix = "new-mpp-lib-and-app", otherProjectName = "sample-lib")
            subProject("Lib").buildGradle.appendText(
                //language=Gradle
                """

                dependencies { implementation(project(':sample-lib')) }
                """.trimIndent()
            )
            val lintTask = ":Lib:lintFlavor1Debug"
            build(lintTask) {
                assertTasksExecuted(lintTask) // Check that the lint task ran successfully, KT-27170
            }
        }
    }

    @DisplayName("MPP allTests task depending on Android unit tests")
    @GradleAndroidTest
    fun testMppAllTests(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            build(":lib:allTests", "--dry-run") {
                assertOutputContains(":lib:testDebugUnitTest SKIPPED")
                assertOutputContains(":lib:testReleaseUnitTest SKIPPED")
            }
        }
    }

    /**
     * Starting from AGP version 7.1.0-alpha13, a new attribute com.android.build.api.attributes.AgpVersionAttr was added.
     * This attribute is *not intended* to be published.
     */
    @DisplayName("KT-49798: com.android.build.api.attributes.AgpVersionAttr is not published")
    @GradleAndroidTest
    @AndroidTestVersions(minVersion = TestVersions.AGP.AGP_71)
    @GradleTestVersions(minVersion = TestVersions.Gradle.G_7_2) // due AGP version limit ^
    fun testKT49798AgpVersionAttrNotPublished(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "new-mpp-android",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            build("publish") {
                val libProject = subProject("lib")
                val debugPublicationDirectory = libProject.projectPath.resolve("build/repo/com/example/lib-androidlib-debug")
                val releasePublicationDirectory = libProject.projectPath.resolve("build/repo/com/example/lib-androidlib")

                listOf(debugPublicationDirectory, releasePublicationDirectory).forEach { publicationDirectory ->
                    assertDirectoryExists(publicationDirectory)
                    val moduleFiles = Files.walk(publicationDirectory).use { it.filter { file -> file.extension == "module" }.toList() }
                    assertTrue(moduleFiles.isNotEmpty(), "Missing .module file in $publicationDirectory")
                    assertTrue(moduleFiles.size == 1, "Multiple .module files in $publicationDirectory: $moduleFiles")

                    val moduleFile = moduleFiles.single()
                    val moduleFileText = moduleFile.readText()
                    assertTrue("AgpVersionAttr" !in moduleFileText, ".module file $moduleFile leaks AgpVersionAttr")
                }
            }
        }
    }

    @DisplayName("produced artifacts are consumable by projects with various AGP versions")
    @GradleAndroidTest
    @AndroidTestVersions(minVersion = TestVersions.AGP.AGP_71)
    @GradleTestVersions(minVersion = TestVersions.Gradle.G_7_2) // due AGP version limit ^
    fun testAndroidMultiplatformPublicationAGPCompatibility(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
        @TempDir tempDir: Path
    ) {
        project(
            "new-mpp-android-agp-compatibility",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location,
            localRepoDir = tempDir
        ) {
            /* Publish a producer library with the current version of AGP */
            build(":producer:publishAllPublicationsToBuildDirRepository") {
                /* Check expected publication layout */
                assertDirectoryExists(tempDir.resolve("com/example/producer-android"))
                assertDirectoryExists(tempDir.resolve("com/example/producer-android-debug"))
                assertDirectoryExists(tempDir.resolve("com/example/producer-jvm"))
            }
        }

        val checkedConsumerAGPVersions = AGPVersion.testedVersions
            .filter { version -> version >= AGPVersion.fromString(TestVersions.AGP.AGP_42) }
            .filter { version -> version < AGPVersion.fromString(TestVersions.AGP.MAX_SUPPORTED) }
            .map { it.toString() }

        checkedConsumerAGPVersions.forEach { consumerAgpVersion ->
            val agpTestVersion = TestVersions.AgpCompatibilityMatrix.values().find { it.version == consumerAgpVersion }
                ?: fail("AGP version $consumerAgpVersion is not defined in TestVersions.AGP!")
            val consumerGradleVersion = when {
                gradleVersion < agpTestVersion.minSupportedGradleVersion -> agpTestVersion.minSupportedGradleVersion
                gradleVersion > agpTestVersion.maxSupportedGradleVersion -> agpTestVersion.maxSupportedGradleVersion
                else -> gradleVersion
            }
            println("Testing compatibility for AGP consumer version $consumerAgpVersion on Gradle ${consumerGradleVersion.version} (Producer: $agpVersion)")
            project(
                "new-mpp-android-agp-compatibility",
                consumerGradleVersion,
                buildOptions = defaultBuildOptions.copy(androidVersion = consumerAgpVersion)
                    .suppressDeprecationWarningsOn(
                        "AGP relies on FileTrees for ignoring empty directories when using @SkipWhenEmpty which has been deprecated."
                    ) { options ->
                        consumerGradleVersion >= GradleVersion.version(TestVersions.Gradle.G_7_4) && AGPVersion.fromString(options.safeAndroidVersion) < AGPVersion.v7_1_0
                    },
                buildJdk = jdkVersion.location,
                localRepoDir = tempDir
            ) {
                /*
                Project: multiplatformAndroidConsumer is a mpp project with jvm and android targets.
                This project depends on the previous publication as 'commonMainImplementation' dependency
                */
                build(":multiplatformAndroidConsumer:assemble")

                /*
                Project: plainAndroidConsumer only uses the 'kotlin("android")' plugin
                This project depends on the previous publication as 'implementation' dependency
                 */
                build(":plainAndroidConsumer:assemble")
            }
            println("Successfully tested compatibility for AGP consumer version $consumerAgpVersion on Gradle ${consumerGradleVersion.version} (Producer: $agpVersion)")
        }
    }

    @DisplayName("KT-49877, KT-35916: associate compilation dependencies are passed correctly to android test compilations")
    @GradleAndroidTest
    @AndroidTestVersions(minVersion = TestVersions.AGP.AGP_71)
    @GradleTestVersions(minVersion = TestVersions.Gradle.G_7_2) // due AGP version limit ^
    fun testAssociateCompilationDependenciesArePassedToAndroidTestCompilations(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) {
        project(
            "kt-49877",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            build("allTests") {
                assertTasksExecuted(
                    ":compileDebugKotlinAndroid",
                    ":compileReleaseKotlinAndroid",
                    ":compileDebugUnitTestKotlinAndroid",
                    ":compileReleaseUnitTestKotlinAndroid",
                    ":testDebugUnitTest",
                    ":testReleaseUnitTest",
                )
            }

            // instrumented tests don't work without a device, so we only compile them
            build("packageDebugAndroidTest") {
                assertTasksExecuted(
                    ":compileDebugAndroidTestKotlinAndroid",
                )
            }
        }
    }

    @GradleAndroidTest
    fun mppAndroidRenameDiagnosticReportedOnKts(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) = testAndroidRenameReported(gradleVersion, agpVersion, jdkVersion, "mppAndroidRenameKts")

    @GradleAndroidTest
    fun mppAndroidRenameDiagnosticReportedOnGroovy(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
    ) = testAndroidRenameReported(gradleVersion, agpVersion, jdkVersion, "mppAndroidRenameGroovy")

    private fun testAndroidRenameReported(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk,
        projectName: String
    ) {
        project(
            projectName,
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            build("tasks") {
                val warnings = output.lines().filter { it.startsWith("w:") }.toSet()
                assert(
                    warnings.any { warning -> warning.contains("androidTarget") }
                )
            }
        }
    }


    // https://youtrack.jetbrains.com/issue/KT-48436
    @GradleAndroidTest
    fun testUnusedSourceSetsReportAndroid(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk
    ) {
        project(
            "new-mpp-android", gradleVersion,
            defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            build("assembleDebug") {
                output.assertNoDiagnostic(KotlinToolingDiagnostics.UnusedSourceSetsWarning)
            }
        }
    }

    @GradleAndroidTest
    fun smokeTestWithIcerockMobileMultiplatformGradlePlugin(
        gradleVersion: GradleVersion,
        agpVersion: String,
        jdkVersion: JdkVersions.ProvidedJdk
    ) {
        project(
            "kgp-with-icerock-mobile-multiplatform", gradleVersion,
            defaultBuildOptions.copy(androidVersion = agpVersion),
            buildJdk = jdkVersion.location
        ) {
            settingsGradleKts.replaceText(
                "resolutionStrategy {",
                """
                    resolutionStrategy {
                        eachPlugin {
                            if (requested.id.id.startsWith("dev.icerock.mobile.multiplatform")) {
                                useModule("dev.icerock:mobile-multiplatform:0.14.2")
                            }
                        }
                """.trimIndent()
            )
            build("assemble")
        }
    }
}
