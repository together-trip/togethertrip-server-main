# Work Plan

## 작업

이슈 #36 `feat: 게시글과 댓글 API 구현`을 진행한다.

구현 대상은 여행 게시글 작성/목록/상세/수정/삭제, 댓글 작성/목록/삭제, 거래 기반 기록의 `transactionId` 검증, 첨부 파일 메타데이터 저장이다.

여행 참여자 여부 검증은 `trip` 도메인 쪽에서 공통 기능으로 제공하는 것을 전제로 한다. 이 작업에서는 해당 공통 권한 기능을 새로 구현하지 않고, Post API에 적용하는 범위만 다룬다.

## 배경

`main` 서버에는 `post` 패키지의 Entity, Repository, Controller skeleton, 요청 DTO가 있으나 `PostService` 구현과 응답 DTO가 비어 있다.

`docs/modeling.md`의 1차 MVP 제외 범위에는 댓글이 포함되어 있지만, GitHub 이슈 #36은 댓글까지 구현 범위에 포함한다. 따라서 이번 작업은 이슈 기준으로 댓글 MVP를 포함하되, 대댓글 고도화와 신고/차단은 제외한다.

도메인 데이터 삭제는 soft delete 정책을 따른다. 게시글과 댓글 삭제는 레코드 물리 삭제가 아니라 `deleted_at`을 기록해 비활성화한다.

## 범위

- `POST /api/trips/{tripId}/posts` 게시글 작성을 구현한다.
- `GET /api/trips/{tripId}/posts` 게시글 목록 조회를 구현한다.
- `GET /api/trips/{tripId}/posts/{postId}` 게시글 상세 조회를 구현한다.
- `PATCH /api/trips/{tripId}/posts/{postId}` 게시글 수정을 구현한다.
- `DELETE /api/trips/{tripId}/posts/{postId}` 게시글 soft delete를 구현한다.
- `POST /api/trips/{tripId}/posts/{postId}/comments` 댓글 작성을 구현한다.
- `GET /api/trips/{tripId}/posts/{postId}/comments` 댓글 목록 조회를 구현한다.
- `DELETE /api/trips/{tripId}/posts/{postId}/comments/{commentId}` 댓글 soft delete를 구현한다.
- `trip` 쪽에서 제공하는 여행 참여자 접근 권한 검증을 Post API에 적용한다.
- 게시글/댓글 작성자 권한을 검증한다.
- 거래 기반 기록인 경우 `transactionId` 존재 여부와 transaction-trip 일치를 검증한다.
- 첨부 파일 업로드 없이 요청으로 전달된 첨부 파일 메타데이터만 저장한다.
- Post API용 `dto/response`를 추가하고 기존 `ApiResponse` 형식에 맞춘다.
- 페이지 목록 응답용 공통 `PageResponse<T>`를 추가한다.
- Post API 성공/실패 경로 테스트를 추가한다.

## 제외 범위

- 실제 파일 업로드 스토리지 연동.
- 파일 URL 발급, 파일 삭제, 이미지 리사이징.
- 댓글 대댓글 고도화. `parentComment`가 Entity에는 있으나 이번 API 요청 DTO에는 포함하지 않는다.
- 신고/차단.
- 게시글 좋아요/북마크.
- 알림 서버 연동.
- 여행 생성/참여자 관리 API 구현.
- 여행 참여자 권한 검증 AOP 또는 공통 권한 컴포넌트 구현.
- 거래 API 구현 자체.
- 정산 상태에 따른 거래 수정/삭제 제한 정책 변경.

## 설계

- Controller는 route mapping, 인증 주체 전달, `ApiResponse` 포장만 담당한다.
- Swagger/OpenAPI 문서 책임은 현재 구조처럼 `post/controller/spec`에 둔다.
- Request DTO는 `post/dto/request`, Response DTO는 `post/dto/response`에 둔다.
- `PostApiSpec`와 `PostController`의 반환 타입을 `ApiResponse<T>`로 맞춘다.
- 목록 응답은 Spring `Page`를 직접 노출하지 않고 `global/response/PageResponse<T>`로 감싼다.
- `PostService`가 트랜잭션 경계와 비즈니스 규칙을 담당한다.
- 쓰기 메서드는 `@Transactional`, 읽기 메서드는 클래스 기본 `@Transactional(readOnly = true)`를 사용한다.
- Entity를 Controller에서 직접 반환하지 않고 응답 DTO로 변환한다.
- soft delete 대상인 `Post`, `PostComment`, `PostAttachment`에는 `@SQLRestriction("deleted_at IS NULL")` 적용을 검토한다.
- 명시성이 필요한 Repository 메서드는 `DeletedAtIsNull` 조건을 유지한다.
- `Post`와 `PostComment` 상태 변경은 가능하면 `update(...)`, `increaseCommentCount()`, `decreaseCommentCount()` 같은 도메인 메서드로 표현한다.

### 권한

- 여행 참여자 접근 권한 검증은 `trip` 쪽 공통 기능을 사용한다.
- Post API는 해당 공통 기능이 제공하는 어노테이션, 인터셉터, 서비스 중 확정된 방식을 적용한다.
- 참여자가 없을 때의 에러 코드는 `trip` 쪽 정책을 따른다.
- 게시글 작성자는 권한 통과 후 `TripParticipantRepository.findByTripIdAndUserIdAndDeletedAtIsNull(...)`로 조회해 `Post.author`로 저장한다. 공통 권한 기능이 참여자 객체를 전달하는 구조라면 중복 조회 제거를 검토한다.
- 게시글 수정/삭제는 `post.author.user?.id == authUser.userId`일 때만 허용한다.
- 댓글 삭제는 `comment.author.user?.id == authUser.userId`일 때만 허용한다.
- 작성자 권한 실패는 `CommonErrorCode.ACCESS_DENIED`를 사용한다.

### 거래 기반 기록

- `CreatePostRequest.transactionId == null`이면 일반 기록으로 생성한다.
- `transactionId != null`이면 `TransactionRepository`로 거래를 조회한다.
- 거래가 없거나 삭제된 거래면 실패한다. 필요한 경우 `TransactionErrorCode.TRANSACTION_NOT_FOUND`를 추가한다.
- 거래의 `trip.id`가 path의 `tripId`와 다르면 실패한다.
- `postType`은 요청값을 신뢰하지 않고 `transactionId` 기준으로 강제한다.
- `transactionId == null`이면 `PostType.RECORD`, `transactionId != null`이면 `PostType.EXPENSE`로 저장한다.

### 응답 DTO 초안

- `CreatePostRequest`: 기존 필드 + `occurredAt`, `placeName`, `latitude`, `longitude`를 포함한다. 값은 nullable로 둔다.
- `PostSummaryResponse`: `id`, `tripId`, `transactionId`, `authorParticipantId`, `authorDisplayName`, `postType`, `title`, `category`, `contentPreview`, `occurredAt`, `placeName`, `latitude`, `longitude`, `commentCount`, `createdAt`, `updatedAt`
- `PostDetailResponse`: summary 필드 + `content`, `attachments`
- `PostAttachmentResponse`: `id`, `attachmentType`, `fileUrl`, `thumbnailUrl`, `fileSize`, `mimeType`, `sortOrder`
- `PostCommentResponse`: `id`, `postId`, `authorParticipantId`, `authorDisplayName`, `content`, `commentDepth`, `createdAt`, `updatedAt`
- `PageResponse<T>`: `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`

## 테스트 계획

- `PostServiceTest`를 Mockito 기반 단위 테스트로 추가한다.
- 참여자가 게시글 작성에 성공하는지 검증한다.
- 여행 참여자 공통 권한 기능이 Post API에 적용되어 있는지 최소 통합 테스트로 검증한다. 공통 기능 자체의 상세 테스트는 `trip` 쪽 책임으로 둔다.
- 일반 기록은 `transaction = null`, `postType = RECORD`로 생성되는지 검증한다.
- 거래 기반 기록은 transaction-trip 일치 시 `postType = EXPENSE`로 생성되는지 검증한다.
- 요청의 `postType`이 `transactionId`와 충돌해도 서버 정책대로 강제되는지 검증한다.
- 거래가 다른 여행에 속하면 실패하는지 검증한다.
- 게시글 목록 조회가 삭제되지 않은 게시글만 최신순으로 조회하는지 검증한다.
- 게시글 상세 조회가 다른 여행의 게시글을 노출하지 않는지 검증한다.
- 작성자가 아니면 게시글 수정/삭제에 실패하는지 검증한다.
- 게시글 삭제 시 `deletedAt != null`이 되는지 검증한다.
- 댓글 작성 시 `post.commentCount`가 증가하는지 검증한다.
- 댓글 삭제 시 `deletedAt != null`이 되고 `post.commentCount`가 감소하는지 검증한다.
- 작성자가 아니면 댓글 삭제에 실패하는지 검증한다.
- 빈 제목, 빈 본문, 빈 댓글, 잘못된 page/size 등 입력 제한을 검증한다.
- 여행 참여자 권한 검증은 `trip` 쪽 공통 기능이 준비된 뒤 `@SpringBootTest` 또는 slice 테스트로 Post API 적용 여부만 최소 검증한다.
- 최종 검증 명령은 `./gradlew test`다.

## 위험과 확인 사항

- `docs/modeling.md`는 댓글을 1차 MVP 제외로 두고 있어 제품 범위 확인이 필요하다. 이슈 #36을 우선하면 문서 갱신이 필요하다.
- 여행 참여자 권한 검증 공통 기능은 외부 선행 작업이다. 해당 기능의 어노테이션명, 적용 위치, 실패 에러 코드, 참여자 객체 전달 여부가 확정되어야 Post API 적용 방식이 고정된다.
- `TripParticipant.user`는 nullable이다. 임시 동행자는 게시글 작성자가 될 수 없도록 로그인 사용자 기반 참여자만 허용해야 한다.
- `PostType` 정책이 문서 주석과 enum 값에서 어긋난다. 주석은 `NORMAL`, `TRANSACTION_LOG`, `MEMORY`를 언급하지만 실제 enum은 `RECORD`, `EXPENSE`다. 이번 구현은 실제 enum 기준으로 진행한다.
- `CreatePostRequest`에는 `occurredAt`, `placeName`, `latitude`, `longitude`를 nullable 필드로 추가한다.
- `PostAttachment` 저장 정책은 메타데이터만 저장한다. URL 유효성, MIME type 허용 목록, 최대 개수 제한은 별도 정책 확인이 필요하다.
- `PostRepository.findByTripIdAndDeletedAtIsNullOrderByCreatedAtDesc(...)`는 postType 필터를 지원하지 않는다. 목록 필터 구현 시 Repository 메서드 추가가 필요하다.
- 현재 로컬 `./gradlew test --rerun-tasks`는 성공했다.
