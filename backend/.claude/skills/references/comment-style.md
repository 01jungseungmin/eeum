# 주석 작성 기준

주석과 Javadoc을 쓰거나 고칠 때 사용하는 단일 기준 문서다.
`CLAUDE.md`의 코딩 규칙과 같은 효력을 가진다.

## 왜 고치는가

`src/main/java` 실측(2026-09-13). 아래 수치는 전부 "목록·수치 재생성"의 스크립트가
그대로 출력하는 값이다.

| 지표 | 값 |
|---|---|
| Java 파일 | 907개 |
| 비어있지 않은 줄 | 54,917 |
| 주석 줄 | 5,352 (9.7%) |
| Javadoc 블록 | 379개, 평균 7.0줄, 총 2,665줄 |
| 12줄 이상 Javadoc 블록 | 58개 (전체 블록의 15%) |
| `<p>` | 381회 / 128개 파일 |
| `{@code` | 282회 / 92개 파일 |
| `<b>` | 115회 / 62개 파일 |
| `{@link` | 40회 / 31개 파일 |
| `<ul>` / `<li>` | 2회 / 6회 |

전체 주석 비율 9.7%는 문제가 아니다. **문제는 세 가지에 몰려 있다.**

1. **소수 파일에 과집중** — 주석 비율 48% 이상인 파일이 10개다
   (주석 15줄 이상인 파일 기준. 필터 근거는 1순위 항목 참고).
   `UsedProductCursor` 66%, `ChatRoomCursor` 58%, `AsyncConfig` 50%.
2. **읽히지 않는 HTML 마크업** — 이 프로젝트는 Javadoc HTML을 생성하지 않는다.
   API 문서는 Swagger가 만든다. `<p>`, `<b>`, `{@code}`는 렌더링되는 곳이 없고
   IDE 툴팁 밖에서는 **그냥 노이즈로 읽힌다.**
3. **프로젝트 문서와 중복** — 커서 페이징 규칙은 `CLAUDE.md`의 "페이징 선택 기준"에
   이미 있는데, 9개 파일이 같은 설명(OFFSET을 쓰지 않는 이유)을 각자 길게 반복한다.

**대부분은 지울 내용이 아니라 옮기거나 줄일 내용이다.** 이 문서는 내용을 버리라는
지침이 아니다 — 설계 근거는 살리되 있어야 할 자리로 보내라는 지침이다.

## 규칙

### R1. 주석은 "왜"만 쓴다

"무엇을"은 코드가 이미 말한다. 코드를 읽어서 알 수 있는 것은 쓰지 않는다.

```java
// ❌ 코드가 말하는 것을 반복
/** 요약에 함께 내려보내는 최근 실패 목록 크기. */
private static final int RECENT_FAILURE_LIMIT = 10;

// ✅ 이름이 충분하면 주석을 지운다
private static final int RECENT_FAILURE_LIMIT = 10;
```

다음은 "왜"에 해당하므로 **남긴다**:
- 그렇게 하지 않으면 깨지는 이유 (경합, 순서 의존, 외부 계약)
- 다른 선택지를 버린 이유
- 코드만 봐서는 틀려 보이는데 맞는 이유

### R2. 길이 상한

| 대상 | 상한 | 초과 시 |
|---|---|---|
| 클래스 Javadoc 본문 | 6줄 | 참조 문서로 옮기고 한 줄 링크 |
| 메서드 Javadoc 본문 | 4줄 | 본문을 쪼개거나 이름을 고친다 |
| 인라인 `//` | 2줄 | 정말 필요한 한 문장만 남긴다 |

Javadoc 본문은 `/**`, `*/` 구분자를 뺀 줄 수로 센다. 상한을 넘겨야 한다면 그건 보통
**주석이 아니라 설계 문서가 필요하다는 신호**다.
`.claude/skills/references/`에 항목을 만들고 코드에서는 한 줄로 가리킨다.

### R3. Javadoc HTML 태그를 쓰지 않는다

| 금지 | 대신 |
|---|---|
| `<p>` | 빈 주석 줄(` *`)로 문단을 나눈다 |
| `<b>`, `<i>` | 강조하지 않는다. 강조가 필요할 만큼 중요하면 문장을 앞으로 옮긴다 |
| `<ul>`, `<li>` | `-` 로 시작하는 평문 목록 |
| `{@code X}` | `X` 그대로 쓴다 |
| `{@link X}` | `X` 그대로 쓴다 |

예외는 하나뿐이다. `@param`, `@return`, `@throws`는 계속 쓴다 — 이건 마크업이 아니라
**구조 정보**이고 IDE가 실제로 활용한다. 단 내용이 이름의 반복이면 태그째 지운다.

**애노테이션 이름은 Javadoc 줄 맨 앞에 두지 않는다.** 줄의 첫 글자가 `@`면 Javadoc이
블록 태그 시작으로 읽어, 뒤 문장이 통째로 그 태그의 본문으로 빨려 들어간다.
`{@code @Async}`를 평문 `@Async`로 바꿀 때 특히 자주 밟는다. 앞에 단어를 하나 붙이거나
문장 순서를 바꿔 줄 중간으로 보낸다.

```java
// ❌ 줄 맨 앞의 @Primary가 미지의 블록 태그가 된다
 * @Primary가 없으면 유일 빈 해석에 실패한다.

// ✅ 줄 중간으로 보낸다
 * 또 @Primary가 없으면 유일 빈 해석에 실패한다.
```

### R3-1. 대량 치환은 Javadoc 범위 안에서만 한다

주석 문자열을 바꾸는 정규식이 Java 코드까지 건드리면 안 된다. 특히 `}}`를 일괄적으로
`}`로 바꾸는 방식은 JSON 예시의 Javadoc을 고치려다 람다·익명 클래스·중첩 블록의 닫는
괄호를 삭제할 수 있다. `{@code ...}` 안에 중괄호가 있거나 여러 줄에 걸쳐 있으면
파일 단위로 읽고 고친다. 중괄호 전체를 대상으로 하는 전역 치환은 금지한다.

대량 변경은 다음 순서를 지킨다.

1. 변경 전 `./gradlew --no-daemon --console=plain compileJava`를 실행한다.
2. Javadoc 블록 내부를 대상으로 하는 치환만 사용한다. 선언부·메서드 본문에는 정규식을
   적용하지 않는다.
3. 변경 직후 `git diff`에서 `class`, `record`, 메서드 선언, 괄호·세미콜론이 바뀌지
   않았는지 확인한다. 주석이 아닌 변경이 보이면 즉시 중단하고 원인을 조사한다.
4. 금지 태그와 줄 맨 앞의 비구조 `@`를 다시 검색한 뒤 컴파일을 재실행한다.

`{@code}`와 `{@link}`는 닫는 중괄호를 단순 삭제하지 않는다. `{@code { "key": {} }}`처럼
중첩 중괄호가 있는 예시는 수동으로 평문화한다. `<p>`·`<b>` 같은 태그도 치환 후 문장이
이어지거나 목록 구조가 깨지지 않았는지 파일 단위로 확인한다.

### R3-2. Javadoc 본문을 자동으로 잘라내지 않는다

**길이를 줄이는 작업은 자동화하지 않는다.** 스크립트로 "첫 문장만 남기고 나머지를 버리는"
축약은 금지한다. 첫 문장은 대개 그 클래스가 *무엇인지*를 적어 둔 줄이고, 뒤따르는 문단이
*왜 그렇게 했는지*다. 기계는 이 둘을 구분하지 못하므로, 길이 기준으로 자르면 R1이
"남긴다"고 정한 것만 골라서 지우고 "쓰지 않는다"고 정한 것만 남기는 정반대 결과가 나온다.

실제로 이 저장소에서 한 번 벌어진 일이다. 37개 블록을 일괄 축약해 138개 파일에서
**순삭제 821줄**이 발생했고, 그중에는 다음처럼 코드만 봐서는 복원할 수 없는 근거가 있었다.

```java
// 자동 축약이 남긴 것 — 이름을 읽으면 아는 내용
/** 토큰 회수의 최종 근거 — 계정의 토큰 세대를 올린다. */

// 자동 축약이 버린 것 — 코드에 없는 내용
//   Redis 삭제만으로는 새는 세 가지 경로
//   AFTER_COMMIT이면 안 되는 이유
//   이 리스너 실패가 원 트랜잭션을 롤백시키는 것이 의도인 이유
```

축약은 다음 순서로 **사람이** 한다.

1. 파일을 열어 원문을 읽는다. 문단마다 R1의 "남긴다" 세 항목에 해당하는지 판정한다.
2. 해당하면 6줄 안으로 압축하거나 참조 문서로 옮긴다. 해당하지 않으면 지운다.
3. 옮긴 내용은 옮긴 곳에 실제로 들어갔는지 확인한다. **지우기 전에 옮긴다.**
4. 되살릴 때 원문을 그대로 붙여 넣지 않는다 — 그 사이 코드가 바뀌어 설명이 이미
   틀렸을 수 있다. 현재 코드와 대조해 여전히 참인 문장만 남긴다.

4번은 가정이 아니다. 위 사고를 복구할 때 `AsyncConfig`의 `CallerRunsPolicy`·`큐 500` 서술이
이미 `WaitForQueueSpacePolicy`·`큐 100`으로 바뀌어 있었다. 원문을 그대로 되살렸다면
틀린 정보를 복원하는 셈이었다.

되돌릴 근거는 `git show <축약 커밋>~1:<경로>`에 남아 있다. 축약 커밋을 코드 변경과 섞지
않아야(`docs:` 단독 커밋) 이 복구가 가능하다.

### R4. 프로젝트 규칙은 반복하지 않는다

`CLAUDE.md`나 `.claude/skills/references/`에 이미 있는 내용은 코드에서 되풀이하지 않는다.
한 줄로 가리키고 끝낸다.

```java
// ❌ 커서 파일 5개가 각자 12~19줄로 같은 설명을 반복
/**
 * ...OFFSET을 쓰지 않는 이유가 이 타입의 존재 이유다. 기본 정렬이 최신순이라
 * 새 글이 맨 앞에 꽂힌다. 1페이지를 읽고 2페이지를 요청하는 사이 한 건이 등록되면...
 */

// ✅ 규칙은 CLAUDE.md에, 여기서는 이 타입에만 해당하는 것만
/**
 * 중고 게시글 목록 커서. 페이징 규칙은 CLAUDE.md "페이징 선택 기준" 참고.
 *
 * 정렬 키를 클라이언트가 고를 수 있어(createdAt·price·favoriteCount·viewCount)
 * 값의 타입도 함께 바뀐다. 그래서 문자열 원문으로 들고 다니고, 해석은 리포지토리가 한다.
 * sortValue가 null이면 가격제안(price null) 글에 걸린 커서다.
 */
```

### R5. 한 파일에서 같은 이야기를 두 번 하지 않는다

클래스 Javadoc에 쓴 내용을 메서드 Javadoc에서 되풀이하지 않는다.
메서드가 여러 개면 공통 근거는 클래스에 한 번만 쓴다.

## 적용 예시

### 예시 1 — 형식만 바꾸면 되는 경우

```java
// Before (본문 14줄)
/**
 * 일반 비동기 풀 — 한정자 없는 모든 {@code @Async}가 여기로 온다.
 *
 * <p>예전에는 이 빈 이름이 {@code applicationTaskExecutor}였다. 그 이름은 Spring MVC가
 * async 처리용 executor를 <b>이름으로 찾는 자리</b>라, 알림 작업과 MVC async가 한 풀을
 * 나눠 쓰게 된다. 알림이 밀리면 MVC async가 함께 밀리고, 그 반대도 마찬가지다.
 * 이름을 분리하고 MVC용 풀을 따로 뒀다.
 *
 * <p>{@code @Primary}가 필요하다. Executor 빈이 둘 이상이면 한정자 없는 {@code @Async}가
 * 유일 빈 해석에 실패하고, 이름이 {@code taskExecutor}인 빈도 없어
 * <b>SimpleAsyncTaskExecutor로 조용히 내려앉는다</b> — 작업마다 새 스레드를 만드는 무제한 실행이다.
 * ...
 */

// After (본문 6줄) — 애노테이션이 줄 맨 앞에 오지 않게 배치한 것도 함께 본다
/**
 * 한정자 없는 @Async가 오는 기본 풀.
 *
 * 빈 이름이 applicationTaskExecutor면 Spring MVC async와 풀을 공유하게 돼 분리했다.
 * 또 @Primary가 없으면 유일 빈 해석에 실패해 SimpleAsyncTaskExecutor로 조용히
 * 내려앉는다 — 작업마다 새 스레드를 만드는 무제한 실행이다.
 * 큐 100: 더 키우면 max에 닿기 전에 큐가 먼저 차서 max 설정이 죽는다.
 */
```

### 예시 2 — 참조 문서로 옮겨야 하는 경우

`UsedReviewResponseDto.from`의 17줄 Javadoc(비공개 게시글 제목 노출 정책)은
주석이 아니라 **공개 범위 정책**이다. `.claude/skills/references/used-favorite-review.md`가
이미 상세 공개 정책을 다루므로 그쪽으로 옮기고, 코드에는 3줄만 남긴다.

```java
/**
 * 비공개 게시글의 제목은 제3자에게만 감춘다(작성자·판매자는 본다).
 * 상세 정책은 used-favorite-review.md 참고.
 */
```

### 예시 3 — 그대로 두는 경우

```java
// ✅ 짧고, 왜만 말하고, 코드로는 알 수 없다 — 손대지 않는다
// 종료된 방은 참여자 레코드가 남아 있어도 구독을 허용하지 않는다
chatAccessHelper.verifyActiveRoomParticipant(accountId, roomId);
```

## 수정 대상

### 1순위 — 주석 비율이 높은 파일

**주석 15줄 이상인 파일 중** 비율 48% 이상 = 10개다.

| 파일 | 주석 비율 | 주석/전체 |
|---|---|---|
| `domain/used/repository/UsedReviewRepositoryCustom.java` | 68% | 17/25 |
| `domain/order/enums/PaymentCancellationStatus.java` | 68% | 17/25 |
| `domain/used/repository/UsedProductCursor.java` | 66% | 29/44 |
| `domain/chat/repository/ChatRoomCursor.java` | 58% | 34/59 |
| `domain/used/repository/UsedReviewCursor.java` | 55% | 28/51 |
| `domain/favorite/repository/FavoriteCursor.java` | 53% | 26/49 |
| `config/AsyncConfig.java` | 50% | 72/143 |
| `domain/chat/repository/ChatMessageCursor.java` | 50% | 23/46 |
| `application/order/dto/response/PortOneCancelResult.java` | 50% | 16/32 |
| `config/SchedulerLockConfig.java` | 48% | 15/31 |

**15줄 필터를 거는 이유.** 필터 없이 세면 48% 이상이 24개로 늘어나는데, 늘어난 14개는
`UsedReviewSummary`(7/10줄, 70%)처럼 **파일 자체가 10줄 남짓인** 것들이다. 짧은 파일에
한 줄짜리 설명이 붙으면 비율은 쉽게 치솟지만 정리할 분량이 없다. 비율만으로 고르면
실제 문제 파일이 그 뒤로 밀린다.

커서 5종은 **R4 중복 제거**로 대부분 해결된다. 공통 설명을 `CLAUDE.md`에 한 번만 두고
각 파일에는 그 타입 고유의 사정만 남긴다.

### 2순위 — 12줄 이상 Javadoc 블록 (58개)

여기서 "12줄"은 `/**`, `*/`를 포함한 **물리 줄 수**다(R2의 본문 줄 수와 다르다).
가장 긴 것부터:

| 줄 | 위치 |
|---|---|
| 22 | `config/AsyncConfig.java:13` |
| 22 | `application/operation/listener/OperationFailureLogListener.java:15` |
| 22 | `application/account/scheduler/WebSocketSessionReconciliationScheduler.java:20` |
| 20 | `application/order/service/PaymentCancellationService.java:19` |
| 19 | `domain/used/repository/UsedProductCursor.java:6` |
| 19 | `config/WaitForQueueSpacePolicy.java:10` |
| 18 | `application/account/listener/AccountSessionTerminationListener.java:12` |
| 17 | `domain/chat/repository/ChatRoomCursor.java:9` |
| 17 | `application/used/service/UsedProductWithdrawalService.java:17` |
| 17 | `application/used/dto/response/UsedReviewResponseDto.java:48` |
| 16 | `domain/favorite/repository/FavoriteCursor.java:9` |
| 16 | `config/AsyncConfig.java:40` |
| 16 | `application/order/dto/response/PortOneCancelResponse.java:9` |
| 16 | `application/notification/service/UnreadSyncExecutor.java:11` |
| 16 | `application/chat/service/ChatRoomService.java:92` |
| 16 | `application/account/service/AccountSanctionPolicy.java:10` |

### 3순위 — 태그 일괄 정리

1순위·2순위를 정리한 뒤 남은 파일에 R3을 적용한다.

```bash
# 남아 있는 태그 위치 확인 — R3이 금지한 태그 전부
grep -rn '<p>\|<b>\|<i>\|<ul>\|<li>\|{@code\|{@link' src/main/java --include=*.java
```

`<p>`는 기계적으로 빈 주석 줄로 바꿀 수 있지만, `{@code}`/`{@link}` 제거는
문장이 어색해지는 경우가 있어 **파일 단위로 읽고 고친다.** 전역 중괄호 치환은 하지 않는다.
평문으로 바꾼 애노테이션이 줄 맨 앞에 오지 않았는지 R3 기준으로 함께 확인한다.

### 목록·수치 재생성

이 문서의 모든 수치와 1·2순위 목록을 다시 만든다.
`src/main/java`가 있는 디렉터리(`backend/`)에서 실행한다.

주의할 점이 둘 있다.

- 태그는 `{@code}`가 아니라 **`{@code`** 로 센다. 실제 코드는 `{@code @Async}` 형태라
  닫는 중괄호까지 붙여 찾으면 **0개로 나온다.**
- Javadoc 블록 탐색은 **줄 시작**으로 한정한다. 그냥 `/**`를 찾으면
  `/sub/chat/rooms/**` 같은 STOMP destination 문자열이 오탐으로 걸린다.

```bash
python3 - <<'PY'
import glob, re

files = sorted(glob.glob("src/main/java/**/*.java", recursive=True))
sources = {path: open(path, encoding="utf-8").read() for path in files}
comment_line = re.compile(r"^\s*(?://|/\*|\*|\*/)")
javadoc = re.compile(r"^[ \t]*/\*\*.*?\*/", re.S | re.M)

blocks = [m.group(0) for src in sources.values() for m in javadoc.finditer(src)]
block_lines = [b.count("\n") + 1 for b in blocks]
lines = [l for src in sources.values() for l in src.splitlines() if l.strip()]
total, comments = len(lines), sum(bool(comment_line.match(l)) for l in lines)

print(f"Java files: {len(files)}")
print(f"Non-empty lines: {total:,}")
print(f"Comment lines: {comments:,} ({comments / total:.1%})")
print(f"Javadoc blocks: {len(blocks)}, total {sum(block_lines):,} lines, "
      f"average {sum(block_lines) / len(blocks):.1f}")
n12 = sum(n >= 12 for n in block_lines)
print(f"Javadoc blocks >= 12 physical lines: {n12} ({n12 / len(blocks):.0%})")
for tag in ("<p>", "{@code", "<b>", "{@link", "<ul>", "<li>"):
    print(f"{tag}: {sum(s.count(tag) for s in sources.values())} occurrences"
          f" / {sum(tag in s for s in sources.values())} files")

# 1순위 — 파일별 주석 비율 (주석 15줄 이상 & 48% 이상). 기준 충족분을 전부 출력한다.
print("\n-- per-file comment ratio (comment lines >= 15, ratio >= 48%) --")
rows = []
for path, src in sources.items():
    ls = [l for l in src.splitlines() if l.strip()]
    c = sum(bool(comment_line.match(l)) for l in ls)
    if ls and c >= 15 and c / len(ls) >= 0.48:
        rows.append((c / len(ls), c, len(ls), path))
for r, c, t, p in sorted(rows, reverse=True):
    print(f"{r:6.0%}  {c:3d}/{t:3d}  {p}")
print(f"total: {len(rows)} files")

# 2순위 — 12줄 이상 Javadoc 블록 전체 목록
print("\n-- javadoc blocks >= 12 physical lines --")
out = []
for path, src in sources.items():
    for m in javadoc.finditer(src):
        n = m.group(0).count("\n") + 1
        if n >= 12:
            out.append((n, path, src[:m.start()].count("\n") + 1))
for n, p, l in sorted(out, reverse=True):
    print(f"{n:3d}줄  {p}:{l}")
PY
```

## 적용 절차

1. 위 순위대로 한 번에 한 파일씩 고친다. **길이를 줄이는 축약은 자동화하지 않는다**(R3-2).
   태그 제거처럼 형식만 바꾸는 치환만 일괄 처리할 수 있고, 그것도 R3-1의 절차를 따른다.
2. 주석만 바꾸는 커밋은 코드 변경과 섞지 않는다 — `docs:` prefix로 따로 커밋한다.
   잘못 지운 근거를 `git show <커밋>~1:<경로>`로 되찾으려면 이 분리가 전제다.
3. 참조 문서로 옮긴 내용은 옮긴 뒤 원본에서 반드시 지운다. 양쪽에 두면 나중에 갈라진다.
   순서를 뒤집지 않는다 — **지우기 전에 옮긴다.**
4. **이 프로젝트에는 Javadoc 자동 검증 수단이 없다.** 눈으로 확인한다.

   - `./gradlew build`는 javadoc을 돌리지 않는다. `build.gradle`에 javadoc 설정이 없고
     `./gradlew build --dry-run` 태스크 그래프에도 javadoc이 없다.
   - `./gradlew javadoc`도 쓸 수 없다. javadoc 도구는 Lombok이 생성하는 코드를 보지 못해
     `ReportTargetResolver.java:210`의 `ReportTargetSnapshotDtoBuilder`(`@Builder` 생성 클래스)에서
     `cannot find symbol`로 즉시 실패한다. delombok 단계를 붙이기 전에는 성립하지 않는 태스크다.
     **이 실패는 주석과 무관하므로 주석 품질의 신호로 읽지 않는다.**

   주석만 바꾸는 변경은 컴파일이 어차피 통과한다. 따라서 IDE 경고와 리뷰가 유일한 방어선이고,
   특히 **줄 맨 앞 `@`**(R3)는 조용히 깨지므로 반드시 눈으로 확인한다.

## 새 코드를 쓸 때

이 기준은 기존 코드 정리용이자 신규 작성 기준이다. 새 주석을 쓰기 전에 자문한다:

- 이 문장은 코드를 읽으면 알 수 있는가? → 그러면 쓰지 않는다
- 이미 `CLAUDE.md`나 참조 문서에 있는가? → 그러면 가리키기만 한다
- 본문이 6줄을 넘는가? → 참조 문서로 보낸다
- HTML 태그를 쓰고 있는가? → 뺀다
- 애노테이션 이름이 줄 맨 앞에 있는가? → 줄 중간으로 옮긴다

기존 주석을 **줄이려** 할 때는 하나 더 묻는다.

- 지우려는 문단이 "왜"인가 "무엇"인가? → "왜"면 압축하거나 옮긴다. 길다고 자르지 않는다(R3-2)
