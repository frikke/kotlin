// RUN_PIPELINE_TILL: FRONTEND
// SKIP_TXT
open class BaseWithPrivate {
    private companion object {
        val X: Int = 1
        val Y: Int = 1
    }
}

open class Base {
    companion object {
        val X: String = ""
    }
}

class Derived : Base() {
    fun foo() {
        object : BaseWithPrivate() {
            fun bar() {
                X.length
                <!INVISIBLE_REFERENCE!>Y<!>.hashCode()
            }
        }
    }
}

/* GENERATED_FIR_TAGS: anonymousObjectExpression, classDeclaration, companionObject, functionDeclaration, integerLiteral,
objectDeclaration, propertyDeclaration, stringLiteral */
