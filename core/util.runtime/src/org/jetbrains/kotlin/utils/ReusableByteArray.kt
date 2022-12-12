/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.utils

import java.io.InputStream

class ReusableByteArray(val bytes: ByteArray) {
    var size: Int = bytes.size
    private var retainCount = 0

    val isRetained: Boolean get() = retainCount > 0

    /**
     * Marks this byte array as "being retained in cache or stored inside some other object" to avoid reuse of its byte array
     * content when `retained = true`. Clears the corresponding mark when `retained = false`.
     */
    fun markRetained(retained: Boolean) {
        if (retained) {
            retainCount++
        } else {
            check(--retainCount >= 0) { "retainCount underflow -- more markRetain(false) calls than markRetained(true)" }
        }
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    fun isNotEmpty(): Boolean = size > 0
}

private const val MIN_SIZE = 8192
private const val MAX_SIZE = 65536
private const val KEEP_COUNT = 2

private val reuse = ThreadLocal<Cache>()

private class Cache {
    val bufs = arrayOfNulls<ReusableByteArray>(KEEP_COUNT)
    var i = 0
}

fun takeReusableByteArray(size: Int): ReusableByteArray {
    if (size > MAX_SIZE) return ReusableByteArray(ByteArray(size))
    val cache = reuse.get() ?: Cache().also { reuse.set(it) }
    val i = cache.i
    val oldBuf = cache.bufs[i]
    if (oldBuf != null && oldBuf.bytes.size >= size && !oldBuf.isRetained) {
        oldBuf.size = size
        return oldBuf
    }
    val prevSize = oldBuf?.bytes?.size ?: (MIN_SIZE / 2)
    val buf = ReusableByteArray(ByteArray(size.coerceAtLeast(2 * prevSize).coerceAtMost(MAX_SIZE)))
    buf.size = size
    cache.bufs[i] = buf
    cache.i = (i + 1) % KEEP_COUNT
    return buf
}

fun InputStream.readToReusableByteArray(): ReusableByteArray {
    TODO()
}

class ReusableByteArrayInputStream(val content: ReusableByteArray) : InputStream() {
    private var closed = false

    override fun read(): Int {
        TODO("Not yet implemented")
    }

    override fun close() {
        if (closed) return
        closed = true
        lock.unlock()
    }
}

