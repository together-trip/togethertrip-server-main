package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.trip.TripParticipantJoinedPayload
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripInvitation
import com.togethertrip.main.trip.domain.TripInvitationStatus
import com.togethertrip.main.trip.domain.TripInvitationType
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.dto.request.JoinTripRequest
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripInvitationRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.trip.service.support.TripNotificationRecipientResolver
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import tools.jackson.module.kotlin.jacksonObjectMapper
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
    private lateinit var tripInvitationExpirationService: TripInvitationExpirationService
    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var outboxEventPublisher: OutboxEventPublisher
    private lateinit var tripNotificationRecipientResolver: TripNotificationRecipientResolver
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
        tripInvitationExpirationService = mock(TripInvitationExpirationService::class.java)
        outboxEventRepository = mock(OutboxEventRepository::class.java)
        outboxEventPublisher = OutboxEventPublisher(
            outboxEventRepository = outboxEventRepository,
            objectMapper = jacksonObjectMapper(),
        )
        tripNotificationRecipientResolver = TripNotificationRecipientResolver(tripParticipantRepository)
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as OutboxEvent
        }
        tripInviteService = TripInviteService(
            tripRepository = tripRepository,
            tripInvitationRepository = tripInvitationRepository,
            tripParticipantRepository = tripParticipantRepository,
            userRepository = userRepository,
            tripInvitationExpirationService = tripInvitationExpirationService,
            outboxEventPublisher = outboxEventPublisher,
            tripNotificationRecipientResolver = tripNotificationRecipientResolver,
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
    fun `방장은 token 기반 초대 링크를 생성할 수 있다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripInvitationRepository.existsByTokenAndDeletedAtIsNull(anyString())).thenReturn(false)
        `when`(tripInvitationRepository.saveAndFlush(any(TripInvitation::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripInvitation).apply { id = 101L }
        }

        val response = tripInviteService.createInviteLink(1L, 10L)

        assertEquals(TripInvitationType.LINK, response.type)
        assertEquals(null, response.code)
        assertTrue(response.inviteUrl.startsWith("https://app.test/invites?token="))
        assertEquals(43, response.token.length)
    }

    @Test
    fun `token 또는 code 충돌이 다섯 번 반복되면 동시 수정 오류로 종료한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripInvitationRepository.existsByTokenAndDeletedAtIsNull(anyString())).thenReturn(true)

        val tokenCollision = assertBusinessException {
            tripInviteService.createInviteCode(1L, 10L)
        }
        assertEquals(CommonErrorCode.CONCURRENT_MODIFICATION, tokenCollision.errorCode)

        `when`(tripInvitationRepository.existsByTokenAndDeletedAtIsNull(anyString())).thenReturn(false)
        `when`(tripInvitationRepository.existsByCodeAndDeletedAtIsNull(anyString())).thenReturn(true)
        val codeCollision = assertBusinessException {
            tripInviteService.createInviteCode(1L, 10L)
        }
        assertEquals(CommonErrorCode.CONCURRENT_MODIFICATION, codeCollision.errorCode)
        verify(tripInvitationRepository, never()).saveAndFlush(any(TripInvitation::class.java))
    }

    @Test
    fun `초대 저장 unique 충돌은 동시 수정 오류로 변환한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripInvitationRepository.existsByTokenAndDeletedAtIsNull(anyString())).thenReturn(false)
        `when`(tripInvitationRepository.existsByCodeAndDeletedAtIsNull(anyString())).thenReturn(false)
        `when`(tripInvitationRepository.saveAndFlush(any(TripInvitation::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate invitation"))

        val exception = assertBusinessException {
            tripInviteService.createInviteCode(1L, 10L)
        }

        assertEquals(CommonErrorCode.CONCURRENT_MODIFICATION, exception.errorCode)
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
    fun `초대 조회는 찾을 수 없음과 비활성 상태를 구분한다`() {
        val user = createUser(id = 2L)
        val invitation = createInvitation(createTrip(createUser()), createUser()).apply {
            invitationStatus = TripInvitationStatus.USED
        }
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(user)

        val missing = assertBusinessException {
            tripInviteService.getInviteInfo(2L, null, "missing-token")
        }
        assertEquals(TripErrorCode.TRIP_INVITATION_NOT_FOUND, missing.errorCode)

        `when`(tripInvitationRepository.findByTokenAndDeletedAtIsNull("used-token")).thenReturn(invitation)
        val inactive = assertBusinessException {
            tripInviteService.getInviteInfo(2L, null, " used-token ")
        }
        assertEquals(TripErrorCode.TRIP_INVITATION_NOT_ACTIVE, inactive.errorCode)
    }

    @Test
    fun `공백 code와 token은 없는 값으로 정규화하고 다른 조회 키를 사용한다`() {
        val user = createUser(id = 2L)
        val owner = createUser()
        val invitation = createInvitation(createTrip(owner), owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(user)
        `when`(tripInvitationRepository.findByTokenAndDeletedAtIsNull("token-value")).thenReturn(invitation)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)

        val byToken = tripInviteService.getInviteInfo(2L, "   ", " token-value ")
        val byCode = tripInviteService.getInviteInfo(2L, " abc12345 ", "   ")

        assertEquals(100L, byToken.invitationId)
        assertEquals(100L, byCode.invitationId)
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
        `when`(tripInvitationRepository.findByTokenAndDeletedAtIsNull("invite-token")).thenReturn(invitation)
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
        `when`(
            tripParticipantRepository.findActiveUserIdsForNotification(
                tripId = 10L,
                participantStatus = TripParticipantStatus.ACTIVE.name,
                userStatus = UserStatus.ACTIVE.name,
            )
        ).thenReturn(listOf(1L, 2L))

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

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        val event = eventCaptor.value
        val payload = jacksonObjectMapper().readValue(
            event.payload,
            TripParticipantJoinedPayload::class.java,
        )
        assertEquals(OutboxEventType.TRIP_PARTICIPANT_JOINED.name, event.eventType)
        assertEquals(listOf(1L), payload.recipients.map { it.userId })
        assertEquals(200L, payload.participantId)
        assertEquals("민서", payload.actorDisplayName)
    }

    @Test
    fun `새 참여자 저장 unique 충돌은 이미 참여 중 오류로 변환한다`() {
        val owner = createUser()
        val member = createUser(id = 2L)
        val trip = createTrip(owner)
        val invitation = createInvitation(trip, owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(tripParticipantRepository.saveAndFlush(any(TripParticipant::class.java)))
            .thenThrow(DataIntegrityViolationException("duplicate participant"))

        val exception = assertBusinessException {
            tripInviteService.joinTrip(2L, JoinTripRequest(code = "ABC12345"))
        }

        assertEquals(TripErrorCode.TRIP_ALREADY_JOINED, exception.errorCode)
        assertEquals(TripInvitationStatus.ACTIVE, invitation.invitationStatus)
    }

    @Test
    fun `사용자와 여행 조회 실패는 각각 계약된 오류로 구분한다`() {
        val missingUser = assertBusinessException {
            tripInviteService.createInviteCode(999L, 10L)
        }
        assertEquals(UserErrorCode.USER_NOT_FOUND, missingUser.errorCode)

        val inactive = createUser(status = UserStatus.SUSPENDED)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(inactive)
        val inactiveUser = assertBusinessException {
            tripInviteService.createInviteCode(1L, 10L)
        }
        assertEquals(UserErrorCode.INACTIVE_USER, inactiveUser.errorCode)

        val active = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(active)
        val missingTrip = assertBusinessException {
            tripInviteService.createInviteCode(1L, 999L)
        }
        assertEquals(TripErrorCode.TRIP_NOT_FOUND, missingTrip.errorCode)
    }

    @Test
    fun `초대 참여 시 비회원 참여자를 현재 회원과 연결한다`() {
        val owner = createUser()
        val member = createUser(
            id = 2L,
            nickname = "민서",
            profileImageUrl = "https://image.test/member.png",
        )
        val trip = createTrip(owner)
        val invitation = createInvitation(trip = trip, createdBy = owner)
        val temporaryParticipant = createParticipant(
            trip = trip,
            id = 300L,
            user = null,
            displayName = "임시 민서",
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                300L,
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(temporaryParticipant)
        `when`(tripParticipantRepository.saveAndFlush(temporaryParticipant)).thenReturn(temporaryParticipant)

        val response = tripInviteService.joinTrip(
            userId = 2L,
            request = JoinTripRequest(
                code = "ABC12345",
                participantId = 300L,
            ),
        )

        assertEquals(10L, response.tripId)
        assertEquals(300L, response.participant.id)
        assertEquals(2L, response.participant.userId)
        assertEquals("민서", response.participant.displayName)
        assertEquals("https://image.test/member.png", response.participant.profileImageUrl)
        assertEquals(member, temporaryParticipant.user)
        assertEquals(Instant.parse("2026-06-11T00:00:00Z"), temporaryParticipant.joinedAt)
        assertEquals(TripInvitationStatus.USED, invitation.invitationStatus)
        assertEquals(member, invitation.usedBy)
    }

    @Test
    fun `임시 참여자 연결 저장 unique 충돌은 이미 참여 중 오류로 변환한다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val trip = createTrip(owner)
        val invitation = createInvitation(trip, owner)
        val temporaryParticipant = createParticipant(trip, 300L, null)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                300L,
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(temporaryParticipant)
        `when`(tripParticipantRepository.saveAndFlush(temporaryParticipant))
            .thenThrow(DataIntegrityViolationException("duplicate participant"))

        val exception = assertBusinessException {
            tripInviteService.joinTrip(
                2L,
                JoinTripRequest(code = "ABC12345", participantId = 300L),
            )
        }

        assertEquals(TripErrorCode.TRIP_ALREADY_JOINED, exception.errorCode)
        assertEquals(TripInvitationStatus.ACTIVE, invitation.invitationStatus)
    }

    @Test
    fun `초대 참여에서 이미 회원과 연결된 참여자는 선택할 수 없다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val linkedUser = createUser(id = 3L, nickname = "지훈")
        val trip = createTrip(owner)
        val invitation = createInvitation(trip = trip, createdBy = owner)
        val linkedParticipant = createParticipant(
            trip = trip,
            id = 300L,
            user = linkedUser,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                300L,
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(linkedParticipant)

        val exception = assertBusinessException {
            tripInviteService.joinTrip(
                userId = 2L,
                request = JoinTripRequest(
                    code = "ABC12345",
                    participantId = 300L,
                ),
            )
        }

        assertEquals(TripErrorCode.TRIP_PARTICIPANT_ALREADY_LINKED, exception.errorCode)
        assertEquals(TripInvitationStatus.ACTIVE, invitation.invitationStatus)
        verify(tripParticipantRepository, never()).saveAndFlush(linkedParticipant)
    }

    @Test
    fun `초대 참여에서 다른 여행의 참여자 ID는 찾을 수 없다`() {
        val owner = createUser()
        val member = createUser(id = 2L)
        val trip = createTrip(owner)
        val invitation = createInvitation(trip = trip, createdBy = owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                999L,
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            tripInviteService.joinTrip(
                userId = 2L,
                request = JoinTripRequest(
                    code = "ABC12345",
                    participantId = 999L,
                ),
            )
        }

        assertEquals(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND, exception.errorCode)
        assertEquals(TripInvitationStatus.ACTIVE, invitation.invitationStatus)
    }

    @Test
    fun `이미 참여 중이면 초대 참여에 실패한다`() {
        val owner = createUser()
        val member = createUser(id = 2L)
        val trip = createTrip(owner)
        val invitation = createInvitation(trip = trip, createdBy = owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
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
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)

        val exception = assertBusinessException {
            tripInviteService.joinTrip(
                userId = 2L,
                request = JoinTripRequest(code = "ABC12345"),
            )
        }

        assertEquals(TripErrorCode.TRIP_INVITATION_EXPIRED, exception.errorCode)
        verify(tripInvitationExpirationService).markExpiredIfActive(
            invitationId = 100L,
            now = Instant.parse("2026-06-11T00:00:00Z"),
        )
        verify(tripParticipantRepository, never()).saveAndFlush(any(TripParticipant::class.java))
    }

    @Test
    fun `정산이 시작된 여행은 초대 참여에 실패한다`() {
        val owner = createUser()
        val member = createUser(id = 2L)
        val trip = createTrip(
            ownerUser = owner,
            settlementStatus = TripSettlementStatus.IN_PROGRESS,
        )
        val invitation = createInvitation(trip = trip, createdBy = owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripInvitationRepository.findByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)
        `when`(tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull("ABC12345")).thenReturn(invitation)

        val exception = assertBusinessException {
            tripInviteService.joinTrip(
                userId = 2L,
                request = JoinTripRequest(code = "ABC12345"),
            )
        }

        assertEquals(TripErrorCode.TRIP_JOIN_CLOSED, exception.errorCode)
        verify(tripParticipantRepository, never()).saveAndFlush(any(TripParticipant::class.java))
    }

    private fun createUser(
        id: Long = 1L,
        nickname: String = "재완",
        profileImageUrl: String? = null,
        status: UserStatus = UserStatus.ACTIVE,
    ): User {
        return User(
            nickname = nickname,
            profileImageUrl = profileImageUrl,
            status = status,
        ).apply {
            this.id = id
        }
    }

    private fun createTrip(
        ownerUser: User,
        settlementStatus: TripSettlementStatus = TripSettlementStatus.NOT_STARTED,
    ): Trip {
        return Trip(
            ownerUser = ownerUser,
            title = "일본 여행",
            defaultCurrency = "JPY",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 5),
            settlementStatus = settlementStatus,
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

    private fun createParticipant(
        trip: Trip,
        id: Long,
        user: User?,
        displayName: String = user?.nickname ?: "임시 참여자",
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = displayName,
            profileImageUrl = user?.profileImageUrl,
            participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
            joinedAt = if (user == null) null else Instant.parse("2026-06-01T00:00:00Z"),
        ).apply {
            this.id = id
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
