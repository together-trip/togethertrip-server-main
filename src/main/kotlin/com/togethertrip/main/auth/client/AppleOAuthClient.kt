package com.togethertrip.main.auth.client

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.togethertrip.main.auth.dto.AppleTokenResponse
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.bodyToMono
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date

@Component
class AppleOAuthClient(
    webClientBuilder: WebClient.Builder,
    private val properties: AppleOAuthProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val webClient = AppleOAuthWebClient.build(webClientBuilder, properties)

    fun exchangeAuthorizationCode(authorizationCode: String): AppleTokenResponse {
        ensureEnabled()
        return try {
            webClient.post()
                .uri(properties.tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    BodyInserters.fromFormData("client_id", properties.clientId)
                        .with("client_secret", createClientSecret())
                        .with("code", authorizationCode)
                        .with("grant_type", "authorization_code")
                )
                .retrieve()
                .bodyToMono<AppleTokenResponse>()
                .timeout(properties.responseTimeout)
                .block()
                ?: authorizationFailed()
        } catch (exception: Exception) {
            handleClientFailure(exception)
        }
    }

    fun revoke(refreshToken: String) {
        ensureEnabled()
        try {
            webClient.post()
                .uri(properties.revokeUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    BodyInserters.fromFormData("client_id", properties.clientId)
                        .with("client_secret", createClientSecret())
                        .with("token", refreshToken)
                        .with("token_type_hint", "refresh_token")
                )
                .retrieve()
                .toBodilessEntity()
                .timeout(properties.responseTimeout)
                .block()
        } catch (exception: Exception) {
            handleClientFailure(exception)
        }
    }

    private fun createClientSecret(): String {
        val now = clock.instant()
        val claims = JWTClaimsSet.Builder()
            .issuer(properties.teamId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(CLIENT_SECRET_TTL)))
            .audience(properties.issuer)
            .subject(properties.clientId)
            .build()
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId).build(),
            claims,
        )
        try {
            jwt.sign(ECDSASigner(parsePrivateKey()))
        } catch (_: Exception) {
            authorizationFailed()
        }
        return jwt.serialize()
    }

    private fun parsePrivateKey(): ECPrivateKey {
        val encoded = properties.privateKey
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
            .let(Base64.getDecoder()::decode)
        return KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(encoded)) as ECPrivateKey
    }

    private fun ensureEnabled() {
        if (
            !properties.enabled ||
            listOf(
                properties.clientId,
                properties.teamId,
                properties.keyId,
                properties.privateKey,
                properties.tokenEncryptionKey,
            ).any(String::isBlank)
        ) {
            authorizationFailed()
        }
    }

    private fun authorizationFailed(): Nothing {
        throw BusinessException(AuthErrorCode.APPLE_AUTHORIZATION_FAILED)
    }

    private fun handleClientFailure(exception: Exception): Nothing {
        if (exception is WebClientException || AppleOAuthWebClient.isTimeout(exception)) {
            authorizationFailed()
        }
        throw exception
    }

    companion object {
        private val CLIENT_SECRET_TTL = Duration.ofDays(150)
    }
}
