# TogetherTrip Agent Guide

이 리포는 TogetherTrip의 Spring Boot 메인 API 서버다. 에이전트와 Codex는 이 파일을 우선 읽고, `docs/agents/repo-context.md`, `docs/agents/quality-gates.md`, `.codex/setup.md`를 함께 따른다.

## 작업 원칙

- 문서와 산출물은 한국어로 작성한다. 명령어, 코드 식별자, 파일 경로, 라이브러리명은 원문을 유지한다.
- 구현 전에는 기획, 도메인 정책, 권한 규칙, 기존 코드 구조를 먼저 확인한다.
- feature-based hexagonal architecture를 기본으로 하고, domain/application/adapter 경계를 지킨다.
- 사용자 개인정보, 인증, 여행/정산 권한, 정산 금액 무결성은 보안 검토 대상으로 본다.
- 실패한 빌드나 테스트는 로그를 기준으로 최소 수정한다.

## 산출물 위치

- 작업 계획: `docs/work-plans/`
- 리뷰 리포트: `docs/reviews/`
- 검증 리포트: `docs/verifications/`
- 인수인계: `docs/handoffs/`
- 아키텍처 결정: `docs/adr/`

## 기본 검증

- `./gradlew test`

## Git 운영 규칙

- 커밋 메시지는 반드시 `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:` 같은 prefix를 붙이고, prefix 뒤의 설명은 한국어로 작성한다.
- 브랜치 이름은 `feature/api-build`, `chore/agent-setup`처럼 `작업성격/작업명` 형식으로 작성한다.
- PR은 항상 작업 브랜치에서 `develop` 브랜치로 생성한다.
- `main` 대상 PR이나 직접 push는 사용자가 명시적으로 요청한 경우에만 진행한다.

## 에이전트 역할

역할 정의는 `docs/agents/roles/`에 있다.

- Planner
- Architect
- TDD Guide
- Code Reviewer
- E2E Test
- Build Fixer
- Security Reviewer
- Verify Agent
- Refactor Cleaner
- Doc Updater

## 명령 이름

- `/togethertrip:workflow`
- `/togethertrip:planner`
- `/togethertrip:architect`
- `/togethertrip:tdd-guide`
- `/togethertrip:code-reviewer`
- `/togethertrip:e2e-test`
- `/togethertrip:build-fixer`
- `/togethertrip:security-reviewer`
- `/togethertrip:verify-agent`
- `/togethertrip:refactor-cleaner`
- `/togethertrip:doc-updater`
