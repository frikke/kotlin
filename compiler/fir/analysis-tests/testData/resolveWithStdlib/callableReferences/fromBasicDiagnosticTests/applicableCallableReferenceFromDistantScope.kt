// RUN_PIPELINE_TILL: BACKEND
fun foo(s: String) {}

object Scope {
    fun foo(a: Int) {}
    fun foo(b: Boolean) {}

    fun <T> bar(f: (T) -> Unit): T = TODO()

    fun test() {
        val s: String = bar(::foo)
    }
}

/* GENERATED_FIR_TAGS: callableReference, functionDeclaration, functionalType, localProperty, nullableType,
objectDeclaration, propertyDeclaration, typeParameter */
