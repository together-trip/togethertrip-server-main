package com.togethertrip.main.settlement.domain

import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettlementTransferTest {

    @Test
    fun `송금자 확인 시 송금자 확인 상태로 전이한다`() {
        val transfer = createTransfer()
        val confirmedAt = Instant.parse("2026-06-08T01:00:00Z")

        transfer.confirmAsSender(confirmedAt)

        assertEquals(confirmedAt, transfer.senderConfirmedAt)
        assertEquals(null, transfer.receiverConfirmedAt)
        assertEquals(null, transfer.completedAt)
        assertEquals(SettlementTransferStatus.SENDER_CONFIRMED, transfer.status)
        assertFalse(transfer.autoConfirmed)
    }

    @Test
    fun `수금자 확인 시 수금자 확인 상태로 전이한다`() {
        val transfer = createTransfer()
        val confirmedAt = Instant.parse("2026-06-08T01:00:00Z")

        transfer.confirmAsReceiver(confirmedAt)

        assertEquals(null, transfer.senderConfirmedAt)
        assertEquals(confirmedAt, transfer.receiverConfirmedAt)
        assertEquals(null, transfer.completedAt)
        assertEquals(SettlementTransferStatus.RECEIVER_CONFIRMED, transfer.status)
    }

    @Test
    fun `송금자와 수금자가 모두 확인하면 완료 처리한다`() {
        val transfer = createTransfer()
        val senderConfirmedAt = Instant.parse("2026-06-08T01:00:00Z")
        val receiverConfirmedAt = Instant.parse("2026-06-08T02:00:00Z")

        transfer.confirmAsSender(senderConfirmedAt)
        transfer.confirmAsReceiver(receiverConfirmedAt)

        assertEquals(senderConfirmedAt, transfer.senderConfirmedAt)
        assertEquals(receiverConfirmedAt, transfer.receiverConfirmedAt)
        assertEquals(receiverConfirmedAt, transfer.completedAt)
        assertEquals(SettlementTransferStatus.COMPLETED, transfer.status)
    }

    @Test
    fun `송금자 재확인은 기존 확인 시각을 덮어쓰지 않는다`() {
        val transfer = createTransfer()
        val firstConfirmedAt = Instant.parse("2026-06-08T01:00:00Z")
        val secondConfirmedAt = Instant.parse("2026-06-08T02:00:00Z")

        transfer.confirmAsSender(firstConfirmedAt)
        transfer.confirmAsSender(secondConfirmedAt)

        assertEquals(firstConfirmedAt, transfer.senderConfirmedAt)
        assertEquals(SettlementTransferStatus.SENDER_CONFIRMED, transfer.status)
    }

    @Test
    fun `완료된 송금 재확인은 완료 시각을 덮어쓰지 않는다`() {
        val transfer = createTransfer()
        val senderConfirmedAt = Instant.parse("2026-06-08T01:00:00Z")
        val receiverConfirmedAt = Instant.parse("2026-06-08T02:00:00Z")
        val replayConfirmedAt = Instant.parse("2026-06-08T03:00:00Z")

        transfer.confirmAsSender(senderConfirmedAt)
        transfer.confirmAsReceiver(receiverConfirmedAt)
        transfer.confirmAsReceiver(replayConfirmedAt)
        transfer.confirmAsSender(replayConfirmedAt)

        assertEquals(senderConfirmedAt, transfer.senderConfirmedAt)
        assertEquals(receiverConfirmedAt, transfer.receiverConfirmedAt)
        assertEquals(receiverConfirmedAt, transfer.completedAt)
        assertEquals(SettlementTransferStatus.COMPLETED, transfer.status)
    }

    @Test
    fun `탈퇴 사용자 자동 동의는 확인 시각과 사유를 기록한다`() {
        val transfer = createTransfer()
        val confirmedAt = Instant.parse("2026-06-08T01:00:00Z")

        transfer.autoConfirmSender(
            reason = "WITHDRAWN_USER_AUTO_CONFIRMED",
            confirmedAt = confirmedAt,
        )

        assertEquals(confirmedAt, transfer.senderConfirmedAt)
        assertEquals(true, transfer.autoConfirmed)
        assertEquals("WITHDRAWN_USER_AUTO_CONFIRMED", transfer.autoConfirmReason)
        assertEquals(SettlementTransferStatus.SENDER_CONFIRMED, transfer.status)
    }

    @Test
    fun `송금자와 수금자가 모두 자동 동의되면 완료 처리한다`() {
        val transfer = createTransfer()
        val confirmedAt = Instant.parse("2026-06-08T01:00:00Z")

        transfer.autoConfirmSender(
            reason = "WITHDRAWN_USER_AUTO_CONFIRMED",
            confirmedAt = confirmedAt,
        )
        transfer.autoConfirmReceiver(
            reason = "WITHDRAWN_USER_AUTO_CONFIRMED",
            confirmedAt = confirmedAt,
        )

        assertEquals(confirmedAt, transfer.senderConfirmedAt)
        assertEquals(confirmedAt, transfer.receiverConfirmedAt)
        assertEquals(confirmedAt, transfer.completedAt)
        assertEquals(true, transfer.autoConfirmed)
        assertEquals(SettlementTransferStatus.COMPLETED, transfer.status)
    }

    @Test
    fun `수금자 자동 동의는 확인 시각과 사유를 기록한다`() {
        val transfer = createTransfer()
        val confirmedAt = Instant.parse("2026-06-08T01:00:00Z")

        transfer.autoConfirmReceiver(
            reason = "WITHDRAWN_USER_AUTO_CONFIRMED",
            confirmedAt = confirmedAt,
        )

        assertEquals(confirmedAt, transfer.receiverConfirmedAt)
        assertEquals(true, transfer.autoConfirmed)
        assertEquals("WITHDRAWN_USER_AUTO_CONFIRMED", transfer.autoConfirmReason)
        assertEquals(SettlementTransferStatus.RECEIVER_CONFIRMED, transfer.status)
    }

    @Test
    fun `이미 수금 확인한 송금은 자동 동의로 재호출해도 자동 사유를 추가하지 않는다`() {
        val transfer = createTransfer()
        val firstConfirmedAt = Instant.parse("2026-06-08T01:00:00Z")

        transfer.confirmAsReceiver(firstConfirmedAt)
        transfer.autoConfirmReceiver(
            reason = "SHOULD_NOT_BE_RECORDED",
            confirmedAt = Instant.parse("2026-06-08T02:00:00Z"),
        )

        assertEquals(firstConfirmedAt, transfer.receiverConfirmedAt)
        assertFalse(transfer.autoConfirmed)
        assertEquals(null, transfer.autoConfirmReason)
    }

    private fun createTransfer(): SettlementTransfer {
        val owner = User(nickname = "방장").apply {
            id = 1L
        }
        val trip = Trip(
            ownerUser = owner,
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            id = 10L
        }
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
            confirmedBy = owner,
        ).apply {
            id = 30L
        }
        val sender = createParticipant(
            id = 100L,
            trip = trip,
            user = owner,
            displayName = "보낼 사람",
        )
        val receiver = createParticipant(
            id = 200L,
            trip = trip,
            user = null,
            displayName = "받을 사람",
        )

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
        trip: Trip,
        user: User?,
        displayName: String,
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = displayName,
            participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            this.id = id
        }
    }
}
