package com.togethertrip.main.transaction.pagination

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

data class TransactionCursor(
    val createdAt: Instant,
    val id: Long,
) {
    fun encode(): String {
        val value = "${createdAt}${SEPARATOR}${id}"
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {
        private const val SEPARATOR = "|"

        fun decode(cursor: String): TransactionCursor {
            val value = String(
                Base64.getUrlDecoder().decode(cursor),
                StandardCharsets.UTF_8,
            )
            val parts = value.split(SEPARATOR)
            require(parts.size == 2)

            return TransactionCursor(
                createdAt = Instant.parse(parts[0]),
                id = parts[1].toLong(),
            )
        }
    }
}
