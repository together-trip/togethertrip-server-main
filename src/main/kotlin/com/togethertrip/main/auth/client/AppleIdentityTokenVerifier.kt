package com.togethertrip.main.auth.client

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.service.apple.AppleNonceStore
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.bodyToMono
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class AppleIdentityTokenVerifier(
    webClientBuilder: WebClient.Builder,
    private val properties: AppleOAuthProperties,
    private val nonceStore: AppleNonceStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val webClient = AppleOAuthWebClient.build(webClientBuilder, properties)
    @Volatile private var cachedKeys: CachedKeys? = null

    fun verify(identityToken: String, rawNonce: String, nickname: String?): OAuthUserInfo {
        if (!properties.enabled || properties.clientId.isBlank()) {
            throw BusinessException(AuthErrorCode.APPLE_AUTHORIZATION_FAILED)
        }
        val signedJwt = try {
            SignedJWT.parse(identityToken)
        } catch (_: Exception) {
            invalidToken()
        }

        if (signedJwt.header.algorithm != JWSAlgorithm.RS256) invalidToken()
        val keyId = signedJwt.header.keyID ?: invalidToken()
        val rsaKey = findKey(keyId, forceRefresh = false)
            ?: findKey(keyId, forceRefresh = true)
            ?: invalidToken()

        val verified = try {
            signedJwt.verify(RSASSAVerifier(rsaKey.toRSAPublicKey()))
        } catch (_: Exception) {
            false
        }
        if (!verified) invalidToken()

        val claims = signedJwt.jwtClaimsSet
        val now = clock.instant()
        val expectedNonce = sha256(rawNonce)
        val expiresAt = claims.expirationTime?.toInstant() ?: invalidToken()
        val issuedAt = claims.issueTime?.toInstant() ?: invalidToken()
        val tokenLifetime = Duration.between(issuedAt, expiresAt)
        if (
            claims.issuer != properties.issuer ||
            properties.clientId !in claims.audience ||
            !expiresAt.isAfter(now) ||
            issuedAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS)) ||
            tokenLifetime.isZero ||
            tokenLifetime.isNegative ||
            tokenLifetime > MAX_IDENTITY_TOKEN_LIFETIME ||
            claims.getStringClaim("nonce") != expectedNonce
        ) {
            invalidToken()
        }

        val subject = claims.subject?.takeIf { it.isNotBlank() } ?: invalidToken()
        val replayKey = claims.jwtid?.takeIf { it.isNotBlank() }
            ?: "$subject:$expectedNonce"
        val replayTtl = Duration.between(now, expiresAt)
        if (!nonceStore.claim(replayKey, replayTtl)) {
            throw BusinessException(AuthErrorCode.APPLE_NONCE_REUSED)
        }

        return OAuthUserInfo(
            provider = OAuthProvider.APPLE,
            providerUserId = subject,
            nickname = nickname,
            profileImageUrl = null,
        )
    }

    private fun findKey(keyId: String, forceRefresh: Boolean): RSAKey? {
        val now = clock.instant()
        val cached = cachedKeys
        val keys = if (!forceRefresh && cached != null && cached.expiresAt.isAfter(now)) {
            cached.jwkSet
        } else {
            loadKeys(now)
        }
        return keys.getKeyByKeyId(keyId) as? RSAKey
    }

    @Synchronized
    private fun loadKeys(now: Instant): JWKSet {
        val body = try {
            webClient.get()
                .uri(properties.jwksUrl)
                .retrieve()
                .bodyToMono<String>()
                .timeout(properties.responseTimeout)
                .block()
        } catch (exception: Exception) {
            if (exception is WebClientException || AppleOAuthWebClient.isTimeout(exception)) {
                null
            } else {
                throw exception
            }
        }
        val jwkSet = try {
            body?.let(JWKSet::parse)
        } catch (_: Exception) {
            null
        } ?: throw BusinessException(AuthErrorCode.APPLE_AUTHORIZATION_FAILED)

        cachedKeys = CachedKeys(jwkSet, now.plus(KEY_CACHE_TTL))
        return jwkSet
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun invalidToken(): Nothing {
        throw BusinessException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN)
    }

    private data class CachedKeys(
        val jwkSet: JWKSet,
        val expiresAt: Instant,
    )

    companion object {
        private const val CLOCK_SKEW_SECONDS = 60L
        private val KEY_CACHE_TTL = Duration.ofHours(1)
        private val MAX_IDENTITY_TOKEN_LIFETIME = Duration.ofMinutes(10)
    }
}
