package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettlementAccessResolverTest {

    private lateinit var tripRepository: TripRepository
    private lateinit var participantRepository: TripParticipantRepository
    private lateinit var userRepository: UserRepository
    private lateinit var resolver: SettlementAccessResolver

    @BeforeEach
    fun setUp() {
        tripRepository = mock(TripRepository::class.java)
        participantRepository = mock(TripParticipantRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        resolver = SettlementAccessResolver(tripRepository, participantRepository, userRepository)
    }

    @Test
    fun `존재하지 않는 사용자는 USER_NOT_FOUND다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(null)

        val exception = assertFailsWith<BusinessException> { resolver.getActiveUser(1L) }

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `정지 사용자는 INACTIVE_USER이고 여행을 조회하지 않는다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user(1L, UserStatus.SUSPENDED))

        val exception = assertFailsWith<BusinessException> { resolver.getAccessibleTrip(1L, 10L) }

        assertEquals(UserErrorCode.INACTIVE_USER, exception.errorCode)
        verifyNoInteractions(tripRepository, participantRepository)
    }

    @Test
    fun `존재하지 않는 여행은 TRIP_NOT_FOUND다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user(1L))
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(null)

        val exception = assertFailsWith<BusinessException> { resolver.getAccessibleTrip(1L, 10L) }

        assertEquals(TripErrorCode.TRIP_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(participantRepository)
    }

    @Test
    fun `활성 참여자가 아니면 TRIP_ACCESS_DENIED다`() {
        val owner = user(1L)
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip(owner))
        `when`(
            participantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                1L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(null)

        val exception = assertFailsWith<BusinessException> { resolver.getAccessibleTrip(1L, 10L) }

        assertEquals(TripErrorCode.TRIP_ACCESS_DENIED, exception.errorCode)
    }

    @Test
    fun `활성 참여자는 접근 가능한 여행을 반환한다`() {
        val owner = user(1L)
        val trip = trip(owner)
        stubActiveAccess(owner, trip, participant(trip, owner))

        assertEquals(trip, resolver.getAccessibleTrip(1L, 10L))
    }

    @Test
    fun `활성 참여자라도 방장이 아니면 TRIP_OWNER_ONLY다`() {
        val owner = user(1L)
        val member = user(2L)
        val trip = trip(owner)
        stubActiveAccess(member, trip, participant(trip, member))

        val exception = assertFailsWith<BusinessException> { resolver.getOwnedTrip(2L, 10L) }

        assertEquals(TripErrorCode.TRIP_OWNER_ONLY, exception.errorCode)
    }

    @Test
    fun `방장 활성 참여자는 owned trip을 반환한다`() {
        val owner = user(1L)
        val trip = trip(owner)
        stubActiveAccess(owner, trip, participant(trip, owner))

        assertEquals(trip, resolver.getOwnedTrip(1L, 10L))
    }

    @Test
    fun `active participant 조회는 실제 참여자 entity를 반환한다`() {
        val owner = user(1L)
        val trip = trip(owner)
        val participant = participant(trip, owner)
        stubActiveAccess(owner, trip, participant)

        assertEquals(participant, resolver.getActiveParticipant(1L, 10L))
    }

    private fun stubActiveAccess(user: User, trip: Trip, participant: TripParticipant) {
        `when`(userRepository.findByIdAndDeletedAtIsNull(user.id)).thenReturn(user)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(trip.id)).thenReturn(trip)
        `when`(
            participantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                trip.id,
                user.id,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
    }

    private fun user(id: Long, status: UserStatus = UserStatus.ACTIVE): User {
        return User(nickname = "사용자$id", status = status).apply { this.id = id }
    }

    private fun trip(owner: User): Trip {
        return Trip(owner, "정산 접근 테스트", "KRW").apply { id = 10L }
    }

    private fun participant(trip: Trip, user: User): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = user.nickname,
            participantRole = if (trip.ownerUser.id == user.id) TripParticipantRole.LEADER else TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply { id = user.id * 100 }
    }
}
