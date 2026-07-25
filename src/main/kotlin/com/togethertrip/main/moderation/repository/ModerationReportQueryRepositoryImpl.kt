package com.togethertrip.main.moderation.repository

import com.togethertrip.main.moderation.domain.ModerationReport
import com.togethertrip.main.moderation.domain.ModerationReportStatus
import com.togethertrip.main.moderation.domain.ModerationTargetType
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import java.time.Instant

class ModerationReportQueryRepositoryImpl(
    private val entityManager: EntityManager,
) : ModerationReportQueryRepository {
    override fun findReports(
        condition: ModerationReportSearchCondition,
        limit: Int,
    ): List<ModerationReport> {
        require(limit > 0) { "limit must be positive" }

        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(ModerationReport::class.java)
        val report = query.from(ModerationReport::class.java)
        val predicates = mutableListOf<Predicate>(
            criteriaBuilder.isNull(report.get<Instant?>("deletedAt"))
        )

        condition.status?.let {
            predicates += criteriaBuilder.equal(report.get<ModerationReportStatus>("status"), it)
        }
        condition.targetType?.let {
            predicates += criteriaBuilder.equal(report.get<ModerationTargetType>("targetType"), it)
        }
        condition.cursor?.let { cursor ->
            val createdAt = report.get<Instant>("createdAt")
            val id = report.get<Long>("id")
            predicates += criteriaBuilder.or(
                criteriaBuilder.greaterThan(createdAt, cursor.createdAt),
                criteriaBuilder.and(
                    criteriaBuilder.equal(createdAt, cursor.createdAt),
                    criteriaBuilder.greaterThan(id, cursor.id),
                ),
            )
        }

        val createdAt = report.get<Instant>("createdAt")
        val id = report.get<Long>("id")
        query.select(report)
            .where(*predicates.toTypedArray())
            .orderBy(criteriaBuilder.asc(createdAt), criteriaBuilder.asc(id))

        return entityManager.createQuery(query)
            .setMaxResults(limit)
            .resultList
    }
}
