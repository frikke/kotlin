// RUN_PIPELINE_TILL: FRONTEND
data class StringPair(val first: String, val second: String)

infix fun String.to(second: String) = StringPair(this, second)

fun hashMapOf(pair: StringPair): MutableMap<String, String> {
<!NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY!>}<!>

fun F() : MutableMap<String, String> {
    val value: String? = "xyz"
    if (value == null) throw Error()
    // Smart cast should be here
    return hashMapOf("sss" to <!DEBUG_INFO_SMARTCAST!>value<!>)
}

/* GENERATED_FIR_TAGS: classDeclaration, data, equalityExpression, funWithExtensionReceiver, functionDeclaration,
ifExpression, infix, localProperty, nullableType, primaryConstructor, propertyDeclaration, smartcast, stringLiteral,
thisExpression */
