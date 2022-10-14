// FILE: Base.java

public class Base {
    public String a = "a";

    public String b = "b";

    public String c = "c";
}

// FILE: test.kt

class Derived : Base() {
    val a = "aa"

    val b get() = "bb"

    lateinit var c: String
}

fun test(d: Derived) {
    d.a
    d.<!BASE_CLASS_FIELD_SHADOWS_DERIVED_CLASS_PROPERTY("Base; with custom getter")!>b<!>
    d.<!BASE_CLASS_FIELD_SHADOWS_DERIVED_CLASS_PROPERTY("Base; with lateinit")!>c<!>
}
