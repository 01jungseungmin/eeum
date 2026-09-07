package com.eeum.eeum.common.web;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.common.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUrlResponseAdviceTest {

    @Mock private FileStorageService fileStorageService;

    private ImageUrlResponseAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new ImageUrlResponseAdvice(new ObjectMapper(), fileStorageService);
    }

    @Test
    void HTTP_응답의_final_objectKey를_presigned_URL로_변환한다() {
        // Given
        String objectKey = "used/7/thumb.webp";
        String presignedUrl = "https://signed.example/thumb";
        when(fileStorageService.isFinalObjectKey(objectKey)).thenReturn(true);
        when(fileStorageService.resolveImageUrl(objectKey)).thenReturn(presignedUrl);
        ApiResponse<Map<String, String>> body = ApiResponse.<Map<String, String>>builder()
                .success(true)
                .data(Map.of("thumbnailUrl", objectKey, "legacyImageUrl", "https://legacy.example/thumb"))
                .build();

        // When
        Object result = advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                mock(ServerHttpRequest.class),
                mock(ServerHttpResponse.class));

        // Then
        JsonNode json = (JsonNode) result;
        assertThat(json.at("/data/thumbnailUrl").asText()).isEqualTo(presignedUrl);
        assertThat(json.at("/data/legacyImageUrl").asText()).isEqualTo("https://legacy.example/thumb");
        verify(fileStorageService).resolveImageUrl(objectKey);
    }
}
