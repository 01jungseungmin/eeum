#!/usr/bin/env bash
# PostToolUse(Write|Edit) — 이음 코딩 표준 빠른 검사 (grep 기반, ~수십 ms)
# 위반 발견 시 exit 2 + stderr → Claude에게 피드백되어 즉시 수정 유도
# (PostToolUse는 이미 실행된 뒤라 차단은 불가, 피드백만 가능)

set -uo pipefail

INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Java 파일만 검사
[[ "$FILE" != *.java ]] && exit 0
[ ! -f "$FILE" ] && exit 0

# 테스트 파일은 일부 규칙 제외
IS_TEST=false
[[ "$FILE" == */src/test/* ]] && IS_TEST=true

VIOLATIONS=""

add() { VIOLATIONS="${VIOLATIONS}- $1\n"; }

# 1. System.out.println 금지 (SLF4J 사용)
if grep -nE 'System\.(out|err)\.print' "$FILE" >/dev/null; then
  LINES=$(grep -nE 'System\.(out|err)\.print' "$FILE" | cut -d: -f1 | tr '\n' ',' | sed 's/,$//')
  add "System.out.println 금지 (line $LINES) — SLF4J 로거(@Slf4j + log.info)로 교체"
fi

# 2. 필드 @Autowired 금지 (생성자 주입)
if grep -A1 '@Autowired' "$FILE" | grep -qE '(private|protected)\s+\w+\s+\w+\s*;'; then
  add "@Autowired 필드 주입 금지 — @RequiredArgsConstructor 생성자 주입으로 교체"
fi

# 3. EAGER 로딩 금지
if grep -n 'FetchType.EAGER' "$FILE" >/dev/null; then
  LINES=$(grep -n 'FetchType.EAGER' "$FILE" | cut -d: -f1 | tr '\n' ',' | sed 's/,$//')
  add "FetchType.EAGER 금지 (line $LINES) — LAZY로 교체"
fi

# 4. 가격 필드 Double/Float 금지 (프로덕션 코드만)
if [ "$IS_TEST" = false ]; then
  if grep -nE '(private|protected)\s+(Double|double|Float|float)\s+\w*([Pp]rice|[Aa]mount|[Cc]ost|[Ff]ee)' "$FILE" >/dev/null; then
    add "가격/금액 필드에 Double/Float 사용 금지 — BigDecimal로 교체"
  fi
fi

# 5. 엔티티 @Setter 금지
if grep -qE '@Entity' "$FILE" && grep -nE '^\s*@Setter' "$FILE" >/dev/null; then
  add "엔티티에 @Setter 금지 — 정적 팩토리 + 도메인 메서드로 상태 변경"
fi

# 6. Service 클래스에 @Transactional 누락 의심
if [ "$IS_TEST" = false ] && echo "$FILE" | grep -qE 'Service\.java$'; then
  if grep -qE 'public\s+\w+.*\(' "$FILE" && ! grep -q '@Transactional' "$FILE"; then
    add "Service에 @Transactional 없음 — 읽기는 readOnly=true, 쓰기는 기본 @Transactional 명시"
  fi
fi

# 7. JUnit Assertions 금지 (AssertJ 사용) — 테스트 파일만
if [ "$IS_TEST" = true ]; then
  if grep -n 'org.junit.jupiter.api.Assertions' "$FILE" >/dev/null; then
    add "JUnit Assertions 금지 — AssertJ(assertThat)로 교체"
  fi
fi

if [ -n "$VIOLATIONS" ]; then
  echo -e "이음 코딩 표준 위반 ($FILE):\n${VIOLATIONS}위 항목을 수정하세요." >&2
  exit 2
fi

exit 0
