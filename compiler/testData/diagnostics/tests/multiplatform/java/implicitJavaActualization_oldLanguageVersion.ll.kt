// RUN_PIPELINE_TILL: FIR2IR
// LANGUAGE: -MultiplatformRestrictions
// MODULE: m1-common
// FILE: common.kt

expect class Foo() {
    fun foo()
}

// MODULE: m2-jvm()()(m1-common)
// FILE: Foo.java

public class Foo {
    public void foo() {
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, expect, functionDeclaration, primaryConstructor */
