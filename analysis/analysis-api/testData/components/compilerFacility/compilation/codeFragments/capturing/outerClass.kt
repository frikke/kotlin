// MODULE: context

// FILE: context.kt
class Test {
    val a: String = "foo"

    fun test() {
        <caret_context>val x = 0
    }
}


// MODULE: main
// MODULE_KIND: CodeFragment

// FILE: fragment.kt
// CODE_FRAGMENT_KIND: EXPRESSION
a.length