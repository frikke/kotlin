class Foo {/* NonReanalyzableDeclarationStructureElement */
    @Suppress("") @MustBeDocumented/* RootStructureElement */
}
class Bar {/* NonReanalyzableDeclarationStructureElement */
    @Suppress("") @MustBeDocumented/* RootStructureElement */
}
class Outer {/* NonReanalyzableDeclarationStructureElement */
    class Inner {/* NonReanalyzableDeclarationStructureElement */
        @Suppress("") @MustBeDocumented/* RootStructureElement */
    }
    fun foo() {/* ReanalyzableFunctionStructureElement */
        class Local {
            @Suppress("") @MustBeDocumented
        }
    }
}
