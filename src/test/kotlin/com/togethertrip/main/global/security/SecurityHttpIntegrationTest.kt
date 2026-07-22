package com.togethertrip.main.global.security

import com.togethertrip.main.auth.service.RefreshTokenService
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.global.security.jwt.JwtTokenProvider
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertFalse

@MainIntegrationTest
@AutoConfigureMockMvc
class SecurityHttpIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) {

    private lateinit var fixture: Fixture

    @BeforeEach
    fun setUp() {
        fixture = createFixture()
    }

    @Test
    fun `인증 없이 정산 확정 요청 시 401 JSON 오류를 반환한다`() {
        mockMvc.perform(post("/api/trips/${fixture.tripId}/settlements"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun `잘못된 Bearer token으로 송금 확인 요청 시 401을 반환한다`() {
        mockMvc.perform(
            patch("/api/trips/${fixture.tripId}/settlement-transfers/1/sender-confirmation")
                .header("Authorization", "Bearer invalid-token")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun `활성 일반 참여자가 정산 확정을 요청하면 방장 권한 403을 반환한다`() {
        mockMvc.perform(
            post("/api/trips/${fixture.tripId}/settlements")
                .header("Authorization", bearer(fixture.member))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("TRIP_OWNER_ONLY"))
    }

    @Test
    fun `일반 USER가 admin API를 요청하면 security access denied 403을 반환한다`() {
        mockMvc.perform(
            get("/api/admin/exchange-rates/backfills")
                .header("Authorization", bearer(fixture.member))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `여행 비참여자가 정산 preview를 요청하면 참여자 404를 반환한다`() {
        mockMvc.perform(
            post("/api/trips/${fixture.tripId}/settlement-preview")
                .header("Authorization", bearer(fixture.outsider))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TRIP_PARTICIPANT_NOT_FOUND"))
    }

    @Test
    fun `빈 refresh token body는 공개 endpoint에서도 validation 400을 반환한다`() {
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"\"}")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `유효한 access token logout은 200을 반환하고 refresh token을 삭제한다`() {
        refreshTokenService.save(fixture.member.id, "stored-refresh-token")

        mockMvc.perform(
            post("/api/auth/logout")
                .header("Authorization", bearer(fixture.member))
        )
            .andExpect(status().isOk)

        assertFalse(refreshTokenService.matches(fixture.member.id, "stored-refresh-token"))
    }

    private fun createFixture(): Fixture {
        return requireNotNull(transactionTemplate.execute {
            val owner = persist(User(nickname = "HTTP 방장"))
            val member = persist(User(nickname = "HTTP 참여자"))
            val outsider = persist(User(nickname = "HTTP 비참여자"))
            val trip = persist(
                Trip(
                    ownerUser = owner,
                    title = "HTTP 권한 테스트 여행",
                    defaultCurrency = "KRW",
                )
            )
            persist(
                TripParticipant(
                    trip = trip,
                    user = owner,
                    displayName = "방장",
                    participantRole = TripParticipantRole.LEADER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            persist(
                TripParticipant(
                    trip = trip,
                    user = member,
                    displayName = "참여자",
                    participantRole = TripParticipantRole.MEMBER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                )
            )
            entityManager.flush()
            Fixture(owner, member, outsider, trip.id)
        })
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
        val outsider: User,
        val tripId: Long,
    )
}
