package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.application.auth.dto.request.BusinessVerifyRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BusinessVerificationService {

    private final RestClient restClient;

    @Value("${business.nts.service-key}")
    private String serviceKey;

    // 컨트롤러에서 /business/verify API 호출할 때 사용하는 메서드
    public boolean verifyBusiness(BusinessVerifyRequestDto request) {
        return verifyBusiness(
                request.getBusinessNumber(),
                request.getOwnerName(),
                request.getOpeningDate()
        );
    }

    // AuthService의 ownerSignup() 내부에서 직접 사용할 수 있는 메서드
    public boolean verifyBusiness(
            String businessNumber,
            String ownerName,
            String openingDate
    ) {
        String normalizedBusinessNumber = businessNumber.replace("-", "");
        String normalizedOpeningDate = openingDate.replace("-", "");

        Map<String, Object> body = Map.of(
                "businesses", List.of(
                        Map.of(
                                "b_no", normalizedBusinessNumber,
                                "start_dt", normalizedOpeningDate,
                                "p_nm", ownerName
                        )
                )
        );

        String encodedServiceKey = URLEncoder.encode(
                serviceKey.trim(),
                StandardCharsets.UTF_8
        );

        String url = "https://api.odcloud.kr/api/nts-businessman/v1/validate"
                + "?serviceKey=" + encodedServiceKey;


        Map response = restClient.post()
                .uri(URI.create(url))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return false;
        }

        List<Map<String, Object>> data =
                (List<Map<String, Object>>) response.get("data");

        if (data == null || data.isEmpty()) {
            return false;
        }

        Map<String, Object> first = data.get(0);

        String valid = String.valueOf(first.get("valid"));

        return "01".equals(valid);
    }
}