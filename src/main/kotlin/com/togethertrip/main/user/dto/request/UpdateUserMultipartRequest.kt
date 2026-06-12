package com.togethertrip.main.user.dto.request

import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

data class UpdateUserMultipartRequest(
    @field:Size(min = 2, max = 20)
    val nickname: String? = null,

    @field:Pattern(regexp = "MALE|FEMALE")
    val gender: String? = null,

    @field:PastOrPresent
    val birthDate: LocalDate? = null,

    @field:Size(max = 500)
    val profileImageUrl: String? = null,

    val profileImage: MultipartFile? = null,
) {

    fun toUpdateUserRequest(): UpdateUserRequest {
        return UpdateUserRequest(
            nickname = nickname,
            gender = gender,
            birthDate = birthDate,
            profileImageUrl = profileImageUrl,
        )
    }
}
