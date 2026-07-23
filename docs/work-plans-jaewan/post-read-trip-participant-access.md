# Work Plan: 게시글 활성 여행 참가자 권한 검증

## 작업

게시글/댓글 API에서 인증 사용자가 해당 여행의 활성 참가자인지 검증한다.

현재 `PostController`는 `getPosts`, `getPost`, `getComments`에서 `AuthUser`를 받지만 서비스 호출에는 `userId`를 넘기지 않는다. 서비스 조회 메서드도 `tripId`/`postId`만으로 조회하므로, 인증된 사용자가 `tripId`만 알면 참가하지 않은 여행의 게시글 목록, 상세, 댓글을 조회할 수 있다.

또한 `createPost`, `createComment`는 `PostService.getParticipant()`를 통해 참가자를 찾지만 `deletedAt`만 확인하고 `participantStatus == ACTIVE`를 확인하지 않는다. `TripParticipantStatus.LEFT` 또는 `REMOVED` 상태가 남아 있으면 퇴장/제거된 사용자가 게시글과 댓글을 계속 작성할 수 있다.

## 범위

- 활성 여행 참가자 권한 검증용 AOP 어노테이션 추가
- AOP에서 `AuthUser`와 `tripId`를 추출해 `TripParticipantRepository`로 활성 참가 여부 검증
- `PostController`의 게시글/댓글 API 전체에 활성 참가자 권한 검증 적용
- `PostService.getParticipant()`도 ACTIVE 참가자만 반환하도록 보조 방어
- 비참가자와 비활성 참가자 실패 테스트 추가

## 제외 범위

- 서비스 조회 메서드에 `userId` 파라미터 추가
- 여행 참가자 권한 정책 자체 변경
- 여행 목록/상세, 내 여행 참가자 조회 등 Post 외 API 정책 변경

## 설계

### 1. AOP 의존성

`build.gradle.kts`에 AOP 런타임 의존성을 추가한다.

```kotlin
implementation("org.springframework:spring-aop")
implementation("org.aspectj:aspectjweaver:1.9.25.1")
```

### 2. 권한 검증 어노테이션

컨트롤러 메서드에 선언적으로 붙일 수 있는 어노테이션을 추가한다.

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireActiveTripParticipant(
    val tripIdParam: String = "tripId",
)
```

### 3. Aspect

`@RequireActiveTripParticipant`가 붙은 메서드 실행 전에 다음을 검증한다.

1. 메서드 인자에서 `AuthUser`를 찾는다.
2. 어노테이션의 `tripIdParam` 이름에 해당하는 `Long` 인자를 찾는다.
3. `tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(tripId, authUser.userId, ACTIVE)`를 조회한다.
4. 활성 참가자가 없으면 `BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)`를 던진다.
5. 활성 참가자가 있으면 원래 메서드를 진행한다.

`AuthUser` 또는 `tripId`를 찾을 수 없는 경우는 잘못된 어노테이션 사용이므로 `IllegalStateException`으로 실패시킨다. `tripId`는 `postId`와 같은 `Long` 인자가 함께 있는 메서드가 많으므로 타입 기반 추론만 사용하지 않는다.

### 4. 적용 대상

`PostController`의 게시글/댓글 API 전체에 적용한다.

- `createPost`
- `getPosts`
- `getPost`
- `updatePost`
- `deletePost`
- `createComment`
- `getComments`
- `deleteComment`

조회 서비스 메서드는 현재처럼 `tripId`/`postId` 중심으로 유지한다. 작성 서비스 경로는 컨트롤러 AOP를 우회하는 내부 호출을 대비해 `PostService.getParticipant()`도 ACTIVE 조건으로 좁힌다.

### 5. Repository

`TripParticipantRepository`에 ACTIVE 상태 조건을 포함한 메서드를 추가한다.

```kotlin
fun findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
    tripId: Long,
    userId: Long,
    participantStatus: TripParticipantStatus,
): TripParticipant?
```

## 테스트 계획

- Aspect 테스트:
  - ACTIVE 참가자가 있으면 대상 메서드가 실행된다.
  - 참가자가 없으면 `TRIP_PARTICIPANT_NOT_FOUND`로 실패한다.
  - LEFT/REMOVED 참가자는 repository 조회 결과가 없어 실패한다.
- Post 조회 API 적용 확인:
  - `PostController`의 게시글/댓글 API 전체에 `@RequireActiveTripParticipant`가 선언되어 있는지 확인한다.
- PostService 테스트:
  - LEFT/REMOVED 참가자는 게시글 작성에 실패한다.
  - LEFT/REMOVED 참가자는 댓글 작성에 실패한다.
- 전체 검증:

```bash
./gradlew test
```

## 위험과 확인 사항

- 서비스 단위 테스트는 AOP 프록시를 거치지 않으므로 조회 권한 검증 테스트는 별도 Aspect/컨트롤러 테스트로 둔다.
- AOP가 컨트롤러에만 붙으면 내부 서비스 호출에는 적용되지 않는다. 작성 경로는 `PostService.getParticipant()`의 ACTIVE 조건으로 보조 방어한다.
- 여행 owner는 여행 생성 시 ACTIVE LEADER 참가자로 함께 저장되므로 정상 데이터에서는 AOP 적용으로 owner 접근이 막히지 않는다.
- 기존 `TripService.getAccessibleTrip()`과 `UserService.getMyTripParticipant()`는 이번 범위에서 변경하지 않는다. Post API 권한만 활성 참가자 기준으로 강화한다.
