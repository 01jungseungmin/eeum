---
name: new-domain
description: 이음 Spring Boot 프로젝트에서 신규 도메인의 Controller, Service, Repository, Entity, DTO, Mapper 기본 구조를 기존 store/order 도메인 패턴에 맞춰 스캐폴딩한다.
argument-hint: "[domain-name]"
allowed-tools: Read, Write, Glob
disable-model-invocation: true
---

# 이음 신규 도메인 스캐폴딩

`$ARGUMENTS` 도메인을 이음 프로젝트 표준 구조로 생성한다.

## 생성 범위 (4-layer)

api/{domain}/                ← Controller
application/{domain}/
service/                   ← Service
dto/request/               ← Request DTO
dto/response/              ← Response DTO
mapper/                    ← Entity ↔ DTO 매퍼
domain/{domain}/
entity/                    ← JPA 엔티티
repository/                ← Repository
enums/                     ← 상태 ENUM

## 작업 순서

1. 도메인명을 패키지명, 클래스명, URL 리소스명(복수형 kebab-case)으로 변환한다.
2. 기존 도메인(store 또는 order)의 실제 코드를 읽고 패키지/네이밍/작성 패턴을 확인한다.
3. `references/layer-rules.md`를 읽고 레이어별 규칙에 따라 파일을 생성한다.
4. URL, 인증, 예외, 삭제, 알림 정책은 `references/policies.md`를 따른다.
5. 작업 완료 후 `references/output.md` 형식으로 요약한다.

## 기존 코드 우선 원칙 (최상위 규칙)

- 새 도메인의 구현 방식은 반드시 기존 도메인의 실제 코드 패턴을 우선한다.
- 판단이 필요한 경우 임의로 정하지 말고, 참고한 기존 도메인과 동일한 방식을 사용한다.
- 기존 코드에서 확인되지 않은 정책은 구현하지 말고 TODO 주석으로 남긴다.
- 목록 조회는 기존 도메인이 Page 기반이면 Page를 사용한다. Slice는 채팅처럼
  기존 코드에서 명확히 Slice를 쓰는 유사 사례가 있을 때만 사용한다.

## 스캐폴딩 범위 제한

- 도메인별 비즈니스 정책을 임의로 추론하거나 확정하지 않는다.
- 정책이 명시되지 않은 필드, 상태값, 조회 조건은 TODO 주석으로 남긴다.
- regionId, status, softDelete, count 정책 등을 임의로 추가하지 않는다.
- 기존 도메인에서 반복적으로 쓰이는 최소 구조만 생성한다.
- RepositoryCustom + RepositoryImpl은 검색 조건, 동적 정렬, 복잡한 조인이
  명시된 경우에만 생성한다. 단순 CRUD/목록 조회만 있으면 생성하지 않는다.

## 금지 사항

- 기존 패키지 구조를 임의로 변경하지 않는다.
- 기존 공통 응답/예외 구조를 새로 만들지 않는다.
- Secret, API Key, 환경변수 값을 생성하거나 하드코딩하지 않는다.
- 테스트를 위해 운영 로직을 변경하지 않는다.
- 도메인 정책을 임의로 확정하지 않는다.