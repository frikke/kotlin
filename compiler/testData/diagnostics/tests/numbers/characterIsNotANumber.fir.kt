// RUN_PIPELINE_TILL: FRONTEND
fun foo(n: Number) = n

fun test() {
    foo(<!ARGUMENT_TYPE_MISMATCH!>'a'<!>)

    val c = 'c'
    foo(<!ARGUMENT_TYPE_MISMATCH!>c<!>)

    val d: Char? = 'd'
    foo(<!ARGUMENT_TYPE_MISMATCH!>d!!<!>)
}

/* GENERATED_FIR_TAGS: checkNotNullCall, functionDeclaration, localProperty, nullableType, propertyDeclaration */
