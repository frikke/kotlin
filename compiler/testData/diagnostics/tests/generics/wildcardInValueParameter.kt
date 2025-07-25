// RUN_PIPELINE_TILL: FRONTEND
// SKIP_JAVAC
// FILE: JavaClass.java
public class JavaClass {
    public void foo(? x) {}

    public void bar(? extends String y) { }
}

// FILE: main.kt
fun foo() {
    JavaClass().foo(Any())
    JavaClass().bar(<!TYPE_MISMATCH!>Any()<!>)
    JavaClass().bar("")
}

/* GENERATED_FIR_TAGS: functionDeclaration, javaFunction, javaType, stringLiteral */
