package com.togethertrip.main.transaction.repository

import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.transaction.domain.Transaction
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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TransactionStatisticsQueryRepositoryTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val queryRepository: TransactionStatisticsQueryRepository,
) {

    @Test
    fun `공동경비 잔액은 활성 충전과 사용 거래만 집계한다`() {
        val fixture = createFixture()
        createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            transactionType = TransactionType.FUND_CHARGE,
            amount = BigDecimal("100000.00"),
            createdAt = Instant.parse("2026-07-01T01:00:00Z"),
        )
        createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            transactionType = TransactionType.FUND_USE,
            amount = BigDecimal("25000.00"),
            createdAt = Instant.parse("2026-07-01T02:00:00Z"),
        )
        createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            transactionType = TransactionType.EXPENSE,
            amount = BigDecimal("7000.00"),
            createdAt = Instant.parse("2026-07-01T03:00:00Z"),
        )
        createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            transactionType = TransactionType.FUND_USE,
            amount = BigDecimal("10000.00"),
            status = TransactionStatus.VOIDED,
            createdAt = Instant.parse("2026-07-01T04:00:00Z"),
        )
        entityManager.flush()

        val row = queryRepository.findCommonFundBalance(fixture.trip.id)

        assertEquals("KRW", row.baseCurrency)
        assertEquals(BigDecimal("100000.00"), row.chargedBaseAmount)
        assertEquals(BigDecimal("25000.00"), row.usedBaseAmount)
    }

    @Test
    fun `카테고리 통계는 연결 게시글 발생일과 카테고리를 기준으로 집계한다`() {
        val fixture = createFixture()
        val foodTransaction = createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            transactionType = TransactionType.EXPENSE,
            amount = BigDecimal("30000.00"),
            createdAt = Instant.parse("2026-07-01T01:00:00Z"),
        )
        createPost(
            trip = fixture.trip,
            transaction = foodTransaction,
            author = fixture.ownerParticipant,
            category = "FOOD",
            occurredAt = Instant.parse("2026-07-02T03:00:00Z"),
        )
        val outsideTransaction = createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            transactionType = TransactionType.EXPENSE,
            amount = BigDecimal("90000.00"),
            createdAt = Instant.parse("2026-07-01T01:00:00Z"),
        )
        createPost(
            trip = fixture.trip,
            transaction = outsideTransaction,
            author = fixture.ownerParticipant,
            category = "FOOD",
            occurredAt = Instant.parse("2026-07-06T03:00:00Z"),
        )
        entityManager.flush()

        val rows = queryRepository.findCategoryStatistics(
            tripId = fixture.trip.id,
            from = Instant.parse("2026-07-01T15:00:00Z"),
            toExclusive = Instant.parse("2026-07-05T15:00:00Z"),
        )

        assertEquals(1, rows.size)
        assertEquals("FOOD", rows.first().key)
        assertEquals(1L, rows.first().transactionCount)
        assertEquals(BigDecimal("30000.00"), rows.first().totalBaseAmount)
    }

    @Test
    fun `참여자 통계는 삭제되지 않은 share의 부담 금액을 집계한다`() {
        val fixture = createFixture()
        val transaction = createTransaction(
            trip = fixture.trip,
            user = fixture.owner,
            transactionType = TransactionType.EXPENSE,
            amount = BigDecimal("60000.00"),
            createdAt = Instant.parse("2026-07-02T03:00:00Z"),
        )
        createShare(
            transaction = transaction,
            participant = fixture.ownerParticipant,
            amount = BigDecimal("20000.00"),
        )
        createShare(
            transaction = transaction,
            participant = fixture.memberParticipant,
            amount = BigDecimal("40000.00"),
        )
        createPost(
            trip = fixture.trip,
            transaction = transaction,
            author = fixture.ownerParticipant,
            category = null,
            occurredAt = Instant.parse("2026-07-02T03:00:00Z"),
        )
        entityManager.flush()

        val rows = queryRepository.findParticipantShareStatistics(
            tripId = fixture.trip.id,
            from = null,
            toExclusive = null,
        )

        assertEquals(2, rows.size)
        assertEquals(fixture.memberParticipant.id.toString(), rows.first().key)
        assertEquals("민서", rows.first().label)
        assertEquals(BigDecimal("40000.00"), rows.first().totalBaseAmount)
        assertEquals(fixture.ownerParticipant.id.toString(), rows[1].key)
        assertEquals(BigDecimal("20000.00"), rows[1].totalBaseAmount)
    }

    private fun createFixture(): StatisticsFixture {
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
                title = "통계 테스트 여행",
                defaultCurrency = "KRW",
                startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 5),
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

        return StatisticsFixture(
            owner = owner,
            trip = trip,
            ownerParticipant = ownerParticipant,
            memberParticipant = memberParticipant,
        )
    }

    private fun createTransaction(
        trip: Trip,
        user: User,
        transactionType: TransactionType,
        amount: BigDecimal,
        status: TransactionStatus = TransactionStatus.ACTIVE,
        createdAt: Instant,
    ): Transaction {
        return persist(
            Transaction(
                trip = trip,
                createdBy = user,
                transactionType = transactionType,
                amount = amount,
                currency = "KRW",
                exchangeRate = BigDecimal("1.000000"),
                baseCurrency = "KRW",
                baseAmount = amount,
                status = status,
            ).apply {
                this.createdAt = createdAt
                this.updatedAt = createdAt
            }
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

    private fun createPost(
        trip: Trip,
        transaction: Transaction,
        author: TripParticipant,
        category: String?,
        occurredAt: Instant,
    ): Post {
        return persist(
            Post(
                trip = trip,
                transaction = transaction,
                author = author,
                postType = PostType.EXPENSE,
                title = "거래 ${transaction.id}",
                category = category,
                occurredAt = occurredAt,
            )
        )
    }

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }

    private data class StatisticsFixture(
        val owner: User,
        val trip: Trip,
        val ownerParticipant: TripParticipant,
        val memberParticipant: TripParticipant,
    )
}
