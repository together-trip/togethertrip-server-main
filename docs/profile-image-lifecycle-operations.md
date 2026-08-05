# 프로필 이미지 파일 수명주기 운영 계약

## 교체와 롤백

- `PATCH /api/users/me`에서 프로필 이미지가 실제로 변경될 때만 이전 URL을 정리 대상으로 본다.
- 이전 URL이 `/uploads/user-profile-images/{file}.{jpg|jpeg|png}` 형식의 서버 관리 파일이면
  프로필 변경과 같은 DB 트랜잭션에 `PROFILE_IMAGE` cleanup 작업을 저장한다.
- Kakao CDN 등 외부 URL은 서버 소유 파일이 아니므로 cleanup payload로 저장하거나 삭제하지 않는다.
- 트랜잭션이 커밋되면 worker가 이전 파일을 삭제한다. 작업 저장에 실패하면 프로필 변경도 함께
  롤백되어 기존 URL과 파일이 유지된다.
- multipart 업로드 뒤 트랜잭션이 롤백되면 해당 요청에서 새로 저장한 파일만 즉시 삭제한다.
  이전 파일 cleanup 작업은 함께 롤백되므로 기존 파일은 삭제하지 않는다.
- 같은 URL로 수정하거나 이미지 입력 없이 다른 프로필 필드만 수정하면 cleanup 작업을 만들지 않는다.
- JSON 요청의 `profileImageUrl`이 서버 관리 URL이면 현재 사용자에게 저장된 값과 같은 URL만
  허용한다. 다른 과거 managed URL을 직접 지정하면 `INVALID_INPUT`으로 거부한다.
- 새 managed 이미지는 multipart `profileImage` 업로드로만 생성한다. 신뢰한 외부 OAuth 이미지
  URL로의 전환은 기존 allowlist 범위에서 허용한다.

## 내구적 정리

프로필 교체는 계정 삭제에서 사용하는 `user_account_deletion_cleanup_tasks`의 기존
`PROFILE_IMAGE` 유형과 worker를 재사용한다.

- 상태는 `PENDING -> PROCESSING -> COMPLETED`로 전이한다.
- 파일 삭제가 실패하면 `FAILED`와 `retry_count`, `next_attempt_at`,
  `PROFILE_IMAGE_DELETE_FAILED`를 기록하고 지수 backoff 뒤 다시 시도한다.
- 완료 시 파일 URL payload를 `NULL`로 제거한다.
- worker lease가 만료되면 다른 worker가 다시 claim할 수 있다.
- 로컬 저장소는 `Files.deleteIfExists`를 사용하므로 완료 저장 전 worker가 중단되어 같은 파일을
  다시 삭제해도 성공한다.
- worker는 삭제 직전에 활성 사용자의 현재 `profile_image_url`을 확인한다. 과거 URL을 API로 다시
  선택해 cleanup payload가 현재 참조와 같아졌다면 물리 삭제를 건너뛰고 작업만 완료한다.
- 회원 탈퇴 사용자는 프로필 URL이 `NULL`이고 활성 사용자 조회에서도 제외되므로 기존 계정 삭제
  cleanup은 파일을 정상 삭제한다.

별도의 환경변수나 scheduler는 추가하지 않는다. 기존
`USER_ACCOUNT_DELETION_CLEANUP_*` 설정을 사용하는 동일 worker가 계정 삭제와 프로필 교체 작업을
함께 처리한다.

## 범위

- API 요청/응답과 허용 이미지 URL 형식은 변경하지 않는다.
- 앱은 이미지 미선택 시 현재 URL을 그대로 보내고 새 이미지는 multipart로 전송하므로 다른 managed
  URL 재지정 금지는 기존 앱 수정 흐름과 호환된다.
- 파일의 법적 보관 정책과 신규 운영 알람 설계는 이 변경 범위에 포함하지 않는다.
- 이 변경은 프로필 이미지 수명주기 누락만 보완하며 이슈 #125 전체 종료를 의미하지 않는다.
