/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A resource that can be closed or released.
 *
 * The [close] function is called automatically after execution of the [use] block.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
//@SinceKotlin("1.8")
public expect interface AutoCloseable {
    /**
     * Closes this resource.
     *
     * This function may throw, thus it is strongly recommended to use the [use] function instead,
     * which closes this resource correctly whether an exception is thrown or not.
     *
     * Cases where the close operation may fail require careful attention by implementers.
     * It is strongly advised to relinquish the underlying resources and to internally mark the resource as closed,
     * prior to throwing the exception. The close method is unlikely to be invoked more than once
     * and so this ensures that the resources are released in a timely manner. Furthermore, it reduces problems
     * that could arise when the resource wraps, or is wrapped, by another resource.
     */
    public fun close(): Unit
}

/**
 * Executes the given [block] function on this resource and then closes it down correctly whether an exception
 * is thrown or not.
 *
 * In case if the resource is being closed due to an exception occurred in [block], and the closing also fails with an exception,
 * the latter is added to the [suppressed][Throwable.addSuppressed] exceptions of the former.
 *
 * @param block a function to process this [AutoCloseable] resource.
 * @return the result of [block] function invoked on this resource.
 */
//@SinceKotlin("1.8")
@kotlin.internal.InlineOnly
public inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        this.closeFinally(exception)
    }
}

/**
 * Closes this [AutoCloseable], suppressing possible exception or error thrown by [AutoCloseable.close] function when
 * it's being closed due to some other [cause] exception occurred.
 *
 * The suppressed exception is added to the list of suppressed exceptions of [cause] exception.
 */
//@SinceKotlin("1.8")
@PublishedApi
internal fun AutoCloseable?.closeFinally(cause: Throwable?) = when {
    this == null -> {}
    cause == null -> close()
    else ->
        try {
            close()
        } catch (closeException: Throwable) {
            cause.addSuppressed(closeException)
        }
}
