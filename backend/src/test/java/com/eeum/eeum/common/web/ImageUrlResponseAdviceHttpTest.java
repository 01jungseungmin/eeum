package com.eeum.eeum.common.web;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageUrlResponseAdviceTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ImageUrlResponseAdvice.class)
class ImageUrlResponseAdviceHttpTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FileStorageService fileStorageService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void 실제_HTTP_JSON_응답에서_objectKey가_presigned_URL로_변환된다() throws Exception {
        // Given
        when(fileStorageService.isFinalObjectKey("used/7/thumb.webp")).thenReturn(true);
        when(fileStorageService.resolveImageUrl("used/7/thumb.webp"))
                .thenReturn("https://signed.example/thumb");

        // When & Then: Spring MVC가 실제 선택한 converter와 운영 JsonMapper Bean을 통과한다.
        mockMvc.perform(get("/test/image-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://signed.example/thumb"));
    }
}
