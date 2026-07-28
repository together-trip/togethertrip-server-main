package com.togethertrip.main.user.service

import com.togethertrip.main.auth.domain.OAuthAccount
import com.togethertrip.main.auth.domain.OAuthProvider
import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserAgreement
import com.togethertrip.main.user.domain.UserAgreementType
import com.togethertrip.main.user.domain.UserStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@MainIntegrationTest
@Transactional
class UserAccountDeletionIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val entityManager: EntityManager,
    private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `계정 삭제는 개인정보와 인증 연결을 제거하고 삭제된 참여자 스냅샷까지 익명화한다`() {
        val user = persist(
            User(
                nickname = "실명 사용자",
                gender = "FEMALE",
                birthDate = LocalDate.of(1995, 5, 1),
                profileImageUrl = "https://k.kakaocdn.net/profile.jpg",
            )
        )
        persist(
            OAuthAccount(
                user = user,
                provider = OAuthProvider.KAKAO,
                providerUserId = "deleted-kakao-user",
                nickname = "카카오 실명",
                profileImageUrl = "https://k.kakaocdn.net/profile.jpg",
            )
        )
        persist(
            UserAgreement(
                user = user,
                agreementType = UserAgreementType.SERVICE_TERMS,
                agreed = true,
                termVersion = "2026-07-01",
                agreedAt = Instant.parse("2026-07-01T00:00:00Z"),
            )
        )
        val trip = persist(
            Trip(
                ownerUser = user,
                title = "삭제 사용자 여행",
                defaultCurrency = "KRW",
            )
        )
        val participant = persist(
            TripParticipant(
                trip = trip,
                user = user,
                displayName = "참여자 실명",
                profileImageUrl = "https://k.kakaocdn.net/profile.jpg",
                participantRole = TripParticipantRole.LEADER,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        )
        participant.markDeleted(Instant.parse("2026-07-20T00:00:00Z"))
        entityManager.flush()

        userService.deleteMe(user.id)
        entityManager.flush()
        entityManager.clear()

        val userRow = jdbcTemplate.queryForMap(
            "select nickname, gender, birth_date, profile_image_url, status, deleted_at from users where id = ?",
            user.id,
        )
        assertEquals(User.WITHDRAWN_USER_NICKNAME, userRow["nickname"])
        assertNull(userRow["gender"])
        assertNull(userRow["birth_date"])
        assertNull(userRow["profile_image_url"])
        assertEquals(UserStatus.WITHDRAWN.name, userRow["status"])
        assertNotNull(userRow["deleted_at"])

        val participantRow = jdbcTemplate.queryForMap(
            "select display_name, profile_image_url, deleted_at from trip_participants where id = ?",
            participant.id,
        )
        assertEquals(User.WITHDRAWN_USER_NICKNAME, participantRow["display_name"])
        assertNull(participantRow["profile_image_url"])
        assertNotNull(participantRow["deleted_at"])

        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "select count(*) from oauth_accounts where user_id = ?",
                Int::class.java,
                user.id,
            ),
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "select count(*) from user_agreements where user_id = ?",
                Int::class.java,
                user.id,
            ),
        )

        val outboxRow = jdbcTemplate.queryForMap(
            "select aggregate_type, aggregate_id, event_type, payload from outbox_events where aggregate_id = ? and event_type = ?",
            user.id,
            "USER_ACCOUNT_DELETED",
        )
        assertEquals("USER", outboxRow["aggregate_type"])
        assertEquals(user.id, (outboxRow["aggregate_id"] as Number).toLong())
        assertEquals("USER_ACCOUNT_DELETED", outboxRow["event_type"])
        val payload = outboxRow["payload"].toString()
        assertEquals(true, payload.contains("\"eventVersion\": 1") || payload.contains("\"eventVersion\":1"))
        assertEquals(true, payload.contains("\"userId\": ${user.id}") || payload.contains("\"userId\":${user.id}"))
        assertEquals(false, payload.contains("실명"))
    }

    private fun <T : Any> persist(entity: T): T {
        entityManager.persist(entity)
        return entity
    }
}
