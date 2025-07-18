// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_MESSAGES

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class Ann(val s: String = "")

@Ann("s")
fun foo() {}

val bar = foo(
    <!TOO_MANY_ARGUMENTS("fun foo(): Unit")!>15<!>
)

/* GENERATED_FIR_TAGS: annotationDeclaration, functionDeclaration, integerLiteral, primaryConstructor,
propertyDeclaration, stringLiteral */
