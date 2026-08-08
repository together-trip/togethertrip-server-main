package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class SettlementShareTokenIssuer(
    private val settlementRepository: SettlementRepository,
    private val settlementShareTokenGenerator: SettlementShareTokenGenerator,
    private val clock: Clock,
) {

    fun issue(settlement: Settlement): String {
        settlement.shareToken?.let { token -> return token }

        val generatedToken = settlementShareTokenGenerator.generate()
        val updatedCount = settlementRepository.updateShareTokenIfAbsent(
            settlementId = settlement.id,
            shareToken = generatedToken,
            updatedAt = Instant.now(clock),
        )

        if (updatedCount > 0) {
            return generatedToken
        }

        return readLatestToken(settlement.id)
    }

    // 유출된 공유 링크를 무효화하는 유일한 수단이다. 기존 토큰 유무와 무관하게 새 토큰을 발급한다.
    fun rotate(settlement: Settlement): String {
        val generatedToken = settlementShareTokenGenerator.generate()
        val updatedCount = settlementRepository.rotateShareToken(
            settlementId = settlement.id,
            shareToken = generatedToken,
            updatedAt = Instant.now(clock),
        )

        if (updatedCount == 0) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND)
        }

        return generatedToken
    }

    private fun readLatestToken(settlementId: Long): String {
        return settlementRepository.findByIdAndDeletedAtIsNull(settlementId)?.shareToken
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_SHARE_TOKEN_NOT_FOUND)
    }
}
