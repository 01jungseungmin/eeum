# 단계별 도메인 개발 워크플로

신규 도메인 생성과 기존 도메인의 큰 기능 확장은 아래 순서로 진행한다. 목적은 구현을
한꺼번에 끝낸 뒤 결함을 몰아서 찾는 것이 아니라, 정책·레이어·교차 도메인 위험을 각 단계에서
닫고 다음 단계로 넘어가는 것이다.

## 목차

- [기본 원칙](#기본-원칙)
- [0단계 — 범위와 기준선 수집](#0단계--범위와-기준선-수집)
- [1단계 — 계약·정책 결정](#1단계--계약정책-결정)
- [2단계 — Domain·Persistence](#2단계--domainpersistence)
- [3단계 — Application·Transaction](#3단계--applicationtransaction)
- [4단계 — API·DTO](#4단계--apidto)
- [5단계 — 교차 도메인·운영 정합성](#5단계--교차-도메인운영-정합성)
- [6단계 — 테스트·실행 검증](#6단계--테스트실행-검증)
- [7단계 — 최종 전체 리뷰](#7단계--최종-전체-리뷰)
- [중단 조건](#중단-조건)

## 기본 원칙

- 시작 시 요청이 `스캐폴딩만`인지 `동작하는 기능 완성`인지 구분한다.
- 완성 구현에서 결과를 바꾸는 정책이 비어 있으면 추측하거나 TODO로 숨기지 않고 사용자 결정을 받는다.
- 순수 스캐폴딩은 정책 미정 부분을 TODO로 둘 수 있지만 실행 가능한 완성 기능처럼 보고하지 않는다.
- 각 단계는 산출물, 검증 결과, 남은 결정이 확인되어야 완료된다.
- 단계 게이트에서 발견한 문제를 해결한 뒤 해당 게이트를 다시 통과한다.
- 기존 코드 패턴은 구현 형식의 근거다. 보안·정합성·외부 계약의 올바름을 보증하지 않으므로
  현재 프로젝트 규칙과 전문 리뷰 기준을 함께 적용한다.

## 0단계 — 범위와 기준선 수집

구현 전에 다음을 확인한다.

- 유사 도메인의 Controller, DTO, Service, Entity, Repository, 테스트
- SecurityConfig, ErrorCode, 공통 응답, 이벤트와 스케줄러
- 연계 도메인과 물리 FK 또는 polymorphic 참조
- 관련 운영 DDL/마이그레이션 방식
- 현재 브랜치 및 working tree의 기존 변경

출력할 산출물:

- 구현 범위와 제외 범위
- 참고할 기존 도메인과 선택 이유
- 생성·수정 예상 파일
- 적용할 도메인 참조 문서와 리뷰어

## 1단계 — 계약·정책 결정

코드를 만들기 전에 아래 결정표를 채운다.

| 결정 축 | 확인 내용 |
|---|---|
| API | method/path, 요청·응답, 공개/인증, 하위 호환 |
| 권한 | actor 역할·상태, 소유자/비소유자/관리자 |
| 공개 상태 | active/hidden/deleted/승인/정지별 목록·상세·count·쓰기 |
| 상태 전이 | 허용 전이, 멱등성, 충돌 시 ErrorCode |
| 삭제 | soft/hard, 자식·역참조·이력·정리 스케줄러 |
| 조회 | Page/Slice, 고정 정렬, 검색 조건, N+1 |
| 카운터 | 증가·감소·재계산, 원자성, 기준 데이터 |
| 동시성 | mutex 대상, 잠금 순서, UNIQUE, 재시도 |
| 외부 효과 | 이벤트, AFTER_COMMIT, 외부 API 계약 |
| 운영 | DDL, 기존 데이터 정리, 배포/write-pause |

### Gate 1

- 결과를 바꾸는 미정 정책이 없거나 사용자가 명시적으로 보류했다.
- 보류 항목의 영향 범위와 구현하지 않을 부분이 분리돼 있다.
- 기존 공개 API 변경은 호환 전략 또는 동시 배포 여부가 정해졌다.

Gate 1을 통과하기 전에는 Entity나 Controller를 생성하지 않는다.

## 2단계 — Domain·Persistence

Entity, enum, Repository와 필요한 QueryDSL/DDL을 먼저 구현한다.

확인 사항:

- BaseEntity/ImageBase, LAZY, 팩토리·도메인 메서드
- FK·polymorphic 참조, UNIQUE, cascade와 삭제 순서
- soft delete 및 모든 조회의 필터
- pagination/count 전에 적용되는 공개 조건
- where/order by에 맞는 인덱스와 운영 스키마 반영
- bulk query의 flush/clear와 영향 행 수 처리

### Gate 2

- `review-persistence` 체크리스트를 적용한다.
- 절대 규칙은 가능한 경우 `arch-rules`로 확인한다.
- 엔티티 변경 후 최소 `compileJava`로 Q 클래스와 컴파일을 확인한다.
- DB 실행 의미가 핵심이면 단위 Mock만으로 통과시키지 않고 통합 테스트 계획을 확정한다.

## 3단계 — Application·Transaction

Service, mapper, 이벤트와 스케줄러를 구현한다.

확인 사항:

- 쓰기/읽기 트랜잭션과 self-invocation
- actor 상태, 소유권, target 상태의 트랜잭션 내부 재검증
- 여러 진입점이 공유하는 mutex와 전역 잠금 순서
- bulk update/delete와 영속성 컨텍스트
- AFTER_COMMIT 이벤트와 외부 부작용
- 상태별 정확한 BusinessException/ErrorCode

### Gate 3

- `review-transaction`과 `review-security`를 적용한다.
- 카운터, 상태 전이, check-then-act, 다중 행 변경이 있으면 `concurrency-auditor`를 적용한다.
- 연계 도메인의 반대 방향 쓰기와 정리/탈퇴/관리자 경로까지 교차 추적한다.

## 4단계 — API·DTO

Controller와 요청·응답 DTO를 구현한다.

확인 사항:

- ApiResponse, URL, HTTP method/status, Swagger security
- `@Validated`, `@Valid`, 식별자·컬렉션 요소 Validation
- Entity·민감정보 미노출과 상태별 응답
- Page/Slice content와 metadata, 실제 적용 정렬
- 공용 DTO 필드 의미와 기존 payload 하위 호환
- 쓰기 응답이 실제 변경 후 DB 상태를 반영하는지

### Gate 4

- `review-api-contract`과 `review-security`를 다시 적용한다.
- SecurityConfig와 Swagger의 공개/인증 계약을 대조한다.
- Validator 또는 MockMvc 테스트로 요청 경계와 응답 계약을 고정한다.

## 5단계 — 교차 도메인·운영 정합성

기능이 직접 소유하지 않는 경로를 확인한다.

- 회원 탈퇴·물리 삭제·관리자 조치·신고·복구 작업
- 다른 도메인이 보유한 FK와 polymorphic dangling 데이터
- 전체 재계산·배치·스케줄러와 실시간 쓰기의 경쟁
- 기존 데이터 정리, UNIQUE/인덱스 생성, 배포 순서와 rollback
- 외부 API/Webhook의 공식 계약과 멱등성

도메인별 참조:

- 중고거래·Favorite·Account/Store 연계: `used-favorite-review.md`
- 결제·정산·PortOne: `payment-settlement.md`
- 스레드·DB 연결·소켓 자원: `resource-budget.md`

### Gate 5

- 운영 DDL과 엔티티 정의가 일치한다.
- 삭제·정리 후 역참조 데이터가 남거나 FK 때문에 배치 전체가 실패하지 않는다.
- 동시 쓰기 중단이나 공통 mutex가 필요한 운영 절차가 문서화돼 있다.

## 6단계 — 테스트·실행 검증

위험에 비례해 아래 순서로 검증한다.

1. 단위 테스트: 분기, 상태 전이, 매핑, ErrorCode
2. HTTP/Validation 테스트: 인증, 입력 경계, 직렬화, API 계약
3. JPA/MySQL 통합 테스트: 쿼리, 1차 캐시, FK, UNIQUE, Page/Slice
4. 동시성 테스트: 실제 경쟁 순서, 최종 행·카운터·상태
5. 관련 테스트와 build/compile

동시성 테스트는 sleep이나 경과 시간만으로 락 획득을 추정하지 않는다. barrier, latch,
DB lock-wait 등으로 경쟁 진입을 결정적으로 증명한다.

### Gate 6

- 실패한 테스트가 없고, 미실행 검증은 이유와 위험을 명시했다.
- 단위 Mock으로 검증할 수 없는 DB·락·HTTP 계약을 별도 증거 없이 통과 처리하지 않았다.

## 7단계 — 최종 전체 리뷰

`code-reviewer`를 전체 리뷰 모드로 실행한다. branch diff뿐 아니라 staged/unstaged/untracked와
지정 도메인의 전체 진입점, 연계 도메인, 운영 DDL, 테스트를 포함한다.

완료 조건:

- 모든 전문 리뷰 영역이 통과했거나 남은 항목이 사용자 승인/보류로 분리됐다.
- 수정 과정에서 새로 만든 DTO, 쿼리, 락, 인덱스, 테스트도 재리뷰했다.
- 정적 검토와 실제 테스트 결과를 구분했다.
- `output.md` 형식으로 단계별 결과와 다음 작업을 보고했다.

## 중단 조건

다음은 임의 결정하지 않고 현재 단계에서 멈춰 사용자에게 묻는다.

- 공개 API breaking change
- 인증·소유권·관리자 권한 선택
- soft/hard delete와 이력 보존 정책
- 금액·재고·예약·정산 상태 전이
- 운영 데이터 정리 또는 write-pause
- 서로 다른 안전한 설계가 제품 동작을 다르게 만드는 경우

단순 클래스명, 기존에 확정된 패키지 형식, 테스트 파일 위치처럼 제품 동작을 바꾸지 않는 사항은
기존 규칙으로 결정하고 계속 진행한다.
