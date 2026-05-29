# Codex Setup

Codex는 이 리포에서 작업할 때 다음 순서를 기본으로 따른다.

1. `AGENTS.md`를 읽고 작업 규칙을 확인한다.
2. `docs/agents/repo-context.md`에서 리포 책임과 경계를 확인한다.
3. `docs/agents/quality-gates.md`에서 검증 명령과 보안 체크를 확인한다.
4. 변경 전 필요한 경우 `docs/work-plans/`에 작업 계획을 남긴다.
5. 구현 후 관련 검증 명령을 실행하고 결과를 요약한다.
6. 리뷰, 검증, 인수인계가 필요한 경우 `docs/agents/templates/`의 형식을 사용한다.

## 공통 규칙

- 산출물은 한국어로 작성한다.
- 명령 실패는 원인 로그를 중심으로 기록한다.
- 외부 API, 인증, 개인정보, 권한, 금액 계산 변경은 보안 리뷰 대상으로 표시한다.
- 문서와 실제 코드가 달라졌다면 Doc Updater 관점으로 문서를 갱신한다.

## Git 운영 규칙

- 커밋 메시지는 `feat: 여행 생성 API 추가`처럼 prefix는 원문으로 두고, 설명은 한국어로 작성한다.
- 커밋 prefix는 생략하지 않는다.
- 브랜치는 `feature/api-build`, `fix/login-token-refresh`, `chore/agent-setup`처럼 `작업성격/작업명` 형식을 사용한다.
- PR은 항상 작업 브랜치에서 `develop` 브랜치로 생성한다.
- `main` 대상 PR, `main` 직접 push, 기본 `codex/` 브랜치명은 사용자가 명시적으로 요청하지 않으면 사용하지 않는다.
