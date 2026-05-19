package com.togethertrip.main.auth.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoUserInfoResponse(
    val id: Long,

    @JsonProperty("kakao_account")
    val kakaoAccount: KakaoAccount? = null,

    val properties: KakaoProperties? = null,
) {
    data class KakaoAccount(
        val email: String? = null,
        val profile: KakaoProfile? = null,
    )

    data class KakaoProfile(
        val nickname: String? = null,

        @JsonProperty("profile_image_url")
        val profileImageUrl: String? = null,
    )

    data class KakaoProperties(
        val nickname: String? = null,

        @JsonProperty("profile_image")
        val profileImage: String? = null,
    )
}