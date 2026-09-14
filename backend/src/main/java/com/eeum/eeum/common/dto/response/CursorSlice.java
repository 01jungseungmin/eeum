package com.eeum.eeum.common.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/** 커서 무한 스크롤 응답. */
@Getter
@Schema(description = "커서 무한 스크롤 응답")
public class CursorSlice<T> {

    @Schema(description = "목록 항목")
    private final List<T> content;

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    private final boolean hasNext;

    /**
     * 다음 요청의 cursorValue. 다음 페이지가 없으면 null이다.
     *
     * 정렬 키가 NULL인 구간(가격제안 글, 대화 없는 채팅방)에서는 다음 페이지가 있어도
     * null이다 — 그 구간은 nextCursorId만으로 이어 읽는다.
     */
    @Schema(description = "다음 요청에 그대로 넣을 cursorValue. 다음 페이지가 없으면 null",
            example = "2026-09-01T10:00:00")
    private final String nextCursorValue;

    @Schema(description = "다음 요청에 그대로 넣을 cursorId. 다음 페이지가 없으면 null", example = "41")
    private final Long nextCursorId;

    /**
     * 실제로 적용된 정렬. "속성,방향" 형식이고, NULL 처리를 지정한 항목에는
     * NULLS_LAST/NULLS_FIRST가 덧붙는다.
     *
     * Spring의 Sort를 그대로 실으면 JSON에 sorted/unsorted/empty 세 개만 나가
     * 정작 어떤 필드를 어느 방향으로 정렬했는지가 응답에서 사라진다. 그래서 문자열로 펼쳐 담는다.
     */
    @Schema(description = "실제로 적용된 정렬. 요청 정렬을 무시했더라도 여기에는 적용값이 담긴다",
            example = "[\"createdAt,DESC\", \"usedReviewId,DESC\"]")
    private final List<String> sort;

    private CursorSlice(
            List<T> content, boolean hasNext, String nextCursorValue, Long nextCursorId, List<String> sort) {
        this.content = content;
        this.hasNext = hasNext;
        this.nextCursorValue = nextCursorValue;
        this.nextCursorId = nextCursorId;
        this.sort = sort;
    }

    /**
     * 다음 페이지가 없으면 커서를 비운다. 마지막 페이지에 커서를 실어 보내면 클라이언트가
     * 빈 페이지를 한 번 더 요청하게 된다.
     */
    public static <T> CursorSlice<T> of(
            List<T> content, boolean hasNext, String nextCursorValue, Long nextCursorId, Sort sort) {
        return new CursorSlice<>(
                content,
                hasNext,
                hasNext ? nextCursorValue : null,
                hasNext ? nextCursorId : null,
                describe(sort));
    }

    public static <T> CursorSlice<T> empty(Sort sort) {
        return new CursorSlice<>(List.of(), false, null, null, describe(sort));
    }

    /**
     * 정렬을 JSON에 그대로 드러나는 문자열로 펼친다.
     *
     * NULL 처리는 지정했을 때만 붙인다. NOT NULL 컬럼에까지 붙이면 응답이 실제로 일어나지
     * 않는 NULL 처리를 주장하게 된다.
     */
    private static List<String> describe(Sort sort) {
        List<String> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            String described = order.getProperty() + "," + order.getDirection();
            if (order.getNullHandling() != Sort.NullHandling.NATIVE) {
                described += "," + order.getNullHandling();
            }
            orders.add(described);
        }
        return List.copyOf(orders);
    }

    /**
     * 항목만 바꾼다 — 커서와 정렬은 그대로 옮긴다.
     *
     * 커서는 엔티티의 정렬 키에서 나오므로 DTO 변환 뒤에는 다시 만들 수 없다.
     * 리포지토리가 만든 값을 그대로 들고 내려온다.
     */
    public <R> CursorSlice<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>(content.size());
        for (T item : content) {
            mapped.add(mapper.apply(item));
        }
        return new CursorSlice<>(mapped, hasNext, nextCursorValue, nextCursorId, sort);
    }

    // Slice와 같은 이름으로 읽을 수 있게 둔다. Lombok의 isHasNext()도 함께 생기지만
    // JSON 필드는 hasNext 하나다 — Jackson은 is-접두 게터만 프로퍼티로 인식한다.
    public boolean hasNext() {
        return hasNext;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    // 배치 조회(참여자 수·썸네일 등)를 위해 항목 ID를 모으는 데 쓴다.
    public Stream<T> stream() {
        return content.stream();
    }
}
