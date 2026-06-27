package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.settlement.domain.TripParticipantBalanceSummary
import com.togethertrip.main.settlement.domain.snapshot.SettlementParticipantRow
import com.togethertrip.main.settlement.repository.SettlementTransactionQueryRepository
import com.togethertrip.main.settlement.repository.TripParticipantBalanceSummaryRepository
import com.togethertrip.main.settlement.repository.projection.SettlementPaymentRow
import com.togethertrip.main.settlement.repository.projection.SettlementShareRow
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.assertEquals

class SettlementCalculationServiceTest {

    private lateinit var settlementTransactionQueryRepository: SettlementTransactionQueryRepository
    private lateinit var balanceSummaryRepository: TripParticipantBalanceSummaryRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var settlementCalculationService: SettlementCalculationService

    @BeforeEach
    fun setUp() {
        settlementTransactionQueryRepository = mock(SettlementTransactionQueryRepository::class.java)
        balanceSummaryRepository = mock(TripParticipantBalanceSummaryRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        settlementCalculationService = SettlementCalculationService(
            settlementTransactionQueryRepository = settlementTransactionQueryRepository,
            balanceSummaryRepository = balanceSummaryRepository,
            tripParticipantRepository = tripParticipantRepository,
        )
    }

    @Test
    fun `정산 계산은 거래에 남은 soft delete 참여자 id도 포함한다`() {
        `when`(settlementTransactionQueryRepository.findSettlementPaymentRows(10L)).thenReturn(
            listOf(paymentRow(participantId = 100L, amount = BigDecimal("10000.00")))
        )
        `when`(settlementTransactionQueryRepository.findSettlementShareRows(10L)).thenReturn(
            listOf(
                shareRow(participantId = 100L, amount = BigDecimal("5000.00")),
                shareRow(participantId = 200L, amount = BigDecimal("5000.00")),
            )
        )

        val calculation = settlementCalculationService.calculate(10L)

        assertEquals(setOf(100L, 200L), calculation.balances.map { it.participantId }.toSet())
        assertEquals(BigDecimal("-5000.00"), calculation.balances.first { it.participantId == 200L }.netAmount)
    }

    @Test
    fun `최신 balance summary projection이 있으면 원본 거래 row를 읽지 않는다`() {
        val owner = User(nickname = "재완").apply { id = 1L }
        val trip = Trip(
            ownerUser = owner,
            title = "정산 projection 테스트",
            defaultCurrency = "KRW",
        ).apply {
            id = 10L
            expenseVersion = 7L
        }
        val payer = participant(
            id = 100L,
            trip = trip,
            user = owner,
            displayName = "결제자",
        )
        val debtor = participant(
            id = 200L,
            trip = trip,
            user = null,
            displayName = "부담자",
        )
        `when`(balanceSummaryRepository.findByTripIdAndDeletedAtIsNull(10L)).thenReturn(
            listOf(
                summary(
                    trip = trip,
                    participant = payer,
                    paidAmount = BigDecimal("10000.00"),
                    shareAmount = BigDecimal("3000.00"),
                    projectionVersion = 7L,
                ),
                summary(
                    trip = trip,
                    participant = debtor,
                    paidAmount = BigDecimal("0.00"),
                    shareAmount = BigDecimal("7000.00"),
                    projectionVersion = 7L,
                ),
            )
        )

        val calculation = settlementCalculationService.calculate(
            tripId = 10L,
            expectedProjectionVersion = 7L,
        )

        assertEquals(BigDecimal("10000.00"), calculation.totalExpenseAmount)
        assertEquals(BigDecimal("10000.00"), calculation.totalShareAmount)
        assertEquals(BigDecimal("7000.00"), calculation.balances.first { it.participantId == 100L }.netAmount)
        assertEquals(BigDecimal("-7000.00"), calculation.balances.first { it.participantId == 200L }.netAmount)
        verifyNoInteractions(settlementTransactionQueryRepository)
    }

    @Test
    fun `탈퇴 사용자는 정산 snapshot 응답에서 개인정보를 마스킹한다`() {
        `when`(settlementTransactionQueryRepository.findSettlementPaymentRows(10L)).thenReturn(
            listOf(paymentRow(participantId = 100L, amount = BigDecimal("10000.00")))
        )
        `when`(settlementTransactionQueryRepository.findSettlementShareRows(10L)).thenReturn(
            listOf(shareRow(participantId = 100L, amount = BigDecimal("10000.00")))
        )
        val calculation = settlementCalculationService.calculate(10L)
        `when`(
            tripParticipantRepository.findSettlementParticipantRows(
                tripId = 10L,
                participantIds = calculation.balances.map { it.participantId }.toSet(),
            )
        ).thenReturn(
            listOf(
                participantRow(
                    participantId = 100L,
                    userId = 1L,
                    displayName = "가나다",
                    profileImageUrl = "https://image.example/profile.png",
                    participantStatus = TripParticipantStatus.LEFT,
                    userStatus = "WITHDRAWN",
                )
            )
        )

        val participants = settlementCalculationService.getParticipantsById(
            tripId = 10L,
            calculation = calculation,
        )
        val balances = settlementCalculationService.createBalanceResponses(
            balances = calculation.balances,
            participants = participants,
        )

        assertEquals("탈퇴한 사용자", balances.single().displayName)
        assertEquals(null, balances.single().userId)
        assertEquals(null, balances.single().profileImageUrl)
        assertEquals(TripParticipantStatus.LEFT, balances.single().participantStatus)
    }

    @Test
    fun `더미 참여자는 개인정보를 마스킹하지 않지만 자동 확인 대상이다`() {
        `when`(settlementTransactionQueryRepository.findSettlementPaymentRows(10L)).thenReturn(
            listOf(paymentRow(participantId = 100L, amount = BigDecimal("10000.00")))
        )
        `when`(settlementTransactionQueryRepository.findSettlementShareRows(10L)).thenReturn(
            listOf(shareRow(participantId = 100L, amount = BigDecimal("10000.00")))
        )
        val calculation = settlementCalculationService.calculate(10L)
        `when`(
            tripParticipantRepository.findSettlementParticipantRows(
                tripId = 10L,
                participantIds = calculation.balances.map { it.participantId }.toSet(),
            )
        ).thenReturn(
            listOf(
                participantRow(
                    participantId = 100L,
                    userId = null,
                    displayName = "현장 추가 동행자",
                    profileImageUrl = null,
                    participantStatus = TripParticipantStatus.ACTIVE,
                    userStatus = null,
                )
            )
        )

        val participants = settlementCalculationService.getParticipantsById(
            tripId = 10L,
            calculation = calculation,
        )
        val participant = participants.getValue(100L)

        assertEquals("현장 추가 동행자", participant.displayName)
        assertEquals(false, participant.isWithdrawnUser)
        assertEquals(true, participant.requiresAutoConfirmation)
    }

    private fun paymentRow(
        participantId: Long,
        amount: BigDecimal,
    ): SettlementPaymentRow {
        return SettlementPaymentRow(
            participantId = participantId,
            amount = amount,
        )
    }

    private fun shareRow(
        participantId: Long,
        amount: BigDecimal,
    ): SettlementShareRow {
        return SettlementShareRow(
            participantId = participantId,
            amount = amount,
        )
    }

    private fun participantRow(
        participantId: Long,
        userId: Long?,
        displayName: String,
        profileImageUrl: String?,
        participantStatus: TripParticipantStatus,
        userStatus: String?,
    ): SettlementParticipantRow {
        return object : SettlementParticipantRow {
            override fun getParticipantId(): Long = participantId

            override fun getUserId(): Long? = userId

            override fun getDisplayName(): String = displayName

            override fun getProfileImageUrl(): String? = profileImageUrl

            override fun getParticipantStatus(): String = participantStatus.name

            override fun getUserStatus(): String? = userStatus
        }
    }

    private fun participant(
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

    private fun summary(
        trip: Trip,
        participant: TripParticipant,
        paidAmount: BigDecimal,
        shareAmount: BigDecimal,
        projectionVersion: Long,
    ): TripParticipantBalanceSummary {
        return TripParticipantBalanceSummary(
            trip = trip,
            tripParticipant = participant,
            paidBaseAmount = paidAmount,
            shareBaseAmount = shareAmount,
            netBaseAmount = paidAmount.subtract(shareAmount),
            projectionVersion = projectionVersion,
        )
    }
}
