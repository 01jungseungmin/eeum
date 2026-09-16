#!/usr/bin/env bash
# PreToolUse(Bash) — 위험한 셸 명령 차단
# exit 0 = 허용, JSON deny 출력 = 차단

set -euo pipefail

INPUT=$(cat)
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

[ -z "$CMD" ] && exit 0

deny() {
  jq -n --arg reason "$1" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$reason}}'
  exit 0
}

# 1. 파괴적 파일 삭제
if echo "$CMD" | grep -qE '\brm\b\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)+(/|\*|~|\$HOME|\.\.)'; then
  deny "차단: 광범위 삭제 명령 (rm -rf /, *, ~ 등). 특정 경로를 명시해서 다시 시도하세요."
fi

# 2. 보호 브랜치 직접 push 차단 (이음 Git 컨벤션: feature/{기능명} → develop PR)
if echo "$CMD" | grep -qE 'git\s+push.*\b(main|master)\b'; then
  deny "차단: main 브랜치 직접 push 금지. develop 베이스 PR을 사용하세요."
fi
if echo "$CMD" | grep -qE 'git\s+push.*\bdevelop\b'; then
  deny "차단: develop 직접 push 금지. feature/{기능명} 브랜치에 push 후 PR로 머지하세요."
fi

# 3. 작업 내역 파괴 (커밋 안 된 변경사항 소실)
if echo "$CMD" | grep -qE 'git\s+reset\s+--hard'; then
  deny "차단: git reset --hard 금지. 커밋 안 된 작업이 사라집니다. git stash를 사용하세요."
fi
if echo "$CMD" | grep -qE 'git\s+clean\s+-[a-zA-Z]*f'; then
  deny "차단: git clean -f 금지. 추적되지 않는 파일이 영구 삭제됩니다."
fi

# 4. 권한 파괴
if echo "$CMD" | grep -qE 'chmod\s+(-[a-zA-Z]*R[a-zA-Z]*\s+)?777|chmod\s+777|chown\s+-[a-zA-Z]*R'; then
  deny "차단: 재귀적 권한/소유자 변경 금지. 필요한 파일만 개별 지정하세요."
fi

# 5. DB 파괴 명령
if echo "$CMD" | grep -qiE 'DROP\s+(TABLE|DATABASE|SCHEMA)|TRUNCATE\s+TABLE'; then
  deny "차단: DROP/TRUNCATE 명령. 스키마 변경은 마이그레이션 스크립트로 작성 후 사람이 직접 실행하세요."
fi
if echo "$CMD" | grep -qiE 'mysql\b.*-e\s+.*\bDELETE\s+FROM\b'; then
  deny "차단: mysql CLI로 DELETE 직접 실행 금지. 데이터 삭제는 사람이 확인 후 실행합니다."
fi

# 6. Docker 볼륨 삭제 (로컬 DB 데이터 소실)
if echo "$CMD" | grep -qiE 'docker(\s+|-)compose\s+down.*-v|docker\s+volume\s+(rm|prune)'; then
  deny "차단: Docker 볼륨 삭제 금지. MySQL/Redis 데이터가 삭제될 수 있습니다. down은 -v 없이 실행하세요."
fi

# 7. .env / 시크릿 파일 덮어쓰기
if echo "$CMD" | grep -qE '>\s*\.{0,2}/?\.env\b|>\s*.*\.env\b'; then
  deny "차단: .env 파일 덮어쓰기 금지. 환경변수 변경은 사람이 직접 합니다."
fi

# 8. git hook 우회
if echo "$CMD" | grep -qE '\-\-no\-verify\b'; then
  deny "차단: --no-verify로 git hook 우회 금지."
fi

# 9. Redis 전체 삭제 (개발 중 토큰/락/캐시 전부 날아감)
if echo "$CMD" | grep -qiE 'redis-cli.*(FLUSHALL|FLUSHDB)'; then
  deny "차단: Redis FLUSH 금지. refresh token, 분산 락, unread 카운트가 전부 삭제됩니다. 특정 키만 DEL 하세요."
fi

exit 0
