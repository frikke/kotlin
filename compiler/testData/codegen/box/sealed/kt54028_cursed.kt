// ALLOW_FILES_WITH_SAME_NAMES
// KT-54028

// Test that if we have two files with the same name in the same package, everything works.

// FILE: cursed.kt

package foo

sealed interface LazyGridLayoutInfo {
    fun ok(): String
}

// FILE: cursed.kt

package foo

class LazyGridState {
    val layoutInfo: LazyGridLayoutInfo
        get() = EmptyLazyGridLayoutInfo
}

private object EmptyLazyGridLayoutInfo : LazyGridLayoutInfo {
    override fun ok() = "OK"
}

// FILE: main.kt

import foo.*

fun box(): String {
    return LazyGridState().layoutInfo.ok()
}

