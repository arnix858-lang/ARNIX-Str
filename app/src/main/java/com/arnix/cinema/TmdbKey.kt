package com.arnix.cinema

/**
 * Lightweight key obfuscation. This prevents the API key from appearing as
 * plain text in source code, but is not equivalent to server-side secret storage.
 */
internal object TmdbKey {
    private val encoded = byteArrayOf(
        62, 63, 98, 105, 59, 60, 99, 56,
        60, 105, 60, 110, 57, 60, 104, 62,
        108, 107, 57, 56, 98, 59, 99, 110,
        108, 109, 106, 110, 111, 109, 108, 98
    )
    private const val mask: Byte = 0x5A

    val value: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        encoded.map { byte -> ((byte.toInt() xor mask.toInt()) and 0xFF).toChar() }.joinToString("")
    }
}
