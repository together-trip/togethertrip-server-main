package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripInvitation
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TripInvitationRepository : JpaRepository<TripInvitation, Long> {

    fun existsByTokenAndDeletedAtIsNull(token: String): Boolean

    fun existsByCodeAndDeletedAtIsNull(code: String): Boolean

    fun findByTokenAndDeletedAtIsNull(token: String): TripInvitation?

    fun findByCodeAndDeletedAtIsNull(code: String): TripInvitation?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select invitation
        from TripInvitation invitation
        where invitation.token = :token
          and invitation.deletedAt is null
        """
    )
    fun findLockedByTokenAndDeletedAtIsNull(@Param("token") token: String): TripInvitation?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select invitation
        from TripInvitation invitation
        where invitation.code = :code
          and invitation.deletedAt is null
        """
    )
    fun findLockedByCodeAndDeletedAtIsNull(@Param("code") code: String): TripInvitation?
}
