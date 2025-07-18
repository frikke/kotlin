// RUN_PIPELINE_TILL: BACKEND
fun bar(x: Int): Int = x + 1

fun foo() {
    val x: Int? = null

    if (x != null) {
        when (x) {
            0 -> bar(x)
            else -> {}
        }
    }

    when (x) {
        0 -> { if (<!SENSELESS_COMPARISON!>x == null<!>) return }
        else -> { if (x == null) return }
    }
    bar(x)
}

/* GENERATED_FIR_TAGS: additiveExpression, equalityExpression, functionDeclaration, ifExpression, integerLiteral,
localProperty, nullableType, propertyDeclaration, smartcast, whenExpression, whenWithSubject */
