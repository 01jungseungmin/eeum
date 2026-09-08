package com.eeum.eeum.application.used.service;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.eeum.eeum.application.used.dto.response.UsedProductDetailResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductSummaryResponseDto;
import com.eeum.eeum.application.used.dto.response.UsedProductImageResponseDto;
import com.eeum.eeum.application.used.service.UsedProductImageService;
import java.util.List;

/**
 * 관리자 중고 게시글 조치.
 *
 * 숨김 적용은 신고 처리 흐름({@code UsedProductReportActionExecutor})에서 이뤄진다.
 * 여기에는 오판을 되돌리는 해제만 둔다 — 해제 수단이 없으면 DB를 직접 고쳐야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUsedProductService {

    private final UsedProductRepository usedProductRepository;
    private final UsedProductImageService usedProductImageService;

    @Transactional(readOnly = true)
    public Page<UsedProductSummaryResponseDto> getProducts(Pageable pageable) {
        return usedProductRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(product -> UsedProductSummaryResponseDto.of(product, null));
    }

    @Transactional(readOnly = true)
    public UsedProductDetailResponseDto getDetail(Long usedProductId) {
        UsedProduct product = usedProductRepository.findById(usedProductId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));
        List<UsedProductImageResponseDto> images = usedProductImageService.getImages(usedProductId);
        return UsedProductDetailResponseDto.from(product, images, product.getSeller().getAccountId());
    }

    @Transactional
    public void hide(Long usedProductId) {
        UsedProduct product = usedProductRepository.findByUsedProductIdForUpdate(usedProductId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));
        if (product.isHidden()) {
            throw new ConflictException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
        product.hide();
    }

    @Transactional
    public void show(Long usedProductId) {
        // 신고 조치(UsedProductReportActionExecutor)와 같은 비관적 잠금을 쓴다.
        // 숨김 해제와 신고 숨김은 같은 컬럼을 다투므로, 잠금 없이 읽으면
        // "숨김 상태인지" 확인과 해제 사이에 조치가 끼어들어 방금 걸린 숨김이 풀린다.
        UsedProduct product = usedProductRepository
                .findByUsedProductIdForUpdate(usedProductId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));

        if (!product.isHidden()) {
            throw new ConflictException(ErrorCode.USED_PRODUCT_NOT_HIDDEN);
        }

        product.show();
        log.info("중고 게시글 숨김 해제: usedProductId={}", usedProductId);
    }
}
