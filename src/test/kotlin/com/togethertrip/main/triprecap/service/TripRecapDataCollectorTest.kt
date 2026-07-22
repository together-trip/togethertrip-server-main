package com.togethertrip.main.triprecap.service

import com.togethertrip.main.post.domain.Post
import com.togethertrip.main.post.domain.PostAttachment
import com.togethertrip.main.post.domain.PostAttachmentType
import com.togethertrip.main.post.domain.PostType
import com.togethertrip.main.post.repository.PostAttachmentRepository
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripCountry
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripCountryRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripRecapDataCollectorTest {

    private lateinit var tripCountryRepository: TripCountryRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var postRepository: PostRepository
    private lateinit var postAttachmentRepository: PostAttachmentRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var collector: TripRecapDataCollector

    @BeforeEach
    fun setUp() {
        tripCountryRepository = mock(TripCountryRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        postRepository = mock(PostRepository::class.java)
        postAttachmentRepository = mock(PostAttachmentRepository::class.java)
        transactionRepository = mock(TransactionRepository::class.java)
        collector = TripRecapDataCollector(
            tripCountryRepository,
            tripParticipantRepository,
            postRepository,
            postAttachmentRepository,
            transactionRepository,
        )
    }

    @Test
    fun `게시글이 없으면 첨부를 조회하지 않고 빈 AI 입력을 만든다`() {
        val trip = trip()
        `when`(tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(10L))
            .thenReturn(emptyList())
        `when`(postRepository.findTripRecapSourcePosts(10L, PageRequest.of(0, 50)))
            .thenReturn(emptyList())
        `when`(
            transactionRepository.findTripRecapExpenseSignals(
                10L,
                TransactionStatus.ACTIVE,
                PageRequest.of(0, 50),
            )
        ).thenReturn(emptyList())
        `when`(
            tripParticipantRepository.countByTripIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(0)

        val result = collector.collect(trip, TripRecapStyle.PHOTO)

        assertEquals("제주 여행", result.tripTitle)
        assertEquals("KRW", result.defaultCurrency)
        assertEquals(0, result.memberCount)
        assertEquals(TripRecapStyle.PHOTO, result.style)
        assertEquals(emptyList(), result.countries)
        assertEquals(emptyList(), result.places)
        assertEquals(emptyList(), result.expenseSignals)
        assertEquals(emptyList(), result.photoReferences)
        verifyNoInteractions(postAttachmentRepository)
    }

    @Test
    fun `여행 원천 데이터를 정규화하고 사진 참조를 스무 장으로 제한한다`() {
        val trip = trip()
        val author = participant(trip)
        val firstOccurredAt = Instant.parse("2026-06-01T10:00:00Z")
        val fallbackCreatedAt = Instant.parse("2026-06-02T11:00:00Z")
        val posts = listOf(
            post(trip, author, 1L, " Seoul ", firstOccurredAt),
            post(trip, author, 2L, "Seoul", Instant.parse("2026-06-01T11:00:00Z")),
            post(trip, author, 3L, "   ", null),
            post(trip, author, 4L, null, null),
            post(trip, author, 5L, "Busan", null).apply { createdAt = fallbackCreatedAt },
        )
        val attachments = (0..20).map { index ->
            PostAttachment(
                post = posts.first(),
                attachmentType = PostAttachmentType.IMAGE,
                fileUrl = "/images/$index.png",
                thumbnailUrl = if (index == 0) "/thumbs/0.png" else null,
                sortOrder = index,
            )
        } + PostAttachment(
            post = posts.first(),
            attachmentType = PostAttachmentType.VIDEO,
            fileUrl = "/videos/ignored.mp4",
        )
        val occurredExpenseAt = Instant.parse("2026-06-03T12:00:00Z")
        val transactions = listOf(
            transaction(trip, 1L, "FOOD", occurredExpenseAt),
            transaction(trip, 2L, null, null).apply {
                createdAt = Instant.parse("2026-06-04T13:00:00Z")
            },
        )
        val countries = listOf(
            TripCountry(trip, "KR", "대한민국", 0),
            TripCountry(trip, "JP", "일본", 1),
        )
        `when`(tripCountryRepository.findByTripIdAndDeletedAtIsNullOrderBySortOrderAsc(10L))
            .thenReturn(countries)
        `when`(postRepository.findTripRecapSourcePosts(10L, PageRequest.of(0, 50))).thenReturn(posts)
        `when`(
            postAttachmentRepository.findByPostIdInAndDeletedAtIsNullOrderByPostIdAscSortOrderAsc(
                listOf(1L, 2L, 3L, 4L, 5L)
            )
        ).thenReturn(attachments)
        `when`(
            transactionRepository.findTripRecapExpenseSignals(
                10L,
                TransactionStatus.ACTIVE,
                PageRequest.of(0, 50),
            )
        ).thenReturn(transactions)
        `when`(
            tripParticipantRepository.countByTripIdAndParticipantStatusAndDeletedAtIsNull(
                10L,
                TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(3)

        val result = collector.collect(trip, TripRecapStyle.ILLUSTRATION)

        assertEquals(listOf("KR", "JP"), result.countries.map { it.countryCode })
        assertEquals(listOf("Seoul", "Busan"), result.places.map { it.name })
        assertEquals(firstOccurredAt, result.places[0].occurredAt)
        assertEquals(fallbackCreatedAt, result.places[1].occurredAt)
        assertEquals(listOf("FOOD", null), result.expenseSignals.map { it.category })
        assertEquals(occurredExpenseAt, result.expenseSignals[0].occurredAt)
        assertEquals(transactions[1].createdAt, result.expenseSignals[1].occurredAt)
        assertEquals(20, result.photoReferences.size)
        assertEquals("/images/0.png", result.photoReferences.first().imageUrl)
        assertEquals("/images/19.png", result.photoReferences.last().imageUrl)
        assertEquals("/thumbs/0.png", result.photoReferences.first().thumbnailUrl)
        assertNull(result.photoReferences.last().thumbnailUrl)
        assertEquals(3, result.memberCount)
        assertEquals(TripRecapStyle.ILLUSTRATION, result.style)
    }

    private fun trip(): Trip {
        return Trip(
            ownerUser = user(),
            title = "제주 여행",
            defaultCurrency = "KRW",
            startDate = LocalDate.parse("2026-06-01"),
            endDate = LocalDate.parse("2026-06-05"),
        ).apply { id = 10L }
    }

    private fun user(): User {
        return User(
            nickname = "재완",
            status = UserStatus.ACTIVE,
        ).apply { id = 1L }
    }

    private fun participant(trip: Trip): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = trip.ownerUser,
            displayName = "재완",
            participantRole = TripParticipantRole.LEADER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply { id = 100L }
    }

    private fun post(
        trip: Trip,
        author: TripParticipant,
        id: Long,
        placeName: String?,
        occurredAt: Instant?,
    ): Post {
        return Post(
            trip = trip,
            author = author,
            postType = PostType.RECORD,
            placeName = placeName,
            occurredAt = occurredAt,
        ).apply { this.id = id }
    }

    private fun transaction(
        trip: Trip,
        id: Long,
        category: String?,
        occurredAt: Instant?,
    ): Transaction {
        return Transaction(
            trip = trip,
            createdBy = trip.ownerUser,
            transactionType = TransactionType.EXPENSE,
            amount = BigDecimal("10000.00"),
            currency = "KRW",
            exchangeRate = BigDecimal.ONE,
            baseCurrency = "KRW",
            baseAmount = BigDecimal("10000.00"),
            category = category,
            occurredAt = occurredAt,
        ).apply { this.id = id }
    }
}
