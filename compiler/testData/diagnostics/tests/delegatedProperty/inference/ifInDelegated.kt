// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// ISSUE: KT-58013 (related)
// WITH_REFLECT
// FIR_DUMP

import kotlin.reflect.KProperty

data class Ref<D>(val t: D)

operator fun <V> Ref<V>.getValue(hisRef: Any?, property: KProperty<*>): V = this.t

fun <E> List<Ref<*>>.getElement(i: Int): Ref<E> = this[i] <!UNCHECKED_CAST!>as Ref<E><!>

fun test(list: List<Ref<*>>, arg: Boolean) {
    val data: String by if (arg) list.getElement(0) else list.getElement(1)
}

/* GENERATED_FIR_TAGS: asExpression, classDeclaration, data, funWithExtensionReceiver, functionDeclaration, ifExpression,
integerLiteral, localProperty, nullableType, operator, primaryConstructor, propertyDeclaration, propertyDelegate,
starProjection, thisExpression, typeParameter */
