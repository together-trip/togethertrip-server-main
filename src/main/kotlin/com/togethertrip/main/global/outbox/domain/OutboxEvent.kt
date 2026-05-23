package com.togethertrip.main.global.outbox.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * DB 커밋 이후 비동기 후처리를 위한 Outbox 이벤트 (Transactional Outbox 패턴).
 * created_at / updated_at / deleted_at 은 BaseEntity 에서 제공한다.
 */
@Entity
@Table(name = "outbox_events")
class OutboxEvent(

    @Column(name = "aggregate_type", nullable = false, length = 50)
    var aggregateType: String,

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long,

    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

) : BaseEntity()
