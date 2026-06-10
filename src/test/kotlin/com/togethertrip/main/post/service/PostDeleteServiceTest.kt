package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.exception.PostErrorCode
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.transaction.service.TransactionService
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PostDeleteServiceTest {

    private lateinit var postRepository: PostRepository
    private lateinit var transactionService: TransactionService
    private lateinit var postDeleteService: PostDeleteService

    @BeforeEach
    fun setUp() {
        postRepository = mock(PostRepository::class.java)
        transactionService = mock(TransactionService::class.java)
        postDeleteService = PostDeleteService(
            postRepository = postRepository,
            transactionService = transactionService,
        )
    }

    @Test
    fun `일반 기록 게시글 삭제는 게시글만 soft delete 처리한다`() {
        val post = createPost()

        mockPost(post)

        postDeleteService.deletePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
        )

        assertNotNull(post.deletedAt)
        verifyNoInteractions(transactionService)
    }

    @Test
    fun `소비 게시글 삭제는 연결 거래를 먼저 무효 처리하고 게시글을 soft delete 처리한다`() {
        val transaction = createTransaction()
        val post = createPost(
            transaction = transaction,
            postType = PostType.EXPENSE,
        )

        mockPost(post)

        postDeleteService.deletePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
        )

        verify(transactionService).deleteTransaction(
            userId = 1L,
            tripId = 10L,
            transactionId = 200L,
        )
        assertNotNull(post.deletedAt)
    }

    @Test
    fun `소비 게시글에 연결 거래가 없으면 삭제에 실패한다`() {
        val post = createPost(postType = PostType.EXPENSE)

        mockPost(post)

        val exception = assertBusinessException {
            postDeleteService.deletePost(
                userId = 1L,
                tripId = 10L,
                postId = 300L,
            )
        }

        assertEquals(PostErrorCode.TRANSACTION_NOT_FOUND, exception.errorCode)
        assertEquals(null, post.deletedAt)
        verifyNoInteractions(transactionService)
    }

    @Test
    fun `거래 무효 처리에 실패하면 게시글을 삭제하지 않는다`() {
        val transaction = createTransaction().apply {
            status = TransactionStatus.VOIDED
        }
        val post = createPost(
            transaction = transaction,
            postType = PostType.EXPENSE,
        )

        mockPost(post)
        `when`(
            transactionService.deleteTransaction(
                userId = 1L,
                tripId = 10L,
                transactionId = 200L,
            )
        ).thenThrow(BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_VOIDED))

        val exception = assertBusinessException {
            postDeleteService.deletePost(
                userId = 1L,
                tripId = 10L,
                postId = 300L,
            )
        }

        assertEquals(TransactionErrorCode.TRANSACTION_ALREADY_VOIDED, exception.errorCode)
        assertEquals(null, post.deletedAt)
    }

    @Test
    fun `정산 시작 이후 소비 게시글 삭제는 실패하고 게시글을 삭제하지 않는다`() {
        val transaction = createTransaction()
        val post = createPost(
            transaction = transaction,
            postType = PostType.EXPENSE,
        )

        mockPost(post)
        `when`(
            transactionService.deleteTransaction(
                userId = 1L,
                tripId = 10L,
                transactionId = 200L,
            )
        ).thenThrow(BusinessException(TransactionErrorCode.TRANSACTION_LOCKED_BY_SETTLEMENT))

        val exception = assertBusinessException {
            postDeleteService.deletePost(
                userId = 1L,
                tripId = 10L,
                postId = 300L,
            )
        }

        assertEquals(TransactionErrorCode.TRANSACTION_LOCKED_BY_SETTLEMENT, exception.errorCode)
        assertEquals(null, post.deletedAt)
    }

    @Test
    fun `작성자가 아니면 게시글 삭제에 실패하고 거래 무효 처리를 호출하지 않는다`() {
        val transaction = createTransaction()
        val post = createPost(
            transaction = transaction,
            postType = PostType.EXPENSE,
        )

        mockPost(post)

        val exception = assertBusinessException {
            postDeleteService.deletePost(
                userId = 2L,
                tripId = 10L,
                postId = 300L,
            )
        }

        assertEquals(CommonErrorCode.ACCESS_DENIED, exception.errorCode)
        assertEquals(null, post.deletedAt)
        verify(transactionService, never()).deleteTransaction(
            userId = 2L,
            tripId = 10L,
            transactionId = 200L,
        )
    }

    private fun mockPost(post: Post) {
        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
    }

    private fun createUser(id: Long = 1L): User {
        return User(
            nickname = "재완",
        ).apply {
            this.id = id
        }
    }

    private fun createTrip(ownerUser: User = createUser()): Trip {
        return Trip(
            ownerUser = ownerUser,
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            id = 10L
        }
    }

    private fun createParticipant(
        user: User = createUser(),
        trip: Trip = createTrip(ownerUser = user),
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = "재완",
            participantRole = TripParticipantRole.LEADER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            id = 100L
        }
    }

    private fun createTransaction(
        trip: Trip = createTrip(),
    ): Transaction {
        return Transaction(
            trip = trip,
            createdBy = trip.ownerUser,
            transactionType = TransactionType.EXPENSE,
            amount = BigDecimal("10000.00"),
            currency = "JPY",
            exchangeRate = BigDecimal("9.500000"),
            baseCurrency = "KRW",
            baseAmount = BigDecimal("95000.00"),
        ).apply {
            id = 200L
        }
    }

    private fun createPost(
        transaction: Transaction? = null,
        postType: PostType = if (transaction == null) PostType.RECORD else PostType.EXPENSE,
    ): Post {
        val author = createParticipant(
            trip = transaction?.trip ?: createTrip(),
        )

        return Post(
            trip = author.trip,
            transaction = transaction,
            author = author,
            postType = postType,
            title = "첫 기록",
            content = "여행 시작",
        ).apply {
            id = 300L
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
}
