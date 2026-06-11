package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripInvitation
import com.togethertrip.main.trip.domain.TripInvitationStatus
import com.togethertrip.main.trip.domain.TripInvitationType
import java.time.Instant

data class TripInviteResponse(
    val id: Long,
    val tripId: Long,
    val type: TripInvitationType,
    val code: String?,
    val token: String,
    val inviteUrl: String,
    val invitationStatus: TripInvitationStatus,
    val expiresAt: Instant?,
) {
    companion object {
        fun from(invitation: TripInvitation): TripInviteResponse {
            return TripInviteResponse(
                id = invitation.id,
                tripId = invitation.trip.id,
                type = invitation.invitationType,
                code = invitation.code,
                token = invitation.token,
                inviteUrl = invitation.inviteUrl,
                invitationStatus = invitation.invitationStatus,
                expiresAt = invitation.expiresAt,
            )
        }
    }
}
