package com.togethertrip.main.global.outbox.repository

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.domain.OutboxStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class OutboxEventRepositoryTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val outboxEventRepository: OutboxEventRepository,
) {

    @Test
    fun `payload는 PostgreSQL jsonb object로 저장된다`() {
        val event = outboxEventRepository.save(
            OutboxEvent(
                aggregateType = "TRIP",
                aggregateId = 20L,
                eventType = "TRIP_PARTICIPANTS_ADDED",
                payload = """
                    {
                      "eventVersion": 1,
                      "recipients": [
                        { "userId": 11 }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        entityManager.flush()

        val result = entityManager.createNativeQuery(
            """
                select jsonb_typeof(payload), payload -> 'recipients' -> 0 ->> 'userId'
                from outbox_events
                where id = :id
            """.trimIndent(),
        )
            .setParameter("id", event.id)
            .singleResult as Array<*>

        assertEquals("object", result[0])
        assertEquals("11", result[1])
        assertEquals(OutboxStatus.PENDING, event.status)
    }
}
