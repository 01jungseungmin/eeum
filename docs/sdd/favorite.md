# Favorite 도메인 SDD

> 코드 경로: favorite
> 비고: 상점/중고상품 찜

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-9. 찜

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자가 관심 있는 상점, 상품을 저장하여 빠르게 다시 확인할 수 있도록 하는 즐겨찾기 기능으로, 관심 대상 등록, 조회 및 해제 기능을 포함한다.

사용자는 서비스 이용 중 관심 있는 대상을 선택하여 즐겨찾기에 추가할 수 있으며, 해당 정보는 Favorite 엔티티를 통해 관리된다. 즐겨찾기 대상은 상점(Store), 상품(Product) 등 다양한 도메인을 포함할 수 있도록 설계하였다.

각 즐겨찾기 정보는 사용자(Account)와 대상 엔티티를 기반으로 생성되며, 대상의 유형을 구분하기 위해 타입 정보를 함께 저장하도록 하였다. 이를 통해 하나의 구조로 다양한 기능의 즐겨찾기를 통합 관리할 수 있도록 설계하였다.
사용자는 저장한 즐겨찾기 목록을 조회할 수 있으며, 이를 통해 관심 있는 상점이나 상품, 게시글에 빠르게 접근할 수 있도록 하였다. 또한 필요에 따라 즐겨찾기 해제 기능을 제공하여 목록을 유연하게 관리할 수 있도록 하였다.

이와 같은 구조를 통해 사용자 맞춤형 정보 접근성을 향상시키고, 서비스 이용 편의성을 높일 수 있도록 설계하였다.

> 참고: 본문 설명에는 즐겨찾기 대상으로 "상점(Store), 상품(Product)"과 "게시글"이 언급되어 있으나, 5.7절/5.8절 실제 명세 상 `FavoriteRefType` ENUM 값은 `STORE`, `USED_PRODUCT` 두 가지만 정의되어 있다. 원문 그대로 보존함 (Product, 게시글 찜은 실제 ENUM에 반영되지 않은 것으로 보임).

## 5.7 공통 도메인 내 Favorite 명세 (원문 위치)

### ● Entity

«Entity» Favorite
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
favoriteId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 찜한 사용자
refType | FavoriteRefType | ENUM | Polymorphic 타입 (STORE / USED_PRODUCT)
refId | Long | NOT NULL | Polymorphic 참조 ID (FK 아님, Service에서 검증)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» Favorite (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
isOwnedBy(accountId) | boolean | 본인 찜 여부 확인
matches(refType, refId) | boolean | 타입+ID 일치 여부 (토글 검증용)

### ● Controller

«Controller» FavoriteController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /favorites | ResponseEntity<FavoriteResponseDto> | 찜 등록/해제 토글 (refType + refId)
DELETE | /favorites/{favoriteId} | ResponseEntity<CommonResponseDto> | 찜 삭제
GET | /favorites/me | ResponseEntity<Page<FavoriteResponseDto>> | 내 찜 목록 (refType 필터, 최신순)
GET | /favorites/me/{refType} | ResponseEntity<Page<XxxResponseDto>> | 타입별 찜 목록 (Store/UsedProduct 정보 조회)
GET | /favorites/check | ResponseEntity<FavoriteCheckResponseDto> | 특정 대상 찜 여부 조회 (?refType=STORE&refId=123)
GET | /favorites/check/batch | ResponseEntity<List<FavoriteCheckResponseDto>> | 다수 대상 찜 여부 일괄 조회 (목록 화면용 N+1 방지)

«Controller» AdminFavoriteController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /admin/favorites/stats | ResponseEntity<List<FavoriteStatResponseDto>> | 인기 항목 통계 (refType별 상위 N개, 운영지표)

> 참고: 코드베이스에는 `OwnerFavoriteController`도 존재하나(CLAUDE.md/실제 코드 기준), SDD 원문 5.7절에는 별도로 명세되어 있지 않다.

### ● Service

«Service» FavoriteService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
toggleFavorite(accountId, FavoriteToggleRequestDto) | FavoriteToggleResponseDto | REQUIRED | RefValidation + Event | 찜 토글 (있으면 해제, 없으면 등록). refType + refId 유효성 검증 후 처리
deleteFavorite(accountId, favoriteId) | void | REQUIRED | Ownership | 찜 삭제
getMyFavorites(accountId, refType, Pageable) | Page<FavoriteResponseDto> | readOnly | - | 내 찜 목록 (refType 필터 옵션, 최신순)
getMyFavoritesByType(accountId, refType, Pageable) | Page<XxxResponseDto> | readOnly | - | 타입별 찜 목록 + 대상 정보 JOIN (Store / UsedProduct별 분기)
checkFavorite(accountId, refType, refId) | FavoriteCheckResponseDto | readOnly | - | 찜 여부 조회 (상세 화면 진입 시)
checkFavoritesBatch(accountId, refType, List<Long> refIds) | List<FavoriteCheckResponseDto> | readOnly | - | 다수 대상 찜 여부 일괄 조회 (목록 화면 N+1 방지)
getFavoriteCount(refType, refId) | Long | readOnly | - | 대상별 찜 수 (Store / UsedProduct 상세 화면용)
deleteAllByRefTypeAndRefId(refType, refId) | void | REQUIRED | - | 대상 도메인 삭제 시 CASCADE — 다른 도메인 Service에서 호출
deleteAllByAccountId(accountId) | void | REQUIRED | Internal | 회원 탈퇴 시 본인 찜 일괄 삭제 (CASCADE)

«Service» AdminFavoriteService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getFavoriteStats(refType, FavoriteStatsRequestDto) | List<FavoriteStatResponseDto> | readOnly | Admin | 인기 항목 통계 (기간 + refType + 상위 N)

«Helper» FavoriteAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyFavoriteOwnership(accountId, favoriteId) | Favorite | 본인 찜 검증
validateRefTypeAndRefId(refType, refId) | void | refType별로 해당 도메인 존재 검증

### ● Repository

«Repository» FavoriteRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long favoriteId) | Optional<Favorite> | 찜 조회
findByIdAndAccountId(Long favoriteId, Long accountId) | Optional<Favorite> | 본인 찜 검증용
findByAccountIdAndRefTypeAndRefId(Long accountId, FavoriteRefType refType, Long refId) | Optional<Favorite> | 토글 시 기존 찜 조회
existsByAccountIdAndRefTypeAndRefId(Long accountId, FavoriteRefType refType, Long refId) | boolean | 찜 여부 확인
findAllByAccountId(Long accountId, Pageable pageable) | Page<Favorite> | 내 찜 전체 (refType 무관)
findAllByAccountIdAndRefType(Long accountId, FavoriteRefType refType, Pageable) | Page<Favorite> | 타입별 내 찜
findAllByAccountIdAndRefType(Long accountId, FavoriteRefType refType, Pageable pageable) | Long | 대상별 찜 수 (상세 화면 표시용)
deleteByAccountIdAndRefTypeAndRefId(Long accountId, FavoriteRefType refType, Long refId) | void | 토글 해제
deleteAllByRefTypeAndRefId(FavoriteRefType refType, Long refId) | void | 대상 도메인 삭제 시 CASCADE
deleteAllByAccountId(Long accountId) | void | 회원 탈퇴 시 CASCADE

> 참고: 원문에 `findAllByAccountIdAndRefType`가 메서드 시그니처는 동일하지만 반환타입이 다른(Page<Favorite> / Long) 두 항목으로 중복 기재되어 있다. 후자는 실제로는 대상별 찜 수 카운트 메서드(예: `countByRefTypeAndRefId`)로 추정되나, 원문 표기 그대로 보존함.

«Repository» FavoriteRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
findFavoriteRefIdsByAccountIdAndType(Long accountId, FavoriteRefType refType, List<Long> refIds) | Set<Long> | 다수 대상 중 사용자가 찜한 refId Set (목록 화면 배치 조회용)
findFavoriteStats(FavoriteRefType refType, LocalDateTime from, LocalDateTime to, int limit) | List<FavoriteStatProjection> | 인기 항목 통계 (Group By refId + 기간 필터)

## 5.8 ENUM (Favorite 관련)

«Enum» FavoriteRefType
ENUM 값 | 설명
---|---
STORE | 상점 찜
USED_PRODUCT | 중고상품 찜

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-25: 찜 토글

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Favorite (찜)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
favorite_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 찜 고유 식별자
account_id | bigint | FK(account), NOT NULL | 찜한 사용자
ref_type | varchar(20) | NOT NULL | FavoriteRefType ENUM
ref_id | bigint | NOT NULL | Polymorphic 참조ID (FK 아님)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

## 중복 참조 (common.md에도 기재됨)

- 멱등성: 좋아요/찜 토글 작업 멱등성(현재 상태와 무관하게 결과 상태 결정)은 `common.md` 6.5.1, 6.5.4 참조
- 삭제 정책: Favorite는 대상 도메인 삭제 시 `deleteAllByRefTypeAndRefId`로 Hard Delete + CASCADE 처리되며, 이는 `common.md` 6.6.6 "공통 삭제" 트리거 참조
- favoriteCount DB 원자 UPDATE(Store, UsedProduct)는 `store.md`, `used-product.md`, `common.md` 6.1.5 참조
- Polymorphic 참조(`refType` + `refId`)는 FK 없이 사용하는 패턴은 CLAUDE.md 엔티티 작성 규칙 참조
