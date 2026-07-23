package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotBalance
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotPayload
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.dto.response.TripSettlementDisplayStatus
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MainIntegrationTest
@Transactional
class SettlementShareServiceIntegrationTest @Autowired constructor(
    private val service: SettlementShareService,
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper,
) {

    @Test
    fun `확정 정산 share token은 snapshot balance와 송금 목록을 반환한다`() {
        createFixture("confirmed-share", SettlementStatus.CONFIRMED, SettlementTransferStatus.PENDING)
        entityManager.flush()

        val response = service.getSettlementByShareToken("confirmed-share")

        assertEquals(SettlementStatus.CONFIRMED, response.status)
        assertEquals(TripSettlementDisplayStatus.IN_PROGRESS, response.settlementDisplayStatus)
        assertEquals(BigDecimal("10000.00"), response.totalExpenseAmount)
        assertEquals(listOf("보낼 사람", "받을 사람"), response.balances.map { it.displayName })
        assertEquals(listOf(BigDecimal("5000.00"), BigDecimal("-5000.00")), response.balances.map { it.netAmount })
        assertEquals(1, response.transfers.size)
        assertEquals("보낼 사람", response.transfers.single().senderDisplayName)
        assertEquals("받을 사람", response.transfers.single().receiverDisplayName)
        assertEquals(SettlementTransferStatus.PENDING, response.transfers.single().status)
    }

    @Test
    fun `모든 송금이 완료된 share 응답은 COMPLETED 표시 상태를 반환한다`() {
        createFixture("completed-share", SettlementStatus.CONFIRMED, SettlementTransferStatus.COMPLETED)
        entityManager.flush()

        val response = service.getSettlementByShareToken("completed-share")

        assertEquals(TripSettlementDisplayStatus.COMPLETED, response.settlementDisplayStatus)
        assertEquals(SettlementTransferStatus.COMPLETED, response.transfers.single().status)
    }

    @Test
    fun `DRAFT 정산의 share token은 공개하지 않는다`() {
        createFixture("draft-share", SettlementStatus.DRAFT, transferStatus = null)
        entityManager.flush()

        val exception = assertFailsWith<BusinessException> {
            service.getSettlementByShareToken("draft-share")
        }

        assertEquals(SettlementErrorCode.SETTLEMENT_NOT_CONFIRMED, exception.errorCode)
    }

    @Test
    fun `삭제되거나 존재하지 않는 share token은 찾을 수 없음으로 처리한다`() {
        val fixture = createFixture("deleted-share", SettlementStatus.CONFIRMED, transferStatus = null)
        fixture.settlement.markDeleted(Instant.parse("2026-07-10T00:00:00Z"))
        entityManager.flush()

        val deleted = assertFailsWith<BusinessException> {
            service.getSettlementByShareToken("deleted-share")
        }
        val missing = assertFailsWith<BusinessException> {
            service.getSettlementByShareToken("missing-share")
        }

        assertEquals(SettlementErrorCode.SETTLEMENT_SHARE_TOKEN_NOT_FOUND, deleted.errorCode)
        assertEquals(SettlementErrorCode.SETTLEMENT_SHARE_TOKEN_NOT_FOUND, missing.errorCode)
    }

    private fun createFixture(
        shareToken: String,
        settlementStatus: SettlementStatus,
        transferStatus: SettlementTransferStatus?,
    ): Fixture {
        val senderUser = persist(User(nickname = "공유 송금자"))
        val receiverUser = persist(User(nickname = "공유 수금자"))
        val trip = persist(
            Trip(
                ownerUser = senderUser,
                title = "정산 공유 여행",
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
        entityManager.flush()
        val snapshot = SettlementSnapshotPayload(
            balances = listOf(
                snapshot(sender, senderUser, paid = "10000.00", share = "5000.00", net = "5000.00"),
                snapshot(receiver, receiverUser, paid = "0.00", share = "5000.00", net = "-5000.00"),
            )
        )
        val settlement = persist(
            Settlement(
                trip = trip,
                status = settlementStatus,
                tripExpenseVersion = 1,
                calculationVersion = "settlement-v1",
                baseCurrency = "KRW",
                totalExpenseAmount = BigDecimal("10000.00"),
                totalShareAmount = BigDecimal("10000.00"),
                snapshotPayload = objectMapper.writeValueAsString(snapshot),
                confirmedAt = if (settlementStatus == SettlementStatus.CONFIRMED) {
                    Instant.parse("2026-07-10T00:00:00Z")
                } else {
                    null
                },
                confirmedBy = if (settlementStatus == SettlementStatus.CONFIRMED) senderUser else null,
                shareToken = shareToken,
            )
        )
        if (transferStatus != null) {
            persist(
                SettlementTransfer(
                    settlement = settlement,
                    sender = sender,
                    receiver = receiver,
                    amount = BigDecimal("5000.00"),
                    currency = "KRW",
                    status = transferStatus,
                    senderConfirmedAt = if (transferStatus == SettlementTransferStatus.COMPLETED) {
                        Instant.parse("2026-07-10T01:00:00Z")
                    } else {
                        null
                    },
                    receiverConfirmedAt = if (transferStatus == SettlementTransferStatus.COMPLETED) {
                        Instant.parse("2026-07-10T02:00:00Z")
                    } else {
                        null
                    },
                    completedAt = if (transferStatus == SettlementTransferStatus.COMPLETED) {
                        Instant.parse("2026-07-10T02:00:00Z")
                    } else {
                        null
                    },
                )
            )
        }
        return Fixture(settlement)
    }

    private fun snapshot(
        participant: TripParticipant,
        user: User,
        paid: String,
        share: String,
        net: String,
    ): SettlementSnapshotBalance {
        return SettlementSnapshotBalance(
            participantId = participant.id,
            userId = user.id,
            displayName = participant.displayName,
            profileImageUrl = null,
            participantStatus = TripParticipantStatus.ACTIVE,
            paidAmount = BigDecimal(paid),
            shareAmount = BigDecimal(share),
            netAmount = BigDecimal(net),
        )
    }

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }

    private data class Fixture(val settlement: Settlement)
}
