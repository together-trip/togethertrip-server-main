package com.togethertrip.main.post.repository

import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.moderation.domain.UserBlock
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Pageable

class PostQueryRepositoryImpl(
    private val entityManager: EntityManager,
    private val jpqlRenderContext: JpqlRenderContext,
) : PostQueryRepository {
    override fun findPosts(
        condition: PostSearchCondition,
        pageable: Pageable,
    ): List<Post> {
        val query = jpql {
            val post = entity(Post::class)
            val moderationVisibility = post(Post::moderationHiddenAt).isNull()
                .and(post(Post::moderationDeletedAt).isNull())
            val recordVisibility = condition.viewerUserId?.let { viewerUserId ->
                moderationVisibility.and(
                    notExists(
                        select(longLiteral(1))
                            .from(entity(UserBlock::class))
                            .whereAnd(
                                path(UserBlock::deletedAt).isNull(),
                                path(UserBlock::blocker)(User::id).eq(viewerUserId)
                                    .and(
                                        path(UserBlock::blocked)(User::id)
                                            .eq(post(Post::author)(TripParticipant::user)(User::id))
                                    )
                                    .or(
                                        path(UserBlock::blocker)(User::id)
                                            .eq(post(Post::author)(TripParticipant::user)(User::id))
                                            .and(path(UserBlock::blocked)(User::id).eq(viewerUserId))
                                    ),
                            )
                            .asSubquery()
                    )
                )
            } ?: moderationVisibility
            select(post)
                .from(post)
                .whereAnd(
                    post(Post::trip)(Trip::id).eq(condition.tripId),
                    post(BaseEntity::deletedAt).isNull(),
                    condition.postType?.let { post(Post::postType).eq(it) },
                    post(Post::postType).eq(PostType.EXPENSE)
                        .or(recordVisibility),
                    condition.cursorCreatedAt?.let { cursorCreatedAt ->
                        post(BaseEntity::createdAt).le(cursorCreatedAt)
                            .and(
                                post(BaseEntity::createdAt).lt(cursorCreatedAt)
                                    .or(
                                        post(BaseEntity::createdAt).eq(cursorCreatedAt)
                                            .and(post(BaseEntity::id).lt(requireNotNull(condition.cursorId)))
                                    )
                            )
                    },
                )
                .orderBy(
                    post(BaseEntity::createdAt).desc(),
                    post(BaseEntity::id).desc(),
                )
        }

        return entityManager.createQuery(query, jpqlRenderContext)
            .setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)
            .resultList
    }
}
