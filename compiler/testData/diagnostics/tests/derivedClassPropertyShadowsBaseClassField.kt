// WITH_STDLIB
// FILE: Base.java

public class Base {
    public String a = "a";

    public String b = "b";

    public String c = "c";

    public String d = "d";

    public String e = "e";
}

// FILE: test.kt

class Derived : Base() {
    val a = "aa"

    val b get() = "bb"

    lateinit var c: String

    val d by lazy { "dd" }

    var e: String = "ee"
        set(value) {
            println(value)
            field = value
        }
}

fun test(d: Derived) {
    d.a
    d.<!BASE_CLASS_FIELD_SHADOWS_DERIVED_CLASS_PROPERTY("Base; with custom getter")!>b<!>
    d.<!BASE_CLASS_FIELD_SHADOWS_DERIVED_CLASS_PROPERTY("Base; with lateinit")!>c<!>
    d.<!BASE_CLASS_FIELD_SHADOWS_DERIVED_CLASS_PROPERTY("Base; with custom getter")!>d<!>
    d.<!BASE_CLASS_FIELD_SHADOWS_DERIVED_CLASS_PROPERTY("Base; with custom setter")!>e<!> = ""
}
