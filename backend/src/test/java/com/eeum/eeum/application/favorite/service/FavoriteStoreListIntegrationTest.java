package com.eeum.eeum.application.favorite.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.favorite.dto.response.FavoriteStoreResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.OwnerInfo;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.OwnerInfoRepository;
import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.enums.StoreStatus;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.support.SqlCaptureInspector;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.eeum.eeum.common.dto.response.CursorSlice;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상점 찜 목록의 공개 조건이 실제 MySQL 쿼리에서 페이징·count 전에 적용되는지 검증한다.
 * <p>
 * 조회 후 메모리에서 거르면 페이지 크기·전체 건수·페이지 경계가 모두 어긋난다.
 * 공개 조건(계정 활성 + 상점 미정지 + 사장 승인)은 조인 3개가 얽혀 있어 Mock으로 재현할 수 없다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class FavoriteStoreListIntegrationTest extends IntegrationTestSupport {


    private final FavoriteService favoriteService;
    private final FavoriteRepository favoriteRepository;
    private final StoreRepository storeRepository;
    private final OwnerInfoRepository ownerInfoRepository;
    private final AccountRepository accountRepository;

    private Account viewer;
    private Long visibleStoreId;

    @BeforeEach
    void setUp() {
        viewer = accountRepository.save(Account.createUser(
                "viewer@test.com", "encoded_pw", "찜한사람", "찜한사람닉", "010-1111-1111"));

        // 노출 대상 3곳
        visibleStoreId = favoriteStore("open1", true, StoreStatus.OPEN, false);
        favoriteStore("open2", true, StoreStatus.OPEN, false);
        favoriteStore("open3", true, StoreStatus.OPEN, false);

        // 비노출 3곳 — 미승인 / 정지 / 탈퇴 계정
        favoriteStore("pending", false, StoreStatus.OPEN, false);
        favoriteStore("suspended", true, StoreStatus.SUSPENDED, false);
        favoriteStore("withdrawn", true, StoreStatus.OPEN, true);
    }

    @AfterEach
    void tearDown() {
        favoriteRepository.deleteAll();
        storeRepository.deleteAll();
        ownerInfoRepository.deleteAll();
    }

    @Test
    void 비공개_상점은_페이징_전에_걸러져_요청한_페이지_크기와_다음_페이지_판정이_유지된다() {
        // when: 노출 대상은 3곳뿐이다.
        CursorSlice<FavoriteStoreResponseDto> first =
                favoriteService.getMyFavoriteStores(viewer.getAccountId(), null, null, 2);
        CursorSlice<FavoriteStoreResponseDto> second =
                favoriteService.getMyFavoriteStores(
                        viewer.getAccountId(),
                        first.getNextCursorValue(), first.getNextCursorId(), 2);

        // then: 조회 후 걸렀다면 첫 페이지가 2건보다 적거나 hasNext 판정이 어긋난다.
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    void 비공개_상점은_레거시_번호_페이징에서도_전체_건수에_잡히지_않는다() {
        // 레거시 경로(/favorites/me/STORE)는 배포된 계약이라 Page 응답을 유지한다.
        Page<FavoriteStoreResponseDto> first =
                favoriteService.getMyFavoriteStoresPaged(viewer.getAccountId(), PageRequest.of(0, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
    }

    @Test
    void 미승인_정지_탈퇴_상점은_목록에_나오지_않는다() {
        CursorSlice<FavoriteStoreResponseDto> result =
                favoriteService.getMyFavoriteStores(viewer.getAccountId(), null, null, 20);

        assertThat(result.getContent())
                .extracting(FavoriteStoreResponseDto::getName)
                .containsExactlyInAnyOrder("open1", "open2", "open3");
    }

    @Test
    void 무한_스크롤_목록은_count_쿼리를_실행하지_않는다() {
        // given: Slice로 바꾼 이유가 count 제거다. Page 구현으로 되돌아가면 여기서 걸린다.
        SqlCaptureInspector.reset();

        // when
        favoriteService.getMyFavoriteStores(viewer.getAccountId(), null, null, 20);

        // then
        assertThat(countQueries()).as("실행된 count 쿼리: %s", countQueries()).isEmpty();
    }

    @Test
    void 레거시_번호_페이징의_전체_건수는_한_번의_count_쿼리로만_구한다() {
        // given: fetchOne()을 삼항 연산자 양쪽에서 부르면 같은 count가 두 번 실행된다.
        SqlCaptureInspector.reset();

        // when
        favoriteService.getMyFavoriteStoresPaged(viewer.getAccountId(), PageRequest.of(0, 20));

        // then
        assertThat(countQueries()).as("실행된 count 쿼리: %s", countQueries()).hasSize(1);
    }

    @Test
    void 찜한_상점이_없으면_실제_적용된_정렬을_담아_빈_목록을_반환한다() {
        favoriteRepository.deleteAll();

        CursorSlice<FavoriteStoreResponseDto> result =
                favoriteService.getMyFavoriteStores(viewer.getAccountId(), null, null, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        // 빈 목록에서도 적용 정렬을 그대로 설명해야 한다. 커서는 비운다 —
        // 남겨두면 클라이언트가 빈 페이지를 한 번 더 요청한다.
        assertThat(result.getSort()).containsExactly("createdAt,DESC", "favoriteId,DESC");
        assertThat(result.getNextCursorValue()).isNull();
        assertThat(result.getNextCursorId()).isNull();
    }

    private List<String> countQueries() {
        return SqlCaptureInspector.captured().stream()
                .filter(sql -> sql.toLowerCase().startsWith("select count("))
                .toList();
    }

    // 상점 하나를 만들고 viewer가 찜한 상태로 둔다. 비공개 상점은 toggleFavorite이 막으므로 직접 저장한다.
    private Long favoriteStore(String name, boolean approved, StoreStatus status, boolean ownerWithdrawn) {
        Account owner = accountRepository.save(Account.createUser(
                name + "@test.com", "encoded_pw", "사장" + name, "닉" + name, "010-2222-2222"));

        OwnerInfo ownerInfo = OwnerInfo.create(owner, "123-45-" + name.hashCode(), LocalDate.now());
        if (approved) {
            ownerInfo.approve();
        }
        ownerInfoRepository.save(ownerInfo);

        Store store = Store.createForOwnerSignup(owner, name, "서울시 강남구", "010-3333-3333");
        ReflectionTestUtils.setField(store, "status", status);
        Long storeId = storeRepository.saveAndFlush(store).getStoreId();

        if (ownerWithdrawn) {
            owner.withdraw();
            accountRepository.saveAndFlush(owner);
        }

        favoriteRepository.saveAndFlush(
                Favorite.create(viewer, FavoriteRefType.STORE, storeId));
        return storeId;
    }
}
