# 여행 Recap OpenAI 로컬 E2E 검증

## 범위

- 대상 이슈: #101
- 대상 PR: #102
- 실제 이미지 생성 provider: OpenAI
- 모델 기본값: `gpt-image-2`
- 저장 방식: main 서버 로컬 디스크
- API 계약 변경: 없음

## 구현 경로

- OpenAI 생성기: `src/main/kotlin/com/togethertrip/main/triprecap/service/ai/OpenAiTripRecapGenerator.kt`
- OpenAI 설정: `src/main/kotlin/com/togethertrip/main/triprecap/service/ai/OpenAiTripRecapProperties.kt`
- 로컬 참고 사진 로더: `src/main/kotlin/com/togethertrip/main/triprecap/service/storage/LocalTripRecapPhotoContentLoader.kt`
- 환경 설정: `src/main/resources/application.yml`
- 로컬 환경 변수 예시: `src/main/resources/.env.example`

## 로컬 환경 준비

실행 위치:

```text
/Users/jujaewan/1_Projects/togethertrip/togethertrip-server-main
```

리포 루트에 Git 추적되지 않는 `.env`를 만든다. 아래 OpenAI 블록은
`src/main/resources/.env.example` 맨 아래 블록과 변수명 및 순서가 같다.
로컬 E2E에서는 `TRIP_RECAP_AI_PROVIDER`와 `OPENAI_API_KEY` 값만 실제 실행값으로 바꾼다.

```dotenv
TRIP_RECAP_AI_PROVIDER=openai
OPENAI_BASE_URL=https://api.openai.com
OPENAI_API_KEY=사용자_서버용_API_KEY
OPENAI_IMAGE_MODEL=gpt-image-2
OPENAI_IMAGE_SIZE=1152x2048
OPENAI_IMAGE_QUALITY=medium
OPENAI_IMAGE_TIMEOUT=3m
OPENAI_MAX_REFERENCE_IMAGES=4
OPENAI_MAX_REFERENCE_IMAGE_BYTES=10485760
```

아래 항목은 OpenAI 신규 설정이 아니라 로컬 전체 흐름 실행을 위한 기존 환경 변수다.

```dotenv
SERVER_PORT=8081
AUTH_LOCAL_TEST_ENABLED=true
NOTIFICATION_SQS_SENDER_ENABLED=false
OUTBOX_DISPATCH_ENABLED=false
```

API 키는 채팅, 커밋, PR 본문, 로그에 남기지 않는다. GPT Image 모델 사용을 위해 OpenAI API 조직 인증이 요구될 수 있다.

서버 기본 의존성은 다음 위치를 사용한다.

- PostgreSQL: `127.0.0.1:5432/together_trip`
- Redis: `127.0.0.1:6379`
- main 서버: `http://localhost:8081`
- gateway: `http://localhost:8080`
- 생성 이미지: `./uploads/trip-recaps`
- 참고 사진: `./uploads/post-attachments`

## 실행

main 서버:

```bash
./gradlew bootRun
```

gateway:

```bash
cd /Users/jujaewan/1_Projects/togethertrip/togethertrip-server-gateway
./gradlew bootRun
```

Flutter 앱:

```bash
cd /Users/jujaewan/1_Projects/togethertrip/togethertrip-client-flutter
flutter run --dart-define=API_BASE_URL=http://localhost:8080
```

iOS 시뮬레이터에서는 위 `localhost`를 사용한다. Android 에뮬레이터에서는 호스트 주소를 `10.0.2.2`로 바꾼다.

Swagger UI는 main 서버 직접 주소를 사용한다.

```text
http://localhost:8081/swagger-ui.html
```

로컬 인증을 별도로 사용하는 경우 기존 main 서버의 `AUTH_LOCAL_TEST_ENABLED` 정책을 따른다. 여행 종료와 정산 완료 조건을 만족하고 이미지 게시글이 있는 테스트 여행에서 다음 순서로 확인한다.

1. `GET /api/trips/{tripId}/recap/status`
2. `POST /api/trips/{tripId}/recap` body `{"style":"PHOTO"}`
3. 상태가 `COMPLETED`가 될 때까지 상태 API를 조회한다.
4. `GET /api/trips/{tripId}/recap`
5. 응답의 각 `imageUrl`을 인증 헤더와 함께 조회한다.
6. `generation_provider=openai`, `generation_model=gpt-image-2`가 DB scene에 기록됐는지 확인한다.

## 자동 검증

```bash
./gradlew test --tests 'com.togethertrip.main.triprecap.service.ai.*' --tests 'com.togethertrip.main.triprecap.service.storage.*'
./gradlew test
```

계약 테스트는 로컬 HTTP 테스트 서버로 다음을 검증한다.

- Bearer API 키 헤더
- 사진 없음: `/v1/images/generations`
- 사진 있음: `/v1/images/edits` multipart `image[]`
- `gpt-image-2`, `1152x2048`, `medium`
- base64 이미지 응답 디코딩
- API 키 누락 시 외부 호출 전 실패
- 로컬 사진 경로 이탈 및 최대 크기 제한

## 남은 수동 검증

- 실제 `OPENAI_API_KEY`를 사용한 과금 이미지 생성
- 실제 여행 사진의 품질과 얼굴 비식별 정책 수동 확인
- 3장, 5장, 7장 생성 시간과 비용 관찰
- 앱 Recap 화면에서 전체 장면 표시 확인

API 키가 저장소에 제공되지 않았으므로 실제 과금 호출은 자동 검증에 포함하지 않는다.
