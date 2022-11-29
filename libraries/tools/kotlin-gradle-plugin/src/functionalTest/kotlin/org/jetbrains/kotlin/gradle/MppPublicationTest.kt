/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MppPublicationTest {

    private val project = ProjectBuilder.builder().build().also {
        addBuildEventsListenerRegistryMock(it)
        disableLegacyWarning(it)
    } as ProjectInternal

    init {
        project.plugins.apply("kotlin-multiplatform")
        project.plugins.apply("maven-publish")
    }

    private val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    init {
        kotlin.jvm()
        kotlin.js().nodejs()
    }

    @Test
    fun `contains kotlinMultiplatform publication`() {
        project.evaluate()
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.publications
            .withType(MavenPublication::class.java)
            .findByName("kotlinMultiplatform") ?: fail("Missing 'kotlinMultiplatform' publication")
    }


    @Test
    fun `all publication contains sourcesJar`() {
        project.evaluate()
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        publishing.publications
            .filterIsInstance<MavenPublication>()
            .forEach { publication ->
                val sources = publication.artifacts.filter { artifact -> artifact.classifier == "sources" }
                assertTrue(sources.isNotEmpty(), "Expected at least one sources artifact for ${publication.name}")
            }
    }

    @Test
    fun `jvm sources elements has same attributes as java sources elements from default java gradle plugin`() {
        val javaProject = ProjectBuilder.builder().build().run {
            // Given
            plugins.apply("java")
            plugins.apply("maven-publish")

            extensions.getByType(JavaPluginExtension::class.java).run {
                withSourcesJar()
            }

            // When
            this as ProjectInternal
            this.evaluate()
        }

        val javaSourcesElementsAttributes = javaProject
            .components
            .getByName("java")
            .attributesOfUsageContext("sourcesElements")

        project.evaluate()

        val kotlinComponent = project.components.getByName("kotlin") as ComponentWithVariants
        val jvmComponent = kotlinComponent.variants.first { it.name == "jvm" }
        val jvmSourcesElementsAttributes = jvmComponent.attributesOfUsageContext("jvmSourcesElements-published")

        val extraExpectedAttributes = mapOf(
            "org.jetbrains.kotlin.platform.type" to "jvm",  // target disambiguation attribute
            "org.gradle.libraryelements" to "jar"           // is set by default by kgp, when it sets usage as `java-runtime-jars`
                                                            // see [KotlinUsages.producerApiUsage]
        )

        val expectedAttributes = javaSourcesElementsAttributes.toMapOfStrings() + extraExpectedAttributes
        val actualAttributes = jvmSourcesElementsAttributes.toMapOfStrings()
        assertEquals(expectedAttributes, actualAttributes)
    }

    @Test
    fun `sources elements includes user-defined attributes`() {
        val userAttribute = Attribute.of("userAttribute", String::class.java)
        kotlin.run {
            targets.all { target -> target.attributes { attribute(userAttribute, target.name) } }
            js(IR)
            linuxX64()
            iosX64()
        }

        project.evaluate()

        val kotlinComponent = project.components.getByName("kotlin") as ComponentWithVariants
        val sourcesElements = listOf("jvm", "js", "linuxX64", "iosX64")
            .map { targetName ->
                targetName to kotlinComponent.variants
                    .first { it.name == targetName }
                    .attributesOfUsageContext("${targetName}SourcesElements-published")
            }

        for ( (targetName, sourceElements) in sourcesElements) {
            assertTrue(
                message = "Sources Elements of target $targetName doesn't have 'userAttribute'"
            ) { sourceElements.attributes.toMapOfStrings().containsKey("userAttribute") }
        }
    }

    private fun SoftwareComponent.attributesOfUsageContext(usageContextName: String): AttributeContainer {
        this as SoftwareComponentInternal
        return usages.first { it.name == usageContextName }.attributes
    }

    private fun AttributeContainer.toMapOfStrings(): Map<String, String> = keySet()
        .associate { key -> key.name to getAttribute(key).toString() }

}
