---
description: develop 최신 상태에서 새 작업 브랜치를 만들고 그 브랜치에서 작업 시작
allowed-tools: Bash, Read, Edit, Write, Grep, Glob
---

"$ARGUMENTS"로 설명된 작업을 위해 develop 기준으로 새 브랜치를 만든 뒤, 그 브랜치에서 바로 작업을 시작하세요.

## 절차

1. `git status`로 현재 브랜치에 커밋 안 된 변경사항이 있는지 확인한다.
   - 있으면 바로 브랜치를 옮기지 말고 사용자에게 먼저 알린다 — 커밋할지, 스태시할지, 버릴지 물어본다. 임의로 버리지 않는다.
2. `git fetch origin`
3. `git checkout develop`
4. `git pull origin develop` (fast-forward만 한다). fast-forward가 안 되면(로컬 develop에만 있는 커밋이 있는 경우) 강제로 진행하지 말고 사용자에게 상황을 설명하고 중단한다.
5. "$ARGUMENTS"를 보고 브랜치 이름을 정한다. 이 저장소의 실제 컨벤션은 `<type>/web-<role>-<slug>`다 (`git branch -a`로 기존 브랜치들 확인 가능):
   - **type**: "수정"/"오류"/"버그"/"고치다" 계열 표현이면 `fix`, "구현"/"연동"/"추가"/"신규" 계열 표현이면 `feat`. 애매하면 사용자에게 묻는다.
   - **role**: 사장 쪽 작업이면 `owner`, 관리자 쪽이면 `admin`. 둘 다 아니거나 애매하면 생략한다.
   - **slug**: 기능을 짧게 요약한 영문 kebab-case (예: "사장 승인 상태 체크리스트 수정" → `owner-approval-checklist`, "AI 매니저 플랜 구독 쪽 수정" → `owner-ai-manager-plan`).
   - 예시: `fix/web-owner-approval-checklist`, `feat/web-admin-report-export`
   - 같은 이름의 로컬/원격 브랜치가 이미 있으면 사용자에게 알리고 다른 이름을 제안한다 (예: 뒤에 숫자나 날짜를 붙이는 대신, 더 구체적인 slug로 구분).
6. `git checkout -b <브랜치명>`
7. 브랜치를 만들었다고 짧게 보고한다 (브랜치명 + 베이스 커밋 해시/메시지). 그다음 "$ARGUMENTS"에 설명된 실제 작업을 시작한다.

작업을 다 마친 뒤 완료 확인이나 이슈 문서 작성이 필요하면 `/issue` 커맨드를 따로 쓴다 — 이 커맨드의 범위는 브랜치 생성까지다.

## 주의사항

- **커밋/푸시/PR 생성은 이 커맨드의 범위 밖이다.** 사용자가 직접 커밋하고 푸시한다 — Claude는 절대 자동으로 커밋/푸시하지 않는다.
- 작업 범위는 `frontend-web/` 디렉토리 내부로 한정한다. 이 커맨드 자체도 `frontend-web/.claude/commands/`에 있어 `frontend-web/` 안에서 작업할 때만 노출된다.
- 이 저장소는 과거 브랜치에서 `feat/`와 `feature/`가 혼용된 이력이 있다 (`git branch -a` 참고) — 새로 만들 때는 `fix/`와 `feat/` 두 가지만 쓴다.
- 브랜치 이름에 이슈 번호가 있다면(`#123` 등 사용자가 언급한 경우) slug 뒤에 붙이지 않는다 — 이 저장소 커밋 메시지 컨벤션(`fix: ~~ #123`)은 이슈 번호를 브랜치명이 아니라 커밋 메시지에 넣는다.
