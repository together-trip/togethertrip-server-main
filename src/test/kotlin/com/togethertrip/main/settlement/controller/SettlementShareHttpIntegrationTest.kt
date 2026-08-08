package com.togethertrip.main.settlement.controller

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotBalance
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotPayload
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotEquals

@MainIntegrationTest
@AutoConfigureMockMvc
class SettlementShareHttpIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtTokenProvider: JwtTokenProvider,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
) {

    private lateinit var fixture: Fixture

    @BeforeEach
    fun setUp() {
        fixture = createFixture()
    }

    @Test
    fun `인증 없이 공유 토큰으로 확정 정산을 조회한다`() {
        mockMvc.perform(get("/api/settlement-shares").param("token", fixture.initialShareToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.data.totalExpenseAmount").value(10000.00))
            .andExpect(jsonPath("$.data.balances[0].displayName").value("방장"))
    }

    @Test
    fun `공유 조회 응답은 캐시되지 않는다`() {
        mockMvc.perform(get("/api/settlement-shares").param("token", fixture.initialShareToken))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
    }

    @Test
    fun `공유 응답에는 사용자 식별자가 포함되지 않는다`() {
        mockMvc.perform(get("/api/settlement-shares").param("token", fixture.initialShareToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.balances[0].userId").doesNotExist())
            .andExpect(jsonPath("$.data.balances[0].participantId").doesNotExist())
    }

    @Test
    fun `존재하지 않는 공유 토큰은 404를 반환한다`() {
        mockMvc.perform(get("/api/settlement-shares").param("token", "unknown-token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SETTLEMENT_SHARE_TOKEN_NOT_FOUND"))
    }

    @Test
    fun `방장이 공유 토큰을 회전하면 이전 링크는 무효가 되고 새 링크로 조회된다`() {
        val response = mockMvc.perform(
            post("/api/trips/${fixture.tripId}/settlements/${fixture.settlementId}/share-tokens/rotation")
                .header("Authorization", bearer(fixture.owner))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlementId").value(fixture.settlementId))
            .andReturn()

        val body = objectMapper.readValue(response.response.contentAsString, Map::class.java)
        val rotatedToken = (body["data"] as Map<*, *>)["shareToken"] as String
        assertNotEquals(fixture.initialShareToken, rotatedToken)

        mockMvc.perform(get("/api/settlement-shares").param("token", fixture.initialShareToken))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("SETTLEMENT_SHARE_TOKEN_NOT_FOUND"))

        mockMvc.perform(get("/api/settlement-shares").param("token", rotatedToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
    }

    @Test
    fun `일반 참여자는 공유 토큰을 회전할 수 없다`() {
        mockMvc.perform(
            post("/api/trips/${fixture.tripId}/settlements/${fixture.settlementId}/share-tokens/rotation")
                .header("Authorization", bearer(fixture.member))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("TRIP_OWNER_ONLY"))
    }

    @Test
    fun `인증 없이 공유 토큰을 회전할 수 없다`() {
        mockMvc.perform(
            post("/api/trips/${fixture.tripId}/settlements/${fixture.settlementId}/share-tokens/rotation")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
    }

    private fun createFixture(): Fixture {
        // 테스트 메서드마다 fixture를 커밋하므로 uk_settlements_share_token 충돌을 피한다.
        val initialShareToken = "http-share-token-${UUID.randomUUID()}"
        return requireNotNull(transactionTemplate.execute {
            val ownerUser = persist(User(nickname = "공유 방장"))
            val memberUser = persist(User(nickname = "공유 참여자"))
            val trip = persist(
                Trip(
                    ownerUser = ownerUser,
                    title = "정산 공유 HTTP 테스트 여행",
                    defaultCurrency = "KRW",
                )
            )
            val owner = persist(
                TripParticipant(
                    trip = trip,
                    user = ownerUser,
                    displayName = "방장",
                    participantRole = TripParticipantRole.LEADER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            val member = persist(
                TripParticipant(
                    trip = trip,
                    user = memberUser,
                    displayName = "참여자",
                    participantRole = TripParticipantRole.MEMBER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            entityManager.flush()

            val snapshot = SettlementSnapshotPayload(
                balances = listOf(
                    snapshot(owner, ownerUser, paid = "10000.00", share = "5000.00", net = "5000.00"),
                    snapshot(member, memberUser, paid = "0.00", share = "5000.00", net = "-5000.00"),
                )
            )
            val settlement = persist(
                Settlement(
                    trip = trip,
                    status = SettlementStatus.CONFIRMED,
                    tripExpenseVersion = 1,
                    calculationVersion = "settlement-v1",
                    baseCurrency = "KRW",
                    totalExpenseAmount = BigDecimal("10000.00"),
                    totalShareAmount = BigDecimal("10000.00"),
                    snapshotPayload = objectMapper.writeValueAsString(snapshot),
                    confirmedAt = Instant.parse("2026-07-10T00:00:00Z"),
                    confirmedBy = ownerUser,
                    shareToken = initialShareToken,
                )
            )
            entityManager.flush()

            Fixture(
                owner = ownerUser,
                member = memberUser,
                tripId = trip.id,
                settlementId = settlement.id,
                initialShareToken = initialShareToken,
            )
        })
    }

    private fun snapshot(
        participant: TripParticipant,
        user: User,
        paid: String,
        share: String,
        net: String,
    ): SettlementSnapshotBalance {
        return SettlementSnapshotBalance(
            participantId = participant.id,
            userId = user.id,
            displayName = participant.displayName,
            profileImageUrl = null,
            participantStatus = TripParticipantStatus.ACTIVE,
            paidAmount = BigDecimal(paid),
            shareAmount = BigDecimal(share),
            netAmount = BigDecimal(net),
        )
    }

    private fun bearer(user: User): String {
        return "Bearer ${jwtTokenProvider.createAccessToken(user.id, user.role)}"
    }

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }

    private data class Fixture(
        val owner: User,
        val member: User,
        val tripId: Long,
        val settlementId: Long,
        val initialShareToken: String,
    )
}
