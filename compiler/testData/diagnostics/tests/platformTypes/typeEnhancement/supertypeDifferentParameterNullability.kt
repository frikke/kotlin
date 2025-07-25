// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// FILE: A.java
import org.jetbrains.annotations.*;

public interface A {
    void foo(@Nullable String x);
}

// FILE: B.java
import org.jetbrains.annotations.*;

public interface B {
    void foo(@NotNull String x);
}

// FILE: C.kt

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class C1<!> : A, B {
    override fun foo(x: String) {}
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class C2<!> : A, B {
    override fun foo(x: String?) {}
}

interface I : A, B

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class C3<!> : I {
    override fun foo(x: String) {}
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class C4<!> : I {
    override fun foo(x: String?) {}
}

interface I2 : B, A

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class C3x<!> : I2 {
    override fun foo(x: String) {}
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class C4x<!> : I2 {
    override fun foo(x: String?) {}
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, interfaceDeclaration, javaType, nullableType, override */
