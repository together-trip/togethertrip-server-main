package com.togethertrip.main.triprecap.service

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.triprecap.TripRecapCompletedPayload
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapScene
import com.togethertrip.main.triprecap.domain.TripRecapStatus
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.triprecap.repository.TripRecapSceneRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripRecapGenerationCompletionServiceTest {

    private lateinit var tripRecapRepository: TripRecapRepository
    private lateinit var tripRecapSceneRepository: TripRecapSceneRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var completionService: TripRecapGenerationCompletionService

    @BeforeEach
    fun setUp() {
        tripRecapRepository = mock(TripRecapRepository::class.java)
        tripRecapSceneRepository = mock(TripRecapSceneRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        outboxEventRepository = mock(OutboxEventRepository::class.java)
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as OutboxEvent
        }
        completionService = TripRecapGenerationCompletionService(
            tripRecapRepository = tripRecapRepository,
            tripRecapSceneRepository = tripRecapSceneRepository,
            tripParticipantRepository = tripParticipantRepository,
            outboxEventPublisher = OutboxEventPublisher(
                outboxEventRepository = outboxEventRepository,
                objectMapper = jacksonObjectMapper(),
            ),
        )
    }

    @Test
    fun `생성 완료 시 scene 저장 후 완료 상태로 변경하고 멤버 전체 알림 이벤트를 발행한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val recap = TripRecap(
            trip = trip,
            requestedBy = owner,
            style = TripRecapStyle.PHOTO,
        ).apply { id = 100L }
        `when`(tripRecapRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(recap)
        `when`(
            tripParticipantRepository.findActiveUserIdsForNotification(
                tripId = 10L,
                participantStatus = "ACTIVE",
                userStatus = "ACTIVE",
            )
        ).thenReturn(listOf(1L, 2L))

        completionService.complete(
            recapId = 100L,
            scenes = listOf(
                TripRecapStoredScene(
                    order = 1,
                    sceneDescription = "description",
                    imagePrompt = "prompt",
                    imageObjectKey = "trip-recaps/10/100/1.png",
                    imageUrl = "/uploads/trip-recaps/10/100/1.png",
                    generationProvider = "stub",
                    generationModel = "stub-v1",
                )
            ),
            completedAt = Instant.parse("2026-07-06T12:00:00Z"),
        )

        assertEquals(TripRecapStatus.COMPLETED, recap.status)
        assertEquals(1, recap.sceneCount)
        assertNull(recap.failureReason)
        verify(tripRecapSceneRepository).softDeleteByRecapId(
            recapId = 100L,
            deletedAt = Instant.parse("2026-07-06T12:00:00Z"),
        )

        val sceneCaptor = ArgumentCaptor.forClass(TripRecapScene::class.java)
        verify(tripRecapSceneRepository).save(sceneCaptor.capture())
        assertEquals("prompt", sceneCaptor.value.imagePrompt)
        assertEquals("/uploads/trip-recaps/10/100/1.png", sceneCaptor.value.imageUrl)

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        assertEquals(OutboxEventType.TRIP_RECAP_COMPLETED.name, eventCaptor.value.eventType)
        val payload = jacksonObjectMapper().readValue(
            eventCaptor.value.payload,
            TripRecapCompletedPayload::class.java,
        )
        assertEquals(10L, payload.tripId)
        assertEquals(100L, payload.tripRecapId)
        assertEquals("제주 여행", payload.tripName)
        assertEquals(listOf(1L, 2L), payload.recipients.map { it.userId })
    }

    private fun createUser(): User {
        return User(
            nickname = "재완",
            status = UserStatus.ACTIVE,
        ).apply {
            id = 1L
        }
    }

    private fun createTrip(owner: User): Trip {
        return Trip(
            ownerUser = owner,
            title = "제주 여행",
            defaultCurrency = "KRW",
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 3),
            settlementStatus = TripSettlementStatus.SETTLED,
        ).apply {
            id = 10L
        }
    }
}
