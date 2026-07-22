package com.togethertrip.main.global.outbox.repository

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {
    @Query(
        value = """
        select *
        from outbox_events
        where status = 'PENDING'
           or (status = 'FAILED' and retry_count < :maxAttempts)
        order by created_at asc, id asc
        limit :limit
        for update skip locked
        """,
        nativeQuery = true,
    )
    fun findPendingForDispatch(
        @Param("limit") limit: Int,
        @Param("maxAttempts") maxAttempts: Int,
    ): List<OutboxEvent>
}
