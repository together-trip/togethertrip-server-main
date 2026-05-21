package com.togethertrip.main.trip.controller

import com.togethertrip.main.trip.service.TripService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Trip", description = "여행 API")
@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
)
