package com.togethertrip.main.settlement.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.test.assertEquals

@MainIntegrationTest
@Transactional
class SettlementTransactionQueryRepositoryTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val queryRepository: SettlementTransactionQueryRepository,
) {

    @Test
    fun `정산 payment와 share row는 참여자별 합계로 조회한다`() {
        val fixture = createFixture()
        val activeTransaction = createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            status = TransactionStatus.ACTIVE,
        )
        val anotherActiveTransaction = createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            status = TransactionStatus.ACTIVE,
        )
        val voidedTransaction = createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            status = TransactionStatus.VOIDED,
        )
        createPayment(
            transaction = activeTransaction,
            participant = fixture.ownerParticipant,
            amount = BigDecimal("10000.00"),
        )
        createPayment(
            transaction = anotherActiveTransaction,
            participant = fixture.ownerParticipant,
            amount = BigDecimal("7000.00"),
        )
        createPayment(
            transaction = activeTransaction,
            participant = fixture.memberParticipant,
            amount = BigDecimal("3000.00"),
        )
        createPayment(
            transaction = voidedTransaction,
            participant = fixture.memberParticipant,
            amount = BigDecimal("9999.00"),
        )
        createShare(
            transaction = activeTransaction,
            participant = fixture.ownerParticipant,
            amount = BigDecimal("4000.00"),
        )
        createShare(
            transaction = activeTransaction,
            participant = fixture.memberParticipant,
            amount = BigDecimal("6000.00"),
        )
        createShare(
            transaction = anotherActiveTransaction,
            participant = fixture.memberParticipant,
            amount = BigDecimal("2000.00"),
        )
        createShare(
            transaction = voidedTransaction,
            participant = fixture.memberParticipant,
            amount = BigDecimal("9999.00"),
        )
        entityManager.flush()

        val paymentRows = queryRepository.findSettlementPaymentRows(fixture.trip.id)
        val shareRows = queryRepository.findSettlementShareRows(fixture.trip.id)

        assertEquals(2, paymentRows.size)
        assertEquals(BigDecimal("17000.00"), paymentRows.first { it.participantId == fixture.ownerParticipant.id }.amount)
        assertEquals(BigDecimal("3000.00"), paymentRows.first { it.participantId == fixture.memberParticipant.id }.amount)
        assertEquals(2, shareRows.size)
        assertEquals(BigDecimal("4000.00"), shareRows.first { it.participantId == fixture.ownerParticipant.id }.amount)
        assertEquals(BigDecimal("8000.00"), shareRows.first { it.participantId == fixture.memberParticipant.id }.amount)
    }

    private fun createFixture(): SettlementTransactionFixture {
        val owner = persist(
            User(
                nickname = "재완",
                profileImageUrl = null,
            )
        )
        val member = persist(
            User(
                nickname = "민서",
                profileImageUrl = null,
            )
        )
        val trip = persist(
            Trip(
                ownerUser = owner,
                title = "정산 집계 테스트 여행",
                defaultCurrency = "KRW",
            )
        )
        val ownerParticipant = persist(
            TripParticipant(
                trip = trip,
                user = owner,
                displayName = "재완",
                participantRole = TripParticipantRole.LEADER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        val memberParticipant = persist(
            TripParticipant(
                trip = trip,
                user = member,
                displayName = "민서",
                participantRole = TripParticipantRole.MEMBER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )

        return SettlementTransactionFixture(
            owner = owner,
            trip = trip,
            ownerParticipant = ownerParticipant,
            memberParticipant = memberParticipant,
        )
    }

    private fun createTransaction(
        trip: Trip,
        user: User,
        status: TransactionStatus,
    ): Transaction {
        return persist(
            Transaction(
                trip = trip,
                createdBy = user,
                transactionType = TransactionType.EXPENSE,
                amount = BigDecimal("10000.00"),
                currency = "KRW",
                exchangeRate = BigDecimal("1.000000"),
                baseCurrency = "KRW",
                baseAmount = BigDecimal("10000.00"),
                status = status,
            )
        )
    }

    private fun createPayment(
        transaction: Transaction,
        participant: TripParticipant,
        amount: BigDecimal,
    ): TransactionPayment {
        return persist(
            TransactionPayment(
                transaction = transaction,
                tripParticipant = participant,
                amount = amount,
                currency = "KRW",
                exchangeRate = BigDecimal("1.000000"),
                baseCurrency = "KRW",
                baseAmount = amount,
            )
        )
    }

    private fun createShare(
        transaction: Transaction,
        participant: TripParticipant,
        amount: BigDecimal,
    ): TransactionShare {
        return persist(
            TransactionShare(
                transaction = transaction,
                tripParticipant = participant,
                shareAmount = amount,
                currency = "KRW",
                exchangeRate = BigDecimal("1.000000"),
                baseCurrency = "KRW",
                baseShareAmount = amount,
            )
        )
    }

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }

    private data class SettlementTransactionFixture(
        val owner: User,
        val trip: Trip,
        val ownerParticipant: TripParticipant,
        val memberParticipant: TripParticipant,
    )
}
