// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
//KT-1736 AssertionError in CallResolver

package kt1736

object Obj {
    fun method() {
    }
}

val x = Obj.method<!TOO_MANY_ARGUMENTS!>{ -> }<!>

/* GENERATED_FIR_TAGS: functionDeclaration, lambdaLiteral, objectDeclaration, propertyDeclaration */
