package com.togethertrip.main.user.service

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.phone.PhoneNumberHasher
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.dto.request.SearchUserByPhoneRequest
import com.togethertrip.main.user.dto.request.UpdateUserRequest
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import com.togethertrip.main.user.service.storage.StoredUserProfileImage
import com.togethertrip.main.user.service.storage.UserProfileImageStorage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var phoneNumberNormalizer: PhoneNumberNormalizer
    private lateinit var phoneNumberHasher: PhoneNumberHasher
    private lateinit var userProfileImageStorage: UserProfileImageStorage
    private lateinit var profileImageUrlPolicy: ProfileImageUrlPolicy
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mock(UserRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        phoneNumberNormalizer = PhoneNumberNormalizer()
        phoneNumberHasher = PhoneNumberHasher(
            key = "test-phone-hash-key-must-be-at-least-32-bytes",
            version = "v1",
        )
        userProfileImageStorage = mock(UserProfileImageStorage::class.java)
        profileImageUrlPolicy = ProfileImageUrlPolicy(
            userProfileImagePublicUrlPrefix = "/uploads/user-profile-images",
        )
        userService = UserService(
            userRepository = userRepository,
            tripParticipantRepository = tripParticipantRepository,
            phoneNumberNormalizer = phoneNumberNormalizer,
            phoneNumberHasher = phoneNumberHasher,
            userProfileImageStorage = userProfileImageStorage,
            profileImageUrlPolicy = profileImageUrlPolicy,
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
    fun `회원 탈퇴는 soft delete로 처리한다`() {
        val user = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)

        userService.deleteMe(1L)

        assertEquals(UserStatus.WITHDRAWN, user.status)
        assertNotNull(user.deletedAt)
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
    fun `전화번호로 인증 완료 활성 사용자를 검색한다`() {
        val authUser = createUser().apply {
            verifyPhoneNumberHash(
                phoneNumberHash = phoneNumberHasher.hash("+821011112222"),
                phoneNumberHashVersion = phoneNumberHasher.version,
            )
        }
        val targetUser = createUser().apply {
            id = 2L
            nickname = "동행자"
            verifyPhoneNumberHash(
                phoneNumberHash = phoneNumberHasher.hash("+821033334444"),
                phoneNumberHashVersion = phoneNumberHasher.version,
            )
        }

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(authUser)
        `when`(
            userRepository.findByPhoneNumberHashAndPhoneVerifiedAtIsNotNullAndStatusAndDeletedAtIsNull(
                phoneNumberHash = phoneNumberHasher.hash("+821033334444"),
                status = UserStatus.ACTIVE,
            )
        ).thenReturn(targetUser)

        val response = userService.searchByPhoneNumber(
            authUserId = 1L,
            request = SearchUserByPhoneRequest(
                phoneNumber = "010-3333-4444",
            ),
        )

        assertEquals(true, response.found)
        assertEquals(2L, response.user?.userId)
        assertEquals("동행자", response.user?.nickname)
    }

    @Test
    fun `전화번호 검색 결과가 없으면 found false를 반환한다`() {
        val authUser = createUser().apply {
            verifyPhoneNumberHash(
                phoneNumberHash = phoneNumberHasher.hash("+821011112222"),
                phoneNumberHashVersion = phoneNumberHasher.version,
            )
        }

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(authUser)
        `when`(
            userRepository.findByPhoneNumberHashAndPhoneVerifiedAtIsNotNullAndStatusAndDeletedAtIsNull(
                phoneNumberHash = phoneNumberHasher.hash("+821033334444"),
                status = UserStatus.ACTIVE,
            )
        ).thenReturn(null)

        val response = userService.searchByPhoneNumber(
            authUserId = 1L,
            request = SearchUserByPhoneRequest(
                phoneNumber = "+821033334444",
            ),
        )

        assertEquals(false, response.found)
        assertEquals(null, response.user)
    }

    @Test
    fun `전화번호 미인증 사용자는 전화번호 검색에 실패한다`() {
        val authUser = createUser()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(authUser)

        val exception = assertBusinessException {
            userService.searchByPhoneNumber(
                authUserId = 1L,
                request = SearchUserByPhoneRequest(
                    phoneNumber = "01033334444",
                ),
            )
        }

        assertEquals(AuthErrorCode.PHONE_VERIFICATION_REQUIRED, exception.errorCode)
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
