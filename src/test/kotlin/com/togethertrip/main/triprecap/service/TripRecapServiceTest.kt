package com.togethertrip.main.triprecap.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.trip.service.support.TripAccessResolver
import com.togethertrip.main.triprecap.domain.TripRecap
import com.togethertrip.main.triprecap.domain.TripRecapScene
import com.togethertrip.main.triprecap.domain.TripRecapStatus
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.dto.response.TripRecapViewStatus
import com.togethertrip.main.triprecap.exception.TripRecapErrorCode
import com.togethertrip.main.triprecap.repository.TripRecapRepository
import com.togethertrip.main.triprecap.repository.TripRecapSceneRepository
import com.togethertrip.main.triprecap.service.storage.TripRecapImageStorage
import com.togethertrip.main.triprecap.service.storage.TripRecapStoredImageFile
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripRecapServiceTest {

    private lateinit var tripRepository: TripRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var userRepository: UserRepository
    private lateinit var tripRecapRepository: TripRecapRepository
    private lateinit var tripRecapSceneRepository: TripRecapSceneRepository
    private lateinit var tripRecapGenerationJobLauncher: TripRecapGenerationJobLauncher
    private lateinit var tripRecapImageStorage: TripRecapImageStorage
    private lateinit var tripRecapService: TripRecapService

    @BeforeEach
    fun setUp() {
        tripRepository = mock(TripRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        tripRecapRepository = mock(TripRecapRepository::class.java)
        tripRecapSceneRepository = mock(TripRecapSceneRepository::class.java)
        tripRecapGenerationJobLauncher = mock(TripRecapGenerationJobLauncher::class.java)
        tripRecapImageStorage = mock(TripRecapImageStorage::class.java)

        tripRecapService = TripRecapService(
            tripAccessResolver = TripAccessResolver(
                tripRepository = tripRepository,
                tripParticipantRepository = tripParticipantRepository,
                userRepository = userRepository,
            ),
            tripRecapRepository = tripRecapRepository,
            tripRecapSceneRepository = tripRecapSceneRepository,
            tripRecapAvailabilityPolicy = TripRecapAvailabilityPolicy(),
            tripRecapGenerationJobLauncher = tripRecapGenerationJobLauncher,
            tripRecapImageStorage = tripRecapImageStorage,
        )
    }

    @Test
    fun `여행 종료 전이면 recap 상태는 unavailable 이다`() {
        val owner = createUser()
        val trip = createTrip(owner).apply {
            endDate = LocalDate.now().plusDays(1)
            settlementStatus = TripSettlementStatus.SETTLED
        }
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)

        val response = tripRecapService.getStatus(
            userId = 1L,
            tripId = 10L,
        )

        assertFalse(response.available)
        assertEquals(TripRecapViewStatus.NONE, response.status)
        assertNull(response.recapId)
    }

    @Test
    fun `생성 가능한 여행에 recap이 없으면 available none 상태를 반환한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(null)

        val response = tripRecapService.getStatus(1L, 10L)

        assertTrue(response.available)
        assertEquals(TripRecapViewStatus.NONE, response.status)
        assertNull(response.recapId)
    }

    @Test
    fun `생성 가능한 여행의 기존 recap 상태와 스타일을 반환한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        val recap = createRecap(trip, owner).apply { id = 100L }
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(recap)

        val response = tripRecapService.getStatus(1L, 10L)

        assertTrue(response.available)
        assertEquals(100L, response.recapId)
        assertEquals(TripRecapViewStatus.CREATING, response.status)
        assertEquals(TripRecapStyle.PHOTO, response.style)
    }

    @Test
    fun `종료 및 정산 완료된 여행은 recap 생성 요청에 성공한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(null)
        `when`(tripRecapRepository.save(any(TripRecap::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TripRecap).apply { id = 100L }
        }

        val response = tripRecapService.create(
            userId = 1L,
            tripId = 10L,
            style = TripRecapStyle.PHOTO,
        )

        assertEquals(100L, response.recapId)
        assertEquals(TripRecapStatus.CREATING, response.status)
        verify(tripRecapGenerationJobLauncher).launchAfterCommit(100L)
    }

    @Test
    fun `이미 recap 이 있으면 기존 상태를 반환하고 생성 작업을 중복 실행하지 않는다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        val recap = createRecap(trip, owner).apply {
            id = 100L
            complete(sceneCount = 3, now = java.time.Instant.now())
        }
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(recap)

        val response = tripRecapService.create(
            userId = 1L,
            tripId = 10L,
            style = TripRecapStyle.ILLUSTRATION,
        )

        assertEquals(100L, response.recapId)
        assertEquals(TripRecapStatus.COMPLETED, response.status)
        verify(tripRecapRepository, never()).save(any(TripRecap::class.java))
        verify(tripRecapGenerationJobLauncher, never()).launchAfterCommit(100L)
    }

    @Test
    fun `종료 또는 정산 조건을 충족하지 못하면 recap 생성과 재시도를 거부한다`() {
        val owner = createUser()
        val unavailableTrip = createTrip(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(unavailableTrip)

        val createFailure = assertBusinessException {
            tripRecapService.create(1L, 10L, TripRecapStyle.PHOTO)
        }
        val retryFailure = assertBusinessException {
            tripRecapService.retry(1L, 10L, TripRecapStyle.ILLUSTRATION)
        }

        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_AVAILABLE, createFailure.errorCode)
        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_AVAILABLE, retryFailure.errorCode)
        verify(tripRecapRepository, never()).save(any(TripRecap::class.java))
    }

    @Test
    fun `실패한 recap 은 스타일을 다시 선택해 재시도할 수 있다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        val recap = createRecap(trip, owner).apply {
            id = 100L
            fail(reason = "timeout", now = java.time.Instant.now())
        }
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(recap)

        val response = tripRecapService.retry(
            userId = 1L,
            tripId = 10L,
            style = TripRecapStyle.ILLUSTRATION,
        )

        assertEquals(100L, response.recapId)
        assertEquals(TripRecapStatus.CREATING, response.status)
        assertEquals(TripRecapStyle.ILLUSTRATION, recap.style)
        assertEquals(2, recap.attemptCount)
        val softDeleteInvocation = mockingDetails(tripRecapSceneRepository).invocations
            .first { it.method.name == "softDeleteByRecapId" }
        assertEquals(100L, softDeleteInvocation.arguments[0])
        verify(tripRecapRepository).save(recap)
        verify(tripRecapGenerationJobLauncher).launchAfterCommit(100L)
    }

    @Test
    fun `recap 재시도는 대상 없음과 실패 상태 아님을 구분한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(null)

        val missing = assertBusinessException {
            tripRecapService.retry(1L, 10L, TripRecapStyle.PHOTO)
        }
        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND, missing.errorCode)

        val creating = createRecap(trip, owner).apply { id = 100L }
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(creating)
        val notFailed = assertBusinessException {
            tripRecapService.retry(1L, 10L, TripRecapStyle.PHOTO)
        }
        assertEquals(TripRecapErrorCode.TRIP_RECAP_RETRY_NOT_ALLOWED, notFailed.errorCode)
        assertTrue(
            mockingDetails(tripRecapSceneRepository).invocations
                .none { it.method.name == "softDeleteByRecapId" }
        )
    }

    @Test
    fun `완료되지 않은 recap 조회는 실패한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        val recap = createRecap(trip, owner).apply { id = 100L }
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(recap)

        val exception = assertBusinessException {
            tripRecapService.getRecap(
                userId = 1L,
                tripId = 10L,
            )
        }

        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_COMPLETED, exception.errorCode)
    }

    @Test
    fun `recap 상세 조회는 recap 없음과 미완료를 구분한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)

        val missing = assertBusinessException { tripRecapService.getRecap(1L, 10L) }
        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND, missing.errorCode)
    }

    @Test
    fun `완료된 recap 조회는 이미지와 순서만 반환한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        val recap = createRecap(trip, owner).apply {
            id = 100L
            complete(sceneCount = 1, now = java.time.Instant.now())
        }
        val scene = TripRecapScene(
            recap = recap,
            sceneOrder = 1,
            style = TripRecapStyle.PHOTO,
            imageObjectKey = "trip-recaps/10/100/1.png",
            imageUrl = "/uploads/trip-recaps/10/100/1.png",
            sceneDescription = "internal description",
            imagePrompt = "internal prompt",
            generationProvider = "stub",
            generationModel = "stub-v1",
        ).apply { id = 200L }
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(recap)
        `when`(tripRecapSceneRepository.findByRecapIdAndDeletedAtIsNullOrderBySceneOrderAsc(100L))
            .thenReturn(listOf(scene))

        val response = tripRecapService.getRecap(
            userId = 1L,
            tripId = 10L,
        )

        assertEquals(100L, response.recapId)
        assertEquals(1, response.scenes.size)
        assertEquals(200L, response.scenes.first().sceneId)
        assertEquals(1, response.scenes.first().order)
        assertEquals("/api/trips/10/recap/scenes/200/image", response.scenes.first().imageUrl)
        assertEquals("9:16", response.scenes.first().aspectRatio)
    }

    @Test
    fun `recap 장면 이미지는 여행 멤버 권한 확인 후 스토리지에서 읽는다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        val recap = createRecap(trip, owner).apply {
            id = 100L
            complete(sceneCount = 1, now = java.time.Instant.now())
        }
        val scene = TripRecapScene(
            recap = recap,
            sceneOrder = 1,
            style = TripRecapStyle.PHOTO,
            imageObjectKey = "trip-recaps/10/100/1.png",
            imageUrl = "/uploads/trip-recaps/10/100/1.png",
            sceneDescription = "internal description",
            imagePrompt = "internal prompt",
            generationProvider = "stub",
            generationModel = "stub-v1",
        ).apply { id = 200L }
        val imageFile = TripRecapStoredImageFile(
            bytes = byteArrayOf(1, 2, 3),
            contentType = "image/png",
        )
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(recap)
        `when`(tripRecapSceneRepository.findByIdAndRecapIdAndDeletedAtIsNull(200L, 100L)).thenReturn(scene)
        `when`(tripRecapImageStorage.load("trip-recaps/10/100/1.png")).thenReturn(imageFile)

        val response = tripRecapService.getSceneImage(
            userId = 1L,
            tripId = 10L,
            sceneId = 200L,
        )

        assertEquals(imageFile, response)
    }

    @Test
    fun `장면 이미지 조회는 recap 없음 미완료 scene 없음을 각각 구분한다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)

        val missingRecap = assertBusinessException {
            tripRecapService.getSceneImage(1L, 10L, 200L)
        }
        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_FOUND, missingRecap.errorCode)

        val creating = createRecap(trip, owner).apply { id = 100L }
        `when`(tripRecapRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(creating)
        val notCompleted = assertBusinessException {
            tripRecapService.getSceneImage(1L, 10L, 200L)
        }
        assertEquals(TripRecapErrorCode.TRIP_RECAP_NOT_COMPLETED, notCompleted.errorCode)

        creating.complete(sceneCount = 1, now = java.time.Instant.now())
        val missingScene = assertBusinessException {
            tripRecapService.getSceneImage(1L, 10L, 200L)
        }
        assertEquals(TripRecapErrorCode.TRIP_RECAP_SCENE_NOT_FOUND, missingScene.errorCode)
        assertTrue(
            mockingDetails(tripRecapImageStorage).invocations
                .none { it.method.name == "load" }
        )
    }

    @Test
    fun `여행 멤버가 아니면 recap 상태 조회가 거부된다`() {
        val owner = createUser()
        val trip = createAvailableTrip(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 2L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            tripRecapService.getStatus(
                userId = 2L,
                tripId = 10L,
            )
        }

        assertEquals(TripErrorCode.TRIP_ACCESS_DENIED, exception.errorCode)
    }

    private fun createUser(
        id: Long = 1L,
        nickname: String = "재완",
    ): User {
        return User(
            nickname = nickname,
            status = UserStatus.ACTIVE,
        ).apply {
            this.id = id
        }
    }

    private fun createTrip(ownerUser: User): Trip {
        return Trip(
            ownerUser = ownerUser,
            title = "제주 여행",
            defaultCurrency = "KRW",
            startDate = LocalDate.now().minusDays(4),
            endDate = LocalDate.now().minusDays(2),
        ).apply {
            id = 10L
        }
    }

    private fun createAvailableTrip(ownerUser: User): Trip {
        return createTrip(ownerUser).apply {
            settlementStatus = TripSettlementStatus.SETTLED
        }
    }

    private fun createRecap(
        trip: Trip,
        user: User,
    ): TripRecap {
        return TripRecap(
            trip = trip,
            requestedBy = user,
            style = TripRecapStyle.PHOTO,
        )
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
