// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// DIAGNOSTICS: -UNUSED_VARIABLE
// KT-10968 Callable reference: type inference by function return type

fun <T> getT(): T = null!!

fun getString() = ""

fun test() {
    val a : () -> String = ::getString
    val b : () -> String = ::getT
}

/* GENERATED_FIR_TAGS: callableReference, checkNotNullCall, functionDeclaration, functionalType, localProperty,
nullableType, propertyDeclaration, stringLiteral, typeParameter */
