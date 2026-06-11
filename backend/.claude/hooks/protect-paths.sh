#!/usr/bin/env bash
# PreToolUse(Write|Edit) — 보호 파일 수정 차단

set -euo pipefail

INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

[ -z "$FILE" ] && exit 0

deny() {
  jq -n --arg reason "$1" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$reason}}'
  exit 0
}

BASENAME=$(basename "$FILE")

# 1. 환경변수 / 시크릿 / 키 파일
case "$BASENAME" in
  .env|.env.*|*.pem|*.key|*service-account*.json|firebase*.json)
    deny "차단: 시크릿 파일($BASENAME)은 Claude가 수정할 수 없습니다. 사람이 직접 수정하세요."
    ;;
  *.p12|*.jks|*.keystore)
    deny "차단: 키스토어 파일($BASENAME)은 수정 금지. 인증서 관리는 사람이 직접 합니다."
    ;;
  id_rsa|id_rsa.*|id_ed25519|id_ed25519.*|id_ecdsa|id_ecdsa.*)
    deny "차단: SSH 개인키($BASENAME)는 접근 금지."
    ;;
esac

# 2. 프로덕션/운영 프로파일 설정
if echo "$FILE" | grep -qE 'application-(prod|real|live)\.(yml|yaml|properties)$|docker-compose\.(prod|real|live)\.ya?ml$'; then
  deny "차단: 운영 프로파일 설정 파일은 직접 수정 금지. 변경이 필요하면 사람에게 diff를 제시하세요."
fi

# 3. CI/CD 파이프라인 (배포 사고 방지 — 수정 필요 시 사람이 확인)
if echo "$FILE" | grep -qE '\.github/workflows/.*\.ya?ml$'; then
  deny "차단: GitHub Actions 워크플로우는 보호 대상입니다. 수정안을 채팅으로 제시하면 사람이 반영합니다."
fi

# 4. Gradle Wrapper (빌드 재현성 보장)
if echo "$FILE" | grep -qE 'gradle/wrapper/|gradlew(\.bat)?$'; then
  deny "차단: Gradle Wrapper 파일은 수정 금지."
fi

exit 0
