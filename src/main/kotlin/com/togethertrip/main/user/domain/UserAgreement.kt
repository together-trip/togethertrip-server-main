package com.togethertrip.main.user.domain

import com.togethertrip.main.global.domain.BaseEntity
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
@Table(name = "user_agreements")
class UserAgreement(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 30)
    var agreementType: UserAgreementType,

    @Column(nullable = false)
    var agreed: Boolean,

    @Column(name = "term_version", nullable = false, length = 20)
    var termVersion: String,

    @Column(name = "agreed_at")
    var agreedAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

) : BaseEntity() {

    fun agree(
        termVersion: String,
        now: Instant = Instant.now(),
    ) {
        this.agreed = true
        this.termVersion = termVersion
        this.agreedAt = now
        this.revokedAt = null
        this.updatedAt = now
    }

    fun revoke(now: Instant = Instant.now()) {
        this.agreed = false
        this.revokedAt = now
        this.updatedAt = now
    }
}
