---
description: 이음 표준 구조로 신규 도메인 스캐폴딩
allowed-tools: Read, Write, Glob
---

"$ARGUMENTS" 도메인을 이음 프로젝트 표준 구조로 생성하세요.

## 작업 순서
1. 기존 도메인 하나(store 또는 order)를 참고해 패키지/네이밍 컨벤션을 확인한다
2. 아래 4개 레이어에 파일을 생성한다
3. 생성 후 파일 목록과 다음 작업(엔티티 필드 채우기 등)을 요약한다

## 생성 구조 (4-layer)
```
api/$ARGUMENTS/              ← Controller
application/$ARGUMENTS/
  service/                   ← Service
  dto/                       ← Request/Response DTO
  mapper/                    ← Entity ↔ DTO 매퍼
domain/$ARGUMENTS/
  entity/                    ← JPA 엔티티
  repository/                ← Repository (+ 필요 시 Custom/Impl)
  enums/                     ← 상태 ENUM
```

## URL 경로 규칙
- 일반 사용자 API: `/api/...`
- 사장 API: `/owner/...`
- 관리자 API: `/admin/...`
- 리소스명은 복수형 kebab-case 사용
    - 예: `/used-products`, `/store-reviews`

## 적용 규칙
- 엔티티: `BaseEntity` 상속 (Location, Region, OrderItem 예외 케이스 참고)
- 이미지 엔티티가 필요하면 `ImageBase` 상속
- Service: 읽기 `@Transactional(readOnly = true)` / 쓰기 `@Transactional` 분리
- Controller: `ApiResponse<T>` 래핑, URL은 kebab-case
- 예외: `ErrorCode` enum에 신규 항목 추가 + `BusinessException` 하위 클래스 사용
- 복잡한 조회가 예상되면 `*RepositoryCustom` + `*RepositoryImpl` 골격도 생성

## 주의
- Soft Delete는 추가하지 않는다 (대상: Account, ChatMessage, Category만)
- 알림이 필요한 도메인이면 직접 호출 대신 도메인 이벤트 클래스를 함께 생성한다

## 금지 사항
- 기존 패키지 구조를 임의로 변경하지 않는다
- 기존 공통 응답/예외 구조를 새로 만들지 않는다
- Secret, API Key, 환경변수 값을 생성하거나 하드코딩하지 않는다
- 테스트를 위해 운영 로직을 변경하지 않는다

## 출력 형식
1. 생성한 파일 목록
2. 각 파일 역할 요약
3. 아직 사람이 채워야 할 부분
4. 다음 작업 추천