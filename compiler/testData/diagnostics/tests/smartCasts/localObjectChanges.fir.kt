// RUN_PIPELINE_TILL: FRONTEND
fun trans(n: Int, f: () -> Boolean) = if (f()) n else null

fun foo() {
    var i: Int? = 5    
    if (i != null) {
        // Write is AFTER this place
        i.hashCode()
        object {
            fun bar() {
                i = null
            }
        }.bar()
        <!SMARTCAST_IMPOSSIBLE!>i<!>.hashCode()
    }
}

/* GENERATED_FIR_TAGS: anonymousObjectExpression, assignment, equalityExpression, functionDeclaration, functionalType,
ifExpression, integerLiteral, localProperty, nullableType, propertyDeclaration, smartcast */
