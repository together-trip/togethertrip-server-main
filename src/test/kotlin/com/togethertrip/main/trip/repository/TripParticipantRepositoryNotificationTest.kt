package com.togethertrip.main.trip.repository

import com.togethertrip.main.global.config.MainIntegrationTest
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals

@MainIntegrationTest
@Transactional
class TripParticipantRepositoryNotificationTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val tripParticipantRepository: TripParticipantRepository,
) {

    @Test
    fun `알림 대상 활성 회원 참여자 userId만 생성 순서대로 조회한다`() {
        val owner = persistUser("방장")
        val activeMember = persistUser("회원")
        val withdrawnMember = persistUser("탈퇴", UserStatus.WITHDRAWN).apply {
            markDeleted(Instant.parse("2026-06-22T00:00:00Z"))
        }
        val suspendedMember = persistUser("정지", UserStatus.SUSPENDED)
        val removedMember = persistUser("제거됨")
        val deletedParticipantUser = persistUser("삭제참여자")
        val trip = persistTrip(owner)

        persistParticipant(
            trip = trip,
            user = owner,
            idOrder = 1,
            createdAt = Instant.parse("2026-06-22T00:00:01Z"),
        )
        persistParticipant(
            trip = trip,
            user = null,
            displayName = "임시",
            idOrder = 2,
            createdAt = Instant.parse("2026-06-22T00:00:02Z"),
        )
        persistParticipant(
            trip = trip,
            user = activeMember,
            idOrder = 3,
            createdAt = Instant.parse("2026-06-22T00:00:03Z"),
        )
        persistParticipant(
            trip = trip,
            user = withdrawnMember,
            idOrder = 4,
            createdAt = Instant.parse("2026-06-22T00:00:04Z"),
        )
        persistParticipant(
            trip = trip,
            user = suspendedMember,
            idOrder = 5,
            createdAt = Instant.parse("2026-06-22T00:00:05Z"),
        )
        persistParticipant(
            trip = trip,
            user = removedMember,
            status = TripParticipantStatus.REMOVED,
            idOrder = 6,
            createdAt = Instant.parse("2026-06-22T00:00:06Z"),
        )
        persistParticipant(
            trip = trip,
            user = deletedParticipantUser,
            idOrder = 7,
            createdAt = Instant.parse("2026-06-22T00:00:07Z"),
        ).markDeleted(Instant.parse("2026-06-22T00:00:08Z"))
        entityManager.flush()

        val userIds = tripParticipantRepository.findActiveUserIdsForNotification(
            tripId = trip.id,
            participantStatus = TripParticipantStatus.ACTIVE.name,
            userStatus = UserStatus.ACTIVE.name,
        )

        assertEquals(listOf(owner.id, activeMember.id), userIds)
    }

    private fun persistUser(
        nickname: String,
        status: UserStatus = UserStatus.ACTIVE,
    ): User {
        val user = User(
            nickname = nickname,
            status = status,
        )
        entityManager.persist(user)
        return user
    }

    private fun persistTrip(owner: User): Trip {
        val trip = Trip(
            ownerUser = owner,
            title = "일본 여행",
            defaultCurrency = "JPY",
        )
        entityManager.persist(trip)
        return trip
    }

    private fun persistParticipant(
        trip: Trip,
        user: User?,
        idOrder: Int,
        createdAt: Instant,
        displayName: String = user?.nickname ?: "임시 참여자",
        status: TripParticipantStatus = TripParticipantStatus.ACTIVE,
    ): TripParticipant {
        val participant = TripParticipant(
            trip = trip,
            user = user,
            displayName = displayName,
            participantRole = if (idOrder == 1) TripParticipantRole.LEADER else TripParticipantRole.MEMBER,
            participantStatus = status,
            joinedAt = user?.let { createdAt },
        ).apply {
            this.createdAt = createdAt
            this.updatedAt = createdAt
        }
        entityManager.persist(participant)
        return participant
    }
}
