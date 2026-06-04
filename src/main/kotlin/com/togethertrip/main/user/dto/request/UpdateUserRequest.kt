package com.togethertrip.main.user.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateUserRequest(
    @field:Size(min = 2, max = 20)
    @field:Schema(
        description = "사용자 닉네임. 2~20자.",
        example = "여행자",
    )
    val nickname: String? = null,

    @field:Pattern(regexp = "MALE|FEMALE")
    @field:Schema(
        description = "성별. MALE 또는 FEMALE.",
        example = "MALE",
        allowableValues = ["MALE", "FEMALE"],
    )
    val gender: String? = null,

    @field:PastOrPresent
    @field:Schema(
        description = "생년월일. yyyy-MM-dd 형식이며 미래 날짜는 허용하지 않습니다.",
        example = "1990-01-01",
    )
    val birthDate: LocalDate? = null,

    @field:Size(max = 500)
    @field:Schema(
        description = "프로필 이미지 URL.",
        example = "https://example.com/profile.png",
    )
    val profileImageUrl: String? = null,
)
