package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferCompletedPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferConfirmedBySenderPayload
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.repository.SettlementTransferSearchCondition
import com.togethertrip.main.settlement.repository.projection.SettlementTransferCompletionSummary
import com.togethertrip.main.settlement.service.support.SettlementAccessResolver
import com.togethertrip.main.settlement.service.support.SettlementTransferConfirmationProcessor
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

class SettlementTransferServiceTest {

    private lateinit var settlementTransferRepository: SettlementTransferRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var settlementAccessResolver: SettlementAccessResolver
    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var settlementTransferService: SettlementTransferService

    @BeforeEach
    fun setUp() {
        settlementTransferRepository = mock(SettlementTransferRepository::class.java)
        tripRepository = mock(TripRepository::class.java)
        settlementAccessResolver = mock(SettlementAccessResolver::class.java)
        outboxEventRepository = mock(OutboxEventRepository::class.java)
        settlementTransferService = SettlementTransferService(
            settlementTransferRepository = settlementTransferRepository,
            tripRepository = tripRepository,
            settlementAccessResolver = settlementAccessResolver,
            settlementTransferConfirmationProcessor = SettlementTransferConfirmationProcessor(
                settlementTransferRepository = settlementTransferRepository,
                clock = Clock.fixed(
                    Instant.parse("2026-07-02T00:30:00Z"),
                    ZoneId.of("Asia/Seoul"),
                ),
            ),
            outboxEventPublisher = OutboxEventPublisher(
                outboxEventRepository = outboxEventRepository,
                objectMapper = jacksonObjectMapper(),
            ),
        )
    }

    @Test
    fun `송금 목록은 direction SENT 기준으로 현재 참여자가 보낸 송금만 반환한다`() {
        val participant = createParticipant(id = 100L)
        val sentTransfer = transferRow(
            id = 1L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
        )
        val receivedTransfer = transferRow(
            id = 2L,
            senderParticipantId = 300L,
            receiverParticipantId = 100L,
        )

        `when`(settlementAccessResolver.getActiveParticipant(1L, 10L)).thenReturn(participant)
        `when`(
            settlementTransferRepository.findTransferRows(
                SettlementTransferSearchCondition(10L, null, null, null),
            )
        ).thenReturn(listOf(sentTransfer, receivedTransfer))

        val responses = settlementTransferService.getTransfers(
            userId = 1L,
            tripId = 10L,
            settlementId = null,
            participantId = null,
            status = null,
            direction = "SENT",
        )

        assertEquals(listOf(1L), responses.map { it.id })
    }

    @Test
    fun `송금 목록은 direction RECEIVED 기준으로 현재 참여자가 받을 송금만 반환한다`() {
        val participant = createParticipant(id = 100L)
        val sentTransfer = transferRow(
            id = 1L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
        )
        val receivedTransfer = transferRow(
            id = 2L,
            senderParticipantId = 300L,
            receiverParticipantId = 100L,
        )

        `when`(settlementAccessResolver.getActiveParticipant(1L, 10L)).thenReturn(participant)
        `when`(
            settlementTransferRepository.findTransferRows(
                SettlementTransferSearchCondition(10L, null, null, null),
            )
        ).thenReturn(listOf(sentTransfer, receivedTransfer))

        val responses = settlementTransferService.getTransfers(
            userId = 1L,
            tripId = 10L,
            settlementId = null,
            participantId = null,
            status = null,
            direction = "RECEIVED",
        )

        assertEquals(listOf(2L), responses.map { it.id })
    }

    @Test
    fun `송금 목록은 탈퇴 사용자의 표시명을 마스킹한다`() {
        val participant = createParticipant(id = 100L)
        val transfer = transferRow(
            id = 1L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
            senderUserStatus = "WITHDRAWN",
            receiverUserStatus = "ACTIVE",
        )

        `when`(settlementAccessResolver.getActiveParticipant(1L, 10L)).thenReturn(participant)
        `when`(
            settlementTransferRepository.findTransferRows(
                SettlementTransferSearchCondition(10L, null, null, null),
            )
        ).thenReturn(listOf(transfer))

        val responses = settlementTransferService.getTransfers(
            userId = 1L,
            tripId = 10L,
            settlementId = null,
            participantId = null,
            status = null,
            direction = null,
        )

        assertEquals("탈퇴한 사용자", responses.single().senderDisplayName)
    }

    @Test
    fun `송금 목록은 status와 participant 필터를 repository에 전달한다`() {
        val participant = createParticipant(id = 100L)

        `when`(settlementAccessResolver.getActiveParticipant(1L, 10L)).thenReturn(participant)
        `when`(
            settlementTransferRepository.findTransferRows(
                SettlementTransferSearchCondition(
                    10L, 30L, 100L, SettlementTransferStatus.PENDING,
                ),
            )
        ).thenReturn(emptyList())

        settlementTransferService.getTransfers(
            userId = 1L,
            tripId = 10L,
            settlementId = 30L,
            participantId = 100L,
            status = "pending",
            direction = null,
        )

        verify(settlementTransferRepository).findTransferRows(
            SettlementTransferSearchCondition(
                10L, 30L, 100L, SettlementTransferStatus.PENDING,
            ),
        )
    }

    @Test
    fun `송금자 본인만 송금 확인 가능하다`() {
        val sender = createParticipant(id = 100L)
        val transfer = createTransfer(
            sender = sender,
            receiver = createParticipant(id = 200L),
        )
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
            status = SettlementTransferStatus.SENDER_CONFIRMED,
            senderConfirmedAt = Instant.parse("2026-06-08T01:00:00Z"),
        )

        mockTransferConfirmation(
            activeParticipant = sender,
            transfer = transfer,
            transferRow = transferRow,
        )
        `when`(
            settlementTransferRepository.confirmAsSenderIfNeeded(
                transferId = eqLong(40L),
                tripId = eqLong(10L),
                participantId = eqLong(100L),
                confirmedAt = anyInstant(),
            )
        ).thenReturn(1)

        val response = settlementTransferService.confirmAsSender(
            userId = 1L,
            tripId = 10L,
            transferId = 40L,
        )

        assertEquals(40L, response.id)
        assertEquals(SettlementTransferStatus.SENDER_CONFIRMED, response.status)
        assertEquals(true, response.senderConfirmedAt != null)
    }

    @Test
    fun `송금자 확인 시 수금자에게 알림 outbox를 발행한다`() {
        val sender = createParticipant(id = 100L)
        val receiver = createParticipant(id = 200L)
        val transfer = createTransfer(
            sender = sender,
            receiver = receiver,
        )
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
            status = SettlementTransferStatus.SENDER_CONFIRMED,
            senderConfirmedAt = Instant.parse("2026-06-08T01:00:00Z"),
            senderUserId = 100L,
            receiverUserId = 200L,
        )

        mockTransferConfirmation(
            activeParticipant = sender,
            transfer = transfer,
            transferRow = transferRow,
        )
        `when`(
            settlementTransferRepository.confirmAsSenderIfNeeded(
                transferId = eqLong(40L),
                tripId = eqLong(10L),
                participantId = eqLong(100L),
                confirmedAt = anyInstant(),
            )
        ).thenReturn(1)
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as OutboxEvent).apply { id = 900L }
        }

        settlementTransferService.confirmAsSender(
            userId = 1L,
            tripId = 10L,
            transferId = 40L,
        )

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        val event = eventCaptor.value
        val payload = jacksonObjectMapper().readValue<SettlementTransferConfirmedBySenderPayload>(event.payload)
        assertEquals(OutboxEventType.SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER.name, event.eventType)
        assertEquals(40L, event.aggregateId)
        assertEquals(listOf(200L), payload.recipients.map { it.userId })
        assertEquals(100L, payload.actorUserId)
        assertEquals(BigDecimal("5000.00"), payload.amount)
    }

    @Test
    fun `송금자가 아니면 송금 확인이 차단된다`() {
        val activeParticipant = createParticipant(id = 300L)
        val transfer = createTransfer(
            sender = createParticipant(id = 100L),
            receiver = createParticipant(id = 200L),
        )
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
        )

        mockTransferConfirmation(
            activeParticipant = activeParticipant,
            transfer = transfer,
            transferRow = transferRow,
        )

        val exception = assertBusinessException {
            settlementTransferService.confirmAsSender(
                userId = 1L,
                tripId = 10L,
                transferId = 40L,
            )
        }

        assertEquals(SettlementErrorCode.SETTLEMENT_TRANSFER_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `방장이라도 송금자나 수금자가 아니면 대리 확인할 수 없다`() {
        val ownerParticipant = createParticipant(
            id = 999L,
            role = TripParticipantRole.LEADER,
        )
        val transfer = createTransfer(
            sender = createParticipant(id = 100L),
            receiver = createParticipant(id = 200L),
        )
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
        )

        mockTransferConfirmation(
            activeParticipant = ownerParticipant,
            transfer = transfer,
            transferRow = transferRow,
        )

        val exception = assertBusinessException {
            settlementTransferService.confirmAsReceiver(
                userId = 1L,
                tripId = 10L,
                transferId = 40L,
            )
        }

        assertEquals(SettlementErrorCode.SETTLEMENT_TRANSFER_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `수금자 본인만 수금 확인 가능하다`() {
        val receiver = createParticipant(id = 200L)
        val transfer = createTransfer(
            sender = createParticipant(id = 100L),
            receiver = receiver,
        )
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
            status = SettlementTransferStatus.RECEIVER_CONFIRMED,
            receiverConfirmedAt = Instant.parse("2026-06-08T01:00:00Z"),
        )

        mockTransferConfirmation(
            activeParticipant = receiver,
            transfer = transfer,
            transferRow = transferRow,
        )
        `when`(
            settlementTransferRepository.confirmAsReceiverIfNeeded(
                transferId = eqLong(40L),
                tripId = eqLong(10L),
                participantId = eqLong(200L),
                confirmedAt = anyInstant(),
            )
        ).thenReturn(1)

        val response = settlementTransferService.confirmAsReceiver(
            userId = 1L,
            tripId = 10L,
            transferId = 40L,
        )

        assertEquals(40L, response.id)
        assertEquals(SettlementTransferStatus.RECEIVER_CONFIRMED, response.status)
        assertEquals(true, response.receiverConfirmedAt != null)
    }

    @Test
    fun `수금자 확인으로 송금이 완료되면 송금자와 수금자에게 완료 알림 outbox를 발행한다`() {
        val receiver = createParticipant(id = 200L)
        val transfer = createTransfer(
            sender = createParticipant(id = 100L),
            receiver = receiver,
        )
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
            status = SettlementTransferStatus.COMPLETED,
            senderConfirmedAt = Instant.parse("2026-06-08T00:30:00Z"),
            receiverConfirmedAt = Instant.parse("2026-06-08T01:00:00Z"),
            completedAt = Instant.parse("2026-06-08T01:00:00Z"),
            senderUserId = 100L,
            receiverUserId = 200L,
        )

        mockTransferConfirmation(
            activeParticipant = receiver,
            transfer = transfer,
            transferRow = transferRow,
        )
        `when`(
            settlementTransferRepository.confirmAsReceiverIfNeeded(
                transferId = eqLong(40L),
                tripId = eqLong(10L),
                participantId = eqLong(200L),
                confirmedAt = anyInstant(),
            )
        ).thenReturn(1)
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as OutboxEvent).apply { id = 901L }
        }

        settlementTransferService.confirmAsReceiver(
            userId = 1L,
            tripId = 10L,
            transferId = 40L,
        )

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        val event = eventCaptor.value
        val payload = jacksonObjectMapper().readValue<SettlementTransferCompletedPayload>(event.payload)
        assertEquals(OutboxEventType.SETTLEMENT_TRANSFER_COMPLETED.name, event.eventType)
        assertEquals(40L, event.aggregateId)
        assertEquals(listOf(100L, 200L), payload.recipients.map { it.userId })
        assertEquals(200L, payload.actorUserId)
    }

    @Test
    fun `마지막 송금이 완료되면 여행 정산 상태를 완료로 변경한다`() {
        val trip = createTrip().apply {
            settlementStatus = TripSettlementStatus.IN_PROGRESS
        }
        val receiver = createParticipant(
            id = 200L,
            trip = trip,
        )
        val transfer = createTransfer(
            trip = trip,
            sender = createParticipant(id = 100L, trip = trip),
            receiver = receiver,
        )
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
            status = SettlementTransferStatus.COMPLETED,
            senderConfirmedAt = Instant.parse("2026-06-08T00:30:00Z"),
            receiverConfirmedAt = Instant.parse("2026-06-08T01:00:00Z"),
            completedAt = Instant.parse("2026-06-08T01:00:00Z"),
        )

        mockTransferConfirmation(
            activeParticipant = receiver,
            transfer = transfer,
            transferRow = transferRow,
        )
        `when`(
            settlementTransferRepository.confirmAsReceiverIfNeeded(
                transferId = eqLong(40L),
                tripId = eqLong(10L),
                participantId = eqLong(200L),
                confirmedAt = anyInstant(),
            )
        ).thenReturn(1)
        `when`(settlementTransferRepository.findCompletionSummaryByTripId(10L))
            .thenReturn(
                settlementTransferCompletionSummary(
                    totalCount = 2L,
                    incompleteCount = 0L,
                )
            )

        settlementTransferService.confirmAsReceiver(
            userId = 1L,
            tripId = 10L,
            transferId = 40L,
        )

        assertEquals(TripSettlementStatus.SETTLED, trip.settlementStatus)
        verify(tripRepository).save(trip)
    }

    @Test
    fun `transfer가 다른 여행에 속하면 확인이 실패한다`() {
        val participant = createParticipant(id = 100L)
        val transfer = createTransfer(
            trip = createTrip(id = 99L),
            sender = participant,
            receiver = createParticipant(id = 200L),
        )

        `when`(settlementAccessResolver.getActiveParticipant(1L, 10L)).thenReturn(participant)
        `when`(settlementTransferRepository.findByIdAndDeletedAtIsNull(40L)).thenReturn(transfer)

        val exception = assertBusinessException {
            settlementTransferService.confirmAsSender(
                userId = 1L,
                tripId = 10L,
                transferId = 40L,
            )
        }

        assertEquals(SettlementErrorCode.SETTLEMENT_TRANSFER_TRIP_MISMATCH, exception.errorCode)
    }

    @Test
    fun `중복 송금 확인은 기존 확인 시각을 덮어쓰지 않는다`() {
        val sender = createParticipant(id = 100L)
        val firstConfirmedAt = Instant.parse("2026-06-08T01:00:00Z")
        val transfer = createTransfer(
            sender = sender,
            receiver = createParticipant(id = 200L),
        ).apply {
            confirmAsSender(firstConfirmedAt)
        }
        val transferRow = transferRow(
            id = 40L,
            senderParticipantId = 100L,
            receiverParticipantId = 200L,
            status = SettlementTransferStatus.SENDER_CONFIRMED,
            senderConfirmedAt = firstConfirmedAt,
        )

        mockTransferConfirmation(
            activeParticipant = sender,
            transfer = transfer,
            transferRow = transferRow,
        )

        settlementTransferService.confirmAsSender(
            userId = 1L,
            tripId = 10L,
            transferId = 40L,
        )

        assertEquals(firstConfirmedAt, transfer.senderConfirmedAt)
        verify(outboxEventRepository, never()).save(any(OutboxEvent::class.java))
    }

    @Test
    fun `잘못된 송금 방향은 실패한다`() {
        val participant = createParticipant(id = 100L)

        `when`(settlementAccessResolver.getActiveParticipant(1L, 10L)).thenReturn(participant)
        `when`(
            settlementTransferRepository.findTransferRows(
                SettlementTransferSearchCondition(10L, null, null, null),
            )
        ).thenReturn(emptyList())

        val exception = assertBusinessException {
            settlementTransferService.getTransfers(
                userId = 1L,
                tripId = 10L,
                settlementId = null,
                participantId = null,
                status = null,
                direction = "UNKNOWN",
            )
        }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_TRANSFER_DIRECTION, exception.errorCode)
    }

    @Test
    fun `잘못된 송금 상태 필터는 실패한다`() {
        val exception = assertBusinessException {
            settlementTransferService.getTransfers(
                userId = 1L,
                tripId = 10L,
                settlementId = null,
                participantId = null,
                status = "UNKNOWN",
                direction = null,
            )
        }

        assertEquals(SettlementErrorCode.INVALID_SETTLEMENT_TRANSFER_STATUS, exception.errorCode)
    }

    private fun mockTransferConfirmation(
        activeParticipant: TripParticipant,
        transfer: SettlementTransfer,
        transferRow: SettlementTransferRow,
    ) {
        `when`(settlementAccessResolver.getActiveParticipant(1L, 10L)).thenReturn(activeParticipant)
        `when`(settlementTransferRepository.findByIdAndDeletedAtIsNull(40L)).thenReturn(transfer)
        `when`(settlementTransferRepository.findTransferRowById(40L)).thenReturn(transferRow)
    }

    private fun createTransfer(
        trip: Trip = createTrip(),
        sender: TripParticipant,
        receiver: TripParticipant,
    ): SettlementTransfer {
        val settlement = Settlement(
            trip = trip,
            status = SettlementStatus.CONFIRMED,
            tripExpenseVersion = 1L,
            calculationVersion = "settlement-v1",
            baseCurrency = "KRW",
            totalExpenseAmount = BigDecimal("10000.00"),
            totalShareAmount = BigDecimal("10000.00"),
            snapshotPayload = "{}",
            confirmedAt = Instant.parse("2026-06-08T00:00:00Z"),
            confirmedBy = trip.ownerUser,
        ).apply {
            id = 30L
        }

        return SettlementTransfer(
            settlement = settlement,
            sender = sender,
            receiver = receiver,
            amount = BigDecimal("5000.00"),
            currency = "KRW",
            status = SettlementTransferStatus.PENDING,
        ).apply {
            id = 40L
        }
    }

    private fun createParticipant(
        id: Long,
        trip: Trip = createTrip(),
        role: TripParticipantRole = TripParticipantRole.MEMBER,
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = createUser(id),
            displayName = "참여자$id",
            participantRole = role,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            this.id = id
        }
    }

    private fun createTrip(id: Long = 10L): Trip {
        return Trip(
            ownerUser = createUser(1L),
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            this.id = id
        }
    }

    private fun createUser(id: Long): User {
        return User(nickname = "사용자$id").apply {
            this.id = id
        }
    }

    private fun transferRow(
        id: Long,
        senderParticipantId: Long,
        receiverParticipantId: Long,
        status: SettlementTransferStatus = SettlementTransferStatus.PENDING,
        senderConfirmedAt: Instant? = null,
        receiverConfirmedAt: Instant? = null,
        completedAt: Instant? = null,
        senderUserStatus: String = "ACTIVE",
        receiverUserStatus: String = "ACTIVE",
        senderUserId: Long? = senderParticipantId,
        receiverUserId: Long? = receiverParticipantId,
    ): SettlementTransferRow {
        return object : SettlementTransferRow {
            override fun getId(): Long = id

            override fun getSettlementId(): Long = 30L

            override fun getTripName(): String = "일본 여행"

            override fun getSenderParticipantId(): Long = senderParticipantId

            override fun getSenderUserId(): Long? = senderUserId

            override fun getSenderDisplayName(): String = "보낼 사람"

            override fun getSenderUserStatus(): String = senderUserStatus

            override fun getReceiverParticipantId(): Long = receiverParticipantId

            override fun getReceiverUserId(): Long? = receiverUserId

            override fun getReceiverDisplayName(): String = "받을 사람"

            override fun getReceiverUserStatus(): String = receiverUserStatus

            override fun getAmount(): BigDecimal = BigDecimal("5000.00")

            override fun getCurrency(): String = "KRW"

            override fun getStatus(): String = status.name

            override fun getSenderConfirmedAt(): Instant? = senderConfirmedAt

            override fun getReceiverConfirmedAt(): Instant? = receiverConfirmedAt

            override fun getCompletedAt(): Instant? = completedAt

            override fun getAutoConfirmed(): Boolean =
                senderConfirmedAt != null || receiverConfirmedAt != null || completedAt != null
        }
    }

    private fun settlementTransferCompletionSummary(
        totalCount: Long,
        incompleteCount: Long,
    ): SettlementTransferCompletionSummary {
        return object : SettlementTransferCompletionSummary {
            override val tripId = 10L

            override val totalCount = totalCount

            override val incompleteCount = incompleteCount
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

    private fun anyInstant(): Instant {
        return any(Instant::class.java) ?: Instant.EPOCH
    }

    private fun eqLong(value: Long): Long {
        return eq(value) ?: value
    }
}
