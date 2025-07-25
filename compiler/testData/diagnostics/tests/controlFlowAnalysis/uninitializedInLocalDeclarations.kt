// RUN_PIPELINE_TILL: FRONTEND
fun test1() {
    fun bar() {
        var i : Int
        doSmth(<!UNINITIALIZED_VARIABLE!>i<!>)
    }
}

fun test2() {
    fun foo() {
        val s: String?

        try {
            s = ""
        }
        catch(e: Exception) {
            doSmth(e)
        }

        doSmth(<!UNINITIALIZED_VARIABLE!>s<!>)
    }
}

fun test3() {
    val <!UNUSED_VARIABLE!>f<!> = {
        val a : Int
        doSmth(<!UNINITIALIZED_VARIABLE!>a<!>)
    }
}

fun test4() {
    doSmth {
        val a : Int
        doSmth(<!UNINITIALIZED_VARIABLE!>a<!>)
    }
}

fun test5() {
    fun inner1() {
        fun inner2() {
            fun inner3() {
                fun inner4() {
                    val a : Int
                    doSmth(<!UNINITIALIZED_VARIABLE!>a<!>)
                }
            }
        }
    }
}

fun doSmth(a: Any?) = a

/* GENERATED_FIR_TAGS: assignment, functionDeclaration, lambdaLiteral, localFunction, localProperty, nullableType,
propertyDeclaration, stringLiteral, tryExpression */
