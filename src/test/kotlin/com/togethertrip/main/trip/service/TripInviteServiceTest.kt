package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripInvitation
import com.togethertrip.main.trip.domain.TripInvitationStatus
import com.togethertrip.main.trip.domain.TripInvitationType
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.dto.request.JoinTripRequest
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripInvitationRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TripInviteServiceTest {

    private lateinit var tripRepository: TripRepository
    private lateinit var tripInvitationRepository: TripInvitationRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var userRepository: UserRepository
    private lateinit var tripInviteService: TripInviteService

    private val clock = Clock.fixed(
        Instant.parse("2026-06-11T00:00:00Z"),
        ZoneId.of("UTC"),
    )

    @BeforeEach
    fun setUp() {
        tripRepository = mock(TripRepository::class.java)
        tripInvitationRepository = mock(TripInvitationRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        tripInviteService = TripInviteService(
            tripRepository = tripRepository,
            tripInvitationRepository = tripInvitationRepository,
            tripParticipantRepository = tripParticipantRepository,
            userRepository = userRepository,
            clock = clock,
            inviteBaseUrl = "https://app.test/invites",
        )
    }

    @Test
    fun `방장은 초대 코드를 생성할 수 있다`() {
        val owner = createUser()
        val trip = createTrip(owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripInvitationRepository.existsByTokenAndDeletedAtIsNull(anyString())).thenReturn(false)
        `when`(tripInvitationRepository.existsByCodeAndDeletedAtIsNull(anyString())).thenReturn(false)
        `when`(tripInvitationRepository.saveAndFlush(any(TripInvitation::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripInvitation).apply { id = 100L }
        }

        val response = tripInviteService.createInviteCode(
            userId = 1L,
            tripId = 10L,
        )

        assertEquals(100L, response.id)
        assertEquals(10L, response.tripId)
        assertEquals(TripInvitationType.CODE, response.type)
        assertNotNull(response.code)
        assertEquals(8, response.code.length)
        assertTrue(response.inviteUrl.startsWith("https://app.test/invites?code="))
        assertEquals(TripInvitationStatus.ACTIVE, response.invitationStatus)
        assertEquals(Instant.parse("2026-06-18T00:00:00Z"), response.expiresAt)
    }

    @Test
    fun `방장이 아니면 초대 링크 생성에 실패한다`() {
        val owner = createUser(id = 1L)
        val member = createUser(id = 2L)
        val trip = createTrip(owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)

        val exception = assertBusinessException {
            tripInviteService.createInviteLink(
                userId = 2L,
                tripId = 10L,
            )
        }

        assertEquals(TripErrorCode.TRIP_OWNER_ONLY, exception.errorCode)
        verify(tripInvitationRepository, never()).saveAndFlush(any(TripInvitation::class.java))
    }

    @Test
    fun `초대 정보 조회는 이미 참여 중인지 함께 반환한다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val trip = createTrip(owner)
        val invitation = createInvitation(trip = trip, createdBy = owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(true)

        val response = tripInviteService.getInviteInfo(
            userId = 2L,
            code = "abc12345",
            token = null,
        )

        assertEquals(100L, response.invitationId)
        assertEquals("일본 여행", response.trip.title)
        assertEquals("재완", response.createdBy.nickname)
        assertEquals(true, response.alreadyJoined)
    }

    @Test
    fun `초대 조회 키가 없거나 둘 다 있으면 실패한다`() {
        val user = createUser(id = 2L)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(user)

        val missingException = assertBusinessException {
            tripInviteService.getInviteInfo(
                userId = 2L,
                code = null,
                token = null,
            )
        }
        val duplicatedException = assertBusinessException {
            tripInviteService.getInviteInfo(
                userId = 2L,
                code = "ABC12345",
                token = "token",
            )
        }

        assertEquals(TripErrorCode.INVALID_TRIP_INVITATION_LOOKUP, missingException.errorCode)
        assertEquals(TripErrorCode.INVALID_TRIP_INVITATION_LOOKUP, duplicatedException.errorCode)
    }

    @Test
    fun `초대 토큰으로 여행에 참여한다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val trip = createTrip(owner)
        val invitation = createInvitation(
            trip = trip,
            createdBy = owner,
            type = TripInvitationType.LINK,
            code = null,
            token = "invite-token",
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findLockedByTokenAndDeletedAtIsNull("invite-token")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(tripParticipantRepository.saveAndFlush(any(TripParticipant::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripParticipant).apply { id = 200L }
        }

        val response = tripInviteService.joinTrip(
            userId = 2L,
            request = JoinTripRequest(token = "invite-token"),
        )

        assertEquals(10L, response.tripId)
        assertEquals(200L, response.participant.id)
        assertEquals(2L, response.participant.userId)
        assertEquals(TripInvitationStatus.USED, invitation.invitationStatus)
        assertEquals(member, invitation.usedBy)
        assertEquals(Instant.parse("2026-06-11T00:00:00Z"), invitation.usedAt)
    }

    @Test
    fun `이미 참여 중이면 초대 참여에 실패한다`() {
        val owner = createUser()
        val member = createUser(id = 2L)
        val trip = createTrip(owner)
        val invitation = createInvitation(trip = trip, createdBy = owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(true)

        val exception = assertBusinessException {
            tripInviteService.joinTrip(
                userId = 2L,
                request = JoinTripRequest(code = "ABC12345"),
            )
        }

        assertEquals(TripErrorCode.TRIP_ALREADY_JOINED, exception.errorCode)
        verify(tripParticipantRepository, never()).saveAndFlush(any(TripParticipant::class.java))
    }

    @Test
    fun `만료된 초대 참여는 실패하고 상태를 만료로 전환한다`() {
        val owner = createUser()
        val member = createUser(id = 2L)
        val trip = createTrip(owner)
        val invitation = createInvitation(
            trip = trip,
            createdBy = owner,
            expiresAt = Instant.parse("2026-06-10T23:59:59Z"),
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)

        val exception = assertBusinessException {
            tripInviteService.joinTrip(
                userId = 2L,
                request = JoinTripRequest(code = "ABC12345"),
            )
        }

        assertEquals(TripErrorCode.TRIP_INVITATION_EXPIRED, exception.errorCode)
        assertEquals(TripInvitationStatus.EXPIRED, invitation.invitationStatus)
        verify(tripParticipantRepository, never()).saveAndFlush(any(TripParticipant::class.java))
    }

    private fun createUser(
        id: Long = 1L,
        nickname: String = "재완",
        status: UserStatus = UserStatus.ACTIVE,
    ): User {
        return User(
            nickname = nickname,
            profileImageUrl = null,
            status = status,
        ).apply {
            this.id = id
        }
    }

    private fun createTrip(ownerUser: User): Trip {
        return Trip(
            ownerUser = ownerUser,
            title = "일본 여행",
            defaultCurrency = "JPY",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 5),
        ).apply {
            id = 10L
        }
    }

    private fun createInvitation(
        trip: Trip,
        createdBy: User,
        type: TripInvitationType = TripInvitationType.CODE,
        code: String? = "ABC12345",
        token: String = "token",
        expiresAt: Instant = Instant.parse("2026-06-18T00:00:00Z"),
    ): TripInvitation {
        return TripInvitation(
            trip = trip,
            token = token,
            code = code,
            inviteUrl = "https://app.test/invites",
            invitationType = type,
            createdBy = createdBy,
            invitationStatus = TripInvitationStatus.ACTIVE,
            expiresAt = expiresAt,
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
