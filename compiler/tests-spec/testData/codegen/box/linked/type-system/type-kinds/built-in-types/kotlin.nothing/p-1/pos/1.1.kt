// WITH_RUNTIME

/*
 * KOTLIN CODEGEN BOX SPEC TEST (POSITIVE)
 *
 * SPEC VERSION: 0.1-213
 * PLACE: type-system, type-kinds, built-in-types, kotlin.nothing -> paragraph 1 -> sentence 1
 * NUMBER: 1
 * DESCRIPTION: todo
 */

fun box() {
    val b : Any? = null
    try {
        val a: Int
        a = b!!
    } catch (e: NullPointerException) {
        return "OK"
    }
    return "NOK"
}