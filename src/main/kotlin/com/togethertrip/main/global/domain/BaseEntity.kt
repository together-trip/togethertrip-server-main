package com.togethertrip.main.global.domain

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant

@MappedSuperclass
abstract class BaseEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0L,

    // TIMESTAMPTZ 매핑: Instant 는 Hibernate 가 timestamp with time zone 으로 매핑한다.
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
