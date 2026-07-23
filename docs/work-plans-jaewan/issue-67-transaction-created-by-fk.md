# transactions created_by_user_id FK 정리 계획

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/67
- 작업 브랜치: `fix/issue-67-transaction-created-by-fk`
- 기준 브랜치: `develop`

## 증상

거래 생성 API에서 `transactions.created_by_user_id`에 현재 사용자 id를 저장할 때,
로컬 DB에 남아 있는 잘못된 FK가 `trip_participants(id)`를 참조해 insert가 실패한다.

## 원인

코드와 V1 DDL 기준 `transactions.created_by_user_id`는 `users(id)`를 참조해야 한다.
실제 로컬 DB에는 같은 컬럼에 아래 FK가 동시에 존재한다.

- 정상: `created_by_user_id -> users(id)`
- 오류: `created_by_user_id -> trip_participants(id)`

## 수정 범위

- 잘못된 `transactions.created_by_user_id -> trip_participants(id)` FK 제거
- 정상 `transactions.created_by_user_id -> users(id)` FK가 없을 경우 보강
- 서비스/DTO/API 계약은 변경하지 않음

## 검증

- `./gradlew test`
- 로컬 DB FK 확인 쿼리
- 거래 생성 API 재시도
