// IGNORE_FIR_DIAGNOSTICS
// RUN_PIPELINE_TILL: FRONTEND
// MODULE: m1-common
// FILE: common.kt

<!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> typealias Foo = String

class <!PACKAGE_OR_CLASSIFIER_REDECLARATION{JVM}!>Outer<!> <!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> constructor() {
    <!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> class Nested

    <!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> init {}

    <!NON_ABSTRACT_FUNCTION_WITH_NO_BODY, NON_ABSTRACT_FUNCTION_WITH_NO_BODY{JVM}!><!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> fun foo()<!>
    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT, MUST_BE_INITIALIZED_OR_BE_ABSTRACT{JVM}!><!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> val bar: Int<!>
}

<!CONFLICTING_OVERLOADS{JVM}!>fun foo()<!> {
    <!NON_MEMBER_FUNCTION_NO_BODY, NON_MEMBER_FUNCTION_NO_BODY{JVM}!><!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> fun localFun()<!>
    <!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> var x = 42
    <!WRONG_MODIFIER_TARGET, WRONG_MODIFIER_TARGET{JVM}!>expect<!> class Bar
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

class <!PACKAGE_OR_CLASSIFIER_REDECLARATION!>Outer<!> <!ACTUAL_WITHOUT_EXPECT!>actual constructor()<!> {
    actual class Nested

    <!WRONG_MODIFIER_TARGET!>actual<!> init {}
}

<!CONFLICTING_OVERLOADS!>fun foo()<!> {
    <!WRONG_MODIFIER_TARGET!>actual<!> fun localFun() {}
    <!WRONG_MODIFIER_TARGET!>actual<!> var x = 42
    <!WRONG_MODIFIER_TARGET!>actual<!> class <!ACTUAL_WITHOUT_EXPECT!>Bar<!>
}

/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, functionDeclaration, init, integerLiteral, localClass,
localFunction, localProperty, nestedClass, primaryConstructor, propertyDeclaration, typeAliasDeclaration */
