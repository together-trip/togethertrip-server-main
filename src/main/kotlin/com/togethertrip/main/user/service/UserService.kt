package com.togethertrip.main.user.service

import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.user.UserAccountDeletedPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.dto.request.SearchUserByNicknameRequest
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.dto.response.MyTripParticipantResponse
import com.togethertrip.main.user.dto.response.NicknameAvailabilityResponse
import com.togethertrip.main.user.dto.response.UserSearchResponse
import com.togethertrip.main.user.dto.response.UserSummaryResponse
import com.togethertrip.main.user.dto.response.UserResponse
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserAgreementRepository
import com.togethertrip.main.user.repository.UserRepository
import com.togethertrip.main.user.service.storage.StoredUserProfileImage
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.LocalDate

@Service
class UserService(
    private val userRepository: UserRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val userProfileImageStorage: UserProfileImageStorage,
    private val profileImageUrlPolicy: ProfileImageUrlPolicy,
    private val oauthAccountRepository: OAuthAccountRepository,
    private val userAgreementRepository: UserAgreementRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val accountDeletionCleanupEnqueueService: UserAccountDeletionCleanupEnqueueService,
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
        profileImage: MultipartFile? = null,
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

        val profileImageUrl = resolveProfileImageUrl(
            request = request,
            profileImage = profileImage,
        )

        // 프로필 수정
        user.updateProfile(
            nickname = request.nickname,
            gender = request.gender,
            birthDate = request.birthDate,
            profileImageUrl = profileImageUrl,
        )

        // 수정된 사용자 응답
        return UserResponse.from(user)
    }

    @Transactional
    fun deleteMe(userId: Long) {
        val user = getLockedActiveUser(userId)
        val deletedAt = Instant.now()
        val profileImageUrl = user.profileImageUrl

        accountDeletionCleanupEnqueueService.enqueue(
            userId = userId,
            profileImageUrl = profileImageUrl,
        )
        tripParticipantRepository.anonymizeAllByUserIdIncludingDeleted(
            userId = userId,
            displayName = User.WITHDRAWN_USER_NICKNAME,
            updatedAt = deletedAt,
        )
        oauthAccountRepository.deleteAllByUserId(userId)
        userAgreementRepository.deleteAllByUserId(userId)
        user.anonymizeAndWithdraw(deletedAt)
        userRepository.save(user)

        outboxEventPublisher.publishLifecycle(
            aggregateType = OutboxAggregateType.USER,
            aggregateId = userId,
            eventType = OutboxEventType.USER_ACCOUNT_DELETED,
            payload = UserAccountDeletedPayload(
                userId = userId,
                occurredAt = deletedAt,
            ),
        )

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
    fun searchByNickname(
        authUserId: Long,
        request: SearchUserByNicknameRequest,
    ): UserSearchResponse {
        getActiveUser(authUserId)
        validateNickname(request.nickname)

        val user = userRepository.findByNicknameAndStatusAndDeletedAtIsNull(
            nickname = request.nickname,
            status = UserStatus.ACTIVE,
        ) ?: return UserSearchResponse.notFound()

        return UserSearchResponse.found(
            UserSummaryResponse.from(user)
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

    private fun getLockedActiveUser(userId: Long): User {
        val user = userRepository.findLockedByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

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
        request.profileImageUrl?.let { profileImageUrl ->
            val trimmedProfileImageUrl = profileImageUrl.trim()
            if (
                trimmedProfileImageUrl.isBlank() ||
                trimmedProfileImageUrl.length > MAX_PROFILE_IMAGE_URL_LENGTH ||
                !profileImageUrlPolicy.isAllowed(trimmedProfileImageUrl)
            ) {
                throw BusinessException(CommonErrorCode.INVALID_INPUT)
            }
        }
    }

    private fun resolveProfileImageUrl(
        request: UpdateUserRequest,
        profileImage: MultipartFile?,
    ): String? {
        if (profileImage == null || profileImage.isEmpty) {
            return request.profileImageUrl?.trim()
        }

        val storedImage = userProfileImageStorage.store(profileImage)
        deleteStoredImageAfterRollback(storedImage)

        return storedImage.fileUrl
    }

    private fun deleteStoredImageAfterRollback(storedImage: StoredUserProfileImage) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        userProfileImageStorage.delete(storedImage)
                    }
                }
            }
        )
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

    companion object {
        private const val MIN_NICKNAME_LENGTH = 2
        private const val MAX_NICKNAME_LENGTH = 20
        private const val MAX_PROFILE_IMAGE_URL_LENGTH = 500
        private val ALLOWED_GENDERS = setOf("MALE", "FEMALE")
    }
}
