package com.togethertrip.main.transaction.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEvent
import com.togethertrip.main.transaction.domain.TransactionEventType
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.TransactionPaymentInput
import com.togethertrip.main.transaction.dto.request.TransactionShareInput
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.transaction.pagination.TransactionCursor
import com.togethertrip.main.transaction.repository.TransactionEventRepository
import com.togethertrip.main.transaction.repository.TransactionPaymentRepository
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.repository.TransactionShareRepository
import com.togethertrip.main.transaction.repository.TransactionStatisticsQueryRepository
import com.togethertrip.main.transaction.repository.projection.CommonFundBalanceRow
import com.togethertrip.main.transaction.repository.projection.TransactionStatisticsRow
import com.togethertrip.main.transaction.service.support.LinkedExpensePostSynchronizer
import com.togethertrip.main.transaction.service.support.TransactionAllocationWriter
import com.togethertrip.main.transaction.service.support.TransactionCreationService
import com.togethertrip.main.transaction.service.support.TransactionEventRecorder
import com.togethertrip.main.settlement.service.support.TripParticipantBalanceSummaryProjectionService
import com.togethertrip.main.exchange.domain.ExchangeRate
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantRole
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.exchange.repository.ExchangeRateRepository
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.trip.service.support.TripAccessResolver
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class TransactionServiceTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var transactionShareRepository: TransactionShareRepository
    private lateinit var transactionPaymentRepository: TransactionPaymentRepository
    private lateinit var transactionEventRepository: TransactionEventRepository
    private lateinit var transactionStatisticsQueryRepository: TransactionStatisticsQueryRepository
    private lateinit var postRepository: PostRepository
    private lateinit var tripRepository: TripRepository
    private lateinit var tripParticipantRepository: TripParticipantRepository
    private lateinit var exchangeRateRepository: ExchangeRateRepository
    private lateinit var userRepository: UserRepository
    private lateinit var balanceSummaryProjectionService: TripParticipantBalanceSummaryProjectionService
    private lateinit var transactionService: TransactionService
    private lateinit var clock: Clock

    @BeforeEach
    fun setUp() {
        transactionRepository = mock(TransactionRepository::class.java)
        transactionShareRepository = mock(TransactionShareRepository::class.java)
        transactionPaymentRepository = mock(TransactionPaymentRepository::class.java)
        transactionEventRepository = mock(TransactionEventRepository::class.java)
        transactionStatisticsQueryRepository = mock(TransactionStatisticsQueryRepository::class.java)
        postRepository = mock(PostRepository::class.java)
        tripRepository = mock(TripRepository::class.java)
        tripParticipantRepository = mock(TripParticipantRepository::class.java)
        exchangeRateRepository = mock(ExchangeRateRepository::class.java)
        userRepository = mock(UserRepository::class.java)
        balanceSummaryProjectionService = mock(TripParticipantBalanceSummaryProjectionService::class.java)
        clock = Clock.fixed(
            Instant.parse("2026-07-02T00:30:00Z"),
            ZoneId.of("Asia/Seoul"),
        )
        val transactionExchangeRateResolver = TransactionExchangeRateResolver(
            exchangeRateRepository = exchangeRateRepository,
            clock = clock,
        )
        val tripAccessResolver = TripAccessResolver(
            tripRepository = tripRepository,
            tripParticipantRepository = tripParticipantRepository,
            userRepository = userRepository,
        )
        val transactionEventRecorder = TransactionEventRecorder(
            transactionEventRepository = transactionEventRepository,
        )
        val transactionAllocationWriter = TransactionAllocationWriter(
            transactionPaymentRepository = transactionPaymentRepository,
            transactionShareRepository = transactionShareRepository,
            tripAccessResolver = tripAccessResolver,
        )
        val linkedExpensePostSynchronizer = LinkedExpensePostSynchronizer(
            postRepository = postRepository,
        )
        val transactionCreationService = TransactionCreationService(
            transactionRepository = transactionRepository,
            transactionExchangeRateResolver = transactionExchangeRateResolver,
            balanceSummaryProjectionService = balanceSummaryProjectionService,
            tripAccessResolver = tripAccessResolver,
            transactionEventRecorder = transactionEventRecorder,
            transactionAllocationWriter = transactionAllocationWriter,
            clock = clock,
        )
        transactionService = TransactionService(
            transactionRepository = transactionRepository,
            transactionShareRepository = transactionShareRepository,
            transactionPaymentRepository = transactionPaymentRepository,
            transactionEventRepository = transactionEventRepository,
            transactionStatisticsQueryRepository = transactionStatisticsQueryRepository,
            transactionExchangeRateResolver = transactionExchangeRateResolver,
            transactionCreationService = transactionCreationService,
            balanceSummaryProjectionService = balanceSummaryProjectionService,
            tripAccessResolver = tripAccessResolver,
            transactionEventRecorder = transactionEventRecorder,
            transactionAllocationWriter = transactionAllocationWriter,
            linkedExpensePostSynchronizer = linkedExpensePostSynchronizer,
            clock = clock,
        )
    }

    @Test
    fun `거래 등록 시 배치 환율 DB를 적용하고 이벤트를 기록한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val exchangeRate = createExchangeRate()
        val savedEvents = mutableListOf<TransactionEvent>()

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(exchangeRate)
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Transaction).apply { id = 300L }
        }
        `when`(transactionPaymentRepository.save(any(TransactionPayment::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionPayment).apply { id = 400L }
        }
        `when`(transactionShareRepository.save(any(TransactionShare::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionShare).apply { id = 500L }
        }
        `when`(transactionEventRepository.save(any(TransactionEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionEvent).apply {
                id = 600L
                savedEvents.add(this)
            }
        }

        val response = transactionService.createTransaction(
            userId = 1L,
            tripId = 10L,
            request = CreateTransactionRequest(
                amount = BigDecimal("1000.00"),
                currency = "jpy",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("1000.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("1000.00"),
                    )
                ),
            ),
        )

        assertEquals(BigDecimal("9.150000"), response.summary.exchangeRate)
        assertEquals(BigDecimal("9150.00"), response.summary.baseAmount)
        assertEquals(BigDecimal("9150.00"), response.payments.first().baseAmount)
        assertEquals(BigDecimal("9150.00"), response.shares.first().baseShareAmount)
        assertEquals(1L, trip.expenseVersion)
        assertEquals(TransactionEventType.CREATED, savedEvents.first().eventType)
    }

    @Test
    fun `거래 등록 시 소비일 기준 환율을 적용한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 6, 30),
            )
        ).thenReturn(
            createExchangeRate(
                rate = BigDecimal("8.800000"),
                rateDate = LocalDate.of(2026, 6, 30),
            )
        )
        mockTransactionSaves()

        val response = transactionService.createTransaction(
            userId = 1L,
            tripId = 10L,
            request = CreateTransactionRequest(
                amount = BigDecimal("1000.00"),
                currency = "JPY",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("1000.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("1000.00"),
                    )
                ),
                occurredAt = Instant.parse("2026-06-30T03:00:00Z"),
            ),
        )

        assertEquals(BigDecimal("8.800000"), response.summary.exchangeRate)
        assertEquals(BigDecimal("8800.00"), response.summary.baseAmount)
    }

    @Test
    fun `거래 등록 시 배분 환산 금액 합계를 거래 기준 금액에 맞춘다`() {
        val user = createUser()
        val trip = createTrip(user)
        val payer = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val shareParticipant = createParticipant(
            id = 101L,
            trip = trip,
            user = createUser().apply { id = 2L },
        )
        val savedPayments = mutableListOf<TransactionPayment>()
        val savedShares = mutableListOf<TransactionShare>()

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = payer,
        )
        mockActiveParticipant(
            trip = trip,
            participant = shareParticipant,
        )
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(createExchangeRate(rate = BigDecimal("9.500000")))
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Transaction).apply { id = 300L }
        }
        `when`(transactionPaymentRepository.save(any(TransactionPayment::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionPayment).apply {
                id = 400L + savedPayments.size
                savedPayments.add(this)
            }
        }
        `when`(transactionShareRepository.save(any(TransactionShare::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionShare).apply {
                id = 500L + savedShares.size
                savedShares.add(this)
            }
        }
        `when`(transactionEventRepository.save(any(TransactionEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionEvent).apply { id = 600L }
        }

        val response = transactionService.createTransaction(
            userId = 1L,
            tripId = 10L,
            request = CreateTransactionRequest(
                amount = BigDecimal("100.00"),
                currency = "JPY",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("100.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("33.33"),
                    ),
                    TransactionShareInput(
                        participantId = 101L,
                        shareAmount = BigDecimal("66.67"),
                    )
                ),
            ),
        )

        assertEquals(BigDecimal("950.00"), response.summary.baseAmount)
        assertEquals(BigDecimal("950.00"), savedPayments.fold(BigDecimal.ZERO) { total, payment -> total + payment.baseAmount })
        assertEquals(BigDecimal("950.00"), savedShares.fold(BigDecimal.ZERO) { total, share -> total + share.baseShareAmount })
    }

    @Test
    fun `KRW 거래 등록 시 환율 DB를 조회하지 않고 1대1 환율을 적용한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        mockTransactionSaves()

        val response = transactionService.createTransaction(
            userId = 1L,
            tripId = 10L,
            request = CreateTransactionRequest(
                amount = BigDecimal("12000.00"),
                currency = "krw",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("12000.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("12000.00"),
                    )
                ),
            ),
        )

        assertEquals("KRW", response.summary.currency)
        assertEquals("KRW", response.summary.baseCurrency)
        assertEquals(BigDecimal("1.000000"), response.summary.exchangeRate)
        assertEquals(BigDecimal("12000.00"), response.summary.baseAmount)
        verifyNoInteractions(exchangeRateRepository)
    }

    @Test
    fun `외화 거래 등록 시 환율 DB row가 없으면 실패한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(null)

        val exception = assertBusinessException {
            transactionService.createTransaction(
                userId = 1L,
                tripId = 10L,
                request = CreateTransactionRequest(
                    amount = BigDecimal("1000.00"),
                    currency = "JPY",
                    payments = listOf(
                        TransactionPaymentInput(
                            participantId = 100L,
                            amount = BigDecimal("1000.00"),
                        )
                    ),
                    shares = listOf(
                        TransactionShareInput(
                            participantId = 100L,
                            shareAmount = BigDecimal("1000.00"),
                        )
                    ),
                ),
            )
        }

        assertEquals(TransactionErrorCode.EXCHANGE_RATE_NOT_READY, exception.errorCode)
        verify(transactionRepository, never()).save(any(Transaction::class.java))
    }

    @Test
    fun `외화 거래 적용 환율을 미리 조회한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(createExchangeRate())

        val response = transactionService.getTransactionExchangeRatePreview(
            userId = 1L,
            tripId = 10L,
            currency = "jpy",
            spendingDate = LocalDate.of(2026, 7, 2),
        )

        assertEquals("KRW", response.baseCurrency)
        assertEquals("JPY", response.targetCurrency)
        assertEquals(BigDecimal("9.150000"), response.rate)
        assertEquals(LocalDate.of(2026, 7, 2), response.rateDate)
        assertEquals("TEST", response.source)
    }

    @Test
    fun `KRW 거래 적용 환율 미리보기는 환율 DB를 조회하지 않는다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )

        val response = transactionService.getTransactionExchangeRatePreview(
            userId = 1L,
            tripId = 10L,
            currency = "krw",
            spendingDate = null,
        )

        assertEquals("KRW", response.baseCurrency)
        assertEquals("KRW", response.targetCurrency)
        assertEquals(BigDecimal("1.000000"), response.rate)
        assertEquals(LocalDate.of(2026, 7, 2), response.rateDate)
        assertEquals("BASE_CURRENCY", response.source)
        verifyNoInteractions(exchangeRateRepository)
    }

    @Test
    fun `미래 소비일 환율 미리보기는 소비일 이하 최신 적재 환율을 조회한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 8, 10),
            )
        ).thenReturn(
            createExchangeRate(
                rate = BigDecimal("9.300000"),
                rateDate = LocalDate.of(2026, 7, 2),
            )
        )

        val response = transactionService.getTransactionExchangeRatePreview(
            userId = 1L,
            tripId = 10L,
            currency = "JPY",
            spendingDate = LocalDate.of(2026, 8, 10),
        )

        assertEquals(BigDecimal("9.300000"), response.rate)
        assertEquals(LocalDate.of(2026, 7, 2), response.rateDate)
    }

    @Test
    fun `거래 수정 시 수정 시점 환율을 다시 적용한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val transaction = createTransaction(
            trip = trip,
            user = user,
            id = 300L,
            createdAt = Instant.parse("2026-07-01T12:00:00Z"),
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(createExchangeRate(rate = BigDecimal("10.000000")))
        `when`(transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L))
            .thenReturn(emptyList())
        `when`(transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L))
            .thenReturn(emptyList())
        mockTransactionSaves()

        val response = transactionService.updateTransaction(
            userId = 1L,
            tripId = 10L,
            transactionId = 300L,
            request = UpdateTransactionRequest(
                amount = BigDecimal("1000.00"),
                currency = "JPY",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("1000.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("1000.00"),
                    )
                ),
            ),
        )

        assertEquals(BigDecimal("10.000000"), response.summary.exchangeRate)
        assertEquals(BigDecimal("10000.00"), response.summary.baseAmount)
        assertEquals(1L, trip.expenseVersion)
    }

    @Test
    fun `거래 수정 시 소비일 기준 환율을 다시 적용한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val transaction = createTransaction(
            trip = trip,
            user = user,
            id = 300L,
            createdAt = Instant.parse("2026-07-01T12:00:00Z"),
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 6, 30),
            )
        ).thenReturn(
            createExchangeRate(
                rate = BigDecimal("8.800000"),
                rateDate = LocalDate.of(2026, 6, 30),
            )
        )
        `when`(transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L))
            .thenReturn(emptyList())
        `when`(transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L))
            .thenReturn(emptyList())
        mockTransactionSaves()

        val response = transactionService.updateTransaction(
            userId = 1L,
            tripId = 10L,
            transactionId = 300L,
            request = UpdateTransactionRequest(
                amount = BigDecimal("1000.00"),
                currency = "JPY",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("1000.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("1000.00"),
                    )
                ),
                occurredAt = Instant.parse("2026-06-30T03:00:00Z"),
            ),
        )

        assertEquals(BigDecimal("8.800000"), response.summary.exchangeRate)
        assertEquals(BigDecimal("8800.00"), response.summary.baseAmount)
    }

    @Test
    fun `거래 수정 시 배분 환산 금액 합계를 거래 기준 금액에 맞춘다`() {
        val user = createUser()
        val trip = createTrip(user)
        val payer = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val shareParticipant = createParticipant(
            id = 101L,
            trip = trip,
            user = createUser().apply { id = 2L },
        )
        val transaction = createTransaction(
            trip = trip,
            user = user,
            id = 300L,
            createdAt = Instant.parse("2026-07-01T12:00:00Z"),
        )
        val savedShares = mutableListOf<TransactionShare>()

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = payer,
        )
        mockActiveParticipant(
            trip = trip,
            participant = shareParticipant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)
        `when`(
            exchangeRateRepository.findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
                baseCurrency = "KRW",
                targetCurrency = "JPY",
                rateDate = LocalDate.of(2026, 7, 2),
            )
        ).thenReturn(createExchangeRate(rate = BigDecimal("9.500000")))
        `when`(transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L))
            .thenReturn(emptyList())
        `when`(transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L))
            .thenReturn(emptyList())
        `when`(transactionPaymentRepository.save(any(TransactionPayment::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionPayment).apply { id = 400L }
        }
        `when`(transactionShareRepository.save(any(TransactionShare::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionShare).apply {
                id = 500L + savedShares.size
                savedShares.add(this)
            }
        }
        `when`(transactionEventRepository.save(any(TransactionEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionEvent).apply { id = 600L }
        }

        val response = transactionService.updateTransaction(
            userId = 1L,
            tripId = 10L,
            transactionId = 300L,
            request = UpdateTransactionRequest(
                amount = BigDecimal("100.00"),
                currency = "JPY",
                payments = listOf(
                    TransactionPaymentInput(
                        participantId = 100L,
                        amount = BigDecimal("100.00"),
                    )
                ),
                shares = listOf(
                    TransactionShareInput(
                        participantId = 100L,
                        shareAmount = BigDecimal("33.33"),
                    ),
                    TransactionShareInput(
                        participantId = 101L,
                        shareAmount = BigDecimal("66.67"),
                    )
                ),
            ),
        )

        assertEquals(BigDecimal("950.00"), response.summary.baseAmount)
        assertEquals(BigDecimal("950.00"), savedShares.fold(BigDecimal.ZERO) { total, share -> total + share.baseShareAmount })
    }

    @Test
    fun `거래 생성자가 아니면 거래 수정에 실패한다`() {
        val author = createUser()
        val otherUser = createUser().apply {
            id = 2L
            nickname = "민서"
        }
        val trip = createTrip(author)
        val otherParticipant = createParticipant(
            id = 101L,
            trip = trip,
            user = otherUser,
        )
        val transaction = createTransaction(
            trip = trip,
            user = author,
            id = 300L,
            createdAt = Instant.parse("2026-07-01T12:00:00Z"),
        )

        mockWritableTrip(
            user = otherUser,
            trip = trip,
            participant = otherParticipant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)

        val exception = assertBusinessException {
            transactionService.updateTransaction(
                userId = 2L,
                tripId = 10L,
                transactionId = 300L,
                request = UpdateTransactionRequest(
                    amount = BigDecimal("1000.00"),
                    currency = "JPY",
                    payments = listOf(
                        TransactionPaymentInput(
                            participantId = 101L,
                            amount = BigDecimal("1000.00"),
                        )
                    ),
                    shares = listOf(
                        TransactionShareInput(
                            participantId = 101L,
                            shareAmount = BigDecimal("1000.00"),
                        )
                    ),
                ),
            )
        }

        assertEquals(CommonErrorCode.ACCESS_DENIED, exception.errorCode)
        verifyNoInteractions(exchangeRateRepository)
        verify(transactionEventRepository, never()).save(any(TransactionEvent::class.java))
    }

    @Test
    fun `결제 금액 합계가 거래 금액과 다르면 거래 등록에 실패한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )

        val exception = assertBusinessException {
            transactionService.createTransaction(
                userId = 1L,
                tripId = 10L,
                request = CreateTransactionRequest(
                    amount = BigDecimal("1000.00"),
                    currency = "JPY",
                    payments = listOf(
                        TransactionPaymentInput(
                            participantId = 100L,
                            amount = BigDecimal("900.00"),
                        )
                    ),
                    shares = listOf(
                        TransactionShareInput(
                            participantId = 100L,
                            shareAmount = BigDecimal("1000.00"),
                        )
                    ),
                ),
            )
        }

        assertEquals(TransactionErrorCode.TRANSACTION_PAYMENT_TOTAL_MISMATCH, exception.errorCode)
        verify(transactionRepository, never()).save(any(Transaction::class.java))
    }

    @Test
    fun `동일 참여자가 결제자 목록에 중복 포함되면 거래 등록에 실패한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )

        val exception = assertBusinessException {
            transactionService.createTransaction(
                userId = 1L,
                tripId = 10L,
                request = CreateTransactionRequest(
                    amount = BigDecimal("1000.00"),
                    currency = "JPY",
                    payments = listOf(
                        TransactionPaymentInput(
                            participantId = 100L,
                            amount = BigDecimal("600.00"),
                        ),
                        TransactionPaymentInput(
                            participantId = 100L,
                            amount = BigDecimal("400.00"),
                        )
                    ),
                    shares = listOf(
                        TransactionShareInput(
                            participantId = 100L,
                            shareAmount = BigDecimal("1000.00"),
                        )
                    ),
                ),
            )
        }

        assertEquals(TransactionErrorCode.DUPLICATE_TRANSACTION_PARTICIPANT, exception.errorCode)
        verify(transactionRepository, never()).save(any(Transaction::class.java))
    }

    @Test
    fun `정산 시작 이후에는 거래 등록에 실패한다`() {
        val user = createUser()
        val trip = createTrip(user).apply {
            settlementStatus = TripSettlementStatus.IN_PROGRESS
        }
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )

        val exception = assertBusinessException {
            transactionService.createTransaction(
                userId = 1L,
                tripId = 10L,
                request = CreateTransactionRequest(
                    amount = BigDecimal("1000.00"),
                    currency = "JPY",
                    payments = listOf(
                        TransactionPaymentInput(
                            participantId = 100L,
                            amount = BigDecimal("1000.00"),
                        )
                    ),
                    shares = listOf(
                        TransactionShareInput(
                            participantId = 100L,
                            shareAmount = BigDecimal("1000.00"),
                        )
                    ),
                ),
            )
        }

        assertEquals(TransactionErrorCode.TRANSACTION_LOCKED_BY_SETTLEMENT, exception.errorCode)
        verify(transactionRepository, never()).save(any(Transaction::class.java))
    }

    @Test
    fun `거래 삭제는 물리 삭제가 아니라 무효 상태로 변경한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val transaction = Transaction(
            trip = trip,
            createdBy = user,
            amount = BigDecimal("1000.00"),
            currency = "JPY",
            exchangeRate = BigDecimal("9.150000"),
            baseCurrency = "KRW",
            baseAmount = BigDecimal("9150.00"),
            transactionType = TransactionType.EXPENSE,
        ).apply {
            id = 300L
        }
        val savedEvents = mutableListOf<TransactionEvent>()

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)
        `when`(transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L)).thenReturn(emptyList())
        `when`(transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(300L)).thenReturn(emptyList())
        `when`(transactionEventRepository.save(any(TransactionEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionEvent).apply {
                id = 600L
                savedEvents.add(this)
            }
        }

        transactionService.deleteTransaction(
            userId = 1L,
            tripId = 10L,
            transactionId = 300L,
        )

        assertEquals(TransactionStatus.VOIDED, transaction.status)
        assertEquals(null, transaction.deletedAt)
        assertEquals(1L, trip.expenseVersion)
        assertEquals(TransactionEventType.VOIDED, savedEvents.first().eventType)
    }

    @Test
    fun `무효 처리된 거래는 수정할 수 없다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val transaction = createTransaction(
            trip = trip,
            user = user,
            id = 300L,
            createdAt = Instant.parse("2026-07-01T12:00:00Z"),
        ).apply {
            void()
        }

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)

        val exception = assertBusinessException {
            transactionService.updateTransaction(
                userId = 1L,
                tripId = 10L,
                transactionId = 300L,
                request = UpdateTransactionRequest(
                    amount = BigDecimal("1000.00"),
                    currency = "JPY",
                    payments = listOf(
                        TransactionPaymentInput(
                            participantId = 100L,
                            amount = BigDecimal("1000.00"),
                        )
                    ),
                    shares = listOf(
                        TransactionShareInput(
                            participantId = 100L,
                            shareAmount = BigDecimal("1000.00"),
                        )
                    ),
                ),
            )
        }

        assertEquals(TransactionErrorCode.TRANSACTION_ALREADY_VOIDED, exception.errorCode)
        assertEquals(0L, trip.expenseVersion)
        verifyNoInteractions(exchangeRateRepository)
        verify(transactionEventRepository, never()).save(any(TransactionEvent::class.java))
    }

    @Test
    fun `무효 처리된 거래는 다시 삭제할 수 없다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )
        val transaction = createTransaction(
            trip = trip,
            user = user,
            id = 300L,
            createdAt = Instant.parse("2026-07-01T12:00:00Z"),
        ).apply {
            void()
        }

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)

        val exception = assertBusinessException {
            transactionService.deleteTransaction(
                userId = 1L,
                tripId = 10L,
                transactionId = 300L,
            )
        }

        assertEquals(TransactionErrorCode.TRANSACTION_ALREADY_VOIDED, exception.errorCode)
        assertEquals(0L, trip.expenseVersion)
        verify(transactionEventRepository, never()).save(any(TransactionEvent::class.java))
    }

    @Test
    fun `거래 생성자가 아니면 거래 삭제에 실패한다`() {
        val author = createUser()
        val otherUser = createUser().apply {
            id = 2L
            nickname = "민서"
        }
        val trip = createTrip(author)
        val otherParticipant = createParticipant(
            id = 101L,
            trip = trip,
            user = otherUser,
        )
        val transaction = createTransaction(
            trip = trip,
            user = author,
            id = 300L,
            createdAt = Instant.parse("2026-07-01T12:00:00Z"),
        )

        mockWritableTrip(
            user = otherUser,
            trip = trip,
            participant = otherParticipant,
        )
        `when`(transactionRepository.findByIdAndDeletedAtIsNull(300L)).thenReturn(transaction)

        val exception = assertBusinessException {
            transactionService.deleteTransaction(
                userId = 2L,
                tripId = 10L,
                transactionId = 300L,
            )
        }

        assertEquals(CommonErrorCode.ACCESS_DENIED, exception.errorCode)
        assertEquals(TransactionStatus.ACTIVE, transaction.status)
        verify(transactionEventRepository, never()).save(any(TransactionEvent::class.java))
    }

    @Test
    fun `거래 목록은 cursor 기반으로 조회한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val firstTransaction = createTransaction(
            trip = trip,
            user = user,
            id = 300L,
            createdAt = Instant.parse("2026-07-02T10:00:00Z"),
        )
        val secondTransaction = createTransaction(
            trip = trip,
            user = user,
            id = 299L,
            createdAt = Instant.parse("2026-07-02T09:00:00Z"),
        )
        val extraTransaction = createTransaction(
            trip = trip,
            user = user,
            id = 298L,
            createdAt = Instant.parse("2026-07-02T08:00:00Z"),
        )
        val cursor = TransactionCursor(
            createdAt = Instant.parse("2026-07-02T11:00:00Z"),
            id = 301L,
        ).encode()

        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(trip)
        `when`(
            transactionRepository.findTransactions(
                tripId = 10L,
                status = TransactionStatus.ACTIVE,
                transactionType = null,
                transactionTypeFilterEnabled = false,
                participantId = null,
                participantFilterEnabled = false,
                cursorCreatedAt = Instant.parse("2026-07-02T11:00:00Z"),
                cursorId = 301L,
                cursorFilterEnabled = true,
                pageable = PageRequest.of(0, 3),
            )
        ).thenReturn(listOf(firstTransaction, secondTransaction, extraTransaction))

        val response = transactionService.getTransactions(
            userId = 1L,
            tripId = 10L,
            type = null,
            participantId = null,
            cursor = cursor,
            size = 2,
        )

        assertEquals(2, response.items.size)
        assertEquals(true, response.hasNext)
        val nextCursor = TransactionCursor.decode(response.nextCursor ?: error("nextCursor가 있어야 합니다."))
        assertEquals(Instant.parse("2026-07-02T09:00:00Z"), nextCursor.createdAt)
        assertEquals(299L, nextCursor.id)
    }

    @Test
    fun `공동경비 잔액은 충전 합계에서 사용 합계를 차감한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            transactionStatisticsQueryRepository.findCommonFundBalance(
                tripId = 10L,
                status = TransactionStatus.ACTIVE,
            )
        ).thenReturn(
            CommonFundBalanceRow(
                baseCurrency = "KRW",
                chargedBaseAmount = BigDecimal("150000.00"),
                usedBaseAmount = BigDecimal("43000.00"),
            )
        )

        val response = transactionService.getCommonFundBalance(
            userId = 1L,
            tripId = 10L,
        )

        assertEquals(10L, response.tripId)
        assertEquals("KRW", response.baseCurrency)
        assertEquals(BigDecimal("150000.00"), response.chargedBaseAmount)
        assertEquals(BigDecimal("43000.00"), response.usedBaseAmount)
        assertEquals(BigDecimal("107000.00"), response.balanceBaseAmount)
    }

    @Test
    fun `공동경비 거래가 없으면 여행 기본 통화를 사용한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            transactionStatisticsQueryRepository.findCommonFundBalance(
                tripId = 10L,
                status = TransactionStatus.ACTIVE,
            )
        ).thenReturn(
            CommonFundBalanceRow(
                baseCurrency = null,
                chargedBaseAmount = BigDecimal.ZERO,
                usedBaseAmount = BigDecimal.ZERO,
            )
        )

        val response = transactionService.getCommonFundBalance(
            userId = 1L,
            tripId = 10L,
        )

        assertEquals("KRW", response.baseCurrency)
        assertEquals(BigDecimal.ZERO, response.balanceBaseAmount)
    }

    @Test
    fun `거래 통계는 기본 groupBy type으로 조회한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            transactionStatisticsQueryRepository.findTypeStatistics(
                tripId = 10L,
                from = null,
                toExclusive = null,
                status = TransactionStatus.ACTIVE,
            )
        ).thenReturn(
            listOf(
                TransactionStatisticsRow(
                    key = "EXPENSE",
                    label = "EXPENSE",
                    transactionCount = 2L,
                    totalBaseAmount = BigDecimal("32000.00"),
                ),
                TransactionStatisticsRow(
                    key = "FUND_USE",
                    label = "FUND_USE",
                    transactionCount = 1L,
                    totalBaseAmount = BigDecimal("12000.00"),
                ),
            )
        )

        val response = transactionService.getTransactionStatistics(
            userId = 1L,
            tripId = 10L,
            from = null,
            to = null,
            groupBy = null,
        )

        assertEquals("type", response.groupBy)
        assertEquals(BigDecimal("44000.00"), response.totalBaseAmount)
        assertEquals(2, response.items.size)
        assertEquals("EXPENSE", response.items.first().key)
    }

    @Test
    fun `거래 통계는 카테고리와 기간 필터로 조회한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            transactionStatisticsQueryRepository.findCategoryStatistics(
                tripId = 10L,
                from = Instant.parse("2026-07-01T15:00:00Z"),
                toExclusive = Instant.parse("2026-07-05T15:00:00Z"),
                status = TransactionStatus.ACTIVE,
            )
        ).thenReturn(
            listOf(
                TransactionStatisticsRow(
                    key = "FOOD",
                    label = "FOOD",
                    transactionCount = 3L,
                    totalBaseAmount = BigDecimal("56000.00"),
                )
            )
        )

        val response = transactionService.getTransactionStatistics(
            userId = 1L,
            tripId = 10L,
            from = "2026-07-02",
            to = "2026-07-05",
            groupBy = "category",
        )

        assertEquals("category", response.groupBy)
        assertEquals(LocalDate.of(2026, 7, 2), response.from)
        assertEquals(LocalDate.of(2026, 7, 5), response.to)
        assertEquals("FOOD", response.items.first().label)
    }

    @Test
    fun `거래 통계는 참여자 부담 금액 기준으로 조회한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )
        `when`(
            transactionStatisticsQueryRepository.findParticipantShareStatistics(
                tripId = 10L,
                from = null,
                toExclusive = null,
                status = TransactionStatus.ACTIVE,
            )
        ).thenReturn(
            listOf(
                TransactionStatisticsRow(
                    key = "100",
                    label = "재완",
                    transactionCount = 2L,
                    totalBaseAmount = BigDecimal("28000.00"),
                )
            )
        )

        val response = transactionService.getTransactionStatistics(
            userId = 1L,
            tripId = 10L,
            from = null,
            to = null,
            groupBy = "participant",
        )

        assertEquals("participant", response.groupBy)
        assertEquals("100", response.items.first().key)
        assertEquals(BigDecimal("28000.00"), response.totalBaseAmount)
    }

    @Test
    fun `거래 통계 groupBy가 지원 값이 아니면 실패한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )

        val exception = assertBusinessException {
            transactionService.getTransactionStatistics(
                userId = 1L,
                tripId = 10L,
                from = null,
                to = null,
                groupBy = "payer",
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `거래 통계 시작일이 종료일보다 늦으면 실패한다`() {
        val user = createUser()
        val trip = createTrip(user)
        val participant = createParticipant(
            id = 100L,
            trip = trip,
            user = user,
        )

        mockWritableTrip(
            user = user,
            trip = trip,
            participant = participant,
        )

        val exception = assertBusinessException {
            transactionService.getTransactionStatistics(
                userId = 1L,
                tripId = 10L,
                from = "2026-07-06",
                to = "2026-07-05",
                groupBy = "type",
            )
        }

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.errorCode)
    }

    private fun mockWritableTrip(
        user: User,
        trip: Trip,
        participant: TripParticipant,
    ) {
        `when`(userRepository.findByIdAndDeletedAtIsNull(user.id)).thenReturn(user)
        `when`(tripRepository.findByIdAndDeletedAtIsNull(trip.id)).thenReturn(trip)
        `when`(tripRepository.findByIdAndDeletedAtIsNullForUpdate(trip.id)).thenReturn(trip)
        `when`(
            tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = trip.id,
                userId = user.id,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                id = participant.id,
                tripId = trip.id,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
    }

    private fun mockActiveParticipant(
        trip: Trip,
        participant: TripParticipant,
    ) {
        `when`(
            tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                id = participant.id,
                tripId = trip.id,
                participantStatus = TripParticipantStatus.ACTIVE,
            )
        ).thenReturn(participant)
    }

    private fun mockTransactionSaves() {
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Transaction).apply { id = 300L }
        }
        `when`(transactionPaymentRepository.save(any(TransactionPayment::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionPayment).apply { id = 400L }
        }
        `when`(transactionShareRepository.save(any(TransactionShare::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionShare).apply { id = 500L }
        }
        `when`(transactionEventRepository.save(any(TransactionEvent::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionEvent).apply { id = 600L }
        }
    }

    private fun createUser(): User {
        return User(
            nickname = "재완",
            profileImageUrl = null,
        ).apply {
            id = 1L
        }
    }

    private fun createTrip(owner: User): Trip {
        return Trip(
            ownerUser = owner,
            title = "일본 여행",
            defaultCurrency = "KRW",
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 5),
        ).apply {
            id = 10L
        }
    }

    private fun createParticipant(
        id: Long,
        trip: Trip,
        user: User,
    ): TripParticipant {
        return TripParticipant(
            trip = trip,
            user = user,
            displayName = user.nickname,
            participantRole = TripParticipantRole.LEADER,
            participantStatus = TripParticipantStatus.ACTIVE,
        ).apply {
            this.id = id
        }
    }

    private fun createExchangeRate(
        rate: BigDecimal = BigDecimal("9.150000"),
        rateDate: LocalDate = LocalDate.of(2026, 7, 2),
    ): ExchangeRate {
        return ExchangeRate(
            baseCurrency = "KRW",
            targetCurrency = "JPY",
            rate = rate,
            rateDate = rateDate,
            source = "TEST",
        ).apply {
            id = 200L
        }
    }

    private fun createTransaction(
        trip: Trip,
        user: User,
        id: Long,
        createdAt: Instant,
    ): Transaction {
        return Transaction(
            trip = trip,
            createdBy = user,
            amount = BigDecimal("1000.00"),
            currency = "JPY",
            exchangeRate = BigDecimal("9.150000"),
            baseCurrency = "KRW",
            baseAmount = BigDecimal("9150.00"),
            transactionType = TransactionType.EXPENSE,
        ).apply {
            this.id = id
            this.createdAt = createdAt
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
