package mx.youteachtk.epsonrasimulator.domain

/** Names must fit the durable session's UTF-8 contract before entering live state. */
fun validatedTeachPointName(value: String): String {
    val name = value.trim()
    require(name.isNotEmpty()) { "Point name is required" }
    require(name.none { it.code < 32 || it.code == 127 } && Charsets.UTF_8.newEncoder().canEncode(name)) {
        "Point name contains invalid characters"
    }
    require(name.toByteArray(Charsets.UTF_8).size <= 256) { "Point name is too long (maximum 256 UTF-8 bytes)" }
    return name
}
