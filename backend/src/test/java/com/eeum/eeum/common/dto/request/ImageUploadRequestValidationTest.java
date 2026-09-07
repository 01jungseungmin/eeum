package com.eeum.eeum.common.dto.request;

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
}
