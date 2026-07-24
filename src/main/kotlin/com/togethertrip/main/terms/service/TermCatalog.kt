package com.togethertrip.main.terms.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.terms.exception.TermsErrorCode
import com.togethertrip.main.user.domain.UserAgreementType
import org.springframework.stereotype.Component

@Component
class TermCatalog {

    fun getTerms(): List<TermDefinition> = TERMS

    fun getTerm(code: UserAgreementType): TermDefinition {
        return TERMS_BY_CODE[code]
            ?: throw BusinessException(TermsErrorCode.TERM_NOT_FOUND)
    }

    fun requiredTerms(): List<TermDefinition> {
        return TERMS.filter { it.required }
    }

    companion object {
        private const val CURRENT_VERSION = "2026-06-18"

        private val TERMS = listOf(
            TermDefinition(
                code = UserAgreementType.SERVICE_TERMS,
                title = "서비스 이용약관",
                required = true,
                version = CURRENT_VERSION,
                content = """
                    제1조 목적
                    이 약관은 투게더트립이 제공하는 여행방, 여행 기록, 지출 기록, 정산 보조 서비스의 이용 조건과 절차, 회사와 회원의 권리와 의무를 정합니다.

                    제2조 서비스의 제공
                    회원은 여행방을 만들고 동행자를 초대하며, 여행 중 발생한 지출과 기록을 입력하고 정산 내역을 확인할 수 있습니다. 회사는 안정적인 서비스 제공을 위해 기능을 변경하거나 점검할 수 있으며, 중요한 변경은 서비스 내 공지 등 합리적인 방법으로 알립니다.

                    제3조 회원의 의무
                    회원은 본인의 정보와 인증 수단을 안전하게 관리해야 하며, 타인의 정보를 도용하거나 허위 지출, 부정확한 정산 정보를 고의로 입력해서는 안 됩니다. 회원이 입력한 여행 기록과 지출 정보의 정확성에 대한 1차 책임은 회원에게 있습니다.

                    제4조 정산의 성격
                    투게더트립의 정산 기능은 회원이 입력한 정보를 바탕으로 금액을 계산해 보여주는 보조 도구입니다. 실제 금전 지급, 송금, 환불, 분쟁 해결은 여행 참여자 사이에서 직접 처리해야 합니다.

                    제5조 서비스 이용 제한
                    회사는 법령 또는 이 약관을 위반하거나 다른 회원에게 피해를 주는 이용에 대해 서비스 이용을 제한할 수 있습니다.
                """.trimIndent(),
            ),
            TermDefinition(
                code = UserAgreementType.PRIVACY_POLICY,
                title = "개인정보 처리방침",
                required = true,
                version = CURRENT_VERSION,
                content = """
                    1. 수집하는 개인정보
                    투게더트립은 회원가입과 서비스 제공을 위해 소셜 로그인 식별자, 닉네임, 프로필 이미지, 성별, 생년월일, 서비스 이용 기록을 처리할 수 있습니다.

                    2. 개인정보 이용 목적
                    수집한 정보는 회원 식별, 로그인과 인증, 여행방 참여자 표시, 지출 및 정산 기능 제공, 고객 문의 대응, 부정 이용 방지, 서비스 안정성 개선에 사용됩니다.

                    3. 보관 및 파기
                    개인정보는 회원 탈퇴 또는 처리 목적 달성 시 지체 없이 파기합니다. 다만 관계 법령에 따라 보관이 필요한 정보는 정해진 기간 동안 분리 보관합니다.

                    4. 제3자 제공 및 처리 위탁
                    회사는 법령에 근거가 있거나 회원의 동의가 있는 경우를 제외하고 개인정보를 외부에 제공하지 않습니다. 서비스 운영을 위해 필요한 처리 위탁이 발생하는 경우 위탁받는 자와 업무 내용을 고지합니다.

                    5. 회원의 권리
                    회원은 본인의 개인정보 열람, 정정, 삭제, 처리 정지를 요청할 수 있습니다. 문의는 앱 내 고객지원 채널을 통해 접수할 수 있습니다.
                """.trimIndent(),
            ),
            TermDefinition(
                code = UserAgreementType.LOCATION_INFO_TERMS,
                title = "위치기반서비스 이용약관",
                required = true,
                version = CURRENT_VERSION,
                content = """
                    제1조 목적
                    이 약관은 투게더트립이 여행 기록과 장소 기반 기능을 제공하기 위해 위치정보를 이용하는 조건과 절차를 정합니다.

                    제2조 위치정보의 이용
                    회사는 회원이 여행 기록에 장소를 추가하거나 현재 위치 기반 편의 기능을 사용하는 경우 위치정보 또는 장소 정보를 이용할 수 있습니다. 위치정보는 여행 기록 작성, 장소 표시, 여행 동선 확인 등 회원이 요청한 기능 제공을 위해 사용됩니다.

                    제3조 보관 및 이용 기간
                    위치정보 또는 장소 정보는 회원이 작성한 여행 기록과 함께 보관될 수 있으며, 회원이 기록을 삭제하거나 회원 탈퇴 시 관련 법령에 따라 삭제 또는 분리 보관됩니다.

                    제4조 회원의 권리
                    회원은 위치정보 이용 동의를 철회할 수 있으나, 필수 가입 약관으로 동의가 필요한 서비스 범위에서는 동의 철회 시 일부 기능 이용이 제한될 수 있습니다.

                    제5조 문의
                    위치정보 이용과 관련한 문의는 앱 내 고객지원 채널을 통해 접수할 수 있습니다.
                """.trimIndent(),
            ),
            TermDefinition(
                code = UserAgreementType.MARKETING_CONSENT,
                title = "광고성 정보 수신 동의",
                required = false,
                version = CURRENT_VERSION,
                content = """
                    투게더트립은 회원이 동의한 경우 서비스 소식, 이벤트, 혜택, 프로모션, 여행 관련 콘텐츠 등 광고성 정보를 알림, 문자, 이메일 등 서비스가 제공하는 채널로 발송할 수 있습니다.

                    광고성 정보 수신 동의는 선택 사항이며, 동의하지 않아도 회원가입과 기본 서비스 이용에는 제한이 없습니다. 회원은 마이페이지에서 언제든지 수신 동의를 변경하거나 철회할 수 있습니다.

                    수신 동의를 철회하면 철회 시점 이후 광고성 정보 발송 대상에서 제외됩니다. 다만 법령상 고지 의무가 있거나 서비스 이용과 직접 관련된 안내는 수신 동의 여부와 관계없이 발송될 수 있습니다.
                """.trimIndent(),
            ),
        )

        private val TERMS_BY_CODE = TERMS.associateBy { it.code }
    }
}

data class TermDefinition(
    val code: UserAgreementType,
    val title: String,
    val required: Boolean,
    val version: String,
    val content: String,
)
