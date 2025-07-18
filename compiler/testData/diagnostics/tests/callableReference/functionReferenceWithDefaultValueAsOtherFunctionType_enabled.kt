// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// LANGUAGE: +FunctionReferenceWithDefaultValueAsOtherType

fun foo(a: String, b: Int = 5): String {
    return a + b
}

fun bar1(body: (String) -> String): String {
    return body("something")
}

fun bar2(body: (String, Int) -> String): String {
    return body("something", 0)
}

fun test() {
    bar1(::foo)
    bar2(::foo)
}

/* GENERATED_FIR_TAGS: additiveExpression, callableReference, functionDeclaration, functionalType, integerLiteral,
stringLiteral */
