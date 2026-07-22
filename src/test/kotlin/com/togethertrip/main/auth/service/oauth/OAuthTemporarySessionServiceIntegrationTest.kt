package com.togethertrip.main.auth.service.oauth

import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@MainIntegrationTest
class OAuthTemporarySessionServiceIntegrationTest @Autowired constructor(
    private val service: OAuthTemporarySessionService,
    private val redisTemplate: StringRedisTemplate,
) {

    private val tokens = mutableSetOf<String>()

    @AfterEach
    fun cleanRedisKeys() {
        redisTemplate.delete(tokens.map(::key))
    }

    @Test
    fun `신규 OAuth 임시 세션은 JSON round trip과 10분 TTL을 보존한다`() {
        val token = service.create(oauthUserInfo(), existingUserId = null).also(tokens::add)

        val session = service.get(token)

        assertEquals(OAuthProvider.KAKAO, session.provider)
        assertEquals("kakao-user-1", session.providerUserId)
        assertEquals("카카오 사용자", session.nickname)
        assertEquals("https://example.com/profile.png", session.profileImageUrl)
        assertEquals(null, session.existingUserId)
        assertTrue(redisTemplate.getExpire(key(token), TimeUnit.SECONDS) in 598..600)
    }

    @Test
    fun `기존 사용자 OAuth 임시 세션은 existingUserId를 보존한다`() {
        val token = service.create(oauthUserInfo(), existingUserId = 91L).also(tokens::add)

        assertEquals(91L, service.get(token).existingUserId)
    }

    @Test
    fun `삭제한 임시 세션은 만료 오류를 반환한다`() {
        val token = service.create(oauthUserInfo(), existingUserId = null).also(tokens::add)
        service.delete(token)

        val exception = assertFailsWith<BusinessException> { service.get(token) }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_TOKEN_EXPIRED, exception.errorCode)
    }

    private fun oauthUserInfo(): OAuthUserInfo {
        return OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-user-1",
            nickname = "카카오 사용자",
            profileImageUrl = "https://example.com/profile.png",
        )
    }

    private fun key(token: String): String = "auth:oauth-temporary:$token"
}
