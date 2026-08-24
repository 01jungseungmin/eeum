package com.eeum.eeum.application.favorite.service;

import com.eeum.eeum.application.favorite.dto.response.FavoriteResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.support.SqlCaptureInspector;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 찜 전체 목록의 페이징 계약 검증.
 *
 * <p>신규 경로 {@code GET /favorites/me/all}은 모바일 무한 스크롤이므로 Slice여야 하고,
 * 레거시 경로 {@code GET /favorites/me}는 이미 배포된 번호 페이징 계약이라 전체 건수를 유지해야 한다.
 * count 쿼리 실행 여부는 Mock으로 재현할 수 없어 실제 DB로 검증한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class FavoriteAllListIntegrationTest extends IntegrationTestSupport {

    private final FavoriteService favoriteService;
    private final FavoriteRepository favoriteRepository;
    private final AccountRepository accountRepository;

    private Account viewer;

    @BeforeEach
    void setUp() {
        viewer = accountRepository.save(Account.createUser(
                "viewer-all@test.com", "encoded_pw", "찜한사람", "찜한사람닉", "010-1111-1111"));

        // 타입을 섞어 담는다 — /me/all은 refType 무관 목록이다.
        favoriteRepository.saveAndFlush(Favorite.create(viewer, FavoriteRefType.STORE, 1L));
        favoriteRepository.saveAndFlush(Favorite.create(viewer, FavoriteRefType.USED_PRODUCT, 2L));
        favoriteRepository.saveAndFlush(Favorite.create(viewer, FavoriteRefType.STORE, 3L));
    }

    @AfterEach
    void tearDown() {
        favoriteRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void 무한_스크롤_목록은_count_쿼리를_실행하지_않는다() {
        // given: Slice로 바꾼 이유가 count 제거다. Page 구현으로 되돌아가면 여기서 걸린다.
        SqlCaptureInspector.reset();

        // when
        favoriteService.getMyFavorites(viewer.getAccountId(), PageRequest.of(0, 20));

        // then
        assertThat(countQueries()).as("실행된 count 쿼리: %s", countQueries()).isEmpty();
    }

    @Test
    void 무한_스크롤_목록은_다음_페이지_여부를_정확히_판정한다() {
        // when
        Slice<FavoriteResponseDto> first =
                favoriteService.getMyFavorites(viewer.getAccountId(), PageRequest.of(0, 2));
        Slice<FavoriteResponseDto> second =
                favoriteService.getMyFavorites(viewer.getAccountId(), PageRequest.of(1, 2));

        // then
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 레거시_번호_페이징은_전체_건수를_그대로_유지한다() {
        // 이미 배포된 계약이라 totalElements가 사라지면 클라이언트가 깨진다.
        @SuppressWarnings("removal")
        Page<FavoriteResponseDto> first =
                favoriteService.getMyFavoritesPaged(viewer.getAccountId(), PageRequest.of(0, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
    }

    @Test
    void 요청한_정렬과_무관하게_찜_등록_최신순으로_고정한다() {
        // given: 요청 sort를 그대로 두면 실제 순서와 응답 메타데이터가 달라진다.
        Slice<FavoriteResponseDto> result = favoriteService.getMyFavorites(
                viewer.getAccountId(), PageRequest.of(0, 20, Sort.by("refId")));

        // then: 마지막에 담은 찜이 먼저 온다
        assertThat(result.getContent())
                .extracting(FavoriteResponseDto::getRefId)
                .containsExactly(3L, 2L, 1L);
        assertThat(result.getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void 두_경로가_같은_순서를_돌려준다() {
        // 레거시와 신규가 다른 순서를 주면 마이그레이션 중 목록이 흔들린다.
        Slice<FavoriteResponseDto> sliced =
                favoriteService.getMyFavorites(viewer.getAccountId(), PageRequest.of(0, 20));
        @SuppressWarnings("removal")
        Page<FavoriteResponseDto> paged =
                favoriteService.getMyFavoritesPaged(viewer.getAccountId(), PageRequest.of(0, 20));

        assertThat(sliced.getContent())
                .extracting(FavoriteResponseDto::getFavoriteId)
                .isEqualTo(paged.getContent().stream().map(FavoriteResponseDto::getFavoriteId).toList());
    }

    private List<String> countQueries() {
        return SqlCaptureInspector.captured().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select count("))
                .toList();
    }
}
