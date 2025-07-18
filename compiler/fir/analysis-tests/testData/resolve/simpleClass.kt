// RUN_PIPELINE_TILL: FRONTEND
interface SomeInterface {
    fun foo(x: Int, y: String): String

    val bar: Boolean
}

class SomeClass : SomeInterface {
    private val baz = 42

    override fun foo(x: Int, y: String): String {
        return y + x + baz
    }

    override var bar: Boolean
        get() = true
        set(value) {}

    <!MUST_BE_INITIALIZED_OR_BE_ABSTRACT!>var fau: Double<!>
}

/* GENERATED_FIR_TAGS: additiveExpression, classDeclaration, functionDeclaration, getter, integerLiteral,
interfaceDeclaration, override, propertyDeclaration, setter */
