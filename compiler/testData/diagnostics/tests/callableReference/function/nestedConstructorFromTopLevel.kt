// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// CHECK_TYPE

import kotlin.reflect.KFunction0

class A {
    class Nested
}

fun main() {
    val x = A::Nested

    checkSubtype<KFunction0<A.Nested>>(x)
}

/* GENERATED_FIR_TAGS: callableReference, classDeclaration, funWithExtensionReceiver, functionDeclaration,
functionalType, infix, localProperty, nestedClass, nullableType, propertyDeclaration, typeParameter, typeWithExtension */
