// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +ProperUninitializedEnumEntryAccessAnalysis
// ISSUE: KT-72743

fun test() {
    <!WRONG_MODIFIER_TARGET!>enum<!> class MyEnum {
        A;

        val someProperty = 10
    }

    MyEnum.<!UNINITIALIZED_ENUM_ENTRY!>A<!>.someProperty
}

enum class MyEnum {
    A;

    val someProperty = 10

    init {
        MyEnum.<!UNINITIALIZED_ENUM_ENTRY!>A<!>.someProperty
    }
}

/* GENERATED_FIR_TAGS: enumDeclaration, enumEntry, functionDeclaration, init, integerLiteral, localClass,
propertyDeclaration */
