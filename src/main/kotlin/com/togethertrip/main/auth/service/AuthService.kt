package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.client.KakaoOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.dto.response.AuthResponse
import com.togethertrip.main.auth.dto.request.ConfirmPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.dto.response.PhoneVerificationCodeSentResponse
import com.togethertrip.main.auth.dto.request.RequestPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.TokenRefreshRequest
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.auth.service.phone.PhoneVerificationService
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.global.security.jwt.TokenType
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val oauthAccountRepository: OAuthAccountRepository,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val temporarySessionService: OAuthTemporarySessionService,
    private val phoneVerificationService: PhoneVerificationService,
) {

    @Transactional
    fun loginWithKakao(request: KakaoLoginRequest): AuthResponse {
        val oauthUserInfo = kakaoOAuthClient.getUserInfo(request.accessToken)

        val oauthAccount = oauthAccountRepository
            .findByProviderAndProviderUserId(
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
            )

        if (oauthAccount != null) {
            val user = loginExistingUser(oauthAccount)

            if (user.phoneVerifiedAt != null) {
                return createAuthenticatedResponse(user)
            }

            return AuthResponse.phoneVerificationRequired(
                temporarySessionService.create(
                    oauthUserInfo = oauthUserInfo,
                    existingUserId = user.id,
                )
            )
        }

        return AuthResponse.phoneVerificationRequired(
            temporarySessionService.create(
                oauthUserInfo = oauthUserInfo,
                existingUserId = null,
            )
        )
    }

    fun requestPhoneVerification(
        request: RequestPhoneVerificationRequest,
    ): PhoneVerificationCodeSentResponse {
        return phoneVerificationService.requestCode(request)
    }

    @Transactional
    fun confirmPhoneVerification(
        request: ConfirmPhoneVerificationRequest,
    ): AuthResponse {
        val confirmedPhoneVerification = phoneVerificationService.confirmCode(request)
        val session = confirmedPhoneVerification.session
        val user = if (session.existingUserId != null) {
            val existingUser = userRepository.findByIdAndDeletedAtIsNull(session.existingUserId)
                ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

            if (existingUser.status != UserStatus.ACTIVE) {
                throw BusinessException(UserErrorCode.INACTIVE_USER)
            }

            existingUser.verifyPhoneNumber(confirmedPhoneVerification.phoneNumber)
            existingUser
        } else {
            registerNewUser(
                oauthUserInfo = OAuthUserInfo(
                    provider = session.provider,
                    providerUserId = session.providerUserId,
                    nickname = session.nickname,
                    profileImageUrl = session.profileImageUrl,
                ),
                phoneNumber = confirmedPhoneVerification.phoneNumber,
            )
        }

        phoneVerificationService.deleteTemporarySession(request.temporaryToken)

        return createAuthenticatedResponse(user)
    }

    @Transactional(readOnly = true)
    fun refreshToken(request: TokenRefreshRequest): TokenResponse {
        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        val claims = jwtTokenProvider.getClaims(request.refreshToken)

        if (claims.tokenType != TokenType.REFRESH) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        if (!refreshTokenService.matches(
                userId = claims.userId,
                refreshToken = request.refreshToken,
            )
        ) {
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

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = request.refreshToken,
        )
    }

    @Transactional
    fun logout(userId: Long) {
        refreshTokenService.delete(userId)
    }

    private fun loginExistingUser(oauthAccount: OAuthAccount): User {
        val user = oauthAccount.user

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }

    private fun registerNewUser(
        oauthUserInfo: OAuthUserInfo,
        phoneNumber: String,
    ): User {
        val user = userRepository.save(
            User(
                nickname = oauthUserInfo.nickname ?: "카카오 사용자",
                profileImageUrl = oauthUserInfo.profileImageUrl,
            ).apply {
                verifyPhoneNumber(phoneNumber)
            }
        )

        oauthAccountRepository.save(
            OAuthAccount(
                user = user,
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
                nickname = oauthUserInfo.nickname,
                profileImageUrl = oauthUserInfo.profileImageUrl,
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

        refreshTokenService.save(
            userId = user.id,
            refreshToken = refreshToken,
        )

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    private fun createAuthenticatedResponse(user: User): AuthResponse {
        val tokenResponse = issueTokens(user)
        if (!user.isProfileCompleted()) {
            return AuthResponse.profileRequired(tokenResponse)
        }

        return AuthResponse.authenticated(tokenResponse)
    }
}
