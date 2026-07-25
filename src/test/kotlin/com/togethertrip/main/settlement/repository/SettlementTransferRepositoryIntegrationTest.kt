package com.togethertrip.main.settlement.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.repository.SettlementTransferSearchCondition
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@MainIntegrationTest
@Transactional
class SettlementTransferRepositoryIntegrationTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val repository: SettlementTransferRepository,
) {

    @Test
    fun `완료 요약은 확정 정산의 삭제되지 않은 송금만 집계한다`() {
        val fixture = createFixture()
        createTransfer(fixture, status = SettlementTransferStatus.COMPLETED)
        createTransfer(
            fixture = fixture,
            sender = fixture.receiver,
            receiver = fixture.sender,
            amount = "2000.00",
        )
        val thirdParticipant = createParticipant(fixture.trip, "세 번째 참여자")
        createTransfer(
            fixture = fixture,
            receiver = thirdParticipant,
            amount = "3000.00",
        ).markDeleted(Instant.parse("2026-07-01T00:00:00Z"))
        entityManager.flush()

        val summary = repository.findCompletionSummaryByTripId(fixture.trip.id)

        requireNotNull(summary)
        assertEquals(fixture.trip.id, summary.tripId)
        assertEquals(2L, summary.totalCount)
        assertEquals(1L, summary.incompleteCount)
    }

    @Test
    fun `송금 row는 참여자와 사용자 상태를 실제 native projection으로 반환한다`() {
        val fixture = createFixture(receiverStatus = UserStatus.WITHDRAWN)
        val transfer = createTransfer(fixture, amount = "4321.00")
        entityManager.flush()
        entityManager.clear()

        val row = repository.findTransferRowById(transfer.id)

        requireNotNull(row)
        assertEquals(fixture.settlement.id, row.getSettlementId())
        assertEquals("정산 계약 테스트 여행", row.getTripName())
        assertEquals(fixture.sender.id, row.getSenderParticipantId())
        assertEquals(fixture.sender.user?.id, row.getSenderUserId())
        assertEquals("보낼 사람", row.getSenderDisplayName())
        assertEquals("ACTIVE", row.getSenderUserStatus())
        assertEquals(fixture.receiver.id, row.getReceiverParticipantId())
        assertEquals("WITHDRAWN", row.getReceiverUserStatus())
        assertEquals(BigDecimal("4321.00"), row.getAmount())
        assertEquals("PENDING", row.getStatus())
    }

    @Test
    fun `송금 목록은 settlement participant status 필터를 동시에 적용한다`() {
        val fixture = createFixture()
        val pending = createTransfer(fixture, amount = "1000.00")
        createTransfer(
            fixture = fixture,
            sender = fixture.receiver,
            receiver = fixture.sender,
            amount = "2000.00",
            status = SettlementTransferStatus.COMPLETED,
        )
        entityManager.flush()

        val rows = repository.findTransferRows(
            SettlementTransferSearchCondition(
                tripId = fixture.trip.id,
                settlementId = fixture.settlement.id,
                participantId = fixture.sender.id,
                status = SettlementTransferStatus.PENDING,
            ),
        )
        val unfilteredRows = repository.findTransferRows(
            SettlementTransferSearchCondition(
                tripId = fixture.trip.id,
                settlementId = null,
                participantId = null,
                status = null,
            ),
        )

        assertEquals(listOf(pending.id), rows.map { it.getId() })
        assertEquals(2, unfilteredRows.size)
    }

    @Test
    fun `삭제된 정산의 송금은 단건과 정산별 목록에서 조회되지 않는다`() {
        val fixture = createFixture()
        val transfer = createTransfer(fixture)
        fixture.settlement.markDeleted(Instant.parse("2026-07-02T00:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        assertNull(repository.findTransferRowById(transfer.id))
        assertEquals(emptyList(), repository.findTransferRowsBySettlementId(fixture.settlement.id))
    }

    @Test
    fun `송금자 확인은 한 번만 반영되고 잘못된 참여자는 갱신하지 않는다`() {
        val fixture = createFixture()
        val transfer = createTransfer(fixture)
        entityManager.flush()
        val firstConfirmedAt = Instant.parse("2026-07-03T01:00:00Z")
        val secondConfirmedAt = Instant.parse("2026-07-03T02:00:00Z")

        assertEquals(
            0,
            repository.confirmAsSenderIfNeeded(transfer.id, fixture.trip.id, fixture.receiver.id, firstConfirmedAt),
        )
        assertEquals(
            1,
            repository.confirmAsSenderIfNeeded(transfer.id, fixture.trip.id, fixture.sender.id, firstConfirmedAt),
        )
        assertEquals(
            0,
            repository.confirmAsSenderIfNeeded(transfer.id, fixture.trip.id, fixture.sender.id, secondConfirmedAt),
        )

        val row = requireNotNull(repository.findTransferRowById(transfer.id))
        assertEquals("SENDER_CONFIRMED", row.getStatus())
        assertEquals(firstConfirmedAt, row.getSenderConfirmedAt())
        assertNull(row.getCompletedAt())
    }

    @Test
    fun `양측 확인은 완료 상태로 수렴하고 최초 완료 시각을 보존한다`() {
        val fixture = createFixture()
        val transfer = createTransfer(fixture)
        entityManager.flush()
        val senderAt = Instant.parse("2026-07-04T01:00:00Z")
        val receiverAt = Instant.parse("2026-07-04T02:00:00Z")

        assertEquals(1, repository.confirmAsSenderIfNeeded(transfer.id, fixture.trip.id, fixture.sender.id, senderAt))
        assertEquals(1, repository.confirmAsReceiverIfNeeded(transfer.id, fixture.trip.id, fixture.receiver.id, receiverAt))
        assertEquals(0, repository.confirmAsReceiverIfNeeded(transfer.id, fixture.trip.id, fixture.receiver.id, receiverAt.plusSeconds(60)))

        val row = requireNotNull(repository.findTransferRowById(transfer.id))
        assertEquals("COMPLETED", row.getStatus())
        assertEquals(senderAt, row.getSenderConfirmedAt())
        assertEquals(receiverAt, row.getReceiverConfirmedAt())
        assertEquals(receiverAt, row.getCompletedAt())
        assertNotEquals(receiverAt.plusSeconds(60), row.getCompletedAt())
    }

    private fun createFixture(receiverStatus: UserStatus = UserStatus.ACTIVE): Fixture {
        val senderUser = persist(User(nickname = "송금자"))
        val receiverUser = persist(User(nickname = "수금자", status = receiverStatus))
        val trip = persist(
            Trip(
                ownerUser = senderUser,
                title = "정산 계약 테스트 여행",
                defaultCurrency = "KRW",
            )
        )
        val sender = persist(
            TripParticipant(
                trip = trip,
                user = senderUser,
                displayName = "보낼 사람",
                participantRole = TripParticipantRole.LEADER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        val receiver = persist(
            TripParticipant(
                trip = trip,
                user = receiverUser,
                displayName = "받을 사람",
                participantRole = TripParticipantRole.MEMBER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        val settlement = persist(
            Settlement(
                trip = trip,
                status = SettlementStatus.CONFIRMED,
                tripExpenseVersion = 1,
                calculationVersion = "settlement-v1",
                baseCurrency = "KRW",
                totalExpenseAmount = BigDecimal("10000.00"),
                totalShareAmount = BigDecimal("10000.00"),
                snapshotPayload = "{}",
                confirmedAt = Instant.parse("2026-07-01T00:00:00Z"),
                confirmedBy = senderUser,
            )
        )
        return Fixture(trip, settlement, sender, receiver)
    }

    private fun createTransfer(
        fixture: Fixture,
        sender: TripParticipant = fixture.sender,
        receiver: TripParticipant = fixture.receiver,
        amount: String = "1000.00",
        status: SettlementTransferStatus = SettlementTransferStatus.PENDING,
    ): SettlementTransfer {
        return persist(
            SettlementTransfer(
                settlement = fixture.settlement,
                sender = sender,
                receiver = receiver,
                amount = BigDecimal(amount),
                currency = "KRW",
                status = status,
                completedAt = if (status == SettlementTransferStatus.COMPLETED) {
                    Instant.parse("2026-07-01T01:00:00Z")
                } else {
                    null
                },
            )
        )
    }

    private fun createParticipant(trip: Trip, displayName: String): TripParticipant {
        val user = persist(User(nickname = displayName))
        return persist(
            TripParticipant(
                trip = trip,
                user = user,
                displayName = displayName,
                participantRole = TripParticipantRole.MEMBER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
    }

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }

    private data class Fixture(
        val trip: Trip,
        val settlement: Settlement,
        val sender: TripParticipant,
        val receiver: TripParticipant,
    )
}
