// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
fun <T: Any> test(f: (T) -> T?) {
    doFun(f.ext())
}

fun <E : Any> Function1<E, E?>.ext(): Function0<E?> = throw Exception()
fun <R : Any> doFun(f: () -> R?) = f

/* GENERATED_FIR_TAGS: funWithExtensionReceiver, functionDeclaration, functionalType, nullableType, typeConstraint,
typeParameter */
