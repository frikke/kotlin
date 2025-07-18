// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// CHECK_TYPE
// FILE: A.java
public class A {
    public int foo(Runnable r) { return 0; }
    public String foo(Object r) { return null;}

    public int bar(Runnable r) { return 1; }
    public String bar(CharSequence r) { return null; }
}

// FILE: 1.kt
fun fn() {}
fun x(a: A, r: Runnable) {
    a.foo(::fn) checkType { _<Int>() }
    a.foo {} checkType { _<Int>() }

    a.foo(null) checkType { _<Int>() }
    a.foo(Runnable { }) checkType { _<Int>() }
    a.foo(r) checkType { _<Int>() }

    a.foo(123) checkType { _<String>() }
    a.foo("") checkType { _<String>() }

    a.bar(::fn) checkType { _<Int>() }
    a.bar {} checkType { _<Int>() }

    a.bar(r) checkType { _<Int>() }

    a.<!OVERLOAD_RESOLUTION_AMBIGUITY!>bar<!>(null)

    a.bar(null as Runnable?) checkType { _<Int>() }
    a.bar(null as CharSequence?) checkType { _<String>() }

    a.bar("") checkType { _<String>() }
    a.<!NONE_APPLICABLE!>bar<!>(123)
}

/* GENERATED_FIR_TAGS: asExpression, callableReference, classDeclaration, flexibleType, funWithExtensionReceiver,
functionDeclaration, functionalType, infix, integerLiteral, javaFunction, javaType, lambdaLiteral, nullableType,
samConversion, stringLiteral, typeParameter, typeWithExtension */
