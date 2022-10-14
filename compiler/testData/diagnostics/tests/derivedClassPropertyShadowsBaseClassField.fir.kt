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
    d.b
    d.c
}
