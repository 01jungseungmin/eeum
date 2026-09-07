package com.eeum.eeum.common.dto.request;

import com.eeum.eeum.application.account.dto.request.UpdateInfoRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedProductImageUploadRequestDto;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증상: 기존 501~1000자 HTTPS 이미지 URL이 400으로 거절된다.
 * 결함 위치: ImageUploadRequestDto, UsedProductImageUploadRequestDto의 Size 제약.
 * 기존 URL 계약의 최대 1000자는 유지하고 초과 입력만 거절한다.
 */
class ImageUploadRequestValidationTest {
    @Test
    void 기존_HTTPS_URL의_1000자_계약을_유지한다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Object dto : new Object[]{new ImageUploadRequestDto(), new UsedProductImageUploadRequestDto()}) {
                // Given
                String url = "https://example.com/" + "a".repeat(980);
                ReflectionTestUtils.setField(dto, "imageUrl", url);
                // When / Then
                assertThat(factory.getValidator().validate(dto)).isEmpty();
                ReflectionTestUtils.setField(dto, "imageUrl", url + "aa");
                assertThat(factory.getValidator().validate(dto)).isNotEmpty();
            }
        }
    }

    /**
     * 증상: 501자 이상 프로필 URL이 400이 아니라 flush 실패로 500이 된다.
     * 결함 위치: UpdateInfoRequestDto.profileImageUrl에 Size 제약이 없다.
     * account.profile_image_url이 VARCHAR(500)이므로 컬럼 길이와 같은 상한을 고정한다.
     */
    @Test
    void 프로필_이미지_URL은_컬럼_길이인_500자를_넘기지_못한다() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            // Given
            UpdateInfoRequestDto dto = new UpdateInfoRequestDto();
            String url = "https://example.com/" + "a".repeat(480);
            ReflectionTestUtils.setField(dto, "profileImageUrl", url);
            // When / Then
            assertThat(url).hasSize(500);
            assertThat(factory.getValidator().validate(dto)).isEmpty();
            ReflectionTestUtils.setField(dto, "profileImageUrl", url + "a");
            assertThat(factory.getValidator().validate(dto)).isNotEmpty();
        }
    }
}
