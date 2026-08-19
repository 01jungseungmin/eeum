# 레이어별 작성 규칙

## Controller — `api/{domain}/`

- 클래스명: `{DomainName}Controller`
- 응답은 `ApiResponse<T>`로 래핑한다.
- URL은 복수형 kebab-case (예: `/used`, `/store-reviews`)
- Controller에서 비즈니스 검증, Repository 직접 호출을 하지 않는다.
- 로그인 사용자 ID가 필요할 때만 `SecurityUtil.getCurrentAccountId()` 또는
  `getCurrentAccountIdOrNull()`을 사용하며, 어떤 메서드를 쓸지는 기존 유사
  API 패턴 또는 명시된 정책에 따른다.

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

## Repository — `domain/{domain}/repository/`

- 기본: `JpaRepository<Entity, Long>`
- N+1 방지를 위한 EntityGraph, fetch join, QueryDSL 사용 여부는 기존 유사
  도메인 패턴을 따른다.
- 전체 조회와 조건 조회 중 애매하면 기존 목록 조회 패턴을 따른다.
- Repository update 쿼리와 엔티티 도메인 메서드를 같은 상태 변경에 중복
  적용하지 않는다.

## Entity — `domain/{domain}/entity/`

- 기본적으로 BaseEntity를 상속한다. 예외 케이스는 Location, Region,
  OrderItem 등 기존 패턴을 참고한다.
- 상태 변경은 가능하면 도메인 메서드로 표현한다. 기존 도메인이 Repository
  update 쿼리를 쓰는 경우에는 기존 방식을 따른다.
- 이미지 엔티티가 필요하면 기존 StoreImage, ProductImage 등의 상속 구조를
  먼저 확인하고 동일하게 적용한다 (표준이 ImageBase 상속이면 그대로).
- softDelete, status, count, regionId 필드를 임의로 추가하지 않는다.
- cascade, orphanRemoval, 연관관계 방향을 임의로 확정하지 않는다.

## DTO — `application/{domain}/dto/`

- Request DTO는 클라이언트가 입력하는 값만 가진다. 로그인 사용자 ID,
  소유자 ID, 상태값, 시스템 계산값, Entity/ResponseDto/연관 객체 타입을
  넣지 않는다.
- Response DTO는 화면에 내려줄 조합 데이터를 가진다. Entity를 그대로
  반환하지 않는다.
- 네이밍: `{DomainName}CreateRequestDto` / `{DomainName}UpdateRequestDto` /
  `{DomainName}ResponseDto`. 목록 응답은 기존 도메인 네이밍을 따른다.
- Validation 어노테이션, Builder/Getter/생성자 패턴은 기존 DTO 스타일을 따른다.

## Mapper — `application/{domain}/mapper/`

- Entity ↔ DTO 변환 책임만 가진다.
- 정적 메서드 방식 vs Component 방식은 기존 프로젝트 방식을 따른다.
- Mapper에서 SecurityUtil, Repository 호출, 상태 전이 검증을 하지 않는다.
