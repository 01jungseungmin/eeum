package com.eeum.eeum.application.search.service;

import com.eeum.eeum.application.search.config.PopularSearchProperties;
import com.eeum.eeum.application.search.dto.response.PopularSearchKeywordResponseDto;
import com.eeum.eeum.application.search.enums.PopularSearchScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PopularSearchServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void 유효한_검색어는_범위별_ZSET과_ALL_ZSET에_원자적으로_집계한다() {
        // given
        PopularSearchService service = createService("");
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);

        // when
        service.record(PopularSearchScope.STORE, "  BBQ   Chicken  ", "account:1");

        // then
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keysCaptor.capture(), any(Object[].class));
        assertThat(keysCaptor.getValue())
                .hasSize(3)
                .anySatisfy(key -> assertThat(key).startsWith("search:popular:dedup:store:"))
                .anySatisfy(key -> assertThat(key).startsWith("search:popular:store:"))
                .anySatisfy(key -> assertThat(key).startsWith("search:popular:all:"));
    }

    @Test
    void 두글자_미만이거나_금칙어면_집계하지_않는다() {
        // given
        PopularSearchService service = createService("광고, spam word");

        // when
        service.record(PopularSearchScope.USED, "a", "account:1");
        service.record(PopularSearchScope.USED, "  SPAM   WORD ", "account:1");

        // then
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void 인기어는_점수_없이_순위와_검색어만_반환한다() {
        // given
        PopularSearchService service = createService("");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("치킨", "12", "피자", "8"));

        // when
        List<PopularSearchKeywordResponseDto> result = service.getPopularKeywords(PopularSearchScope.ALL, 10);

        // then
        assertThat(result)
                .extracting(PopularSearchKeywordResponseDto::getRank,
                        PopularSearchKeywordResponseDto::getKeyword)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "치킨"),
                        org.assertj.core.groups.Tuple.tuple(2, "피자")
                );
    }

    @Test
    void Redis_조회에_실패해도_빈_목록으로_응답한다() {
        // given
        PopularSearchService service = createService("");
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        // when
        List<PopularSearchKeywordResponseDto> result = service.getPopularKeywords(PopularSearchScope.ALL, 10);

        // then
        assertThat(result).isEmpty();
    }

    private PopularSearchService createService(String bannedKeywords) {
        PopularSearchProperties properties = new PopularSearchProperties(
                Duration.ofHours(25),
                Duration.ofMinutes(10),
                Duration.ofMinutes(1),
                24,
                2,
                50,
                bannedKeywords
        );
        return new PopularSearchService(redisTemplate, properties);
    }
}
