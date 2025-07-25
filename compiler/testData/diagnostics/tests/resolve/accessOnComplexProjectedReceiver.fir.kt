// RUN_PIPELINE_TILL: BACKEND
// ISSUE: KT-66638

class Super<T1, T2>
data class Child<T : Super<String, Child<T>>>(val foo: String)

fun foo(child: Child<*>) {
    child.foo
}

/* GENERATED_FIR_TAGS: classDeclaration, data, functionDeclaration, nullableType, primaryConstructor,
propertyDeclaration, starProjection, typeConstraint, typeParameter */
