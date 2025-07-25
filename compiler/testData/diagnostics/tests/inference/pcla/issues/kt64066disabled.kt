// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// WITH_STDLIB
// ISSUE: KT-64066, KT-53478
// LANGUAGE: +NoBuilderInferenceWithoutAnnotationRestriction

fun box1() {
    val map = buildMap {
        put(1, 1)
        for (v in values) {}
    }
}

class UncompilingClass<T : Any>(
    val block: (UncompilingClass<T>.() -> Unit)? = null,
) {

    var uncompilingFun: ((T) -> Unit)? = null
}

fun handleInt(arg: Int) = Unit

fun box2() {
    val obj = UncompilingClass {
        uncompilingFun = { handleInt(it) }
    }
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, forLoop, functionDeclaration, functionalType, integerLiteral,
lambdaLiteral, localProperty, nullableType, primaryConstructor, propertyDeclaration, typeConstraint, typeParameter,
typeWithExtension */
