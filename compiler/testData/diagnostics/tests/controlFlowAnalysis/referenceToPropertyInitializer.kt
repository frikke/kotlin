// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_VARIABLE
package o

class TestFunctionLiteral {
    val sum: (Int) -> Int = { x: Int ->
        <!UNINITIALIZED_VARIABLE!>sum<!>(x - 1) + x
    }
    val foo: () -> Unit = l@ ({ <!UNINITIALIZED_VARIABLE!>foo<!>() })
}

open class A(val a: A)

class TestObjectLiteral {
    val obj: A = object: A(<!UNINITIALIZED_VARIABLE!>obj<!>) {
        init {
            val x = <!UNINITIALIZED_VARIABLE!>obj<!>
        }
        fun foo() {
            val y = <!UNINITIALIZED_VARIABLE!>obj<!>
        }
    }
    val obj1: A = <!REDUNDANT_LABEL_WARNING!>l@<!> ( object: A(<!UNINITIALIZED_VARIABLE!>obj1<!>) {
        init {
            val x = <!UNINITIALIZED_VARIABLE!>obj1<!>
        }
        fun foo() = <!UNINITIALIZED_VARIABLE!>obj1<!>
    })
}

class TestOther {
    val x: Int = <!UNINITIALIZED_VARIABLE!>x<!> + 1
}

/* GENERATED_FIR_TAGS: additiveExpression, anonymousObjectExpression, classDeclaration, functionDeclaration,
functionalType, init, integerLiteral, lambdaLiteral, localProperty, primaryConstructor, propertyDeclaration */
