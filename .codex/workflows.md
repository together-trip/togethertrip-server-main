# Codex Workflows

## 새 기능

Planner -> Architect -> TDD Guide -> 구현 -> Build Fixer -> Code Reviewer -> 필요 시 Security Reviewer -> 사용자 흐름 변경 시 E2E Test -> Verify Agent -> Doc Updater

## 버그 수정

Planner -> TDD Guide -> 구현 -> Build Fixer -> Code Reviewer -> Verify Agent -> 동작이 바뀌면 Doc Updater

## 아키텍처 변경

Planner -> Architect -> TDD Guide -> 구현 -> Code Reviewer -> Verify Agent -> Doc Updater

## 보안 민감 변경

Planner -> Architect -> TDD Guide -> 구현 -> Code Reviewer -> Security Reviewer -> Verify Agent -> Doc Updater

## 문서만 변경

Planner -> Doc Updater -> Verify Agent

## 기본 인수인계 형식

```text
작업:
맥락:
변경 파일:
실행한 명령:
결과:
남은 위험:
다음 추천 에이전트:
```

