package com.togethertrip.main.post.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.service.ModerationPolicy
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.dto.request.CreateExpensePostRequest
import com.togethertrip.main.post.dto.request.CreatePostCommentRequest
import com.togethertrip.main.post.dto.request.CreatePostRequest
import com.togethertrip.main.post.dto.request.UpdateExpensePostRequest
import com.togethertrip.main.post.dto.request.UpdatePostRequest
import com.togethertrip.main.post.repository.PostAttachmentRepository
import com.togethertrip.main.post.repository.PostCommentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.post.service.storage.PostAttachmentStorage
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.service.TransactionService
import com.togethertrip.main.transaction.service.support.TransactionCreationService
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.service.support.TripNotificationRecipientResolver
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigDecimal
import kotlin.test.assertEquals

class PostServiceModerationGuardTest {
    private val rejectingPolicy = object : ModerationPolicy {
        override fun validateUserCanWrite(userId: Long) = Unit
        override fun validateContent(vararg values: String?) {
            throw BusinessException(ModerationErrorCode.CONTENT_REJECTED)
        }
        override fun validateCommentInteraction(userId: Long, post: Post) = Unit
        override fun canViewPost(viewerUserId: Long, post: Post) = true
        override fun canViewComment(viewerUserId: Long, comment: PostComment) = true
        override fun canNotify(actorUserId: Long, recipientUserId: Long) = true
        override fun filterNotifiableUserIds(actorUserId: Long, recipientUserIds: Collection<Long>) = recipientUserIds.toList()
    }
    private val service = PostService(
        mock(PostRepository::class.java),
        mock(PostAttachmentRepository::class.java),
        mock(PostCommentRepository::class.java),
        mock(TripParticipantRepository::class.java),
        mock(TransactionRepository::class.java),
        mock(PostAttachmentStorage::class.java),
        mock(TransactionCreationService::class.java),
        mock(TransactionService::class.java),
        mock(OutboxEventPublisher::class.java),
        mock(TripNotificationRecipientResolver::class.java),
        rejectingPolicy,
    )

    @Test
    fun `create update expense comment 모든 쓰기 경로가 공통 게시 전 필터를 사용한다`() {
        val failures = listOf<() -> Unit>(
            { service.createPost(1, 10, CreatePostRequest(content = "차단")) },
            { service.updatePost(1, 10, 20, UpdatePostRequest(content = "차단")) },
            { service.createExpensePost(1, 10, CreateExpensePostRequest(content = "차단")) },
            { service.updateExpensePost(1, 10, 20, UpdateExpensePostRequest(
                content = "차단", amount = BigDecimal.ONE, currency = "KRW",
                payments = emptyList(), shares = emptyList(),
            )) },
            { service.createComment(1, 10, 20, CreatePostCommentRequest("차단")) },
        )
        failures.forEach { call ->
            val exception = try { call(); error("BusinessException expected") } catch (e: BusinessException) { e }
            assertEquals(ModerationErrorCode.CONTENT_REJECTED, exception.errorCode)
        }
    }
}
