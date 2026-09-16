package com.eeum.eeum.common.web;

import com.eeum.eeum.common.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ImageUrlResponseAdviceTestController {

    @GetMapping("/test/image-url")
    public ApiResponse<Map<String, String>> imageUrl() {
        return ApiResponse.success(Map.of("thumbnailUrl", "used/7/thumb.webp"));
    }
}
