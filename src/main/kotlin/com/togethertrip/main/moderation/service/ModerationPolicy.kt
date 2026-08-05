package com.togethertrip.main.moderation.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.moderation.exception.ModerationErrorCode
import com.togethertrip.main.moderation.repository.UserBlockRepository
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

interface ModerationPolicy {
    fun validateUserCanWrite(userId: Long)
    fun validateContent(vararg values: String?)
    fun validateCommentInteraction(userId: Long, post: Post)
    fun canViewPost(viewerUserId: Long, post: Post): Boolean
    fun canViewComment(viewerUserId: Long, comment: PostComment): Boolean
    fun canNotify(actorUserId: Long, recipientUserId: Long): Boolean
    fun filterNotifiableUserIds(actorUserId: Long, recipientUserIds: Collection<Long>): List<Long>

    companion object {
        val NOOP = object : ModerationPolicy {
            override fun validateUserCanWrite(userId: Long) = Unit
            override fun validateContent(vararg values: String?) = Unit
            override fun validateCommentInteraction(userId: Long, post: Post) = Unit
            override fun canViewPost(viewerUserId: Long, post: Post) = true
            override fun canViewComment(viewerUserId: Long, comment: PostComment) = true
            override fun canNotify(actorUserId: Long, recipientUserId: Long) = true
            override fun filterNotifiableUserIds(actorUserId: Long, recipientUserIds: Collection<Long>) = recipientUserIds.toList()
        }
    }
}

@Service
class DefaultModerationPolicy(
    private val userRepository: UserRepository,
    private val userBlockRepository: UserBlockRepository,
    private val contentModerationFilter: ContentModerationFilter,
) : ModerationPolicy {
    override fun validateUserCanWrite(userId: Long) {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(ModerationErrorCode.TARGET_NOT_FOUND)
        if (user.isModerationRestricted(Instant.now())) {
            throw BusinessException(ModerationErrorCode.USER_RESTRICTED)
        }
    }

    override fun validateContent(vararg values: String?) = contentModerationFilter.validate(*values)

    override fun validateCommentInteraction(userId: Long, post: Post) {
        val authorUserId = post.author.user?.id ?: return
        if (userBlockRepository.existsBidirectionalBlock(userId, authorUserId)) {
            throw BusinessException(ModerationErrorCode.INTERACTION_BLOCKED)
        }
    }

    override fun canViewPost(viewerUserId: Long, post: Post): Boolean {
        if (!post.isVisibleByModeration()) return false
        if (post.postType == PostType.EXPENSE) return true
        val authorUserId = post.author.user?.id ?: return true
        return !userBlockRepository.existsBidirectionalBlock(viewerUserId, authorUserId)
    }

    override fun canViewComment(viewerUserId: Long, comment: PostComment): Boolean {
        if (!comment.isVisibleByModeration()) return false
        val authorUserId = comment.author.user?.id ?: return true
        return !userBlockRepository.existsBidirectionalBlock(viewerUserId, authorUserId)
    }

    override fun canNotify(actorUserId: Long, recipientUserId: Long): Boolean {
        return !userBlockRepository.existsBidirectionalBlock(actorUserId, recipientUserId)
    }

    override fun filterNotifiableUserIds(actorUserId: Long, recipientUserIds: Collection<Long>): List<Long> {
        if (recipientUserIds.isEmpty()) return emptyList()
        return userBlockRepository.findNotBlockedRecipientUserIds(actorUserId, recipientUserIds)
    }
}
