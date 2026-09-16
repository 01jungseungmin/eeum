# UsedProduct 도메인 SDD

> 코드 경로: usedProduct, usedProductImage, usedProductReview
> 비고: 중고거래 게시글, 거래 상태, 중고거래 리뷰

## 구현 상태

현재 `used-product` 도메인은 SDD 기준 설계만 존재하며, 실제 코드는 아직 구현 전이다.

- UsedProduct
- UsedProductImage
- UsedProductReview
- UsedProduct 관련 Controller / Service / Repository
- 관리자 중고거래 관리 기능

따라서 sdd-compliance-checker 검토 시 현재 미구현 항목은 코드 결함이 아니라 "구현 예정 기능"으로 분류한다.

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-5. 중고 거래, 중고 거래 리뷰 및 답글

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자 간 중고 상품 거래를 지원하기 위한 기능으로, 게시글 등록, 조회, 상태 관리 및 거래 이후 리뷰 작성 기능을 포함한다.

사용자는 중고 상품을 등록하여 판매할 수 있으며, 게시글 정보는 UsedProduct 엔티티를 통해 관리된다. 각 게시글은 작성자(Account) 및 지역 정보(Region)와 연관되어 지역 기반으로 노출되도록 설계하였다. 게시글에는 제목, 설명, 가격, 카테고리, 조회수 등의 정보가 포함되며, 거래 상태는 판매 중(ON_SALE), 예약 중(RESERVED), 거래 완료(SOLD)로 구분하여 관리한다.

사용자는 특정 게시글에 대해 거래를 완료한 이후 상대방에 대한 리뷰를 작성할 수 있으며, 해당 정보는 UsedReview 엔티티를 통해 관리된다. 리뷰는 대상 사용자(targetAccountId)와 작성자(Account) 간의 관계를 기반으로 생성되며, 평점(rating)과 내용(content)을 포함하여 거래 경험을 평가할 수 있도록 설계하였다.

또한 작성된 리뷰에 대해 답글을 작성할 수 있으며, 해당 정보는 UsedReviewReply 엔티티를 통해 관리된다. 이를 통해 사용자 간 상호 피드백 및 소통이 가능하도록 하였다.

이와 같은 구조를 통해 지역 기반 중고 거래를 지원하고, 거래 이후 리뷰 시스템을 통해 사용자 간 신뢰도를 형성할 수 있도록 설계하였다.

> 참고: 본문 설명에는 UsedReview/UsedReviewReply 명칭이 사용되었으나, 5.4절 실제 클래스 명세에서는 `UsedProductReview`로 명명되어 있고 별도의 `UsedProductReviewReply` 엔티티는 명세되어 있지 않다. 원문 그대로 보존함.

## 5.4 UsedProduct 도메인

### ● Entity

«Entity» UsedProduct
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
usedproductId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 판매자
buyerId | Long | FK, nullable | 구매자 (예약/완료 시 설정)
regionId | Long | FK, NOT NULL | 게시 활동 지역
categoryId | Long | FK,NOT NULL | Category 테이블 참조, type=USED 검증은 Service 레벨
title | String | NOT NULL | 제목
description | String | NOT NULL | 상품 설명
price | BigDecimal | NOT NULL, ≥0 | 희망 가격
tradeLocation | String | nullable | 거래 희망 장소
viewCount | int | DEFAULT 0 | 조회수 (Redis 캐싱 대상)
status | UsedProductStatus | NOT NULL | ON_SALE / RESERVED / SOLD
favoriteCount | int | NOT NULL,Default 0 | 중고거래 좋아요 수
isBuyerConfirmed | boolean | NOT NULL, DEFAULT false | 구매자 거래 완료 확인 여부
version | Long | NOT NULL | 낙관적 락
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» UsedProductReview
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
usedproductreviewId | Long | PK, NOT NULL | 고유 식별자
usedproductId | Long | FK, NOT NULL,UNIQUE | 연관 주문(중복 방지)
accountId | Long | FK, NOT NULL | 작성자
rating | int | NOT NULL, 1~5 | 별점
content | String | NOT NULL | 리뷰 내용
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» UsedProduct (도메인 메서드)
메서드명 | 타입 | 설명
---|---|---
reserve(buyerId) | void | ON_SALE 상태에서만 예약 처리 (buyerId 설정, RESERVED 변경)
cancelReservation() | void | RESERVED 상태에서 판매자가 예약 취소 → ON_SALE 복귀
confirmPurchase(buyerId) | void | 구매자 거래 완료 확인 (isBuyerConfirmed = true)
completeSale(accountId) | void | 구매자 확인 이후 판매자가 SOLD 처리
changeStatus(status) | void | 내부 상태 변경 (외부 직접 호출 금지)
incrementViewCount() | void | 조회수 증가 (Redis 경유)
isEditable(accountId) | boolean | 판매자이며 ON_SALE 상태일 때만 수정 가능
isOwnedBy(accountId) | boolean | 판매자 본인 여부 확인
isDeletable(accountId) | boolean | 판매자이며 SOLD가 아닐 경우 삭제 가능
isPurchasable() | boolean | ON_SALE 상태 여부 반환

### ● Controller

«Controller» UsedProductController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /used-products | ResponseEntity<UsedProductResponseDto> | 중고 게시글 등록
PATCH | /used-products/{productId} | ResponseEntity<UsedProductResponseDto> | 중고 게시글 수정
DELETE | /used-products/{productId} | ResponseEntity<CommonResponseDto> | 중고 게시글 삭제
GET | /used-products | ResponseEntity<Page<UsedProductResponseDto>> | 중고 게시글 목록 조회
GET | /used-products/{productId} | ResponseEntity<UsedProductDetailResponseDto> | 중고 게시글 상세 조회
POST | /used-products/{productId}/reserve | ResponseEntity<CommonResponseDto> | 중고 거래 예약
PATCH | /used-products/{productId}/reserve/cancel | ResponseEntity<CommonResponseDto> | 거래 예약 취소 (RESERVED → ON_SALE)
POST | /used-products/{productId}/confirm | ResponseEntity<CommonResponseDto> | 거래 완료 처리 (구매자 확정 → SOLD)
POST | /used-products/{productId}/images | ResponseEntity<UsedProductImageResponseDto> | 중고 거래 상품 이미지 추가
DELETE | /used-products/{productId}/images/{imageId} | ResponseEntity<CommonResponseDto> | 중고 거래 상품 이미지 삭제
PATCH | /used-products/{productId}/images/{imageId}/thumbnail | ResponseEntity<CommonResponseDto> | 중고 거래 상품 대표 이미지 지정

«Controller» UsedProductReviewController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /used-products/{productId}/reviews/{reviewId} | ResponseEntity<UsedProductReviewDetailResponseDto> | 리뷰 상세 조회
POST | /used-products/{productId}/reviews | ResponseEntity<UsedProductReviewResponseDto> | 중고 거래 리뷰 작성
PATCH | /used-products/{productId}/reviews/{reviewId} | ResponseEntity<UsedProductReviewResponseDto> | 중고 거래 리뷰 수정 (본인만)
DELETE | /used-products/{productId}/reviews/{reviewId} | ResponseEntity<CommonResponseDto> | 중고 거래 리뷰 삭제 (본인만)

«Controller» AdminUsedProductController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /admin/used-products | ResponseEntity<Page<UsedProductResponseDto>> | 전체 중고 상품 조회 (상태/지역/기간 필터)
GET | /admin/used-products/{productId} | ResponseEntity<UsedProductDetailResponseDto> | 중고 상품 상세 조회
DELETE | /admin/used-products/{productId} | ResponseEntity<CommonResponseDto> | 중고 상품 강제 삭제 (신고 처리)
GET | /admin/used-products/reviews | ResponseEntity<Page<UsedProductReviewResponseDto>> | 전체 리뷰 조회
DELETE | /admin/used-products/reviews/{reviewId} | ResponseEntity<CommonResponseDto> | 리뷰 강제 삭제

### ● Service

«Service» UsedProductService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getUsedProductList(UsedProductSearchDto) | Page<UsedProductResponseDto> | readOnly | QueryDSL | 중고 상품 목록 조회
getUsedProductDetail(usedProductId) | UsedProductDetailResponseDto | readOnly | - | 중고 상품 상세 조회 (조회수 증가 포함)
registerUsedProduct(accountId, UsedProductCreateRequestDto) | UsedProductResponseDto | REQUIRED | - | 중고 상품 등록
updateUsedProduct(accountId, usedProductId, UsedProductUpdateRequestDto) | UsedProductResponseDto | REQUIRED | Ownership | 중고 상품 수정
deleteUsedProduct(accountId, usedproductId) | void | REQUIRED | Ownership | 중고 상품 삭제
reserveUsedProduct(accountId, usedProductId) | CommonResponseDto | REQUIRED | OptimisticLock + Event | 중고 상품 예약
cancelReservation(accountId, usedproductId) | CommonResponseDto | REQUIRED | Ownership + RESERVED | 중고 상품 예약 취소
confirmPurchase(accountId, usedProductId) | CommonResponseDto | REQUIRED | BuyerOnly + RESERVED + Event | 구매자 거래 완료 확정
getMyUsedProducts(accountId, Pageable) | Page<UsedProductResponseDto> | readOnly | - | 내가 등록한 중고 상품
getMyPurchasedUsedProducts(accountId, Pageable) | Page<UsedProductResponseDto> | readOnly | - | 내가 거래한 중고 상품
incrementViewCount(usedProductId) | void | REQUIRED | REDIS | 조회수 증가 (Redis 집계 → 5분 주기 배치 동기화)
addUsedProductImage(accountId, usedProductId, imageUrl) | UsedProductImageResponseDto | REQUIRED | Ownership + ImageService | 상품 이미지 추가
deleteUsedProductImage(accountId, usedProductId, imageId) | void | REQUIRED | Ownership + ImageService | 상품 이미지 삭제
setUsedProductImageThumbnail(accountId, usedProductId, imageId) | void | REQUIRED | Ownership + ImageService | 상품 대표 이미지 지정
incrementFavoriteCount(usedProductId) | void | REQUIRED | Internal | 상품 찜 개수 증가
decrementFavoriteCount(usedProductId) | void | REQUIRED | Internal | 상품 찜 개수 감소

«Service» UsedProductReviewService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getUsedProductReviewDetail(reviewId) | UsedProductReviewDetailResponseDto | readOnly | - | 리뷰 상세 조회
registerUsedProductReview(accountId, usedproductId, UsedProductReviewCreateRequestDto) | UsedProductReviewResponseDto | REQUIRED | UNIQUE(productId) + BuyerOnly | 리뷰 등록
updateUsedProductReview(accountId, reviewId, UsedProductReviewUpdateRequestDto) | UsedProductReviewResponseDto | REQUIRED | Ownership | 리뷰 수정
deleteUsedProductReview(accountId, reviewId) | void | REQUIRED | Ownership | 리뷰 삭제
getMyReceivedUsedReviews(accountId, Pageable) | Page<UsedProductReviewResponseDto> | readOnly | | 내가 받은 중고거래 리뷰
getMyWrittenUsedReviews(accountId, Pageable) | Page<UsedProductReviewResponseDto> | readOnly | | 내가 작성한 중고거래 리뷰

«Service» AdminUsedProductService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getAllUsedProducts(Pageable, filter) | Page<UsedProductResponseDto> | readOnly | Admin | 전체 중고 상품 조회
getUsedProductDetail(usedProductId) | UsedProductDetailResponseDto | readOnly | Admin | 중고 상품 상세
forceDeleteUsedProduct(usedProductId) | void | REQUIRED | Admin | 중고 상품 강제 삭제
getAllReviews(Pageable) | Page<UsedProductReviewResponseDto> | readOnly | Admin | 전체 리뷰 조회
forceDeleteReview(reviewId) | void | REQUIRED | Admin | 중고 상품 리뷰 강제 삭제

«Helper» UsedProductAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyOwnership(accountId, usedProductId) | UsedProduct | 판매자 본인 검증 → 실패 시 ForbiddenException
verifyBuyer(accountId, usedProductId) | UsedProduct | 예약된 구매자 본인 검증
verifyOnSale(usedProductId) | UsedProduct | ON_SALE 상태 검증
verifyReserved(usedProductId) | UsedProduct | RESERVED 상태 검증

### ● Repository

«Repository» UsedProductRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long usedproductId) | Optional<UsedProduct> | 기본 조회
findByIdForUpdate(Long usedproductId) | Optional<UsedProduct> | @Lock(PESSIMISTIC_WRITE) / 거래 상태 변경 시 사용
findAll(Pageable pageable) | Page<UsedProduct> | 전체 상품 목록 (페이징)
findAllByRegionId(Long regionId, Pageable pageable) | Page<UsedProduct> | 지역 기반 상품 목록
findAllByCategoryId(Long categoryId, Pageable pageable) | Page<UsedProduct> | 카테고리 기반 상품 목록
findAllByStatus(UsedProductStatus status, Pageable pageable) | Page<UsedProduct> | 상태별 상품 목록 (ON_SALE / RESERVED / SOLD)
countByAccountId(Long accountId) | Long | 판매자 상품 수
countByBuyerId(Long buyerId) | Long | 구매자 거래 수
incrementFavoriteCount(Long usedproductId) | int | 찜 카운트 +1 (DB 원자 UPDATE, 영향받은 행 수 반환)
decrementFavoriteCount(Long usedproductId) | int | 찜 카운트 -1 (favoriteCount > 0 가드, 영향받은 행 수 반환)

«Repository» UsedProductRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchProducts(ProductSearchDto condition, Pageable pageable) | Page<UsedProduct> | 지역 + 카테고리 + 키워드 + 정렬 복합 검색
findNearbyProducts(NearbySearchCondition condition) | List<UsedProduct> | 위치 기반 상품 검색

«Repository» UsedProductImageRepository
메서드명 | 반환타입 | 설명
---|---|---
findAllByUsedproductIdOrderByDisplayOrder(Long usedproductId) | List<UsedProductImage> | 중고상품 이미지 목록 (순서대로)
findByUsedproductIdAndIsThumbnailTrue(Long usedproductId) | Optional<UsedProductImage> | 대표 이미지 조회
findByIdAndUsedproductId(Long imageId, Long usedproductId) | Optional<UsedProductImage> | 소유권 확인용 조회
countByUsedproductId(Long usedproductId) | Long | 상품 이미지 개수 (제한 검증용)
findFirstByUsedproductIdOrderByDisplayOrderAsc(Long usedproductId) | Optional<UsedProductImage> | 대표 자동 승격용
deleteAllByUsedproductId(Long usedproductId) | void | 게시글 삭제 시 CASCADE

«Repository» UsedProductReviewRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long usedproductReviewId) | Optional<UsedProductReview> | 리뷰 ID로 조회
findByUsedProductId(Long usedproductId) | Optional<UsedProductReview> | 상품별 리뷰 조회
existsByUsedProductId(Long usedproductId) | boolean | 리뷰 존재 여부
countByAccountId(Long accountId) | Long | 사용자 리뷰 수

## 5.8 ENUM (UsedProduct 관련)

«Enum» UsedProductStatus
ENUM 값 | 설명
---|---
ON_SALE | 판매중
RESERVED | 예약됨
SOLD | 판매 완료

«Enum» AuditActionType (UsedProduct 관련, 중복 참조 — account.md 참조)
ENUM 값 | 설명
---|---
USED_PRODUCT_FORCE_DELETE | 중고상품 삭제
USED_REVIEW_FORCE_DELETE | 중고 리뷰 삭제

«Enum» NotificationType (UsedProduct 관련, 중복 참조 — notification.md 참조)
ENUM 값 | 설명
---|---
USED_PRODUCT_RESERVED | 상품 예약됨
USED_PRODUCT_SOLD | 상품 판매 완료
USED_PRODUCT_REVIEW_NEW | 거래 리뷰 등록

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-17: 중고상품 등록
- DSEQ-18: 거래 완료 처리

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.2 도메인 ERD (참조)

● UsedProduct 도메인
[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### UsedProduct (중고 상품)

| 컬럼명             | 타입          | 설명                         | 설명                              |
| ------------------ | ------------- | ---------------------------- | --------------------------------- |
| used_product_id    | bigint        | PK, NOT NULL, AUTO_INCREMENT | 중고 상품 고유 식별자             |
| account_id         | bigint        | FK(account), NOT NULL        | 판매자                            |
| buyer_id           | bigint        | FK(account), nullable        | 구매자(예약/완료 시 설정)         |
| region_id          | bigint        | FK(region), NOT NULL         | 게시 활동 지역                    |
| category_id        | bigint        | FK(category), NOT NULL       | 카테고리(type=USED 검증은Service) |
| title              | varchar(100)  | NOT NULL                     | 제목                              |
| description        | text          | NOT NULL                     | 상품 설명                         |
| price              | decimal(10,2) | NOT NULL, ≥0                 | 희망 가격                         |
| trade_location     | varchar(255)  | nullable                     | 거래 희망 장소                    |
| view_count         | int           | NOT NULL, DEFAULT 0          | 조회수(Redis 캐싱)                |
| status             | varchar(20)   | NOT NULL                     | UsedProductStatus ENUM            |
| favorite_count     | int           | NOT NULL, DEFAULT 0          | 중고거래 좋아요 수                |
| is_buyer_confirmed | boolean       | NOT NULL, DEFAULT false      | 구매자 거래 완료 확인 여부        |
| version            | bigint        | NOT NULL                     | 낙관적 락(@Version)               |
| created_at         | datetime      | NOT NULL                     | BaseEntity 상속                   |
| modified_at        | datetime      | NOT NULL                     | BaseEntity 상속                   |

### UsedProductImage (중고 상품 이미지)

| 컬럼명                | 타입         | 설명                         | 설명                               |
| --------------------- | ------------ | ---------------------------- | ---------------------------------- |
| used_product_image_id | bigint       | PK, NOT NULL, AUTO_INCREMENT | 이미지 고유 식별자(ImageBase 상속) |
| used_product_id       | bigint       | FK(used_product), NOT NULL   | 연관 중고 상품                     |
| image_url             | varchar(500) | NOT NULL                     | S3 이미지URL                       |
| display_order         | int          | NOT NULL, DEFAULT 0          | 표시 순서                          |
| is_thumbnail          | boolean      | NOT NULL, DEFAULT false      | 대표 이미지 여부                   |
| created_at            | datetime     | NOT NULL                     | BaseEntity 상속                    |
| modified_at           | datetime     | NOT NULL                     | BaseEntity 상속                    |

### UsedProductReview (중고 거래 리뷰)

| 컬럼명                 | 타입     | 설명                               | 설명                       |
| ---------------------- | -------- | ---------------------------------- | -------------------------- |
| used_product_review_id | bigint   | PK, NOT NULL, AUTO_INCREMENT       | 중고 거래 리뷰 고유 식별자 |
| used_product_id        | bigint   | FK(used_product), UNIQUE, NOT NULL | 연관 거래(중복 방지)       |
| account_id             | bigint   | FK(account), NOT NULL              | 작성자                     |
| rating                 | int      | NOT NULL, 1~5                      | 별점                       |
| content                | text     | NOT NULL                           | 리뷰 내용                  |
| created_at             | datetime | NOT NULL                           | BaseEntity 상속            |
| modified_at            | datetime | NOT NULL                           | BaseEntity 상속            |

## 중복 참조 (common.md에도 기재됨)

- Redis 분산 락 키: `used-product:trade:{usedproductId}` (TTL 5s, 중고거래 상태 변경)은 `common.md` 6.1.2 참조
- 비관적 락: `UsedProductRepository.findByIdForUpdate`(거래 상태 변경)는 `common.md` 6.1.3 참조
- 낙관적 락 적용 대상 Entity(UsedProduct)는 `common.md` 6.1.4 참조
- 중고상품 조회수 Redis Write-Through 동기화(`view:used:{usedProductId}`)는 `common.md` 6.4.1, 6.4.3 참조
- favoriteCount DB 원자 UPDATE(증감) 패턴은 `common.md` 6.1.5 참조
