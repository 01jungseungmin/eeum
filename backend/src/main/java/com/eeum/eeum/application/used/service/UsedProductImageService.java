package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.used.dto.response.UsedProductImageResponseDto;
import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadListRequestDto;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedProductImage;
import com.eeum.eeum.domain.used.repository.UsedProductImageRepository;
import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.ForbiddenException;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 중고 게시글 사진 관리
@Slf4j
@Service
@RequiredArgsConstructor
public class UsedProductImageService {

    /** 게시글당 사진 상한. {@code IMAGE_LIMIT_EXCEEDED} 메시지와 맞춘 값이다. */
    private static final int MAX_IMAGE_COUNT = 10;

    private final AccountWriteGuard accountWriteGuard;
    private final UsedProductRepository usedProductRepository;
    private final UsedProductImageRepository usedProductImageRepository;

    @Transactional
    public List<UsedProductImageResponseDto> addImages(
            Long sellerId,
            Long usedProductId,
            UsedProductImageUploadListRequestDto request
    ) {
        UsedProduct product = getOwnedOrThrow(sellerId, usedProductId);

        int currentCount = usedProductImageRepository.countByUsedProduct_UsedProductId(usedProductId);
        if (currentCount + request.getImages().size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        List<UsedProductImage> images = new ArrayList<>();
        for (int i = 0; i < request.getImages().size(); i++) {
            // 사진이 하나도 없던 게시글의 첫 장이 대표가 된다.
            boolean thumbnail = (currentCount == 0 && i == 0);
            images.add(UsedProductImage.create(
                    product,
                    request.getImages().get(i).getImageUrl(),
                    currentCount + i + 1,
                    thumbnail
            ));
        }

        List<UsedProductImage> saved = usedProductImageRepository.saveAll(images);
        log.info("중고 게시글 사진 등록: usedProductId={}, count={}", usedProductId, saved.size());
        return saved.stream().map(UsedProductImageResponseDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UsedProductImageResponseDto> getImages(Long usedProductId) {
        return usedProductImageRepository
                .findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(usedProductId).stream()
                .map(UsedProductImageResponseDto::from)
                .toList();
    }

    @Transactional
    public void deleteImage(Long sellerId, Long usedProductId, Long imageId) {
        getOwnedOrThrow(sellerId, usedProductId);

        UsedProductImage image = getImageOfProductOrThrow(usedProductId, imageId);
        boolean wasThumbnail = image.isThumbnail();

        usedProductImageRepository.delete(image);
        // 아래에서 남은 목록을 다시 읽으므로, 삭제를 DB에 먼저 반영해야 지운 행이 딸려오지 않는다.
        usedProductImageRepository.flush();

        List<UsedProductImage> remaining = usedProductImageRepository
                .findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(usedProductId);

        // 중간 사진을 지우면 순서에 구멍이 생긴다. 1부터 다시 매긴다.
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).changeDisplayOrder(i + 1);
        }

        // 대표를 지웠으면 남은 첫 장을 승격시킨다 — 대표 없는 게시글을 만들지 않는다.
        if (wasThumbnail && !remaining.isEmpty()) {
            remaining.get(0).markAsThumbnail();
        }

        log.info("중고 게시글 사진 삭제: usedProductId={}, imageId={}, 대표승격={}",
                usedProductId, imageId, wasThumbnail && !remaining.isEmpty());
    }

    @Transactional
    public void changeThumbnail(Long sellerId, Long usedProductId, Long imageId) {
        getOwnedOrThrow(sellerId, usedProductId);

        UsedProductImage target = getImageOfProductOrThrow(usedProductId, imageId);

        // 기존 대표를 먼저 내린다. 두 장이 동시에 대표인 상태를 만들지 않는다.
        usedProductImageRepository
                .findByUsedProduct_UsedProductIdOrderByDisplayOrderAsc(usedProductId)
                .forEach(UsedProductImage::unmarkAsThumbnail);

        target.markAsThumbnail();
        log.info("중고 게시글 대표 사진 변경: usedProductId={}, imageId={}", usedProductId, imageId);
    }

    // ===================== 내부 헬퍼 =====================

    // 사진 추가·삭제·대표 변경은 모두 이 메서드를 거치는 쓰기 경로다.
    // 부모 게시글을 먼저 잠그지 않으면 동시 요청이 각자 "현재 사진 수"·"현재 대표"를 읽고 진행해
    // 10장 초과, 순서 중복, 대표 복수·부재가 생긴다.
    // 잠금 순서는 게시글 삭제·찜과 같은 used_product → 하위 테이블이다.
    // 잠금 조회에는 삭제 필터가 없으므로 여기서 거른다.
    private UsedProduct getOwnedOrThrow(Long sellerId, Long usedProductId) {
        // 잠금 순서 account → used_product → image의 첫 단계.
        // 탈퇴 처리가 지나간 뒤 살아 있는 토큰으로 사진이 추가되는 것을 막는다.
        accountWriteGuard.lockActive(sellerId);

        UsedProduct product = usedProductRepository
                .findByUsedProductIdForUpdate(usedProductId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));

        // 숨김 글은 작성자에게만 보인다. 비소유자에게 403을 주면 상세 조회는 404인데 수정·삭제만
        // 403이 되어, 그 차이로 숨김 글의 존재가 드러난다. 상세 조회와 같은 응답으로 맞춘다.
        // 공개 글의 403은 유지한다 — 존재가 이미 공개라 404로 바꾸면 정상적인 권한 오류를 가린다.
        if (product.isHidden() && !product.isOwnedBy(sellerId)) {
            throw new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND);
        }

        if (!product.isOwnedBy(sellerId)) {
            throw new ForbiddenException(ErrorCode.USED_PRODUCT_ACCESS_DENIED);
        }
        return product;
    }

    // 다른 게시글의 이미지 ID를 넘겨 남의 사진을 지우는 경로를 막는다.
    private UsedProductImage getImageOfProductOrThrow(Long usedProductId, Long imageId) {
        UsedProductImage image = usedProductImageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.IMAGE_NOT_FOUND));

        if (!image.belongsTo(usedProductId)) {
            throw new NotFoundException(ErrorCode.IMAGE_NOT_FOUND);
        }
        return image;
    }
}
