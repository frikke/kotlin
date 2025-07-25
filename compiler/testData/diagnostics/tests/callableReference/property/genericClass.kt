// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// CHECK_TYPE

import kotlin.reflect.KProperty1

class A<T>(val t: T) {
    val foo: T = t
}

fun bar() {
    val x = A<String>::foo
    checkSubtype<KProperty1<A<String>, String>>(x)
    checkSubtype<KProperty1<A<String>, Any?>>(x)

    val y = A<*>::foo
    checkSubtype<KProperty1<A<*>, Any?>>(y)
}

/* GENERATED_FIR_TAGS: callableReference, classDeclaration, funWithExtensionReceiver, functionDeclaration,
functionalType, infix, localProperty, nullableType, primaryConstructor, propertyDeclaration, starProjection,
typeParameter, typeWithExtension */
