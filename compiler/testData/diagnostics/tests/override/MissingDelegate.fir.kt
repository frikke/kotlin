// RUN_PIPELINE_TILL: FRONTEND
class C : <!UNRESOLVED_REFERENCE, UNRESOLVED_REFERENCE!>Base1<!> by <!UNRESOLVED_REFERENCE!>Base2<!>(1) {
  fun test() { }
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, inheritanceDelegation, integerLiteral */
