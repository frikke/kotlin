// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// OPT_IN: kotlin.RequiresOptIn
// DIAGNOSTICS: -UNUSED_PARAMETER

import kotlin.experimental.ExperimentalTypeInference

class Buildee<T>
class Builder<T>

@OptIn(ExperimentalTypeInference::class)
inline fun <T> builder(block: Builder<T>.() -> Unit): Buildee<T> = TODO()

private fun <T> Builder<T>.consumer(builder: Builder<T>): Unit = TODO()

fun <T> Builder<T>.foo(): Buildee<T> = builder {
    consumer(this)
}

/* GENERATED_FIR_TAGS: classDeclaration, classReference, funWithExtensionReceiver, functionDeclaration, functionalType,
inline, lambdaLiteral, nullableType, thisExpression, typeParameter, typeWithExtension */
