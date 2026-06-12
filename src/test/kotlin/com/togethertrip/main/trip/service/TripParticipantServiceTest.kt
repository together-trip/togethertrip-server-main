package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.dto.request.AddTripParticipantRequest
import com.togethertrip.main.trip.dto.request.LinkTripParticipantRequest
import com.togethertrip.main.trip.dto.request.UpdateTripParticipantRequest
import com.togethertrip.main.trip.dto.response.TripParticipantType
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.mockito.ArgumentMatchers.any
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
import kotlin.test.assertNull

class TripParticipantServiceTest {

    private lateinit var tripRepository: TripRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var userRepository: UserRepository
    private lateinit var tripParticipantService: TripParticipantService

    private val clock = Clock.fixed(
        Instant.parse("2026-06-12T00:00:00Z"),
        ZoneId.of("UTC"),
    )

    @BeforeEach
    fun setUp() {
        tripRepository = mock(TripRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        tripParticipantService = TripParticipantService(
            tripRepository = tripRepository,
            tripParticipantRepository = tripParticipantRepository,
            userRepository = userRepository,
            clock = clock,
        )
    }

    @Test
    fun `방장은 임시 참여자를 추가할 수 있다`() {
        val owner = createUser()
        val trip = createTrip(owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripParticipantRepository.save(any(TripParticipant::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripParticipant).apply { id = 100L }
        }

        val response = tripParticipantService.addTemporaryParticipant(
            userId = 1L,
            tripId = 10L,
            request = AddTripParticipantRequest(
                displayName = " 민서 ",
                profileImageUrl = " https://image.test/profile.png ",
            ),
        )

        assertEquals(100L, response.id)
        assertNull(response.userId)
        assertEquals("민서", response.displayName)
        assertEquals("https://image.test/profile.png", response.profileImageUrl)
        assertEquals(TripParticipantType.TEMPORARY, response.participantType)
        assertEquals(TripParticipantRole.MEMBER, response.participantRole)
        assertEquals(TripParticipantStatus.ACTIVE, response.participantStatus)
    }

    @Test
    fun `방장이 아니면 임시 참여자 추가에 실패한다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val trip = createTrip(owner)
        val memberParticipant = createParticipant(
            trip = trip,
            user = member,
            id = 200L,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(memberParticipant)

        val exception = assertBusinessException {
            tripParticipantService.addTemporaryParticipant(
                userId = 2L,
                tripId = 10L,
                request = AddTripParticipantRequest(displayName = "지훈"),
            )
        }

        assertEquals(TripErrorCode.TRIP_OWNER_ONLY, exception.errorCode)
        verify(tripParticipantRepository, never()).save(any(TripParticipant::class.java))
    }

    @Test
    fun `참여자 목록은 상태와 유형으로 필터링할 수 있다`() {
        val owner = createUser()
        val userParticipant = createParticipant(
            trip = createTrip(owner),
            user = owner,
            id = 100L,
            role = TripParticipantRole.LEADER,
        )
        val temporaryParticipant = createParticipant(
            trip = userParticipant.trip,
            user = null,
            id = 101L,
            displayName = "민서",
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(userParticipant.trip)
        `when`(
            tripParticipantRepository.findByTripIdAndParticipantStatusIncludingDeleted(
                10L,
                TripParticipantStatus.ACTIVE.name,
            )
        )
            .thenReturn(listOf(userParticipant, temporaryParticipant))

        val response = tripParticipantService.getParticipants(
            userId = 1L,
            tripId = 10L,
            status = "active",
            type = "temporary",
        )

        assertEquals(1, response.size)
        assertEquals(101L, response[0].id)
        assertEquals(TripParticipantType.TEMPORARY, response[0].participantType)
    }

    @Test
    fun `참여자 표시 정보를 수정한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val participant = createParticipant(
            trip = trip,
            user = null,
            id = 100L,
            displayName = "민서",
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripParticipantRepository.findByIdAndTripIdAndDeletedAtIsNull(100L, 10L)).thenReturn(participant)

        val response = tripParticipantService.updateParticipant(
            userId = 1L,
            tripId = 10L,
            participantId = 100L,
            request = UpdateTripParticipantRequest(
                displayName = " 지훈 ",
                profileImageUrl = "",
            ),
        )

        assertEquals("지훈", response.displayName)
        assertNull(response.profileImageUrl)
        assertEquals(Instant.parse("2026-06-12T00:00:00Z"), participant.updatedAt)
    }

    @Test
    fun `참여자 제거는 상태와 삭제 시각만 변경한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val participant = createParticipant(
            trip = trip,
            user = null,
            id = 100L,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripParticipantRepository.findByIdAndTripIdAndDeletedAtIsNull(100L, 10L)).thenReturn(participant)

        tripParticipantService.removeParticipant(
            userId = 1L,
            tripId = 10L,
            participantId = 100L,
        )

        assertEquals(TripParticipantStatus.REMOVED, participant.participantStatus)
        assertEquals(Instant.parse("2026-06-12T00:00:00Z"), participant.leftAt)
        assertEquals(Instant.parse("2026-06-12T00:00:00Z"), participant.deletedAt)
    }

    @Test
    fun `방장은 제거할 수 없다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val leader = createParticipant(
            trip = trip,
            user = owner,
            id = 100L,
            role = TripParticipantRole.LEADER,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripParticipantRepository.findByIdAndTripIdAndDeletedAtIsNull(100L, 10L)).thenReturn(leader)

        val exception = assertBusinessException {
            tripParticipantService.removeParticipant(
                userId = 1L,
                tripId = 10L,
                participantId = 100L,
            )
        }

        assertEquals(TripErrorCode.TRIP_LEADER_REMOVE_DENIED, exception.errorCode)
        assertNull(leader.deletedAt)
    }

    @Test
    fun `임시 참여자를 회원과 연결한다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서", profileImageUrl = "https://image.test/member.png")
        val trip = createTrip(owner)
        val temporaryParticipant = createParticipant(
            trip = trip,
            user = null,
            id = 100L,
            displayName = "임시 민서",
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                100L,
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(temporaryParticipant)
        `when`(tripParticipantRepository.saveAndFlush(temporaryParticipant)).thenReturn(temporaryParticipant)

        val response = tripParticipantService.linkTemporaryParticipant(
            userId = 1L,
            tripId = 10L,
            request = LinkTripParticipantRequest(
                participantId = 100L,
                userId = 2L,
            ),
        )

        assertEquals(2L, response.userId)
        assertEquals("민서", response.displayName)
        assertEquals("https://image.test/member.png", response.profileImageUrl)
        assertEquals(TripParticipantType.USER, response.participantType)
        assertEquals(Instant.parse("2026-06-12T00:00:00Z"), temporaryParticipant.joinedAt)
    }

    @Test
    fun `임시 참여자 연결 중 DB 중복 제약이 발생하면 중복 참여로 실패한다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val trip = createTrip(owner)
        val temporaryParticipant = createParticipant(
            trip = trip,
            user = null,
            id = 100L,
            displayName = "임시 민서",
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                100L,
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(temporaryParticipant)
        `when`(tripParticipantRepository.saveAndFlush(temporaryParticipant))
            .thenThrow(DataIntegrityViolationException("duplicate active participant"))

        val exception = assertBusinessException {
            tripParticipantService.linkTemporaryParticipant(
                userId = 1L,
                tripId = 10L,
                request = LinkTripParticipantRequest(
                    participantId = 100L,
                    userId = 2L,
                ),
            )
        }

        assertEquals(TripErrorCode.TRIP_ALREADY_JOINED, exception.errorCode)
    }

    @Test
    fun `이미 활성 참여자인 사용자는 임시 참여자에 연결할 수 없다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val trip = createTrip(owner)

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(true)

        val exception = assertBusinessException {
            tripParticipantService.linkTemporaryParticipant(
                userId = 1L,
                tripId = 10L,
                request = LinkTripParticipantRequest(
                    participantId = 100L,
                    userId = 2L,
                ),
            )
        }

        assertEquals(TripErrorCode.TRIP_ALREADY_JOINED, exception.errorCode)
        verify(tripParticipantRepository, never()).findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
            100L,
            10L,
            TripParticipantStatus.ACTIVE,
        )
    }

    @Test
    fun `이미 회원과 연결된 참여자는 다시 연결할 수 없다`() {
        val owner = createUser()
        val member = createUser(id = 2L, nickname = "민서")
        val otherMember = createUser(id = 3L, nickname = "지훈")
        val trip = createTrip(owner)
        val linkedParticipant = createParticipant(
            trip = trip,
            user = otherMember,
            id = 100L,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(member)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                2L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(false)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                100L,
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(linkedParticipant)

        val exception = assertBusinessException {
            tripParticipantService.linkTemporaryParticipant(
                userId = 1L,
                tripId = 10L,
                request = LinkTripParticipantRequest(
                    participantId = 100L,
                    userId = 2L,
                ),
            )
        }

        assertEquals(TripErrorCode.TRIP_PARTICIPANT_ALREADY_LINKED, exception.errorCode)
    }

    private fun createUser(
        id: Long = 1L,
        nickname: String = "동현",
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

    private fun createParticipant(
        trip: Trip,
        user: User?,
        id: Long,
        displayName: String = user?.nickname ?: "임시 참여자",
        role: TripParticipantRole = TripParticipantRole.MEMBER,
        status: TripParticipantStatus = TripParticipantStatus.ACTIVE,
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = displayName,
            profileImageUrl = user?.profileImageUrl,
            participantRole = role,
            participantStatus = status,
            joinedAt = user?.let { Instant.parse("2026-06-01T00:00:00Z") },
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
