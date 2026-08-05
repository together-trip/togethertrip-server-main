package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.client.AppleIdentityTokenVerifier
import com.togethertrip.main.auth.client.AppleOAuthClient
import com.togethertrip.main.auth.client.KakaoOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.dto.request.AppleLoginRequest
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.request.TokenRefreshRequest
import com.togethertrip.main.auth.dto.response.AuthResponse
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.auth.service.apple.AppleTokenCipher
import com.togethertrip.main.auth.service.oauth.OAuthSignupLock
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.global.security.jwt.TokenType
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class AuthService(
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val appleIdentityTokenVerifier: AppleIdentityTokenVerifier,
    private val appleOAuthClient: AppleOAuthClient,
    private val appleTokenCipher: AppleTokenCipher,
    private val oauthAccountRepository: OAuthAccountRepository,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val oauthSignupLock: OAuthSignupLock,
    private val profileImageUrlPolicy: ProfileImageUrlPolicy,
) {

    @Transactional
    fun loginWithKakao(request: KakaoLoginRequest): AuthResponse {
        val oauthUserInfo = sanitizeOAuthUserInfo(
            kakaoOAuthClient.getUserInfo(request.accessToken)
        )

        return login(oauthUserInfo)
    }

    @Transactional
    fun loginWithApple(request: AppleLoginRequest): AuthResponse {
        val appleTokens = appleOAuthClient.exchangeAuthorizationCode(request.authorizationCode)
        val oauthUserInfo = appleIdentityTokenVerifier.verify(
            identityToken = appleTokens.idToken ?: request.identityToken,
            rawNonce = request.rawNonce,
            nickname = appleNickname(request),
        )
        val encryptedRefreshToken = appleTokens.refreshToken?.let(appleTokenCipher::encrypt)

        return login(oauthUserInfo, encryptedRefreshToken)
    }

    private fun login(
        oauthUserInfo: OAuthUserInfo,
        encryptedRefreshToken: String? = null,
    ): AuthResponse {
        return oauthSignupLock.withLock(
            provider = oauthUserInfo.provider,
            providerUserId = oauthUserInfo.providerUserId,
        ) {
            val oauthAccount = oauthAccountRepository.findByProviderAndProviderUserId(
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
            )
            val user = if (oauthAccount == null) {
                registerNewUser(oauthUserInfo, encryptedRefreshToken)
            } else {
                if (encryptedRefreshToken != null) {
                    oauthAccount.encryptedRefreshToken = encryptedRefreshToken
                }
                resolveOAuthAccountUser(oauthAccount)
            }

            userRepository.flush()
            createAuthenticatedResponse(user)
        }
    }

    @Transactional
    fun refreshToken(request: TokenRefreshRequest): TokenResponse {
        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        val claims = jwtTokenProvider.getClaims(request.refreshToken)
        if (claims.tokenType != TokenType.REFRESH) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        val user = userRepository.findById(claims.userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        val accessToken = jwtTokenProvider.createAccessToken(
            userId = user.id,
            role = user.role,
        )
        val refreshToken = jwtTokenProvider.createRefreshToken(
            userId = user.id,
            role = user.role,
        )

        if (!refreshTokenService.rotate(
                userId = user.id,
                currentRefreshToken = request.refreshToken,
                newRefreshToken = refreshToken,
            )
        ) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    @Transactional
    fun logout(userId: Long) {
        refreshTokenService.delete(userId)
    }

    private fun resolveOAuthAccountUser(oauthAccount: OAuthAccount): User {
        val user = oauthAccount.user
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }
        return user
    }

    private fun registerNewUser(
        oauthUserInfo: OAuthUserInfo,
        encryptedRefreshToken: String?,
    ): User {
        val user = userRepository.save(
            User(
                nickname = "",
                profileImageUrl = oauthUserInfo.profileImageUrl,
            )
        )

        oauthAccountRepository.save(
            OAuthAccount(
                user = user,
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
                nickname = oauthUserInfo.nickname,
                profileImageUrl = oauthUserInfo.profileImageUrl,
                encryptedRefreshToken = encryptedRefreshToken,
            )
        )
        return user
    }

    private fun issueTokens(user: User): TokenResponse {
        val accessToken = jwtTokenProvider.createAccessToken(
            userId = user.id,
            role = user.role,
        )
        val refreshToken = jwtTokenProvider.createRefreshToken(
            userId = user.id,
            role = user.role,
        )

        saveRefreshTokenAfterCommit(user.id, refreshToken)
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    private fun createAuthenticatedResponse(user: User): AuthResponse {
        val tokenResponse = issueTokens(user)
        return if (user.isProfileCompleted()) {
            AuthResponse.authenticated(tokenResponse)
        } else {
            AuthResponse.profileRequired(tokenResponse)
        }
    }

    private fun saveRefreshTokenAfterCommit(
        userId: Long,
        refreshToken: String,
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            refreshTokenService.save(userId = userId, refreshToken = refreshToken)
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    refreshTokenService.save(userId = userId, refreshToken = refreshToken)
                }
            }
        )
    }

    private fun sanitizeOAuthUserInfo(oauthUserInfo: OAuthUserInfo): OAuthUserInfo {
        return oauthUserInfo.copy(
            profileImageUrl = profileImageUrlPolicy.sanitize(oauthUserInfo.profileImageUrl)
        )
    }

    private fun appleNickname(request: AppleLoginRequest): String? {
        return listOfNotNull(
            request.familyName?.trim()?.takeIf(String::isNotBlank),
            request.givenName?.trim()?.takeIf(String::isNotBlank),
        ).joinToString(" ")
            .takeIf(String::isNotBlank)
            ?.take(MAX_OAUTH_NICKNAME_LENGTH)
    }

    companion object {
        private const val MAX_OAUTH_NICKNAME_LENGTH = 50
    }
}
