# Store 도메인 SDD

> 코드 경로: store, product, eventProduct, productCategory, productOption, storeReview, storeNotice, storeImage
> 비고: 상점, 상품, 이벤트상품, 상점리뷰 포함

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-2. 동네 상점 관리

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 지역 기반 상점 운영을 위한 기능으로, 상점 등록 및 관리, 상품 등록 및 관리, 이벤트 상품 설정 기능을 포함한다.

사장 회원은 Account 엔티티를 기반으로 자신의 상점을 생성 및 관리할 수 있으며, 상점 정보는 Store 엔티티를 통해 관리된다. 각 상점은 특정 지역에 소속되며, Region과의 연관 관계를 통해 사용자에게 지역 기반으로 노출되도록 설계하였다.

상점에는 다수의 상품을 등록할 수 있으며, 상품 정보는 Product 엔티티를 통해 관리된다. 상품은 가격, 재고, 판매 상태 등의 정보를 포함하며, 상점 단위로 관리된다.

또한 특정 상품에 대해 할인 이벤트를 적용할 수 있으며, 해당 정보는 EventProduct 엔티티를 통해 관리된다. 이벤트는 시작일과 종료일을 기준으로 활성 상태를 판단하며, 할인 가격을 통해 사용자에게 혜택을 제공하도록 설계하였다.

이와 같은 구조를 통해 사장 회원은 상점과 상품을 효율적으로 관리할 수 있으며, 사용자는 자신의 지역에 맞는 상점과 상품 정보를 제공받을 수 있도록 하였다.

### DCOM-4. 동네 상점 리뷰 및 답글

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자가 상점 이용 후 리뷰를 작성하고, 상점 운영자가 이에 대해 답글을 작성할 수 있도록 하는 기능으로, 상점에 대한 평가 및 리뷰를 관리한다.

사용자는 주문을 통해 상품을 이용한 이후 해당 상점에 대해 리뷰를 작성할 수 있으며, 리뷰 정보는 StoreReview 엔티티를 통해 관리된다. 각 리뷰는 특정 상점(Store)과 사용자(Account), 그리고 주문(Order)과 연관되어 실제 이용 기반의 신뢰성 있는 평가가 가능하도록 설계하였다.

리뷰에는 평점(rating)과 내용(content)이 포함되며, 이를 통해 상점에 대한 사용자 경험을 정량적, 정성적으로 표현할 수 있도록 하였다.

상점 운영자는 작성된 리뷰에 대해 답글을 작성할 수 있으며, 해당 정보는 StoreReviewReply 엔티티를 통해 관리된다. 각 답글은 특정 리뷰와 연관되며, 이를 통해 사용자와 상점 간의 소통 및 피드백 반영이 가능하도록 설계하였다.

이와 같은 구조를 통해 상점에 대한 신뢰도 형성과 사용자 경험 개선을 지원하며, 사용자 간의 평가 정보를 기반으로 서비스 품질을 향상시킬 수 있도록 하였다.

## 5.2 Store 도메인

### ● Entity

«Entity» Store
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
storeId | Long | PK, NOT NULL | 상점 고유 식별자
accountId | Long | FK, UNIQUE, NOT NULL | 상점 소유자 (사장 1:1 상점 제약)
regionId | Long | FK, NOT NULL | 상점 소속 지역
categoryId | Long | FK, NOT NULL | 상점 카테고리
latitude | Double | NOT NULL | 상점 위도
longitude | Double | NOT NULL | 상점 경도
name | String | NOT NULL | 상점명
address | String | NOT NULL | 주소
phone | String | NOT NULL | 상점 연락처
description | String | nullable | 상점 소개
businessHours | String | NOT NULL | 영업시간
rating | Double | NOT NULL,Default 0.0 | 평균 평점 수(캐싱) -> 리뷰 변경 시 갱신
favoriteCount | int | NOT NULL,Default 0 | 상점 좋아요 수
reviewCount | int | NOT NULL,Default 0 | 리뷰 수(캐싱) -> 리뷰 변경 시 갱신
status | StoreStatus | NOT NULL | OPEN / CLOSED / TEMP_CLOSED
version | Long | NOT NULL | 낙관적 락용 (@Version) — 평점 갱신 동시성 제어
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» StoreNotice
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
noticeId | Long | PK, NOT NULL | 고유 식별자
storeId | Long | FK, NOT NULL | 연관 상점
noticeType | NoticeType | NOT NULL, ENUM | 공지 유형 (GENERAL, HOLIDAY, EVENT 등)
content | String | NOT NUL | 공지 상세 내용
isActive | boolean | NOT NULL, DEFAULT true | 공지 노출 여부 (true: 노출, false: 비노출)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» StoreImage
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
storeimageId | Long | PK, NOT NULL | 이미지 고유 식별자(ImageBase 상속)
storeId | Long | FK,, NOT NULL | 연관 상점
imageUrl | String | NOT NULL | 이미지 URL(ImageBase 상속)
displayOrder | int | NOT NULL, DEFAULT 0 | 표시 순서(ImageBase 상속)
isThumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부(ImageBase 상속)
createdAt | LocalDateTime | NOT NULL | ImageBase 상속
modifiedAt | LocalDateTime | NOT NULL | ImageBase 상속

«Entity» ProductCategory
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
productcategoryId | Long | PK, NOT NULL | 상품 카테고리 고유 식별자
storeId | Long | FK, NOT NULL | 소속 상점
name | String | NOT NULL | 상품 카테고리
displayOrder | int | NOT NULL, DEFAULT 0 | 표시 순서
isActive | boolean | NOT NULL, DEFAULT true | 노출 여부
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» Product
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
productId | Long | PK, NOT NULL | 상품 고유 식별자
storeId | Long | FK, NOT NULL | 소속 상점
productcategoryId | Long | FK, NOT NULL | 상품 카테고리
name | String | NOT NULL | 상품명
description | String | NOT NULL | 상품 설명
price | BigDecimal | NOT NULL, ≥0 | 가격
stock | int | NULLABLE, ≥0 | 재고 수량 (null=무제한)
viewCount | int | NOT NULL, DEFAULT 0 | 조회수 (Redis 실시간 집계 → 5분 주기 배치 동기화)
status | ProductStatus | NotNull,DEFAULT 'ACTIVE' | ACTIVE/SOLD_OUT
version | Long | NOT NULL | 낙관적 락용 (@Version) — 재고 차감 동시성 제어
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» ProductImage
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
productimageId | Long | PK, NOT NULL | 이미지 고유 식별자(ImageBase 상속)
productId | Long | FK, NOT NULL | 연관 상품
imageUrl | String | NOT NULL | 이미지 URL(ImageBase 상속)
displayOrder | int | NOT NULL, DEFAULT 0 | 표시 순서(ImageBase 상속)
isThumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부(ImageBase 상속)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» ProductOption
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
productoptionId | Long | PK, NOT NULL | 고유 식별자
productId | Long | FK, NOT NULL | 연관 상품
groupName | String | NOT NULL | 옵션 그룹 이름 (예: 용량, 매운맛)
selectionType | OptionSelectionType | NOT NULL, DEFAULT 0 | 옵션 선택 방식 (단일 선택 / 다중 선택)
isRequired | boolean | NOT NULL, DEFAULT false | 해당 옵션 선택 필수 여부
displayOrder | int | NOT NULL, DEFAULT SINGLE | 옵션 항목 노출 순서
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» ProductOptionItem
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
productoptionitemId | Long | PK, NOT NULL | 고유 식별자
productoptionId | Long | FK, NOT NULL | 연관 상품옵션
itemName | String | NOT NULL | 옵션 선택 값 (예: 300g, 매운맛)
additionalPrice | BigDecimal | NOT NULL, DEFAULT 0 | 선택 시 추가되는 금액
isDefault | boolean | NOT NULL, DEFAULT false | 기본 선택 여부
displayOrder | int | NOT NULL, DEFAULT 0 | 옵션 항목 노출 순서
isAvailable | boolean | DEFAULT true | 옵션 선택 가능 여부 (품절/비활성 상태)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» EventProduct
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
eventproductId | Long | PK, NOT NULL | 고유 식별자
productId | Long | FK, NOT NULL | 연관 상품 (동시에 ACTIVE 1개만)
activeProductId | Long | GENERATED COLUMN, unique | status = 'ACTIVE'인 경우 productId 값, 그 외 NULL → 동일 productId에 ACTIVE 이벤트 1개만 허용
discountRate | int | NOT NULL, 0~99 | 할인율(%)
stock | int | NULLABLE, ≥0 | 이벤트 수량 (null=무제한)
startDate | LocalDateTime | NOT NULL | 이벤트 시작일
endDate | LocalDateTime | NOT NULL | 이벤트 종료일
status | EventStatus | NOT NULL | ACTIVE / ENDED
version | Long | NOT NULL | 낙관적 락용 (@Version) — 이벤트 재고 차감
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» StoreReview
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
storereviewId | Long | PK, NOT NULL | 고유 식별자
storeId | Long | FK, NOT NULL | 연관 상점
accountId | Long | FK, NOT NULL | 작성자
orderId | Long | FK, NOT NULL,UNIQUE | 1주문 1리뷰 보장
rating | int | NOT NULL, 1~5 | 별점
content | String | NOT NULL | 리뷰 내용
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» StoreReviewImage
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
storereviewimageId | Long | PK, NOT NULL | 이미지 고유 식별자(ImageBase 상속)
storeReviewId | Long | FK, NOT NULL | 연관 상점 리뷰
imageUrl | String | NOT NULL | 이미지 URL(ImageBase 상속)
displayOrder | int | NOT NULL, DEFAULT 0 | 표시 순서(ImageBase 상속)
isThumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부(ImageBase 상속)
createdAt | LocalDateTime | NOT NULL | ImageBase 상속
modifiedAt | LocalDateTime | NOT NULL | ImageBase 상속

«Entity» StoreReviewReply
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
storereplyId | Long | PK, NOT NULL | 고유 식별자
storereviewId | Long | FK, UNIQUE, NOT NULL | 연관 리뷰(1:1)
accountId | Long | FK, NOT NULL | 답글 작성자(사장 회원)
content | String | NOT NULL | 답글 내용
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» Store (도메인 메서드)
메서드명 | 반환 타입 | 설명
---|---|---
open() | void | 상태 OPEN 변경
close() | void | 상태 CLOSED 변경
temporaryClose() | void | 상태 TEMP_CLOSED 변경
updateStoreInfo(dto) | void | 상점 정보 수정 (이름, 주소, 전화번호 등)
updateRating(newRating, newCount) | void | 평균 평점 재계산
increaseReviewCount() | void | 리뷰 작성 시 호출
decreaseReviewCount() | void | 리뷰 삭제 시 호출
isOwnedBy(accountId) | boolean | 소유권 확인

«Entity» ProductCategory (도메인 메서드)
메서드명 | 반환 타입 | 설명
---|---|---
activate() | void | 활성화
deactivate() | void | 비활성화 (Soft Delete)
updateDisplayOrder(int) | void | 표시 순서 변경
isOwnedBy(storeId) | boolean | 본인 매장 카테고리 검증

«Entity» Product (도메인 메서드)
메서드명 | 반환 타입 | 설명
---|---|---
decreaseStock(qty) | void | 재고 차감→ 0 이하 시 SOLD_OUT 처리
increaseStock(qty) | void | 재고 복구(취소/환불 시)
isSoldOut() | boolean | 품절 여부 반환
markAsSoldOut() | void | 수동 품절 처리 (stock이 null일 때 사장이 직접)
markAsAvailable() | void | 판매 재개
hasUnlimitedStock() | boolean | 재고 무제한 여부 (stock == null)

«Entity» EventProduct (도메인 메서드)
메서드명 | 반환 타입 | 설명
---|---|---
endEvent() | void | 이벤트 종료— status=ENDED
isExpired() | boolean | 현재 시각이 endDate 초과 여부 반환
getDiscountedPrice(originalPrice) | BigDecimal | 할인 적용 가격 계산
isActive() | boolean | 진행 중 여부 (현재 시간이 start~end 사이 + status=ACTIVE)
decreaseStock(qty) | void | 재고 차감→ 0 이하 시 SOLD_OUT 처리
isSoldOut() | boolean | 품절 여부 반환

### ● Controller

«Controller» StoreController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /stores | ResponseEntity<Page<StoreResponseDto>> | 상점 목록 조회 (지역/카테고리/키워드 필터링)
GET | /stores/nearby | ResponseEntity<List<StoreMapResponseDto>> | 주변 상점 조회 (지도 마커용, 위도/경도/반경)
GET | /stores/{storeId} | ResponseEntity<StoreDetailResponseDto> | 상점 상세 정보 조회 (평점, 리뷰 수 포함)
GET | /stores/{storeId}/products | ResponseEntity<List<StoreProductResponseDto>> | 상점 상품 목록 조회 (이벤트 상품 포함)
GET | /stores/{storeId}/product-categories | ResponseEntity<List<ProductCategoryResponseDto>> | 매장 메뉴 카테고리 (활성만, 사용자 화면용)
GET | /stores/{storeId}/products/{productId} | ResponseEntity<StoreProductDetailResponseDto> | 상품 상세 조회
GET | /stores/{storeId}/products/{productId}/options | ResponseEntity<List<StoreProductResponseDto>> | 상품 상세 진입 시 옵션 트리 조회
GET | /stores/{storeId}/events | ResponseEntity<List<StoreEventProductResponseDto>> | 진행 중인 이벤트 상품 목록
GET | /stores/{storeId}/notices | ResponseEntity<List<StoreNoticeResponseDto>> | 매장 활성 공지 목록 (매장 상세 진입 시)

«Controller» OwnerStoreController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /owner/stores | ResponseEntity<StoreResponseDto> | 상점 등록 (imageUrls 포함)
PATCH | /owner/stores/{storeId} | ResponseEntity<StoreResponseDto> | 상점 정보 수정
GET | /owner/stores/{storeId} | ResponseEntity<StoreDashboardResponseDto> | 상점 대시보드 조회 (요약 정보)
POST | /owner/stores/{storeId}/images | ResponseEntity<StoreImageResponseDto> | 상점 이미지 추가
DELETE | /owner/stores/{storeId}/images/{imageId} | ResponseEntity<CommonResponseDto> | 상점 이미지 삭제
PATCH | /owner/stores/{storeId}/images/{imageId}/thumbnail | ResponseEntity<CommonResponseDto> | 상점 대표 이미지 지정
PATCH | /owner/stores/{storeId}/status | ResponseEntity<StoreResponseDto> | 상점 상태 변경
DELETE | /owner/stores/{storeId} | ResponseEntity<CommonResponseDto> | 상점 삭제
POST | /owner/stores/{storeId}/products | ResponseEntity<StoreProductResponseDto> | 상품 등록 (imageUrls 포함)
PATCH | /owner/stores/{storeId}/products/{productId} | ResponseEntity<StoreProductResponseDto> | 상품 정보 수정
POST | /owner/stores/{storeId}/products/{productId}/images | ResponseEntity<StoreProductImageResponseDto> | 상품 이미지 추가
DELETE | /owner/stores/{storeId}/products/{productId}/images/{imageId} | ResponseEntity<CommonResponseDto> | 상품 이미지 삭제
PATCH | /owner/stores/{storeId}/products/{productId}/images/{imageId}/thumbnail | ResponseEntity<CommonResponseDto> | 상품 대표 이미지 지정
GET | /owner/stores/{storeId}/reviews | ResponseEntity<Page<OwnerStoreReviewResponseDto>> | 상점 리뷰 전체 조회
GET | /owner/stores/{storeId}/reviews/{reviewId} | ResponseEntity<OwnerStoreReviewDetailResponseDto> | 상점 리뷰 상세
PATCH | /owner/stores/{storeId}/products/{productId}/sold-out | ResponseEntity<StoreProductResponseDto> | 수동 품절 처리 (stock null인 경우)
PATCH | /owner/stores/{storeId}/products/{productId}/available | ResponseEntity<StoreProductResponseDto> | 판매 재개
DELETE | /owner/stores/{storeId}/products/{productId} | ResponseEntity<CommonResponseDto> | 상품 삭제
POST | /owner/stores/{storeId}/events | ResponseEntity<StoreEventProductResponseDto> | 이벤트 상품 등록
PATCH | /owner/stores/{storeId}/events/{eventId} | ResponseEntity<StoreEventProductResponseDto> | 이벤트 상품 수정
DELETE | /owner/stores/{storeId}/events/{eventId} | ResponseEntity<CommonResponseDto> | 이벤트 상품 삭제
GET | /owner/stores/{storeId}/orders | ResponseEntity<Page<StoreOrderResponseDto>> | 주문/예약 목록 조회 (필터링: 기간, 상태)
GET | /owner/stores/{storeId}/orders/{orderId} | ResponseEntity<StoreOrderResponseDto> | 주문/예약 상세 조회

> 참고: 주문/예약 관련 API는 `order-payment.md`, `reservation.md`에도 교차 기재됨 (Store 도메인 컨트롤러 안에 포함된 구조).

«Controller» OwnerStoreNoticeController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /owner/notices | ResponseEntity<List<StoreNoticeResponseDto>> | 내 매장 공지 이력
POST | /owner/notices | ResponseEntity<StoreNoticeResponseDto> | 공지 등록
PATCH | /owner/notices/{noticeId} | ResponseEntity<StoreNoticeResponseDto> | 공지 수정
DELETE | /owner/notices/{noticeId} | ResponseEntity<CommonResponseDto> | 공지 삭제

«Controller» OwnerProductOptionController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /owner/products/{productId}/options | ResponseEntity<List<ProductOptionDto>> | 상품 옵션 목록 조회
POST | /owner/products/{productId}/options | ResponseEntity<ProductOptionDto> | 옵션 그룹 생성 (선택지 포함)
PUT | /owner/products/{productId}/options/{optionId} | ResponseEntity<ProductOptionDto> | 옵션 그룹 전체 교체
PATCH | /owner/products/{productId}/options/{optionId} | ResponseEntity<ProductOptionDto> | 옵션 그룹 부분 수정
DELETE | /owner/products/{productId}/options/{optionId} | ResponseEntity<CommonResponseDto> | 옵션 그룹 삭제
PATCH | /owner/products/{productId}/options/items/{itemId}/availability | ResponseEntity<CommonResponseDto> | 선택지 품절 토글

«Controller» OwnerProductCategoryController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /owner/stores/{storeId}/product-categories | ResponseEntity<List<ProductCategoryResponseDto>> | 본인 매장 카테고리 목록
POST | /owner/stores/{storeId}/product-categories | ResponseEntity<ProductCategoryResponseDto> | 카테고리 생성
PATCH | /owner/product-categories/{categoryId} | ResponseEntity<ProductCategoryResponseDto> | 수정 (이름 / 표시순서)
DELETE | /owner/product-categories/{categoryId} | ResponseEntity<CommonResponseDto> | 삭제 (소속 상품 검증)
DELETE | /owner/product-categories/{categoryId}/hard | ResponseEntity<CommonResponseDto> | 물리 삭제 (소속 상품 0개일 때만)
PATCH | /owner/product-categories/{categoryId}/activate | ResponseEntity<CommonResponseDto> | 활성화
PATCH | /owner/product-categories/{categoryId}/deactivate | ResponseEntity<CommonResponseDto> | 비활성화

«Controller» StoreReviewController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /stores/{storeId}/reviews | ResponseEntity<Page<StoreReviewResponseDto>> | 상점 리뷰 목록 조회
GET | /stores/{storeId}/reviews/{reviewId} | ResponseEntity<StoreReviewDetailResponseDto> | 리뷰 상세 조회
POST | /stores/{storeId}/reviews | ResponseEntity<StoreReviewResponseDto> | 리뷰 작성 (거래 완료 orderId 필요, imageUrls 포함)
PATCH | /stores/{storeId}/reviews/{reviewId} | ResponseEntity<StoreReviewResponseDto> | 리뷰 수정 (본인만)
DELETE | /stores/{storeId}/reviews/{reviewId} | ResponseEntity<CommonResponseDto> | 리뷰 삭제 (본인만)
POST | /stores/{storeId}/reviews/{reviewId}/images | ResponseEntity<StoreImageResponseDto> | 리뷰 이미지 추가
DELETE | /stores/{storeId}/reviews/{reviewId}/images/{imageId} | ResponseEntity<CommonResponseDto> | 리뷰 이미지 삭제
PATCH | /stores/{storeId}/reviews/{reviewId}/images/{imageId}/thumbnail | ResponseEntity<CommonResponseDto> | 대표 이미지 지정
POST | /stores/{storeId}/reviews/{reviewId}/reply | ResponseEntity<StoreReviewReplyResponseDto> | 리뷰 답글 작성 (상점 사장만)
PATCH | /stores/{storeId}/reviews/{reviewId}/reply | ResponseEntity<StoreReviewReplyResponseDto> | 리뷰 답글 수정 (상점 사장만)
DELETE | /stores/{storeId}/reviews/{reviewId}/reply | ResponseEntity<CommonResponseDto> | 리뷰 답글 삭제 (상점 사장만)

«Controller» AdminStoreController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /admin/stores | ResponseEntity<Page<StoreResponseDto>> | 관리자 - 전체 상점 조회
PATCH | /admin/stores/{storeId}/suspend | ResponseEntity<CommonResponseDto> | 상점 정지 (신고 처리 시)
PATCH | /admin/stores/{storeId}/activate | ResponseEntity<CommonResponseDto> | 상점 정지 해제

### ● Service

«Service» StoreService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getStoreList(StoreSearchDto) | Page<StoreResponseDto> | readOnly | QueryDSL | 상점 목록 조회 (지역/카테고리/키워드/평점/정렬)
getNearbyStores(latitude, longitude, radius) | List<StoreMapResponseDto> | readOnly | - | 주변 상점 조회 (지도용)
getStoreDetail(storeId) | StoreDetailResponseDto | readOnly | CheckStoreAccess | 상점 상세 정보 조회 (평점/리뷰 수 포함)
getProductList(storeId) | List<ProductResponseDto> | readOnly | - | 상점의 상품 목록 (이벤트 포함)
getActiveProductCategoriesByStore(storeId) | List<ProductCategoryResponseDto> | readOnly | - | 사용자 화면용 활성 카테고리 (displayOrder 정렬)
getProductDetail(productId) | ProductDetailResponseDto | readOnly | CheckStoreAccess | 상품 상세 조회 (조회수 증가)
getActiveEvents(storeId) | List<EventProductResponseDto> | readOnly | - | 진행 중인 이벤트 상품 목록

«Service» OwnerStoreService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
registerStore(accountId, StoreCreateRequestDto) | StoreResponseDto | REQUIRED | Ownership | 상점 등록 (1인 1상점 검증 후 저장)
updateStore(accountId, storeId, StoreUpdateRequestDto) | StoreResponseDto | REQUIRED | Ownership | 상점 정보 수정
updateStoreStatus(accountId, storeId, status) | StoreResponseDto | REQUIRED | Ownership | 상점 상태 변경
deleteStore(accountId, storeId) | void | REQUIRED | Ownership | 상점 delete
getProductCategoriesByStore(storeId) | List<ProductCategoryResponseDto> | readOnly | Ownership | 본인 매장 카테고리 목록
createProductCategory(storeId, ProductCategoryCreateRequestDto) | ProductCategoryResponseDto | REQUIRED | Ownership | 카테고리 생성 (이름 중복 검증)
updateProductCategory(categoryId, ProductCategoryUpdateRequestDto) | ProductCategoryResponseDto | REQUIRED | Ownership | 수정
deactivateProductCategory(categoryId) | void | REQUIRED | Ownership | Soft Delete(기본 삭제 = isActive=false)
hardDeleteProductCategory(categoryId) | void | REQUIRED | Ownership | 물리 삭제 (소속 Product 0개 검증)
activateProductCategory(categoryId) | void | REQUIRED | Ownership | 활성화
deactivateProductCategory(categoryId) | void | REQUIRED | Ownership | 비활성화
addStoreImage(accountId, storeId, imageUrl) | StoreImageResponseDto | REQUIRED | ImageService | 상점 이미지 추가
deleteStoreImage(accountId, storeId, imageId) | void | REQUIRED | ImageService | 상점 이미지 삭제
setStoreImageThumbnail(accountId, storeId, imageId) | void | REQUIRED | ImageService | 상점 대표 이미지 지정

«Service» StoreNoticeService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
createNotice(Long storeId, CreateNoticeDto dto) | StoreNoticeDto | REQUIRED | Ownership | 공지 등록 (사장)
updateNotice(Long storeId, Long noticeId, UpdateNoticeDto dto) | StoreNoticeDto | REQUIRED | Ownership | 공지 수정
deleteNotice(Long storeId, Long noticeId) | void | REQUIRED | Ownership | 공지 삭제
getNoticeHistory(Long storeId, Pageable pageable) | Page<StoreNoticeDto> | readOnly | Ownership | 공지 이력 (사장 페이지)
getActiveNotices(Long storeId) | List<StoreNoticeDto> | readOnly | - | 활성 공지 (사용자 매장 상세)
getLatestActiveNotice(Long storeId) | Optional<StoreNoticeDto> | readOnly | - | 최신 공지

«Service» ProductService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
registerProduct(accountId, storeId, ProductCreateRequestDto) | ProductResponseDto | REQUIRED | Ownership | 상품 등록
updateProduct(accountId, storeId, productId, ProductUpdateRequestDto) | ProductResponseDto | REQUIRED | Ownership + OptimisticLock | 상품 수정
deleteProduct(accountId, storeId, productId) | void | REQUIRED | Ownership | 상품 삭제
markProductAsSoldOut(accountId, storeId, productId) | ProductResponseDto | REQUIRED | - | 수동 품절 처리
markProductAsAvailable(accountId, storeId, productId) | ProductResponseDto | REQUIRED | - | 판매 재개
addProductImage(accountId, storeId, productId, imageUrl) | ProductImageResponseDto | REQUIRED | ImageService | 상품 이미지 추가
deleteProductImage(accountId, storeId, productId, imageId) | void | REQUIRED | ImageService | 상품 이미지 삭제
setProductImageThumbnail(accountId, storeId, productId, imageId) | void | REQUIRED | ImageService | 상품 대표 이미지 지정

> 참고: 원문에는 ProductService 명세가 동일 내용으로 중복 기재되어 있음 (5.2절 내 두 번 등장). 원문 보존을 위해 그대로 기재.

«Service» ProductOptionService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getProductOptions(Long productId) | List<ProductOptionDto> | READ ONLY | - | 상품 옵션 트리 조회 (그룹 + 선택지)
createOption(Long storeId, Long productId, CreateOptionDto dto) | ProductOptionDto | REQUIRED | OWNER | 옵션 그룹 + 선택지 일괄 생성
updateOption(Long storeId, Long productOptionId, UpdateOptionDto dto) | ProductOptionDto | REQUIRED | OWNER | 옵션 그룹 + 선택지 일괄 수정
deleteOption(Long storeId, Long productOptionId) | void | REQUIRED | OWNER | 옵션 그룹 삭제 (CASCADE)
toggleItemAvailability(Long storeId, Long itemId) | void | REQUIRED | OWNER | 선택지 품절 토글
validateAndCalculatePrice(Long productId, List<Long> selectedItemIds) | OptionValidationResult | READ ONLY | OptionValidation | 주문/장바구니 시 검증 + 가격 계산
buildOptionsSnapshot(List<Long> selectedItemIds) | String | READ ONLY | Snapshot | OrderItem 스냅샷 JSON 생성

«Service» EventProductService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
registerEvent(accountId, storeId, EventCreateRequestDto) | EventProductResponseDto | REQUIRED | PESSIMISTIC_WRITE + UNIQUE | 동일 상품 ACTIVE 이벤트 1개 보장
updateEvent(accountId, storeId, eventId, EventUpdateRequestDto) | EventProductResponseDto | REQUIRED | - | 종료 이벤트 수정 불가
deleteEvent(accountId, storeId, eventId) | void | REQUIRED | - | 이벤트 삭제
autoEndExpiredEvents() | void | REQUIRED | Scheduler | 만료 이벤트 자동 종료

«Service» StoreDashboardService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getDashboardSummary(accountId) | StoreDashboardResponseDto | readOnly | Cacheable | 매출/주문/리뷰 통합 조회
getSalesStatistics(accountId, StatisticsPeriod) | SalesStatisticsResponseDto | readOnly | - | 기간별 매출 통계
getProductStatistics(accountId) | ProductStatisticsResponseDto | readOnly | - | 인기상품/재고 분석
getStoreOrders(accountId, OrderSearchDto, Pageable) | Page<StoreOrderResponseDto> | readOnly | - | 주문 목록 조회
getStoreOrderDetail(accountId, orderId) | StoreOrderDetailResponseDto | readOnly | - | 주문 상세 조회
getReviewStatistics(accountId) | ReviewStatisticsResponseDto | readOnly | - | 평점/리뷰 분석

«Service» StoreReviewService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getReviewList(storeId, Pageable) | Page<StoreReviewResponseDto> | readOnly | - | 삭제 제외 리뷰 조회
getMyStoreReviews(accountId, Pageable) | Page<StoreReviewResponseDto> | readOnly | - | 내가 작성한 상점 리뷰
getReviewDetail(reviewId) | StoreReviewDetailResponseDto | readOnly | - | 리뷰 상세 조회
registerReview(accountId, storeId, StoreReviewCreateRequestDto) | StoreReviewResponseDto | REQUIRED | UNIQUE(orderId) + Event | 리뷰 등록
updateReview(accountId, reviewId, StoreReviewUpdateRequestDto) | StoreReviewResponseDto | REQUIRED | Ownership + Event | 리뷰 수정
deleteReview(accountId, reviewId) | void | REQUIRED | Ownership + Event | 리뷰 delete
addReviewImage(accountId, reviewId, imageUrl) | StoreReviewImageResponseDto | REQUIRED | ImageService | 리뷰 이미지 추가
deleteReviewImage(accountId, reviewId, imageId) | void | REQUIRED | ImageService | 리뷰 이미지 삭제
setReviewImageThumbnail(accountId, reviewId, imageId) | void | REQUIRED | ImageService | 대표 이미지 설정

«Service» StoreReplyService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
registerReply(accountId, reviewId,StoreReviewReplyCreateRequestDto) | StoreReviewReplyResponseDto | REQUIRED | OwnerOnly | 리뷰 답글 등록
updateReply(accountId, reviewId, StoreReviewReplyUpdateRequestDto) | StoreReviewDetailResponseDto | REQUIRED | OwnerOnly | 답글 수정
deleteReply(accountId, reviewId) | CommonResponseDto | REQUIRED | OwnerOnly | 답글 삭제

«Service» AdminStoreService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getAllStores(Pageable, filter) | Page<StoreResponseDto> | readOnly | Admin | 전체 상점 조회
suspendStore(storeId) | void | REQUIRED | Admin | 상점 정지
forceDeleteReview(reviewId) | void | REQUIRED | Admin | 리뷰 영구 삭제

«Helper» StoreAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyStoreOwnership(accountId, storeId) | Store | 소유권 검증 → 실패 시 ForbiddenException
verifyProductOwnership(accountId, storeId, productId) | Product | 상품 소유권 검증
verifyActiveStore(storeId) | Store | 상점 접근 가능 여부 (CLOSED 차단)
verifyProductCategoryOwnership(accountId, productcategoryId) | ProductCategory | 본인 매장 카테고리 검증

### ● Repository

«Repository» StoreRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long storeId) | Optional<Store> | 상점 ID로 조회
findByAccountId(Long accountId) | Optional<Store> | 사장 ID로 상점 조회
existsByAccountId(Long accountId) | boolean | 사장의 상점 중복 등록 방지
incrementFavoriteCount(Long storeId) | int | 찜 카운트 +1 (DB 원자 UPDATE, 영향받은 행 수 반환)
decrementFavoriteCount(Long storeId) | int | 찜 카운트 -1 (favoriteCount > 0 가드, 영향받은 행 수 반환)
findNearbyStores(Double latitude, Double longitude, Double radius) | List<Store> | 위치 기반 주변 상점 조회 (지도용)
findAllByStatus(StoreStatus status, Pageable pageable) | Page<Store> | 상태별 상점 목록 (관리자용)

«Repository» StoreRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchStores(StoreSearchDto condition, Pageable pageable) | Page<Store> | 지역+카테고리+키워드+정렬 복합 검색
findNearbyStoresWithFilter(NearbySearchCondition condition) | List<Store> | 위치 + 필터 복합 검색

«Repository» StoreNoticeRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long noticeId) | Optional<StoreNotice> | 공지 조회
findByIdAndStoreId(Long noticeId, Long storeId) | Optional<StoreNotice> | 소유권 검증
findAllByStoreId(Long storeId) | List<StoreNotice> | 매장 공지 이력 (사장용)
findAllByStoreIdAndIsActiveTrue(Long storeId) | Page<StoreNotice> | 매장 활성 공지 (사용자 화면 노출용)
findFirstByStoreIdAndIsActiveTrueOrderByCreatedAtDesc(Long storeId) | Optional<StoreNotice> | 최신 활성 공지 1개 (목록 화면 배지용)
deleteAllByStoreId(Long storeId) | void | 매장 삭제 시 CASCADE

«Repository» StoreImageRepository
메서드명 | 반환타입 | 설명
---|---|---
findAllByStoreIdOrderByDisplayOrder(Long storeId) | List<StoreImage> | 상점 이미지 목록 (순서대로)
findByStoreIdAndIsThumbnailTrue(Long storeId) | Optional<StoreImage> | 대표 이미지 조회
findByIdAndStoreId(Long storeImageId, Long storeId) | Optional<StoreImage> | 소유권 확인용 조회
countByStoreId(Long storeId) | Long | 상점 이미지 개수 (제한 검증용)
findFirstByStoreIdOrderByDisplayOrderAsc(Long storeId) | Optional<StoreImage> | 대표 자동 승격용(대표 삭제 시)
deleteAllByStoreId(Long storeId) | void | 상점 이미지 전체 삭제 (상점 삭제 시)

«Repository» ProductCategoryRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long categoryId) | Optional<ProductCategory> | 카테고리 조회
findAllByStoreId(Long storeId) | List<ProductCategory> | 매장별 전체 (사장용, displayOrder 정렬)
findAllByStoreIdAndIsActiveTrue(Long storeId) | List<ProductCategory> | 매장별 활성 카테고리 (사용자용)
existsByStoreIdAndName(Long storeId, String name) | boolean | 중복 이름 검증
countByStoreId(Long storeId) | Long | 매장별 카테고리 수
deleteAllByStoreId(Long storeId) | void | 매장 삭제 시 CASCADE

«Repository» ProductRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long productId) | Optional<Product> | 상품 ID로 조회
findByIdAndStoreId(Long productId, Long storeId) | Optional<Product> | 상점-상품 소유권 확인용
findByIdForUpdate(productId) | Optional<Product> | 재고 차감용 락
findAllByStoreId(Long storeId, Pageable pageable) | Page<Product> | 상점의 상품 목록
findAllByStoreIdAndStatus(Long storeId, ProductStatus status, Pageable pageable) | Page<Product> | 상점 상품 목록 (상태별, 페이징)
findAllByStoreIdAndProductCategoryId(Long storeId, Long productCategoryId) | List<Product> | 상점 내 카테고리별 상품
countByStoreIdAndStatusNot(Long storeId, ProductStatus status) | Long | 삭제되지 않은 상품 수 (대시보드용)
countByProductCategoryId(Long productCategoryId) | Long | ProductCategory Hard Delete 검증용(소속 Product 0개 확인)

«Repository» ProductImageRepository
메서드명 | 반환타입 | 설명
---|---|---
findAllByProductIdOrderByDisplayOrder(Long productId) | List<ProductImage> | 상품 이미지 목록 (순서대로)
findByProductIdAndIsThumbnailTrue(Long productId) | Optional<ProductImage> | 대표 이미지 조회
findByIdAndProductId(Long productImageId, Long productId) | Optional<ProductImage> | 소유권 확인용 조회
countByProductId(Long productId) | Long | 상품 이미지 개수 (제한 검증용)
findFirstByProductIdOrderByDisplayOrderAsc(Long productId) | Optional<ProductImage> | 대표 자동 승격용
deleteAllByProductId(Long productId) | void | 상품 이미지 전체 삭제 (상품 삭제 시)

«Repository» ProductOptionRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long productOptionId) | Optional<ProductOption> | 옵션 그룹 조회
findAllByProductIdOrderByDisplayOrder(Long productId) | List<ProductOption> | 상품의 옵션 그룹 목록
existsByProductIdAndGroupName(Long productId, String groupName) | boolean | 중복 그룹명 검증
countByProductId(Long productId) | Long | 옵션 그룹 수
deleteAllByProductId(Long productId) | void | 상품 삭제 시 CASCADE

«Repository» ProductOptionItemRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long productOptionItemId) | Optional<ProductOptionItem> | 선택지 조회
findAllByProductOptionIdOrderByDisplayOrder(Long productOptionId) | List<ProductOptionItem> | 그룹의 선택지 목록
findAllByIdIn(List<Long> itemIds) | List<ProductOptionItem> | 여러 선택지 일괄 조회 (주문 시 검증용)
findAllByProductOptionId_ProductId(Long productId) | List<ProductOptionItem> | 상품의 모든 선택지 (한 번에 로딩)
existsByProductOptionIdAndItemName(Long productOptionId, String itemName) | boolean | 중복 이름 검증
countByProductOptionId(Long productOptionId) | Long | 선택지 개수
deleteAllByProductOptionId(Long productOptionId) | void | 그룹 삭제 시 CASCADE

«Repository» EventProductRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long eventProductId) | Optional<EventProduct> | 이벤트 상품 ID로 조회
findActiveByProductId(Long productId) | Optional<EventProduct> | 특정 상품의 진행 중(ACTIVE) 이벤트 조회)
findActiveByProductIdForUpdate(Long productId) | Optional<EventProduct> | @Lock(PESSIMISTIC_WRITE) / 이벤트 등록 시 동시성 제어
existsByActiveProductId(Long productId) | boolean | ACTIVE 이벤트 존재 여부 확인 (동시성 제어 + 중복 생성 방지)
findAllByStatusAndEndDateBefore(EventStatus status, LocalDateTime now) | List<EventProduct> | 만료 이벤트 자동 종료용 (스케줄러)
bulkUpdateExpiredEvents() | int | 만료 이벤트 일괄 종료 처리 (@Modifying 스케줄러)

«Repository» StoreReviewRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long storeReviewId) | Optional<StoreReview> | 리뷰 ID로 조회
findAllByStoreId(Long storeId, Pageable pageable) | Page<StoreReview> | 상점 리뷰 목록 (페이징)
findAllByAccountId(Long accountId, Pageable pageable) | Page<StoreReview> | 내가 작성한 리뷰 목록 (마이페이지용)
existsByOrderId(Long orderId) | boolean | 주문에 대한 리뷰 존재 여부 (중복 작성 방지)
findByOrderId(Long orderId) | Optional<StoreReview> | 주문 ID로 리뷰 조회
findAverageRatingByStoreId(Long storeId) | Double | 상점 평균 평점 계산 (갱신용)
countByStoreId(Long storeId) | Long | 상점 리뷰 수 (캐싱용)

«Repository» StoreReviewImageRepository
메서드명 | 반환타입 | 설명
---|---|---
findAllByStoreReviewIdOrderByDisplayOrder(Long storeReviewId) | List<StoreReviewImage> | 리뷰 이미지 목록
findByStoreReviewIdAndIsThumbnailTrue(Long storeReviewId) | Optional<StoreReviewImage> | 대표 이미지 조회
findByIdAndStoreReviewId(Long imageId, Long reviewId) | Optional<StoreReviewImage> | 소유권 확인용
countByStoreReviewId(Long storeReviewId) | Long | 리뷰 이미지 개수
findFirstByStoreReviewIdOrderByDisplayOrderAsc(Long storeReviewId) | Optional<ProductImage> | 대표 자동 승격용
deleteAllByStoreReviewId(Long storeReviewId) | void | 리뷰 이미지 전체 삭제

«Repository» StoreReviewReplyRepository
메서드명 | 반환타입 | 설명
---|---|---
findByStoreReviewId(Long storeReviewId) | Optional<StoreReviewReply> | 리뷰의 답글 조회
existsByStoreReviewId(Long storeReviewId) | boolean | 답글 존재 여부 (중복 작성 방지)
deleteByStoreReviewId(Long storeReviewId) | void | 리뷰 삭제 시 답글도 함께 삭제

## 5.8 ENUM (Store 관련)

«Enum» StoreStatus
ENUM 값 | 설명
---|---
OPEN | 영업중
CLOSED | 영업 종료
TEMP_CLOSED | 일시 휴무

«Enum» NoticeType
ENUM 값 | 설명
---|---
GENERAL | 일반 공지
HOLIDAY | 휴무 공지
EVENT | 이벤트 공지

«Enum» ProductStatus
ENUM 값 | 설명
---|---
ACTIVE | 판매중
SOLD_OUT | 품절

«Enum» EventStatus
ENUM 값 | 설명
---|---
ACTIVE | 진행중
ENDED | 이벤트 종료

«Enum» OptionSelectionType
ENUM 값 | 설명
---|---
SINGLE | 단일 선택
MULTIPLE | 다중 선택

«Enum» AuditActionType (Store 관련, 중복 참조 — account.md 참조)
ENUM 값 | 설명
---|---
STORE_SUSPEND | 상점 정지
STORE_ACTIVATE | 상점 활성화
REVIEW_FORCE_DELETE | 리뷰 강제 삭제

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-7: 사장 매장 등록
- DSEQ-8: 매장 상세 조회 (사용자)
- DSEQ-9: 상품 등록 (옵션 포함)
- DSEQ-10: 이벤트 상품 등록
- DSEQ-11: 매장 리뷰 작성 + 답글

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.2 도메인 ERD (참조)

● Store / Product 도메인
[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Store (상점)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
store_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상점 고유 식별자
account_id | bigint | FK(account), UNIQUE, NOT NULL | 상점 소유자(사장1:1 상점 제약)
region_id | bigint | FK(region), NOT NULL | 상점 소속 지역
category_id | bigint | FK(category), NOT NULL | 상점 카테고리(type=STORE)
latitude | double | NOT NULL | 상점 위도
longitude | double | NOT NULL | 상점 경도
name | varchar(100) | NOT NULL | 상점명
address | varchar(255) | NOT NULL | 주소
phone | varchar(20) | NOT NULL | 상점 연락처
description | text | nullable | 상점 소개
business_hours | varchar(255) | NOT NULL | 영업시간
rating | double | NOT NULL, DEFAULT 0.0 | 평균 평점(캐싱)
favorite_count | int | NOT NULL, DEFAULT 0 | 상점 좋아요 수
review_count | int | NOT NULL, DEFAULT 0 | 리뷰 수(캐싱)
status | varchar(20) | NOT NULL | StoreStatus ENUM
version | bigint | NOT NULL | 낙관적 락(@Version) - 평점 갱신 동시성
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### StoreNotice (상점 공지)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
notice_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상점 공지 고유 식별자
store_id | bigint | FK(store), NOT NULL | 연관 상점
notice_type | varchar(20) | NOT NULL | NoticeType ENUM
content | text | NOT NULL | 공지 상세 내용
is_active | boolean | NOT NULL, DEFAULT true | 공지 노출 여부
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### StoreImage (상점 이미지)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
store_image_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 이미지 고유 식별자(ImageBase 상속)
store_id | bigint | FK(store), NOT NULL | 연관 상점
image_url | varchar(500) | NOT NULL | S3 이미지URL (ImageBase 상속)
display_order | int | NOT NULL, DEFAULT 0 | 표시 순서(ImageBase 상속)
is_thumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부(ImageBase 상속)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### ProductCategory (상점 카테고리)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
product_category_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상품 카테고리 고유 식별자
store_id | bigint | FK(store), NOT NULL | 소속 상점
name | varchar(50) | NOT NULL | 상품 카테고리명
display_order | int | NOT NULL, DEFAULT 0 | 표시 순서
is_active | boolean | NOT NULL, DEFAULT true | 노출 여부(Soft Delete)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### Product (상품)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
product_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상품 고유 식별자
store_id | bigint | FK(store), NOT NULL | 소속 상점
product_category_id | bigint | FK(product_category), NOT NULL | 상품 카테고리
name | varchar(100) | NOT NULL | 상품명
description | text | NOT NULL | 상품 설명
price | decimal(10,2) | NOT NULL, ≥0 | 가격
stock | int | nullable, ≥0 | 재고 수량(null=무제한)
view_count | int | NOT NULL, DEFAULT 0 | 조회수(Redis 집계→ 5분 주기)
status | varchar(20) | NOT NULL, DEFAULT 'ACTIVE' | ProductStatus ENUM
version | bigint | NOT NULL | 낙관적 락(@Version) - 재고 차감
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### ProductImage (상품 이미지)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
product_image_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 이미지 고유 식별자(ImageBase 상속)
product_id | bigint | FK(product), NOT NULL | 연관 상품
image_url | varchar(500) | NOT NULL | S3 이미지URL
display_order | int | NOT NULL, DEFAULT 0 | 표시 순서
is_thumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### ProductOption (상품 옵션)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
product_option_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상품 옵션 고유 식별자
product_id | bigint | FK(product), NOT NULL | 연관 상품
group_name | varchar(50) | NOT NULL | 옵션 그룹 이름(예: 용량, 매운맛)
selection_type | varchar(20) | NOT NULL, DEFAULT 'SINGLE' | OptionSelectionType ENUM
is_required | boolean | NOT NULL, DEFAULT false | 옵션 선택 필수 여부
display_order | int | NOT NULL, DEFAULT 0 | 옵션 그룹 노출 순서
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### ProductOptionItem (상품 옵션 선택지)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
product_option_item_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상품 옵션 선택지 고유 식별자
product_option_id | bigint | FK(product_option), NOT NULL | 연관 상품 옵션 그룹
item_name | varchar(50) | NOT NULL | 옵션 선택 값(예: 300g, 매운맛)
additional_price | decimal(10,2) | NOT NULL, DEFAULT 0 | 선택 시 추가 금액
is_default | boolean | NOT NULL, DEFAULT false | 기본 선택 여부
display_order | int | NOT NULL, DEFAULT 0 | 옵션 항목 노출 순서
is_available | boolean | NOT NULL, DEFAULT true | 옵션 선택 가능 여부(품절/비활성)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### EventProduct (이벤트 상품)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
event_product_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 이벤트 상품 고유 식별자
product_id | bigint | FK(product), NOT NULL | 연관 상품
active_product_id | bigint | GENERATED COLUMN, UNIQUE | 'ACTIVE' 일 때 product_id, 그 외 NULL
discount_rate | int | NOT NULL, 0~99 | 할인율(%)
stock | int | nullable, ≥0 | 이벤트 수량 (null=무제한)
start_date | datetime | NOT NULL | 이벤트 시작일
end_date | datetime | NOT NULL | 이벤트 종료일
status | varchar(20) | NOT NULL | EventStatus ENUM
version | bigint | NOT NULL | 낙관적 락(@Version) - 이벤트 재고 차감
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### StoreReview (상점 리뷰)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
store_review_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상점 리뷰 고유 식별자
store_id | bigint | FK(store), NOT NULL | 연관 상점
account_id | bigint | FK(account), NOT NULL | 작성자
order_id | bigint | FK(order), UNIQUE, NOT NULL | 1주문 1리뷰 보장
rating | int | NOT NULL, 1~5 | 별점
content | text | NOT NULL | 리뷰 내용
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### StoreReviewImage (상점 리뷰 이미지)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
store_review_image_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 이미지 고유 식별자(ImageBase 상속)
store_review_id | bigint | FK(store_review), NOT NULL | 연관 상점 리뷰
image_url | varchar(500) | NOT NULL | S3 이미지URL
display_order | int | NOT NULL, DEFAULT 0 | 표시 순서
is_thumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### StoreReviewReply (상점 리뷰 답글)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
store_reply_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 상점 리뷰 답글 고유 식별자
store_review_id | bigint | FK(store_review), UNIQUE, NOT NULL | 연관 리뷰(1:1)
account_id | bigint | FK(account), NOT NULL | 답글 작성자(사장 회원)
content | text | NOT NULL | 답글 내용
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

## 중복 참조 (common.md에도 기재됨)

- 동시성 제어: 낙관적 락 적용 대상 Entity(Store, Product, EventProduct)는 `common.md` 6.1.4 참조
- 평점 갱신 재시도 정책(`@Retryable` ObjectOptimisticLockingFailureException)은 `common.md` 6.1.4 참조
- favoriteCount DB 원자 UPDATE 패턴은 `common.md` 6.1.5 참조
- 상점 상세/대시보드 캐싱(`store:detail:{storeId}`, `dashboard:store:{storeId}`)은 `common.md` 6.4.1 참조
- 상품 조회수 Redis Write-Through 동기화(`view:product:{productId}`)는 `common.md` 6.4.3 참조
- 분산 락 키 `order:product:{productId}`, `order:products:{1-3-5}`는 `order-payment.md`, `common.md` 6.1.2 참조
