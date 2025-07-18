// RUN_PIPELINE_TILL: FRONTEND
// CHECK_TYPE
// DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER

class Outer<E> {
    inner class Inner<F> {
        fun foo() = this
        fun baz(): Inner<String> = null!!
    }

    fun innerFactory(): Outer<E>.Inner<String> = null!!

    fun bar() = Inner<E>()
    fun set(inner: Inner<out E>) {}

    fun inside() {
        innerFactory().checkType { _<Inner<String>>() }
    }
}

fun factoryString(): Outer<String>.Inner<String> = null!!

fun <T, Y> infer(x: T, y: Y): Outer<T>.Inner<Y> = null!!
val inferred = infer("", 1)

fun main() {
    val outer = Outer<String>()

    checkSubtype<Outer<String>.Inner<String>>(outer.bar())
    checkSubtype<Outer<String>.Inner<Int>>(outer.Inner<Int>())
    checkSubtype<Outer<*>.Inner<*>>(outer.bar())
    checkSubtype<Outer<*>.Inner<*>>(outer.Inner<Int>())

    checkSubtype<Outer<CharSequence>.Inner<CharSequence>>(<!ARGUMENT_TYPE_MISMATCH!>outer.bar()<!>)
    checkSubtype<Outer<CharSequence>.Inner<CharSequence>>(<!ARGUMENT_TYPE_MISMATCH!>outer.<!CANNOT_INFER_PARAMETER_TYPE!>Inner<!>()<!>)

    outer.set(outer.bar())
    outer.set(outer.Inner())

    val x: Outer<String>.Inner<String> = factoryString()
    outer.set(x)
}

/* GENERATED_FIR_TAGS: checkNotNullCall, classDeclaration, funWithExtensionReceiver, functionDeclaration, functionalType,
infix, inner, integerLiteral, lambdaLiteral, localProperty, nullableType, outProjection, propertyDeclaration,
starProjection, stringLiteral, thisExpression, typeParameter, typeWithExtension */
