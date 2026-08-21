package com.eeum.eeum.application.account.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.favorite.dto.request.FavoriteToggleRequestDto;
import com.eeum.eeum.application.favorite.service.FavoriteService;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.account.repository.RegionRepository;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import com.eeum.eeum.domain.favorite.repository.FavoriteRepository;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴 계정의 찜 정리를 실제 MySQL에서 검증한다.
 * favorite.account_id는 NOT NULL FK라, 찜이 남은 계정은 물리 삭제 자체가 제약 위반으로 실패한다.
 * Mock 단위 테스트는 이 FK 제약을 재현하지 못한다.
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class AccountCleanupFavoriteIntegrationTest extends IntegrationTestSupport {


    private final AccountCleanupService accountCleanupService;
    private final FavoriteService favoriteService;
    private final FavoriteRepository favoriteRepository;
    private final UsedProductRepository usedProductRepository;
    private final AccountRepository accountRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    private Long withdrawnAccountId;
    private final List<Long> productIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Account viewer = accountRepository.save(Account.createUser(
                "viewer@test.com", "encoded_pw", "찜한사람", "찜한사람닉", "010-1111-1111"));
        Account seller = accountRepository.save(Account.createUser(
                "seller@test.com", "encoded_pw", "판매자", "판매자닉", "010-2222-2222"));
        withdrawnAccountId = viewer.getAccountId();

        Region region = regionRepository.save(
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3));
        Category category = categoryRepository.save(
                Category.createRoot(CategoryType.USED, "디지털기기", 1));

        for (int i = 0; i < 3; i++) {
            UsedProduct product = usedProductRepository.saveAndFlush(UsedProduct.create(
                    seller, category, region,
                    "자전거 팝니다 " + i, "거의 새것입니다",
                    UsedProductPriceType.FIXED, new BigDecimal("10000")));
            productIds.add(product.getUsedProductId());
            favoriteService.toggleFavorite(withdrawnAccountId, toggleRequest(product.getUsedProductId()));
        }
    }

    @AfterEach
    void tearDown() {
        favoriteRepository.deleteAll();
        usedProductRepository.deleteAll();
        categoryRepository.deleteAll();
        regionRepository.deleteAll();
        accountRepository.deleteAll();
        productIds.clear();
    }

    @Test
    void 탈퇴_30일_경과_계정은_찜이_남아_있어도_물리_삭제되고_찜_수도_함께_줄어든다() {
        // given: 이 배선 이전에 탈퇴해 찜이 남아 있는 계정을 재현한다.
        Account withdrawn = accountRepository.findById(withdrawnAccountId).orElseThrow();
        withdrawn.withdraw();
        ReflectionTestUtils.setField(withdrawn, "deletedAt", LocalDateTime.now().minusDays(31));
        accountRepository.saveAndFlush(withdrawn);

        assertThat(favoriteRepository.count()).isEqualTo(3);

        // when: 찜을 정리하지 않으면 여기서 FK 제약 위반으로 스케줄러가 실패한다.
        accountCleanupService.findDeletableAccountIds()
                .forEach(accountCleanupService::deleteAccount);

        // then
        assertThat(accountRepository.findById(withdrawnAccountId)).isEmpty();
        assertThat(favoriteRepository.count()).isZero();
        assertThat(usedProductRepository.findAllById(productIds))
                .allSatisfy(product -> assertThat(product.getFavoriteCount()).isZero());
    }

    private FavoriteToggleRequestDto toggleRequest(Long refId) {
        FavoriteToggleRequestDto request = new FavoriteToggleRequestDto();
        ReflectionTestUtils.setField(request, "refType", FavoriteRefType.USED_PRODUCT);
        ReflectionTestUtils.setField(request, "refId", refId);
        return request;
    }
}
