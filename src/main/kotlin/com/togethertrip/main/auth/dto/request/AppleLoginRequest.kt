package com.togethertrip.main.auth.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AppleLoginRequest(
    @field:NotBlank
    @field:Schema(description = "Apple authorization code")
    val authorizationCode: String,

    @field:NotBlank
    @field:Schema(description = "Apple identity token")
    val identityToken: String,

    @field:NotBlank
    @field:Size(min = 32, max = 128)
    @field:Schema(description = "Apple 요청에 SHA-256 해시로 전달한 원본 nonce")
    val rawNonce: String,

    @field:Size(max = 100)
    @field:Schema(description = "최초 Apple 승인에서만 제공되는 이름")
    val givenName: String? = null,

    @field:Size(max = 100)
    @field:Schema(description = "최초 Apple 승인에서만 제공되는 성")
    val familyName: String? = null,
)
