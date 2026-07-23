package com.togethertrip.main.trip.domain

import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(name = "trip_invitations")
@SQLRestriction("deleted_at IS NULL")
class TripInvitation(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @Column(nullable = false, length = 100)
    var token: String,

    @Column(length = 20)
    var code: String? = null,

    @Column(name = "invite_url", nullable = false, length = 1000)
    var inviteUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_type", nullable = false, length = 20)
    var invitationType: TripInvitationType,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    var createdBy: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_user_id")
    var usedBy: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_status", nullable = false, length = 20)
    var invitationStatus: TripInvitationStatus = TripInvitationStatus.ACTIVE,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

) : BaseEntity() {

    fun isExpired(now: Instant): Boolean {
        return expiresAt?.let { it <= now } ?: false
    }

    fun markExpired(now: Instant) {
        invitationStatus = TripInvitationStatus.EXPIRED
        updatedAt = now
    }

    fun markUsed(
        user: User,
        now: Instant,
    ) {
        usedBy = user
        usedAt = now
        invitationStatus = TripInvitationStatus.USED
        updatedAt = now
    }
}
