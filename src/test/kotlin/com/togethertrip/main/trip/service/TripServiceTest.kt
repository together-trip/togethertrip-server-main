package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.trip.TripParticipantsAddedPayload
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.global.storage.ProfileImageUrlPolicy
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripCountry
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripStatus
import com.togethertrip.main.trip.dto.request.CreateTripRequest
import com.togethertrip.main.trip.dto.request.TripCompanionInput
import com.togethertrip.main.trip.dto.request.TripCountryInput
import com.togethertrip.main.trip.dto.request.UpdateTripCountriesRequest
import com.togethertrip.main.trip.dto.request.UpdateTripRequest
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripCountryRepository
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TripServiceTest {

    private lateinit var tripRepository: TripRepository
    private lateinit var tripCountryRepository: TripCountryRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var userRepository: UserRepository
    private lateinit var profileImageUrlPolicy: ProfileImageUrlPolicy
    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var outboxEventPublisher: OutboxEventPublisher
    private lateinit var tripNotificationRecipientResolver: TripNotificationRecipientResolver
    private lateinit var tripService: TripService

    @BeforeEach
    fun setUp() {
        tripRepository = mock(TripRepository::class.java)
        tripCountryRepository = mock(TripCountryRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        outboxEventRepository = mock(OutboxEventRepository::class.java)
        outboxEventPublisher = OutboxEventPublisher(
            outboxEventRepository = outboxEventRepository,
            objectMapper = jacksonObjectMapper(),
        )
        tripNotificationRecipientResolver = TripNotificationRecipientResolver(tripParticipantRepository)
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as OutboxEvent
        }
        profileImageUrlPolicy = ProfileImageUrlPolicy(
            userProfileImagePublicUrlPrefix = "/uploads/user-profile-images",
        )
        tripService = TripService(
            tripRepository = tripRepository,
            tripCountryRepository = tripCountryRepository,
            tripParticipantRepository = tripParticipantRepository,
            userRepository = userRepository,
            profileImageUrlPolicy = profileImageUrlPolicy,
            outboxEventPublisher = outboxEventPublisher,
            tripNotificationRecipientResolver = tripNotificationRecipientResolver,
        )
    }

    @Test
    fun `여행 생성에 성공한다`() {
        val user = createUser()
        val savedCountries = mutableListOf<TripCountry>()
        val savedParticipants = mutableListOf<TripParticipant>()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        `when`(tripRepository.save(any(Trip::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Trip).apply { id = 10L }
        }
        `when`(tripParticipantRepository.save(any(TripParticipant::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripParticipant).apply {
                id = (100L + savedParticipants.size)
                savedParticipants.add(this)
            }
        }
        `when`(tripCountryRepository.save(any(TripCountry::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripCountry).apply {
                id = (200L + savedCountries.size)
                savedCountries.add(this)
            }
        }
        `when`(tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(10L))
            .thenAnswer { savedCountries.sortedBy { country -> country.sortOrder } }
        `when`(tripParticipantRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(10L))
            .thenAnswer { savedParticipants }
        val response = tripService.createTrip(
            userId = 1L,
            request = CreateTripRequest(
                title = "오사카 여행",
                defaultCurrency = "jpy",
                startDate = LocalDate.now().plusDays(1),
                endDate = LocalDate.now().plusDays(3),
                countries = listOf(
                    TripCountryInput(
                        countryCode = "jp",
                        countryName = "일본",
                    )
                ),
                participants = listOf(
                    TripCompanionInput(
                        displayName = "동행자1",
                    )
                ),
            ),
        )

        assertEquals(10L, response.id)
        assertEquals("오사카 여행", response.title)
        assertEquals("JPY", response.defaultCurrency)
        assertEquals(TripStatus.PLANNED, response.tripStatus)
        assertEquals(1, response.countries.size)
        assertEquals("JP", response.countries.first().countryCode)
        assertEquals(2, response.participants.size)
        assertEquals(TripParticipantRole.LEADER, response.participants.first().participantRole)
        assertEquals("동행자1", response.participants.last().displayName)
    }

    @Test
    fun `여행 생성 시 사용자 동행자를 바로 연결한다`() {
        val owner = createUser()
        val companionUser = createUser(
            id = 2L,
            nickname = "동행자",
        )
        val savedParticipants = mutableListOf<TripParticipant>()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(companionUser)
        `when`(tripRepository.save(any(Trip::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Trip).apply { id = 10L }
        }
        `when`(tripParticipantRepository.save(any(TripParticipant::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripParticipant).apply {
                id = (100L + savedParticipants.size)
                savedParticipants.add(this)
            }
        }
        `when`(tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(10L))
            .thenReturn(emptyList())
        `when`(tripParticipantRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(10L))
            .thenAnswer { savedParticipants }

        val response = tripService.createTrip(
            userId = 1L,
            request = CreateTripRequest(
                title = "오사카 여행",
                defaultCurrency = "JPY",
                participants = listOf(
                    TripCompanionInput(
                        displayName = "동행자",
                        userId = 2L,
                    )
                ),
            ),
        )

        assertEquals(2, response.participants.size)
        assertEquals(2L, response.participants.last().userId)
        assertEquals("동행자", response.participants.last().displayName)
        assertNotNull(savedParticipants.last().joinedAt)

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        val event = eventCaptor.value
        val payload = jacksonObjectMapper().readValue(
            event.payload,
            TripParticipantsAddedPayload::class.java,
        )
        assertEquals(OutboxEventType.TRIP_PARTICIPANTS_ADDED.name, event.eventType)
        assertEquals(listOf(2L), payload.recipients.map { it.userId })
        assertEquals(listOf(101L), payload.participantIds)
        assertEquals("오사카 여행", payload.tripName)
        assertEquals("재완", payload.actorDisplayName)
    }

    @Test
    fun `여행 생성 시 방장을 사용자 동행자로 추가하면 실패한다`() {
        val user = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)

        val exception = assertBusinessException {
            tripService.createTrip(
                userId = 1L,
                request = CreateTripRequest(
                    title = "오사카 여행",
                    defaultCurrency = "JPY",
                    participants = listOf(
                        TripCompanionInput(
                            displayName = "재완",
                            userId = 1L,
                        )
                    ),
                ),
            )
        }

        assertEquals(TripErrorCode.TRIP_ALREADY_JOINED, exception.errorCode)
        verify(tripRepository, never()).save(any(Trip::class.java))
    }

    @Test
    fun `시작일이 종료일보다 늦으면 여행 생성에 실패한다`() {
        val user = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)

        val exception = assertBusinessException {
            tripService.createTrip(
                userId = 1L,
                request = CreateTripRequest(
                    title = "유럽 여행",
                    defaultCurrency = "EUR",
                    startDate = LocalDate.of(2026, 6, 10),
                    endDate = LocalDate.of(2026, 6, 1),
                ),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verify(tripRepository, never()).save(any(Trip::class.java))
    }

    @Test
    fun `허용되지 않는 동행자 프로필 이미지 URL이면 여행 생성에 실패한다`() {
        val user = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)

        val exception = assertBusinessException {
            tripService.createTrip(
                userId = 1L,
                request = CreateTripRequest(
                    title = "오사카 여행",
                    defaultCurrency = "JPY",
                    participants = listOf(
                        TripCompanionInput(
                            displayName = "동행자1",
                            profileImageUrl = "javascript:alert(1)",
                        )
                    ),
                ),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verify(tripRepository, never()).save(any(Trip::class.java))
    }

    @Test
    fun `여행 목록을 상태 조건으로 조회한다`() {
        val user = createUser()
        val trip = createTrip(ownerUser = user).apply {
            tripStatus = TripStatus.ONGOING
        }

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        `when`(
            tripRepository.findAccessibleTrips(
                1L,
                TripStatus.ONGOING,
                PageRequest.of(0, 21),
            )
        ).thenReturn(listOf(trip))

        val response = tripService.getTrips(
            userId = 1L,
            status = "ongoing",
            cursor = null,
            size = 20,
        )

        assertEquals(1, response.items.size)
        assertEquals(10L, response.items.first().id)
        assertEquals(TripStatus.ONGOING, response.items.first().tripStatus)
        assertEquals(false, response.hasNext)
        assertEquals(null, response.nextCursor)
    }

    @Test
    fun `유효하지 않은 여행 상태로 목록 조회하면 실패한다`() {
        val user = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)

        val exception = assertBusinessException {
            tripService.getTrips(
                userId = 1L,
                status = "invalid",
                cursor = null,
                size = 20,
            )
        }

        assertEquals(TripErrorCode.INVALID_TRIP_STATUS, exception.errorCode)
    }

    @Test
    fun `커서 기반으로 다음 여행 목록을 조회한다`() {
        val user = createUser()
        val firstTrip = createTrip(ownerUser = user).apply {
            id = 10L
            createdAt = Instant.parse("2026-06-05T12:30:00Z")
        }
        val secondTrip = createTrip(ownerUser = user).apply {
            id = 9L
            createdAt = Instant.parse("2026-06-05T12:20:00Z")
        }
        val extraTrip = createTrip(ownerUser = user).apply {
            id = 8L
            createdAt = Instant.parse("2026-06-05T12:10:00Z")
        }

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        `when`(
            tripRepository.findAccessibleTripsAfterCursor(
                1L,
                null,
                Instant.parse("2026-06-05T13:00:00Z"),
                20L,
                PageRequest.of(0, 3),
            )
        ).thenReturn(listOf(firstTrip, secondTrip, extraTrip))

        val response = tripService.getTrips(
            userId = 1L,
            status = null,
            cursor = "2026-06-05T13:00:00Z_20",
            size = 2,
        )

        assertEquals(2, response.items.size)
        assertEquals(true, response.hasNext)
        assertEquals("2026-06-05T12:20:00Z_9", response.nextCursor)
    }

    @Test
    fun `잘못된 커서로 여행 목록 조회하면 실패한다`() {
        val user = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)

        val exception = assertBusinessException {
            tripService.getTrips(
                userId = 1L,
                status = null,
                cursor = "invalid-cursor",
                size = 20,
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `참여자는 여행 상세를 조회할 수 있다`() {
        val owner = createUser(id = 2L, nickname = "방장")
        val requester = createUser(id = 1L)
        val trip = createTrip(ownerUser = owner)
        val participant = createParticipant(
            id = 101L,
            trip = trip,
            user = requester,
            role = TripParticipantRole.MEMBER,
        )
        val leader = createParticipant(
            id = 100L,
            trip = trip,
            user = owner,
            role = TripParticipantRole.LEADER,
        )
        val country = createCountry(trip)

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(requester)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(participant)
        `when`(tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(10L)).thenReturn(listOf(country))
        `when`(tripParticipantRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc(10L)).thenReturn(listOf(leader, participant))

        val response = tripService.getTrip(
            userId = 1L,
            tripId = 10L,
        )

        assertEquals(10L, response.id)
        assertEquals(1, response.countries.size)
        assertEquals(2, response.participants.size)
    }

    @Test
    fun `소유자가 아니면 여행 수정에 실패한다`() {
        val owner = createUser(id = 2L, nickname = "방장")
        val requester = createUser(id = 1L)
        val trip = createTrip(ownerUser = owner)
        val participant = createParticipant(
            id = 101L,
            trip = trip,
            user = requester,
            role = TripParticipantRole.MEMBER,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(requester)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(participant)

        val exception = assertBusinessException {
            tripService.updateTrip(
                userId = 1L,
                tripId = 10L,
                request = UpdateTripRequest(title = "새 제목"),
            )
        }

        assertEquals(TripErrorCode.TRIP_OWNER_ONLY, exception.errorCode)
    }

    @Test
    fun `여행 국가 목록을 변경한다`() {
        val owner = createUser(id = 1L)
        val trip = createTrip(ownerUser = owner)
        val oldCountry = createCountry(trip)
        val savedCountries = mutableListOf<TripCountry>()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(10L)).thenReturn(listOf(oldCountry))
        `when`(tripCountryRepository.save(any(TripCountry::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripCountry).apply {
                id = (300L + savedCountries.size)
                savedCountries.add(this)
            }
        }

        val response = tripService.updateTripCountries(
            userId = 1L,
            tripId = 10L,
            request = UpdateTripCountriesRequest(
                countries = listOf(
                    TripCountryInput(
                        countryCode = "KR",
                        countryName = "대한민국",
                        sortOrder = 0,
                    ),
                    TripCountryInput(
                        countryCode = "JP",
                        countryName = "일본",
                        sortOrder = 1,
                    ),
                )
            ),
        )

        assertEquals(2, response.countries.size)
        assertEquals("KR", response.countries.first().countryCode)
        assertEquals("JP", response.countries.last().countryCode)
        assertEquals(null, oldCountry.deletedAt)
        assertEquals(1, oldCountry.sortOrder)
        assertEquals(1, savedCountries.size)
    }

    @Test
    fun `비활성 사용자는 여행 조회에 실패한다`() {
        val suspendedUser = createUser(status = UserStatus.SUSPENDED)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(suspendedUser)

        val exception = assertBusinessException {
            tripService.getTrips(
                userId = 1L,
                status = null,
                cursor = null,
                size = 20,
            )
        }

        assertEquals(UserErrorCode.INACTIVE_USER, exception.errorCode)
        verifyNoInteractions(tripRepository)
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

    private fun createParticipant(
        id: Long,
        trip: Trip,
        user: User?,
        role: TripParticipantRole,
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = user?.nickname ?: "임시 동행자",
            participantRole = role,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            this.id = id
        }
    }

    private fun createCountry(trip: Trip): TripCountry {
        return TripCountry(
            trip = trip,
            countryCode = "JP",
            countryName = "일본",
            sortOrder = 0,
        ).apply {
            id = 200L
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
