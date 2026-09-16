# Image 도메인 SDD

> 코드 경로: image, s3, imageBase, presignedUrl
> 비고: 공통 이미지 처리

## 5.7 공통 도메인 내 Image 명세 (원문 위치)

> 참고: SDD 원문에는 Image 도메인에 대한 4장 DCOM 식별 항목(설명 단락)이 별도로 존재하지 않는다. ImageBase는 도메인별 이미지 엔티티(StoreImage, ProductImage, StoreReviewImage, UsedProductImage, CommunityImage, InquiryImage 등)가 공통으로 상속하는 MappedSuperclass이며, 5.7절(공통 도메인)에 명세되어 있다.

### ● Entity

«MappedSuperclass» ImageBase
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
imageUrl | String | NOT NULL | S3 이미지 URL
displayOrder | int | NOT NULL, DEFAULT 0 | 표시 순서
isThumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«MappedSuperclass» ImageBase (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
markAsThumbnail() | void | 대표 이미지로 설정
unmarkThumbnail() | void | 대표 이미지 해제
updateDisplayOrder(int) | void | 표시 순서 변경

> 참고: CLAUDE.md 절대 규칙상 "Image 관련 모든 엔티티는 ImageBase 상속 필수"이다. ImageBase를 상속하는 구체 엔티티 목록: `StoreImage`(store.md), `ProductImage`(store.md), `StoreReviewImage`(store.md), `UsedProductImage`(used-product.md), `CommunityImage`(community.md), `InquiryImage`(inquiry.md).

### ● Service

«Service» ImageService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
generatePresignedUrl(PresignedUrlRequestDto) | PresignedUrlResponseDto | - | - | 기본 업로드 방식 — S3 Presigned URL 발급 (도메인별 디렉토리 분기)
uploadImage(MultipartFile, ImageType) | ImageUploadResponseDto | - | S3Upload | 서버 경유 업로드 (예외 케이스 — 관리자 업로드 등)
deleteImage(String imageUrl) | void | - | S3Delete | S3에서 이미지 물리 삭제
validateImageFile(MultipartFile) | void | - | - | 확장자(jpg/png/webp) / 크기(10MB) / MIME 검증
validateImageUrl(String imageUrl) | boolean | - | - | Presigned URL로 업로드된 객체 존재 검증 (DB 저장 전)

### ● Repository

«Repository» ImageRepository (도메인별 Image Repository에 공통 쿼리 패턴)
메서드명 | 반환타입 | 설명
---|---|---
findById(Long imageId) | Optional<XxxImage> | 이미지 조회
findAllByXxxId(Long parentId) | List<XxxImage> | 부모 엔티티 이미지 목록 (displayOrder 정렬)
findThumbnailByXxxId(Long parentId) | Optional<XxxImage> | 대표 이미지 조회 (목록 화면용)
countByXxxId(Long parentId) | Long | 이미지 개수 (업로드 제한 검증)
findFirstByXxxIdOrderByDisplayOrderAsc(Long parentId) | Optional<XxxImage> | 대표 자동 승격용 (대표 삭제 시)
deleteAllByXxxId(Long parentId) | void | 부모 삭제 시 CASCADE

> 참고: `Xxx`는 도메인별 구체 이미지 Repository(StoreImageRepository, ProductImageRepository, StoreReviewImageRepository, UsedProductImageRepository, CommunityImageRepository, InquiryImageRepository 등)로 대체된다. 각 구체 Repository의 상세 메서드 목록은 해당 도메인 파일(store.md, used-product.md, community.md, inquiry.md)에 기재되어 있다.

## 5.8 ENUM (Image 관련)

«Enum» ImageType
ENUM 값 | 설명
---|---
STORE | 상점
PRODUCT | 상품
STORE_REVIEW | 상점 리뷰
USED_PRODUCT | 중고상품
COMMUNITY | 커뮤니티
INQUIRY | 문의

## 6.9.4 S3 Presigned URL 패턴 (원문 6장 위치, 도메인 연관성으로 중복 기재)

이미지 업로드 시 서버를 거치지 않고 클라이언트가 S3에 직접 업로드하도록 설계한다.

처리 흐름
1. Client → Server: Presigned URL 요청
2. Server → URL 생성 (TTL 5분)
3. Client → S3 직접 업로드
4. Client → Server: imageUrl 전달
5. Server → S3 객체 존재 검증 후 DB 저장

설계 의도
- 서버 부하 감소
- 업로드 성능 향상
- 대용량 파일 처리 효율화

## 6.9.1 외부 시스템 격리 전략 - AWS S3 항목 (원문 위치, 중복 기재)

외부 시스템 | 용도 | 격리 전략
---|---|---
AWS S3 | 이미지 저장 | Presigned URL 방식

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-24: 이미지 업로드 (Presigned URL)

[다이어그램 원본은 기존 SDD 문서 참조]

## 중복 참조 (common.md에도 기재됨)

- S3 Presigned URL 패턴, 외부 시스템 격리 전략(AWS S3)은 `common.md` 6.9.1, 6.9.4 참조
- 물리 배포 구조상 AWS S3(객체 스토리지, 이미지 저장, 5GB 무료)는 `common.md` 12.2 배포 노드 명세 참조
- 도메인별 이미지 업로드 제한(개수 검증), 대표 이미지 자동 승격 로직은 각 도메인 파일(store.md, used-product.md, community.md, inquiry.md)의 Repository 절 참조
- CLAUDE.md 절대 규칙: "Image 관련 모든 엔티티는 ImageBase 상속 필수"
