package com.togethertrip.main.user.dto.request

import jakarta.validation.constraints.NotBlank

data class SearchUserByPhoneRequest(
    @field:NotBlank
    val phoneNumber: String,
)
