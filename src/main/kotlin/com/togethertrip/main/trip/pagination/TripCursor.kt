package com.togethertrip.main.trip.pagination

import java.time.Instant

data class TripCursor(
    val createdAt: Instant,
    val id: Long,
) {
    fun encode(): String {
        return "$createdAt$SEPARATOR$id"
    }

    companion object {
        private const val SEPARATOR = "_"

        fun decode(cursor: String): TripCursor {
            val separatorIndex = cursor.lastIndexOf(SEPARATOR)

            require(separatorIndex > 0 && separatorIndex < cursor.lastIndex) {
                "Invalid trip cursor format"
            }

            return TripCursor(
                createdAt = Instant.parse(cursor.substring(0, separatorIndex)),
                id = cursor.substring(separatorIndex + 1).toLong(),
            )
        }
    }
}
