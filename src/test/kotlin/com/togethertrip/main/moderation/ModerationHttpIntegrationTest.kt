package com.togethertrip.main.moderation

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionType
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import kotlin.test.assertEquals

@MainIntegrationTest
@AutoConfigureMockMvc
class ModerationHttpIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtTokenProvider: JwtTokenProvider,
    private val postRepository: PostRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) {
    private lateinit var fixture: Fixture

    @BeforeEach
    fun setUp() {
        fixture = requireNotNull(transactionTemplate.execute {
            val owner = persist(User("moderation-owner-${System.nanoTime()}"))
            val viewer = persist(User("moderation-viewer-${System.nanoTime()}"))
            val author = persist(User("moderation-author-${System.nanoTime()}"))
            val trip = persist(Trip(owner, "moderation-trip", "KRW"))
            val ownerParticipant = persist(participant(trip, owner, TripParticipantRole.LEADER))
            val viewerParticipant = persist(participant(trip, viewer, TripParticipantRole.MEMBER))
            val authorParticipant = persist(participant(trip, author, TripParticipantRole.MEMBER))
            val record = persist(Post(trip = trip, author = authorParticipant, postType = PostType.RECORD, content = "record"))
            val expenseTransaction = persist(Transaction(
                trip = trip, createdBy = author, transactionType = TransactionType.EXPENSE,
                amount = BigDecimal.ONE, currency = "KRW", exchangeRate = BigDecimal.ONE,
                baseCurrency = "KRW", baseAmount = BigDecimal.ONE,
            ))
            val expense = persist(Post(
                trip = trip, transaction = expenseTransaction, author = authorParticipant,
                postType = PostType.EXPENSE, content = "expense",
            ))
            val attachment = persist(PostAttachment(
                post = record,
                attachmentType = PostAttachmentType.IMAGE,
                fileUrl = "/uploads/post-attachments/00000000-0000-0000-0000-000000000000.png",
                mimeType = "image/png",
            ))
            entityManager.flush()
            Fixture(owner, viewer, author, trip.id, ownerParticipant.id, viewerParticipant.id,
                authorParticipant.id, record.id, expense.id, attachment.id)
        })
    }

    @Test
    fun `Postgres 양방향 차단은 일반 기록만 숨기고 지출 기록은 유지한다`() {
        insertBlock(fixture.viewer.id, fixture.author.id)
        assertEquals(listOf(fixture.expensePostId), visiblePostIds())

        jdbcTemplate.update(
            "update user_blocks set deleted_at = now() where blocker_user_id = ? and blocked_user_id = ?",
            fixture.viewer.id, fixture.author.id,
        )
        insertBlock(fixture.author.id, fixture.viewer.id)
        assertEquals(listOf(fixture.expensePostId), visiblePostIds())
    }

    @Test
    fun `공개 static 첨부 URL은 차단하고 인증 경로도 게시글 노출 정책을 적용한다`() {
        insertBlock(fixture.viewer.id, fixture.author.id)
        mockMvc.perform(get("/uploads/post-attachments/00000000-0000-0000-0000-000000000000.png"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            get("/api/trips/${fixture.tripId}/posts/${fixture.recordPostId}/attachments/${fixture.attachmentId}")
                .header("Authorization", bearer(fixture.viewer))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
    }

    @Test
    fun `운영 제한 사용자는 기존 JWT로 읽을 수 있지만 UGC 쓰기는 403이다`() {
        jdbcTemplate.update(
            "update users set moderation_restricted_at = now(), moderation_restriction_reason = 'policy' where id = ?",
            fixture.viewer.id,
        )
        entityManager.clear()

        mockMvc.perform(
            get("/api/users/me/blocks").header("Authorization", bearer(fixture.viewer))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/trips/${fixture.tripId}/posts/${fixture.recordPostId}/comments")
                .header("Authorization", bearer(fixture.viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"댓글\"}")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("USER_RESTRICTED"))
    }

    @Test
    fun `차단과 일반 기록 숨김은 payment share settlement snapshot을 변경하지 않는다`() {
        val transactionId = jdbcTemplate.queryForObject(
            """
            insert into transactions(
              trip_id, created_by_user_id, transaction_type, amount, currency,
              exchange_rate, base_currency, base_amount, status
            ) values (?, ?, 'EXPENSE', 100, 'KRW', 1, 'KRW', 100, 'ACTIVE') returning id
            """.trimIndent(),
            Long::class.java, fixture.tripId, fixture.owner.id,
        ) ?: error("transaction id")
        jdbcTemplate.update(
            """insert into transaction_payments(transaction_id, trip_participant_id, amount, currency,
                exchange_rate, base_currency, base_amount) values (?, ?, 100, 'KRW', 1, 'KRW', 100)""",
            transactionId, fixture.ownerParticipantId,
        )
        jdbcTemplate.update(
            """insert into transaction_shares(transaction_id, trip_participant_id, share_amount, currency,
                exchange_rate, base_currency, base_share_amount) values (?, ?, 100, 'KRW', 1, 'KRW', 100)""",
            transactionId, fixture.viewerParticipantId,
        )
        jdbcTemplate.update(
            """insert into settlements(trip_id, status, trip_expense_version, calculation_version,
                base_currency, total_expense_amount, total_share_amount, snapshot_payload)
                values (?, 'CONFIRMED', 1, 'v1', 'KRW', 100, 100, '{"locked":true}'::jsonb)""",
            fixture.tripId,
        )
        val before = ledgerSnapshot(transactionId)

        insertBlock(fixture.viewer.id, fixture.author.id)
        jdbcTemplate.update("update posts set moderation_hidden_at = now() where id = ?", fixture.recordPostId)

        assertEquals(before, ledgerSnapshot(transactionId))
    }

    private fun ledgerSnapshot(transactionId: Long): Map<String, Any?> {
        return jdbcTemplate.queryForMap(
            """
            select t.amount as transaction_amount,
              (select sum(p.base_amount) from transaction_payments p where p.transaction_id = t.id) as payment_amount,
              (select sum(s.base_share_amount) from transaction_shares s where s.transaction_id = t.id) as share_amount,
              (select snapshot_payload::text from settlements st where st.trip_id = t.trip_id) as snapshot
            from transactions t where t.id = ?
            """.trimIndent(),
            transactionId,
        )
    }

    private fun visiblePostIds(): List<Long> {
        entityManager.clear()
        return postRepository.findPosts(fixture.tripId, PageRequest.of(0, 20), fixture.viewer.id).map { it.id }
    }

    private fun insertBlock(blockerId: Long, blockedId: Long) {
        jdbcTemplate.update(
            """insert into user_blocks(blocker_user_id, blocked_user_id) values (?, ?)
                on conflict (blocker_user_id, blocked_user_id) where deleted_at is null do nothing""",
            blockerId, blockedId,
        )
    }

    private fun bearer(user: User): String = "Bearer ${jwtTokenProvider.createAccessToken(user.id, user.role)}"
    private fun participant(trip: Trip, user: User, role: TripParticipantRole) = TripParticipant(
        trip = trip, user = user, displayName = user.nickname, participantRole = role,
        participantStatus = TripParticipantStatus.ACTIVE,
    )
    private fun <T : Any> persist(entity: T): T { entityManager.persist(entity); return entity }

    private data class Fixture(
        val owner: User,
        val viewer: User,
        val author: User,
        val tripId: Long,
        val ownerParticipantId: Long,
        val viewerParticipantId: Long,
        val authorParticipantId: Long,
        val recordPostId: Long,
        val expensePostId: Long,
        val attachmentId: Long,
    )
}
