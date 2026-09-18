package com.eeum.eeum.common.web;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.config.JacksonConfig;
import com.eeum.eeum.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageUrlResponseAdviceTestController.class)
@AutoConfigureMockMvc(addFilters = false)
// 운영에는 Jackson 2 ObjectMapper Bean(JacksonConfig)이 함께 있다. 슬라이스 테스트는
// @Configuration을 읽지 않으므로 직접 넣어야 두 매퍼가 공존하는 운영 조건과 같아진다.
@Import({ImageUrlResponseAdvice.class, JacksonConfig.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ImageUrlResponseAdviceHttpTest {

    private final MockMvc mockMvc;
    @MockitoBean private FileStorageService fileStorageService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void 실제_HTTP_JSON_응답에서_objectKey가_presigned_URL로_변환된다() throws Exception {
        // Given
        when(fileStorageService.isResolvableImageRef("used/7/thumb.webp")).thenReturn(true);
        when(fileStorageService.resolveImageUrl("used/7/thumb.webp"))
                .thenReturn("https://signed.example/thumb");

        // When & Then: Spring MVC가 실제 선택한 converter와 운영 JsonMapper Bean을 통과한다.
        mockMvc.perform(get("/test/image-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://signed.example/thumb"));
    }

    @Test
    void advice를_거친_응답의_timestamp는_ISO_문자열로_직렬화된다() throws Exception {
        // Given: advice가 body를 JsonNode로 다시 만들기 때문에, 그 매퍼의 날짜 설정이 응답 형식이 된다.
        //        숫자 배열([2026,9,15,...])로 바뀌면 클라이언트의 날짜 파싱이 깨진다.
        when(fileStorageService.isResolvableImageRef("used/7/thumb.webp")).thenReturn(true);
        when(fileStorageService.resolveImageUrl("used/7/thumb.webp"))
                .thenReturn("https://signed.example/thumb");

        // When & Then
        mockMvc.perform(get("/test/image-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.timestamp")
                        .value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")));
    }
}
