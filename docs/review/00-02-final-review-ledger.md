# 0~2 단계 최종 검토 원장

검토 기준: `98167dca` 이후 현재 `HEAD(6b10d0a5)`의 구현과 전체 결제·정산 진입점, 스케줄러, Flyway DDL을 읽기 전용으로 대조했다. Gradle 및 외부 통합 테스트는 이 검토에 포함하지 않았다.

상태는 `미점검`, `지적됨`, `수정됨`, `재검토 통과`, `정책 보류`로 관리한다. `재검토 통과`는 정적 검토 결과이며 실행 검증 통과를 뜻하지 않는다.

| 단계 | 검토 단위 | 상태 | 결과 |
| --- | --- | --- | --- |
| 0 | 범위·진입점·외부 의존성·스케줄러·DDL·테스트 목록 | 재검토 통과 | 현재 검토 범위와 잔여 지적을 이 원장에 기록했다. |
| 1 | SecurityConfig, JWT, Redis 락·Rate Limit, 예외/응답, DB·Redis·Executor·SchedulerLock, 외부 HTTP | 재검토 통과 | 관리자 정산 API는 `/admin/**` 권한과 서비스의 관리자 확인을 모두 거친다. Redis 락은 DB 락·버전·제약과 함께 사용된다. |
| 2 | 주문·장바구니·재고·Payment·PortOne·AI 플랜·정산·대사 배치 | 재검토 통과 | 결제/취소/AI 상태 전이와 P1·P2·P3, 대사·만료 스케줄러의 처리량 제한을 정적 재검토했다. 실행 검증은 별도다. |

## 현재 도메인 목록

| 영역 | 주요 진입점·상태 | 외부/저장소 의존성 | 스케줄러·DDL·테스트 |
| --- | --- | --- | --- |
| 인증·공통 | SecurityConfig, JwtAuthenticationFilter, RedisLockService | Redis, MySQL | SchedulerLock, Hikari, 공통 예외/ApiResponse |
| 주문·결제 | OrderService, PaymentService, PaymentVerificationProcessor, PaymentCancellationService | PortOne, MySQL, Redis | OrderExpirationScheduler, PaymentReconciliationScheduler, `V21`, `V26` |
| AI 플랜 | AiPlanSubscriptionService, AiPlanPaymentCommandExecutor | PortOne, MySQL, Redis | 결제 만료·구독 만료·취소 대사, `V20`, `V22`~`V24`, `V27` |
| 정산 | WeeklySettlementClosingService, ManualSettlementPayoutService | MySQL, 은행 수동 지급 증빙 | 주간 마감, `V25` |

## 확정된 잔여 지적

| ID | 등급 | 상태 | 위치 | 내용 |
| --- | --- | --- | --- | --- |
| M-01 | Major | 수정됨 | `PaymentReconciliationScheduler`, `AiPlanPaymentCancellationReconciliationScheduler`, 주문·AI 결제 만료 스케줄러, `SchedulingConfig` | 일반 결제 대사는 20건, AI 취소 대사는 상태별 10건으로 제한했다. 주문·AI 결제 만료도 20건, AI 구독 만료·예약 활성화는 각각 100건만 조회한다. PortOne의 10초 타임아웃 기준 AI 취소 대사는 최악 200초로 10분 ShedLock 안에 끝난다. |
| m-01 | Minor | 수정됨 | `README.md:246` | trailing whitespace를 제거했고, 현재 수정 범위의 `git diff --check`를 통과했다. |

## 재검토 통과한 수정 항목

| 항목 | 상태 | 확인 내용 |
| --- | --- | --- |
| P1 주문 가격·옵션 | 재검토 통과 | 주문 시 상품 행 비관적 락 아래 가격·필수 옵션·SINGLE 선택을 재검증하고, 옵션 쓰기 경로도 같은 상품 락을 사용한다. |
| P2 수동 지급 임대·인계 | 재검토 통과 | 30분 임대, 만료 자동 재claim 금지, 증빙 완료/미송금 반환 API, 취소 작업·정산 항목 대사를 확인했다. |
| P3 AI 플랜 | 재검토 통과 | 잔여 기간 연장·다운그레이드 예약·50% 초과 부분 취소 기록·FAILED 뒤 PAID 자동 환불 작업·취소 작업 영속화를 확인했다. |
| PortOne 결제·취소 | 재검토 통과 | raw-body 서명 검증, 단건 조회 대조, 늦은 결제 취소, 전액/부분 취소의 내부 반영과 결제 `@Version` DDL을 확인했다. |
| Flyway/엔티티 | 재검토 통과 | AI 부분 취소 enum, AI 취소 작업/대사 시각, cart/payment version, 정산 gateway 제약이 각각 후속 마이그레이션에 존재한다. |

## 완료 조건

1. 사용자가 실행하는 Gradle 단위·통합·동시성 회귀 결과를 확인한다.
