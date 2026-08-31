# Codex project guidance

이 저장소의 코딩 규칙·아키텍처·도메인 설명은 **`.claude/CLAUDE.md` 한 곳에만** 있다.
작업을 시작하기 전에 그 파일을 처음부터 끝까지 읽고 그대로 따른다.

예전에는 같은 내용을 이 파일에도 복사해 두었으나, 두 벌을 유지하다 실제로 내용이 어긋났다
(결제·정산 검토 게이트가 한쪽에만 존재하는 등). 그래서 사본을 없애고 단일 소스로 합쳤다.
**이 파일에 규칙을 다시 복사해 오지 않는다.**

## 파일 위치

| 내용 | 경로 |
|---|---|
| 프로젝트 규칙 전체 (빌드·아키텍처·코딩 표준·도메인) | `.claude/CLAUDE.md` |
| 공통 참조 문서 (리뷰 공통 규칙, 레이어 규칙, 동시성 체크리스트, 결제·정산, 자원 예산) | `.claude/skills/references/` |
| 재사용 워크플로 | `.codex/skills/*/SKILL.md` — 각 파일은 `.claude/` 원본을 가리키는 포인터다 |

## Codex에서 읽을 때의 대응

`.claude/CLAUDE.md`와 `.claude/**/*.md`는 Claude Code용 표현으로 쓰여 있다. 아래만 바꿔 읽으면 된다.

- "커맨드" / "슬래시 커맨드" → 이 저장소의 skill
- `$ARGUMENTS` → 사용자가 skill에 넘긴 인자
- `.claude/agents/{name}.md` → `.codex/skills/{name}/SKILL.md`와 같은 역할

## 규칙을 수정할 때

`.claude/` 아래 원본만 고친다. `.codex/` 아래에는 규칙 본문을 두지 않는다.
새 skill을 추가하면 `.codex/skills/{name}/SKILL.md`에 frontmatter와 원본 경로만 적는다.

## 마무리

- 모든 기능이 종료된 후 바뀐 부분에 대한 설명과 이유를 작성해서 정리
- 테스트면 테스트로 기능이면 기능으로 묶어서 출력
