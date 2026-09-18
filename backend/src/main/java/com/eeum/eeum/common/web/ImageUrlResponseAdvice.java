package com.eeum.eeum.common.web;

import com.eeum.eeum.application.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Iterator;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * DB 트랜잭션이 끝난 HTTP 응답 직전에만 private S3 objectKey를 조회 URL로 바꾼다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ImageUrlResponseAdvice implements ResponseBodyAdvice<Object> {

    private final JsonMapper jsonMapper;
    private final FileStorageService fileStorageService;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType);
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
        JsonNode root = jsonMapper.valueToTree(body);
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
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.properties().iterator();
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
