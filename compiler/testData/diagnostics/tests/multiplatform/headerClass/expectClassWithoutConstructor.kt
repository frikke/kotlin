// IGNORE_FIR_DIAGNOSTICS
// RUN_PIPELINE_TILL: FRONTEND
// MODULE: m1-common
// FILE: common.kt

expect class Foo
expect class Bar()
expect class Baz constructor()
expect class FooBar {
    constructor()
}

fun test() {
    <!RESOLUTION_TO_CLASSIFIER!>Foo<!>()
    Bar()
    Baz()
    FooBar()
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

actual class Foo
actual class Bar
actual class Baz
actual class FooBar

/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, functionDeclaration, primaryConstructor, secondaryConstructor */
