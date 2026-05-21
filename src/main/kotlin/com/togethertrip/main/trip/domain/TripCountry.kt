package com.togethertrip.main.trip.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "trip_countries")
class TripCountry(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    var trip: Trip,

    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String,

    @Column(name = "country_name", nullable = false, length = 100)
    var countryName: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,

) : BaseEntity()
