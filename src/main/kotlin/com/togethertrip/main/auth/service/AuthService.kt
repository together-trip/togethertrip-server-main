package com.togethertrip.main.auth.service

import com.togethertrip.main.auth.client.KakaoOAuthClient
import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.dto.OAuthUserInfo
import com.togethertrip.main.auth.dto.TokenResponse
import com.togethertrip.main.auth.dto.request.ConfirmPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.KakaoLoginRequest
import com.togethertrip.main.auth.dto.request.RequestPhoneVerificationRequest
import com.togethertrip.main.auth.dto.request.TokenRefreshRequest
import com.togethertrip.main.auth.dto.response.AuthResponse
import com.togethertrip.main.auth.dto.response.PhoneVerificationCodeSentResponse
import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.auth.service.oauth.OAuthSignupLock
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySession
import com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionService
import com.togethertrip.main.auth.service.phone.ConfirmedPhoneVerification
import com.togethertrip.main.auth.service.phone.PhoneVerificationService
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.global.security.jwt.TokenType
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class AuthService(
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val oauthAccountRepository: OAuthAccountRepository,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val temporarySessionService: OAuthTemporarySessionService,
    private val phoneVerificationService: PhoneVerificationService,
    private val oauthSignupLock: OAuthSignupLock,
    private val profileImageUrlPolicy: ProfileImageUrlPolicy,
) {

    @Transactional
    fun loginWithKakao(request: KakaoLoginRequest): AuthResponse {
        val oauthUserInfo = sanitizeOAuthUserInfo(
            kakaoOAuthClient.getUserInfo(request.accessToken)
        )

        // OAuth 계정 조회
        val oauthAccount = oauthAccountRepository
            .findByProviderAndProviderUserId(
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
            )

        // 기존 OAuth 계정 로그인
        if (oauthAccount != null) {
            val user = resolveOAuthAccountUser(oauthAccount)

            // 전화번호 인증 완료 사용자 로그인
            if (user.phoneVerifiedAt != null) {
                return createAuthenticatedResponse(user)
            }

            // 기존 사용자 전화번호 인증 요구
            return AuthResponse.phoneVerificationRequired(
                temporarySessionService.create(
                    oauthUserInfo = oauthUserInfo,
                    existingUserId = user.id,
                )
            )
        }

        // 신규 OAuth 사용자 전화번호 인증 요구
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
        val session = temporarySessionService.get(request.temporaryToken)

        // OAuth 가입 단위 잠금
        return oauthSignupLock.withLock(session) {
            confirmPhoneVerificationWithLockedSession(
                request = request,
                session = session,
            )
        }
    }

    private fun confirmPhoneVerificationWithLockedSession(
        request: ConfirmPhoneVerificationRequest,
        session: OAuthTemporarySession,
    ): AuthResponse {
        rejectIfSignupAlreadyCompleted(
            session = session,
            temporaryToken = request.temporaryToken,
        )

        // 인증번호 확인
        val confirmedPhoneVerification = phoneVerificationService.confirmCode(request)
        // 인증된 전화번호로 회원가입 완료
        val user = completeSignup(
            session = session,
            temporaryToken = request.temporaryToken,
            confirmedPhoneVerification = confirmedPhoneVerification,
        )

        // 가입 상태 저장 및 인증 세션 삭제
        flushSignupState(request.temporaryToken)
        phoneVerificationService.deleteTemporarySession(request.temporaryToken)

        // 인증 응답 생성
        return createAuthenticatedResponse(user)
    }

    @Transactional
    fun refreshToken(request: TokenRefreshRequest): TokenResponse {
        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        // refresh token claim 조회
        val claims = jwtTokenProvider.getClaims(request.refreshToken)

        // refresh token 타입 확인
        if (claims.tokenType != TokenType.REFRESH) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        // token 사용자 조회
        val user = userRepository.findById(claims.userId)
            .orElseThrow { BusinessException(UserErrorCode.USER_NOT_FOUND) }

        // 활성 사용자 확인
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        // 새 token 발급
        val accessToken = jwtTokenProvider.createAccessToken(
            userId = user.id,
            role = user.role,
        )
        val refreshToken = jwtTokenProvider.createRefreshToken(
            userId = user.id,
            role = user.role,
        )

        // refresh token 회전
        if (!refreshTokenService.rotate(
                userId = user.id,
                currentRefreshToken = request.refreshToken,
                newRefreshToken = refreshToken,
            )
        ) {
            throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        }

        // token 갱신 응답
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

        // 탈퇴 사용자 재가입 상태 전환
        if (user.status == UserStatus.WITHDRAWN) {
            user.reactivateForSignup()
            return user
        }

        // 활성 사용자 확인
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        // OAuth 사용자 반환
        return user
    }

    private fun completeSignup(
        session: OAuthTemporarySession,
        temporaryToken: String,
        confirmedPhoneVerification: ConfirmedPhoneVerification,
    ): User {
        return if (session.existingUserId != null) {
            // 기존 사용자 가입 완료
            completeExistingUserSignup(
                userId = session.existingUserId,
                temporaryToken = temporaryToken,
                confirmedPhoneVerification = confirmedPhoneVerification,
            )
        } else {
            // 신규 사용자 가입 완료
            registerNewUser(
                oauthUserInfo = session.toOAuthUserInfo(),
                confirmedPhoneVerification = confirmedPhoneVerification,
            )
        }
    }

    private fun completeExistingUserSignup(
        userId: Long,
        temporaryToken: String,
        confirmedPhoneVerification: ConfirmedPhoneVerification,
    ): User {
        val user = userRepository.findLockedByIdIncludingDeleted(userId)
            ?: rejectExpiredTemporarySession(temporaryToken)

        // 탈퇴 사용자 재활성화
        if (user.status == UserStatus.WITHDRAWN || user.deletedAt != null) {
            user.reactivateForSignup()
        }

        // 활성 사용자 확인
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        // 이미 인증된 사용자 확인
        if (user.phoneVerifiedAt != null) {
            rejectAlreadyCompleted(temporaryToken)
        }

        // 기존 사용자 전화번호 인증 정보 저장
        verifyPhoneNumberForSignup(
            user = user,
            confirmedPhoneVerification = confirmedPhoneVerification,
        )

        // 기존 사용자 반환
        return user
    }

    private fun registerNewUser(
        oauthUserInfo: OAuthUserInfo,
        confirmedPhoneVerification: ConfirmedPhoneVerification,
    ): User {
        validatePhoneNumberAvailable(
            phoneNumberHash = confirmedPhoneVerification.phoneNumberHash,
            currentUserId = null,
        )

        // 신규 사용자 저장
        val user = userRepository.save(
            User(
                nickname = oauthUserInfo.nickname ?: "카카오 사용자",
                profileImageUrl = oauthUserInfo.profileImageUrl,
            ).apply {
                verifyPhoneNumberHash(
                    phoneNumberHash = confirmedPhoneVerification.phoneNumberHash,
                    phoneNumberHashVersion = confirmedPhoneVerification.phoneNumberHashVersion,
                    phoneNumberEncrypted = confirmedPhoneVerification.phoneNumberEncrypted,
                    phoneNumberEncryptionVersion = confirmedPhoneVerification.phoneNumberEncryptionVersion,
                    phoneNumberMasked = confirmedPhoneVerification.phoneNumberMasked,
                )
            }
        )

        // OAuth 계정 저장
        oauthAccountRepository.save(
            OAuthAccount(
                user = user,
                provider = oauthUserInfo.provider,
                providerUserId = oauthUserInfo.providerUserId,
                nickname = oauthUserInfo.nickname,
                profileImageUrl = oauthUserInfo.profileImageUrl,
            )
        )

        // 신규 사용자 반환
        return user
    }

    private fun verifyPhoneNumberForSignup(
        user: User,
        confirmedPhoneVerification: ConfirmedPhoneVerification,
    ) {
        validatePhoneNumberAvailable(
            phoneNumberHash = confirmedPhoneVerification.phoneNumberHash,
            currentUserId = user.id,
        )
        user.verifyPhoneNumberHash(
            phoneNumberHash = confirmedPhoneVerification.phoneNumberHash,
            phoneNumberHashVersion = confirmedPhoneVerification.phoneNumberHashVersion,
            phoneNumberEncrypted = confirmedPhoneVerification.phoneNumberEncrypted,
            phoneNumberEncryptionVersion = confirmedPhoneVerification.phoneNumberEncryptionVersion,
            phoneNumberMasked = confirmedPhoneVerification.phoneNumberMasked,
        )
    }

    private fun rejectIfSignupAlreadyCompleted(
        session: OAuthTemporarySession,
        temporaryToken: String,
    ) {
        // OAuth 계정 완료 여부 조회
        val oauthAccount = oauthAccountRepository.findByProviderAndProviderUserId(
            provider = session.provider,
            providerUserId = session.providerUserId,
        ) ?: return

        // 이미 가입 완료된 세션 거부
        if (oauthAccount.user.phoneVerifiedAt != null) {
            rejectAlreadyCompleted(temporaryToken)
        }
    }

    private fun rejectAlreadyCompleted(temporaryToken: String): Nothing {
        phoneVerificationService.deleteTemporarySession(temporaryToken)
        throw BusinessException(AuthErrorCode.SIGNUP_ALREADY_COMPLETED)
    }

    private fun rejectExpiredTemporarySession(temporaryToken: String): Nothing {
        phoneVerificationService.deleteTemporarySession(temporaryToken)
        throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_TOKEN_EXPIRED)
    }

    private fun validatePhoneNumberAvailable(
        phoneNumberHash: String,
        currentUserId: Long?,
    ) {
        // 전화번호 hash 중복 확인
        val alreadyUsed = if (currentUserId == null) {
            userRepository.existsByPhoneNumberHashAndDeletedAtIsNull(phoneNumberHash)
        } else {
            userRepository.existsByPhoneNumberHashAndIdNotAndDeletedAtIsNull(
                phoneNumberHash = phoneNumberHash,
                id = currentUserId,
            )
        }

        if (alreadyUsed) {
            throw BusinessException(AuthErrorCode.PHONE_NUMBER_ALREADY_USED)
        }
    }

    private fun flushSignupState(temporaryToken: String) {
        try {
            // 가입 상태 DB 반영
            userRepository.flush()
        } catch (_: DataIntegrityViolationException) {
            phoneVerificationService.deleteTemporarySession(temporaryToken)
            throw BusinessException(AuthErrorCode.PHONE_NUMBER_ALREADY_USED)
        }
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

        // refresh token 저장 예약
        saveRefreshTokenAfterCommit(user.id, refreshToken)

        // token 응답 생성
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    private fun createAuthenticatedResponse(user: User): AuthResponse {
        val tokenResponse = issueTokens(user)

        // 프로필 입력 필요 응답
        if (!user.isProfileCompleted()) {
            return AuthResponse.profileRequired(tokenResponse)
        }

        // 인증 완료 응답
        return AuthResponse.authenticated(tokenResponse)
    }

    private fun saveRefreshTokenAfterCommit(
        userId: Long,
        refreshToken: String,
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            refreshTokenService.save(
                userId = userId,
                refreshToken = refreshToken,
            )
            return
        }

        // 커밋 후 refresh token 저장
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    refreshTokenService.save(
                        userId = userId,
                        refreshToken = refreshToken,
                    )
                }
            }
        )
    }

    private fun OAuthTemporarySession.toOAuthUserInfo(): OAuthUserInfo {
        // 임시 세션을 OAuth 사용자 정보로 변환
        return OAuthUserInfo(
            provider = provider,
            providerUserId = providerUserId,
            nickname = nickname,
            profileImageUrl = profileImageUrlPolicy.sanitize(profileImageUrl),
        )
    }

    private fun sanitizeOAuthUserInfo(oauthUserInfo: OAuthUserInfo): OAuthUserInfo {
        return oauthUserInfo.copy(
            profileImageUrl = profileImageUrlPolicy.sanitize(oauthUserInfo.profileImageUrl)
        )
    }
}
