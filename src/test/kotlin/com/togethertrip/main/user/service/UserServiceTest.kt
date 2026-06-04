package com.togethertrip.main.user.service

import com.togethertrip.main.auth.exception.AuthErrorCode
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.phone.PhoneNumberNormalizer
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var phoneNumberNormalizer: PhoneNumberNormalizer
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mock(UserRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        phoneNumberNormalizer = PhoneNumberNormalizer()
        userService = UserService(
            userRepository = userRepository,
            tripParticipantRepository = tripParticipantRepository,
            phoneNumberNormalizer = phoneNumberNormalizer,
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
                profileImageUrl = "https://example.com/profile.png",
            ),
        )

        assertEquals("새닉네임", user.nickname)
        assertEquals("https://example.com/profile.png", user.profileImageUrl)
        assertEquals("새닉네임", response.nickname)
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
            verifyPhoneNumber("+821011112222")
        }
        val targetUser = createUser().apply {
            id = 2L
            nickname = "동행자"
            verifyPhoneNumber("+821033334444")
        }

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(authUser)
        `when`(
            userRepository.findByPhoneNumberAndPhoneVerifiedAtIsNotNullAndStatusAndDeletedAtIsNull(
                phoneNumber = "+821033334444",
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
            verifyPhoneNumber("+821011112222")
        }

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(authUser)
        `when`(
            userRepository.findByPhoneNumberAndPhoneVerifiedAtIsNotNullAndStatusAndDeletedAtIsNull(
                phoneNumber = "+821033334444",
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
            email = "user@example.com",
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
