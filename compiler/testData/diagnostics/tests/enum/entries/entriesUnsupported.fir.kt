// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: -EnumEntries
// WITH_STDLIB
// ISSUE: KT-55251

enum class Foo {
    BAR;
}

fun main() {
    Foo.entries
}

/* GENERATED_FIR_TAGS: enumDeclaration, enumEntry, functionDeclaration */
