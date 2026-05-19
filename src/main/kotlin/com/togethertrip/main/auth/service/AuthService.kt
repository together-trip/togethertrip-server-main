package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.client.KakaoOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.dto.KakaoLoginRequest
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.dto.TokenRefreshRequest
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.ErrorCode
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
) {

    @Transactional
    fun loginWithKakao(request: KakaoLoginRequest): TokenResponse {
        val oauthUserInfo = kakaoOAuthClient.getUserInfo(request.accessToken)

        val oauthAccount = oauthAccountRepository
            .findByProviderAndProviderUserId(
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
            )

        val user = if (oauthAccount != null) {
            loginExistingUser(oauthAccount)
        } else {
            registerNewUser(oauthUserInfo)
        }

        return issueTokens(user)
    }

    @Transactional(readOnly = true)
    fun refreshToken(request: TokenRefreshRequest): TokenResponse {
        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        val claims = jwtTokenProvider.getClaims(request.refreshToken)

        if (claims.tokenType != TokenType.REFRESH) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        if (!refreshTokenService.matches(
                userId = claims.userId,
                refreshToken = request.refreshToken,
            )
        ) {
            throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        val user = userRepository.findById(claims.userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(ErrorCode.INACTIVE_USER)
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
            throw IllegalStateException("활성 상태의 사용자가 아닙니다.")
        }

        return user
    }

    private fun registerNewUser(
        oauthUserInfo: OAuthUserInfo,
    ): User {
        val user = userRepository.save(
            User(
                email = oauthUserInfo.email,
                nickname = oauthUserInfo.nickname ?: "카카오 사용자",
                profileImageUrl = oauthUserInfo.profileImageUrl,
            )
        )

        oauthAccountRepository.save(
            OAuthAccount(
                user = user,
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
                email = oauthUserInfo.email,
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
}