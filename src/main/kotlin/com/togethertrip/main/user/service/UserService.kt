package com.togethertrip.main.user.service

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.phone.PhoneNumberHasher
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.dto.request.SearchUserByPhoneRequest
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.dto.response.MyTripParticipantResponse
import com.togethertrip.main.user.dto.response.NicknameAvailabilityResponse
import com.togethertrip.main.user.dto.response.PhoneUserSearchResponse
import com.togethertrip.main.user.dto.response.PhoneUserSummaryResponse
import com.togethertrip.main.user.dto.response.UserResponse
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.LocalDate

@Service
class UserService(
    private val userRepository: UserRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val phoneNumberHasher: PhoneNumberHasher,
) {

    @Transactional(readOnly = true)
    fun getMe(userId: Long): UserResponse {
        return UserResponse.from(
            getActiveUser(userId)
        )
    }

    @Transactional(readOnly = true)
    fun checkNicknameAvailability(
        nickname: String,
    ): NicknameAvailabilityResponse {
        validateNickname(nickname)

        // 닉네임 사용 가능 여부 응답
        return NicknameAvailabilityResponse(
            available = !userRepository.existsByNicknameAndDeletedAtIsNull(nickname)
        )
    }

    @Transactional
    fun updateMe(
        userId: Long,
        request: UpdateUserRequest,
    ): UserResponse {
        validateUpdateRequest(request)

        // 수정 대상 사용자 조회
        val user = getActiveUser(userId)

        // 닉네임 중복 확인
        if (
            request.nickname != null &&
            request.nickname != user.nickname &&
            userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(
                nickname = request.nickname,
                id = userId,
            )
        ) {
            throw BusinessException(UserErrorCode.NICKNAME_ALREADY_USED)
        }

        // 프로필 수정
        user.updateProfile(
            nickname = request.nickname,
            gender = request.gender,
            birthDate = request.birthDate,
            profileImageUrl = request.profileImageUrl,
        )

        // 수정된 사용자 응답
        return UserResponse.from(user)
    }

    @Transactional
    fun deleteMe(userId: Long) {
        val user = getActiveUser(userId)

        // 회원 탈퇴 처리
        user.withdraw()
    }

    @Transactional(readOnly = true)
    fun getMyTripParticipant(
        userId: Long,
        tripId: Long,
    ): MyTripParticipantResponse {
        getActiveUser(userId)

        // 내 여행 참여자 조회
        val tripParticipant = tripParticipantRepository
            .findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = tripId,
                userId = userId,
            )
            ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)

        // 내 여행 참여자 응답
        return MyTripParticipantResponse.from(tripParticipant)
    }

    @Transactional(readOnly = true)
    fun searchByPhoneNumber(
        authUserId: Long,
        request: SearchUserByPhoneRequest,
    ): PhoneUserSearchResponse {
        val authUser = getActiveUser(authUserId)

        // 검색 요청자 전화번호 인증 확인
        if (authUser.phoneVerifiedAt == null) {
            throw BusinessException(AuthErrorCode.PHONE_VERIFICATION_REQUIRED)
        }

        // 검색 전화번호 hash 변환
        val phoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber)
        val phoneNumberHash = phoneNumberHasher.hash(phoneNumber)
        val user = userRepository
            .findByPhoneNumberHashAndPhoneVerifiedAtIsNotNullAndStatusAndDeletedAtIsNull(
                phoneNumberHash = phoneNumberHash,
                status = UserStatus.ACTIVE,
            )
            ?: return PhoneUserSearchResponse.notFound()

        // 전화번호 검색 결과 응답
        return PhoneUserSearchResponse.found(
            PhoneUserSummaryResponse.from(user)
        )
    }

    private fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        // 활성 사용자 확인
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        // 활성 사용자 반환
        return user
    }

    private fun validateUpdateRequest(request: UpdateUserRequest) {
        request.nickname?.let(::validateNickname)

        // 성별 입력값 검증
        if (request.gender != null && request.gender !in ALLOWED_GENDERS) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        // 생년월일 입력값 검증
        if (request.birthDate != null && request.birthDate.isAfter(LocalDate.now())) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        // 프로필 이미지 URL 검증
        if (request.profileImageUrl != null && !isValidProfileImageUrl(request.profileImageUrl)) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun validateNickname(nickname: String) {
        if (
            nickname.isBlank() ||
            nickname.length < MIN_NICKNAME_LENGTH ||
            nickname.length > MAX_NICKNAME_LENGTH
        ) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun isValidProfileImageUrl(profileImageUrl: String): Boolean {
        val uri = runCatching { URI(profileImageUrl) }
            .getOrNull()
            ?: return false

        // URL 스킴과 host 확인
        return uri.scheme in ALLOWED_PROFILE_IMAGE_URL_SCHEMES &&
            uri.host != null
    }

    companion object {
        private const val MIN_NICKNAME_LENGTH = 2
        private const val MAX_NICKNAME_LENGTH = 20
        private val ALLOWED_GENDERS = setOf("MALE", "FEMALE")
        private val ALLOWED_PROFILE_IMAGE_URL_SCHEMES = setOf("http", "https")
    }
}
