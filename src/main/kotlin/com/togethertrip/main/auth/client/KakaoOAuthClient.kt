package com.togethertrip.main.auth.client

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.KakaoUserInfoResponse
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class KakaoOAuthClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${auth.local-test.enabled:false}")
    private val localTestEnabled: Boolean,
) {

    private val webClient: WebClient = webClientBuilder
        .baseUrl("https://kapi.kakao.com")
        .build()

    fun getUserInfo(accessToken: String): OAuthUserInfo {
        createLocalTestUserInfo(accessToken)?.let {
            return it
        }

        val response = try {
            webClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .bodyToMono<KakaoUserInfoResponse>()
                .block()
        } catch (exception: WebClientResponseException) {
            throw BusinessException(AuthErrorCode.OAUTH_USER_INFO_FAILED)
        } ?: throw BusinessException(AuthErrorCode.OAUTH_USER_INFO_FAILED)

        return OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            providerUserId = response.id.toString(),
            nickname = response.kakaoAccount?.profile?.nickname
                ?: response.properties?.nickname,
            profileImageUrl = response.kakaoAccount?.profile?.profileImageUrl
                ?: response.properties?.profileImage,
        )
    }

    private fun createLocalTestUserInfo(accessToken: String): OAuthUserInfo? {
        if (!localTestEnabled || !accessToken.startsWith(LOCAL_TEST_TOKEN_PREFIX)) {
            return null
        }

        val localUserId = accessToken
            .removePrefix(LOCAL_TEST_TOKEN_PREFIX)
            .ifBlank { "swagger" }

        return OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            providerUserId = "local-test-$localUserId",
            nickname = "로컬 테스트 $localUserId",
            profileImageUrl = null,
        )
    }

    companion object {
        private const val LOCAL_TEST_TOKEN_PREFIX = "local-test:"
    }
}
