package com.eeum.eeum.application.category.service;

import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import com.eeum.eeum.support.IntegrationTestSupport;
import com.eeum.eeum.application.category.dto.request.CategoryCreateRequestDto;
import com.eeum.eeum.application.category.dto.request.CategoryUpdateRequestDto;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 카테고리 동시성 — 실제 MySQL/Redis Testcontainer 환경에서 실행.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>동일 (type, 부모, 이름) 동시 생성 → DB 유니크 제약으로 한 건만 성공</li>
 *   <li>루트 범위(parentId = null)도 동일하게 보장 — parent_scope 컬럼이 NULL 우회를 막는다</li>
 *   <li>이름 수정과 비활성화 경쟁 → @Version이 lost update를 차단</li>
 *   <li>부모 삭제와 하위 생성 경쟁 → 부모 행 잠금으로 고아 카테고리가 남지 않는다</li>
 * </ul>
 */
@EnabledIfDockerAvailable
@RequiredArgsConstructor
class CategoryConcurrencyIntegrationTest extends IntegrationTestSupport {



    private final AdminCategoryService adminCategoryService;
    private final CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        categoryRepository.deleteAll();
    }

    @Test
    void 같은_부모_아래_같은_이름을_동시_생성하면_한_건만_저장된다() throws InterruptedException {
        Category parent = categoryRepository.saveAndFlush(
                Category.createRoot(CategoryType.STORE, "음식점", 0));

        RaceOutcome outcome = race(
                () -> adminCategoryService.createCategory(
                        createRequest(CategoryType.STORE, parent.getCategoryId(), "한식")),
                () -> adminCategoryService.createCategory(
                        createRequest(CategoryType.STORE, parent.getCategoryId(), "한식"))
        );

        assertThat(outcome.successCount()).isEqualTo(1);
        assertDuplicateConflict(outcome.failure());
        assertThat(countByName(CategoryType.STORE, parent.getCategoryId(), "한식")).isEqualTo(1);
    }

    @Test
    void 루트_카테고리도_같은_이름을_동시_생성하면_한_건만_저장된다() throws InterruptedException {
        // parent_id가 NULL인 범위 — MySQL UNIQUE는 NULL을 서로 다른 값으로 취급하므로
        // parent_scope 컬럼이 없으면 이 케이스가 그대로 뚫린다.
        RaceOutcome outcome = race(
                () -> adminCategoryService.createCategory(
                        createRequest(CategoryType.COMMUNITY, null, "자유게시판")),
                () -> adminCategoryService.createCategory(
                        createRequest(CategoryType.COMMUNITY, null, "자유게시판"))
        );

        assertThat(outcome.successCount()).isEqualTo(1);
        assertDuplicateConflict(outcome.failure());
        assertThat(countByName(CategoryType.COMMUNITY, null, "자유게시판")).isEqualTo(1);
    }

    @Test
    void 서로_다른_카테고리를_같은_이름으로_동시_변경하면_한_건만_성공한다() throws InterruptedException {
        Category first = categoryRepository.saveAndFlush(
                Category.createRoot(CategoryType.STORE, "카페", 0));
        Category second = categoryRepository.saveAndFlush(
                Category.createRoot(CategoryType.STORE, "베이커리", 1));

        RaceOutcome outcome = race(
                () -> adminCategoryService.updateCategory(first.getCategoryId(), updateRequest("디저트")),
                () -> adminCategoryService.updateCategory(second.getCategoryId(), updateRequest("디저트"))
        );

        assertThat(outcome.successCount()).isEqualTo(1);
        assertThat(countByName(CategoryType.STORE, null, "디저트")).isEqualTo(1);
    }

    @Test
    void 이름_수정과_비활성화가_경쟁해도_비활성화가_사라지지_않는다() throws InterruptedException {
        Category category = categoryRepository.saveAndFlush(
                Category.createRoot(CategoryType.STORE, "주점", 0));

        RaceOutcome outcome = race(
                () -> adminCategoryService.updateCategory(category.getCategoryId(), updateRequest("펍")),
                () -> adminCategoryService.deactivateCategory(category.getCategoryId())
        );

        // 두 요청이 모두 성공했다면 둘 다 반영돼야 하고, 하나가 실패했다면 나머지가 온전해야 한다.
        Category stored = categoryRepository.findById(category.getCategoryId()).orElseThrow();
        if (outcome.successCount() == 2) {
            assertThat(stored.isActive())
                    .as("이름 수정이 비활성화를 덮어써서는 안 된다")
                    .isFalse();
            assertThat(stored.getName()).isEqualTo("펍");
        } else {
            assertThat(outcome.successCount()).isEqualTo(1);
        }
    }

    @Test
    void 부모_삭제와_하위_생성이_경쟁해도_고아_카테고리가_남지_않는다() throws InterruptedException {
        Category parent = categoryRepository.saveAndFlush(
                Category.createRoot(CategoryType.STORE, "숙박", 0));
        Long parentId = parent.getCategoryId();

        race(
                () -> adminCategoryService.hardDeleteCategory(parentId),
                () -> adminCategoryService.createCategory(
                        createRequest(CategoryType.STORE, parentId, "모텔"))
        );

        boolean parentExists = categoryRepository.findById(parentId).isPresent();
        List<Category> children = categoryRepository.findAll().stream()
                .filter(c -> parentId.equals(c.getParentId()))
                .toList();

        if (!parentExists) {
            assertThat(children)
                    .as("삭제된 부모를 가리키는 고아 카테고리가 남으면 안 된다")
                    .isEmpty();
        } else {
            assertThat(children).hasSize(1);
        }
    }

    // ===================== 헬퍼 =====================

    private CategoryCreateRequestDto createRequest(CategoryType type, Long parentId, String name) {
        CategoryCreateRequestDto request = new CategoryCreateRequestDto();
        ReflectionTestUtils.setField(request, "type", type);
        ReflectionTestUtils.setField(request, "parentId", parentId);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "displayOrder", 0);
        return request;
    }

    private CategoryUpdateRequestDto updateRequest(String name) {
        CategoryUpdateRequestDto request = new CategoryUpdateRequestDto();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "displayOrder", 0);
        return request;
    }

    private long countByName(CategoryType type, Long parentId, String name) {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getType() == type
                        && java.util.Objects.equals(c.getParentId(), parentId)
                        && name.equals(c.getName()))
                .count();
    }

    private void assertDuplicateConflict(Throwable failure) {
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failure).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_DUPLICATE);
    }

    private RaceOutcome race(ThrowingRunnable first, ThrowingRunnable second) throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        AtomicReference<Throwable> secondError = new AtomicReference<>();

        Thread firstThread = new Thread(() -> run(startLatch, doneLatch, first, firstError));
        Thread secondThread = new Thread(() -> run(startLatch, doneLatch, second, secondError));
        firstThread.start();
        secondThread.start();
        startLatch.countDown();

        assertThat(doneLatch.await(20, TimeUnit.SECONDS))
                .as("두 동시 요청은 20초 안에 완료되어야 한다")
                .isTrue();
        return new RaceOutcome(firstError.get(), secondError.get());
    }

    private void run(
            CountDownLatch startLatch,
            CountDownLatch doneLatch,
            ThrowingRunnable action,
            AtomicReference<Throwable> error
    ) {
        try {
            startLatch.await();
            action.run();
        } catch (Throwable throwable) {
            error.set(throwable);
        } finally {
            doneLatch.countDown();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record RaceOutcome(Throwable firstError, Throwable secondError) {
        int successCount() {
            int count = 0;
            if (firstError == null) count++;
            if (secondError == null) count++;
            return count;
        }

        Throwable failure() {
            return firstError != null ? firstError : secondError;
        }
    }
}
