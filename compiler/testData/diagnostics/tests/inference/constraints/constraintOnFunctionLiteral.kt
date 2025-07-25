// RUN_PIPELINE_TILL: FRONTEND
package c

import java.util.ArrayList

fun Array<Int>.toIntArray(): IntArray = this.<!TYPE_MISMATCH!>mapTo(<!TYPE_MISMATCH!>IntArray(size)<!>, {it})<!>

fun Array<Int>.toArrayList(): ArrayList<Int> = this.mapTo(ArrayList<Int>(size), {it})

public fun <T, R, C: MutableCollection<in R>> Array<out T>.mapTo(result: C, transform : (T) -> R) : C =
        throw Exception("$result $transform")

/* GENERATED_FIR_TAGS: flexibleType, funWithExtensionReceiver, functionDeclaration, functionalType, inProjection,
intersectionType, javaFunction, lambdaLiteral, nullableType, outProjection, stringLiteral, thisExpression,
typeConstraint, typeParameter */
