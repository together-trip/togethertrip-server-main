package com.togethertrip.main.trip.repository

import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Pageable

class TripQueryRepositoryImpl(
    private val entityManager: EntityManager,
    private val jpqlRenderContext: JpqlRenderContext,
) : TripQueryRepository {
    override fun findAccessibleTrips(
        condition: TripSearchCondition,
        pageable: Pageable,
    ): List<Trip> {
        val query = jpql {
            val trip = entity(Trip::class)
            select(trip)
                .from(trip)
                .whereAnd(
                    trip(BaseEntity::deletedAt).isNull(),
                    trip(Trip::ownerUser)(User::id).eq(condition.userId)
                        .or(
                            exists(
                                select(longLiteral(1))
                                    .from(entity(TripParticipant::class))
                                    .whereAnd(
                                        path(TripParticipant::trip).eq(trip),
                                        path(TripParticipant::deletedAt).isNull(),
                                        path(TripParticipant::user)(User::id).eq(condition.userId),
                                    )
                                    .asSubquery()
                            )
                        ),
                    condition.status?.let { trip(Trip::tripStatus).eq(it) },
                    condition.cursorCreatedAt?.let { cursorCreatedAt ->
                        trip(BaseEntity::createdAt).le(cursorCreatedAt)
                            .and(
                                trip(BaseEntity::createdAt).lt(cursorCreatedAt)
                                    .or(
                                        trip(BaseEntity::createdAt).eq(cursorCreatedAt)
                                            .and(trip(BaseEntity::id).lt(requireNotNull(condition.cursorId)))
                                    )
                            )
                    },
                )
                .orderBy(
                    trip(BaseEntity::createdAt).desc(),
                    trip(BaseEntity::id).desc(),
                )
        }

        return entityManager.createQuery(query, jpqlRenderContext)
            .setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)
            .resultList
    }
}
