# URL / 보안 / 예외 / 삭제 / 알림 정책

## URL

- 전역 `/api` prefix가 설정되어 있으면 Controller에 `/api`를 중복 작성하지 않는다.
- 기존 도메인에서 Controller URL에 `/api`를 직접 붙이지 않았다면 새 도메인에도 붙이지 않는다.
- 일반 사용자 API는 기존 Controller 패턴을 따른다.
- 사장 API: `/owner/...` / 관리자 API: `/admin/...`
- 리소스명은 복수형 kebab-case를 사용한다.

## 인증/인가

- API가 비회원 접근 가능한지, 로그인 필수인지 임의로 판단하지 않는다.
- 기존 유사 도메인의 SecurityConfig, `@PreAuthorize` 패턴을 우선 확인한다.
- ROLE_USER와 ROLE_OWNER가 모두 접근해야 하는 예외 케이스는 기존 실제
  Controller 패턴을 따른다.
- 인증/인가 정책이 명시되지 않은 경우 아래 주석을 남긴다:
  `// TODO: 인증/인가 정책 확정 후 @PreAuthorize 또는 SecurityConfig에 반영`
- 동작하는 기능 완성 요청에서는 위 TODO를 남기지 말고 구현 전에 사용자 결정을 받는다.
- 인증 필터 통과 후 계정이 탈퇴·정지될 수 있으므로 쓰기 Service에서 현재 Account 상태를
  다시 검증한다.
- 목록·상세·count·등록·신고처럼 같은 대상을 공개하는 API는 동일한 공개 상태 조건을 사용한다.
- 비공개 전환 뒤에도 삭제·정리가 필요하면 쓰기는 허용할 수 있지만 응답에서 보호된 대상 정보나
  카운터를 새로 노출하지 않는다.

## 예외 처리

- 기존 `ErrorCode` enum 패턴을 확인하고, 필요한 경우 기존 네이밍 규칙에 맞춰
  도메인별 ErrorCode를 추가한다.
- 예외는 기존 방식에 맞춰 `BusinessException(ErrorCode.X)` 또는 기존 하위
  예외 클래스를 사용한다.
- 도메인별 BusinessException 하위 클래스는 기존 도메인에서 사용 중인 경우에만 생성한다.
- ErrorCode 없이 문자열 메시지만 던지지 않는다.
- HTTP status를 임의로 확정하지 않고 기존 유사 에러코드를 참고한다.

## 삭제 정책

- 기본적으로 Soft Delete를 임의로 추가하지 않는다.
- 기존 도메인에서 Soft Delete를 사용하는 경우에만 동일 패턴을 따른다.
  (프로젝트 기준 Soft Delete 대상: Account, ChatMessage, Category, CommunityComment, UsedProduct)
- 부모-자식 구조 보존 등 삭제 정책 판단이 필요하면 구현하지 말고 TODO로 남긴다.
- Hard Delete 시 연관 자식 데이터 삭제 순서를 고려하되, cascade/orphanRemoval
  사용 여부는 기존 도메인 패턴을 따른다.
- 완성 구현의 삭제 정책이 미정이면 TODO 대신 Gate 1에서 사용자 결정을 받는다.
- 계정·부모 엔티티의 물리 삭제 전 다른 도메인의 FK와 polymorphic 참조를 모두 확인한다.
- 배치가 여러 대상을 처리하면 한 건의 FK 실패가 전체 트랜잭션을 롤백하는지도 확인한다.

## 동시성/카운터

- actor 상태와 target 상태를 함께 검증하는 쓰기는 공통 mutex와 잠금 순서를 정한다.
- 여러 대상 행을 잠그면 ID 오름차순처럼 모든 진입점에서 같은 전역 순서를 사용한다.
- 카운터는 원본 행의 INSERT/DELETE 성공과 같은 트랜잭션에서 원자적으로 변경한다.
- UNIQUE는 엔티티 선언과 운영 스키마 양쪽에 반영하고 기존 중복 정리 순서를 명시한다.
- 전체 재계산·마이그레이션은 실시간 쓰기와 공유 mutex 또는 write-pause 정책을 정한다.

## 알림/이벤트

- 알림이 필요한 도메인이면 Service에서 알림을 직접 호출하지 않는다.
- 기존 이벤트 패턴(`@TransactionalEventListener(AFTER_COMMIT)`)을 참고해
  도메인 이벤트 클래스를 함께 생성한다.
- 알림 필요 여부가 명시되지 않은 경우 TODO 주석으로 남긴다.
