# 예약 동시성 — 알려진 경쟁 시나리오

`$concurrency-auditor`가 예약 도메인 감사 시 확인하는 최소 점검 목록이다.
이 목록은 하한선일 뿐이며, 목록 밖 시나리오도 코드에서 도출해야 한다.

## 상태 전이

- 예약: `PENDING → APPROVED`, `PENDING/APPROVED → CANCELLED`

## 예약 생성/확정

- `maxVisitorCount` / `maxTeamCount` 초과가 동시 예약으로 뚫리지 않는가?
- 동일 시간대 중복 예약, 동일 사용자 중복 예약이 가능한가?
- 취소 기한 검증이 상태 변경과 함께 보호되는가?
- 슬롯 조회와 예약 생성 사이 race condition:

```text
예약 가능 여부 검증 → 예약 생성 → 카운트 반영
```

이 흐름 전체가 하나의 락 또는 조건부 INSERT/UPDATE로 보호되는지 확인한다.

## 대응 통합 테스트

- IT-RES-001: 동일 슬롯 동시 예약 → capacity 초과 차단
