package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.response.UsedProductImageResponseDto;
import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadListRequestDto;
import com.eeum.eeum.common.dto.request.ImageUploadRequestDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.entity.Region;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.enums.UsedProductPriceType;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsedProductImageServiceTest {

    private static final Long SELLER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long PRODUCT_ID = 10L;

    @Mock private UsedProductRepository usedProductRepository;
    @Mock private UsedProductImageRepository usedProductImageRepository;

    @InjectMocks
    private UsedProductImageService usedProductImageService;

    // ─────────────────── 등록 ───────────────────

    @Test
    void 사진이_없던_게시글의_첫_장이_대표가_된다() {
        // given
        givenOwnedProduct();
        when(usedProductImageRepository.countByUsedProduct_UsedProductId(PRODUCT_ID)).thenReturn(0);
        when(usedProductImageRepository.saveAll(any())).thenAnswer(returnsSavedList());

        // when
        List<UsedProductImageResponseDto> result = usedProductImageService.addImages(
                SELLER_ID, PRODUCT_ID, uploadRequest("a.jpg", "b.jpg"));

        // then — 대표 없는 게시글을 만들지 않기 위해 첫 장을 자동 지정한다
        assertThat(result).hasSize(2);
        assertThat(result.get(0).isThumbnail()).isTrue();
        assertThat(result.get(1).isThumbnail()).isFalse();
        assertThat(result).extracting(UsedProductImageResponseDto::getDisplayOrder)
                .containsExactly(1, 2);
    }

    @Test
    void 이미_사진이_있으면_새로_올린_사진은_대표가_되지_않는다() {
        // given
        givenOwnedProduct();
        when(usedProductImageRepository.countByUsedProduct_UsedProductId(PRODUCT_ID)).thenReturn(3);
        when(usedProductImageRepository.saveAll(any())).thenAnswer(returnsSavedList());

        // when
        List<UsedProductImageResponseDto> result = usedProductImageService.addImages(
                SELLER_ID, PRODUCT_ID, uploadRequest("d.jpg"));

        // then — 기존 대표를 밀어내지 않는다
        assertThat(result.get(0).isThumbnail()).isFalse();
        assertThat(result.get(0).getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void 열_장을_넘기면_등록이_거부된다() {
        // given — 이미 9장인데 2장을 더 올리려는 경우
        givenOwnedProduct();
        when(usedProductImageRepository.countByUsedProduct_UsedProductId(PRODUCT_ID)).thenReturn(9);

        // when & then
        assertThatThrownBy(() -> usedProductImageService.addImages(
                SELLER_ID, PRODUCT_ID, uploadRequest("a.jpg", "b.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_LIMIT_EXCEEDED);

        verify(usedProductImageRepository, never()).saveAll(any());
    }

    @Test
    void 남의_게시글에는_사진을_올릴_수_없다() {
        givenOwnedProduct();

        assertThatThrownBy(() -> usedProductImageService.addImages(
                OTHER_ID, PRODUCT_ID, uploadRequest("a.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
    }

    @Test
    void 숨김_게시글은_비소유자에게_사진_변경_요청에도_없는_것으로_응답한다() {
        // given — 게시글 수정·삭제 경로와 같은 정책을 사진 경로에도 적용한다
        UsedProduct hidden = product();
        hidden.hide();
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> usedProductImageService.addImages(
                OTHER_ID, PRODUCT_ID, uploadRequest("a.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    @Test
    void 삭제된_게시글에는_사진을_올릴_수_없다() {
        // given — 조회 자체가 deletedAt IS NULL 조건을 포함한다
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> usedProductImageService.addImages(
                SELLER_ID, PRODUCT_ID, uploadRequest("a.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_NOT_FOUND);
    }

    // ─────────────────── 삭제 ───────────────────

    @Test
    void 대표_사진을_지우면_남은_첫_장이_대표로_올라간다() {
        // given — 대표 없는 게시글이 생기면 목록 화면이 매번 예외 처리를 해야 한다
        UsedProduct product = product();
        UsedProductImage thumbnail = image(product, 100L, "a.jpg", 1, true);
        UsedProductImage second = image(product, 101L, "b.jpg", 2, false);
        UsedProductImage third = image(product, 102L, "c.jpg", 3, false);

        givenOwnedProduct(product);
        when(usedProductImageRepository.findById(100L)).thenReturn(Optional.of(thumbnail));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(new ArrayList<>(List.of(second, third)));

        // when
        usedProductImageService.deleteImage(SELLER_ID, PRODUCT_ID, 100L);

        // then
        verify(usedProductImageRepository).delete(thumbnail);
        assertThat(second.isThumbnail()).isTrue();
        assertThat(third.isThumbnail()).isFalse();
    }

    @Test
    void 사진을_지우면_노출_순서가_1부터_다시_매겨진다() {
        // given — 가운데를 지우면 순서에 구멍이 생긴다
        UsedProduct product = product();
        UsedProductImage first = image(product, 100L, "a.jpg", 1, true);
        UsedProductImage middle = image(product, 101L, "b.jpg", 2, false);
        UsedProductImage last = image(product, 102L, "c.jpg", 3, false);

        givenOwnedProduct(product);
        when(usedProductImageRepository.findById(101L)).thenReturn(Optional.of(middle));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(new ArrayList<>(List.of(first, last)));

        // when
        usedProductImageService.deleteImage(SELLER_ID, PRODUCT_ID, 101L);

        // then
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(last.getDisplayOrder()).isEqualTo(2);
        // 대표는 그대로다 — 대표가 아닌 사진을 지웠다
        assertThat(first.isThumbnail()).isTrue();
    }

    @Test
    void 마지막_한_장을_지워도_승격_대상이_없어_그대로_끝난다() {
        UsedProduct product = product();
        UsedProductImage only = image(product, 100L, "a.jpg", 1, true);

        givenOwnedProduct(product);
        when(usedProductImageRepository.findById(100L)).thenReturn(Optional.of(only));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(new ArrayList<>());

        usedProductImageService.deleteImage(SELLER_ID, PRODUCT_ID, 100L);

        verify(usedProductImageRepository).delete(only);
    }

    @Test
    void 다른_게시글의_이미지_ID로는_삭제할_수_없다() {
        // given — 이미지 ID만 바꿔 남의 사진을 지우는 경로를 막는다
        UsedProduct product = product();
        UsedProduct otherProduct = product();
        ReflectionTestUtils.setField(otherProduct, "usedProductId", 999L);

        givenOwnedProduct(product);
        when(usedProductImageRepository.findById(100L))
                .thenReturn(Optional.of(image(otherProduct, 100L, "a.jpg", 1, true)));

        assertThatThrownBy(() -> usedProductImageService.deleteImage(SELLER_ID, PRODUCT_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_NOT_FOUND);
    }

    // ─────────────────── 대표 변경 ───────────────────

    @Test
    void 대표를_바꾸면_기존_대표가_내려간다() {
        // given — 두 장이 동시에 대표인 상태를 만들지 않는다
        UsedProduct product = product();
        UsedProductImage oldThumbnail = image(product, 100L, "a.jpg", 1, true);
        UsedProductImage target = image(product, 101L, "b.jpg", 2, false);

        givenOwnedProduct(product);
        when(usedProductImageRepository.findById(101L)).thenReturn(Optional.of(target));
        when(usedProductImageRepository.findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(PRODUCT_ID))
                .thenReturn(new ArrayList<>(List.of(oldThumbnail, target)));

        // when
        usedProductImageService.changeThumbnail(SELLER_ID, PRODUCT_ID, 101L);

        // then
        assertThat(oldThumbnail.isThumbnail()).isFalse();
        assertThat(target.isThumbnail()).isTrue();
    }

    @Test
    void 남의_게시글의_대표는_바꿀_수_없다() {
        givenOwnedProduct();

        assertThatThrownBy(() -> usedProductImageService.changeThumbnail(OTHER_ID, PRODUCT_ID, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
    }

    // ─────────────────── 헬퍼 ───────────────────

    @Test
    void 사진_쓰기는_부모_게시글을_비관적_잠금으로_읽는다() {
        // given — 잠그지 않으면 동시 요청이 각자 현재 사진 수·대표를 읽어
        // 10장 초과, 순서 중복, 대표 복수가 생긴다
        givenOwnedProduct();
        when(usedProductImageRepository.countByUsedProduct_UsedProductId(PRODUCT_ID)).thenReturn(0);
        when(usedProductImageRepository.saveAll(any())).thenAnswer(returnsSavedList());

        // when
        usedProductImageService.addImages(SELLER_ID, PRODUCT_ID, uploadRequest("a.jpg"));

        // then
        verify(usedProductRepository, never()).findByUsedProductIdAndDeletedAtIsNull(any());
    }

    private void givenOwnedProduct() {
        givenOwnedProduct(product());
    }

    private void givenOwnedProduct(UsedProduct product) {
        when(usedProductRepository.findByUsedProductIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));
    }

    @SuppressWarnings("unchecked")
    private org.mockito.stubbing.Answer<List<UsedProductImage>> returnsSavedList() {
        return invocation -> new ArrayList<>((List<UsedProductImage>) invocation.getArgument(0));
    }

    private UsedProductImageUploadListRequestDto uploadRequest(String... urls) {
        UsedProductImageUploadListRequestDto request = new UsedProductImageUploadListRequestDto();
        List<ImageUploadRequestDto> images = Arrays.stream(urls).map(url -> {
            ImageUploadRequestDto image = new ImageUploadRequestDto();
            ReflectionTestUtils.setField(image, "imageUrl", url);
            return image;
        }).toList();
        ReflectionTestUtils.setField(request, "images", images);
        return request;
    }

    private UsedProductImage image(
            UsedProduct product, Long imageId, String url, int order, boolean thumbnail) {
        UsedProductImage image = UsedProductImage.create(product, url, order, thumbnail);
        ReflectionTestUtils.setField(image, "imageId", imageId);
        return image;
    }

    private UsedProduct product() {
        Account seller = Account.createUser(
                "seller@test.com", "encoded-pw", "판매자", "판매자닉", "010-0000-0000");
        ReflectionTestUtils.setField(seller, "accountId", SELLER_ID);

        UsedProduct product = UsedProduct.create(
                seller, Category.createRoot(CategoryType.USED, "디지털기기", 1),
                Region.create("1168010100", "서울특별시", "강남구", "역삼동", 3),
                "자전거 팝니다", "거의 새것입니다",
                UsedProductPriceType.FIXED, new BigDecimal("10000"));
        ReflectionTestUtils.setField(product, "usedProductId", PRODUCT_ID);
        return product;
    }
}
