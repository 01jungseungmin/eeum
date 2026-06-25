# Report 도메인 SDD 명세

> 코드 경로: report
> 비고: 신고 및 관리자 처리
> 파일명 안내: 작업 지시상 원래 파일명은 `report.md`이나, 작업 환경의 파일 쓰기 도구가 "report"로 시작하는 정확한 파일명(`report.md`)을 보고서 파일로 오인하여 차단하는 정책 제약이 있어 `sdd-report.md`로 저장하였다. 필요 시 사람이 직접 `report.md`로 파일명을 변경하면 된다.

## 구현 상태

현재 Report 도메인은 아직 실제 코드로 구현되지 않았다.

구현 예정 범위:

- Report Entity
- ReportController
- AdminReportController
- ReportService
- AdminReportService
- ReportRepository
- 신고 대상 타입 관리
- 신고 상태 처리
- 관리자 신고 처리 기능

따라서 현재 코드에 Report 관련 구현이 없는 것은 결함이 아니라 "구현 예정"으로 분류한다.

## 4. 설계 클래스 다이어그램 - 관련 기능 설명

### DCOM-11. 신고

설계다이어그램
[다이어그램 원본은 기존 SDD 문서 참조]

주의: SDD 원문에는 DCOM-11(신고) 항목의 "기능"과 "설계다이어그램" 표 헤더만 존재하며, 다른 DCOM 항목들과 달리 서술형 설명 단락이 원문에 기재되어 있지 않다. 원문이 이후 곧바로 5. 설계 클래스 명세 절로 이어지는 구조로 되어 있어, 신고 기능에 대한 서술형 설명은 원문 자체에 누락된 것으로 보인다. 원문 그대로 보존하며 빈 설명 영역을 임의로 추가하지 않았다.

## 5.x Report 도메인 명세

주의: SDD 원문 5장(설계 클래스 명세)은 Account(5.1) -> Store(5.2) -> Order/Payment(5.3) -> UsedProduct(5.4) -> Community(5.5) -> Chat(5.6) -> 공통 도메인(5.7) -> ENUM(5.8) 순서로 구성되어 있으며, Report에 대한 별도 절(예: "5.x Report 도메인")이나 Entity Report, Controller ReportController, Service ReportService 명세가 존재하지 않는다.

Report 관련하여 원문에 실제로 존재하는 내용은 다음 세 가지뿐이다.

1. 4장의 DCOM-11 "신고" 식별 항목 (서술형 설명 없음, 위 참조)
2. 5.7절(공통 도메인) Repository 섹션 말미에 위치한 ReportRepository, ReportRepositoryCustom 명세 (아래 Repository 항목 참조)
3. 5.8절(ENUM) 내 ReportReason, ReportRefType, ReportStatus ENUM 정의 (아래 ENUM 항목 참조)

따라서 Report Entity 속성, Controller API, Service 메서드 명세는 SDD 원문에 존재하지 않으며, 본 파일에서도 임의로 생성하지 않고 원문에 있는 내용만 기재한다. 향후 SDD 보강이 필요한 영역이다.

### Repository (원문 5.7절 공통 도메인 Repository 섹션에 위치)

«Repository» ReportRepository
메서드명 | 반환타입 | 설명
---|---|---
findById(Long reportId) | Optional<Report> | 신고 조회
findByIdAndAccountId(Long reportId, Long accountId) | Page<Report> | 본인 신고 검증용
findAllByAccountId(Long accountId, Pageable pageable) | List<Report> | 내 신고 목록
findAllByRefTypeAndRefId(ReportRefType refType, Long refId) | List<Report> | 대상별 신고 목록
findAllByStatus(ReportStatus status, Pageable pageable) | Page<Report> | 상태별 신고 목록(관리자용)
countByRefTypeAndRefIdAndStatus(ReportRefType refType, Long refId, ReportStatus status) | Long | 중복/누적 신고 검증
existsByAccountIdAndRefTypeAndRefId(Long accountId, ReportRefType refType, Long refId) | boolean | 동일 대상 중복 신고 방지
deleteAllByRefTypeAndRefId(ReportRefType refType, Long refId) | void | 대상 도메인 삭제 시 CASCADE

참고: 원문에는 findByIdAndAccountId의 반환타입이 Page<Report>, findAllByAccountId의 반환타입이 List<Report>로 기재되어 있다 (메서드명과 반환타입이 일반적인 Spring Data 명명 규칙과 어긋나는 것으로 보이나, 원문 표기 그대로 보존함 - 실제로는 findByIdAndAccountId -> Optional<Report>, findAllByAccountId -> Page<Report>일 가능성이 높음).

«Repository» ReportRepositoryCustom (QueryDSL)
메서드명 | 반환타입 | 설명
---|---|---
searchReports(ReportSearchDto condition, Pageable pageable) | Page<Report> | 관리자 복합 필터(refType/status/기간/키워드)
findTopReportedTargets(ReportRefType refType, LocalDateTime from, int limit) | List<ReportStatProjection> | 신고 누적 통계(관리자 대시보드)

## 5.8 ENUM (Report 관련)

«Enum» ReportReason
ENUM 값 | 설명
---|---
SPAM | 스팸
INAPPROPRIATE_CONTENT | 부적절한 내용
HARASSMENT | 괴롭힘
FRAUD | 사기
FAKE_PRODUCT | 가짜 상품
SEXUAL_CONTENT | 음란물
VIOLENCE | 폭력
PRIVACY_VIOLATION | 개인정보 침해
COPYRIGHT | 저작권 침해
REPORT_ABUSE | 신고
ETC | 기타

«Enum» ReportRefType
ENUM 값 | 설명
---|---
USER | 사용자
STORE | 상점
STORE_REVIEW | 상점 리뷰
USED_PRODUCT | 중고상품
USED_PRODUCT_REVIEW | 중고 리뷰
COMMUNITY_POST | 게시글
COMMUNITY_COMMENT | 댓글
COMMUNITY_REPLY | 대댓글
CHAT_MESSAGE | 채팅 메시지

«Enum» ReportStatus
ENUM 값 | 설명
---|---
PENDING | 처리 대기
REVIEWING | 검토중
PROCESSED | 처리 완료
DISMISSED | 기각

## 8. 설계 시퀀스 다이어그램 (관련)

- DSEQ-29: 신고 등록

[다이어그램 원본은 기존 SDD 문서 참조]

참고: 시퀀스 다이어그램 목록에는 "DSEQ-29 신고 등록"이 존재하나 본문 다이어그램/세부 흐름은 원문에 별도로 기재되어 있지 않다 (8장은 식별 번호와 기능명 목록만 제공하고, 실제 다이어그램은 [다이어그램 원본은 기존 SDD 문서 참조]로 별도 보관됨).

## 9.3 테이블 정의

주의: SDD 원문 9.3절(테이블 정의)에는 Report 테이블에 대한 컬럼 정의가 존재하지 않는다. Account부터 InquiryImage까지의 테이블 정의 이후 곧바로 10장(UI 설계)으로 이어지며, Report 관련 테이블 정의는 원문에서 누락되어 있다. ReportRepository 명세(위 참조)를 통해 최소한 report_id, account_id, ref_type, ref_id, reason(ReportReason), status(ReportStatus), content 등의 컬럼이 존재할 것으로 추정되나, 원문에 명시되어 있지 않으므로 임의로 추가하지 않는다.

## 중복 참조 (common.md에도 기재됨)

- Hard Delete + CASCADE: Report는 대상 도메인 삭제 시 deleteAllByRefTypeAndRefId로 정리되며, common.md 6.6.6 "공통 삭제" 트리거 표(Favorite, Report -> deleteAllByRefTypeAndRefId)에 동일하게 기재되어 있음
- 관리자 신고 처리 관련 Controller/Service(예: AdminReportController, AdminReportService)는 원문에 명시되어 있지 않으나, 다른 도메인의 관리자 강제 삭제/정지 처리(account.md의 AdminAccountService, store.md의 AdminStoreService 등)와 동일한 패턴(Admin + AdminAudit)을 따를 것으로 예상됨 - 코드베이스 구현 시 참고
