package com.togethertrip.main.transaction.repository

import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Pageable

class TransactionQueryRepositoryImpl(
    private val entityManager: EntityManager,
    private val jpqlRenderContext: JpqlRenderContext,
) : TransactionQueryRepository {
    override fun findTransactions(
        condition: TransactionSearchCondition,
        pageable: Pageable,
    ): List<Transaction> {
        val query = jpql {
            val transaction = entity(Transaction::class)
            select(transaction)
                .from(transaction)
                .whereAnd(
                    transaction(BaseEntity::deletedAt).isNull(),
                    transaction(Transaction::trip)(Trip::id).eq(condition.tripId),
                    transaction(Transaction::status).eq(condition.status),
                    condition.transactionType?.let { transaction(Transaction::transactionType).eq(it) },
                    condition.cursorCreatedAt?.let { cursorCreatedAt ->
                        transaction(BaseEntity::createdAt).le(cursorCreatedAt)
                            .and(
                                transaction(BaseEntity::createdAt).lt(cursorCreatedAt)
                                    .or(
                                        transaction(BaseEntity::createdAt).eq(cursorCreatedAt)
                                            .and(transaction(BaseEntity::id).lt(requireNotNull(condition.cursorId)))
                                    )
                            )
                    },
                    condition.participantId?.let { participantId ->
                        exists(
                            select(longLiteral(1))
                                .from(entity(TransactionPayment::class))
                                .whereAnd(
                                    path(TransactionPayment::transaction).eq(transaction),
                                    path(TransactionPayment::deletedAt).isNull(),
                                    path(TransactionPayment::tripParticipant)(TripParticipant::id).eq(participantId),
                                )
                                .asSubquery()
                        ).or(
                            exists(
                                select(longLiteral(1))
                                    .from(entity(TransactionShare::class))
                                    .whereAnd(
                                        path(TransactionShare::transaction).eq(transaction),
                                        path(TransactionShare::deletedAt).isNull(),
                                        path(TransactionShare::tripParticipant)(TripParticipant::id).eq(participantId),
                                    )
                                    .asSubquery()
                            )
                        )
                    },
                )
                .orderBy(
                    transaction(BaseEntity::createdAt).desc(),
                    transaction(BaseEntity::id).desc(),
                )
        }

        return entityManager.createQuery(query, jpqlRenderContext)
            .setFirstResult(pageable.offset.toInt())
            .setMaxResults(pageable.pageSize)
            .resultList
    }
}
