// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
val u = <!UNRESOLVED_REFERENCE!>Unresolved<!>::class
val g = <!UNRESOLVED_REFERENCE!>UnresolvedGeneric<!><<!UNRESOLVED_REFERENCE!>UnresolvedTypeArg<!>>::class

/* GENERATED_FIR_TAGS: classReference, propertyDeclaration */
