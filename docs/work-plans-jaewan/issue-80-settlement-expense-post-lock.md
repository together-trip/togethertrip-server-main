# 정산 이후 소비 게시글 백엔드 변경 제한 구현 계획

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/80
- 작업 브랜치: `fix/issue-80-settlement-expense-post-lock`
- 기준 브랜치: `develop`
- 작성일: 2026-06-19

## 배경

정산 확정 이후 거래 원장은 `TransactionService.validateWritableTrip(...)`에서 막고 있다.

- 거래 생성: `createTransaction`
- 거래 수정: `updateTransaction`
- 거래 삭제: `deleteTransaction`
- 거래 환율 미리보기: `getTransactionExchangeRatePreview`

하지만 거래와 연결된 `EXPENSE` 게시글은 `PostService.updatePost(...)`에서 정산 상태를 확인하지 않는다. 따라서 정산 이후에도 소비 게시글의 제목, 카테고리, 발생일, 장소, 첨부를 수정할 수 있다.

특히 소비 통계는 연결 게시글의 `category`, `occurredAt`을 사용한다. 정산 이후 `EXPENSE` 게시글 메타데이터가 바뀌면 정산 원장 금액은 고정되어도 소비 통계/조회 기준이 흔들릴 수 있다.

정책 목표는 다음과 같다.

- 일반 여행 기록은 정산 이후에도 작성/수정/삭제할 수 있다.
- 소비 원장과 소비 통계에 영향을 주는 변경은 정산 이후 막는다.
- 기존 거래 잠금 정책과 충돌하지 않는다.

## TogetherTrip 에이전트 판단

### Planner

- 이번 이슈는 정산 완료 후 쓰기 제한의 백엔드 정책 보강이다.
- 프론트 UI 제한은 client-flutter #23에서 처리하고, 서버는 API 직접 호출을 방어한다.
- 일반 기록 기능을 막지 않도록 소비 연결 게시글만 좁게 제한한다.

### Architect

- 현재 구조는 feature-based MVC다.
- `PostService`가 게시글 수정 유스케이스를 소유하므로, 정산 잠금 검증도 `PostService.updatePost(...)` 진입부에서 수행한다.
- 정산 상태 판정은 연결된 `post.transaction.trip.settlementStatus` 또는 `post.trip.settlementStatus`를 사용한다.
- 에러 코드는 `PostErrorCode`에 추가하거나, 기존 정책과 맞는 공통/거래 에러를 재사용할지 구현 전 결정한다.

### TDD Guide

- 실패 테스트를 먼저 추가해 정책을 고정한다.
- 일반 `RECORD` 게시글은 정산 완료 후에도 수정 가능해야 한다.
- `EXPENSE` 게시글은 정산 완료 후 수정이 실패해야 한다.
- 정산 미시작 상태에서는 기존 소비 게시글 수정 동작이 유지되어야 한다.

### Verify Agent

- `./gradlew test --tests '*PostServiceTest*'`를 우선 실행한다.
- 삭제 정책 회귀가 포함되면 `./gradlew test --tests '*PostDeleteServiceTest*'`도 실행한다.
- 필요 시 전체 `./gradlew test`로 정산/거래 회귀를 확인한다.

## 범위

- `PostService.updatePost(...)`에 정산 이후 소비 게시글 수정 제한을 추가한다.
- `post.transaction != null` 또는 `post.postType == EXPENSE`인 게시글을 소비 게시글로 판단한다.
- `post.transaction != null`이 없는 `EXPENSE` 데이터는 비정상 상태로 보고 기존 정책처럼 명확한 실패를 고려한다.
- 일반 `RECORD` 게시글 작성/수정/댓글은 정산 이후에도 허용한다.
- 소비 게시글 삭제는 기존 `PostDeleteService -> TransactionService.deleteTransaction(...)` 경로가 정산 잠금을 따르는지 테스트로 보강한다.

## 제외 범위

- 거래 금액, 결제자, 부담자 수정 정책 변경
- 정산 확정 취소 또는 재정산
- 정산 스냅샷 재계산
- 프론트엔드 버튼/액션 제한

## 설계

### 수정 제한 정책

1차 구현은 단순하고 안전한 정책을 추천한다.

- 정산 이후 `EXPENSE` 게시글 수정 전체를 차단한다.
- 정산 이후 `RECORD` 게시글 수정은 허용한다.

부분 허용을 선택할 경우 `title`, `content`, `placeName`, `attachments`는 허용하고 `category`, `occurredAt`만 차단할 수 있다. 하지만 사용자가 보기에는 소비 게시글 전체가 정산 결과와 연결되어 있으므로, MVP에서는 전체 차단이 더 명확하다.

### 예상 코드 흐름

1. `PostService.updatePost(...)`에서 `post` 조회와 작성자 검증을 수행한다.
2. `validateEditablePostAfterSettlement(post)` 같은 private 함수를 호출한다.
3. 함수 내부에서 소비 게시글이고 `post.trip.settlementStatus != NOT_STARTED`이면 비즈니스 예외를 던진다.
4. 기존 `post.update(...)`, 첨부 교체 로직은 그대로 유지한다.

### 에러 코드 후보

- `POST_LOCKED_BY_SETTLEMENT`
- HTTP status: `409 CONFLICT`
- 메시지: `정산이 완료된 여행에서는 소비 기록을 변경할 수 없습니다.`

거래 에러 `TRANSACTION_LOCKED_BY_SETTLEMENT`를 그대로 노출하면 게시글 API 문맥과 맞지 않을 수 있어 `PostErrorCode` 추가를 우선 검토한다.

## 테스트 계획

### PostServiceTest

- 정산 완료 여행에서 일반 기록 게시글 수정은 성공한다.
- 정산 완료 여행에서 거래 연결 소비 게시글 수정은 실패한다.
- 정산 미시작 여행에서 거래 연결 소비 게시글 수정은 성공한다.
- 정산 완료 여행에서 일반 기록 댓글 작성/삭제는 기존처럼 가능하다.

### PostDeleteServiceTest

- 정산 완료 여행에서 소비 게시글 삭제가 `TransactionService.deleteTransaction(...)` 실패를 전파한다.
- 정산 완료 여행에서 일반 기록 게시글 삭제는 성공한다.

### 회귀 확인

- 거래 생성/수정/삭제의 정산 잠금 테스트가 기존대로 통과한다.
- 소비 통계 쿼리 변경은 없다.

## 위험과 확인 사항

- `PostType.EXPENSE`인데 `transaction == null`인 데이터가 존재할 수 있는지 확인해야 한다.
- `PostService.getPostOrThrow(...)`가 가져온 `post.trip` 접근 시 lazy loading이 트랜잭션 안에서 안전한지 확인한다.
- 소비 게시글 전체 수정 차단과 일부 필드 허용 중 어떤 정책이 제품 의도에 맞는지 구현 전에 이슈 코멘트로 확정하면 좋다.

## 완료 기준

- 정산 이후 소비 게시글 수정 API가 실패한다.
- 정산 이후 일반 기록 수정 API는 성공한다.
- 정책이 서비스 테스트로 고정된다.
- 프론트 이슈와 분리되어 백엔드 직접 호출 방어가 가능하다.
