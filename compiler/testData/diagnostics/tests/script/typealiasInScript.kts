// RUN_PIPELINE_TILL: FRONTEND
typealias TopLevelInScript = String

class C {
    <!TOPLEVEL_TYPEALIASES_ONLY!>typealias NestedInClass = String<!>
}

fun foo() {
    <!TOPLEVEL_TYPEALIASES_ONLY!>typealias Local = String<!>
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, localProperty, propertyDeclaration, typeAliasDeclaration */
