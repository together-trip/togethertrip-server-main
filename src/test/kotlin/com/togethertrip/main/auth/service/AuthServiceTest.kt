package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.client.KakaoOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.dto.request.ConfirmPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.response.AuthStatus
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.auth.service.oauth.OAuthSignupLock
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySession
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.auth.service.phone.ConfirmedPhoneVerification
import com.togethertrip.main.auth.service.phone.PhoneVerificationService
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private lateinit var temporarySessionService: OAuthTemporarySessionService
    private lateinit var phoneVerificationService: PhoneVerificationService
    private lateinit var oauthSignupLock: OAuthSignupLock
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
        oauthSignupLock = object : OAuthSignupLock {
            override fun <T> withLock(
                session: OAuthTemporarySession,
                block: () -> T,
            ): T = block()
        }
        authService = AuthService(
            kakaoOAuthClient = kakaoOAuthClient,
            oauthAccountRepository = oauthAccountRepository,
            userRepository = userRepository,
            jwtTokenProvider = jwtTokenProvider,
            refreshTokenService = refreshTokenService,
            temporarySessionService = temporarySessionService,
            phoneVerificationService = phoneVerificationService,
            oauthSignupLock = oauthSignupLock,
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

    @Test
    fun `다른 세션에서 이미 회원가입을 완료하면 stale 임시 토큰의 인증 완료를 거부한다`() {
        val session = OAuthTemporarySession(
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-123",
            nickname = "여행자",
            profileImageUrl = null,
            existingUserId = 1L,
        )
        val user = User(nickname = "여행자").apply {
            id = 1L
            verifyPhoneNumber("+821011112222")
        }
        val oauthAccount = OAuthAccount(
            user = user,
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-123",
        )

        `when`(temporarySessionService.get("stale-token"))
            .thenReturn(session)
        `when`(
            oauthAccountRepository.findByProviderAndProviderUserId(
                provider = OAuthProvider.KAKAO,
                providerUserId = "kakao-123",
            )
        ).thenReturn(oauthAccount)

        val exception = assertFailsWith<BusinessException> {
            authService.confirmPhoneVerification(
                ConfirmPhoneVerificationRequest(
                    temporaryToken = "stale-token",
                    phoneNumber = "010-3333-4444",
                    code = "123456",
                )
            )
        }

        assertEquals(AuthErrorCode.SIGNUP_ALREADY_COMPLETED, exception.errorCode)
        verify(phoneVerificationService).deleteTemporarySession("stale-token")
    }

    @Test
    fun `재활성화 세션은 전화번호 인증 후 기존 사용자에 전화번호를 저장한다`() {
        val session = OAuthTemporarySession(
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-123",
            nickname = "여행자",
            profileImageUrl = null,
            existingUserId = 1L,
        )
        val user = User(nickname = "여행자").apply {
            id = 1L
        }

        `when`(temporarySessionService.get("temporary-token"))
            .thenReturn(session)
        `when`(
            oauthAccountRepository.findByProviderAndProviderUserId(
                provider = OAuthProvider.KAKAO,
                providerUserId = "kakao-123",
            )
        ).thenReturn(null)
        `when`(phoneVerificationService.confirmCode(
            ConfirmPhoneVerificationRequest(
                temporaryToken = "temporary-token",
                phoneNumber = "010-3333-4444",
                code = "123456",
            )
        )).thenReturn(
            ConfirmedPhoneVerification(
                session = session,
                phoneNumber = "+821033334444",
            )
        )
        `when`(userRepository.findLockedByIdIncludingDeleted(1L))
            .thenReturn(user)
        `when`(
            userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull(
                phoneNumber = "+821033334444",
                id = 1L,
            )
        ).thenReturn(false)
        `when`(jwtTokenProvider.createAccessToken(userId = 1L, role = user.role))
            .thenReturn("access-token")
        `when`(jwtTokenProvider.createRefreshToken(userId = 1L, role = user.role))
            .thenReturn("refresh-token")

        val response = authService.confirmPhoneVerification(
            ConfirmPhoneVerificationRequest(
                temporaryToken = "temporary-token",
                phoneNumber = "010-3333-4444",
                code = "123456",
            )
        )

        assertEquals(AuthStatus.PROFILE_REQUIRED, response.status)
        assertEquals("+821033334444", user.phoneNumber)
        verify(phoneVerificationService).deleteTemporarySession("temporary-token")
        verify(refreshTokenService).save(
            userId = 1L,
            refreshToken = "refresh-token",
        )
    }

    @Test
    fun `탈퇴 사용자 임시 세션은 인증 확인 단계에서도 재활성화 후 전화번호를 저장한다`() {
        val session = OAuthTemporarySession(
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-123",
            nickname = "여행자",
            profileImageUrl = null,
            existingUserId = 1L,
        )
        val user = User(nickname = "여행자").apply {
            id = 1L
            verifyPhoneNumber("+821011112222")
            withdraw()
        }

        `when`(temporarySessionService.get("temporary-token"))
            .thenReturn(session)
        `when`(
            oauthAccountRepository.findByProviderAndProviderUserId(
                provider = OAuthProvider.KAKAO,
                providerUserId = "kakao-123",
            )
        ).thenReturn(null)
        `when`(phoneVerificationService.confirmCode(
            ConfirmPhoneVerificationRequest(
                temporaryToken = "temporary-token",
                phoneNumber = "010-3333-4444",
                code = "123456",
            )
        )).thenReturn(
            ConfirmedPhoneVerification(
                session = session,
                phoneNumber = "+821033334444",
            )
        )
        `when`(userRepository.findLockedByIdIncludingDeleted(1L))
            .thenReturn(user)
        `when`(
            userRepository.existsByPhoneNumberAndIdNotAndDeletedAtIsNull(
                phoneNumber = "+821033334444",
                id = 1L,
            )
        ).thenReturn(false)
        `when`(jwtTokenProvider.createAccessToken(userId = 1L, role = user.role))
            .thenReturn("access-token")
        `when`(jwtTokenProvider.createRefreshToken(userId = 1L, role = user.role))
            .thenReturn("refresh-token")

        val response = authService.confirmPhoneVerification(
            ConfirmPhoneVerificationRequest(
                temporaryToken = "temporary-token",
                phoneNumber = "010-3333-4444",
                code = "123456",
            )
        )

        assertEquals(AuthStatus.PROFILE_REQUIRED, response.status)
        assertEquals(UserStatus.ACTIVE, user.status)
        assertNull(user.deletedAt)
        assertEquals("+821033334444", user.phoneNumber)
        verify(phoneVerificationService).deleteTemporarySession("temporary-token")
    }

    @Test
    fun `임시 세션의 기존 사용자 id가 DB에 없으면 세션 만료로 거부한다`() {
        val session = OAuthTemporarySession(
            provider = OAuthProvider.KAKAO,
            providerUserId = "kakao-123",
            nickname = "여행자",
            profileImageUrl = null,
            existingUserId = 1L,
        )

        `when`(temporarySessionService.get("temporary-token"))
            .thenReturn(session)
        `when`(
            oauthAccountRepository.findByProviderAndProviderUserId(
                provider = OAuthProvider.KAKAO,
                providerUserId = "kakao-123",
            )
        ).thenReturn(null)
        `when`(phoneVerificationService.confirmCode(
            ConfirmPhoneVerificationRequest(
                temporaryToken = "temporary-token",
                phoneNumber = "010-3333-4444",
                code = "123456",
            )
        )).thenReturn(
            ConfirmedPhoneVerification(
                session = session,
                phoneNumber = "+821033334444",
            )
        )
        `when`(userRepository.findLockedByIdIncludingDeleted(1L))
            .thenReturn(null)

        val exception = assertFailsWith<BusinessException> {
            authService.confirmPhoneVerification(
                ConfirmPhoneVerificationRequest(
                    temporaryToken = "temporary-token",
                    phoneNumber = "010-3333-4444",
                    code = "123456",
                )
            )
        }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_TOKEN_EXPIRED, exception.errorCode)
        verify(phoneVerificationService).deleteTemporarySession("temporary-token")
    }
}
