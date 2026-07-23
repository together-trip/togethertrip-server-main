# Verification Report: 게시글 활성 여행 참가자 권한 검증

## 검증 대상

`feature/issue-36-post-api` 브랜치의 게시글/댓글 API 활성 여행 참가자 권한 검증.

## 변경 요약

- `@RequireActiveTripParticipant` 어노테이션과 Aspect를 추가했다.
- `PostController`의 게시글/댓글 API 전체에 활성 참가자 검증을 적용했다.
- `PostService.getParticipant()`를 ACTIVE 참가자 조회로 좁혀 컨트롤러 AOP 우회 호출도 방어했다.
- `TripParticipantRepository`에 `participantStatus` 조건 포함 조회 메서드를 추가했다.

## 리뷰 결과

- Security: 통과. 비참가자와 `LEFT`/`REMOVED` 참가자가 Post API를 조회/작성/수정/삭제하지 못하도록 컨트롤러와 서비스 경로를 함께 보강했다.
- Correctness: 통과. `tripId`는 파라미터명으로 찾도록 해 `postId`와 같은 다른 `Long` 인자를 오인하지 않는다.
- Maintainability: 통과. Post API 접근 제어는 어노테이션으로 선언하고, 작성자 엔티티 조회는 서비스 내부 ACTIVE 조건으로 보조 방어한다.
- Test Coverage: 통과. Aspect 성공/실패/파라미터 구분/오적용 방어 테스트와 PostService 작성 실패 테스트를 추가했다.

## 실행한 명령

```bash
./gradlew test
```

## 실행 결과

```text
BUILD SUCCESSFUL in 9s
```

## 남은 위험

- AOP는 컨트롤러 진입점 기준으로 적용된다. Post 외 다른 여행 접근 API의 `LEFT`/`REMOVED` 정책은 이번 범위에서 변경하지 않았다.
- 정상 데이터에서는 여행 생성자가 ACTIVE LEADER 참가자로 함께 생성되므로 owner 접근은 유지된다. legacy 데이터에 owner participant row가 없으면 Post API 접근이 막힐 수 있다.
