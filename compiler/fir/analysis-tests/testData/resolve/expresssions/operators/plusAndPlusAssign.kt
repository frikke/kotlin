// RUN_PIPELINE_TILL: FRONTEND
class Foo {
    operator fun plus(f: Foo): Foo {<!NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY!>}<!>
    operator fun plusAssign(f: Foo) {}
}

fun test() {
    var f = Foo()
    f <!ASSIGN_OPERATOR_AMBIGUITY!>+=<!> f
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, localProperty, operator, propertyDeclaration */
