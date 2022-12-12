/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:kotlin.jvm.JvmName("AutoCloseableKt")
@file:kotlin.jvm.JvmPackageName("kotlin.jdk7")

package kotlin


@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias AutoCloseable = java.lang.AutoCloseable

@SinceKotlin("1.2")
@Deprecated("Provided for compatibility")
@DeprecatedSinceKotlin(hiddenSince = "1.8")
@PublishedApi
internal fun java.lang.AutoCloseable?.closeFinally(cause: Throwable?) = when {
    this == null -> {}
    cause == null -> close()
    else ->
        try {
            close()
        } catch (closeException: Throwable) {
            cause.addSuppressed(closeException)
        }
}