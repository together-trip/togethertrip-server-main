# UGC 신고·차단 운영 가이드

## API 계약

- 신고: `POST /api/trips/{tripId}/reports`
  - `targetType`: `POST`, `COMMENT`, `USER`, `TRIP_RECAP`
  - `targetId`: 게시글/댓글/사용자/AI Recap의 전역 ID
  - `reason`: `SPAM`, `HARASSMENT`, `HATE_SPEECH`, `SEXUAL_CONTENT`, `VIOLENCE`, `PRIVACY`, `OTHER`
  - `description`: 선택, 최대 1,000자
- 사용자 차단/해제: `POST`, `DELETE /api/users/{userId}/blocks`
- 내 차단 목록: `GET /api/users/me/blocks`
- 운영자 신고 큐: `GET /api/admin/moderation/reports?status=&targetType=&cursor=&size=`
  - `(createdAt, id)` 오름차순 커서로 오래된 신고부터 안정적으로 조회한다.
- 운영자 처리: `PATCH /api/admin/moderation/reports/{reportId}`

신고자는 해당 여행의 활성 참여자여야 하고 대상도 같은 여행에 속해야 한다. `USER`의 `targetId`는 `userId`이며 임시 참여자는 신고·차단할 수 없다. 자기 신고·차단과 처리 중인 중복 신고는 거부한다.

## 노출 및 데이터 보존 정책

- 차단은 양방향 노출/상호작용 정책이다. 어느 한쪽이 차단하면 일반 `RECORD`와 댓글은 DB 조회 단계에서 제외한다.
- 운영자 숨김/삭제도 원본과 감사 자료를 보존하는 별도 시각 필드로 처리한다.
- `EXPENSE`, 거래, 결제 분담, 정산, 정산 스냅샷은 차단 및 UGC 운영 조치로 숨기거나 삭제하지 않는다.
- 게시글/댓글 알림은 차단 관계 수신자에게 발행하지 않는다. 지출 알림은 정산 근거 전달을 위해 유지한다.
- 게시글 첨부는 공개 `/uploads/post-attachments/**`로 제공하지 않는다. 응답의 `fileUrl`과 `thumbnailUrl`은 `/api/trips/{tripId}/posts/{postId}/attachments/{attachmentId}` 인증 경로이며 앱은 Bearer token을 포함해 byte를 받아 렌더링한다.
- 신고 설명과 필터에 걸린 원문은 애플리케이션 로그에 기록하지 않는다.

현재 인증 첨부 응답은 이미지 중심의 전체 byte 응답이다. 영상 첨부의 seek/streaming에 필요한 HTTP Range 응답은 후속 작업으로 분리하며, Range 지원 전에는 앱에서 영상 자동 재생을 활성화하지 않는다.

## 처리 SLA

| 우선순위 | 예시 | 최초 확인 | 목표 처리 |
|---|---|---:|---:|
| 긴급 | 신체 위협, 개인정보 노출, 불법 촬영물 | 1시간 | 4시간 |
| 높음 | 혐오·성적 콘텐츠, 반복 괴롭힘 | 4시간 | 24시간 |
| 일반 | 스팸, 기타 정책 위반 | 24시간 | 72시간 |

`PENDING → IN_REVIEW → RESOLVED/REJECTED` 순서로 처리한다. 모든 상태·조치 변경은 처리자, 처리시각, 이전/이후 상태와 사유를 append-only 감사 이력으로 남긴다. 긴급 신고는 먼저 `HIDE`하고 사실 확인 후 최종 처리한다.

## 운영 점검

1. 미처리 신고의 SLA 초과 건을 상태·생성시각 순서로 확인한다.
2. `HIDE`/`DELETE`는 일반 기록, 댓글, AI Recap에만 적용되었는지 확인한다.
3. `RESTRICT_USER`의 기간과 근거를 입력하고, 해제 시 `UNRESTRICT_USER` 감사 이력을 남긴다.
4. 신고 원문은 최소 권한 운영자만 조회하고 외부 채널에 복사하지 않는다.
5. 동시 처리 충돌은 다시 조회한 최신 상태를 기준으로 재처리한다.
