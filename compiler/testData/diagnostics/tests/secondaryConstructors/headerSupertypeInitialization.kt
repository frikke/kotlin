// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_PARAMETER
open class B(x: Int)
class A : <!SUPERTYPE_INITIALIZED_WITHOUT_PRIMARY_CONSTRUCTOR!>B(1)<!> {
    constructor(): super(1)
}

/* GENERATED_FIR_TAGS: classDeclaration, integerLiteral, primaryConstructor, secondaryConstructor */
