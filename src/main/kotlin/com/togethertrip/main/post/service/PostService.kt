package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.post.ExpensePostCreatedPayload
import com.togethertrip.main.global.outbox.payload.post.PostCommentCreatedPayload
import com.togethertrip.main.global.outbox.payload.post.PostCreatedPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.moderation.service.ModerationPolicy
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreateExpensePostRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdateExpensePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.dto.response.CreateExpensePostResponse
import com.togethertrip.main.post.dto.response.PostCommentResponse
import com.togethertrip.main.post.dto.response.PostDetailResponse
import com.togethertrip.main.post.dto.response.PostSummaryResponse
import com.togethertrip.main.post.exception.PostErrorCode
import com.togethertrip.main.post.pagination.PostCommentCursor
import com.togethertrip.main.post.pagination.PostCursor
import com.togethertrip.main.post.repository.PostAttachmentRepository
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.post.service.storage.PostAttachmentStorage
import com.togethertrip.main.post.service.storage.StoredPostAttachment
import com.togethertrip.main.post.service.storage.StoredPostAttachmentFile
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.dto.response.TransactionDetailResponse
import com.togethertrip.main.transaction.service.TransactionService
import com.togethertrip.main.transaction.service.support.TransactionCreationResult
import com.togethertrip.main.transaction.service.support.TransactionCreationService
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.service.support.TripNotificationRecipientResolver
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

@Service
class PostService(
    private val postRepository: PostRepository,
    private val postAttachmentRepository: PostAttachmentRepository,
    private val postCommentRepository: PostCommentRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val transactionRepository: TransactionRepository,
    private val postAttachmentStorage: PostAttachmentStorage,
    private val transactionCreationService: TransactionCreationService,
    private val transactionService: TransactionService,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val tripNotificationRecipientResolver: TripNotificationRecipientResolver,
    private val moderationPolicy: ModerationPolicy = ModerationPolicy.NOOP,
) {

    @Transactional
    fun createPost(
        userId: Long,
        tripId: Long,
        request: CreatePostRequest,
    ): PostDetailResponse {
        moderationPolicy.validateUserCanWrite(userId)
        moderationPolicy.validateContent(request.title, request.category, request.content, request.placeName)
        val author = getParticipant(
            userId = userId,
            tripId = tripId,
        )
        val transaction = request.transactionId?.let { transactionId ->
            transactionRepository.findByIdAndDeletedAtIsNull(transactionId)
                ?: throw BusinessException(PostErrorCode.TRANSACTION_NOT_FOUND)
        }

        if (transaction != null && transaction.trip.id != tripId) {
            throw BusinessException(PostErrorCode.TRANSACTION_TRIP_MISMATCH)
        }
        if (transaction != null) {
            validateLinkableExpenseTransaction(transaction)
        }

        val post = Post(
            trip = author.trip,
            transaction = transaction,
            author = author,
            postType = if (transaction == null) PostType.RECORD else PostType.EXPENSE,
            title = request.title,
            category = request.category,
            content = request.content,
            occurredAt = request.occurredAt,
            placeName = request.placeName,
            latitude = request.latitude,
            longitude = request.longitude,
        )
        transaction?.updateMetadata(
            category = request.category,
            occurredAt = request.occurredAt,
        )

        postRepository.save(post)

        val attachments = saveAttachments(
            post = post,
            files = request.files,
        )

        if (post.postType == PostType.RECORD) {
            publishPostCreated(post)
        }

        return PostDetailResponse.from(
            post = post,
            attachments = attachments,
        )
    }

    @Transactional
    fun createExpensePost(
        userId: Long,
        tripId: Long,
        request: CreateExpensePostRequest,
    ): CreateExpensePostResponse {
        moderationPolicy.validateUserCanWrite(userId)
        moderationPolicy.validateContent(request.title, request.category, request.content, request.placeName)
        val creationResult = transactionCreationService.create(
            userId = userId,
            tripId = tripId,
            request = request.toCreateTransactionRequest(),
        )
        val post = Post(
            trip = creationResult.trip,
            transaction = creationResult.transaction,
            author = creationResult.actorParticipant,
            postType = PostType.EXPENSE,
            title = request.title,
            category = request.category,
            content = request.content,
            occurredAt = request.occurredAt,
            placeName = request.placeName,
            latitude = request.latitude,
            longitude = request.longitude,
        )

        postRepository.save(post)

        val attachments = saveAttachments(
            post = post,
            files = request.files,
        )

        publishExpensePostCreated(
            creationResult = creationResult,
            post = post,
        )

        return CreateExpensePostResponse(
            post = PostDetailResponse.from(
                post = post,
                attachments = attachments,
            ),
            transaction = TransactionDetailResponse.from(
                transaction = creationResult.transaction,
                payments = creationResult.payments,
                shares = creationResult.shares,
            ),
        )
    }

    @Transactional
    fun updateExpensePost(
        userId: Long,
        tripId: Long,
        postId: Long,
        request: UpdateExpensePostRequest,
    ): CreateExpensePostResponse {
        moderationPolicy.validateUserCanWrite(userId)
        moderationPolicy.validateContent(request.title, request.category, request.content, request.placeName)
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        validateAuthor(
            author = post.author,
            userId = userId,
        )
        validateEditablePost(post)
        validateExpensePost(post)
        val transactionId = post.transaction?.id
            ?: throw BusinessException(PostErrorCode.TRANSACTION_NOT_FOUND)

        val transaction = transactionService.updateTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
            request = request.toUpdateTransactionRequest(),
        )
        post.update(
            title = request.title,
            category = request.category,
            content = request.content,
            occurredAt = request.occurredAt,
            placeName = request.placeName,
            latitude = request.latitude,
            longitude = request.longitude,
        )
        val attachments = if (request.replaceAttachments) {
            replaceAttachments(
                post = post,
                files = request.files,
            )
        } else {
            postAttachmentRepository
                .findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(postId)
        }

        return CreateExpensePostResponse(
            post = PostDetailResponse.from(
                post = post,
                attachments = attachments,
            ),
            transaction = transaction,
        )
    }

    @Transactional(readOnly = true)
    fun getPosts(
        tripId: Long,
        postType: String?,
        cursor: String?,
        size: Int?,
        userId: Long? = null,
    ): CursorResponse<PostSummaryResponse> {
        val requestedSize = size?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
        val pageable = PageRequest.of(0, requestedSize + 1)
        val parsedPostType = postType?.let(::parsePostType)
        val parsedCursor = cursor?.let(::parseCursor)
        val posts = findPosts(
            tripId = tripId,
            postType = parsedPostType,
            cursor = parsedCursor,
            pageable = pageable,
            viewerUserId = userId,
        )
        val responseItems = posts.take(requestedSize)
        val hasNext = posts.size > requestedSize
        val nextCursor = if (hasNext && responseItems.isNotEmpty()) {
            val lastPost = responseItems.last()
            PostCursor(
                createdAt = lastPost.createdAt,
                id = lastPost.id,
            ).encode()
        } else {
            null
        }

        val attachmentsByPostId = findAttachmentsByPostId(responseItems)
        val visibleCommentCounts = findVisibleCommentCounts(responseItems, userId)

        return CursorResponse(
            items = responseItems.map { post ->
                PostSummaryResponse.from(
                    post = post,
                    attachments = attachmentsByPostId[post.id] ?: emptyList(),
                    commentCount = visibleCommentCounts[post.id] ?: 0,
                )
            },
            nextCursor = nextCursor,
            hasNext = hasNext,
            size = responseItems.size,
        )
    }

    private fun findPosts(
        tripId: Long,
        postType: PostType?,
        cursor: PostCursor?,
        pageable: PageRequest,
        viewerUserId: Long?,
    ): List<Post> {
        return when {
            postType != null && cursor != null -> postRepository.findPostsByTypeAndCursor(
                tripId = tripId,
                postType = postType,
                cursorCreatedAt = cursor.createdAt,
                cursorId = cursor.id,
                pageable = pageable,
                viewerUserId = viewerUserId,
            )

            postType != null -> postRepository.findPostsByType(
                tripId = tripId,
                postType = postType,
                pageable = pageable,
                viewerUserId = viewerUserId,
            )

            cursor != null -> postRepository.findPostsByCursor(
                tripId = tripId,
                cursorCreatedAt = cursor.createdAt,
                cursorId = cursor.id,
                pageable = pageable,
                viewerUserId = viewerUserId,
            )

            else -> postRepository.findPosts(
                tripId = tripId,
                pageable = pageable,
                viewerUserId = viewerUserId,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getPost(
        tripId: Long,
        postId: Long,
        userId: Long? = null,
    ): PostDetailResponse {
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        if (userId != null && !moderationPolicy.canViewPost(userId, post)) {
            throw BusinessException(PostErrorCode.POST_NOT_FOUND)
        }
        val attachments = postAttachmentRepository
            .findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(postId)

        return PostDetailResponse.from(
            post = post,
            attachments = attachments,
            commentCount = visibleCommentCount(post, userId),
        )
    }

    @Transactional(readOnly = true)
    fun getAttachment(
        userId: Long,
        tripId: Long,
        postId: Long,
        attachmentId: Long,
    ): StoredPostAttachmentFile {
        getParticipant(userId, tripId)
        val post = getPostOrThrow(tripId, postId)
        if (!moderationPolicy.canViewPost(userId, post)) {
            throw BusinessException(PostErrorCode.POST_NOT_FOUND)
        }
        val attachment = postAttachmentRepository.findByIdAndPostIdAndDeletedAtIsNull(attachmentId, postId)
            ?: throw BusinessException(PostErrorCode.POST_NOT_FOUND)
        return postAttachmentStorage.load(attachment)
    }

    @Transactional
    fun updatePost(
        userId: Long,
        tripId: Long,
        postId: Long,
        request: UpdatePostRequest,
    ): PostDetailResponse {
        moderationPolicy.validateUserCanWrite(userId)
        moderationPolicy.validateContent(request.title, request.category, request.content, request.placeName)
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        validateAuthor(
            author = post.author,
            userId = userId,
        )
        validateEditablePost(post)

        post.update(
            title = request.title,
            category = request.category,
            content = request.content,
            occurredAt = request.occurredAt,
            placeName = request.placeName,
            latitude = request.latitude,
            longitude = request.longitude,
        )
        post.transaction?.updateMetadata(
            category = request.category,
            occurredAt = request.occurredAt,
        )

        val attachments = if (request.replaceAttachments) {
            replaceAttachments(
                post = post,
                files = request.files,
            )
        } else {
            postAttachmentRepository
                .findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(postId)
        }

        return PostDetailResponse.from(
            post = post,
            attachments = attachments,
        )
    }

    @Transactional
    fun createComment(
        userId: Long,
        tripId: Long,
        postId: Long,
        request: CreatePostCommentRequest,
    ): PostCommentResponse {
        moderationPolicy.validateUserCanWrite(userId)
        moderationPolicy.validateContent(request.content)
        val author = getParticipant(
            userId = userId,
            tripId = tripId,
        )
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        if (!post.isVisibleByModeration()) {
            throw BusinessException(PostErrorCode.POST_NOT_FOUND)
        }
        moderationPolicy.validateCommentInteraction(userId, post)
        val comment = PostComment(
            post = post,
            author = author,
            content = request.content,
        )

        post.increaseCommentCount()
        postCommentRepository.save(comment)

        publishPostCommentCreated(comment)

        return PostCommentResponse.from(comment)
    }

    @Transactional(readOnly = true)
    fun getComments(
        tripId: Long,
        postId: Long,
        cursor: String?,
        size: Int?,
        userId: Long? = null,
    ): CursorResponse<PostCommentResponse> {
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        if (userId != null && !moderationPolicy.canViewPost(userId, post)) {
            throw BusinessException(PostErrorCode.POST_NOT_FOUND)
        }
        val requestedSize = size?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
        val pageable = PageRequest.of(0, requestedSize + 1)
        val parsedCursor = cursor?.let(::parseCommentCursor)
        val comments = if (parsedCursor == null) {
            postCommentRepository.findRootComments(
                postId = postId,
                pageable = pageable,
                viewerUserId = userId,
            )
        } else {
            postCommentRepository.findRootCommentsByCursor(
                postId = postId,
                cursorCreatedAt = parsedCursor.createdAt,
                cursorId = parsedCursor.id,
                pageable = pageable,
                viewerUserId = userId,
            )
        }
        val responseItems = comments.take(requestedSize)
        val hasNext = comments.size > requestedSize
        val nextCursor = if (hasNext && responseItems.isNotEmpty()) {
            val lastComment = responseItems.last()
            PostCommentCursor(
                createdAt = lastComment.createdAt,
                id = lastComment.id,
            ).encode()
        } else {
            null
        }

        return CursorResponse(
            items = responseItems.map(PostCommentResponse::from),
            nextCursor = nextCursor,
            hasNext = hasNext,
            size = responseItems.size,
        )
    }

    @Transactional
    fun deleteComment(
        userId: Long,
        tripId: Long,
        postId: Long,
        commentId: Long,
    ) {
        moderationPolicy.validateUserCanWrite(userId)
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        val comment = postCommentRepository.findByIdAndPostIdAndParentCommentIsNullAndDeletedAtIsNull(
            id = commentId,
            postId = postId,
        ) ?: throw BusinessException(PostErrorCode.POST_COMMENT_NOT_FOUND)

        validateAuthor(
            author = comment.author,
            userId = userId,
        )

        comment.markDeleted()
        post.decreaseCommentCount()
    }

    private fun getPostOrThrow(
        tripId: Long,
        postId: Long,
    ): Post {
        return postRepository.findByIdAndTripIdAndDeletedAtIsNull(
            id = postId,
            tripId = tripId,
        ) ?: throw BusinessException(PostErrorCode.POST_NOT_FOUND)
    }

    private fun getParticipant(
        userId: Long,
        tripId: Long,
    ): TripParticipant {
        return tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
            participantStatus = TripParticipantStatus.ACTIVE,
        ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
    }

    private fun validateAuthor(
        author: TripParticipant,
        userId: Long,
    ) {
        if (author.user?.id != userId) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
    }

    private fun validateEditablePost(post: Post) {
        if (post.transaction != null && post.trip.settlementStatus != TripSettlementStatus.NOT_STARTED) {
            throw BusinessException(PostErrorCode.POST_LOCKED_BY_SETTLEMENT)
        }
    }

    private fun validateExpensePost(post: Post) {
        if (post.postType != PostType.EXPENSE) {
            throw BusinessException(PostErrorCode.TRANSACTION_NOT_FOUND)
        }
    }

    private fun validateLinkableExpenseTransaction(transaction: Transaction) {
        if (transaction.trip.settlementStatus != TripSettlementStatus.NOT_STARTED) {
            throw BusinessException(PostErrorCode.POST_LOCKED_BY_SETTLEMENT)
        }
        if (transaction.status != TransactionStatus.ACTIVE) {
            throw BusinessException(PostErrorCode.TRANSACTION_NOT_ACTIVE)
        }
        if (postRepository.existsByTransactionIdAndDeletedAtIsNull(transaction.id)) {
            throw BusinessException(PostErrorCode.EXPENSE_POST_ALREADY_EXISTS)
        }
    }

    private fun findAttachmentsByPostId(posts: List<Post>): Map<Long, List<PostAttachment>> {
        if (posts.isEmpty()) {
            return emptyMap()
        }

        return postAttachmentRepository
            .findByPostIdInAndDeletedAtIsNullOrderByPostIdAscSortOrderAsc(posts.map { it.id })
            .groupBy { it.post.id }
    }

    private fun visibleCommentCount(post: Post, viewerUserId: Long?): Int {
        if (viewerUserId == null) return post.commentCount
        return postCommentRepository.countVisibleRootComments(post.id, viewerUserId).toInt()
    }

    private fun findVisibleCommentCounts(posts: List<Post>, viewerUserId: Long?): Map<Long, Int> {
        if (posts.isEmpty()) return emptyMap()
        if (viewerUserId == null) return posts.associate { it.id to it.commentCount }
        return postCommentRepository.findVisibleCommentCounts(posts.map { it.id }, viewerUserId)
            .associate { it.postId to it.commentCount.toInt() }
    }

    private fun replaceAttachments(
        post: Post,
        files: List<MultipartFile>,
    ): List<PostAttachment> {
        postAttachmentRepository
            .findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(post.id)
            .forEach { it.markDeleted() }

        return saveAttachments(
            post = post,
            files = files,
        )
    }

    private fun saveAttachments(
        post: Post,
        files: List<MultipartFile>,
    ): List<PostAttachment> {
        val uploadFiles = files.filterNot { it.isEmpty }

        if (uploadFiles.size > MAX_ATTACHMENT_COUNT) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        val postAttachments = uploadFiles.mapIndexed { index, file ->
            val storedAttachment = postAttachmentStorage.store(file)
            registerRollbackCleanup(storedAttachment)

            PostAttachment(
                post = post,
                attachmentType = storedAttachment.attachmentType,
                fileUrl = storedAttachment.fileUrl,
                thumbnailUrl = storedAttachment.thumbnailUrl,
                fileSize = storedAttachment.fileSize,
                mimeType = storedAttachment.mimeType,
                sortOrder = index,
            )
        }

        if (postAttachments.isNotEmpty()) {
            postAttachmentRepository.saveAll(postAttachments)
        }

        return postAttachments.sortedBy { it.sortOrder }
    }

    private fun registerRollbackCleanup(storedAttachment: StoredPostAttachment) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        postAttachmentStorage.delete(storedAttachment)
                    }
                }
            }
        )
    }

    private fun publishExpensePostCreated(
        creationResult: TransactionCreationResult,
        post: Post,
    ) {
        val recipients = tripNotificationRecipientResolver.findActiveUserIds(
            tripId = creationResult.trip.id,
            actorUserId = creationResult.actor.id,
        ).map(::DefaultOutboxRecipientPayload)

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.POST,
            aggregateId = post.id,
            eventType = OutboxEventType.EXPENSE_POST_CREATED,
            payload = ExpensePostCreatedPayload(
                recipients = recipients,
                actorUserId = creationResult.actor.id,
                tripId = creationResult.trip.id,
                postId = post.id,
                transactionId = creationResult.transaction.id,
                tripName = creationResult.trip.title,
                actorDisplayName = creationResult.actor.nickname,
                postType = post.postType.name,
                title = post.title,
                amount = creationResult.transaction.amount,
                currency = creationResult.transaction.currency,
                baseAmount = creationResult.transaction.baseAmount,
                baseCurrency = creationResult.transaction.baseCurrency,
                occurredAt = java.time.Instant.now(),
            ),
        )
    }

    private fun publishPostCreated(post: Post) {
        val actorUserId = post.author.user?.id ?: return
        val candidateRecipientIds = tripNotificationRecipientResolver.findActiveUserIds(
            tripId = post.trip.id,
            actorUserId = actorUserId,
        )
        val recipients = moderationPolicy.filterNotifiableUserIds(actorUserId, candidateRecipientIds)
            .map(::DefaultOutboxRecipientPayload)

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.POST,
            aggregateId = post.id,
            eventType = OutboxEventType.POST_CREATED,
            payload = PostCreatedPayload(
                recipients = recipients,
                actorUserId = actorUserId,
                tripId = post.trip.id,
                postId = post.id,
                tripName = post.trip.title,
                actorDisplayName = post.author.user?.nickname ?: post.author.displayName,
                postType = post.postType.name,
                title = post.title,
                occurredAt = Instant.now(),
            ),
        )
    }

    private fun publishPostCommentCreated(comment: PostComment) {
        val actorUserId = comment.author.user?.id ?: return
        val recipientUserId = tripNotificationRecipientResolver.resolveSingleUserId(
            userId = comment.post.author.user?.id,
            actorUserId = actorUserId,
        )
        val recipients = moderationPolicy.filterNotifiableUserIds(actorUserId, listOfNotNull(recipientUserId))
            .map(::DefaultOutboxRecipientPayload)

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.POST,
            aggregateId = comment.post.id,
            eventType = OutboxEventType.POST_COMMENT_CREATED,
            payload = PostCommentCreatedPayload(
                recipients = recipients,
                actorUserId = actorUserId,
                tripId = comment.post.trip.id,
                postId = comment.post.id,
                commentId = comment.id,
                tripName = comment.post.trip.title,
                actorDisplayName = comment.author.user?.nickname ?: comment.author.displayName,
                postTitle = comment.post.title,
                occurredAt = Instant.now(),
            ),
        )
    }

    private fun parsePostType(postType: String): PostType {
        return try {
            PostType.valueOf(postType.uppercase())
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun parseCursor(cursor: String): PostCursor {
        return try {
            PostCursor.decode(cursor)
        } catch (exception: RuntimeException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun parseCommentCursor(cursor: String): PostCommentCursor {
        return try {
            PostCommentCursor.decode(cursor)
        } catch (exception: RuntimeException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_ATTACHMENT_COUNT = 10
    }
}
