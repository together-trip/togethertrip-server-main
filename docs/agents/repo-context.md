# Repo Context

## 리포 역할

`main`은 TogetherTrip의 Spring Boot 메인 API 서버다.

## 책임 범위

- 사용자/인증, 카카오 로그인 연동, 회원 프로필
- 여행 생성, 여행 정보, 동행자 초대/제거, 방장 위임
- 기록/소비, 댓글/대댓글, 권한 정책
- 정산 상태, 정산 계산, 송금/수금 확인, 정산 확정
- 탈퇴/퇴장 사용자 표시 정책과 데이터 생명주기

## 아키텍처 원칙

- feature-based hexagonal architecture를 기본으로 한다.
- domain, application use case, adapter 책임을 분리한다.
- Controller는 application use case를 호출하고, domain 규칙을 우회하지 않는다.
- 외부 시스템 접근은 port/adapter 뒤에 둔다.
- 정산 계산과 권한 정책은 테스트 가능한 domain/application 계층에 둔다.

## 통신 규칙

- `app -> gateway -> main` 흐름의 API 진입을 기본으로 한다.
- `main -> notification` 알림 생성/발송 요청을 허용한다.
- 다른 서비스의 DB에 직접 접근하지 않는다.
- 서비스 간 통신 방식이 확정되기 전까지 port/adapter 경계를 유지한다.

## 핵심 도메인 주의점

- 기록은 언제든 등록 가능하지만 소비는 여행 기간과 정산 상태 제약을 따른다.
- 정산 시작 이후 소비 금액 관련 수정/삭제는 불가하다.
- 자기 자신이 발생시킨 행동에는 알림을 보내지 않는다.
- 동행자, 방장, 탈퇴/퇴장 사용자 표시 정책을 UI에서 혼동하지 않는다.
