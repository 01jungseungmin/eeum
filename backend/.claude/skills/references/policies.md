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
  (프로젝트 기준 Soft Delete 대상: Account, ChatMessage, Category)
- 부모-자식 구조 보존 등 삭제 정책 판단이 필요하면 구현하지 말고 TODO로 남긴다.
- Hard Delete 시 연관 자식 데이터 삭제 순서를 고려하되, cascade/orphanRemoval
  사용 여부는 기존 도메인 패턴을 따른다.

## 알림/이벤트

- 알림이 필요한 도메인이면 Service에서 알림을 직접 호출하지 않는다.
- 기존 이벤트 패턴(`@TransactionalEventListener(AFTER_COMMIT)`)을 참고해
  도메인 이벤트 클래스를 함께 생성한다.
- 알림 필요 여부가 명시되지 않은 경우 TODO 주석으로 남긴다.