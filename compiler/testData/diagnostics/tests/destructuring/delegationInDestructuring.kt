// RUN_PIPELINE_TILL: FRONTEND
// WITH_STDLIB

fun foo() {
    <!INITIALIZER_REQUIRED_FOR_DESTRUCTURING_DECLARATION!>val (a, b) by <!DEBUG_INFO_MISSING_UNRESOLVED!>listOf<!>(1, 2)<!>
    <!INITIALIZER_REQUIRED_FOR_DESTRUCTURING_DECLARATION!>val (c, d) by <!DEBUG_INFO_MISSING_UNRESOLVED!>lazy<!> { <!DEBUG_INFO_MISSING_UNRESOLVED!>listOf<!>(3, 4) }<!>
}

/* GENERATED_FIR_TAGS: destructuringDeclaration, functionDeclaration, localProperty, propertyDeclaration */
