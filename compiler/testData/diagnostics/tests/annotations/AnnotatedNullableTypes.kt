// RUN_PIPELINE_TILL: FRONTEND
annotation class Ann

val a: <!WRONG_ANNOTATION_TARGET!>@Ann<!> String? = ""
val b: (@Ann String)? = "" // false negative in K1, OK in K2

@Target(AnnotationTarget.TYPE)
annotation class TypeAnn

val c: @TypeAnn String? = ""
val d: (@TypeAnn String)? = ""

/* GENERATED_FIR_TAGS: annotationDeclaration, nullableType, propertyDeclaration, stringLiteral */
