// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
interface P {
    var f: Number
}

open class Q {
    val x: Int = 42
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class R<!> : P, Q()

val s: Q = <!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>object<!> : Q(), P {}

/* GENERATED_FIR_TAGS: anonymousObjectExpression, classDeclaration, integerLiteral, interfaceDeclaration,
propertyDeclaration */
