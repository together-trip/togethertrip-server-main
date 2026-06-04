package com.togethertrip.main.user.dto.response

import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.domain.UserStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

data class UserResponse(
    @field:Schema(example = "1")
    val id: Long,

    @field:Schema(example = "여행자")
    val nickname: String,

    @field:Schema(example = "MALE", allowableValues = ["MALE", "FEMALE"])
    val gender: String?,

    @field:Schema(example = "1990-01-01")
    val birthDate: LocalDate?,

    @field:Schema(example = "https://example.com/profile.png")
    val profileImageUrl: String?,

    @field:Schema(example = "+821012345678")
    val phoneNumber: String?,

    @field:Schema(example = "2026-06-04T00:00:00Z")
    val phoneVerifiedAt: Instant?,

    @field:Schema(example = "USER")
    val role: UserRole,

    @field:Schema(example = "ACTIVE")
    val status: UserStatus,
) {

    companion object {
        fun from(user: User): UserResponse {
            return UserResponse(
                id = user.id,
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
