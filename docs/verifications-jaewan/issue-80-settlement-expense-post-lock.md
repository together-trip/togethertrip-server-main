# 정산 이후 소비 게시글 백엔드 변경 제한 검증

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/80
- 작업 브랜치: `fix/issue-80-settlement-expense-post-lock`
- 검증일: 2026-06-19

## 루프 선택

- 선택 루프: `/togethertrip:workflow`
- 하위 루프: Planner -> Architect -> TDD Guide -> Implementation -> Code Reviewer -> Security Reviewer -> Verify Agent -> Doc Updater
- 선택 이유: 정산 이후 소비 변경 제한은 기존 백엔드 정책 수정이며, 정산/금액 무결성에 영향을 준다.

## 점수 루프

| 루프 | 점수 | 기준 | 결과 |
| --- | ---: | --- | --- |
| Planner | 5/5 | 범위와 제외 범위가 이슈 #80에 맞게 제한됨 | 통과 |
| Architect | 5/5 | feature-based MVC 유지, `PostService` 내부 검증으로 제한 | 통과 |
| TDD Guide | 5/5 | 정산 완료 RECORD 허용, EXPENSE 차단, 정산 미시작 EXPENSE 허용 테스트 추가 | 통과 |
| Code Reviewer | 4/5 | 변경이 좁고 기존 흐름을 유지함. 부분 필드 허용 정책은 후속 결정 여지 있음 | 통과 |
| Security Reviewer | 5/5 | 정산 이후 소비 통계/원장 관련 변경 방어 | 통과 |
| Verify Agent | 5/5 | 관련 테스트와 전체 테스트 통과 | 통과 |
| Doc Updater | 4/5 | 루트 공통 API/정책 문서 갱신. 단, 루트 `docs/`는 `main` repo PR에 포함되지 않음 | 주의 |

통과 기준은 평균 4점 이상이고, Security/Verify 5점이다. 현재 평균 4.71점으로 통과한다.

## 변경 요약

- `POST_LOCKED_BY_SETTLEMENT` 에러 코드를 추가했다.
- `PostService.updatePost(...)`에서 거래 연결 게시글이 정산 완료 여행에 속하면 수정 실패하도록 했다.
- 일반 기록 게시글은 정산 완료 후에도 수정 가능하게 유지했다.
- 정책 회귀 테스트를 추가했다.

## 검증 명령

```bash
./gradlew test --tests '*PostServiceTest*' --tests '*PostDeleteServiceTest*'
./gradlew test
```

## 검증 결과

- `./gradlew test --tests '*PostServiceTest*' --tests '*PostDeleteServiceTest*'`: 성공
- `./gradlew test`: 성공

## 확인한 정책

- 정산 완료 이후 `RECORD` 게시글 수정 가능
- 정산 완료 이후 거래 연결 `EXPENSE` 게시글 수정 불가
- 정산 미시작 상태의 거래 연결 `EXPENSE` 게시글 수정 가능
- 소비 게시글 삭제는 기존 `TransactionService.deleteTransaction(...)` 정산 잠금 정책을 따름

## 남은 위험

- 루트 `docs/api-spec.md`, `docs/expense-policy.md`는 갱신했지만 `main` repo Git 관리 대상이 아니라 PR에 포함되지 않는다.
- 현재 구현은 정산 이후 소비 게시글 수정 전체를 차단한다. 일부 필드 허용이 필요하면 별도 정책 결정이 필요하다.
