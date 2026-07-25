package com.togethertrip.main.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostCommentSearchCondition
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.repository.TransactionSearchCondition
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripStatus
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.trip.repository.TripSearchCondition
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

@MainIntegrationTest
@Transactional
class DynamicQueryRepositoryIntegrationTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val tripRepository: TripRepository,
    private val transactionRepository: TransactionRepository,
    private val postCommentRepository: PostCommentRepository,
) {
    @Test
    fun `여행 선택 조건과 커서는 실제 값이 있을 때만 적용된다`() {
        val owner = persist(User(nickname = "방장"))
        val member = persist(User(nickname = "동행자"))
        val visible = createTrip(owner, TripStatus.ONGOING, "2026-07-03T00:00:00Z")
        val cursorBoundary = createTrip(owner, TripStatus.ONGOING, "2026-07-04T00:00:00Z")
        createTrip(owner, TripStatus.COMPLETED, "2026-07-02T00:00:00Z")
        persist(
            TripParticipant(
                trip = visible,
                user = member,
                displayName = member.nickname,
                participantRole = TripParticipantRole.MEMBER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        persist(
            TripParticipant(
                trip = cursorBoundary,
                user = member,
                displayName = member.nickname,
                participantRole = TripParticipantRole.MEMBER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        entityManager.flush()

        val result = tripRepository.findAccessibleTrips(
            TripSearchCondition(
                userId = member.id,
                status = TripStatus.ONGOING,
                cursorCreatedAt = cursorBoundary.createdAt,
                cursorId = cursorBoundary.id,
            ),
            PageRequest.of(0, 20),
        )
        val withoutOptionalFilters = tripRepository.findAccessibleTrips(
            TripSearchCondition(member.id, null, null, null),
            PageRequest.of(0, 20),
        )

        assertEquals(listOf(visible.id), result.map { it.id })
        assertEquals(listOf(cursorBoundary.id, visible.id), withoutOptionalFilters.map { it.id })
    }

    @Test
    fun `거래 유형 참여자 커서 조건은 하나의 동적 조회에서 조합된다`() {
        val owner = persist(User(nickname = "방장"))
        val member = persist(User(nickname = "동행자"))
        val trip = createTrip(owner, TripStatus.ONGOING, "2026-07-01T00:00:00Z")
        val participant = persist(
            TripParticipant(
                trip = trip,
                user = member,
                displayName = member.nickname,
                participantRole = TripParticipantRole.MEMBER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        val matching = createTransaction(trip, owner, TransactionType.EXPENSE, "2026-07-02T00:00:00Z")
        val cursorBoundary = createTransaction(trip, owner, TransactionType.EXPENSE, "2026-07-03T00:00:00Z")
        createTransaction(trip, owner, TransactionType.FUND_USE, "2026-07-01T00:00:00Z")
        persist(
            TransactionPayment(
                transaction = matching,
                tripParticipant = participant,
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                exchangeRate = BigDecimal("1.000000"),
                baseCurrency = "KRW",
                baseAmount = BigDecimal("1000.00"),
            )
        )
        entityManager.flush()

        val result = transactionRepository.findTransactions(
            TransactionSearchCondition(
                tripId = trip.id,
                status = TransactionStatus.ACTIVE,
                transactionType = TransactionType.EXPENSE,
                participantId = participant.id,
                cursorCreatedAt = cursorBoundary.createdAt,
                cursorId = cursorBoundary.id,
            ),
            PageRequest.of(0, 20),
        )
        val withoutOptionalFilters = transactionRepository.findTransactions(
            TransactionSearchCondition(
                tripId = trip.id,
                status = TransactionStatus.ACTIVE,
                transactionType = null,
                participantId = null,
                cursorCreatedAt = null,
                cursorId = null,
            ),
            PageRequest.of(0, 20),
        )

        assertEquals(listOf(matching.id), result.map { it.id })
        assertEquals(3, withoutOptionalFilters.size)
    }

    @Test
    fun `댓글 조회는 조회자와 커서 유무에 따라 필요한 Predicate만 생성한다`() {
        val owner = persist(User(nickname = "작성자"))
        val trip = createTrip(owner, TripStatus.ONGOING, "2026-07-01T00:00:00Z")
        val participant = persist(
            TripParticipant(
                trip = trip,
                user = owner,
                displayName = owner.nickname,
                participantRole = TripParticipantRole.LEADER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        val post = persist(
            Post(
                trip = trip,
                author = participant,
                postType = PostType.RECORD,
                title = "동적 댓글 조회",
            )
        )
        val first = createComment(post, participant, "첫 댓글", "2026-07-01T01:00:00Z")
        val second = createComment(post, participant, "둘째 댓글", "2026-07-01T02:00:00Z")
        entityManager.flush()

        val withoutViewer = postCommentRepository.findRootComments(
            PostCommentSearchCondition(post.id, null, null, null),
            PageRequest.of(0, 20),
        )
        val withViewerAndCursor = postCommentRepository.findRootComments(
            PostCommentSearchCondition(post.id, owner.id, first.createdAt, first.id),
            PageRequest.of(0, 20),
        )

        assertEquals(listOf(first.id, second.id), withoutViewer.map { it.id })
        assertEquals(listOf(second.id), withViewerAndCursor.map { it.id })
        assertEquals(2L, postCommentRepository.countVisibleRootComments(post.id, null))
        assertEquals(2L, postCommentRepository.countVisibleRootComments(post.id, owner.id))
        assertEquals(emptyList(), postCommentRepository.findVisibleCommentCounts(emptyList(), owner.id))
        assertEquals(
            2L,
            postCommentRepository.findVisibleCommentCounts(listOf(post.id), owner.id).single().commentCount,
        )
    }

    private fun createTrip(owner: User, status: TripStatus, createdAt: String): Trip = persist(
        Trip(
            ownerUser = owner,
            title = "동적 조회 여행",
            defaultCurrency = "KRW",
            tripStatus = status,
        ).apply {
            this.createdAt = Instant.parse(createdAt)
            this.updatedAt = this.createdAt
        }
    )

    private fun createTransaction(
        trip: Trip,
        owner: User,
        type: TransactionType,
        createdAt: String,
    ): Transaction = persist(
        Transaction(
            trip = trip,
            createdBy = owner,
            transactionType = type,
            amount = BigDecimal("1000.00"),
            currency = "KRW",
            exchangeRate = BigDecimal("1.000000"),
            baseCurrency = "KRW",
            baseAmount = BigDecimal("1000.00"),
        ).apply {
            this.createdAt = Instant.parse(createdAt)
            this.updatedAt = this.createdAt
        }
    )

    private fun createComment(
        post: Post,
        author: TripParticipant,
        content: String,
        createdAt: String,
    ): PostComment = persist(
        PostComment(
            post = post,
            author = author,
            content = content,
        ).apply {
            this.createdAt = Instant.parse(createdAt)
            this.updatedAt = this.createdAt
        }
    )

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }
}
