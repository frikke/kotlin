// RUN_PIPELINE_TILL: BACKEND
fun f(a: (Int) -> Unit) {
    a as Int.() -> Unit

    f1(a as Int.() -> Unit)
}

fun f1(a: Int.() -> Unit) {
    a as (Int) -> Unit
    f(a as (Int) -> Unit)
}

/* GENERATED_FIR_TAGS: asExpression, functionDeclaration, functionalType, typeWithExtension */
