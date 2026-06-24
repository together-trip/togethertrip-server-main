package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.post.ExpensePostCreatedPayload
import com.togethertrip.main.global.outbox.payload.post.PostCommentCreatedPayload
import com.togethertrip.main.global.outbox.payload.post.PostCreatedPayload
import com.togethertrip.main.global.outbox.repository.OutboxEventRepository
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.exchange.repository.ExchangeRateRepository
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.dto.request.CreateExpensePostRequest
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.exception.PostErrorCode
import com.togethertrip.main.post.pagination.PostCommentCursor
import com.togethertrip.main.post.pagination.PostCursor
import com.togethertrip.main.post.repository.PostAttachmentRepository
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.post.service.storage.PostAttachmentStorage
import com.togethertrip.main.post.service.storage.StoredPostAttachment
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEvent
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.dto.request.TransactionPaymentInput
import com.togethertrip.main.transaction.dto.request.TransactionShareInput
import com.togethertrip.main.transaction.repository.TransactionEventRepository
import com.togethertrip.main.transaction.repository.TransactionPaymentRepository
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.repository.TransactionShareRepository
import com.togethertrip.main.transaction.service.TransactionExchangeRateResolver
import com.togethertrip.main.transaction.service.support.TransactionCreationService
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.trip.service.support.TripNotificationRecipientResolver
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.data.domain.PageRequest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PostServiceTest {

    private lateinit var postRepository: PostRepository
    private lateinit var postAttachmentRepository: PostAttachmentRepository
    private lateinit var postCommentRepository: PostCommentRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transactionShareRepository: TransactionShareRepository
    private lateinit var transactionPaymentRepository: TransactionPaymentRepository
    private lateinit var transactionEventRepository: TransactionEventRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var userRepository: UserRepository
    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var postAttachmentStorage: PostAttachmentStorage
    private lateinit var postService: PostService

    @BeforeEach
    fun setUp() {
        postRepository = mock(PostRepository::class.java)
        postAttachmentRepository = mock(PostAttachmentRepository::class.java)
        postCommentRepository = mock(PostCommentRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        transactionRepository = mock(TransactionRepository::class.java)
        transactionShareRepository = mock(TransactionShareRepository::class.java)
        transactionPaymentRepository = mock(TransactionPaymentRepository::class.java)
        transactionEventRepository = mock(TransactionEventRepository::class.java)
        tripRepository = mock(TripRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        outboxEventRepository = mock(OutboxEventRepository::class.java)
        postAttachmentStorage = mock(PostAttachmentStorage::class.java)
        val transactionExchangeRateResolver = TransactionExchangeRateResolver(
            exchangeRateRepository = mock(ExchangeRateRepository::class.java),
            clock = Clock.fixed(
                Instant.parse("2026-07-02T00:30:00Z"),
                ZoneId.of("Asia/Seoul"),
            ),
        )
        val transactionCreationService = TransactionCreationService(
            transactionRepository = transactionRepository,
            transactionShareRepository = transactionShareRepository,
            transactionPaymentRepository = transactionPaymentRepository,
            transactionEventRepository = transactionEventRepository,
            tripRepository = tripRepository,
            tripParticipantRepository = tripParticipantRepository,
            transactionExchangeRateResolver = transactionExchangeRateResolver,
            userRepository = userRepository,
        )
        `when`(
            tripParticipantRepository.findActiveUserIdsForNotification(
                tripId = 10L,
                participantStatus = TripParticipantStatus.ACTIVE.name,
                userStatus = "ACTIVE",
            )
        ).thenReturn(emptyList())
        postService = PostService(
            postRepository = postRepository,
            postAttachmentRepository = postAttachmentRepository,
            postCommentRepository = postCommentRepository,
            tripParticipantRepository = tripParticipantRepository,
            transactionRepository = transactionRepository,
            postAttachmentStorage = postAttachmentStorage,
            transactionCreationService = transactionCreationService,
            outboxEventPublisher = OutboxEventPublisher(
                outboxEventRepository = outboxEventRepository,
                objectMapper = jacksonObjectMapper(),
            ),
            tripNotificationRecipientResolver = TripNotificationRecipientResolver(
                tripParticipantRepository = tripParticipantRepository,
            ),
        )
    }

    @Test
    fun `일반 기록은 transaction 없이 RECORD로 생성한다`() {
        val participant = createParticipant()

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
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
    fun `일반 기록 작성 시 작성자를 제외한 여행 참여자에게 알림 outbox를 발행한다`() {
        val participant = createParticipant()

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
        `when`(
            tripParticipantRepository.findActiveUserIdsForNotification(
                tripId = 10L,
                participantStatus = TripParticipantStatus.ACTIVE.name,
                userStatus = "ACTIVE",
            )
        ).thenReturn(listOf(1L, 2L))
        `when`(postRepository.save(any(Post::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Post).apply { id = 300L }
        }
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as OutboxEvent).apply { id = 900L }
        }

        postService.createPost(
            userId = 1L,
            tripId = 10L,
            request = CreatePostRequest(
                title = "첫 기록",
                content = "여행 시작",
            ),
        )

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        val event = eventCaptor.value
        val payload = jacksonObjectMapper().readValue<PostCreatedPayload>(event.payload)
        assertEquals(OutboxEventType.POST_CREATED.name, event.eventType)
        assertEquals(300L, event.aggregateId)
        assertEquals(listOf(2L), payload.recipients.map { it.userId })
        assertEquals("첫 기록", payload.title)
        assertEquals("일본 여행", payload.tripName)
        assertEquals("재완", payload.actorDisplayName)
    }

    @Test
    fun `거래 기반 기록은 요청 postType과 무관하게 EXPENSE로 생성한다`() {
        val participant = createParticipant()
        val transaction = createTransaction(trip = participant.trip)

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
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
    fun `소비 게시글 통합 작성은 거래와 게시글을 함께 생성하고 알림 outbox를 발행한다`() {
        val user = createUser(id = 1L)
        val trip = createTrip(ownerUser = user)
        val participant = createParticipant(
            user = user,
            trip = trip,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                id = 100L,
                tripId = 10L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
        `when`(
            tripParticipantRepository.findActiveUserIdsForNotification(
                tripId = 10L,
                participantStatus = TripParticipantStatus.ACTIVE.name,
                userStatus = "ACTIVE",
            )
        ).thenReturn(listOf(1L, 2L))
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Transaction).apply { id = 200L }
        }
        `when`(transactionPaymentRepository.save(any(TransactionPayment::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionPayment).apply { id = 210L }
        }
        `when`(transactionShareRepository.save(any(TransactionShare::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionShare).apply { id = 220L }
        }
        `when`(transactionEventRepository.save(any(TransactionEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionEvent).apply { id = 230L }
        }
        `when`(postRepository.save(any(Post::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Post).apply { id = 300L }
        }
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as OutboxEvent).apply { id = 900L }
        }

        val response = postService.createExpensePost(
            userId = 1L,
            tripId = 10L,
            request = CreateExpensePostRequest(
                title = "라멘",
                category = "식비",
                amount = BigDecimal("12000.00"),
                currency = "KRW",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("12000.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("12000.00"),
                    )
                ),
            ),
        )

        assertEquals(PostType.EXPENSE, response.post.postType)
        assertEquals(200L, response.post.transactionId)
        assertEquals(BigDecimal("12000.00"), response.transaction.summary.amount)
        assertEquals(1L, trip.expenseVersion)

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        val event = eventCaptor.value
        val payload = jacksonObjectMapper().readValue<ExpensePostCreatedPayload>(event.payload)
        assertEquals(OutboxEventType.EXPENSE_POST_CREATED.name, event.eventType)
        assertEquals(300L, event.aggregateId)
        assertEquals(listOf(2L), payload.recipients.map { it.userId })
        assertEquals(200L, payload.transactionId)
        assertEquals("라멘", payload.title)
        assertEquals(BigDecimal("12000.00"), payload.amount)
        assertEquals("KRW", payload.currency)
    }

    @Test
    fun `게시글 작성 시 multipart 파일을 저장하고 첨부 메타데이터를 응답한다`() {
        val participant = createParticipant()
        val file = createMultipartFile(
            name = "files",
            originalFilename = "receipt.jpg",
            contentType = "image/jpeg",
        )

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
        `when`(postAttachmentStorage.store(file)).thenReturn(
            StoredPostAttachment(
                storageKey = "receipt-stored.jpg",
                attachmentType = PostAttachmentType.IMAGE,
                fileUrl = "/uploads/post-attachments/receipt-stored.jpg",
                thumbnailUrl = null,
                fileSize = file.size,
                mimeType = file.contentType,
            )
        )
        `when`(postAttachmentRepository.saveAll(anyList<PostAttachment>())).thenAnswer { invocation ->
            invocation.getArgument<List<PostAttachment>>(0)
        }

        val response = postService.createPost(
            userId = 1L,
            tripId = 10L,
            request = CreatePostRequest(
                title = "영수증",
                files = listOf(file),
            ),
        )

        assertEquals(1, response.attachments.size)
        assertEquals(PostAttachmentType.IMAGE, response.attachments.first().attachmentType)
        assertEquals("/uploads/post-attachments/receipt-stored.jpg", response.attachments.first().fileUrl)
        verify(postAttachmentStorage, never()).delete(
            StoredPostAttachment(
                storageKey = "receipt-stored.jpg",
                attachmentType = PostAttachmentType.IMAGE,
                fileUrl = "/uploads/post-attachments/receipt-stored.jpg",
                thumbnailUrl = null,
                fileSize = file.size,
                mimeType = file.contentType,
            )
        )
    }

    @Test
    fun `게시글 작성 트랜잭션이 롤백되면 저장된 첨부 파일을 삭제한다`() {
        val participant = createParticipant()
        val file = createMultipartFile()
        val storedAttachment = StoredPostAttachment(
            storageKey = "rollback.jpg",
            attachmentType = PostAttachmentType.IMAGE,
            fileUrl = "/uploads/post-attachments/rollback.jpg",
            thumbnailUrl = null,
            fileSize = file.size,
            mimeType = file.contentType,
        )

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
        `when`(postAttachmentStorage.store(file)).thenReturn(storedAttachment)

        TransactionSynchronizationManager.initSynchronization()
        try {
            postService.createPost(
                userId = 1L,
                tripId = 10L,
                request = CreatePostRequest(
                    title = "영수증",
                    files = listOf(file),
                ),
            )

            TransactionSynchronizationManager
                .getSynchronizations()
                .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }

        verify(postAttachmentStorage).delete(storedAttachment)
    }

    @Test
    fun `게시글 작성 시 첨부 파일이 10장을 초과하면 실패한다`() {
        val participant = createParticipant()

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)

        val exception = assertBusinessException {
            postService.createPost(
                userId = 1L,
                tripId = 10L,
                request = CreatePostRequest(
                    title = "영수증",
                    files = (1..11).map { index ->
                        createMultipartFile(originalFilename = "image-$index.jpg")
                    },
                ),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(postAttachmentStorage)
    }

    @Test
    fun `거래가 다른 여행에 속하면 게시글 작성에 실패한다`() {
        val participant = createParticipant()
        val otherTrip = createTrip(id = 11L)
        val transaction = createTransaction(trip = otherTrip)

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
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
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
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
    fun `활성 여행 참여자가 아니면 게시글 작성에 실패한다`() {
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
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
            postRepository.findPosts(
                tripId = 10L,
                pageable = PageRequest.of(0, 3),
            )
        ).thenReturn(listOf(first, second, extra))
        `when`(
            postAttachmentRepository.findByPostIdInAndDeletedAtIsNullOrderByPostIdAscSortOrderAsc(
                listOf(303L, 302L)
            )
        ).thenReturn(emptyList())

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
    fun `게시글 목록 응답에 첨부를 sortOrder 순서로 포함한다`() {
        val post = createPost(id = 303L)
        val firstAttachment = createAttachment(
            post = post,
            id = 501L,
            fileUrl = "https://cdn.example.com/first.jpg",
            sortOrder = 1,
        )
        val secondAttachment = createAttachment(
            post = post,
            id = 502L,
            fileUrl = "https://cdn.example.com/second.jpg",
            sortOrder = 2,
        )

        `when`(
            postRepository.findPosts(
                tripId = 10L,
                pageable = PageRequest.of(0, 2),
            )
        ).thenReturn(listOf(post))
        `when`(
            postAttachmentRepository.findByPostIdInAndDeletedAtIsNullOrderByPostIdAscSortOrderAsc(
                listOf(303L)
            )
        ).thenReturn(listOf(firstAttachment, secondAttachment))

        val response = postService.getPosts(
            tripId = 10L,
            postType = null,
            cursor = null,
            size = 1,
        )

        assertEquals(1, response.items.size)
        assertEquals(listOf(501L, 502L), response.items.first().attachments.map { it.id })
        assertEquals("https://cdn.example.com/first.jpg", response.items.first().attachments.first().fileUrl)
    }

    @Test
    fun `cursor가 있으면 cursor 이후 게시글을 조회한다`() {
        val cursor = PostCursor(
            createdAt = Instant.parse("2026-06-05T02:00:00Z"),
            id = 302L,
        )

        `when`(
            postRepository.findPostsByTypeAndCursor(
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
    fun `게시글 수정 시 날짜 위치 필드를 함께 수정한다`() {
        val post = createPost()
        val occurredAt = Instant.parse("2026-06-06T02:30:00Z")

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(postAttachmentRepository.findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(300L))
            .thenReturn(emptyList())

        val response = postService.updatePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            request = UpdatePostRequest(
                title = "수정 제목",
                category = "식사",
                content = "수정 내용",
                occurredAt = occurredAt,
                placeName = "오사카",
                latitude = BigDecimal("34.6937000"),
                longitude = BigDecimal("135.5023000"),
            ),
        )

        assertEquals("수정 제목", response.title)
        assertEquals("식사", response.category)
        assertEquals("수정 내용", response.content)
        assertEquals(occurredAt, response.occurredAt)
        assertEquals("오사카", response.placeName)
        assertEquals(BigDecimal("34.6937000"), response.latitude)
        assertEquals(BigDecimal("135.5023000"), response.longitude)
    }

    @Test
    fun `정산 완료 이후에도 일반 기록 게시글은 수정할 수 있다`() {
        val post = createPost().apply {
            trip.settlementStatus = TripSettlementStatus.SETTLED
        }

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(postAttachmentRepository.findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(300L))
            .thenReturn(emptyList())

        val response = postService.updatePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            request = UpdatePostRequest(title = "정산 후 기록 수정"),
        )

        assertEquals("정산 후 기록 수정", response.title)
    }

    @Test
    fun `정산 완료 이후에는 소비 게시글 수정에 실패한다`() {
        val user = createUser()
        val trip = createTrip(ownerUser = user).apply {
            settlementStatus = TripSettlementStatus.SETTLED
        }
        val participant = createParticipant(
            user = user,
            trip = trip,
        )
        val transaction = createTransaction(trip = trip)
        val post = createPost(
            author = participant,
            transaction = transaction,
        )

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)

        val exception = assertBusinessException {
            postService.updatePost(
                userId = 1L,
                tripId = 10L,
                postId = 300L,
                request = UpdatePostRequest(title = "정산 후 소비 수정"),
            )
        }

        assertEquals(PostErrorCode.POST_LOCKED_BY_SETTLEMENT, exception.errorCode)
        verify(postAttachmentRepository, never()).findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(300L)
    }

    @Test
    fun `정산 미시작 상태에서는 소비 게시글을 수정할 수 있다`() {
        val user = createUser()
        val trip = createTrip(ownerUser = user)
        val participant = createParticipant(
            user = user,
            trip = trip,
        )
        val transaction = createTransaction(trip = trip)
        val post = createPost(
            author = participant,
            transaction = transaction,
        )

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(postAttachmentRepository.findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(300L))
            .thenReturn(emptyList())

        val response = postService.updatePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            request = UpdatePostRequest(title = "소비 수정"),
        )

        assertEquals("소비 수정", response.title)
    }

    @Test
    fun `게시글 수정에서 replaceAttachments가 false면 기존 첨부를 유지한다`() {
        val post = createPost()
        val attachment = createAttachment(post = post)

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(postAttachmentRepository.findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(300L))
            .thenReturn(listOf(attachment))

        val response = postService.updatePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            request = UpdatePostRequest(title = "수정"),
        )

        assertEquals(listOf(500L), response.attachments.map { it.id })
        assertEquals(null, attachment.deletedAt)
    }

    @Test
    fun `게시글 수정에서 replaceAttachments가 true이고 파일이 없으면 기존 첨부를 제거한다`() {
        val post = createPost()
        val attachment = createAttachment(post = post)

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(postAttachmentRepository.findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(300L))
            .thenReturn(listOf(attachment))

        val response = postService.updatePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            request = UpdatePostRequest(
                title = "수정",
                replaceAttachments = true,
            ),
        )

        assertEquals(emptyList(), response.attachments)
        assertNotNull(attachment.deletedAt)
    }

    @Test
    fun `게시글 수정에서 replaceAttachments가 true이고 파일이 있으면 기존 첨부를 업로드 결과로 교체한다`() {
        val post = createPost()
        val oldAttachment = createAttachment(post = post)
        val file = createMultipartFile(
            name = "files",
            originalFilename = "new.jpg",
            contentType = "image/jpeg",
        )

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(postAttachmentRepository.findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(300L))
            .thenReturn(listOf(oldAttachment))
        `when`(postAttachmentStorage.store(file)).thenReturn(
            StoredPostAttachment(
                storageKey = "new-stored.jpg",
                attachmentType = PostAttachmentType.IMAGE,
                fileUrl = "/uploads/post-attachments/new-stored.jpg",
                thumbnailUrl = null,
                fileSize = file.size,
                mimeType = file.contentType,
            )
        )
        `when`(postAttachmentRepository.saveAll(anyList<PostAttachment>())).thenAnswer { invocation ->
            invocation.getArgument<List<PostAttachment>>(0)
        }

        val response = postService.updatePost(
            userId = 1L,
            tripId = 10L,
            postId = 300L,
            request = UpdatePostRequest(
                title = "수정",
                replaceAttachments = true,
                files = listOf(file),
            ),
        )

        assertNotNull(oldAttachment.deletedAt)
        assertEquals(listOf("/uploads/post-attachments/new-stored.jpg"), response.attachments.map { it.fileUrl })
    }

    @Test
    fun `게시글 수정 시 교체 첨부 파일이 10장을 초과하면 실패한다`() {
        val post = createPost()

        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)

        val exception = assertBusinessException {
            postService.updatePost(
                userId = 1L,
                tripId = 10L,
                postId = 300L,
                request = UpdatePostRequest(
                    title = "수정",
                    replaceAttachments = true,
                    files = (1..11).map { index ->
                        createMultipartFile(originalFilename = "image-$index.jpg")
                    },
                ),
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
        verifyNoInteractions(postAttachmentStorage)
    }

    @Test
    fun `댓글 작성 시 댓글 수가 증가한다`() {
        val participant = createParticipant()
        val post = createPost(author = participant)

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
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
    fun `댓글 작성 시 게시글 작성자에게 알림 outbox를 발행한다`() {
        val authorUser = createUser(id = 1L)
        val commenterUser = createUser(id = 2L)
        val trip = createTrip(ownerUser = authorUser)
        val postAuthor = createParticipant(
            user = authorUser,
            trip = trip,
        )
        val commenter = createParticipant(
            user = commenterUser,
            trip = trip,
        ).apply {
            id = 101L
            displayName = "민서"
        }
        val post = createPost(author = postAuthor)

        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 2L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(commenter)
        `when`(
            postRepository.findByIdAndTripIdAndDeletedAtIsNull(
                id = 300L,
                tripId = 10L,
            )
        ).thenReturn(post)
        `when`(postCommentRepository.save(any(PostComment::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as PostComment).apply { id = 400L }
        }
        `when`(outboxEventRepository.save(any(OutboxEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as OutboxEvent).apply { id = 901L }
        }

        postService.createComment(
            userId = 2L,
            tripId = 10L,
            postId = 300L,
            request = CreatePostCommentRequest(content = "좋아요"),
        )

        val eventCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
        verify(outboxEventRepository).save(eventCaptor.capture())
        val event = eventCaptor.value
        val payload = jacksonObjectMapper().readValue<PostCommentCreatedPayload>(event.payload)
        assertEquals(OutboxEventType.POST_COMMENT_CREATED.name, event.eventType)
        assertEquals(300L, event.aggregateId)
        assertEquals(listOf(1L), payload.recipients.map { it.userId })
        assertEquals(2L, payload.actorUserId)
        assertEquals(400L, payload.commentId)
        assertEquals("첫 기록", payload.postTitle)
    }

    @Test
    fun `활성 여행 참여자가 아니면 댓글 작성에 실패한다`() {
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = 10L,
                userId = 1L,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            postService.createComment(
                userId = 1L,
                tripId = 10L,
                postId = 300L,
                request = CreatePostCommentRequest(content = "좋아요"),
            )
        }

        assertEquals(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND, exception.errorCode)
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
            postCommentRepository.findRootComments(
                postId = 300L,
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
        transaction: Transaction? = null,
        postType: PostType = if (transaction == null) PostType.RECORD else PostType.EXPENSE,
    ): Post {
        return Post(
            trip = author.trip,
            transaction = transaction,
            author = author,
            postType = postType,
            title = "첫 기록",
            content = "여행 시작",
        ).apply {
            this.id = id
        }
    }

    private fun createAttachment(
        post: Post,
        id: Long = 500L,
        fileUrl: String = "https://cdn.example.com/old.jpg",
        sortOrder: Int = 0,
    ): PostAttachment {
        return PostAttachment(
            post = post,
            attachmentType = PostAttachmentType.IMAGE,
            fileUrl = fileUrl,
            sortOrder = sortOrder,
        ).apply {
            this.id = id
        }
    }

    private fun createMultipartFile(
        name: String = "files",
        originalFilename: String = "image.jpg",
        contentType: String = "image/jpeg",
    ): MultipartFile {
        return MockMultipartFile(
            name,
            originalFilename,
            contentType,
            "image-content".toByteArray(),
        )
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
