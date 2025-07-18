// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_PARAMETER

// FILE: a.kt

package a

fun <T> foo(block: (T?) -> Unit) {}

// FILE: b.kt

package b

fun <K> foo(block: (K) -> Unit) {}

// FILE: test.kt

import a.*
import b.*

fun main() {
    foo<String> {  }
}

/* GENERATED_FIR_TAGS: functionDeclaration, functionalType, lambdaLiteral, nullableType, typeParameter */
