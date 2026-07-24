package com.togethertrip.main.user.dto.response

data class UserSearchResponse(
    val found: Boolean,
    val user: UserSummaryResponse?,
) {

    companion object {
        fun found(user: UserSummaryResponse): UserSearchResponse {
            return UserSearchResponse(
                found = true,
                user = user,
            )
        }

        fun notFound(): UserSearchResponse {
            return UserSearchResponse(
                found = false,
                user = null,
            )
        }
    }
}
