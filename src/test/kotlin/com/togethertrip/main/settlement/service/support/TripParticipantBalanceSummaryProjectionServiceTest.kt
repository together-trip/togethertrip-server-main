package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.settlement.domain.TripParticipantBalanceSummary
import com.togethertrip.main.settlement.repository.TripParticipantBalanceSummaryRepository
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.assertEquals

class TripParticipantBalanceSummaryProjectionServiceTest {

    private lateinit var balanceSummaryRepository: TripParticipantBalanceSummaryRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var projectionService: TripParticipantBalanceSummaryProjectionService

    @BeforeEach
    fun setUp() {
        balanceSummaryRepository = mock(TripParticipantBalanceSummaryRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        projectionService = TripParticipantBalanceSummaryProjectionService(
            balanceSummaryRepository = balanceSummaryRepository,
            tripParticipantRepository = tripParticipantRepository,
        )
    }

    @Test
    fun `거래 생성 projection은 참여자별 결제와 부담 delta를 더한다`() {
        val fixture = createFixture(expenseVersion = 3L)
        val payment = payment(
            transaction = fixture.transaction,
            participant = fixture.participant,
            amount = BigDecimal("12000.00"),
        )
        val share = share(
            transaction = fixture.transaction,
            participant = fixture.participant,
            amount = BigDecimal("5000.00"),
        )
        `when`(
            balanceSummaryRepository.findByTripIdAndTripParticipantIdAndDeletedAtIsNull(
                tripId = 10L,
                tripParticipantId = 100L,
            )
        ).thenReturn(null)
        `when`(tripParticipantRepository.getReferenceById(100L)).thenReturn(fixture.participant)

        projectionService.applyTransactionCreated(
            trip = fixture.trip,
            payments = listOf(payment),
            shares = listOf(share),
        )

        val captor = ArgumentCaptor.forClass(TripParticipantBalanceSummary::class.java)
        verify(balanceSummaryRepository).save(captor.capture())
        val summary = captor.value
        assertEquals(BigDecimal("12000.00"), summary.paidBaseAmount)
        assertEquals(BigDecimal("5000.00"), summary.shareBaseAmount)
        assertEquals(BigDecimal("7000.00"), summary.netBaseAmount)
        assertEquals(3L, summary.projectionVersion)
    }

    @Test
    fun `거래 수정 projection은 이전 allocation을 차감하고 현재 allocation을 더한다`() {
        val fixture = createFixture(expenseVersion = 4L)
        val existingSummary = TripParticipantBalanceSummary(
            trip = fixture.trip,
            tripParticipant = fixture.participant,
            paidBaseAmount = BigDecimal("12000.00"),
            shareBaseAmount = BigDecimal("5000.00"),
            netBaseAmount = BigDecimal("7000.00"),
            projectionVersion = 3L,
        )
        val previousPayment = payment(
            transaction = fixture.transaction,
            participant = fixture.participant,
            amount = BigDecimal("12000.00"),
        )
        val previousShare = share(
            transaction = fixture.transaction,
            participant = fixture.participant,
            amount = BigDecimal("5000.00"),
        )
        val currentPayment = payment(
            transaction = fixture.transaction,
            participant = fixture.participant,
            amount = BigDecimal("20000.00"),
        )
        val currentShare = share(
            transaction = fixture.transaction,
            participant = fixture.participant,
            amount = BigDecimal("8000.00"),
        )
        `when`(
            balanceSummaryRepository.findByTripIdAndTripParticipantIdAndDeletedAtIsNull(
                tripId = 10L,
                tripParticipantId = 100L,
            )
        ).thenReturn(existingSummary)

        projectionService.applyTransactionUpdated(
            trip = fixture.trip,
            previousPayments = listOf(previousPayment),
            previousShares = listOf(previousShare),
            currentPayments = listOf(currentPayment),
            currentShares = listOf(currentShare),
        )

        assertEquals(BigDecimal("20000.00"), existingSummary.paidBaseAmount)
        assertEquals(BigDecimal("8000.00"), existingSummary.shareBaseAmount)
        assertEquals(BigDecimal("12000.00"), existingSummary.netBaseAmount)
        assertEquals(4L, existingSummary.projectionVersion)
        verify(balanceSummaryRepository).save(existingSummary)
    }

    private fun createFixture(
        expenseVersion: Long,
    ): ProjectionFixture {
        val user = User(nickname = "재완").apply { id = 1L }
        val trip = Trip(
            ownerUser = user,
            title = "projection 테스트",
            defaultCurrency = "KRW",
        ).apply {
            id = 10L
            this.expenseVersion = expenseVersion
        }
        val participant = TripParticipant(
            trip = trip,
            user = user,
            displayName = "재완",
            participantRole = TripParticipantRole.LEADER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            id = 100L
        }
        val transaction = Transaction(
            trip = trip,
            createdBy = user,
            transactionType = TransactionType.EXPENSE,
            amount = BigDecimal("10000.00"),
            currency = "KRW",
            exchangeRate = BigDecimal("1.000000"),
            baseCurrency = "KRW",
            baseAmount = BigDecimal("10000.00"),
        ).apply {
            id = 300L
        }

        return ProjectionFixture(
            trip = trip,
            participant = participant,
            transaction = transaction,
        )
    }

    private fun payment(
        transaction: Transaction,
        participant: TripParticipant,
        amount: BigDecimal,
    ): TransactionPayment {
        return TransactionPayment(
            transaction = transaction,
            tripParticipant = participant,
            amount = amount,
            currency = "KRW",
            exchangeRate = BigDecimal("1.000000"),
            baseCurrency = "KRW",
            baseAmount = amount,
        )
    }

    private fun share(
        transaction: Transaction,
        participant: TripParticipant,
        amount: BigDecimal,
    ): TransactionShare {
        return TransactionShare(
            transaction = transaction,
            tripParticipant = participant,
            shareAmount = amount,
            currency = "KRW",
            exchangeRate = BigDecimal("1.000000"),
            baseCurrency = "KRW",
            baseShareAmount = amount,
        )
    }

    private data class ProjectionFixture(
        val trip: Trip,
        val participant: TripParticipant,
        val transaction: Transaction,
    )
}
