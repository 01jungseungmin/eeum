# 레이어별 작성 규칙

## Controller — `api/{domain}/`

- 클래스명: `{DomainName}Controller`
- 응답은 `ApiResponse<T>`로 래핑한다.
- URL은 복수형 kebab-case (예: `/used`, `/store-reviews`)
- Controller에서 비즈니스 검증, Repository 직접 호출을 하지 않는다.
- 로그인 사용자 ID가 필요할 때만 `SecurityUtil.getCurrentAccountId()` 또는
  `getCurrentAccountIdOrNull()`을 사용하며, 어떤 메서드를 쓸지는 기존 유사
  API 패턴 또는 명시된 정책에 따른다.
- PathVariable/RequestParam 제약은 Controller `@Validated`와 함께 적용하고,
  RequestBody는 `@Valid` 및 컬렉션 요소 Validation까지 확인한다.
- Swagger의 공개/인증 표시와 SecurityConfig의 실제 matcher를 일치시킨다.

## Service — `application/{domain}/service/`

- 클래스명: `{DomainName}Service`
- 읽기: `@Transactional(readOnly = true)` / 쓰기: `@Transactional`
- 소유자 검증, 관리자 검증, 사장 권한 검증, 상태 전이 검증, 접근 범위 제한은
  Service에서 처리하고, 검증 로직은 private validate 메서드로 분리한다.
- 서버 결정값은 로그인 사용자 정보나 기존 엔티티를 조회해서 설정한다.
- Service에서 알림을 직접 호출하지 않는다. 알림이 필요하면 기존 이벤트 패턴
  (`ApplicationEventPublisher`)을 확인하고 이벤트 클래스로 분리하거나 TODO로 남긴다.
- 아래 정책이 명시되지 않으면 구현하지 않고 TODO로 남긴다:
  소유자 검증 기준 / 관리자 접근 범위 / 사장 승인 상태 검증 / 상태 전이 /
  soft delete 여부 / 알림 발행 여부 / 카운트 정책 / 지역 제한 정책
- 완성 구현에서는 위 정책을 TODO로 남기기 전에 Gate 1에서 사용자 결정을 받는다.
- 인증 사용자 쓰기는 트랜잭션 안에서 Account 상태와 소유권·대상 상태를 다시 검증한다.
- check-then-act, 카운터, 다중 행 상태 변경은 모든 경쟁 진입점의 공통 mutex와 잠금 순서를 정한다.
- bulk update/delete 뒤 같은 엔티티를 반환하면 flush/clear/refresh와 영향 행 수를 확인한다.

## Repository — `domain/{domain}/repository/`

- 기본: `JpaRepository<Entity, Long>`
- N+1 방지를 위한 EntityGraph, fetch join, QueryDSL 사용 여부는 기존 유사
  도메인 패턴을 따른다.
- 전체 조회와 조건 조회 중 애매하면 기존 목록 조회 패턴을 따른다.
- Repository update 쿼리와 엔티티 도메인 메서드를 같은 상태 변경에 중복
  적용하지 않는다.
- 공개/상태 필터는 Page/Slice와 count 전에 DB 쿼리에서 적용한다.
- 실제 where/order by와 인덱스 컬럼 순서를 대조하고 엔티티 인덱스를 운영 DDL에도 반영한다.
- 복잡 조건은 RepositoryCustom + QueryDSL로 구현하고 JPQL 문자열로 우회하지 않는다.

## Entity — `domain/{domain}/entity/`

- 기본적으로 BaseEntity를 상속한다. 예외 케이스는 Location, Region,
  OrderItem 등 기존 패턴을 참고한다.
- 상태 변경은 가능하면 도메인 메서드로 표현한다. 기존 도메인이 Repository
  update 쿼리를 쓰는 경우에는 기존 방식을 따른다.
- 이미지 엔티티가 필요하면 기존 StoreImage, ProductImage 등의 상속 구조를
  먼저 확인하고 동일하게 적용한다 (표준이 ImageBase 상속이면 그대로).
- softDelete, status, count, regionId 필드를 임의로 추가하지 않는다.
- cascade, orphanRemoval, 연관관계 방향을 임의로 확정하지 않는다.
- UNIQUE와 FK 없는 polymorphic 참조는 중복·dangling 데이터 정리 정책을 함께 설계한다.
- 삭제 시 자식뿐 아니라 다른 도메인이 가진 역방향 FK·polymorphic 참조도 확인한다.

## DTO — `application/{domain}/dto/`

- Request DTO는 클라이언트가 입력하는 값만 가진다. 로그인 사용자 ID,
  소유자 ID, 상태값, 시스템 계산값, Entity/ResponseDto/연관 객체 타입을
  넣지 않는다.
- Response DTO는 화면에 내려줄 조합 데이터를 가진다. Entity를 그대로
  반환하지 않는다.
- 네이밍: `{DomainName}CreateRequestDto` / `{DomainName}UpdateRequestDto` /
  `{DomainName}ResponseDto`. 목록 응답은 기존 도메인 네이밍을 따른다.
- Validation 어노테이션, Builder/Getter/생성자 패턴은 기존 DTO 스타일을 따른다.
- 식별자는 양수 범위, 컬렉션은 컨테이너와 요소의 null/크기/중첩 검증을 모두 적용한다.
- 공용 DTO 필드를 특정 도메인이 무시하지 않는다. 의미가 다르면 전용 DTO로 분리한다.
- 기존 공개 DTO 필드를 제거·변경하면 호환 정책을 Gate 1에서 확정한다.

## Mapper — `application/{domain}/mapper/`

- Entity ↔ DTO 변환 책임만 가진다.
- 정적 메서드 방식 vs Component 방식은 기존 프로젝트 방식을 따른다.
- Mapper에서 SecurityUtil, Repository 호출, 상태 전이 검증을 하지 않는다.
