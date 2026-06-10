# 게시글 첨부와 소비 게시글 삭제 정책 보강 계획

## 배경

Flutter 앱 Issue #10 `여행 상세 게시판 CRUD 및 소비 등록 화면 구현` 검토 중, 게시글 첨부와 소비 게시글 삭제 정책이 현재 백엔드 구현과 맞지 않는 점을 확인했다.

현재 소비 게시글 삭제 상태:

- `PostService.deletePost(...)`는 게시글만 `post.markDeleted()` 처리한다.
- `TransactionService.deleteTransaction(...)`는 거래를 `transaction.void()` 처리하고 `TransactionEventType.VOIDED` 이벤트를 기록한다.
- 소비 게시글은 `Post.transactionId != null`, `postType = EXPENSE`로 거래와 연결되지만, 게시글 삭제가 연결 거래 삭제/무효 처리까지 수행하지 않는다.

프론트에서 `DELETE /transactions/{transactionId}`와 `DELETE /posts/{postId}`를 순차 호출하면 중간 실패 시 게시글/거래 상태가 어긋날 수 있다. 따라서 소비 게시글 삭제와 연결 거래 무효 처리는 백엔드 트랜잭션 안에서 원자적으로 처리하는 정책이 필요하다.

현재 게시글 첨부 상태:

- `CreatePostRequest.attachments`와 `PostAttachment` 저장은 있었지만, 프론트가 `fileUrl`을 직접 전달하는 구조라 파일 업로드 책임이 프론트로 밀려 있다.
- `PostDetailResponse.attachments`는 있다.
- `PostSummaryResponse`에는 첨부가 없어 피드 카드에서 첨부를 표시하려면 게시글별 상세 조회가 필요하다.
- `UpdatePostRequest`에는 `occurredAt`, `placeName`, `latitude`, `longitude`, 첨부 변경 필드가 없어 게시글 수정 시 날짜/위치/첨부를 수정할 수 없다.
- 파일 저장소 업로드, 업로드 URL 발급, 이미지 리사이징 API는 없다.

## 목표

- 소비 게시글 삭제 시 연결된 거래도 같은 서버 트랜잭션에서 무효 처리한다.
- 일반 기록 게시글 삭제는 기존처럼 게시글 soft delete만 수행한다.
- 게시글 목록 응답에서도 첨부 미리보기를 표시할 수 있게 첨부 응답을 포함한다.
- 게시글 수정 시 날짜/위치와 첨부 목록을 수정할 수 있게 요청 DTO와 서비스 로직을 보강한다.
- 게시글 작성/수정 첨부는 프론트가 `fileUrl`을 전달하지 않고 `multipart/form-data` 파일 자체를 전송한다.
- 서버는 저장소 포트를 통해 파일을 저장하고, 로컬 환경은 로컬 디스크에 저장한다. 운영 환경의 S3 저장은 같은 포트 구현체 교체로 분리한다.
- 거래 무효 처리 정책은 기존 거래 원장 규칙을 유지한다.
  - 물리 삭제하지 않는다.
  - `Transaction.status = VOIDED`로 변경한다.
  - `TransactionEventType.VOIDED` 이벤트를 기록한다.
  - 정산 시작 이후에는 실패한다.

## 게시글 첨부 정책 결정안

### 목록 응답

- `PostSummaryResponse`에 `attachments: List<PostAttachmentResponse>`를 추가한다.
- 목록 조회 시 게시글별 첨부를 함께 조회해 피드 카드에서 바로 표시할 수 있게 한다.
- 1차 구현은 N+1을 피하기 위해 post id 목록 기반 bulk 조회를 우선 검토한다.
- 정렬은 기존 첨부 정책처럼 `sortOrder ASC`를 유지한다.

### 작성

- `POST /api/trips/{tripId}/posts`는 `multipart/form-data`를 사용한다.
- 프론트는 `files` 필드에 이미지/영상 파일 자체를 전달한다.
- 서버는 파일을 저장소에 저장한 뒤 생성된 URL, MIME type, size, attachment type을 `PostAttachment`로 저장한다.
- 로컬 환경은 `post.attachments.local-storage-path` 아래 파일을 저장하고 `post.attachments.public-url-prefix` 기반 URL을 반환한다.
- 운영 환경 S3 저장은 `PostAttachmentStorage` 구현체 교체로 확장한다.

### 수정

- `UpdatePostRequest`에 `occurredAt`, `placeName`, `latitude`, `longitude`를 추가한다.
- `UpdatePostRequest`에 `replaceAttachments: Boolean`과 `files: List<MultipartFile>`를 추가한다.
- 날짜/위치 필드는 기존 작성 DTO와 같은 의미로 저장한다.
- `latitude`, `longitude`는 둘 다 null이거나 둘 다 값이 있는 형태를 권장한다.
- `replaceAttachments == false`이면 기존 첨부를 변경하지 않는다.
- `replaceAttachments == true`이면 기존 첨부를 soft delete하고 `files` 업로드 결과로 교체한다.
- `replaceAttachments == true`이고 `files`가 비어 있으면 첨부 전체 제거로 해석한다.

### 제외

- 업로드 URL 발급
- 이미지 리사이징
- 파일 삭제 worker

## 정책 결정안

### 삭제 동작

- `RECORD` 게시글:
  - `DELETE /api/trips/{tripId}/posts/{postId}`
  - 게시글 `deletedAt`만 기록한다.
- `EXPENSE` 게시글:
  - `DELETE /api/trips/{tripId}/posts/{postId}`
  - 연결된 transaction을 먼저 `VOIDED` 처리한다.
  - 거래 무효 이벤트를 기록한다.
  - 게시글 `deletedAt`을 기록한다.
  - 전체 작업은 하나의 `@Transactional` 안에서 처리한다.

### 권한

- 게시글 작성자만 삭제할 수 있다.
- 연결 거래 무효 처리도 같은 사용자 권한으로 수행한다.
- 여행 참가자/정산 상태 검증은 기존 Post/Transaction 정책과 충돌하지 않게 유지한다.

### 실패 처리

- 연결 거래가 없거나 이미 무효/삭제 상태면 명확한 오류를 반환한다.
- 정산이 시작된 여행이면 거래 무효 처리와 게시글 삭제 모두 실패한다.
- 거래 무효 처리 실패 시 게시글도 삭제하지 않는다.

## 구현 후보

### 후보 A: PostService에서 TransactionService를 호출

- `PostService.deletePost(...)`에서 `post.transaction != null`이면 `TransactionService.deleteTransaction(...)`를 호출한다.
- 장점: 기존 transaction 무효 처리 로직과 이벤트 기록을 재사용한다.
- 단점: `post` feature가 `transaction.service`에 의존한다. 순환 의존 여부를 확인해야 한다.

### 후보 B: Post-Transaction 전용 application service 추가

- 예: `PostTransactionDeleteService`
- 컨트롤러는 기존 endpoint를 유지하되 삭제 유스케이스만 전용 서비스로 위임한다.
- 장점: cross-feature orchestration 책임이 명확하다.
- 단점: 작은 유스케이스치고 파일이 늘어난다.

추천은 후보 B다. 게시글 삭제와 거래 무효 처리를 하나의 사용자 유스케이스로 묶되, 각 도메인 서비스의 내부 책임을 과도하게 섞지 않는다.

## 구현 단계

### 게시글 첨부

1. `PostSummaryResponse`에 `attachments`를 추가한다.
2. 목록 조회에서 post id 목록 기준 첨부를 조회하고 post별로 묶어 응답한다.
3. `CreatePostRequest`, `UpdatePostRequest`를 multipart 파일 입력 기준으로 변경한다.
4. `UpdatePostRequest`에 `occurredAt`, `placeName`, `latitude`, `longitude`, `replaceAttachments`, `files`를 추가한다.
5. 수정 요청의 날짜/위치 필드가 Post 엔티티에 반영되는지 구현한다.
6. 수정 요청의 `replaceAttachments`가 false이면 첨부 유지, true이면 파일 업로드 결과로 교체 처리한다.
7. 기존 첨부 교체 시 기존 row는 soft delete하고 새 row를 저장한다.
8. 목록/상세/작성/수정 응답의 첨부 정렬이 `sortOrder ASC`인지 검증한다.
9. API 문서와 Swagger spec 설명을 갱신한다.

### 소비 게시글 삭제

1. 현재 `PostService.deletePost(...)`의 소비 게시글 삭제 동작을 테스트로 고정한다.
2. 연결 거래가 있는 게시글 삭제 시 거래 무효 처리가 함께 필요한 실패 테스트를 추가한다.
3. 전용 application service 또는 PostService orchestration 방식을 선택한다.
4. 소비 게시글 삭제 시 연결 transaction을 `VOIDED` 처리하고 이벤트를 기록한다.
5. 거래 무효 처리 실패 시 게시글 soft delete가 롤백되는지 검증한다.
6. 정산 시작 이후 소비 게시글 삭제가 실패하는지 검증한다.
7. 일반 기록 게시글 삭제는 기존처럼 게시글만 soft delete되는지 회귀 테스트를 추가한다.
8. API 문서에 소비 게시글 삭제 정책을 반영한다.

## 테스트 계획

### 게시글 첨부

- 게시글 작성:
  - multipart 파일이 서버 저장소에 저장된다.
  - 저장소가 반환한 첨부 메타데이터가 저장된다.
  - 상세 응답에 첨부가 `sortOrder ASC`로 포함된다.
- 게시글 목록:
  - 목록 응답에 첨부가 포함된다.
  - 삭제된 첨부는 목록/상세 응답에 포함되지 않는다.
- 게시글 수정:
  - 제목/카테고리/내용뿐 아니라 `occurredAt`, `placeName`, `latitude`, `longitude`가 수정된다.
  - `replaceAttachments == false`이면 기존 첨부가 유지된다.
  - `replaceAttachments == true`이고 `files`가 비어 있으면 기존 첨부가 제거된다.
  - `replaceAttachments == true`이고 `files`가 있으면 기존 첨부가 업로드 결과로 교체된다.

### 소비 게시글 삭제

- 일반 기록 게시글 삭제:
  - 게시글 `deletedAt`이 기록된다.
  - 거래 이벤트가 생성되지 않는다.
- 소비 게시글 삭제:
  - 연결 transaction status가 `VOIDED`가 된다.
  - `TransactionEventType.VOIDED` 이벤트가 생성된다.
  - 게시글 `deletedAt`이 기록된다.
- 권한:
  - 작성자가 아니면 삭제에 실패한다.
- 정산 상태:
  - `NOT_STARTED`가 아니면 소비 게시글 삭제와 거래 무효 처리 모두 실패한다.
- 원자성:
  - 거래 무효 처리 중 예외가 발생하면 게시글 `deletedAt`이 기록되지 않는다.

## 프론트 연동 메모

- 프론트는 피드 목록 응답의 `attachments`를 우선 사용한다.
- 목록 응답에 첨부가 포함되면 게시글별 상세 보강 조회는 제거할 수 있다.
- 게시글 수정에서 날짜/위치를 바꾸려면 `PATCH /posts/{postId}`에 `occurredAt`, `placeName`, `latitude`, `longitude`를 포함한다.
- 게시글 작성/수정 첨부는 `multipart/form-data`의 `files` 필드로 파일 자체를 전달한다.
- 게시글 수정에서 첨부를 바꾸려면 `PATCH /posts/{postId}`에 `replaceAttachments=true`와 `files`를 포함한다.
- 첨부를 모두 제거하려면 `replaceAttachments=true`만 보내고 `files`를 비운다.
- 프론트는 소비 게시글 삭제 시에도 일반 게시글과 동일하게 `DELETE /api/trips/{tripId}/posts/{postId}`만 호출하는 것이 목표다.
- 프론트에서 `DELETE /transactions/{transactionId}`를 별도로 순차 호출하지 않는다.
- 백엔드 보강 전까지 소비 게시글 삭제 버튼 연결은 보류하거나 서버 정책 미정 TODO로 남긴다.

## 남은 질문

- 거래 삭제 API(`DELETE /transactions/{transactionId}`) 호출 시 연결된 소비 게시글도 함께 삭제해야 하는 역방향 정책도 필요한가?
- 한 transaction에 여러 소비 게시글이 연결될 수 있는지, 가능하다면 거래 무효 처리 시 다른 게시글 처리 정책은 어떻게 할 것인가?
- 소비 게시글 삭제가 거래 무효 이벤트 payload에 게시글 ID를 남겨야 하는가?
- 첨부 파일 업로드/URL 발급 API는 별도 이슈로 분리할 것인가, 같은 hotfix 범위에 최소 presigned URL 발급까지 포함할 것인가?
