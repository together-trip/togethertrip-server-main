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
@Table(name = "trip_participants")
class TripParticipant(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    var user: User? = null,

    @Column(name = "display_name", nullable = false, length = 50)
    var displayName: String,

    @Column(name = "profile_image_url", length = 500)
    var profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_role", nullable = false, length = 20)
    var participantRole: TripParticipantRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_status", nullable = false, length = 20)
    var participantStatus: TripParticipantStatus,

    @Column(name = "joined_at")
    var joinedAt: Instant? = null,

    @Column(name = "left_at")
    var leftAt: Instant? = null,

) : BaseEntity()
