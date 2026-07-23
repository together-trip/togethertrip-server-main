package com.togethertrip.main.auth.service.oauth

import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

@Service
class OAuthTemporarySessionService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun create(
        oauthUserInfo: OAuthUserInfo,
        existingUserId: Long?,
    ): String {
        // 임시 토큰 생성
        val temporaryToken = UUID.randomUUID().toString()

        // OAuth 임시 세션 생성
        val session = OAuthTemporarySession(
            provider = oauthUserInfo.provider,
            providerUserId = oauthUserInfo.providerUserId,
            nickname = oauthUserInfo.nickname,
            profileImageUrl = oauthUserInfo.profileImageUrl,
            existingUserId = existingUserId,
        )

        // Redis 임시 세션 저장
        redisTemplate.opsForValue().set(
            getKey(temporaryToken),
            objectMapper.writeValueAsString(session),
            TEMPORARY_TOKEN_TTL,
        )

        // 임시 토큰 반환
        return temporaryToken
    }

    fun get(temporaryToken: String): OAuthTemporarySession {
        val value = redisTemplate.opsForValue().get(getKey(temporaryToken))
            ?: throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_TOKEN_EXPIRED)

        // Redis 임시 세션 역직렬화
        return objectMapper.readValue(value, OAuthTemporarySession::class.java)
    }

    fun delete(temporaryToken: String) {
        redisTemplate.delete(getKey(temporaryToken))
    }

    private fun getKey(temporaryToken: String): String {
        // OAuth 임시 세션 key
        return "auth:oauth-temporary:$temporaryToken"
    }

    companion object {
        private val TEMPORARY_TOKEN_TTL: Duration = Duration.ofMinutes(10)
    }
}
