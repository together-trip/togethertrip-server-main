package com.togethertrip.main.auth.client

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.KakaoUserInfoResponse
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.ErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

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
                .bodyToMono<KakaoUserInfoResponse>()
                .block()
        } catch (exception: WebClientResponseException) {
            throw BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED)
        } ?: throw BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED)

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