package com.togethertrip.main.terms.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.terms.dto.request.SaveTermAgreementsRequest
import com.togethertrip.main.terms.dto.request.TermAgreementRequest
import com.togethertrip.main.terms.dto.request.UpdateTermAgreementRequest
import com.togethertrip.main.terms.exception.TermsErrorCode
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserAgreement
import com.togethertrip.main.user.domain.UserAgreementType
import com.togethertrip.main.user.repository.UserAgreementRepository
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class TermsServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userAgreementRepository: UserAgreementRepository
    private lateinit var termsService: TermsService

    private val clock = Clock.fixed(
        Instant.parse("2026-06-18T10:15:30Z"),
        ZoneOffset.UTC,
    )

    @BeforeEach
    fun setUp() {
        userRepository = mock(UserRepository::class.java)
        userAgreementRepository = mock(UserAgreementRepository::class.java)
        termsService = TermsService(
            termCatalog = TermCatalog(),
            userRepository = userRepository,
            userAgreementRepository = userAgreementRepository,
            clock = clock,
        )
    }

    @Test
    fun `약관 목록을 조회한다`() {
        val response = termsService.getTerms()

        assertEquals(4, response.size)
        assertEquals(UserAgreementType.SERVICE_TERMS, response[0].code)
        assertTrue(response.first { it.code == UserAgreementType.LOCATION_INFO_TERMS }.required)
        assertFalse(response.first { it.code == UserAgreementType.MARKETING_CONSENT }.required)
    }

    @Test
    fun `필수 약관과 선택 약관 동의를 저장한다`() {
        val user = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        UserAgreementType.entries.forEach { code ->
            `when`(
                userAgreementRepository.findByUserIdAndAgreementTypeAndDeletedAtIsNull(
                    userId = 1L,
                    agreementType = code,
                )
            ).thenReturn(null)
        }
        `when`(userAgreementRepository.findAllByUserIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())

        termsService.saveMyAgreements(
            userId = 1L,
            request = SaveTermAgreementsRequest(
                agreements = listOf(
                    agreement(UserAgreementType.SERVICE_TERMS, agreed = true),
                    agreement(UserAgreementType.PRIVACY_POLICY, agreed = true),
                    agreement(UserAgreementType.LOCATION_INFO_TERMS, agreed = true),
                    agreement(UserAgreementType.MARKETING_CONSENT, agreed = false),
                )
            ),
        )

        val captor = ArgumentCaptor.forClass(UserAgreement::class.java)
        verify(userAgreementRepository, org.mockito.Mockito.times(4)).save(captor.capture())
        val saved = captor.allValues.associateBy { it.agreementType }

        assertTrue(saved.getValue(UserAgreementType.SERVICE_TERMS).agreed)
        assertFalse(saved.getValue(UserAgreementType.MARKETING_CONSENT).agreed)
        assertEquals("2026-06-18", saved.getValue(UserAgreementType.SERVICE_TERMS).termVersion)
        assertEquals(Instant.parse("2026-06-18T10:15:30Z"), saved.getValue(UserAgreementType.MARKETING_CONSENT).revokedAt)
    }

    @Test
    fun `선택 약관은 누락되어도 필수 약관만 있으면 저장한다`() {
        val user = createUser()
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        listOf(
            UserAgreementType.SERVICE_TERMS,
            UserAgreementType.PRIVACY_POLICY,
            UserAgreementType.LOCATION_INFO_TERMS,
        ).forEach { code ->
            `when`(
                userAgreementRepository.findByUserIdAndAgreementTypeAndDeletedAtIsNull(
                    userId = 1L,
                    agreementType = code,
                )
            ).thenReturn(null)
        }
        `when`(userAgreementRepository.findAllByUserIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())

        termsService.saveMyAgreements(
            userId = 1L,
            request = SaveTermAgreementsRequest(
                agreements = listOf(
                    agreement(UserAgreementType.SERVICE_TERMS, agreed = true),
                    agreement(UserAgreementType.PRIVACY_POLICY, agreed = true),
                    agreement(UserAgreementType.LOCATION_INFO_TERMS, agreed = true),
                )
            ),
        )

        verify(userAgreementRepository, org.mockito.Mockito.times(3)).save(org.mockito.Mockito.any(UserAgreement::class.java))
    }

    @Test
    fun `필수 약관이 누락되면 저장에 실패한다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(createUser())

        val exception = assertBusinessException {
            termsService.saveMyAgreements(
                userId = 1L,
                request = SaveTermAgreementsRequest(
                    agreements = listOf(
                        agreement(UserAgreementType.SERVICE_TERMS, agreed = true),
                        agreement(UserAgreementType.PRIVACY_POLICY, agreed = true),
                    )
                ),
            )
        }

        assertEquals(TermsErrorCode.REQUIRED_TERM_MISSING, exception.errorCode)
    }

    @Test
    fun `약관 버전이 다르면 저장에 실패한다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(createUser())

        val exception = assertBusinessException {
            termsService.saveMyAgreements(
                userId = 1L,
                request = SaveTermAgreementsRequest(
                    agreements = listOf(
                        agreement(UserAgreementType.SERVICE_TERMS, agreed = true),
                        agreement(UserAgreementType.PRIVACY_POLICY, agreed = true),
                        agreement(UserAgreementType.LOCATION_INFO_TERMS, agreed = true, version = "old"),
                    )
                ),
            )
        }

        assertEquals(TermsErrorCode.TERM_VERSION_MISMATCH, exception.errorCode)
    }

    @Test
    fun `마이페이지에서 선택 약관을 철회한다`() {
        val user = createUser()
        val marketingAgreement = UserAgreement(
            user = user,
            agreementType = UserAgreementType.MARKETING_CONSENT,
            agreed = true,
            termVersion = "2026-06-18",
            agreedAt = Instant.parse("2026-06-18T00:00:00Z"),
        )
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(user)
        `when`(
            userAgreementRepository.findByUserIdAndAgreementTypeAndDeletedAtIsNull(
                userId = 1L,
                agreementType = UserAgreementType.MARKETING_CONSENT,
            )
        ).thenReturn(marketingAgreement)
        `when`(userAgreementRepository.findAllByUserIdAndDeletedAtIsNull(1L)).thenReturn(listOf(marketingAgreement))

        val response = termsService.updateMyAgreement(
            userId = 1L,
            code = UserAgreementType.MARKETING_CONSENT,
            request = UpdateTermAgreementRequest(
                version = "2026-06-18",
                agreed = false,
            ),
        )

        assertFalse(marketingAgreement.agreed)
        assertEquals(Instant.parse("2026-06-18T10:15:30Z"), marketingAgreement.revokedAt)
        assertFalse(response.agreements.first { it.code == UserAgreementType.MARKETING_CONSENT }.agreed)
    }

    @Test
    fun `마이페이지에서 필수 약관은 철회할 수 없다`() {
        `when`(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(createUser())

        val exception = assertBusinessException {
            termsService.updateMyAgreement(
                userId = 1L,
                code = UserAgreementType.SERVICE_TERMS,
                request = UpdateTermAgreementRequest(
                    version = "2026-06-18",
                    agreed = false,
                ),
            )
        }

        assertEquals(TermsErrorCode.REQUIRED_TERM_WITHDRAW_NOT_ALLOWED, exception.errorCode)
    }

    private fun agreement(
        code: UserAgreementType,
        agreed: Boolean,
        version: String = "2026-06-18",
    ): TermAgreementRequest {
        return TermAgreementRequest(
            code = code,
            version = version,
            agreed = agreed,
        )
    }

    private fun createUser(): User {
        return User(
            nickname = "재완",
        ).apply {
            id = 1L
        }
    }

    private fun assertBusinessException(block: () -> Unit): BusinessException {
        try {
            block()
        } catch (exception: BusinessException) {
            return exception
        }

        fail("BusinessException이 발생해야 합니다.")
    }
}
