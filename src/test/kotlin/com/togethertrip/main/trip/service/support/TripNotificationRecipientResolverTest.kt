package com.togethertrip.main.trip.service.support

import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.user.domain.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripNotificationRecipientResolverTest {

    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var resolver: TripNotificationRecipientResolver

    @BeforeEach
    fun setUp() {
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        resolver = TripNotificationRecipientResolver(tripParticipantRepository)
    }

    @Test
    fun `활성 회원 참여자 userId에서 행위자와 중복을 제거한다`() {
        `when`(
            tripParticipantRepository.findActiveUserIdsForNotification(
                tripId = 20L,
                participantStatus = TripParticipantStatus.ACTIVE.name,
                userStatus = UserStatus.ACTIVE.name,
            ),
        ).thenReturn(listOf(10L, 11L, 12L, 11L, 13L))

        val userIds = resolver.findActiveUserIds(
            tripId = 20L,
            actorUserId = 10L,
        )

        assertEquals(listOf(11L, 12L, 13L), userIds)
        verify(tripParticipantRepository).findActiveUserIdsForNotification(
            tripId = 20L,
            participantStatus = TripParticipantStatus.ACTIVE.name,
            userStatus = UserStatus.ACTIVE.name,
        )
    }

    @Test
    fun `단일 수신자 userId가 행위자와 같으면 제외한다`() {
        val userId = resolver.resolveSingleUserId(
            userId = 10L,
            actorUserId = 10L,
        )

        assertNull(userId)
    }

    @Test
    fun `단일 수신자 userId가 없으면 제외한다`() {
        val userId = resolver.resolveSingleUserId(
            userId = null,
            actorUserId = 10L,
        )

        assertNull(userId)
    }

    @Test
    fun `단일 수신자 userId가 행위자와 다르면 유지한다`() {
        val userId = resolver.resolveSingleUserId(
            userId = 11L,
            actorUserId = 10L,
        )

        assertEquals(11L, userId)
    }
}
