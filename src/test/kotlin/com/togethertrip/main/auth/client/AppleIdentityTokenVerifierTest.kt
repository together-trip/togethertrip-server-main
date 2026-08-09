package com.togethertrip.main.auth.client

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.service.apple.AppleNonceStore
import com.togethertrip.main.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppleIdentityTokenVerifierTest {
    private val now = Instant.parse("2026-07-24T00:00:00Z")
    private val key = RSAKeyGenerator(2048).keyID("apple-key").generate()

    @Test
    fun `유효한 Apple identity token을 검증하고 공개키를 캐시한다`() {
        val jwksCalls = AtomicInteger()
        val nonceStore = RecordingNonceStore()
        val verifier = verifier(key, nonceStore) {
            jwksCalls.incrementAndGet()
        }

        val first = verifier.verify(token(key, "nonce-a", "jwt-a"), "nonce-a", "주 재완")
        val second = verifier.verify(token(key, "nonce-b", "jwt-b"), "nonce-b", null)

        assertEquals(OAuthProvider.APPLE, first.provider)
        assertEquals("apple-user", first.providerUserId)
        assertEquals("주 재완", first.nickname)
        assertEquals("apple-user", second.providerUserId)
        assertEquals(1, jwksCalls.get())
    }

    @Test
    fun `다른 키로 위조된 identity token을 거부한다`() {
        val verifier = verifier(key, RecordingNonceStore())
        val forgedKey = RSAKeyGenerator(2048).keyID("apple-key").generate()

        assertInvalidToken { verifier.verify(token(forgedKey, "nonce-a", "jwt-a"), "nonce-a", null) }
    }

    @Test
    fun `nonce 불일치와 만료 token을 거부한다`() {
        val verifier = verifier(key, RecordingNonceStore())

        assertInvalidToken { verifier.verify(token(key, "nonce-a", "jwt-a"), "nonce-b", null) }
        assertInvalidToken {
            verifier.verify(
                token(key, "nonce-c", "jwt-c", expiresAt = now.minusSeconds(1)),
                "nonce-c",
                null,
            )
        }
    }

    @Test
    fun `issuer audience 발급시각 subject가 올바르지 않으면 거부한다`() {
        val verifier = verifier(key, RecordingNonceStore())

        assertInvalidToken {
            verifier.verify(token(key, "nonce-a", "jwt-a", issuer = "https://attacker.test"), "nonce-a", null)
        }
        assertInvalidToken {
            verifier.verify(token(key, "nonce-b", "jwt-b", audience = "wrong-client"), "nonce-b", null)
        }
        assertInvalidToken {
            verifier.verify(token(key, "nonce-c", "jwt-c", issuedAt = now.plusSeconds(61)), "nonce-c", null)
        }
        assertInvalidToken {
            verifier.verify(token(key, "nonce-d", "jwt-d", subject = null), "nonce-d", null)
        }
    }

    @Test
    fun `파싱할 수 없거나 kid가 없는 token을 거부한다`() {
        val verifier = verifier(key, RecordingNonceStore())

        assertInvalidToken { verifier.verify("not-a-jwt", "nonce-a", null) }
        assertInvalidToken {
            verifier.verify(token(key, "nonce-b", "jwt-b", includeKeyId = false), "nonce-b", null)
        }
    }

    @Test
    fun `설정이 비활성화되면 공개키 요청 전에 실패한다`() {
        val verifier = AppleIdentityTokenVerifier(
            webClientBuilder = WebClient.builder(),
            properties = AppleOAuthProperties(enabled = false),
            nonceStore = RecordingNonceStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        val exception = assertFailsWith<BusinessException> {
            verifier.verify("not-a-jwt", "nonce-a", null)
        }

        assertEquals(AuthErrorCode.APPLE_AUTHORIZATION_FAILED, exception.errorCode)
    }

    @Test
    fun `token kid가 공개키에 없으면 키를 갱신한 뒤 거부한다`() {
        val jwksCalls = AtomicInteger()
        val verifier = verifier(key, RecordingNonceStore()) { jwksCalls.incrementAndGet() }
        val unknownKey = RSAKeyGenerator(2048).keyID("unknown-key").generate()

        assertInvalidToken {
            verifier.verify(token(unknownKey, "nonce-a", "jwt-a"), "nonce-a", null)
        }
        assertEquals(2, jwksCalls.get())
    }

    @Test
    fun `Apple 공개키 응답이 손상되면 인증 서버 장애로 처리한다`() {
        val exchange = ExchangeFunction {
            Mono.just(ClientResponse.create(HttpStatus.OK).body("not-jwks").build())
        }
        val verifier = AppleIdentityTokenVerifier(
            webClientBuilder = WebClient.builder().exchangeFunction(exchange),
            properties = AppleOAuthProperties(enabled = true, clientId = "com.togethertrip.app"),
            nonceStore = RecordingNonceStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        val exception = assertFailsWith<BusinessException> {
            verifier.verify(token(key, "nonce-a", "jwt-a"), "nonce-a", null)
        }

        assertEquals(AuthErrorCode.APPLE_AUTHORIZATION_FAILED, exception.errorCode)
    }

    @Test
    fun `Apple 공개키 응답이 timeout되면 인증 서버 장애로 처리한다`() {
        val verifier = AppleIdentityTokenVerifier(
            webClientBuilder = WebClient.builder().exchangeFunction { Mono.never() },
            properties = AppleOAuthProperties(
                enabled = true,
                clientId = "com.togethertrip.app",
                responseTimeout = Duration.ofMillis(10),
            ),
            nonceStore = RecordingNonceStore(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        val exception = assertFailsWith<BusinessException> {
            verifier.verify(token(key, "nonce-a", "jwt-a"), "nonce-a", null)
        }

        assertEquals(AuthErrorCode.APPLE_AUTHORIZATION_FAILED, exception.errorCode)
    }

    @Test
    fun `허용 범위보다 수명이 긴 identity token을 거부한다`() {
        val verifier = verifier(key, RecordingNonceStore())

        assertInvalidToken {
            verifier.verify(
                token(
                    signingKey = key,
                    rawNonce = "nonce-a",
                    jwtId = "jwt-a",
                    expiresAt = now.plusSeconds(601),
                ),
                "nonce-a",
                null,
            )
        }
    }

    @Test
    fun `replay 방지 key를 identity token 만료까지 유지한다`() {
        val nonceStore = RecordingNonceStore()
        val verifier = verifier(key, nonceStore)

        verifier.verify(
            token(
                signingKey = key,
                rawNonce = "nonce-a",
                jwtId = "jwt-a",
                issuedAt = now.plusSeconds(30),
                expiresAt = now.plusSeconds(630),
            ),
            "nonce-a",
            null,
        )

        assertEquals(listOf(Duration.ofSeconds(630)), nonceStore.claimedTtls)
    }

    @Test
    fun `같은 identity token 재사용을 거부한다`() {
        val verifier = verifier(key, RecordingNonceStore())
        val identityToken = token(key, "nonce-a", "jwt-a")
        verifier.verify(identityToken, "nonce-a", null)

        val exception = assertFailsWith<BusinessException> {
            verifier.verify(identityToken, "nonce-a", null)
        }

        assertEquals(AuthErrorCode.APPLE_NONCE_REUSED, exception.errorCode)
    }

    private fun verifier(
        jwk: RSAKey,
        nonceStore: AppleNonceStore,
        onJwksRequest: () -> Unit = {},
    ): AppleIdentityTokenVerifier {
        val exchange = ExchangeFunction {
            onJwksRequest()
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .body("{\"keys\":[${jwk.toPublicJWK()}]}")
                    .build()
            )
        }
        return AppleIdentityTokenVerifier(
            webClientBuilder = WebClient.builder().exchangeFunction(exchange),
            properties = AppleOAuthProperties(
                enabled = true,
                clientId = "com.togethertrip.app",
            ),
            nonceStore = nonceStore,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    private fun token(
        signingKey: RSAKey,
        rawNonce: String,
        jwtId: String,
        expiresAt: Instant = now.plusSeconds(300),
        issuer: String = "https://appleid.apple.com",
        audience: String = "com.togethertrip.app",
        issuedAt: Instant = now,
        subject: String? = "apple-user",
        includeKeyId: Boolean = true,
    ): String {
        val claimsBuilder = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .jwtID(jwtId)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .claim("nonce", sha256(rawNonce))
        if (subject != null) claimsBuilder.subject(subject)
        val claims = claimsBuilder.build()
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).apply {
                if (includeKeyId) keyID(signingKey.keyID)
            }.build(),
            claims,
        ).apply { sign(RSASSASigner(signingKey)) }.serialize()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun assertInvalidToken(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN, exception.errorCode)
    }

    private class RecordingNonceStore : AppleNonceStore {
        private val claimed = mutableSetOf<String>()
        val claimedTtls = mutableListOf<Duration>()

        override fun claim(nonce: String, ttl: Duration): Boolean {
            claimedTtls += ttl
            return claimed.add(nonce)
        }
    }
}
