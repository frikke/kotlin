// RUN_PIPELINE_TILL: FRONTEND
abstract class A {
    <!EXTERNAL_DECLARATION_CANNOT_BE_ABSTRACT!>abstract<!> val x : Int
        external get
}

interface B {
    val x: Int
        <!EXTERNAL_DECLARATION_IN_INTERFACE!>external<!> get
}

/* GENERATED_FIR_TAGS: classDeclaration, interfaceDeclaration, propertyDeclaration */
