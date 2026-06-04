package com.togethertrip.main.user.dto.response

import com.togethertrip.main.user.domain.User

data class PhoneUserSummaryResponse(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
) {

    companion object {
        fun from(user: User): PhoneUserSummaryResponse {
            return PhoneUserSummaryResponse(
                userId = user.id,
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
            )
        }
    }
}
