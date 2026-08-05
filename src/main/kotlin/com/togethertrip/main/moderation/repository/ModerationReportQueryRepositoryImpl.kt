package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.ModerationReport
import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import jakarta.persistence.EntityManager

class ModerationReportQueryRepositoryImpl(
    private val entityManager: EntityManager,
    private val jpqlRenderContext: JpqlRenderContext,
) : ModerationReportQueryRepository {
    override fun findReports(
        condition: ModerationReportSearchCondition,
        limit: Int,
    ): List<ModerationReport> {
        require(limit > 0) { "limit must be positive" }

        val query = jpql {
            select(entity(ModerationReport::class))
                .from(entity(ModerationReport::class))
                .whereAnd(
                    path(ModerationReport::deletedAt).isNull(),
                    condition.status?.let { path(ModerationReport::status).eq(it) },
                    condition.targetType?.let { path(ModerationReport::targetType).eq(it) },
                    condition.cursor?.let { cursor ->
                        path(ModerationReport::createdAt).ge(cursor.createdAt)
                            .and(
                                path(ModerationReport::createdAt).gt(cursor.createdAt)
                                    .or(
                                        path(ModerationReport::createdAt).eq(cursor.createdAt)
                                            .and(path(ModerationReport::id).gt(cursor.id))
                                    )
                            )
                    },
                )
                .orderBy(
                    path(ModerationReport::createdAt).asc(),
                    path(ModerationReport::id).asc(),
                )
        }

        return entityManager.createQuery(query, jpqlRenderContext)
            .setMaxResults(limit)
            .resultList
    }
}
