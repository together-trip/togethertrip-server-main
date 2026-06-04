package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.client.KakaoOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.response.AuthStatus
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.auth.service.phone.PhoneVerificationService
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthServiceTest {

    private lateinit var kakaoOAuthClient: KakaoOAuthClient
    private lateinit var oauthAccountRepository: OAuthAccountRepository
    private lateinit var userRepository: UserRepository
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var temporarySessionService: OAuthTemporarySessionService
    private lateinit var phoneVerificationService: PhoneVerificationService
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        kakaoOAuthClient = mock(KakaoOAuthClient::class.java)
        oauthAccountRepository = mock(OAuthAccountRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        jwtTokenProvider = mock(JwtTokenProvider::class.java)
        refreshTokenService = mock(RefreshTokenService::class.java)
        temporarySessionService = mock(OAuthTemporarySessionService::class.java)
        phoneVerificationService = mock(PhoneVerificationService::class.java)
        authService = AuthService(
            kakaoOAuthClient = kakaoOAuthClient,
            oauthAccountRepository = oauthAccountRepository,
            userRepository = userRepository,
            jwtTokenProvider = jwtTokenProvider,
            refreshTokenService = refreshTokenService,
            temporarySessionService = temporarySessionService,
            phoneVerificationService = phoneVerificationService,
        )
    }

    @Test
    fun `탈퇴 사용자가 동일 카카오 계정으로 로그인하면 재활성화 후 전화번호 인증을 요구한다`() {
        val oauthUserInfo = OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-123",
            nickname = "여행자",
            profileImageUrl = null,
        )
        val user = User(nickname = "여행자").apply {
            id = 1L
            verifyPhoneNumber("+821012345678")
            withdraw()
        }
        val oauthAccount = OAuthAccount(
            user = user,
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-123",
        )

        `when`(kakaoOAuthClient.getUserInfo("kakao-token"))
            .thenReturn(oauthUserInfo)
        `when`(
            oauthAccountRepository.findByProviderAndProviderUserId(
                provider = OAuthProvider.KAKAO,
                providerUserId = "kakao-123",
            )
        ).thenReturn(oauthAccount)
        `when`(
            temporarySessionService.create(
                oauthUserInfo = oauthUserInfo,
                existingUserId = 1L,
            )
        ).thenReturn("temporary-token")

        val response = authService.loginWithKakao(
            KakaoLoginRequest(accessToken = "kakao-token")
        )

        assertEquals(AuthStatus.PHONE_VERIFICATION_REQUIRED, response.status)
        assertEquals("temporary-token", response.temporaryToken)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertNull(user.deletedAt)
        assertNull(user.phoneNumber)
        assertNull(user.phoneVerifiedAt)
    }
}
