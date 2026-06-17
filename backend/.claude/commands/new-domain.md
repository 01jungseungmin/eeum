---
description: 이음 표준 구조로 신규 도메인 스캐폴딩
allowed-tools: Read, Write, Glob
---

"$ARGUMENTS" 도메인을 이음 프로젝트 표준 구조로 생성하세요.

## 작업 순서
1. 기존 도메인 하나(store 또는 order)를 참고해 패키지/네이밍 컨벤션을 확인한다
2. 기존 도메인의 Controller, Service, DTO, Entity, Repository 작성 패턴을 확인한다.
3. 아래 4개 레이어에 파일을 생성한다
4. 생성 후 파일 목록과 다음 작업(엔티티 필드 채우기 등)을 요약한다

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

## 기존 코드 우선 규칙
- 새 도메인의 구현 방식은 반드시 기존 도메인의 실제 코드 패턴을 우선한다.
- 판단이 필요한 경우 임의로 정하지 말고, 참고한 기존 도메인과 동일한 방식을 사용한다.
- 기존 코드에서 확인되지 않은 정책은 구현하지 말고 TODO 주석으로 남긴다.
- 기존 도메인에서 목록 조회가 Page 기반이면 Page를 사용하고, Slice는 기존 도메인에서 동일 목적 API가 Slice일 때만 사용한다.
- Slice는 채팅처럼 기존 코드에서 명확히 Slice를 사용하는 유사 사례가 있을 때만 사용한다. 
- URL prefix, 예외 처리, DTO 네이밍, Mapper 방식은 참고한 기존 도메인과 동일하게 작성한다. 
- N+1 방지를 위한 EntityGraph, fetch join, QueryDSL 사용 여부도 기존 도메인 패턴을 따른다.

## 스캐폴딩 범위
- 이 템플릿은 범용 스캐폴딩용이므로, 도메인별 비즈니스 정책을 임의로 추론하지 않는다.
- 실제 비즈니스 정책은 확정하지 않는다.
- 도메인별 정책이 명시되지 않은 필드, 상태값, 조회 조건은 TODO 주석으로 남긴다.
- 임의로 regionId, status, softDelete, count 정책 등을 추가하지 않는다.
- 기존 도메인에서 반복적으로 쓰이는 최소 구조만 생성한다.
- 단순 CRUD 또는 단순 목록 조회만 있는 경우 RepositoryCustom + RepositoryImpl은 생성하지 않는다. 
- RepositoryCustom + RepositoryImpl은 검색 조건, 동적 정렬, 복잡한 조인이 명시된 경우에만 생성한다.

## URL 경로 규칙
- 일반 사용자 API는 기존 Controller 패턴을 따른다.
- 프로젝트에 전역 `/api` prefix가 설정되어 있다면 Controller에는 `/api`를 중복 작성하지 않는다.
- 기존 도메인에서 Controller URL에 /api를 직접 붙이지 않았다면 새 도메인에도 붙이지 않는다.
- 사장 API: `/owner/...`
- 관리자 API: `/admin/...`
- 리소스명은 복수형 kebab-case 사용
  - 예: `/used-products`, `/store-reviews`

## Controller 작성 규칙
- ApiResponse<T>로 응답을 래핑한다. 
- URL은 kebab-case를 사용한다. 
- API가 비회원 접근 가능한지, 로그인 필수인지 임의로 판단하지 않는다. 
- 기존 유사 도메인의 인증 방식을 우선 따른다. 
- 인증 여부가 명시되지 않은 경우 TODO 주석으로 남긴다. 
- SecurityUtil.getCurrentAccountId()와 getCurrentAccountIdOrNull() 사용 여부는 기존 API 패턴 또는 명시된 정책에 따른다.

## Service 작성 규칙
- 읽기 메서드는 @Transactional(readOnly = true)를 사용한다. 
- 쓰기 메서드는 @Transactional을 사용한다. 
- 소유자 검증, 관리자 검증, 사장 권한 검증은 Service에서 private validate 메서드로 분리한다. 
- 소유자 검증, 권한 검증, 상태 전이 검증, 접근 범위 제한 등 비즈니스 정책은 Service 계층에서 처리한다. 
- 서버에서 결정해야 하는 값은 로그인 사용자 정보나 기존 엔티티를 조회해서 설정한다. 
- Request DTO에 정책 우회가 가능한 필드를 임의로 추가하지 않는다.

## Entity 작성 규칙
- 엔티티는 기본적으로 BaseEntity를 상속한다. 
- 예외 케이스는 Location, Region, OrderItem 등 기존 도메인 패턴을 참고한다. 
- 이미지 엔티티가 필요하면 기존 이미지 엔티티(StoreImage, ProductImage 등)의 상속 구조를 확인한 뒤 동일하게 적용한다. 
- 프로젝트 표준이 ImageBase 상속이면 ImageBase를 사용한다. 
- 엔티티 상태 변경은 가능하면 도메인 메서드로 표현한다. 
- 기존 도메인에서 카운트 변경을 엔티티 메서드로 처리하면 엔티티 메서드를 만든다. 
- 기존 도메인이 Repository update 쿼리를 사용하는 경우에는 기존 방식을 따른다. 
- 동일한 상태 변경을 도메인 메서드와 Repository update 쿼리로 중복 구현하지 않는다. 
- 엔티티에 softDelete, status, count 필드를 임의로 추가하지 않는다.

## DTO 작성 규칙
- Request DTO는 클라이언트가 입력하는 값만 가진다.
- Request DTO에 ResponseDto, Entity, 연관 객체 타입을 넣지 않는다.
- Response DTO는 화면에 내려줄 조합 데이터를 가진다.
- 서버에서 결정해야 하는 값은 Request DTO에 넣지 않는다. 
  - 예: 로그인 사용자 ID, 소유자 ID, 상태값, 시스템 계산값 등
- 요청 필드와 응답 필드의 타입/이름은 역할에 맞게 분리한다.

## Repository 작성 규칙
- 기본 Repository는 JpaRepository<Entity, Long> 패턴을 따른다.
- Repository에 전체 조회(findAll)와 조건 조회 중 무엇을 쓸지 애매하면 기존 목록 조회 패턴을 따른다.
- 복잡한 조회가 명시되지 않은 경우 Custom Repository를 임의로 생성하지 않는다.
- N+1 방지를 위한 EntityGraph, fetch join, QueryDSL 사용 여부는 기존 도메인 패턴을 따른다.

## 예외 처리 규칙
- 기존 패턴에 맞춰 ErrorCode enum 항목을 추가한다.
- 예외는 기존 프로젝트 방식에 맞춰 BusinessException(ErrorCode.X) 또는 기존 하위 예외 클래스를 사용한다.
- 도메인별 BusinessException 하위 클래스는 기존 도메인에서 사용 중인 경우에만 생성한다.
- 기존 공통 응답/예외 구조를 새로 만들지 않는다.

## 삭제 정책
- 기본적으로 Soft Delete는 임의로 추가하지 않는다.
- 기존 도메인에서 Soft Delete를 사용하는 경우에만 동일한 패턴을 따른다.
- 부모-자식 구조 보존 등 삭제 정책 판단이 필요한 경우 구현하지 말고 TODO 주석으로 남긴다.
- Hard Delete 시 연관 자식 데이터 삭제 순서를 고려하되, cascade/orphanRemoval 사용 여부는 기존 도메인 패턴을 따른다.

## 알림/이벤트 규칙
- 알림이 필요한 도메인이면 Service에서 알림을 직접 호출하지 않는다.
- 기존 이벤트 패턴을 참고해 도메인 이벤트 클래스를 함께 생성한다.
- 알림 필요 여부가 명시되지 않은 경우 TODO 주석으로 남긴다.

## 금지 사항
- 기존 패키지 구조를 임의로 변경하지 않는다.
- 기존 공통 응답/예외 구조를 새로 만들지 않는다.
- Secret, API Key, 환경변수 값을 생성하거나 하드코딩하지 않는다.
- 테스트를 위해 운영 로직을 변경하지 않는다.
- 도메인 정책을 임의로 확정하지 않는다.

## 출력 형식
1. 생성한 파일 목록
2. 각 파일 역할 요약
3. 아직 사람이 채워야 할 부분
4. 다음 작업 추천