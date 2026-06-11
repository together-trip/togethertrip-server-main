package com.togethertrip.main.trip.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripInvitation
import com.togethertrip.main.trip.domain.TripInvitationStatus
import com.togethertrip.main.trip.domain.TripInvitationType
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.dto.request.JoinTripRequest
import com.togethertrip.main.trip.dto.response.JoinTripResponse
import com.togethertrip.main.trip.dto.response.TripInviteInfoResponse
import com.togethertrip.main.trip.dto.response.TripInviteResponse
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripInvitationRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

@Service
@Transactional(readOnly = true)
class TripInviteService(
    private val tripRepository: TripRepository,
    private val tripInvitationRepository: TripInvitationRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
    @Value("\${trip.invite.base-url:https://togethertrip.app/invites}")
    private val inviteBaseUrl: String,
) {

    @Transactional
    fun createInviteCode(
        userId: Long,
        tripId: Long,
    ): TripInviteResponse {
        val user = getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)

        return createInvitation(
            trip = trip,
            createdBy = user,
            type = TripInvitationType.CODE,
        )
    }

    @Transactional
    fun createInviteLink(
        userId: Long,
        tripId: Long,
    ): TripInviteResponse {
        val user = getActiveUser(userId)
        val trip = getOwnedTrip(userId, tripId)

        return createInvitation(
            trip = trip,
            createdBy = user,
            type = TripInvitationType.LINK,
        )
    }

    fun getInviteInfo(
        userId: Long,
        code: String?,
        token: String?,
    ): TripInviteInfoResponse {
        getActiveUser(userId)
        val invitation = findInvitation(
            code = code,
            token = token,
            lock = false,
        )
        validateUsableInvitation(invitation, markExpired = false)

        val alreadyJoined = tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = invitation.trip.id,
            userId = userId,
            participantStatus = TripParticipantStatus.ACTIVE,
        )

        return TripInviteInfoResponse.from(
            invitation = invitation,
            alreadyJoined = alreadyJoined,
        )
    }

    @Transactional
    fun joinTrip(
        userId: Long,
        request: JoinTripRequest,
    ): JoinTripResponse {
        val user = getActiveUser(userId)
        val invitation = findInvitation(
            code = request.code,
            token = request.token,
            lock = true,
        )
        validateUsableInvitation(invitation, markExpired = true)

        if (
            tripParticipantRepository.existsByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = invitation.trip.id,
                userId = user.id,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ) {
            throw BusinessException(TripErrorCode.TRIP_ALREADY_JOINED)
        }

        val now = Instant.now(clock)
        val participant = try {
            tripParticipantRepository.saveAndFlush(
                TripParticipant(
                    trip = invitation.trip,
                    user = user,
                    displayName = user.nickname,
                    profileImageUrl = user.profileImageUrl,
                    participantRole = TripParticipantRole.MEMBER,
                    participantStatus = TripParticipantStatus.ACTIVE,
                    joinedAt = now,
                )
            )
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(TripErrorCode.TRIP_ALREADY_JOINED)
        }

        invitation.markUsed(
            user = user,
            now = now,
        )

        return JoinTripResponse.from(
            invitation = invitation,
            participant = participant,
        )
    }

    private fun createInvitation(
        trip: Trip,
        createdBy: User,
        type: TripInvitationType,
    ): TripInviteResponse {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val token = generateToken()
            val code = if (type == TripInvitationType.CODE) generateCode() else null

            if (tripInvitationRepository.existsByTokenAndDeletedAtIsNull(token)) {
                return@repeat
            }

            if (code != null && tripInvitationRepository.existsByCodeAndDeletedAtIsNull(code)) {
                return@repeat
            }

            val invitation = TripInvitation(
                trip = trip,
                token = token,
                code = code,
                inviteUrl = createInviteUrl(
                    code = code,
                    token = token,
                ),
                invitationType = type,
                createdBy = createdBy,
                invitationStatus = TripInvitationStatus.ACTIVE,
                expiresAt = Instant.now(clock).plus(DEFAULT_INVITATION_TTL),
            )

            return try {
                TripInviteResponse.from(tripInvitationRepository.saveAndFlush(invitation))
            } catch (_: DataIntegrityViolationException) {
                return@repeat
            }
        }

        throw BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION)
    }

    private fun findInvitation(
        code: String?,
        token: String?,
        lock: Boolean,
    ): TripInvitation {
        val normalizedCode = code?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        val normalizedToken = token?.trim()?.takeIf { it.isNotBlank() }

        if ((normalizedCode == null) == (normalizedToken == null)) {
            throw BusinessException(TripErrorCode.INVALID_TRIP_INVITATION_LOOKUP)
        }

        return if (normalizedCode != null) {
            if (lock) {
                tripInvitationRepository.findLockedByCodeAndDeletedAtIsNull(normalizedCode)
            } else {
                tripInvitationRepository.findByCodeAndDeletedAtIsNull(normalizedCode)
            }
        } else {
            val tokenValue = normalizedToken
                ?: throw BusinessException(TripErrorCode.INVALID_TRIP_INVITATION_LOOKUP)

            if (lock) {
                tripInvitationRepository.findLockedByTokenAndDeletedAtIsNull(tokenValue)
            } else {
                tripInvitationRepository.findByTokenAndDeletedAtIsNull(tokenValue)
            }
        } ?: throw BusinessException(TripErrorCode.TRIP_INVITATION_NOT_FOUND)
    }

    private fun validateUsableInvitation(
        invitation: TripInvitation,
        markExpired: Boolean,
    ) {
        if (invitation.invitationStatus != TripInvitationStatus.ACTIVE) {
            throw BusinessException(TripErrorCode.TRIP_INVITATION_NOT_ACTIVE)
        }

        val now = Instant.now(clock)
        if (invitation.isExpired(now)) {
            if (markExpired && invitation.invitationStatus == TripInvitationStatus.ACTIVE) {
                invitation.markExpired(now)
            }
            throw BusinessException(TripErrorCode.TRIP_INVITATION_EXPIRED)
        }
    }

    private fun getOwnedTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        val trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)

        if (trip.ownerUser.id != userId) {
            throw BusinessException(TripErrorCode.TRIP_OWNER_ONLY)
        }

        return trip
    }

    private fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private fun generateCode(): String {
        return (1..CODE_LENGTH)
            .map { CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)] }
            .joinToString("")
    }

    private fun createInviteUrl(
        code: String?,
        token: String,
    ): String {
        val baseUrl = inviteBaseUrl.trimEnd('/')
        val query = code?.let { "code=$it" } ?: "token=$token"

        return "$baseUrl?$query"
    }

    companion object {
        private const val CODE_LENGTH = 8
        private const val TOKEN_BYTE_LENGTH = 32
        private const val MAX_GENERATION_ATTEMPTS = 5
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val DEFAULT_INVITATION_TTL: Duration = Duration.ofDays(7)
        private val secureRandom = SecureRandom()
    }
}
