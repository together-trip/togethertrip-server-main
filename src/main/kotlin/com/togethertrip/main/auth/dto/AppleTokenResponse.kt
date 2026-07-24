package com.togethertrip.main.auth.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class AppleTokenResponse(
    @field:JsonProperty("access_token")
    val accessToken: String? = null,
    @field:JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @field:JsonProperty("id_token")
    val idToken: String? = null,
)
