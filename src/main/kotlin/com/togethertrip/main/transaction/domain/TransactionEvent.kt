package com.togethertrip.main.transaction.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 거래 변경 이벤트 이력.
 * created_at / updated_at / deleted_at(soft delete) 은 BaseEntity 에서 제공한다.
 */
@Entity
@Table(
    name = "transaction_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_transaction_events_version",
            columnNames = ["transaction_id", "aggregate_version"],
        ),
    ],
)
class TransactionEvent(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    var transaction: Transaction,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    var eventType: TransactionEventType,

    @Column(name = "aggregate_version", nullable = false)
    var aggregateVersion: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User,

) : BaseEntity()
