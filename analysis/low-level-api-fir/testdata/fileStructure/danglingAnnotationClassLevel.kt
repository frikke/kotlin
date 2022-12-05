class Foo {/* NonReanalyzableDeclarationStructureElement */
    @Suppress("") @MustBeDocumented/* DanglingModifierListStructureElement */
}
class Bar {/* NonReanalyzableDeclarationStructureElement */
    @Suppress("") @MustBeDocumented/* DanglingModifierListStructureElement */
}
class Outer {/* NonReanalyzableDeclarationStructureElement */
    class Inner {/* NonReanalyzableDeclarationStructureElement */
        @Suppress("") @MustBeDocumented/* DanglingModifierListStructureElement */
    }
    fun foo() {/* ReanalyzableFunctionStructureElement */
        class Local {
            @Suppress("") @MustBeDocumented
        }
    }
}
