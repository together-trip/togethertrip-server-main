package com.togethertrip.main.user.service

import com.togethertrip.main.auth.repository.OAuthAccountRepository
import com.togethertrip.main.auth.service.RefreshTokenService
import com.togethertrip.main.auth.service.apple.OAuthAccountRevoker
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.user.UserAccountDeletedPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.dto.request.SearchUserByNicknameRequest
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import com.togethertrip.main.user.repository.UserAgreementRepository
import com.togethertrip.main.user.service.storage.StoredUserProfileImage
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var userProfileImageStorage: UserProfileImageStorage
    private lateinit var profileImageUrlPolicy: ProfileImageUrlPolicy
    private lateinit var oauthAccountRepository: OAuthAccountRepository
    private lateinit var userAgreementRepository: UserAgreementRepository
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var outboxEventPublisher: OutboxEventPublisher
    private lateinit var oauthAccountRevoker: OAuthAccountRevoker
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mock(UserRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        userProfileImageStorage = mock(UserProfileImageStorage::class.java)
        profileImageUrlPolicy = ProfileImageUrlPolicy(
            userProfileImagePublicUrlPrefix = "/uploads/user-profile-images",
        )
        oauthAccountRevoker = mock(OAuthAccountRevoker::class.java)
        oauthAccountRepository = mock(OAuthAccountRepository::class.java)
        userAgreementRepository = mock(UserAgreementRepository::class.java)
        refreshTokenService = mock(RefreshTokenService::class.java)
        outboxEventPublisher = mock(OutboxEventPublisher::class.java)
        userService = UserService(
            userRepository = userRepository,
            tripParticipantRepository = tripParticipantRepository,
            userProfileImageStorage = userProfileImageStorage,
            profileImageUrlPolicy = profileImageUrlPolicy,
            oauthAccountRepository = oauthAccountRepository,
            userAgreementRepository = userAgreementRepository,
            refreshTokenService = refreshTokenService,
            outboxEventPublisher = outboxEventPublisher,
            oauthAccountRevoker = oauthAccountRevoker,
        )
    }

    @Test
    fun `내 정보 조회에 성공한다`() {
        val user = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)

        val response = userService.getMe(1L)

        assertEquals(1L, response.id)
        assertEquals("재완", response.nickname)
        assertEquals(UserStatus.ACTIVE, response.status)
    }

    @Test
    fun `사용자가 없으면 내 정보 조회에 실패한다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(null)

        val exception = assertBusinessException {
            userService.getMe(1L)
        }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `비활성 사용자는 내 정보 조회에 실패한다`() {
        val user = createUser(status = UserStatus.SUSPENDED)

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)

        val exception = assertBusinessException {
            userService.getMe(1L)
        }

        assertEquals(UserErrorCode.INACTIVE_USER, exception.errorCode)
    }

    @Test
    fun `닉네임과 프로필 이미지를 수정한다`() {
        val user = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)

        val response = userService.updateMe(
            userId = 1L,
            request = UpdateUserRequest(
                nickname = "새닉네임",
                profileImageUrl = "/uploads/user-profile-images/profile.png",
            ),
        )

        assertEquals("새닉네임", user.nickname)
        assertEquals("/uploads/user-profile-images/profile.png", user.profileImageUrl)
        assertEquals("새닉네임", response.nickname)
    }

    @Test
    fun `업로드한 프로필 이미지를 저장하고 저장된 URL로 수정한다`() {
        val user = createUser()
        val profileImage = MockMultipartFile(
            "profileImage",
            "profile.JPG",
            "image/jpeg",
            "image-content".toByteArray(),
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)
        `when`(userProfileImageStorage.store(profileImage))
            .thenReturn(
                StoredUserProfileImage(
                    storageKey = "stored-profile.jpg",
                    fileUrl = "/uploads/user-profile-images/stored-profile.jpg",
                    fileSize = profileImage.size,
                    mimeType = "image/jpeg",
                )
            )

        val response = userService.updateMe(
            userId = 1L,
            request = UpdateUserRequest(
                nickname = "새닉네임",
            ),
            profileImage = profileImage,
        )

        assertEquals("새닉네임", user.nickname)
        assertEquals("/uploads/user-profile-images/stored-profile.jpg", user.profileImageUrl)
        assertEquals("/uploads/user-profile-images/stored-profile.jpg", response.profileImageUrl)
        verify(userProfileImageStorage).store(profileImage)
    }

    @Test
    fun `업로드한 프로필 이미지 수정 트랜잭션이 롤백되면 저장된 파일을 삭제한다`() {
        val user = createUser()
        val profileImage = MockMultipartFile(
            "profileImage",
            "profile.JPG",
            "image/jpeg",
            "image-content".toByteArray(),
        )
        val storedImage = StoredUserProfileImage(
            storageKey = "rollback-profile.jpg",
            fileUrl = "/uploads/user-profile-images/rollback-profile.jpg",
            fileSize = profileImage.size,
            mimeType = "image/jpeg",
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)
        `when`(userProfileImageStorage.store(profileImage))
            .thenReturn(storedImage)

        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        try {
            userService.updateMe(
                userId = 1L,
                request = UpdateUserRequest(
                    nickname = "새닉네임",
                ),
                profileImage = profileImage,
            )

            TransactionSynchronizationManager
                .getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false)
            TransactionSynchronizationManager.clearSynchronization()
        }

        verify(userProfileImageStorage).delete(storedImage)
    }

    @Test
    fun `닉네임 성별 생년월일을 수정한다`() {
        val user = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)
        `when`(
            userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(
                nickname = "여행자",
                id = 1L,
            )
        ).thenReturn(false)

        val response = userService.updateMe(
            userId = 1L,
            request = UpdateUserRequest(
                nickname = "여행자",
                gender = "MALE",
                birthDate = LocalDate.of(1990, 1, 1),
            ),
        )

        assertEquals("여행자", user.nickname)
        assertEquals("MALE", user.gender)
        assertEquals(LocalDate.of(1990, 1, 1), user.birthDate)
        assertEquals("여행자", response.nickname)
        assertEquals("MALE", response.gender)
        assertEquals(LocalDate.of(1990, 1, 1), response.birthDate)
    }

    @Test
    fun `빈 닉네임으로 수정하면 실패한다`() {
        val exception = assertBusinessException {
            userService.updateMe(
                userId = 1L,
                request = UpdateUserRequest(nickname = " "),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `신뢰하지 않는 외부 프로필 이미지 URL이면 실패한다`() {
        val exception = assertBusinessException {
            userService.updateMe(
                userId = 1L,
                request = UpdateUserRequest(
                    profileImageUrl = "https://example.com/profile.png",
                ),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `중복 닉네임으로 수정하면 실패한다`() {
        val user = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)
        `when`(
            userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(
                nickname = "동행자",
                id = 1L,
            )
        ).thenReturn(true)

        val exception = assertBusinessException {
            userService.updateMe(
                userId = 1L,
                request = UpdateUserRequest(nickname = "동행자"),
            )
        }

        assertEquals(UserErrorCode.NICKNAME_ALREADY_USED, exception.errorCode)
    }

    @Test
    fun `허용되지 않은 성별로 수정하면 실패한다`() {
        val exception = assertBusinessException {
            userService.updateMe(
                userId = 1L,
                request = UpdateUserRequest(gender = "UNKNOWN"),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `미래 생년월일로 수정하면 실패한다`() {
        val exception = assertBusinessException {
            userService.updateMe(
                userId = 1L,
                request = UpdateUserRequest(
                    birthDate = LocalDate.now().plusDays(1),
                ),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `프로필 이미지 URL이 http 또는 https가 아니면 실패한다`() {
        val exception = assertBusinessException {
            userService.updateMe(
                userId = 1L,
                request = UpdateUserRequest(
                    profileImageUrl = "javascript:alert(1)",
                ),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `사용 가능한 닉네임이면 available true를 반환한다`() {
        `when`(userRepository.existsByNicknameAndDeletedAtIsNull("여행자"))
            .thenReturn(false)

        val response = userService.checkNicknameAvailability(
            nickname = "여행자",
        )

        assertEquals(true, response.available)
    }

    @Test
    fun `이미 사용 중인 닉네임이면 available false를 반환한다`() {
        `when`(userRepository.existsByNicknameAndDeletedAtIsNull("동행자"))
            .thenReturn(true)

        val response = userService.checkNicknameAvailability(
            nickname = "동행자",
        )

        assertEquals(false, response.available)
    }

    @Test
    fun `회원 탈퇴는 개인정보와 인증 연결을 제거하고 lifecycle 이벤트를 저장한다`() {
        val user = createUser().apply {
            gender = "MALE"
            birthDate = LocalDate.of(1990, 1, 1)
            profileImageUrl = "/uploads/user-profile-images/profile.jpg"
        }

        `when`(userRepository.findLockedByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)

        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        try {
            userService.deleteMe(1L)

            assertEquals(User.WITHDRAWN_USER_NICKNAME, user.nickname)
            assertNull(user.gender)
            assertNull(user.birthDate)
            assertNull(user.profileImageUrl)
            assertEquals(UserStatus.WITHDRAWN, user.status)
            assertNotNull(user.deletedAt)
            verify(oauthAccountRevoker).revokeForUser(1L)
            verify(oauthAccountRepository).deleteAllByUserId(1L)
            verify(userAgreementRepository).deleteAllByUserId(1L)
            verifyNoInteractions(refreshTokenService)

            val participantInvocation = mockingDetails(tripParticipantRepository).invocations
                .single { it.method.name == "anonymizeAllByUserIdIncludingDeleted" }
            assertEquals(1L, participantInvocation.arguments[0])
            assertEquals(User.WITHDRAWN_USER_NICKNAME, participantInvocation.arguments[1])

            val outboxInvocation = mockingDetails(outboxEventPublisher).invocations
                .single { it.method.name == "publishLifecycle" }
            assertEquals(OutboxAggregateType.USER, outboxInvocation.arguments[0])
            assertEquals(1L, outboxInvocation.arguments[1])
            assertEquals(OutboxEventType.USER_ACCOUNT_DELETED, outboxInvocation.arguments[2])
            val payload = outboxInvocation.arguments[3] as UserAccountDeletedPayload
            assertEquals(1, payload.eventVersion)
            assertEquals(1L, payload.userId)

            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false)
            TransactionSynchronizationManager.clearSynchronization()
        }

        verify(refreshTokenService).delete(1L)
        verify(userProfileImageStorage)
            .deleteByFileUrl("/uploads/user-profile-images/profile.jpg")
    }

    @Test
    fun `회원 탈퇴 트랜잭션이 롤백되면 외부 저장소를 정리하지 않는다`() {
        val user = createUser().apply {
            profileImageUrl = "/uploads/user-profile-images/profile.jpg"
        }
        `when`(userRepository.findLockedByIdAndDeletedAtIsNull(1L)).thenReturn(user)

        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.setActualTransactionActive(true)
        try {
            userService.deleteMe(1L)
            TransactionSynchronizationManager.getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false)
            TransactionSynchronizationManager.clearSynchronization()
        }

        verifyNoInteractions(refreshTokenService)
        assertTrue(
            mockingDetails(userProfileImageStorage).invocations
                .none { it.method.name == "deleteByFileUrl" }
        )
    }

    @Test
    fun `내 여행 참여자 정보를 조회한다`() {
        val user = createUser()
        val trip = createTrip(ownerUser = user)
        val tripParticipant = createTripParticipant(
            user = user,
            trip = trip,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
        ).thenReturn(tripParticipant)

        val response = userService.getMyTripParticipant(
            userId = 1L,
            tripId = 10L,
        )

        assertEquals(100L, response.id)
        assertEquals(10L, response.tripId)
        assertEquals(1L, response.userId)
        assertEquals(TripParticipantStatus.ACTIVE, response.participantStatus)
    }

    @Test
    fun `내 여행 참여자가 없으면 조회에 실패한다`() {
        val user = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            userService.getMyTripParticipant(
                userId = 1L,
                tripId = 10L,
            )
        }

        assertEquals(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND, exception.errorCode)
        verify(tripParticipantRepository)
            .findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
    }

    @Test
    fun `닉네임으로 활성 사용자를 검색한다`() {
        val authUser = createUser()
        val targetUser = createUser().apply {
            id = 2L
            nickname = "동행자"
        }

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(authUser)
        `when`(
            userRepository.findByNicknameAndStatusAndDeletedAtIsNull(
                nickname = "동행자",
                status = UserStatus.ACTIVE,
            )
        ).thenReturn(targetUser)

        val response = userService.searchByNickname(
            authUserId = 1L,
            request = SearchUserByNicknameRequest(
                nickname = "동행자",
            ),
        )

        assertEquals(true, response.found)
        assertEquals(2L, response.user?.userId)
        assertEquals("동행자", response.user?.nickname)
    }

    @Test
    fun `닉네임 검색 결과가 없으면 found false를 반환한다`() {
        val authUser = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(authUser)
        `when`(
            userRepository.findByNicknameAndStatusAndDeletedAtIsNull(
                nickname = "없는사용자",
                status = UserStatus.ACTIVE,
            )
        ).thenReturn(null)

        val response = userService.searchByNickname(
            authUserId = 1L,
            request = SearchUserByNicknameRequest(
                nickname = "없는사용자",
            ),
        )

        assertEquals(false, response.found)
        assertEquals(null, response.user)
    }

    private fun createUser(
        status: UserStatus = UserStatus.ACTIVE,
    ): User {
        return User(
            nickname = "재완",
            profileImageUrl = null,
            status = status,
        ).apply {
            id = 1L
        }
    }

    private fun createTrip(ownerUser: User): Trip {
        return Trip(
            ownerUser = ownerUser,
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            id = 10L
        }
    }

    private fun createTripParticipant(
        user: User,
        trip: Trip,
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = "재완",
            participantRole = TripParticipantRole.LEADER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            id = 100L
        }
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }
}
