package com.togethertrip.main.settlement.dto.response

import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class SettlementShareResponseTest {

    @Test
    fun `공개 공유 응답은 내부 식별자와 프로필 이미지를 노출하지 않는다`() {
        val forbiddenNames = setOf(
            "id",
            "settlementId",
            "tripId",
            "participantId",
            "userId",
            "confirmedByUserId",
            "profileImageUrl",
        )
        val responseFieldNames = SettlementShareResponse::class.java.declaredFields.map { it.name }.toSet()
        val balanceFieldNames = SettlementShareBalanceResponse::class.java.declaredFields.map { it.name }.toSet()
        val transferFieldNames = SettlementShareTransferResponse::class.java.declaredFields.map { it.name }.toSet()

        assertFalse(responseFieldNames.any(forbiddenNames::contains))
        assertFalse(balanceFieldNames.any(forbiddenNames::contains))
        assertFalse(transferFieldNames.any(forbiddenNames::contains))
    }

    @Test
    fun `공개 공유 응답 mapper는 내부 식별자 없이 표시 정보와 금액만 전달한다`() {
        val settlement = createSettlement()
        val balance = SettlementParticipantBalanceResponse(
            participantId = 100L,
            userId = 1L,
            displayName = "가나다",
            profileImageUrl = "https://image.example/profile.png",
            participantStatus = TripParticipantStatus.ACTIVE,
            paidAmount = BigDecimal("10000.00"),
            shareAmount = BigDecimal("5000.00"),
            netAmount = BigDecimal("5000.00"),
        )
        val transfer = SettlementTransferResponse(
            id = 300L,
            senderParticipantId = 200L,
            senderDisplayName = "보낼 사람",
            receiverParticipantId = 100L,
            receiverDisplayName = "받을 사람",
            amount = BigDecimal("5000.00"),
            currency = "KRW",
            status = SettlementTransferStatus.PENDING,
            senderConfirmedAt = null,
            receiverConfirmedAt = null,
            completedAt = null,
        )

        val response = SettlementShareResponse.from(
            settlement = settlement,
            balances = listOf(balance),
            transfers = listOf(transfer),
        )

        assertEquals(SettlementStatus.CONFIRMED, response.status)
        assertEquals("가나다", response.balances.single().displayName)
        assertEquals(BigDecimal("10000.00"), response.balances.single().paidAmount)
        assertEquals("보낼 사람", response.transfers.single().senderDisplayName)
        assertEquals("받을 사람", response.transfers.single().receiverDisplayName)
        assertEquals(BigDecimal("5000.00"), response.transfers.single().amount)
    }

    private fun createSettlement(): Settlement {
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

        return Settlement(
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
            id = 20L
        }
    }
}
