// RUN_PIPELINE_TILL: FRONTEND
interface Some {
    fun test()
}

class SomeImpl : Some  {
    override <!CONFLICTING_OVERLOADS!>fun test()<!> {}
    override <!CONFLICTING_OVERLOADS!>fun test()<!> {}
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, interfaceDeclaration, override */
