package com.togethertrip.main.triprecap.service

import com.togethertrip.main.global.exception.BusinessException
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
import com.togethertrip.main.triprecap.exception.TripRecapErrorCode
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
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
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
        verify(tripRecapRepository).save(recap)
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

    @Test
    fun `완료 대상 recap이 없으면 명시적 오류를 반환한다`() {
        val exception = assertBusinessException {
            completionService.complete(999L, emptyList())
        }

        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(tripRecapSceneRepository, tripParticipantRepository, outboxEventRepository)
    }

    @Test
    fun `이미 완료된 recap의 중복 완료 요청은 아무 상태도 변경하지 않는다`() {
        val owner = createUser()
        val recap = TripRecap(createTrip(owner), owner, TripRecapStyle.PHOTO).apply {
            id = 100L
            complete(1, Instant.parse("2026-07-06T11:00:00Z"))
        }
        `when`(tripRecapRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(recap)

        completionService.complete(100L, emptyList(), Instant.parse("2026-07-06T12:00:00Z"))

        assertEquals(TripRecapStatus.COMPLETED, recap.status)
        assertEquals(1, recap.sceneCount)
        verify(tripRecapRepository, never()).save(recap)
        verifyNoInteractions(tripRecapSceneRepository, tripParticipantRepository, outboxEventRepository)
    }

    @Test
    fun `생성 실패는 예외 메시지를 저장하고 이미 완료된 recap은 변경하지 않는다`() {
        val owner = createUser()
        val creating = TripRecap(createTrip(owner), owner, TripRecapStyle.PHOTO).apply { id = 100L }
        val completed = TripRecap(createTrip(owner), owner, TripRecapStyle.PHOTO).apply {
            id = 101L
            complete(1, Instant.parse("2026-07-06T11:00:00Z"))
        }
        `when`(tripRecapRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(creating)
        `when`(tripRecapRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(completed)

        completionService.fail(100L, IllegalStateException("provider timeout"), Instant.parse("2026-07-06T12:00:00Z"))
        completionService.fail(101L, IllegalStateException("must be ignored"), Instant.parse("2026-07-06T12:00:00Z"))

        assertEquals(TripRecapStatus.FAILED, creating.status)
        assertEquals("provider timeout", creating.failureReason)
        assertEquals(TripRecapStatus.COMPLETED, completed.status)
        assertNull(completed.failureReason)
    }

    @Test
    fun `메시지 없는 생성 실패는 예외 클래스명과 unknown 순서로 fallback한다`() {
        val owner = createUser()
        val classNamed = TripRecap(createTrip(owner), owner, TripRecapStyle.PHOTO).apply { id = 100L }
        val anonymous = TripRecap(createTrip(owner), owner, TripRecapStyle.PHOTO).apply { id = 101L }
        `when`(tripRecapRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(classNamed)
        `when`(tripRecapRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(anonymous)

        completionService.fail(100L, Throwable(), Instant.parse("2026-07-06T12:00:00Z"))
        completionService.fail(101L, object : Throwable() {}, Instant.parse("2026-07-06T12:00:00Z"))

        assertEquals("Throwable", classNamed.failureReason)
        assertEquals("unknown generation failure", anonymous.failureReason)
    }

    @Test
    fun `실패 대상 recap이 없으면 조용히 종료한다`() {
        completionService.fail(999L, IllegalStateException("ignored"))

        verify(tripRecapRepository, never()).save(any(TripRecap::class.java))
        verifyNoInteractions(tripRecapSceneRepository, tripParticipantRepository, outboxEventRepository)
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

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }
}
