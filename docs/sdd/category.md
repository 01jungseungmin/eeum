# Category 도메인 SDD

> 코드 경로: category, adminCategory
> 비고: 공통 카테고리

## 5.7 공통 도메인 내 Category 명세 (원문 위치)

> 참고: SDD 원문에는 Category 도메인에 대한 4장 DCOM 식별 항목(설명 단락)이 별도로 존재하지 않는다. Category는 Store(상점 카테고리), UsedProduct(중고거래 카테고리), Community(커뮤니티 카테고리) 도메인에서 공통으로 참조하는 전역 엔티티로서 5.7절(공통 도메인)에 명세되어 있다.

## 구현 상태

현재 Category 도메인은 일부 구현 상태일 수 있으며, 관리자 카테고리 관리 기능은 아직 구현 전 또는 보완 예정이다.

구현 예정 범위:

- AdminCategoryController
- AdminCategoryService
- 카테고리 생성
- 카테고리 수정
- 카테고리 비활성화
- 카테고리 재활성화
- 참조 데이터가 없는 경우의 물리 삭제
- 카테고리 트리 캐시 갱신

sdd-compliance-checker 검토 시 사용자 조회용 Category 기능과 관리자 Category 기능을 구분해서 판단한다.

### ● Entity

«Entity» Category
속성명 | 타입 | 제약조건 | 설명
---|---|---|---
categoryId | Long | PK, NOT NULL | 고유 식별자
type | CategoryType | NOT NULL | STORE / USED / COMMUNITY
parentId | Long | FK, nullable | 상위 카테고리 (대>중>소, 최상위는 null)
name | String | NOT NULL | 카테고리명
displayOrder | int | NOT NULL, DEFAULT 0 | 같은 부모 내 표시 순서
depth | int | NOT NULL, DEFAULT 1 | 1=대분류, 2=중분류, 3=소분류 (최대 3)
isActive | boolean | NOT NULL, DEFAULT true | 노출 여부 (Soft Delete 기본값)
createdAt | LocalDateTime | NOT NULL | BaseEntity 상속
modifiedAt | LocalDateTime | NOT NULL | BaseEntity 상속

«Entity» Category (도메인 메서드)
메서드명 | 반환타입 | 설명
---|---|---
activate() | void | 노출 활성화
deactivate() | void | 노출 비활성화 (Soft Delete)
isRoot() | boolean | 최상위 여부 (parentId == null)
canHaveChild() | boolean | 자식 추가 가능 여부 (depth < 3)
updateDisplayOrder(int) | void | 표시 순서 변경

### ● Controller

«Controller» CategoryController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
GET | /categories/{type} | ResponseEntity<List<CategoryTreeResponseDto>> | 타입별 카테고리 트리 (활성만, 캐싱 응답)
GET | /categories/{type}/roots | ResponseEntity<List<CategoryResponseDto>> | 최상위 카테고리 목록
GET | /categories/{categoryId}/children | ResponseEntity<List<CategoryResponseDto>> | 직속 자식 카테고리

«Controller» AdminCategoryController
MAP 방식 | API명 | 반환타입 | 설명
---|---|---|---
POST | /admin/categories | ResponseEntity<CategoryResponseDto> | 카테고리 생성 (depth 검증)
PATCH | /admin/categories/{categoryId} | ResponseEntity<CategoryResponseDto> | 수정 (이름 / 표시순서)
DELETE | /admin/categories/{categoryId} | ResponseEntity<CommonResponseDto> | 삭제 (기본: Soft Delete, 참조 0개일 때만 Hard Delete 가능)
PATCH | /admin/categories/{categoryId}/activate | ResponseEntity<CommonResponseDto> | 활성화
PATCH | /admin/categories/{categoryId}/deactivate | ResponseEntity<CommonResponseDto> | 비활성화
GET | /admin/categories | ResponseEntity<List<CategoryResponseDto>> | 전체 조회 (비활성 포함)

### ● Service

«Service» CategoryService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
getCategoryTree(CategoryType type) | List<CategoryTreeResponseDto> | readOnly | RedisCache | 타입별 활성 카테고리 트리 (Key: category:tree:{type}, TTL 1h)
getRootCategories(CategoryType type) | List<CategoryResponseDto> | readOnly | RedisCache | 최상위 활성 카테고리
getChildCategories(Long categoryId) | List<CategoryResponseDto> | readOnly | - | 직속 자식 카테고리
getCategoryById(Long categoryId) | Category | readOnly | - | 카테고리 조회 (다른 도메인 검증용)
validateCategoryType(Long categoryId, CategoryType expected) | void | readOnly | - | 타입 일치 검증
validateCategoryActive(categoryId) | void | readOnly | - | 활성 카테고리인지 검증 (게시글/상점 등록 시 사용)

«Service» AdminCategoryService
메서드명 | 반환타입 | 트랜잭션 | 정책 / AOP | 설명
---|---|---|---|---
createCategory(CategoryCreateRequestDto) | CategoryResponseDto | REQUIRED | Admin + CacheEvict | 생성 (parent 검증, depth ≤ 3, 중복 이름 검증)
updateCategory(Long categoryId, CategoryUpdateRequestDto) | CategoryResponseDto | REQUIRED | Admin + CacheEvict | 수정 (이름 / 순서)
deactivateCategory(Long categoryId) | void | REQUIRED | Admin + CacheEvict | Soft Delete (기본 삭제)
activateCategory(Long categoryId) | void | REQUIRED | Admin + CacheEvict | 재활성화
hardDeleteCategory(Long categoryId) | void | REQUIRED | Admin + CacheEvict | 물리 삭제 (자식 0 + 참조 0 검증, 실패 시 예외)
getAllCategories(CategoryType type) | List<CategoryResponseDto> | readOnly | Admin | 비활성 포함 전체 조회

### ● Repository

«Repository» CategoryRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long categoryId) | Optional<Category> | 카테고리 조회
findAllByTypeAndIsActiveTrue(CategoryType type) | List<Category> | 타입별 활성 카테고리 (트리 구성용)
findAllByType(CategoryType type) | List<Category> | 타입별 전체 (관리자)
findAllByTypeAndParentIdIsNullAndIsActiveTrue(CategoryType type) | List<Category> | 타입별 최상위 활성 @Cacheable(value = "categoryTree", key = "#type")
findAllByParentIdAndIsActiveTrue(Long parentId) | List<Category> | 활성 자식
existsByParentId(Long categoryId) | boolean | 자식 존재 여부 (Hard Delete 검증)
existsByTypeAndParentIdAndName(CategoryType type, Long parentId, String name) | boolean | 중복 이름 검증
countByType(CategoryType type) | Long | 타입별 카테고리 수

## 6. 공통 설계 패턴 - Category 관련 캐싱 전략 (원문 위치는 common.md 6.4와 동일, 도메인 연관성으로 중복 기재)

### 6.4.2 Cache-Aside 패턴 (카테고리 트리 적용 예시)

조회 시 캐시를 먼저 확인하고, 없을 경우 DB 조회 후 캐시에 저장한다.

적용 예시

```java
@Cacheable(value = "categoryTree", key = "#type", unless = "#result == null")
@Transactional(readOnly = true)
public List<CategoryTreeResponseDto> getCategoryTree(CategoryType type) {
    List<Category> categories = categoryRepository.findAllByTypeAndIsActiveTrue(type);
    return buildTree(categories);
}
@CacheEvict(value = "categoryTree", key = "#dto.type")
@Transactional
public CategoryResponseDto createCategory(CategoryCreateRequestDto dto) {
    ...
}
```

### 6.6.5 Category 삭제 전략 (원문 6장 위치, 도메인 연관성으로 중복 기재)

기본적으로 Soft Delete를 적용하며, 참조 데이터가 없는 경우에만 Hard Delete를 허용한다.

적용 예시

```java
public void deactivateCategory(Long categoryId) {
    category.deactivate();
}
public void hardDeleteCategory(Long categoryId) {
    if (참조 존재) throw Exception;
    categoryRepository.deleteById(categoryId);
}
```

설계 의도

- 참조 무결성 유지
- 안전한 삭제 정책 적용

## 5.8 ENUM (Category 관련)

«Enum» CategoryType
ENUM 값 | 설명
---|---
STORE | 상점 카테고리
USED | 중고거래 카테고리
COMMUNITY | 커뮤니티 카테고리

«Enum» AuditActionType (Category 관련, 중복 참조 — account.md 참조)
ENUM 값 | 설명
---|---
CATEGORY_CREATE | 카테고리 생성
CATEGORY_UPDATE | 카테고리 수정
CATEGORY_DEACTIVATE | 카테고리 비활성화
CATEGORY_HARD_DELETE | 카테고리 완전 삭제

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-27: 카테고리 트리 조회 (캐싱)

[다이어그램 원본은 기존 SDD 문서 참조]

## 9.3 테이블 정의

### Category (전역 카테고리)

| 컬럼명        | 타입        | 설명                         | 설명                                |
| ------------- | ----------- | ---------------------------- | ----------------------------------- |
| category_id   | bigint      | PK, NOT NULL, AUTO_INCREMENT | 전역 카테고리 고유 식별자           |
| type          | varchar(20) | NOT NULL                     | CategoryType ENUM                   |
| parent_id     | bigint      | FK(category), nullable       | 상위 카테고리(대>중>소, 최상위null) |
| name          | varchar(50) | NOT NULL                     | 카테고리명                          |
| display_order | int         | NOT NULL, DEFAULT 0          | 같은 부모 내 표시 순서              |
| depth         | int         | NOT NULL, DEFAULT 1          | 1=대분류, 2=중분류, 3=소분류(최대3) |
| is_active     | boolean     | NOT NULL, DEFAULT true       | 노출 여부(Soft Delete 기본값)       |
| created_at    | datetime    | NOT NULL                     | BaseEntity 상속                     |
| modified_at   | datetime    | NOT NULL                     | BaseEntity 상속                     |

## 중복 참조 (common.md에도 기재됨)

- 캐싱 전략: `category:tree:{type}` (TTL 1h, 관리자 변경 시 CacheEvict)는 `common.md` 6.4.1 참조
- 삭제 정책: Category는 Soft Delete(isActive) + 조건부 Hard Delete 정책을 따르며 `common.md` 6.6.2, 6.6.5 참조
- Category는 CLAUDE.md 기준 Soft Delete(deletedAt 아닌 isActive 필드 사용) 대상 엔티티 목록(Account, ChatMessage, Category) 중 하나임 — `common.md` 참조
