// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// WITH_STDLIB

@Suppress("inapplicable_jvm_name")
interface Factory {
    @get:JvmName("supportsMultilevelIntrospection") // K1: ok, K2: INAPPLICABLE_JVM_NAME
    val supportsMultilevelIntrospection: Boolean
        get() = false
}

/* GENERATED_FIR_TAGS: annotationUseSiteTargetPropertyGetter, getter, interfaceDeclaration, propertyDeclaration,
stringLiteral */
