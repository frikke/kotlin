// RUN_PIPELINE_TILL: BACKEND
class Foo(val bar: String?)

fun test(foo: Foo?) {
    foo!!.bar.let {
        // Correct
        foo.bar?.length
        // Unnecessary
        foo<!UNNECESSARY_SAFE_CALL!>?.<!>bar?.length
    }
    foo.bar?.length
}

/* GENERATED_FIR_TAGS: checkNotNullCall, classDeclaration, functionDeclaration, lambdaLiteral, nullableType,
primaryConstructor, propertyDeclaration, safeCall, smartcast */
