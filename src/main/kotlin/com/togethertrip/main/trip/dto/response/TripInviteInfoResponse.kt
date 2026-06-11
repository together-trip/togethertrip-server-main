package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripInvitation
import com.togethertrip.main.trip.domain.TripInvitationStatus
import com.togethertrip.main.trip.domain.TripInvitationType
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.domain.TripStatus
import java.time.Instant
import java.time.LocalDate

data class TripInviteInfoResponse(
    val invitationId: Long,
    val type: TripInvitationType,
    val code: String?,
    val invitationStatus: TripInvitationStatus,
    val expiresAt: Instant?,
    val trip: TripInviteTripResponse,
    val createdBy: TripInviteUserResponse,
    val alreadyJoined: Boolean,
) {
    companion object {
        fun from(
            invitation: TripInvitation,
            alreadyJoined: Boolean,
        ): TripInviteInfoResponse {
            return TripInviteInfoResponse(
                invitationId = invitation.id,
                type = invitation.invitationType,
                code = invitation.code,
                invitationStatus = invitation.invitationStatus,
                expiresAt = invitation.expiresAt,
                trip = TripInviteTripResponse.from(invitation),
                createdBy = TripInviteUserResponse.from(invitation),
                alreadyJoined = alreadyJoined,
            )
        }
    }
}

data class TripInviteTripResponse(
    val id: Long,
    val title: String,
    val defaultCurrency: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val tripStatus: TripStatus,
    val settlementStatus: TripSettlementStatus,
    val ownerUserId: Long,
) {
    companion object {
        fun from(invitation: TripInvitation): TripInviteTripResponse {
            val trip = invitation.trip

            return TripInviteTripResponse(
                id = trip.id,
                title = trip.title,
                defaultCurrency = trip.defaultCurrency,
                startDate = trip.startDate,
                endDate = trip.endDate,
                tripStatus = trip.tripStatus,
                settlementStatus = trip.settlementStatus,
                ownerUserId = trip.ownerUser.id,
            )
        }
    }
}

data class TripInviteUserResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
) {
    companion object {
        fun from(invitation: TripInvitation): TripInviteUserResponse {
            val user = invitation.createdBy

            return TripInviteUserResponse(
                id = user.id,
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
            )
        }
    }
}
