package com.togethertrip.main.triprecap.service.ai

interface TripRecapGenerator {

    fun generate(request: TripRecapGenerateRequest): TripRecapGenerateResult
}
