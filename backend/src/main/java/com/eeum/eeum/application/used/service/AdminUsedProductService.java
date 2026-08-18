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

    @Transactional
    public void show(Long usedProductId) {
        UsedProduct product = usedProductRepository
                .findByUsedProductIdAndDeletedAtIsNull(usedProductId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));

        if (!product.isHidden()) {
            throw new ConflictException(ErrorCode.USED_PRODUCT_NOT_HIDDEN);
        }

        product.show();
        log.info("중고 게시글 숨김 해제: usedProductId={}", usedProductId);
    }
}
