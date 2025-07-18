// RUN_PIPELINE_TILL: FRONTEND
fun Any?.foo(my: My) = my === this

class My(val x: Any)

// my is nullable in brackets because Any?.foo has nullable receiver
fun foo(my: My?) = my?.x.foo(<!ARGUMENT_TYPE_MISMATCH!>my<!>)

/* GENERATED_FIR_TAGS: classDeclaration, equalityExpression, funWithExtensionReceiver, functionDeclaration, nullableType,
primaryConstructor, propertyDeclaration, safeCall, thisExpression */
