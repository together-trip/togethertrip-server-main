package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.domain.calculation.SettlementCalculationResult
import com.togethertrip.main.settlement.domain.calculation.SettlementParticipantBalance
import com.togethertrip.main.settlement.domain.calculation.SettlementTransferPlan
import com.togethertrip.main.settlement.domain.snapshot.SettlementParticipantSnapshot
import com.togethertrip.main.settlement.dto.response.SettlementParticipantBalanceResponse
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementRepository
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.service.support.SettlementCalculationService
import com.togethertrip.main.settlement.service.support.SettlementShareTokenGenerator
import com.togethertrip.main.settlement.service.support.SettlementSnapshotMapper
import com.togethertrip.main.settlement.service.support.SettlementTripAccessGuard
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

class SettlementServiceTest {

    private lateinit var settlementRepository: SettlementRepository
    private lateinit var settlementTransferRepository: SettlementTransferRepository
    private lateinit var settlementCalculationService: SettlementCalculationService
    private lateinit var settlementSnapshotMapper: SettlementSnapshotMapper
    private lateinit var settlementTripAccessGuard: SettlementTripAccessGuard
    private lateinit var tripRepository: TripRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var settlementService: SettlementService

    @BeforeEach
    fun setUp() {
        settlementRepository = mock(SettlementRepository::class.java)
        settlementTransferRepository = mock(SettlementTransferRepository::class.java)
        settlementCalculationService = mock(SettlementCalculationService::class.java)
        settlementSnapshotMapper = mock(SettlementSnapshotMapper::class.java)
        settlementTripAccessGuard = mock(SettlementTripAccessGuard::class.java)
        tripRepository = mock(TripRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        settlementService = SettlementService(
            settlementRepository = settlementRepository,
            settlementTransferRepository = settlementTransferRepository,
            settlementCalculationService = settlementCalculationService,
            settlementSnapshotMapper = settlementSnapshotMapper,
            settlementShareTokenGenerator = mock(SettlementShareTokenGenerator::class.java),
            settlementTripAccessGuard = settlementTripAccessGuard,
            tripRepository = tripRepository,
            tripParticipantRepository = tripParticipantRepository,
        )
    }

    @Test
    fun `정산 확정 응답은 저장된 송금 id를 반환한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val sender = createParticipant(
            id = 100L,
            trip = trip,
            user = owner,
            displayName = "보낼 사람",
        )
        val receiver = createParticipant(
            id = 200L,
            trip = trip,
            user = null,
            displayName = "받을 사람",
        )
        val calculation = createCalculation()
        val participants = createParticipantSnapshots()

        mockConfirmBase(
            user = owner,
            trip = trip,
            calculation = calculation,
            participants = participants,
        )
        `when`(tripParticipantRepository.getReferenceById(100L)).thenReturn(sender)
        `when`(tripParticipantRepository.getReferenceById(200L)).thenReturn(receiver)
        `when`(settlementRepository.saveAndFlush(any(Settlement::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Settlement).apply { id = 30L }
        }
        `when`(settlementTransferRepository.save(any(SettlementTransfer::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as SettlementTransfer).apply { id = 40L }
        }
        `when`(tripRepository.saveAndFlush(trip)).thenReturn(trip)
        `when`(settlementTransferRepository.findTransferRowsBySettlementId(30L)).thenReturn(
            listOf(transferRow(id = 40L))
        )

        val response = settlementService.confirmSettlement(
            userId = 1L,
            tripId = 10L,
        )

        assertEquals(40L, response.transfers.single().id)
    }

    @Test
    fun `confirmed 정산 unique 충돌은 비즈니스 예외로 변환한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val calculation = createCalculation()
        val participants = createParticipantSnapshots()

        mockConfirmBase(
            user = owner,
            trip = trip,
            calculation = calculation,
            participants = participants,
        )
        `when`(settlementRepository.saveAndFlush(any(Settlement::class.java))).thenThrow(
            DataIntegrityViolationException("duplicate")
        )

        val exception = assertBusinessException {
            settlementService.confirmSettlement(
                userId = 1L,
                tripId = 10L,
            )
        }

        assertEquals(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED, exception.errorCode)
    }

    @Test
    fun `Trip optimistic lock 충돌은 비즈니스 예외로 변환한다`() {
        val owner = createUser()
        val trip = createTrip(owner)
        val sender = createParticipant(
            id = 100L,
            trip = trip,
            user = owner,
            displayName = "보낼 사람",
        )
        val receiver = createParticipant(
            id = 200L,
            trip = trip,
            user = null,
            displayName = "받을 사람",
        )
        val calculation = createCalculation()
        val participants = createParticipantSnapshots()

        mockConfirmBase(
            user = owner,
            trip = trip,
            calculation = calculation,
            participants = participants,
        )
        `when`(tripParticipantRepository.getReferenceById(100L)).thenReturn(sender)
        `when`(tripParticipantRepository.getReferenceById(200L)).thenReturn(receiver)
        `when`(settlementRepository.saveAndFlush(any(Settlement::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Settlement).apply { id = 30L }
        }
        `when`(settlementTransferRepository.save(any(SettlementTransfer::class.java))).thenAnswer { invocation ->
            invocation.arguments[0] as SettlementTransfer
        }
        `when`(tripRepository.saveAndFlush(trip)).thenThrow(
            ObjectOptimisticLockingFailureException(Trip::class.java, 10L)
        )

        val exception = assertBusinessException {
            settlementService.confirmSettlement(
                userId = 1L,
                tripId = 10L,
            )
        }

        assertEquals(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED, exception.errorCode)
    }

    private fun mockConfirmBase(
        user: User,
        trip: Trip,
        calculation: SettlementCalculationResult,
        participants: Map<Long, SettlementParticipantSnapshot>,
    ) {
        `when`(settlementTripAccessGuard.getActiveUser(1L)).thenReturn(user)
        `when`(settlementTripAccessGuard.getOwnedTrip(1L, 10L)).thenReturn(trip)
        `when`(
            settlementRepository.findFirstByTripIdAndStatusAndDeletedAtIsNull(
                tripId = 10L,
                status = SettlementStatus.CONFIRMED,
            )
        ).thenReturn(null)
        `when`(settlementCalculationService.calculate(10L)).thenReturn(calculation)
        `when`(
            settlementCalculationService.getParticipantsById(
                tripId = 10L,
                calculation = calculation,
            )
        ).thenReturn(participants)
        val balances = listOf(
            SettlementParticipantBalanceResponse(
                participantId = 100L,
                userId = 1L,
                displayName = "보낼 사람",
                profileImageUrl = null,
                participantStatus = TripParticipantStatus.ACTIVE,
                paidAmount = BigDecimal("0.00"),
                shareAmount = BigDecimal("5000.00"),
                netAmount = BigDecimal("-5000.00"),
            )
        )
        `when`(
            settlementCalculationService.createBalanceResponses(
                balances = calculation.balances,
                participants = participants,
            )
        ).thenReturn(balances)
        `when`(settlementSnapshotMapper.write(balances)).thenReturn("{}")
    }

    private fun createCalculation(): SettlementCalculationResult {
        return SettlementCalculationResult(
            baseCurrency = "KRW",
            totalExpenseAmount = BigDecimal("10000.00"),
            totalShareAmount = BigDecimal("10000.00"),
            balances = listOf(
                SettlementParticipantBalance(
                    participantId = 100L,
                    paidAmount = BigDecimal("0.00"),
                    shareAmount = BigDecimal("5000.00"),
                    netAmount = BigDecimal("-5000.00"),
                ),
                SettlementParticipantBalance(
                    participantId = 200L,
                    paidAmount = BigDecimal("10000.00"),
                    shareAmount = BigDecimal("5000.00"),
                    netAmount = BigDecimal("5000.00"),
                ),
            ),
            transfers = listOf(
                SettlementTransferPlan(
                    senderParticipantId = 100L,
                    receiverParticipantId = 200L,
                    amount = BigDecimal("5000.00"),
                )
            ),
        )
    }

    private fun createParticipantSnapshots(): Map<Long, SettlementParticipantSnapshot> {
        return mapOf(
            100L to SettlementParticipantSnapshot(
                participantId = 100L,
                userId = 1L,
                displayName = "보낼 사람",
                profileImageUrl = null,
                participantStatus = TripParticipantStatus.ACTIVE,
                isWithdrawnUser = false,
            ),
            200L to SettlementParticipantSnapshot(
                participantId = 200L,
                userId = null,
                displayName = "받을 사람",
                profileImageUrl = null,
                participantStatus = TripParticipantStatus.ACTIVE,
                isWithdrawnUser = false,
            ),
        )
    }

    private fun createUser(): User {
        return User(nickname = "방장").apply {
            id = 1L
        }
    }

    private fun createTrip(owner: User): Trip {
        return Trip(
            ownerUser = owner,
            title = "일본 여행",
            defaultCurrency = "JPY",
        ).apply {
            id = 10L
        }
    }

    private fun createParticipant(
        id: Long,
        trip: Trip,
        user: User?,
        displayName: String,
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = displayName,
            participantRole = TripParticipantRole.MEMBER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            this.id = id
        }
    }

    private fun transferRow(id: Long): SettlementTransferRow {
        return object : SettlementTransferRow {
            override fun getId(): Long = id

            override fun getSenderParticipantId(): Long = 100L

            override fun getSenderDisplayName(): String = "보낼 사람"

            override fun getSenderUserStatus(): String = "ACTIVE"

            override fun getReceiverParticipantId(): Long = 200L

            override fun getReceiverDisplayName(): String = "받을 사람"

            override fun getReceiverUserStatus(): String? = null

            override fun getAmount(): BigDecimal = BigDecimal("5000.00")

            override fun getCurrency(): String = "KRW"

            override fun getStatus(): String = SettlementTransferStatus.PENDING.name

            override fun getSenderConfirmedAt(): Instant? = null

            override fun getReceiverConfirmedAt(): Instant? = null

            override fun getCompletedAt(): Instant? = null
        }
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        return try {
            block()
            throw AssertionError("BusinessException이 발생해야 합니다.")
        } catch (exception: BusinessException) {
            exception
        }
    }
}
