---
name: resource-test
description: "스레드·DB 커넥션·소켓·비동기 풀 같은 런타임 자원의 고갈, 블로킹, 누수를 재현하는 통합 테스트를 요청했을 때 사용한다. 서버 무응답, 요청 타임아웃, 커넥션 풀 고갈, SSE/WebSocket 연결 누수의 원인을 테스트로 검증하고 싶을 때 사용한다."
---

이 skill의 지시는 `.claude/commands/resource-test.md`에 있다. **그 파일을 처음부터 끝까지 읽고 그대로 따른다.**

- 지시가 "커맨드"라고 표현한 것은 이 skill을 가리킨다.
- `$ARGUMENTS`는 사용자가 이 skill에 넘긴 인자로 읽는다.
- 지시가 `.claude/CLAUDE.md`를 가리키면 `.codex/AGENTS.md`를 대신 읽어도 된다 — 내용이 같다.
- 공통 참조 문서는 `.claude/skills/references/` 아래에 있다.

원본을 이 파일로 복사해 오지 않는다. 사본을 만들면 두 곳이 어긋난다.
