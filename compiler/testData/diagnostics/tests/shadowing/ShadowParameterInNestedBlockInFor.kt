// RUN_PIPELINE_TILL: BACKEND
// DIAGNOSTICS: +UNUSED_PARAMETER +UNUSED_LAMBDA_EXPRESSION +UNUSED_VARIABLE
fun f(<!UNUSED_PARAMETER!>i<!>: Int) {
    for (j in 1..100) {
        <!UNUSED_LAMBDA_EXPRESSION!>{
            var <!NAME_SHADOWING, UNUSED_VARIABLE!>i<!> = 12
        }<!>
    }
}

/* GENERATED_FIR_TAGS: forLoop, functionDeclaration, integerLiteral, lambdaLiteral, localProperty, propertyDeclaration,
rangeExpression */
