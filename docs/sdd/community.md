# Community 도메인 SDD

> 코드 경로: community, communityPost, communityComment, communityImage, postLike, commentLike
> 비고: 게시글, 댓글, 대댓글, 좋아요

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-6. 커뮤니티

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

설명
본 기능은 사용자 간 정보 공유 및 소통을 위한 커뮤니티 게시판 기능으로, 게시글 작성, 댓글 및 대댓글 작성, 좋아요 기능을 포함한다.

사용자는 게시글을 작성할 수 있으며, 게시글 정보는 CommunityPost 엔티티를 통해 관리된다. 각 게시글은 작성자(Account), 카테고리(PostCategory), 그리고 지역 정보(Region)와 연관되어 지역 기반 커뮤니티 기능을 제공하도록 설계하였다. 게시글에는 제목, 내용, 조회수(viewCount), 좋아요 수(likeCount) 등의 정보가 포함된다.

게시글은 카테고리(PostCategory)를 기준으로 분류되며, 이를 통해 사용자들이 관심 있는 주제의 게시글을 쉽게 탐색할 수 있도록 하였다.

사용자는 게시글에 댓글을 작성할 수 있으며, 댓글 정보는 CommunityComment 엔티티를 통해 관리된다. 각 댓글은 특정 게시글과 작성자(Account)와 연관되며, 삭제 여부를 나타내는 isDeleted 속성을 통해 논리적 삭제가 가능하도록 설계하였다.

또한 댓글에 대한 대댓글 기능을 제공하며, 이는 CommunityCommentReply 엔티티를 통해 관리된다. 대댓글 역시 작성자(Account)와 연관되며, 댓글과 동일하게 논리적 삭제를 지원하여 데이터 무결성을 유지하도록 하였다.

사용자는 게시글에 대해 좋아요를 누를 수 있으며, 해당 정보는 PostLike 엔티티를 통해 관리된다. 이를 통해 사용자 간 게시글에 대한 관심도를 표현할 수 있도록 하였으며, 하나의 사용자는 동일 게시글에 대해 중복으로 좋아요를 누를 수 없도록 설계하였다.

이와 같은 구조를 통해 사용자 간 자유로운 소통과 정보 공유를 지원하며, 지역 기반 커뮤니티 활성화를 도모할 수 있도록 설계하였다.

> 참고: 본문 설명에는 "카테고리(PostCategory)"라는 명칭이 사용되었으나, 실제 5.5절/9.3절 명세에서는 공통 `Category` 엔티티(type=COMMUNITY)를 참조하는 구조로 되어 있다. 원문 그대로 보존함. 또한 본문에는 "isDeleted 속성을 통한 댓글/대댓글 논리적 삭제"가 언급되었으나, 5.5절 CommunityComment/CommunityCommentReply Entity 명세에는 isDeleted 컬럼이 별도로 명시되어 있지 않다 (Hard Delete + CASCADE로 6.6.2/6.6.6에 기술됨). 원문 그대로 보존하며 불일치 가능성을 기록한다.

## 5.5 Community 도메인

### ● Entity

«Entity» CommunityPost
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
communitypostId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 작성자
categoryId | Long | FK, NOT NULL | 카테고리 (Category, type=COMMUNITY)
regionId | Long | FK, NOT NULL | 게시 활동 지역
title | String | NOT NULL | 제목
content | String | NOT NULL | 본문
viewCount | int | NOT NULL, DEFAULT 0 | 조회수 (Redis 캐싱)
likeCount | int | NOT NULL, DEFAULT 0 | 좋아요 수 (Redis 캐싱)
commentCount | int | NOT NULL, DEFAULT 0 | 댓글 수 (Redis 캐싱)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» CommunityImage
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
communityimageId | Long | PK, NOT NULL | 이미지 고유 식별자(ImageBase 상속)
communitypostId | Long | FK, NOT NULL | 연관 게시글
imageUrl | String | NOT NULL | 이미지 URL(ImageBase 상속)
displayOrder | int | NOT NULL, DEFAULT 0 | 표시 순서(ImageBase 상속)
isThumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부(ImageBase 상속)
createdAt | LocalDateTime | NOT NULL | ImageBase 상속
modifiedAt | LocalDateTime | NOT NULL | ImageBase 상속

«Entity» CommunityComment
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
communitycommentId | Long | PK, NOT NULL | 고유 식별자
communitypostId | Long | FK, NOT NULL | 연관 게시글
accountId | Long | FK, NOT NULL | 작성자
content | String | NOT NULL | 댓글 내용
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» CommunityCommentReply
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
communitcommentyreplyId | Long | PK, NOT NULL | 고유 식별자
communitycommentId | Long | FK, NOT NULL | 연관 댓글
accountId | Long | FK, NOT NULL | 작성자
content | String | NOT NULL | 대댓글 내용
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» PostLike
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
postlikeId | Long | PK, NOT NULL | 고유 식별자
accountId | Long | FK, NOT NULL | 좋아요 누른 사용자
communitypostId | Long | FK, NOT NULL | 좋아요 대상 게시글

> 참고: 원문 5.5절에는 CommunityPost/CommunityImage/CommunityComment/CommunityCommentReply/PostLike Entity의 "도메인 메서드" 섹션이 별도로 존재하지 않는다 (4장 DCOM-6 설명에 일부 메서드 의도가 서술되어 있을 뿐). 원문 그대로 보존함.

### ● Controller

«Controller» CommunityController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /community/posts | ResponseEntity<CommunityPostResponseDto> | 게시글 작성
PATCH | /community/posts/{postId} | ResponseEntity<CommunityPostResponseDto> | 게시글 수정 (본인만)
DELETE | /community/posts/{postId} | ResponseEntity<CommonResponseDto> | 게시글 삭제 (본인만)
GET | /community/posts | ResponseEntity<Page<CommunityPostResponseDto>> | 게시글 목록 (필터: 카테고리, 지역, 키워드)
GET | /community/posts/{postId} | ResponseEntity<CommunityPostDetailResponseDto> | 게시글 상세 (조회수 증가)
POST | /community/posts/{postId}/like | ResponseEntity<CommonResponseDto> | 좋아요 등록/해제 토글(Favorite와 별개)
POST | /community/posts/{postId}/images | ResponseEntity<CommunityImageResponseDto> | 게시글 이미지 추가
DELETE | /community/posts/{postId}/images/{imageId} | ResponseEntity<CommonResponseDto> | 게시글 이미지 삭제
PATCH | /community/posts/{postId}/images/{imageId}/thumbnail | ResponseEntity<CommonResponseDto> | 대표 이미지 지정
GET | /community/posts/me | ResponseEntity<Page<CommunityPostResponseDto>> | 내가 쓴 게시글
GET | /community/posts/me/liked | ResponseEntity<Page<CommunityPostResponseDto>> | 좋아요(PostLike) 누른 게시글 (Favorite와 별개)

«Controller» CommunityCommentController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /community/posts/{postId}/comments | ResponseEntity<Page<CommunityCommentResponseDto>> | 댓글 목록 (대댓글 포함)
POST | /community/posts/{postId}/comments | ResponseEntity<CommunityCommentResponseDto> | 댓글 작성
PATCH | /community/comments/{commentId} | ResponseEntity<CommunityCommentResponseDto> | 댓글 수정 (본인만)
DELETE | /community/comments/{commentId} | ResponseEntity<CommonResponseDto> | 댓글 삭제 (본인만, CASCADE)
POST | /community/comments/{commentId}/replies | ResponseEntity<CommunityCommentReplyResponseDto> | 대댓글 작성
PATCH | /community/replies/{replyId} | ResponseEntity<CommunityCommentReplyResponseDto> | 대댓글 수정 (본인만)
DELETE | /community/replies/{replyId} | ResponseEntity<CommonResponseDto> | 대댓글 삭제 (본인만)
GET | /community/comments/me | ResponseEntity<Page<CommunityCommentResponseDto>> | 내가 쓴 댓글

«Controller» AdminCommunityController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /admin/community/posts | ResponseEntity<Page<CommunityPostResponseDto>> | 전체 게시글 조회 (필터: 카테고리/지역/기간)
DELETE | /admin/community/posts/{postId} | ResponseEntity<CommonResponseDto> | 게시글 강제 삭제 (신고 처리)
GET | /admin/community/comments | ResponseEntity<Page<CommunityCommentResponseDto>> | 전체 댓글 조회
DELETE | /admin/community/comments/{commentId} | ResponseEntity<CommonResponseDto> | 댓글 강제 삭제
DELETE | /admin/community/replies/{replyId} | ResponseEntity<CommonResponseDto> | 대댓글 강제 삭제

### ● Service

«Service» CommunityPostService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getCommunityPostList(CommunityPostSearchDto, Pageable) | Page<CommunityPostResponseDto> | readOnly | QueryDSL | 게시글 목록 (카테고리/지역/키워드 복합 필터)
getCommunityPostDetail(postId) | CommunityPostDetailResponseDto | readOnly | - | 게시글 상세 조회 (조회수는 별도incrementViewCount 메서드 호출)
registerCommunityPost(accountId, CommunityPostCreateRequestDto) | CommunityPostResponseDto | REQUIRED | - | 게시글 등록
updateCommunityPost(accountId, postId, CommunityPostUpdateRequestDto) | CommunityPostResponseDto | REQUIRED | Ownership | 게시글 수정
deleteCommunityPost(accountId, postId) | void | REQUIRED | Ownership | 게시글 삭제 (CASCADE로 이미지/댓글/좋아요 동시 삭제)
togglePostLike(accountId, postId) | void | REQUIRED | Event | 좋아요 토글 (likeCount 갱신 + 알림 이벤트)
incrementViewCount(postId) | void | REQUIRED | REDIS | 조회수 증가 (Redis 집계 → 5분 주기 배치 동기화)
getMyPosts(accountId, Pageable) | Page<CommunityPostResponseDto> | readOnly | - | 내가 쓴 게시글
getLikedPosts(accountId, Pageable) | Page<CommunityPostResponseDto> | readOnly | - | 좋아요한 게시글
addPostImage(accountId, postId, imageUrl) | CommunityImageResponseDto | REQUIRED | Ownership + ImageService | 게시글 이미지 추가
deletePostImage(accountId, postId, imageId) | void | REQUIRED | Ownership + ImageService | 게시글 이미지 삭제
setPostImageThumbnail(accountId, postId, imageId) | void | REQUIRED | Ownership + ImageService | 대표 이미지 지정

«Service» CommunityCommentService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getCommentsByPost(postId, Pageable) | Page<CommunityCommentResponseDto> | readOnly | - | 게시글 댓글 목록 (대댓글 포함, N+1 방지)
registerComment(accountId, postId, CommunityCommentCreateRequestDto) | CommunityCommentResponseDto | REQUIRED | Event | 댓글 작성 (commentCount 증가 + 게시글 작성자 알림)
updateComment(accountId, commentId, CommunityCommentUpdateRequestDto) | CommunityCommentResponseDto | REQUIRED | Ownership | 댓글 수정
deleteComment(accountId, commentId) | void | REQUIRED | Ownership | 댓글 삭제 + CASCADE 대댓글 삭제 + commentCount 감소
registerReply(accountId, commentId, CommunityCommentReplyCreateRequestDto) | CommunityCommentReplyResponseDto | REQUIRED | Event | 대댓글 작성 (commentCount 증가 + 댓글 작성자 알림)
updateReply(accountId, replyId, CommunityCommentReplyUpdateRequestDto) | CommunityCommentReplyResponseDto | REQUIRED | Ownership | 대댓글 수정
deleteReply(accountId, replyId) | void | REQUIRED | Ownership | 대댓글 삭제 (commentCount 감소)
getMyComments(accountId, Pageable) | Page<CommunityCommentResponseDto> | readOnly | - | 내가 쓴 댓글

«Service» AdminCommunityService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getAllPosts(Pageable, filter) | Page<CommunityPostResponseDto> | readOnly | Admin | 전체 게시글 조회
forceDeletePost(postId) | void | REQUIRED | Admin | 게시글 강제 삭제 (신고 처리)
getAllComments(Pageable) | Page<CommunityCommentResponseDto> | readOnly | Admin | 전체 댓글 + 대댓글 일괄 조회
forceDeleteComment(commentId) | void | REQUIRED | Admin | 댓글 강제 삭제
forceDeleteReply(replyId) | void | REQUIRED | Admin | 대댓글 강제 삭제

«Helper» CommunityAccessHelper
메서드명 | 반환타입 | 설명
---|---|---
verifyPostOwnership(accountId, postId) | CommunityPost | 게시글 작성자 본인 검증 → 실패 시 ForbiddenException
verifyCommentOwnership(accountId, commentId) | CommunityComment | 댓글 작성자 본인 검증
verifyReplyOwnership(accountId, replyId) | CommunityCommentReply | 대댓글 작성자 본인 검증

### ● Repository

«Repository» CommunityPostRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long postId) | Optional<CommunityPost> | 게시글 ID로 조회
findByIdAndAccountId(Long postId, Long accountId) | Optional<CommunityPost> | 본인 게시글 검증용
findAllByAccountId(Long accountId, Pageable pageable) | Page<CommunityPost> | 사용자 게시글 목록 (내가 쓴 글)
findAllByCategoryId(Long categoryId, Pageable pageable) | Page<CommunityPost> | 카테고리별 게시글
findAllByRegionId(Long regionId, Pageable pageable) | Page<CommunityPost> | 지역별 게시글
countByAccountId(Long accountId) | Long | 사용자 게시글 수

«Repository» CommunityPostRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchPosts(CommunityPostSearchDto, Pageable) | Page<CommunityPost> | 카테고리 + 지역 + 키워드(제목/본문) + 정렬 복합 검색
findLikedPostsByAccountId(Long accountId, Pageable) | Page<CommunityPost> | 좋아요한 게시글 (PostLike JOIN)

«Repository» CommunityImageRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long imageId) | Optional<CommunityImage> | 이미지 조회
findAllByCommunityPostId(Long postId) | List<CommunityImage> | 게시글 이미지 목록 (displayOrder 정렬)
findThumbnailByCommunityPostId(Long postId) | Optional<CommunityImage> | 대표 이미지 조회 (목록 화면용)
countByCommunityPostId(Long postId) | Long | 이미지 개수 (업로드 제한 검증)
findFirstByCommunityPostIdOrderByDisplayOrderAsc(Long postId) | Optional<CommunityImage> | 대표 자동 승격용
deleteAllByCommunityPostId(Long postId) | void | 게시글 삭제 시CASCADE

«Repository» CommunityCommentRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long commentId) | Optional<CommunityComment> | 댓글 조회
findByIdAndAccountId(Long commentId, Long accountId) | Optional<CommunityComment> | 본인 댓글 검증용
findAllByCommunityPostId(Long postId, Pageable pageable) | Page<CommunityComment> | 게시글 댓글 목록
findAllByAccountId(Long accountId, Pageable pageable) | Page<CommunityComment> | 사용자 댓글 목록
countByCommunityPostId(Long postId) | Long | 게시글 댓글 수 (commentCount 동기화 검증용)
deleteAllByCommunityPostId(Long postId) | void | 게시글 삭제 시 CASCADE

«Repository» CommunityCommentReplyRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long replyId) | Optional<CommunityCommentReply> | 대댓글 조회
findByIdAndAccountId(Long replyId, Long accountId) | Optional<CommunityCommentReply> | 본인 대댓글 검증용
findAllByCommunityCommentId(Long commentId) | List<CommunityCommentReply> | 댓글의 대댓글 목록
findAllByCommunityCommentIdIn(List<Long> commentIds) | List<CommunityCommentReply> | 다수 댓글의 대댓글 일괄 조회 (N+1 방지)
countByCommunityCommentId(Long commentId) | Long | 대댓글 수
deleteAllByCommunityCommentId(Long commentId) | void | 댓글 삭제 시 CASCADE

«Repository» PostLikeRepository
메서드명 | 반환타입 | 설명
---|---|---
findByAccountIdAndCommunityPostId(Long accountId, Long postId) | Optional<PostLike> | 좋아요 조회 (토글용)
existsByAccountIdAndCommunityPostId(Long accountId, Long postId) | boolean | 좋아요 여부 확인
countByCommunityPostId(Long postId) | Long | 게시글 좋아요 수 (likeCount 동기화 검증용)
findAllByAccountId(Long accountId, Pageable pageable) | Page<PostLike> | 사용자가 좋아요한 목록
deleteByAccountIdAndCommunityPostId(Long accountId, Long postId) | void | 좋아요 해제
deleteAllByCommunityPostId(Long postId) | void | 게시글 삭제 시 CASCADE

## 5.8 ENUM (Community 관련)

«Enum» AuditActionType (Community 관련, 중복 참조 — account.md 참조)
ENUM 값 | 설명
---|---
POST_FORCE_DELETE | 게시글 삭제
COMMENT_FORCE_DELETE | 댓글 삭제
REPLY_FORCE_DELETE | 대댓글 삭제

«Enum» CategoryType (커뮤니티 관련 항목, 중복 참조 — category.md 참조)
ENUM 값 | 설명
---|---
COMMUNITY | 커뮤니티 카테고리

«Enum» NotificationType (Community 관련, 중복 참조 — notification.md 참조)
ENUM 값 | 설명
---|---
COMMUNITY_COMMENT_NEW | 댓글 등록
COMMUNITY_REPLY_NEW | 대댓글 등록
COMMUNITY_POST_LIKE | 게시글 좋아요

«Enum» ReportRefType (Community 관련, 중복 참조 — report.md 참조)
ENUM 값 | 설명
---|---
COMMUNITY_POST | 게시글
COMMUNITY_COMMENT | 댓글
COMMUNITY_REPLY | 대댓글

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-19: 게시글 작성
- DSEQ-20: 댓글 + 좋아요

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.2 도메인 ERD (참조)

● Community 도메인
[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### CommunityPost (커뮤니티 게시글)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
community_post_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 커뮤니티 고유 식별자
account_id | bigint | FK(account), NOT NULL | 작성자
category_id | bigint | FK(category), NOT NULL | 카테고리(type=COMMUNITY)
region_id | bigint | FK(region), NOT NULL | 게시 활동 지역
title | varchar(100) | NOT NULL | 제목
content | text | NOT NULL | 본문
view_count | int | NOT NULL, DEFAULT 0 | 조회수(Redis 캐싱)
like_count | int | NOT NULL, DEFAULT 0 | 좋아요 수(Redis 캐싱)
comment_count | int | NOT NULL, DEFAULT 0 | 댓글 수(Redis 캐싱)
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### CommunityImage (게시글 이미지)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
community_image_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 이미지 고유 식별자(ImageBase 상속)
community_post_id | bigint | FK(community_post), NOT NULL | 연관 게시글
image_url | varchar(500) | NOT NULL | S3 이미지URL
display_order | int | NOT NULL, DEFAULT 0 | 표시 순서
is_thumbnail | boolean | NOT NULL, DEFAULT false | 대표 이미지 여부
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### CommunityComment (댓글)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
community_comment_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 댓글 고유 식별자
community_post_id | bigint | FK(community_post), NOT NULL | 연관 게시글
account_id | bigint | FK(account), NOT NULL | 작성자
content | text | NOT NULL | 댓글 내용
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### CommunityCommentReply (대댓글)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
community_comment_reply_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 대댓글 고유 식별자
community_comment_id | bigint | FK(community_comment), NOT NULL | 연관 댓글
account_id | bigint | FK(account), NOT NULL | 작성자
content | text | NOT NULL | 대댓글 내용
created_at | datetime | NOT NULL | BaseEntity 상속
modified_at | datetime | NOT NULL | BaseEntity 상속

### PostLike (게시글 좋아요)
컬럼명 | 타입 | 설명 | 설명
---|---|---|---
post_like_id | bigint | PK, NOT NULL, AUTO_INCREMENT | 게시글 좋아요 고유 식별자
account_id | bigint | FK(account), NOT NULL | 좋아요 누른 사용자
community_post_id | bigint | FK(community_post), NOT NULL | 좋아요 대상 게시글

> 참고: 원문 PostLike 테이블 정의에는 created_at/modified_at 컬럼이 명시되어 있지 않음 (BaseEntity 상속 절대 규칙과 다를 수 있음 — 원문 그대로 보존).

## 중복 참조 (common.md에도 기재됨)

- 게시글 조회수 Redis Write-Through 동기화(`view:post:{postId}`)는 `common.md` 6.4.1, 6.4.3 참조
- 좋아요 토글 멱등성((accountId, postId) UNIQUE + 토글)은 `common.md` 6.5.1, 6.5.4 참조
- 게시글 삭제 시 CASCADE(이미지/댓글/좋아요)는 `common.md` 6.6.6 참조
- 이벤트 카탈로그: PostLikedEvent는 `common.md` 6.2.5 참조
