package com.togethertrip.main.user.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.ErrorCode
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.dto.request.UpdateUserRequest
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
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mock(UserRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        userService = UserService(
            userRepository = userRepository,
            tripParticipantRepository = tripParticipantRepository,
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

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `비활성 사용자는 내 정보 조회에 실패한다`() {
        val user = createUser(status = UserStatus.SUSPENDED)

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(user)

        val exception = assertBusinessException {
            userService.getMe(1L)
        }

        assertEquals(ErrorCode.INACTIVE_USER, exception.errorCode)
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

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
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

        assertEquals(ErrorCode.TRIP_PARTICIPANT_NOT_FOUND, exception.errorCode)
        verify(tripParticipantRepository)
            .findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
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
