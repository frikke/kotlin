// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// CHECK_TYPE
// FILE: p/J.java

package p;

public class J {
    public void arr(Object[] s) {}
}

// FILE: k.kt

import p.*

fun test(
        aa: Array<Any>,
        sa: Array<String>,
        san: Array<String?>
) {
    J().arr(null)
    J().arr(aa)
    J().arr(sa)
    J().arr(san)
}

/* GENERATED_FIR_TAGS: classDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType, infix,
javaFunction, javaType, nullableType, typeParameter, typeWithExtension */
