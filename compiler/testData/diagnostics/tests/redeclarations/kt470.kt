// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
val <!REDECLARATION!>a<!> : Int = 1
val <!REDECLARATION!>a<!> : Int = 1
val <!REDECLARATION!>a<!> : Int = 1

val <!REDECLARATION!>b<!> : Int = 1
val <!REDECLARATION!>b<!> : Int = 1
val <!REDECLARATION!>b<!> : Int = 1
val <!REDECLARATION!>b<!> : Int = 1

<!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here too
<!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here
<!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here
<!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here

<!CONFLICTING_OVERLOADS!>fun bar()<!> {} // and here
<!CONFLICTING_OVERLOADS!>fun bar()<!> {} // and here
<!CONFLICTING_OVERLOADS!>fun bar()<!> {} // and here

class A {
    val <!REDECLARATION!>a<!> : Int = 1
    val <!REDECLARATION!>a<!> : Int = 1
    val <!REDECLARATION!>a<!> : Int = 1

    val <!REDECLARATION!>b<!> : Int = 1
    val <!REDECLARATION!>b<!> : Int = 1
    val <!REDECLARATION!>b<!> : Int = 1
    val <!REDECLARATION!>b<!> : Int = 1

    <!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here too
    <!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here
    <!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here
    <!CONFLICTING_OVERLOADS!>fun foo()<!> {} // and here

    <!CONFLICTING_OVERLOADS!>fun bar()<!> {} // and here
    <!CONFLICTING_OVERLOADS!>fun bar()<!> {} // and here
    <!CONFLICTING_OVERLOADS!>fun bar()<!> {} // and here
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, integerLiteral, propertyDeclaration */
