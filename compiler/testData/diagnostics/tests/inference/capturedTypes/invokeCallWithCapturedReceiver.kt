// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_PARAMETER

class Inv1<T>

operator fun <T> Inv1<T>.invoke(action: T.() -> Unit) {}

fun test(inv: Inv1<out Number>) {
    inv { }
    inv.invoke { }
}

/* GENERATED_FIR_TAGS: capturedType, classDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType,
lambdaLiteral, nullableType, operator, outProjection, typeParameter, typeWithExtension */
