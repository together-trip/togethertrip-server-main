package com.togethertrip.main.post.repository

import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.moderation.domain.UserBlock
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostComment
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Pageable

class PostCommentQueryRepositoryImpl(
    private val entityManager: EntityManager,
    private val jpqlRenderContext: JpqlRenderContext,
) : PostCommentQueryRepository {
    override fun countVisibleRootComments(postId: Long, viewerUserId: Long?): Long {
        val query = jpql {
            val comment = entity(PostComment::class)
            select(count(comment))
                .from(comment)
                .whereAnd(
                    comment(PostComment::post)(Post::id).eq(postId),
                    comment(PostComment::parentComment).isNull(),
                    comment(BaseEntity::deletedAt).isNull(),
                    comment(PostComment::moderationHiddenAt).isNull(),
                    comment(PostComment::moderationDeletedAt).isNull(),
                    viewerUserId?.let { visibleToViewer(comment, it) },
                )
        }
        return entityManager.createQuery(query, jpqlRenderContext).singleResult
    }

    override fun findVisibleCommentCounts(
        postIds: Collection<Long>,
        viewerUserId: Long?,
    ): List<PostCommentCountProjection> {
        if (postIds.isEmpty()) return emptyList()
        val query = jpql {
            val comment = entity(PostComment::class)
            val postId = comment(PostComment::post)(Post::id)
            selectNew(
                PostCommentCountRow::class,
                postId,
                count(comment),
            )
                .from(comment)
                .whereAnd(
                    postId.`in`(postIds),
                    comment(PostComment::parentComment).isNull(),
                    comment(BaseEntity::deletedAt).isNull(),
                    comment(PostComment::moderationHiddenAt).isNull(),
                    comment(PostComment::moderationDeletedAt).isNull(),
                    viewerUserId?.let { visibleToViewer(comment, it) },
                )
                .groupBy(postId)
        }
        return entityManager.createQuery(query, jpqlRenderContext).resultList
    }

    override fun findRootComments(
        condition: PostCommentSearchCondition,
        pageable: Pageable,
    ): List<PostComment> {
        val query = jpql {
            val comment = entity(PostComment::class)
            select(comment)
                .from(comment)
                .whereAnd(
                    comment(PostComment::post)(Post::id).eq(condition.postId),
                    comment(PostComment::parentComment).isNull(),
                    comment(BaseEntity::deletedAt).isNull(),
                    comment(PostComment::moderationHiddenAt).isNull(),
                    comment(PostComment::moderationDeletedAt).isNull(),
                    condition.viewerUserId?.let { visibleToViewer(comment, it) },
                    condition.cursorCreatedAt?.let { cursorCreatedAt ->
                        comment(BaseEntity::createdAt).ge(cursorCreatedAt)
                            .and(
                                comment(BaseEntity::createdAt).gt(cursorCreatedAt)
                                    .or(
                                        comment(BaseEntity::createdAt).eq(cursorCreatedAt)
                                            .and(comment(BaseEntity::id).gt(requireNotNull(condition.cursorId)))
                                    )
                            )
                    },
                )
                .orderBy(
                    comment(BaseEntity::createdAt).asc(),
                    comment(BaseEntity::id).asc(),
                )
        }
        return entityManager.createQuery(query, jpqlRenderContext)
            .setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)
            .resultList
    }

    private fun com.linecorp.kotlinjdsl.dsl.jpql.Jpql.visibleToViewer(
        comment: com.linecorp.kotlinjdsl.querymodel.jpql.entity.Entity<PostComment>,
        viewerUserId: Long,
    ) = notExists(
        select(longLiteral(1))
            .from(entity(UserBlock::class))
            .whereAnd(
                path(UserBlock::deletedAt).isNull(),
                path(UserBlock::blocker)(User::id).eq(viewerUserId)
                    .and(
                        path(UserBlock::blocked)(User::id)
                            .eq(comment(PostComment::author)(TripParticipant::user)(User::id))
                    )
                    .or(
                        path(UserBlock::blocker)(User::id)
                            .eq(comment(PostComment::author)(TripParticipant::user)(User::id))
                            .and(path(UserBlock::blocked)(User::id).eq(viewerUserId))
                    ),
            )
            .asSubquery()
    )
}
