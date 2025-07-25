// IGNORE_FIR_DIAGNOSTICS
// RUN_PIPELINE_TILL: BACKEND
// MODULE: m1-common
// FILE: common.kt
expect class Foo {
    fun foo(param: Int)
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt
actual class <!EXPECT_ACTUAL_INCOMPATIBLE_CLASS_SCOPE!>Foo<!> : A

interface A : B {
    override fun foo(param: Int) {}
}

interface B {
    fun foo(param: Int = 3) {}
}

/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, functionDeclaration, integerLiteral, interfaceDeclaration,
override */
