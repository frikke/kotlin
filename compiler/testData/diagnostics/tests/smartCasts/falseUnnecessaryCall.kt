// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// See KT-10276

class Bar() {
    var test: String? = null
    fun foo() {
        if (test != null) {
            // No warning: test is a mutable property
            test?.hashCode()
        }
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, equalityExpression, functionDeclaration, ifExpression, nullableType,
primaryConstructor, propertyDeclaration, safeCall, smartcast */
