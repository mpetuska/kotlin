// SKIP_TXT

fun <T> T.myApply(block: T.() -> Unit): T = this

fun bar(): Int = 1

interface A : C
interface B : C
interface C {
    fun baz()
}

fun Any.foo() = myApply {
    when (this) {
        is A -> ::bar
        is B -> ::bar
        else -> throw RuntimeException()
    }

    baz() // Smart cast should work
}
