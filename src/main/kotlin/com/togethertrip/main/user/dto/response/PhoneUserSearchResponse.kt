package com.togethertrip.main.user.dto.response

data class PhoneUserSearchResponse(
    val found: Boolean,
    val user: PhoneUserSummaryResponse?,
) {

    companion object {
        fun found(user: PhoneUserSummaryResponse): PhoneUserSearchResponse {
            return PhoneUserSearchResponse(
                found = true,
                user = user,
            )
        }

        fun notFound(): PhoneUserSearchResponse {
            return PhoneUserSearchResponse(
                found = false,
                user = null,
            )
        }
    }
}
