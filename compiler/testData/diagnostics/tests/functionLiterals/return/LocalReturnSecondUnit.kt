// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
val flag = true

val a = b@ {
    if (flag) return@b <!RETURN_TYPE_MISMATCH!>4<!>
    return@b
}

/* GENERATED_FIR_TAGS: ifExpression, integerLiteral, lambdaLiteral, propertyDeclaration */
