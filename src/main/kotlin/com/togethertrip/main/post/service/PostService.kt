package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.response.PageResponse
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
import com.togethertrip.main.post.repository.PostAttachmentRepository
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostService(
    private val postRepository: PostRepository,
    private val postAttachmentRepository: PostAttachmentRepository,
    private val postCommentRepository: PostCommentRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val transactionRepository: TransactionRepository,
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

        val attachments = request.attachments.map { attachmentRequest ->
            PostAttachment(
                post = post,
                attachmentType = attachmentRequest.attachmentType,
                fileUrl = attachmentRequest.fileUrl,
                thumbnailUrl = attachmentRequest.thumbnailUrl,
                fileSize = attachmentRequest.fileSize,
                mimeType = attachmentRequest.mimeType,
                sortOrder = attachmentRequest.sortOrder,
            )
        }
        if (attachments.isNotEmpty()) {
            postAttachmentRepository.saveAll(attachments)
        }

        return PostDetailResponse.from(
            post = post,
            attachments = attachments,
        )
    }

    @Transactional(readOnly = true)
    fun getPosts(
        tripId: Long,
        postType: String?,
        page: Int?,
        size: Int?,
    ): PageResponse<PostSummaryResponse> {
        val pageable = PageRequest.of(
            page?.coerceAtLeast(0) ?: DEFAULT_PAGE,
            size?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE,
        )
        val parsedPostType = postType?.let(::parsePostType)
        val posts = if (parsedPostType == null) {
            postRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tripId = tripId,
                pageable = pageable,
            )
        } else {
            postRepository.findByTripIdAndPostTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
                tripId = tripId,
                postType = parsedPostType,
                pageable = pageable,
            )
        }

        return PageResponse.from(
            posts.map(PostSummaryResponse::from)
        )
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
        )

        val attachments = postAttachmentRepository
            .findByPostIdAndDeletedAtIsNullOrderBySortOrderAsc(postId)

        return PostDetailResponse.from(
            post = post,
            attachments = attachments,
        )
    }

    @Transactional
    fun deletePost(
        userId: Long,
        tripId: Long,
        postId: Long,
    ) {
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        validateAuthor(
            author = post.author,
            userId = userId,
        )

        post.markDeleted()
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

    fun getComments(
        tripId: Long,
        postId: Long,
    ): List<PostCommentResponse> {
        getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )

        return postCommentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId)
            .map(PostCommentResponse::from)
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
        val comment = postCommentRepository.findByIdAndPostIdAndDeletedAtIsNull(
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
        return tripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
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

    private fun parsePostType(postType: String): PostType {
        return try {
            PostType.valueOf(postType.uppercase())
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
