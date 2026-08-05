package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.exception.PostErrorCode
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.service.TransactionService
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.moderation.service.ModerationPolicy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostDeleteService(
    private val postRepository: PostRepository,
    private val transactionService: TransactionService,
    private val moderationPolicy: ModerationPolicy = ModerationPolicy.NOOP,
) {

    @Transactional
    fun deletePost(
        userId: Long,
        tripId: Long,
        postId: Long,
    ) {
        moderationPolicy.validateUserCanWrite(userId)
        val post = getPostOrThrow(
            tripId = tripId,
            postId = postId,
        )
        validateAuthor(
            author = post.author,
            userId = userId,
        )

        val transactionId = post.transaction?.id
        if (transactionId != null) {
            transactionService.deleteTransaction(
                userId = userId,
                tripId = tripId,
                transactionId = transactionId,
            )
        } else if (post.postType == PostType.EXPENSE) {
            throw BusinessException(PostErrorCode.TRANSACTION_NOT_FOUND)
        }

        post.markDeleted()
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

    private fun validateAuthor(
        author: TripParticipant,
        userId: Long,
    ) {
        if (author.user?.id != userId) {
            throw BusinessException(CommonErrorCode.ACCESS_DENIED)
        }
    }
}
