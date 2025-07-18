// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// FILE: a/Test.java
package a;

public class Test<T> {
  T t() {return null;}
}
// FILE: b.kt
package a

fun foo() {
  // If this fails, it means that we have broken the rule that Java returns are always nullable
  a.Test<Int>().t() + 1
}

/* GENERATED_FIR_TAGS: additiveExpression, flexibleType, functionDeclaration, integerLiteral, javaFunction, javaType */
