# 이슈 129 Kotlin JDSL 동적 쿼리 및 SQL Shape 구현 계획

## 목표

- 선택 조건이 null일 때 해당 Predicate를 생성 SQL에서 제거한다.
- Kotlin JDSL을 동적 JPQL 기본 도구로 설정하고 도메인별 QueryRepository 기준 구현을 만든다.
- PostgreSQL 전용 집계와 정산 projection은 native SQL을 유지하되 안전한 고정 fragment로 Shape를 조립한다.
- 기존 API, 권한, 차단, moderation visibility, soft delete, keyset pagination 계약을 유지한다.

## 구현 순서

1. Kotlin JDSL 3.9.0 및 Spring Boot 4 지원 모듈의 컴파일·자동 설정 호환성을 확인한다.
2. moderation Criteria 구현을 JDSL 기준 구현으로 전환한다.
3. 여행·거래 조회를 condition 객체와 custom QueryRepository로 전환한다.
4. 게시글·댓글의 유형·조회자·커서 중복 메서드를 동적 조회 하나로 통합한다.
5. 정산 native projection과 통계 기간 조건을 고정 SQL fragment Shape로 전환한다.
6. 실제 실행계획에서 확인된 참여자 역방향 접근 인덱스를 Flyway migration으로 추가한다.
7. 쿼리 컨벤션, 품질 게이트, PR 체크리스트, 금지 패턴 자동 검사를 추가한다.
8. 단위·통합·커버리지·mutation gate와 대표 실행계획을 검증한다.

## 제외 범위

- 고정 CRUD 쿼리의 일괄 JDSL 전환
- PostgreSQL native enum, jOOQ, Querydsl, MyBatis 도입
- 잠금 및 bulk update native SQL 제거
- API 요청·응답 계약 변경
