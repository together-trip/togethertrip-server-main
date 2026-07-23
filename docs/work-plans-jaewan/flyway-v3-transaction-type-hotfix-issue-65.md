# Flyway V3 거래 유형 Constraint Hotfix

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/65
- 작업 브랜치: `hotfix/issue-65-flyway-transaction-type-check`
- 기준 브랜치: `develop`

## 배경

Flyway history를 비운 뒤 기존 데이터가 남아 있는 개발 DB에 마이그레이션을 다시 적용하면, V3에서 `transactions.transaction_type`을 `EXPENSE`만 허용하도록 constraint를 추가한다. 현재 도메인 enum과 V11 최종 constraint는 `FUND_CHARGE`, `FUND_USE`도 허용하므로, 기존 거래 데이터에 공동경비 유형이 있으면 V3 적용이 실패한다.

## 구현 범위

- V3 `transactions_transaction_type_check` 허용값을 현재 `TransactionType` enum과 정렬한다.
- V11은 유지하여 최종 constraint 보강 migration 역할을 그대로 둔다.
- API 동작과 도메인 정책은 변경하지 않는다.

## 제외 범위

- 운영 DB 데이터 정리 SQL 작성
- 공동경비 거래 정책 변경
- 신규 API 또는 DTO 변경

## 작업 계획

1. `TransactionType` enum과 V3/V11 constraint 허용값을 대조한다.
2. V3 constraint 허용값에 `FUND_CHARGE`, `FUND_USE`를 추가한다.
3. 테스트로 기존 코드 영향이 없는지 확인한다.

## 검증 방법

- `./gradlew test`
- 필요 시 local DB에서 Flyway history 초기화 후 애플리케이션 재기동

## PR 템플릿 초안

# 작업 내용

- V3 거래 유형 check constraint를 현재 거래 유형 enum과 맞게 보정
- 기존 `FUND_CHARGE`, `FUND_USE` 데이터가 있는 DB에서 V3 마이그레이션이 실패하지 않도록 수정

# 변경 유형

- [ ] 기능 추가
- [x] 버그 수정
- [ ] 리팩토링
- [ ] 설정 변경
- [ ] 문서 수정
- [ ] 테스트 추가/수정

# 확인 사항

- [ ] 로컬에서 빌드가 성공했습니다.
- [ ] 테스트가 성공했습니다.
- [x] 불필요한 로그/주석을 제거했습니다.
- [x] 민감 정보가 포함되지 않았습니다.
- [x] API 변경 사항이 있다면 문서 또는 요청 예시를 함께 수정했습니다.

# 테스트 방법

```bash
./gradlew test
```
