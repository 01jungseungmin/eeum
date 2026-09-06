package com.eeum.eeum.common.web;

import com.eeum.eeum.application.file.FileStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Iterator;
import java.util.Map;

// DB에는 objectKey를 보관하고, JSON 응답을 만들기 직전에만 private S3 조회 URL로 바꾼다.
// Service 트랜잭션 안에서 AWS 자격증명 갱신 I/O가 일어나는 것을 피하면서 모든 이미지 응답에 같은 규칙을 적용한다.
@RestControllerAdvice
@RequiredArgsConstructor
public class ImageUrlResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body == null || !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }

        JsonNode root = objectMapper.valueToTree(body);
        resolveImageUrls(root);
        return root;
    }

    private void resolveImageUrls(JsonNode node) {
        if (node.isArray()) {
            node.forEach(this::resolveImageUrls);
            return;
        }
        if (!node.isObject()) {
            return;
        }

        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (isImageUrlField(field.getKey()) && value.isTextual()
                    && fileStorageService.isFinalObjectKey(value.asText())) {
                objectNode.put(field.getKey(), fileStorageService.resolveImageUrl(value.asText()));
                continue;
            }
            resolveImageUrls(value);
        }
    }

    private boolean isImageUrlField(String fieldName) {
        String normalized = fieldName.toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("imageurl")
                || normalized.endsWith("profileurl")
                || normalized.endsWith("thumbnailurl");
    }
}
