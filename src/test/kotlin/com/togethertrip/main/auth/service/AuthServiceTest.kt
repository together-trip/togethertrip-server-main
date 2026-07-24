package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.client.KakaoOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.response.AuthStatus
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.auth.service.oauth.OAuthSignupLock
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthServiceTest {

    private lateinit var kakaoOAuthClient: KakaoOAuthClient
    private lateinit var oauthAccountRepository: OAuthAccountRepository
    private lateinit var userRepository: UserRepository
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var signupLock: CapturingOAuthSignupLock
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        kakaoOAuthClient = mock(KakaoOAuthClient::class.java)
        oauthAccountRepository = mock(OAuthAccountRepository::class.java)
        userRepository = mock(UserRepository::class.java) { invocation ->
            if (invocation.method.name == "save") {
                (invocation.arguments[0] as User).apply { id = 1L }
            } else {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
        jwtTokenProvider = mock(JwtTokenProvider::class.java)
        refreshTokenService = mock(RefreshTokenService::class.java)
        signupLock = CapturingOAuthSignupLock()
        authService = AuthService(
            kakaoOAuthClient = kakaoOAuthClient,
            oauthAccountRepository = oauthAccountRepository,
            userRepository = userRepository,
            jwtTokenProvider = jwtTokenProvider,
            refreshTokenService = refreshTokenService,
            oauthSignupLock = signupLock,
            profileImageUrlPolicy = ProfileImageUrlPolicy(
                userProfileImagePublicUrlPrefix = "/uploads/user-profile-images",
            ),
        )
    }

    @Test
    fun `신규 카카오 사용자를 즉시 생성하고 프로필 입력 필요 토큰을 반환한다`() {
        stubKakaoUser(
            nickname = "카카오 닉네임",
            profileImageUrl = "https://profile.kakaocdn.net/image.jpg",
        )
        stubTokens(userId = 1L)

        val response = authService.loginWithKakao(KakaoLoginRequest("kakao-token"))

        assertEquals(AuthStatus.PROFILE_REQUIRED, response.status)
        assertEquals("access-token", response.accessToken)
        assertEquals("refresh-token", response.refreshToken)
        assertEquals(OAuthProvider.KAKAO, signupLock.provider)
        assertEquals("kakao-123", signupLock.providerUserId)

        val savedUser = savedUser()
        assertEquals("", savedUser.nickname)
        assertEquals("https://profile.kakaocdn.net/image.jpg", savedUser.profileImageUrl)
        val savedAccount = savedOAuthAccount()
        assertEquals(savedUser, savedAccount.user)
        assertEquals("카카오 닉네임", savedAccount.nickname)
        verify(refreshTokenService).save(1L, "refresh-token")
    }

    @Test
    fun `허용되지 않은 OAuth 프로필 이미지 URL은 저장하지 않는다`() {
        stubKakaoUser(profileImageUrl = "http://example.com/profile.jpg")
        stubTokens(userId = 1L)

        authService.loginWithKakao(KakaoLoginRequest("kakao-token"))

        assertNull(savedUser().profileImageUrl)
        assertNull(savedOAuthAccount().profileImageUrl)
    }

    @Test
    fun `프로필을 완료한 기존 사용자는 인증 완료를 반환한다`() {
        val user = existingUser(nickname = "여행자")
        stubExistingAccount(user)
        stubKakaoUser()
        stubTokens(userId = user.id)

        val response = authService.loginWithKakao(KakaoLoginRequest("kakao-token"))

        assertEquals(AuthStatus.AUTHENTICATED, response.status)
        verify(refreshTokenService).save(user.id, "refresh-token")
    }

    @Test
    fun `프로필이 비어 있는 기존 사용자는 프로필 입력 필요를 반환한다`() {
        val user = existingUser(nickname = "")
        stubExistingAccount(user)
        stubKakaoUser()
        stubTokens(userId = user.id)

        val response = authService.loginWithKakao(KakaoLoginRequest("kakao-token"))

        assertEquals(AuthStatus.PROFILE_REQUIRED, response.status)
    }

    @Test
    fun `탈퇴 사용자가 동일 카카오 계정으로 로그인하면 즉시 재활성화한다`() {
        val user = existingUser(nickname = "여행자").apply { withdraw() }
        stubExistingAccount(user)
        stubKakaoUser()
        stubTokens(userId = user.id)

        val response = authService.loginWithKakao(KakaoLoginRequest("kakao-token"))

        assertEquals(AuthStatus.AUTHENTICATED, response.status)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertNull(user.deletedAt)
    }

    @Test
    fun `정지 사용자는 로그인할 수 없다`() {
        val user = existingUser(nickname = "여행자", status = UserStatus.SUSPENDED)
        stubExistingAccount(user)
        stubKakaoUser()

        val exception = assertFailsWith<BusinessException> {
            authService.loginWithKakao(KakaoLoginRequest("kakao-token"))
        }

        assertEquals(UserErrorCode.INACTIVE_USER, exception.errorCode)
    }

    private fun stubKakaoUser(
        nickname: String? = "여행자",
        profileImageUrl: String? = null,
    ) {
        `when`(kakaoOAuthClient.getUserInfo("kakao-token")).thenReturn(
            OAuthUserInfo(
                provider = OAuthProvider.KAKAO,
                providerUserId = "kakao-123",
                nickname = nickname,
                profileImageUrl = profileImageUrl,
            )
        )
    }

    private fun stubExistingAccount(user: User) {
        `when`(
            oauthAccountRepository.findByProviderAndProviderUserId(
                OAuthProvider.KAKAO,
                "kakao-123",
            )
        ).thenReturn(
            OAuthAccount(
                user = user,
                provider = OAuthProvider.KAKAO,
                providerUserId = "kakao-123",
            )
        )
    }

    private fun stubTokens(userId: Long) {
        `when`(jwtTokenProvider.createAccessToken(userId, UserRole.USER))
            .thenReturn("access-token")
        `when`(jwtTokenProvider.createRefreshToken(userId, UserRole.USER))
            .thenReturn("refresh-token")
    }

    private fun existingUser(
        nickname: String,
        status: UserStatus = UserStatus.ACTIVE,
    ): User = User(nickname = nickname, status = status).apply { id = 7L }

    private fun savedUser(): User {
        val invocation = org.mockito.Mockito.mockingDetails(userRepository).invocations
            .last { it.method.name == "save" }
        return invocation.arguments[0] as User
    }

    private fun savedOAuthAccount(): OAuthAccount {
        val invocation = org.mockito.Mockito.mockingDetails(oauthAccountRepository).invocations
            .last { it.method.name == "save" }
        return invocation.arguments[0] as OAuthAccount
    }

    private class CapturingOAuthSignupLock : OAuthSignupLock {
        var provider: OAuthProvider? = null
        var providerUserId: String? = null

        override fun <T> withLock(
            provider: OAuthProvider,
            providerUserId: String,
            block: () -> T,
        ): T {
            this.provider = provider
            this.providerUserId = providerUserId
            return block()
        }
    }
}
