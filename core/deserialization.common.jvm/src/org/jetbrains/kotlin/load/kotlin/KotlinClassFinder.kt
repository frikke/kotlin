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

package org.jetbrains.kotlin.load.kotlin

import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.serialization.deserialization.KotlinMetadataFinder
import org.jetbrains.kotlin.utils.ReusableByteArray

interface KotlinClassFinder : KotlinMetadataFinder {
    fun findKotlinClassOrContent(classId: ClassId): Result?

    fun findKotlinClassOrContent(javaClass: JavaClass): Result?

    sealed class Result {
        fun toKotlinJvmBinaryClass(): KotlinJvmBinaryClass? = (this as? KotlinClass)?.kotlinJvmBinaryClass

        abstract val content: ReusableByteArray?

        class KotlinClass(val kotlinJvmBinaryClass: KotlinJvmBinaryClass, override val content: ReusableByteArray? = null) : Result() {
            operator fun component1(): KotlinJvmBinaryClass = kotlinJvmBinaryClass
            operator fun component2(): ReusableByteArray? = content
        }

        class ClassFileContent(override val content: ReusableByteArray) : Result()
    }
}

fun KotlinClassFinder.findKotlinClass(classId: ClassId): KotlinJvmBinaryClass? =
    findKotlinClassOrContent(classId)?.toKotlinJvmBinaryClass()

fun KotlinClassFinder.findKotlinClass(javaClass: JavaClass): KotlinJvmBinaryClass? =
    findKotlinClassOrContent(javaClass)?.toKotlinJvmBinaryClass()
