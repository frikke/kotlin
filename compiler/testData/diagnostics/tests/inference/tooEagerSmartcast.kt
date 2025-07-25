// RUN_PIPELINE_TILL: FRONTEND
// SKIP_TXT
// DIAGNOSTICS: -UNUSED_VARIABLE

interface OutBase<out E>
interface OutDerived<out F> : OutBase<F>

fun <X> OutBase<X>.myLast(): X = TODO()

fun <T> foo(x: OutBase<T>) {
    if (x is OutDerived<*>) {
        val l: T = x.myLast() // required T, found Cap(*). Only in NI
    }
}

interface InvBase<E>
interface InvDerived<F> : InvBase<F>

fun <X> InvBase<X>.myLastInv(): X = TODO()

fun <T> fooInv(x: InvBase<T>) {
    if (x is InvDerived<*>) {
        val l: T = <!TYPE_MISMATCH!>x.<!TYPE_MISMATCH!>myLastInv()<!><!> // required T, found Cap(*). Only in NI
    }
}

interface Base<E>

fun <Q> Base<Q>.foo(): Q = TODO()
fun <Q : CharSequence> Base<Q>.baz(): Q = TODO()

interface Derived<F : CharSequence> : Base<F>

fun Number.num() {}

fun main(b: Base<out Number>) {
    b.foo().num()
    if (b is Derived<*>) {
        b.foo().<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>num<!>()
        b.baz().length
    }
}

/* GENERATED_FIR_TAGS: capturedType, funWithExtensionReceiver, functionDeclaration, ifExpression, interfaceDeclaration,
intersectionType, isExpression, localProperty, nullableType, out, outProjection, propertyDeclaration, smartcast,
starProjection, typeConstraint, typeParameter */
