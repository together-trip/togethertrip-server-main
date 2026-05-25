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
import java.time.Instant

@Entity
@Table(name = "trip_invitations")
class TripInvitation(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @Column(nullable = false, length = 100)
    var token: String,

    @Column(name = "invite_url", nullable = false, length = 1000)
    var inviteUrl: String,

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

) : BaseEntity()
