package com.eeum.eeum.application.used.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 등록 요청의 HTTP 검증 계약 테스트.
 * 서비스 단위 테스트는 이미 만들어진 DTO를 받으므로 null 요소·장수 제한 같은
 * Bean Validation 계약을 검증하지 못한다.
 */
class UsedProductImageUploadListRequestDtoTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();

    @Test
    void 이미지_목록에_null_요소가_있으면_거절한다() {
        // given — 요소 검증이 없으면 {"images":[null]}이 통과해 서비스에서 NPE 500이 된다
        UsedProductImageUploadListRequestDto request = request(image("a.jpg"), null);

        // when
        Set<ConstraintViolation<UsedProductImageUploadListRequestDto>> violations =
                validator.validate(request);

        // then
        assertThat(violations).isNotEmpty();
    }

    @Test
    void 사진은_한_번에_10장까지만_허용한다() {
        // given — 공용 DTO는 20장까지 허용하지만 중고 게시글 상한은 10장이다
        UsedProductImageUploadRequestDto[] images = new UsedProductImageUploadRequestDto[11];
        Arrays.setAll(images, i -> image("img" + i + ".jpg"));

        // when
        Set<ConstraintViolation<UsedProductImageUploadListRequestDto>> violations =
                validator.validate(request(images));

        // then
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .anyMatch(message -> message.contains("최대 10장"));
    }

    @Test
    void 빈_목록은_거절한다() {
        assertThat(validator.validate(request())).isNotEmpty();
    }

    @Test
    void 이미지_URL이_비어_있으면_거절한다() {
        assertThat(validator.validate(request(image(" ")))).isNotEmpty();
    }

    @Test
    void 정상_요청은_통과한다() {
        assertThat(validator.validate(request(image("a.jpg"), image("b.jpg")))).isEmpty();
    }

    private UsedProductImageUploadListRequestDto request(UsedProductImageUploadRequestDto... images) {
        UsedProductImageUploadListRequestDto request = new UsedProductImageUploadListRequestDto();
        List<UsedProductImageUploadRequestDto> list = new ArrayList<>(Arrays.asList(images));
        ReflectionTestUtils.setField(request, "images", list);
        return request;
    }

    private UsedProductImageUploadRequestDto image(String url) {
        UsedProductImageUploadRequestDto image = new UsedProductImageUploadRequestDto();
        ReflectionTestUtils.setField(image, "imageUrl", url);
        return image;
    }
}
