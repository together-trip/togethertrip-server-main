package com.togethertrip.main.auth.client

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.KakaoUserInfoResponse
import com.togethertrip.main.auth.dto.OAuthUserInfo
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class KakaoOAuthClient(
    private val webClientBuilder: WebClient.Builder,
) {

    private val webClient: WebClient = webClientBuilder
        .baseUrl("https://kapi.kakao.com")
        .build()

    fun getUserInfo(accessToken: String): OAuthUserInfo {
        val response = try {
            webClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .bodyToMono(KakaoUserInfoResponse::class.java)
                .block()
        } catch (exception: WebClientResponseException) {
            throw IllegalArgumentException("카카오 사용자 정보 조회에 실패했습니다.")
        } ?: throw IllegalArgumentException("카카오 사용자 정보 응답이 비어 있습니다.")

        return OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            providerUserId = response.id.toString(),
            email = response.kakaoAccount?.email,
            nickname = response.kakaoAccount?.profile?.nickname
                ?: response.properties?.nickname,
            profileImageUrl = response.kakaoAccount?.profile?.profileImageUrl
                ?: response.properties?.profileImage,
        )
    }
}