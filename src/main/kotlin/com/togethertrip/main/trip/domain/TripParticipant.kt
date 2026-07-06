package com.togethertrip.main.trip.domain

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.domain.BaseEntity
import com.togethertrip.main.trip.exception.TripErrorCode
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
@Table(name = "trip_participants")
@SQLRestriction("deleted_at IS NULL")
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

) : BaseEntity() {

    fun updateTemporaryProfile(
        displayName: String?,
        profileImageUrl: String?,
        profileImageUrlChanged: Boolean,
        updatedAt: Instant,
    ) {
        if (user != null) {
            throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_PROFILE_EDIT_DENIED)
        }
        displayName?.let {
            this.displayName = it
        }
        if (profileImageUrlChanged) {
            this.profileImageUrl = profileImageUrl
        }
        this.updatedAt = updatedAt
    }

    fun remove(
        tripOwnerUserId: Long,
        removedAt: Instant,
    ) {
        if (participantRole == TripParticipantRole.LEADER || user?.id == tripOwnerUserId) {
            throw BusinessException(TripErrorCode.TRIP_LEADER_REMOVE_DENIED)
        }

        participantStatus = TripParticipantStatus.REMOVED
        leftAt = removedAt
        markDeleted(removedAt)
    }

    fun linkUser(
        user: User,
        linkedAt: Instant,
    ) {
        if (this.user != null) {
            throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_ALREADY_LINKED)
        }

        this.user = user
        displayName = user.nickname
        profileImageUrl = user.profileImageUrl
        joinedAt = linkedAt
        updatedAt = linkedAt
    }
}
