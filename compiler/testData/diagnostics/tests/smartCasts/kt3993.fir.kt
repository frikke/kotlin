// RUN_PIPELINE_TILL: BACKEND
class User(val login : Boolean) {}

fun currentAccess(user: User?): Int {
    return when {
        user == null -> 0
        // We should get smartcast here
        user.login -> 1 
        else -> -1
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, equalityExpression, functionDeclaration, integerLiteral, nullableType,
primaryConstructor, propertyDeclaration, smartcast, whenExpression */
