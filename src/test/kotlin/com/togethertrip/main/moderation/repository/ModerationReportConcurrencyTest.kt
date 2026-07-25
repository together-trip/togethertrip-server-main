package com.togethertrip.main.moderation.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import com.togethertrip.main.moderation.service.UserBlockService

@MainIntegrationTest
class ModerationReportConcurrencyTest @Autowired constructor(
    private val jdbcTemplate: JdbcTemplate,
    private val userBlockService: UserBlockService,
) {
    @Test
    fun `동일 차단 upsert 16건은 aborted transaction 없이 하나로 수렴한다`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val blockerId = insertUser("blocker-$suffix")
        val blockedId = insertUser("blocked-$suffix")
        val tripId = jdbcTemplate.queryForObject(
            "insert into trips(owner_user_id, title, default_currency) values (?, ?, 'KRW') returning id",
            Long::class.java, blockerId, "block-trip-$suffix",
        ) ?: error("trip id required")
        jdbcTemplate.update(
            """insert into trip_participants(trip_id, user_id, display_name, participant_role, participant_status)
                values (?, ?, 'blocker', 'LEADER', 'ACTIVE'), (?, ?, 'blocked', 'MEMBER', 'ACTIVE')""",
            tripId, blockerId, tripId, blockedId,
        )
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..16).map {
                CompletableFuture.runAsync({
                    assertEquals(blockedId, userBlockService.block(blockerId, blockedId).blockedUserId)
                }, executor)
            }
            CompletableFuture.allOf(*futures.toTypedArray()).get(10, TimeUnit.SECONDS)
            assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from user_blocks where blocker_user_id = ? and blocked_user_id = ? and deleted_at is null",
                Int::class.java, blockerId, blockedId,
            ))
        } finally {
            executor.shutdownNow()
            jdbcTemplate.update("delete from user_blocks where blocker_user_id = ?", blockerId)
            jdbcTemplate.update("delete from trip_participants where trip_id = ?", tripId)
            jdbcTemplate.update("delete from trips where id = ?", tripId)
            jdbcTemplate.update("delete from users where id in (?, ?)", blockerId, blockedId)
        }
    }

    @Test
    fun `신고 감사 이력은 PostgreSQL에서 수정과 삭제를 모두 거부한다`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val reporterId = insertUser("audit-reporter-$suffix")
        val targetId = insertUser("audit-target-$suffix")
        val adminId = insertUser("audit-admin-$suffix")
        val tripId = jdbcTemplate.queryForObject(
            "insert into trips(owner_user_id, title, default_currency) values (?, ?, 'KRW') returning id",
            Long::class.java, reporterId, "audit-trip-$suffix",
        ) ?: error("trip id required")
        val reportId = jdbcTemplate.queryForObject(
            """
            insert into moderation_reports(
              trip_id, reporter_user_id, target_type, target_id, target_user_id, reason, status
            ) values (?, ?, 'USER', ?, ?, 'SPAM', 'IN_REVIEW') returning id
            """.trimIndent(),
            Long::class.java, tripId, reporterId, targetId, targetId,
        ) ?: error("report id required")
        val auditId = jdbcTemplate.queryForObject(
            """
            insert into moderation_report_audits(
              report_id, actor_user_id, action, previous_status, next_status, note
            ) values (?, ?, 'NONE', 'PENDING', 'IN_REVIEW', 'audit') returning id
            """.trimIndent(),
            Long::class.java, reportId, adminId,
        ) ?: error("audit id required")

        kotlin.test.assertFailsWith<DataAccessException> {
            jdbcTemplate.update("update moderation_report_audits set note = 'tampered' where id = ?", auditId)
        }
        kotlin.test.assertFailsWith<DataAccessException> {
            jdbcTemplate.update("delete from moderation_report_audits where id = ?", auditId)
        }
        assertEquals("audit", jdbcTemplate.queryForObject(
            "select note from moderation_report_audits where id = ?", String::class.java, auditId
        ))
    }

    @Test
    fun `동일 신고가 동시에 생성되어도 active 신고는 하나만 저장된다`() {
        val suffix = UUID.randomUUID().toString().take(8)
        val reporterId = insertUser("reporter-$suffix")
        val targetId = insertUser("target-$suffix")
        val tripId = jdbcTemplate.queryForObject(
            "insert into trips(owner_user_id, title, default_currency) values (?, ?, 'KRW') returning id",
            Long::class.java, reporterId, "trip-$suffix",
        ) ?: error("trip id required")
        val successes = AtomicInteger()
        val executor = Executors.newFixedThreadPool(8)

        try {
            val futures = (1..16).map {
                CompletableFuture.runAsync({
                    try {
                        jdbcTemplate.update(
                            """
                            insert into moderation_reports(
                              trip_id, reporter_user_id, target_type, target_id,
                              target_user_id, reason, status
                            ) values (?, ?, 'USER', ?, ?, 'SPAM', 'PENDING')
                            """.trimIndent(),
                            tripId, reporterId, targetId, targetId,
                        )
                        successes.incrementAndGet()
                    } catch (_: DataIntegrityViolationException) {
                        Unit
                    }
                }, executor)
            }
            CompletableFuture.allOf(*futures.toTypedArray()).get(10, TimeUnit.SECONDS)
            assertEquals(1, successes.get())
            assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from moderation_reports where trip_id = ? and reporter_user_id = ?",
                Int::class.java, tripId, reporterId,
            ))
        } finally {
            executor.shutdownNow()
            jdbcTemplate.update("delete from moderation_reports where trip_id = ?", tripId)
            jdbcTemplate.update("delete from trips where id = ?", tripId)
            jdbcTemplate.update("delete from users where id in (?, ?)", reporterId, targetId)
        }
    }

    private fun insertUser(nickname: String): Long {
        return jdbcTemplate.queryForObject(
            "insert into users(nickname) values (?) returning id",
            Long::class.java, nickname,
        ) ?: error("user id required")
    }
}
