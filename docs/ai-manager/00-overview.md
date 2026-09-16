# 이음(Eeum) 사장 웹 - AI 매니저 1차 MVP 개발 요청

## 프로젝트 배경

이 프로젝트는 사용자와 지역 상점을 연결하는 동네 기반 통합 커머스 플랫폼 **이음(Eeum)** 이다.

사용자는 활동 지역을 기반으로 상점 상품 구매, 매장 방문 예약, 중고 거래, 커뮤니티 활동, 채팅 기능을 이용하며, 사장 회원은 상품 관리, 주문 처리, 이벤트 상품 등록, 공지 관리 기능을 수행한다.

본 프로젝트는 단순 기능 구현을 넘어 실제 서비스 수준의 백엔드 아키텍처 설계를 목표로 하며, 아래 기술/설계 전략이 이미 적용되어 있다.

- Spring Boot 기반 계층형 아키텍처
    - Controller
    - Service
    - Repository
- JWT 기반 인증 + Redis 토큰 관리
- Redis 캐싱 및 분산 락 기반 성능·동시성 제어
- JPA + QueryDSL 기반 데이터 접근 계층
- 이벤트 기반 아키텍처
    - ApplicationEventPublisher
    - @TransactionalEventListener(AFTER_COMMIT)
- 경량 CQRS
    - 읽기: `@Transactional(readOnly = true)`
    - 쓰기 트랜잭션 분리
- 멱등성 보장
    - Redis
    - DB Unique 제약
- Soft Delete / Hard Delete 정책 기반 데이터 관리
- AOP 기반 인증, 감사 로그, 분산 락, 멱등성 처리 분리
- AWS 인프라
    - EC2
    - RDS
    - S3
    - Redis
- GitHub Actions CI/CD

이번 작업도 반드시 위 기조를 유지해야 한다.

너는 이 프로젝트의 **Spring Boot 백엔드 + 프론트 연동 개발자**다.  
현재 프로젝트 구조와 기존 컨벤션을 먼저 분석한 뒤, 사장 웹의 **AI 매니저 1차 MVP**를 구현해줘.

---

# 0. 개발 전 반드시 확인할 것

먼저 아래를 확인하고 기존 스타일을 그대로 따라라.

- Controller / Service / Repository / DTO / Entity 패키지 구조
- Owner 전용 API 인증 방식
- Store 소유자 검증 방식
    - 기존 `getOwnerStore(accountId)` 류 메서드 재사용
- 기존 ErrorCode / Exception 처리 방식
- Swagger 어노테이션 작성 방식
- 기존 도메인 구조
    - Notification
    - EventProduct
    - StoreReview
    - Inquiry
    - Order
    - Product
- 읽기/쓰기 트랜잭션 분리 패턴
- AOP 적용 패턴
    - 감사 로그
    - 분산 락
    - 멱등성
- 기존 테스트 스타일
    - JUnit5
    - Mockito
    - AssertJ
    - Given-When-Then
    - 메서드명 한글 가능

절대 임의로 프로젝트 구조를 새로 만들지 말고, 기존 컨벤션에 맞춰서 구현한다.

---

# 1. 목표

사장 회원이 웹에서 사용할 수 있는 **AI 매니저 기능**을 1차 MVP로 구현한다.

## 핵심 범위

- AI 매니저 메인 대시보드 조회
- AI 고객 케어 카드 조회
- AI 고객 케어 메시지 초안 생성 / 수정 / 검토 후 보내기
- 리뷰/문의 자동 대응 요약 조회
- 리뷰/문의 답변 초안 생성
- 마케팅 자동화 문구 생성
- 공지 등록용 초안 생성 / 등록 / 예약
- 생활권 매칭 분석 조회
- 생활권 매칭 노출 시작 / 상태 조회 / 중지
- 이벤트 성과 조회
- 다음 이벤트 추천
    - 구조화된 프리필 데이터 포함
- 운영 위험 조기정보 조회
- 사장님 실측값 입력
- 절감 계획 생성 / 저장
- 전력 사용 리포트 조회
- AI 활동 요약 조회
- AI 매니저 상담 챗봇
    - 고정 질문
    - 제한된 자유 입력
- 플랜 관리 조회
- 플랜별 기능 게이팅

## 1차에서 제외할 것

1차에서는 아래 기능을 실제로 연동하지 않는다.

- 실제 OpenAI API 연동
- 실제 카카오 알림톡 발송
- 실제 FCM 발송
- 실제 공공데이터 API 호출
- 실제 결제/구독 변경
- 실제 광고 노출 집행

---

# 2. 1차 MVP 개발 원칙

## 2-1. 실제 AI 연동은 하지 않는다

실제 AI 모델 호출 대신 **Template 기반 AI 문구 생성기**를 만든다.

나중에 OpenAI 또는 다른 LLM으로 교체할 수 있도록 인터페이스를 분리한다.

### 인터페이스 예시

```java
public interface AiTextGenerator {
    ...
}
```

### 구현체 예시

```java
@Component
public class TemplateAiTextGenerator implements AiTextGenerator {
    ...
}
```

챗봇 답변도 동일 인터페이스 뒤에서 템플릿 기반으로 처리한다.

---

## 2-2. 실제 외부 발송은 하지 않는다

`검토 후 보내기`, `공지 등록하기`, `예약 발송`은 실제 외부 채널 발송 대신 DB에 상태를 저장한다.

발송 상태는 아래 Enum으로 관리한다.

- DRAFT
- REVIEWED
- SENT
- SCHEDULED
- FAILED
- CANCELLED

기존 Notification 도메인과 쉽게 연결할 수 있게 설계하되, 1차에서는 실제 푸시/알림톡 전송까지 연결하지 않는다.

발송 이벤트가 필요하면 기존 이벤트 기반 아키텍처 패턴으로 발행만 해두고, 리스너는 로그 기록 수준으로 둔다.

- ApplicationEventPublisher 사용
- `@TransactionalEventListener(AFTER_COMMIT)` 사용

---

## 2-3. 실제 데이터 우선, 부족하면 빈 상태 반환

기존 주문, 상품, 이벤트, 리뷰, 문의 데이터가 있으면 그것을 기반으로 집계한다.

데이터가 부족하면 하드코딩된 숫자 대신 아래 형태로 응답한다.

- 빈 리스트
- 0
- null
- `hasData = false`
- `emptyMessage`

단, AI 문구 생성 문장은 템플릿 기반 생성을 허용한다.

---

## 2-4. 동시성 / 멱등성

아래 기능은 중복 클릭 시 1회만 처리되도록 기존 멱등성 패턴을 재사용한다.

- 초안 발송 처리
- 공지 등록
- 예약 발송
- 생활권 노출 시작

사용 가능한 방식:

- Redis
- DB Unique 제약
- 기존 Idempotency AOP
- 기존 분산 락 패턴

사용량 카운트는 동시 요청 시 월 제한을 초과하지 않도록 처리한다.

- 기존 분산 락 사용
- 또는 DB 제약 활용

## 2-5. 추후 실제 AI 연동 대상

1차 MVP에서는 Template 기반으로 처리하지만, 추후 실제 LLM 또는 AI API를 연동할 대상은 아래 기능이다.

- AI 고객 케어 메시지 초안 생성
- 리뷰 답글 초안 생성
- 문의 답변 초안 생성
- 반복 불만 키워드 대응 문구 생성
- 마케팅 자동화 문구 생성
- 공지 등록용 문구 생성
- 이벤트 성과 자연어 요약
- 다음 이벤트 추천 문구 및 추천 이유 생성
- 생활권 매칭 추천 이유 생성
- 운영 위험 조기정보의 AI 판단 문구 생성
- 안전/에너지 체크리스트 추천
- 절감 계획 생성
- 전력 사용 리포트 핵심 진단 생성
- AI 매니저 상담 챗봇 답변 생성

단, 아래 기능은 AI 연동 대상이 아니다.

- 대시보드 숫자 집계
- 고객 수 계산
- 최근 7일 알림 제외 고객 계산
- 발송 상태 변경
- 예약 시간 검증
- 플랜 제한
- 사용량 카운트
- owner/store 권한 검증
- 공공데이터 API 호출
- 알림톡/FCM 실제 발송
- 결제/구독 처리

따라서 실제 AI 연동은 AiTextGenerator 또는 AiInsightGenerator 인터페이스 뒤에서만 수행한다. 
AiTextGenerator는 고객·마케팅 메시지 등 발송용 문구 생성을, AiInsightGenerator는 리스크 판단·매칭 이유·요약 등 분석/판단성 텍스트 생성을 담당한다. 
1차에서는 둘 다 템플릿 구현체(TemplateAiTextGenerator, TemplateAiInsightGenerator)로 시작한다. Controller나 Service에서 OpenAI API를 직접 호출하지 않는다.

---
# 4. 권한 정책

모든 API는 사장 회원만 접근 가능해야 한다.

검증 조건:

- 로그인한 accountId 확인
- 해당 account가 `ROLE_OWNER` 권한인지 확인
- 해당 owner가 소유한 store만 접근 가능
- 다른 사장의 store 또는 메시지 접근 시 예외 발생

```text
AI_FORBIDDEN
```

기존 `getOwnerStore(accountId)` 류 메서드를 재사용한다.

---
