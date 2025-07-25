// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
abstract class A

internal class B : A()

abstract class Base {
    protected abstract val a: A
}

internal class Derived : Base() {
    override val a = B()
        get() = field
}

/* GENERATED_FIR_TAGS: classDeclaration, getter, override, propertyDeclaration */
