// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
suspend fun foo1(q: suspend () -> Unit) = q()
suspend fun foo2(x: suspend (Int) -> String) = x(1)


suspend fun foo3(y: suspend String.(Int) -> Double) = "".y(1)
suspend fun String.foo4(y: suspend String.(Int) -> Double) = "".y(1)

fun noSuspend(x: suspend (Int) -> String) {
    <!ILLEGAL_SUSPEND_FUNCTION_CALL!>x<!>(1)
}

/* GENERATED_FIR_TAGS: funWithExtensionReceiver, functionDeclaration, functionalType, integerLiteral, stringLiteral,
suspend, typeWithExtension */
