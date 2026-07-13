package com.togethertrip.main.triprecap.service.ai

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import java.time.LocalDate

data class TripRecapGenerateRequest(
    val tripTitle: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val defaultCurrency: String,
    val memberCount: Int,
    val style: TripRecapStyle,
    val imageAspectRatio: String = "9:16",
    val countries: List<TripRecapCountryInput>,
    val places: List<TripRecapPlaceInput>,
    val expenseSignals: List<TripRecapExpenseSignal>,
    val photoReferences: List<TripRecapPhotoReference>,
)
