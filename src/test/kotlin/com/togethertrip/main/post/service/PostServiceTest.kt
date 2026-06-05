package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.exception.PostErrorCode
import com.togethertrip.main.post.pagination.PostCommentCursor
import com.togethertrip.main.post.pagination.PostCursor
import com.togethertrip.main.post.repository.PostAttachmentRepository
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PostServiceTest {

    private lateinit var postRepository: PostRepository
    private lateinit var postAttachmentRepository: PostAttachmentRepository
    private lateinit var postCommentRepository: PostCommentRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var postService: PostService

    @BeforeEach
    fun setUp() {
        postRepository = mock(PostRepository::class.java)
        postAttachmentRepository = mock(PostAttachmentRepository::class.java)
        postCommentRepository = mock(PostCommentRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        transactionRepository = mock(TransactionRepository::class.java)
        postService = PostService(
            postRepository = postRepository,
            postAttachmentRepository = postAttachmentRepository,
            postCommentRepository = postCommentRepository,
            tripParticipantRepository = tripParticipantRepository,
            transactionRepository = transactionRepository,
        )
    }

    @Test
    fun `일반 기록은 transaction 없이 RECORD로 생성한다`() {
        val participant = createParticipant()

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
        ).thenReturn(participant)

        val response = postService.createPost(
            userId = 1L,
            tripId = 10L,
            request = CreatePostRequest(
                title = "첫 기록",
                content = "여행 시작",
                postType = PostType.EXPENSE,
            ),
        )

        assertEquals(PostType.RECORD, response.postType)
        assertEquals(null, response.transactionId)
        verify(postRepository).save(any(Post::class.java))
    }

    @Test
    fun `거래 기반 기록은 요청 postType과 무관하게 EXPENSE로 생성한다`() {
        val participant = createParticipant()
        val transaction = createTransaction(trip = participant.trip)

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
        ).thenReturn(participant)
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(200L))
            .thenReturn(transaction)

        val response = postService.createPost(
            userId = 1L,
            tripId = 10L,
            request = CreatePostRequest(
                transactionId = 200L,
                postType = PostType.RECORD,
            ),
        )

        assertEquals(PostType.EXPENSE, response.postType)
        assertEquals(200L, response.transactionId)
    }

    @Test
    fun `거래가 다른 여행에 속하면 게시글 작성에 실패한다`() {
        val participant = createParticipant()
        val otherTrip = createTrip(id = 11L)
        val transaction = createTransaction(trip = otherTrip)

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
        ).thenReturn(participant)
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(200L))
            .thenReturn(transaction)

        val exception = assertBusinessException {
            postService.createPost(
                userId = 1L,
                tripId = 10L,
                request = CreatePostRequest(transactionId = 200L),
            )
        }

        assertEquals(PostErrorCode.TRANSACTION_TRIP_MISMATCH, exception.errorCode)
    }

    @Test
    fun `여행 참여자가 없으면 게시글 작성에 실패한다`() {
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            postService.createPost(
                userId = 1L,
                tripId = 10L,
                request = CreatePostRequest(title = "기록"),
            )
        }

        assertEquals(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `게시글 목록은 size보다 하나 더 조회해 다음 cursor를 생성한다`() {
        val first = createPost(id = 303L).apply {
            createdAt = Instant.parse("2026-06-05T03:00:00Z")
        }
        val second = createPost(id = 302L).apply {
            createdAt = Instant.parse("2026-06-05T02:00:00Z")
        }
        val extra = createPost(id = 301L).apply {
            createdAt = Instant.parse("2026-06-05T01:00:00Z")
        }

        `when`(
            postRepository.findPostsByCursor(
                tripId = 10L,
                postType = null,
                cursorCreatedAt = null,
                cursorId = null,
                pageable = PageRequest.of(0, 3),
            )
        ).thenReturn(listOf(first, second, extra))

        val response = postService.getPosts(
            tripId = 10L,
            postType = null,
            cursor = null,
            size = 2,
        )

        assertEquals(2, response.items.size)
        assertEquals(true, response.hasNext)
        assertNotNull(response.nextCursor)

        val nextCursor = PostCursor.decode(response.nextCursor!!)
        assertEquals(second.createdAt, nextCursor.createdAt)
        assertEquals(302L, nextCursor.id)
    }

    @Test
    fun `cursor가 있으면 cursor 이후 게시글을 조회한다`() {
        val cursor = PostCursor(
            createdAt = Instant.parse("2026-06-05T02:00:00Z"),
            id = 302L,
        )

        `when`(
            postRepository.findPostsByCursor(
                tripId = 10L,
                postType = PostType.RECORD,
                cursorCreatedAt = cursor.createdAt,
                cursorId = cursor.id,
                pageable = PageRequest.of(0, 21),
            )
        ).thenReturn(emptyList())

        val response = postService.getPosts(
            tripId = 10L,
            postType = "RECORD",
            cursor = cursor.encode(),
            size = null,
        )

        assertEquals(false, response.hasNext)
        assertEquals(null, response.nextCursor)
    }

    @Test
    fun `잘못된 cursor면 조회에 실패한다`() {
        val exception = assertBusinessException {
            postService.getPosts(
                tripId = 10L,
                postType = null,
                cursor = "invalid-cursor",
                size = 20,
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `작성자가 아니면 게시글 수정에 실패한다`() {
        val author = createParticipant()
        val post = createPost(author = author)

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)

        val exception = assertBusinessException {
            postService.updatePost(
                userId = 2L,
                tripId = 10L,
                postId = 300L,
                request = UpdatePostRequest(title = "수정"),
            )
        }

        assertEquals(CommonErrorCode.ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `게시글 삭제는 soft delete로 처리한다`() {
        val post = createPost()

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)

        postService.deletePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
        )

        assertNotNull(post.deletedAt)
    }

    @Test
    fun `댓글 작성 시 댓글 수가 증가한다`() {
        val participant = createParticipant()
        val post = createPost(author = participant)

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
            )
        ).thenReturn(participant)
        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)

        val response = postService.createComment(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            request = CreatePostCommentRequest(content = "좋아요"),
        )

        assertEquals("좋아요", response.content)
        assertEquals(1, post.commentCount)
        verify(postCommentRepository).save(any(PostComment::class.java))
    }

    @Test
    fun `댓글 목록은 원댓글만 size보다 하나 더 조회해 다음 cursor를 생성한다`() {
        val post = createPost()
        val first = createComment(
            post = post,
            id = 401L,
            content = "첫 댓글",
        ).apply {
            createdAt = Instant.parse("2026-06-05T01:00:00Z")
        }
        val second = createComment(
            post = post,
            id = 402L,
            content = "둘째 댓글",
        ).apply {
            createdAt = Instant.parse("2026-06-05T02:00:00Z")
        }
        val extra = createComment(
            post = post,
            id = 403L,
            content = "셋째 댓글",
        ).apply {
            createdAt = Instant.parse("2026-06-05T03:00:00Z")
        }

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(
            postCommentRepository.findRootCommentsByCursor(
                postId = 300L,
                cursorCreatedAt = null,
                cursorId = null,
                pageable = PageRequest.of(0, 3),
            )
        ).thenReturn(listOf(first, second, extra))

        val response = postService.getComments(
            tripId = 10L,
            postId = 300L,
            cursor = null,
            size = 2,
        )

        assertEquals(2, response.items.size)
        assertEquals(true, response.hasNext)
        assertNotNull(response.nextCursor)

        val nextCursor = PostCommentCursor.decode(response.nextCursor!!)
        assertEquals(second.createdAt, nextCursor.createdAt)
        assertEquals(402L, nextCursor.id)
    }

    @Test
    fun `댓글 cursor가 있으면 cursor 이후 원댓글을 조회한다`() {
        val post = createPost()
        val cursor = PostCommentCursor(
            createdAt = Instant.parse("2026-06-05T02:00:00Z"),
            id = 402L,
        )

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(
            postCommentRepository.findRootCommentsByCursor(
                postId = 300L,
                cursorCreatedAt = cursor.createdAt,
                cursorId = cursor.id,
                pageable = PageRequest.of(0, 21),
            )
        ).thenReturn(emptyList())

        val response = postService.getComments(
            tripId = 10L,
            postId = 300L,
            cursor = cursor.encode(),
            size = null,
        )

        assertEquals(false, response.hasNext)
        assertEquals(null, response.nextCursor)
    }

    @Test
    fun `잘못된 댓글 cursor면 조회에 실패한다`() {
        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(createPost())

        val exception = assertBusinessException {
            postService.getComments(
                tripId = 10L,
                postId = 300L,
                cursor = "invalid-cursor",
                size = 20,
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `댓글 삭제 시 댓글 수가 감소하고 soft delete로 처리한다`() {
        val participant = createParticipant()
        val post = createPost(author = participant).apply {
            commentCount = 1
        }
        val comment = PostComment(
            post = post,
            author = participant,
            content = "좋아요",
        ).apply {
            id = 400L
        }

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(
            postCommentRepository.findByIdAndPostIdAndParentCommentIsNullAndDeletedAtIsNull(
                id = 400L,
                postId = 300L,
            )
        ).thenReturn(comment)

        postService.deleteComment(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            commentId = 400L,
        )

        assertEquals(0, post.commentCount)
        assertNotNull(comment.deletedAt)
    }

    private fun createUser(id: Long = 1L): User {
        return User(
            nickname = "재완",
        ).apply {
            this.id = id
        }
    }

    private fun createTrip(
        id: Long = 10L,
        ownerUser: User = createUser(),
    ): Trip {
        return Trip(
            ownerUser = ownerUser,
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            this.id = id
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
        trip: Trip,
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
            status = TransactionStatus.ACTIVE,
        ).apply {
            id = 200L
        }
    }

    private fun createPost(
        id: Long = 300L,
        author: TripParticipant = createParticipant(),
    ): Post {
        return Post(
            trip = author.trip,
            author = author,
            postType = PostType.RECORD,
            title = "첫 기록",
            content = "여행 시작",
        ).apply {
            this.id = id
        }
    }

    private fun createComment(
        post: Post,
        id: Long,
        content: String,
    ): PostComment {
        return PostComment(
            post = post,
            author = post.author,
            content = content,
        ).apply {
            this.id = id
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
