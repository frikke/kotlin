// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
fun trans(n: Int, f: () -> Boolean) = if (f()) n else null

fun foo() {
    var i: Int? = 5    
    if (i != null) {
        class Changing {
            fun bar() {
                i = null
            }
        }
        <!SMARTCAST_IMPOSSIBLE!>i<!>.hashCode()
        Changing().bar()
        <!SMARTCAST_IMPOSSIBLE!>i<!>.hashCode()
    }
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, equalityExpression, functionDeclaration, functionalType,
ifExpression, integerLiteral, localClass, localProperty, nullableType, propertyDeclaration, smartcast */
