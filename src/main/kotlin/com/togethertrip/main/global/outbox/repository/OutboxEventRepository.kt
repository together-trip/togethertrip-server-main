package com.togethertrip.main.global.outbox.repository

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    // idx_outbox_events_status_created_at 활용: 발행 대기 이벤트를 오래된 순으로 조회
    fun findByStatusOrderByCreatedAtAsc(status: OutboxStatus, pageable: Pageable): List<OutboxEvent>
}
