---
name: test-gen
description: "사용자가 Service 클래스의 JUnit 5, Mockito, AssertJ 기반 Given-When-Then 단위 테스트 생성이나 보강을 요청할 때 사용한다."
---

사용자가 지정한 클래스의 단위 테스트를 작성한다.

## 작업 순서
1. 대상 Service 클래스와 의존하는 Repository/Service 시그니처를 읽는다
2. 각 public 메서드별로 정상 케이스 + 예외 케이스를 도출한다
3. 기존 테스트 파일이 있으면 중복 없이 누락된 케이스만 추가한다

## 테스트 작성 규칙 (이음 프로젝트 표준)
- `@ExtendWith(MockitoExtension.class)` 사용
- 모든 외부 의존성은 `@Mock` 처리 (Repository, RedisLockService, EventPublisher 포함)
- 메서드명은 한글로 작성: `주문_생성_시_재고가_차감된다`
- Given-When-Then 주석으로 3단 구분
- 검증은 AssertJ (`assertThat`) 사용 — JUnit Assertions 금지
- 예외 검증: `assertThatThrownBy(...).isInstanceOf(BusinessException.class)` + ErrorCode까지 검증
  - assertThatThrownBy(() -> ...)
    .isInstanceOf(BusinessException.class)
    .extracting("errorCode")
    .isEqualTo(ErrorCode.xxx);
- 이벤트 발행 검증: `verify(eventPublisher).publishEvent(any(XxxEvent.class))`

## 케이스 도출 기준
- 정상 흐름 1개 이상
- ErrorCode를 던지는 분기마다 예외 케이스 1개씩
- 분산 락이 걸린 메서드는 락 획득 실패 케이스 포함
- 멱등성 처리 메서드는 중복 요청 케이스 포함
- verify로 외부 의존성 호출 여부를 검증한다
- `verifyNoMoreInteractions(...)`는 부수 호출이 있으면 안 되는 핵심 흐름(결제, 재고 차감 등)에만 사용한다 — 일괄 적용하면 리팩토링에 취약한 테스트가 된다
- Repository/Service 메서드가 같은 타입(Long, Long 등) 파라미터를 2개 이상 받는 경우, `verify(...)`에서 `any()`로 뭉뚱그리지 않고 `eq(구체값)`으로 각 인자를 검증한다 — 인자 순서가 바뀌어도 `any()`는 통과하므로 매개변수 순서 오류를 못 잡는다

## 테스트 데이터 빌더 규칙
- 테스트 객체는 private helper method로 생성한다
- 중복되는 Entity/DTO 생성 코드는 `createXxx()` 형태로 분리한다

## 출력
- 테스트 파일 경로: `src/test/java/...` (대상 클래스와 동일 패키지)
- 마지막에 "생성한 케이스 목록 + 커버하지 못한 분기" 요약

## integration-test 필요 여부 안내
이 skill은 Repository를 전부 Mock 처리하므로 Hibernate flush/clear 시점, FK 제약,
실제 쿼리 결과처럼 실제 DB 없이는 검증할 수 없는 버그는 잡지 못한다.
대상 메서드에 아래 신호가 하나라도 있으면 출력 마지막에
"`$integration-test <대상>` 진행 권장 — 이유: ..."를 표시한다.
- 같은 트랜잭션 안에서 엔티티 변경 + `@Modifying` bulk 쿼리가 섞여 있음
- 자기참조 FK 또는 부모-자식 구조 엔티티의 bulk delete/hard delete
- 여러 계정의 데이터가 함께 있어야 정상 동작하는 정책 (지역 매칭, 소유권 등)
- LAZY 연관관계를 트랜잭션 경계 너머로 접근할 가능성
- 분산 락/Pessimistic Lock/Unique 제약이 적용된 쓰기 연산
신호가 없으면 "단위 테스트로 충분 — integration-test 불필요"라고 명시한다.

## 금지 사항
- 기존 패키지 구조를 임의로 변경하지 않는다
- 기존 공통 응답/예외 구조를 새로 만들지 않는다
- Secret, API Key, 환경변수 값을 생성하거나 하드코딩하지 않는다
- 테스트를 위해 운영 로직을 변경하지 않는다

## 출력 형식
1. 생성/수정한 테스트 파일 경로
2. 추가한 테스트 케이스 목록
3. 커버하지 못한 분기
4. Mock 처리한 의존성 목록
