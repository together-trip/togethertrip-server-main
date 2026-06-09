package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
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
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile

@Service
class PostService(
    private val postRepository: PostRepository,
    private val postAttachmentRepository: PostAttachmentRepository,
    private val postCommentRepository: PostCommentRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val transactionRepository: TransactionRepository,
    private val postAttachmentStorage: PostAttachmentStorage,
) {

    @Transactional
    fun createPost(
        userId: Long,
        tripId: Long,
        request: CreatePostRequest,
    ): PostDetailResponse {
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

        postRepository.save(post)

        val attachments = saveAttachments(
            post = post,
            files = request.files,
        )

        return PostDetailResponse.from(
            post = post,
            attachments = attachments,
        )
    }

    @Transactional(readOnly = true)
    fun getPosts(
        tripId: Long,
        postType: String?,
        cursor: String?,
        size: Int?,
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

        return CursorResponse(
            items = responseItems.map { post ->
                PostSummaryResponse.from(
                    post = post,
                    attachments = attachmentsByPostId[post.id] ?: emptyList(),
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
    ): List<Post> {
        return when {
            postType != null && cursor != null -> postRepository.findPostsByTypeAndCursor(
                tripId = tripId,
                postType = postType,
                cursorCreatedAt = cursor.createdAt,
                cursorId = cursor.id,
                pageable = pageable,
            )

            postType != null -> postRepository.findPostsByType(
                tripId = tripId,
                postType = postType,
                pageable = pageable,
            )

            cursor != null -> postRepository.findPostsByCursor(
                tripId = tripId,
                cursorCreatedAt = cursor.createdAt,
                cursorId = cursor.id,
                pageable = pageable,
            )

            else -> postRepository.findPosts(
                tripId = tripId,
                pageable = pageable,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getPost(
        tripId: Long,
        postId: Long,
    ): PostDetailResponse {
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        val attachments = postAttachmentRepository
            .findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(postId)

        return PostDetailResponse.from(
            post = post,
            attachments = attachments,
        )
    }

    @Transactional
    fun updatePost(
        userId: Long,
        tripId: Long,
        postId: Long,
        request: UpdatePostRequest,
    ): PostDetailResponse {
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        validateAuthor(
            author = post.author,
            userId = userId,
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
        val author = getParticipant(
            userId = userId,
            tripId = tripId,
        )
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        val comment = PostComment(
            post = post,
            author = author,
            content = request.content,
        )

        post.increaseCommentCount()
        postCommentRepository.save(comment)

        return PostCommentResponse.from(comment)
    }

    @Transactional(readOnly = true)
    fun getComments(
        tripId: Long,
        postId: Long,
        cursor: String?,
        size: Int?,
    ): CursorResponse<PostCommentResponse> {
        getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        val requestedSize = size?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
        val pageable = PageRequest.of(0, requestedSize + 1)
        val parsedCursor = cursor?.let(::parseCommentCursor)
        val comments = if (parsedCursor == null) {
            postCommentRepository.findRootComments(
                postId = postId,
                pageable = pageable,
            )
        } else {
            postCommentRepository.findRootCommentsByCursor(
                postId = postId,
                cursorCreatedAt = parsedCursor.createdAt,
                cursorId = parsedCursor.id,
                pageable = pageable,
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

    private fun findAttachmentsByPostId(posts: List<Post>): Map<Long, List<PostAttachment>> {
        if (posts.isEmpty()) {
            return emptyMap()
        }

        return postAttachmentRepository
            .findByPostIdInAndDeletedAtIsNullOrderByPostIdAscSortOrderAsc(posts.map { it.id })
            .groupBy { it.post.id }
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
        if (files.size > MAX_ATTACHMENT_COUNT) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }

        val postAttachments = files.mapIndexed { index, file ->
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
