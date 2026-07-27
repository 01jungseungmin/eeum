---
description: 이음 표준 구조로 신규 도메인 스캐폴딩
allowed-tools: Read, Write, Glob
---

"$ARGUMENTS" 도메인을 이음 프로젝트 표준 구조로 생성하세요.

## 작업 순서

1. 도메인명을 패키지명, 클래스명, URL 리소스명(복수형 kebab-case)으로 변환한다.
2. 기존 도메인(store 또는 order)의 실제 코드를 읽고 패키지/네이밍/작성 패턴을 확인한다.
3. `.claude/skills/references/layer-rules.md`의 레이어별 규칙에 따라
   4-layer 구조(api/{domain} · application/{domain}의 service/dto/mapper · domain/{domain}의 entity/repository/enums)에 파일을 생성한다.
4. URL, 인증, 예외, 삭제, 알림 정책은 `.claude/skills/references/policies.md`를 따른다.
5. 완료 후 `.claude/skills/references/output.md` 형식으로 요약한다.

## 기존 코드 우선 원칙 (최상위 규칙)

- 구현 방식은 반드시 기존 도메인의 실제 코드 패턴을 우선한다. 판단이 필요하면
  임의로 정하지 말고 참고한 도메인과 동일하게 한다.
- 기존 코드에서 확인되지 않은 정책(필드, 상태값, 조회 조건, soft delete, count,
  regionId 등)은 구현하지 말고 TODO 주석으로 남긴다.
- 목록 조회는 기존 도메인이 Page 기반이면 Page를 사용한다. Slice는 채팅처럼
  기존 코드에 명확한 유사 사례가 있을 때만 사용한다.
- RepositoryCustom + RepositoryImpl은 검색 조건, 동적 정렬, 복잡한 조인이
  명시된 경우에만 생성한다.

## 금지 사항

- 기존 패키지 구조, 공통 응답/예외 구조를 새로 만들거나 변경하지 않는다.
- Secret, API Key, 환경변수 값을 생성하거나 하드코딩하지 않는다.
- 테스트를 위해 운영 로직을 변경하지 않는다.
- 도메인 비즈니스 정책을 임의로 확정하지 않는다.
