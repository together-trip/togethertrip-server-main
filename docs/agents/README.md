# TogetherTrip 에이전트 셋업

이 문서는 TogetherTrip 리포에서 사용할 에이전트 역할, 소통 방식, 검증 게이트를 정의한다.

## 전제

- 모든 에이전트는 루트 `AGENTS.md`, `.codex/setup.md`, `docs/agents/repo-context.md`, `docs/agents/quality-gates.md`를 먼저 따른다.
- 작업은 작은 단위로 나누고, 각 에이전트는 자기 산출물을 검증 가능한 형태로 남긴다.
- 문서와 에이전트 산출물은 한국어로 작성한다.
- 명령어, 코드 식별자, 파일 경로, 라이브러리명은 원문을 유지한다.

## 에이전트

| 단계 | 에이전트 | 목적 | 역할 프롬프트 |
| --- | --- | --- | --- |
| 기획 | Planner | 구현 계획서 작성 | [planner.md](./roles/planner.md) |
| 기획 | Architect | 아키텍처 설계 검증 | [architect.md](./roles/architect.md) |
| 개발 | TDD Guide | 테스트 주도 개발 안내 | [tdd-guide.md](./roles/tdd-guide.md) |
| 검증 | Code Reviewer | 코드 리뷰 | [code-reviewer.md](./roles/code-reviewer.md) |
| 검증 | E2E Test | 사용자 흐름 검증 | [e2e-test.md](./roles/e2e-test.md) |
| 검증 | Build Fixer | 빌드/테스트 실패 수정 | [build-fixer.md](./roles/build-fixer.md) |
| 검증 | Security Reviewer | 보안 심화 검증 | [security-reviewer.md](./roles/security-reviewer.md) |
| 검증 | Verify Agent | 독립 컨텍스트 검증 | [verify-agent.md](./roles/verify-agent.md) |
| 정리 | Refactor Cleaner | 범위 안 코드 정리 | [refactor-cleaner.md](./roles/refactor-cleaner.md) |
| 문서 | Doc Updater | 문서 갱신 | [doc-updater.md](./roles/doc-updater.md) |

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

## 소통 모델

에이전트는 직접 채팅한다고 가정하지 않는다. 다음 산출물을 통해 비동기 소통한다.

1. Planner가 `docs/work-plans/<task-slug>.md` 초안을 만든다.
2. Architect가 설계와 경계를 승인하거나 수정 요청을 남긴다.
3. TDD Guide가 실패하는 테스트 또는 테스트 목록을 먼저 제안한다.
4. 구현 담당자가 변경한다.
5. Build Fixer가 실패한 빌드/테스트 로그를 기준으로 최소 수정한다.
6. Code Reviewer, Security Reviewer, E2E Test, Verify Agent가 독립 검증한다.
7. Refactor Cleaner가 요청 범위 안에서만 정리한다.
8. Doc Updater가 실제 변경과 문서 차이를 맞춘다.

