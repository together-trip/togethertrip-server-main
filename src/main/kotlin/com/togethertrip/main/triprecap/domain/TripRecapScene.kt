package com.togethertrip.main.triprecap.domain

import com.togethertrip.main.global.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "trip_recap_scenes")
@SQLRestriction("deleted_at IS NULL")
class TripRecapScene(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recap_id", nullable = false)
    var recap: TripRecap,

    @Column(name = "scene_order", nullable = false)
    var sceneOrder: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var style: TripRecapStyle,

    @Column(name = "image_object_key", nullable = false, length = 500)
    var imageObjectKey: String,

    @Column(name = "image_url", nullable = false, length = 1000)
    var imageUrl: String,

    @Column(name = "scene_description", nullable = false, columnDefinition = "TEXT")
    var sceneDescription: String,

    @Column(name = "image_prompt", nullable = false, columnDefinition = "TEXT")
    var imagePrompt: String,

    @Column(name = "generation_provider", nullable = false, length = 50)
    var generationProvider: String,

    @Column(name = "generation_model", nullable = false, length = 100)
    var generationModel: String,

) : BaseEntity()
