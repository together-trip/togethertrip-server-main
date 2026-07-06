package com.togethertrip.main.triprecap.service

import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.repository.PostAttachmentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripCountryRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.service.ai.TripRecapCountryInput
import com.togethertrip.main.triprecap.service.ai.TripRecapExpenseSignal
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerateRequest
import com.togethertrip.main.triprecap.service.ai.TripRecapPhotoReference
import com.togethertrip.main.triprecap.service.ai.TripRecapPlaceInput
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class TripRecapDataCollector(
    private val tripCountryRepository: TripCountryRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val postRepository: PostRepository,
    private val postAttachmentRepository: PostAttachmentRepository,
    private val transactionRepository: TransactionRepository,
) {

    fun collect(
        trip: Trip,
        style: TripRecapStyle,
    ): TripRecapGenerateRequest {
        val countries = tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(trip.id)
            .map {
                TripRecapCountryInput(
                    countryCode = it.countryCode,
                    countryName = it.countryName,
                )
            }
        val posts = postRepository.findTripRecapSourcePosts(
            tripId = trip.id,
            pageable = PageRequest.of(0, MAX_SOURCE_POSTS),
        )
        val postIds = posts.map { it.id }
        val attachments = if (postIds.isEmpty()) {
            emptyList()
        } else {
            postAttachmentRepository.findByPostIdInAndDeletedAtIsNullOrderByPostIdAscSortOrderAsc(postIds)
        }
        val transactions = transactionRepository.findTripRecapExpenseSignals(
            tripId = trip.id,
            status = TransactionStatus.ACTIVE,
            pageable = PageRequest.of(0, MAX_EXPENSE_SIGNALS),
        )

        return TripRecapGenerateRequest(
            tripTitle = trip.title,
            startDate = trip.startDate,
            endDate = trip.endDate,
            defaultCurrency = trip.defaultCurrency,
            memberCount = tripParticipantRepository.countByTripIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = trip.id,
                participantStatus = TripParticipantStatus.ACTIVE,
            ),
            style = style,
            countries = countries,
            places = posts.mapNotNull { post ->
                post.placeName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { placeName ->
                        TripRecapPlaceInput(
                            name = placeName,
                            occurredAt = post.occurredAt ?: post.createdAt,
                        )
                    }
            }.distinctBy { it.name },
            expenseSignals = transactions.map { transaction ->
                TripRecapExpenseSignal(
                    category = transaction.category,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    occurredAt = transaction.occurredAt ?: transaction.createdAt,
                )
            },
            photoReferences = attachments
                .filter { it.attachmentType == PostAttachmentType.IMAGE }
                .take(MAX_PHOTO_REFERENCES)
                .map {
                    TripRecapPhotoReference(
                        imageUrl = it.fileUrl,
                        thumbnailUrl = it.thumbnailUrl,
                    )
                },
        )
    }

    companion object {
        private const val MAX_SOURCE_POSTS = 50
        private const val MAX_EXPENSE_SIGNALS = 50
        private const val MAX_PHOTO_REFERENCES = 20
    }
}
