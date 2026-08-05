package com.togethertrip.main.global.outbox.repository

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {
    @Query(
        value = """
        select event.*
        from outbox_events event
        where (
                event.status = 'PENDING'
                or (event.status = 'FAILED' and event.retry_count < :maxAttempts)
              )
          and not exists (
                select 1
                from outbox_events predecessor
                where predecessor.aggregate_type = event.aggregate_type
                  and predecessor.aggregate_id = event.aggregate_id
                  and predecessor.status <> 'PUBLISHED'
                  and (predecessor.created_at, predecessor.id) < (event.created_at, event.id)
              )
        order by event.created_at asc, event.id asc
        limit :limit
        for update of event skip locked
        """,
        nativeQuery = true,
    )
    fun findPendingForDispatch(
        @Param("limit") limit: Int,
        @Param("maxAttempts") maxAttempts: Int,
    ): List<OutboxEvent>
}
