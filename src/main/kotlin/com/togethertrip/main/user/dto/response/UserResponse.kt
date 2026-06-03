package com.togethertrip.main.user.dto.response

import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.domain.UserStatus
import java.time.Instant
import java.time.LocalDate

data class UserResponse(
    val id: Long,
    val email: String?,
    val nickname: String,
    val gender: String?,
    val birthDate: LocalDate?,
    val profileImageUrl: String?,
    val phoneNumber: String?,
    val phoneVerifiedAt: Instant?,
    val role: UserRole,
    val status: UserStatus,
) {

    companion object {
        fun from(user: User): UserResponse {
            return UserResponse(
                id = user.id,
                email = user.email,
                nickname = user.nickname,
                gender = user.gender,
                birthDate = user.birthDate,
                profileImageUrl = user.profileImageUrl,
                phoneNumber = user.phoneNumber,
                phoneVerifiedAt = user.phoneVerifiedAt,
                role = user.role,
                status = user.status,
            )
        }
    }
}
