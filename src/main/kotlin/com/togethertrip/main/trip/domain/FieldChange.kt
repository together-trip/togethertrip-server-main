package com.togethertrip.main.trip.domain

sealed interface FieldChange<out T> {

    data object Unchanged : FieldChange<Nothing>

    data class Changed<T>(
        val value: T,
    ) : FieldChange<T>
}
