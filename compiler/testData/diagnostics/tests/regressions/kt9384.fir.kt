// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_PARAMETER
fun main(args: Array<String>) {
    fun f() = run {
        <!WRONG_MODIFIER_TARGET!>private<!> class C {
            private fun foo() {
                <!TYPECHECKER_HAS_RUN_INTO_RECURSIVE_PROBLEM!>f()<!>.<!UNRESOLVED_REFERENCE!>foo<!>();

            }
        }
        C()
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, lambdaLiteral, localClass, localFunction */
