// IGNORE_BACKEND_FIR: JVM_IR
// https://youtrack.jetbrains.com/issue/KT-52236/Different-modality-in-psi-and-fir
// WITH_STDLIB
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses, +CustomEqualsInValueClasses
// CHECK_BYTECODE_LISTING

OPTIONAL_JVM_INLINE_ANNOTATION
value class A(val value: MyClass) {
    override fun hashCode() = 42
}

class MyClass() {
    override fun hashCode() = -1
}

fun box(): String = if (A(MyClass()).hashCode() == 42) "OK" else "Fail"
