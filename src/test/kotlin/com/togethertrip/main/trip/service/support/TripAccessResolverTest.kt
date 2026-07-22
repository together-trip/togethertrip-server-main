package com.togethertrip.main.trip.service.support

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

class TripAccessResolverTest {

    private lateinit var tripRepository: TripRepository
    private lateinit var participantRepository: TripParticipantRepository
    private lateinit var userRepository: UserRepository
    private lateinit var resolver: TripAccessResolver

    @BeforeEach
    fun setUp() {
        tripRepository = mock(TripRepository::class.java)
        participantRepository = mock(TripParticipantRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        resolver = TripAccessResolver(tripRepository, participantRepository, userRepository)
    }

    @Test
    fun `사용자가 없거나 활성 상태가 아니면 접근을 거부한다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(null)
        assertEquals(
            UserErrorCode.USER_NOT_FOUND,
            assertFailsWith<BusinessException> { resolver.getActiveUser(1L) }.errorCode,
        )

        `when`(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(user(2L, UserStatus.SUSPENDED))
        assertEquals(
            UserErrorCode.INACTIVE_USER,
            assertFailsWith<BusinessException> { resolver.getActiveUser(2L) }.errorCode,
        )
    }

    @Test
    fun `여행이 없으면 TRIP_NOT_FOUND다`() {
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(null)

        val exception = assertFailsWith<BusinessException> { resolver.getAccessibleTrip(1L, 10L) }

        assertEquals(TripErrorCode.TRIP_NOT_FOUND, exception.errorCode)
        verifyNoInteractions(participantRepository)
    }

    @Test
    fun `방장은 참여자 조회 없이 여행에 접근한다`() {
        val owner = user(1L)
        val trip = trip(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)

        assertEquals(trip, resolver.getAccessibleTrip(1L, 10L))
        verifyNoInteractions(participantRepository)
    }

    @Test
    fun `활성 참여자는 여행에 접근하고 비활성 참여자는 거부된다`() {
        val owner = user(1L)
        val member = user(2L)
        val trip = trip(owner)
        val participant = participant(trip, member)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            participantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L, 2L, TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)

        assertEquals(trip, resolver.getAccessibleTrip(2L, 10L))

        `when`(
            participantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L, 3L, TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(null)
        assertEquals(
            TripErrorCode.TRIP_ACCESS_DENIED,
            assertFailsWith<BusinessException> { resolver.getAccessibleTrip(3L, 10L) }.errorCode,
        )
    }

    @Test
    fun `쓰기 접근은 잠금 조회를 사용하고 동일한 권한을 검증한다`() {
        val owner = user(1L)
        val trip = trip(owner)
        `when`(tripRepository.findByIdAndDeletedAtIsNullForUpdate(10L)).thenReturn(trip)

        assertEquals(trip, resolver.getAccessibleTripForUpdate(1L, 10L))

        `when`(tripRepository.findByIdAndDeletedAtIsNullForUpdate(11L)).thenReturn(null)
        assertEquals(
            TripErrorCode.TRIP_NOT_FOUND,
            assertFailsWith<BusinessException> { resolver.getAccessibleTripForUpdate(1L, 11L) }.errorCode,
        )
    }

    @Test
    fun `participant id 조회는 활성 참여자만 반환한다`() {
        val owner = user(1L)
        val trip = trip(owner)
        val participant = participant(trip, owner)
        `when`(
            participantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                100L, 10L, TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)

        assertEquals(participant, resolver.getActiveParticipantById(10L, 100L))
        assertEquals(
            TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND,
            assertFailsWith<BusinessException> { resolver.getActiveParticipantById(10L, 999L) }.errorCode,
        )
    }

    @Test
    fun `participant user id 조회는 활성 참여자만 반환한다`() {
        val owner = user(1L)
        val trip = trip(owner)
        val participant = participant(trip, owner)
        `when`(
            participantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                10L, 1L, TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)

        assertEquals(participant, resolver.getActiveParticipantByUserId(10L, 1L))
        assertEquals(
            TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND,
            assertFailsWith<BusinessException> { resolver.getActiveParticipantByUserId(10L, 999L) }.errorCode,
        )
    }

    private fun user(id: Long, status: UserStatus = UserStatus.ACTIVE): User {
        return User(nickname = "사용자$id", status = status).apply { this.id = id }
    }

    private fun trip(owner: User): Trip {
        return Trip(owner, "권한 테스트", "KRW").apply { id = 10L }
    }

    private fun participant(trip: Trip, user: User): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = user.nickname,
            participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply { id = 100L }
    }
}
