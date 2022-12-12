// TARGET_BACKEND: NATIVE
// test is disabled now because of https://youtrack.jetbrains.com/issue/KT-55426
// IGNORE_BACKEND: NATIVE

// MODULE: lib
// FILE: lib.kt

import kotlin.native.concurrent.*
import kotlin.native.internal.*

class Box(@Volatile var value: String)

// MODULE: main(lib)
// FILE: main.kt

@file:Suppress("INVISIBLE_MEMBER")

import kotlin.native.concurrent.*

fun box() : String {
    val o = "O"
    val x = Box(o)
    return x.compareAndSwapField(Box::value, o, "K") + x.value
}