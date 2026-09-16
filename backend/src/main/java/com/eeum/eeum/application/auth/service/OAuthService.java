package com.eeum.eeum.application.auth.service;

import com.eeum.eeum.application.auth.dto.response.OAuthUserInfo;
import com.eeum.eeum.config.RestClientConfig;
import com.eeum.eeum.domain.account.enums.OAuthProvider;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Map;

//URL 인코딩 문제
//네이버 state=test는 임시값이라 나중에 수정 필요
@Component
@RequiredArgsConstructor
public class OAuthService {

    private final RestClientConfig restClientConfig;

    public OAuthUserInfo getUserInfo(OAuthProvider provider, String accessToken) {
        return switch (provider) {
            case KAKAO -> fetchKakaoProfile(accessToken);
            case NAVER -> fetchNaverProfile(accessToken);
            default -> throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        };
    }

    public void validateToken(OAuthProvider provider, String accessToken, String expectedProviderId) {
        try {
            OAuthUserInfo userInfo = getUserInfo(provider, accessToken);

            if (userInfo.getProviderId() == null || !userInfo.getProviderId().equals(expectedProviderId)) {
                throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException | ClassCastException e) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }
    }

    // ======================== 카카오 ========================

    private OAuthUserInfo fetchKakaoProfile(String accessToken) {
        Map response = restClientConfig.restClient().get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve() //요청 실행 후 응답 받기
                .body(Map.class); //응답 JSON을 Map으로 파싱

        String providerId = String.valueOf(response.get("id"));

        Map<String, Object> kakaoAccount = (Map<String, Object>) response.getOrDefault("kakao_account",Map.of());
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile",Map.of());

        String email = (String) kakaoAccount.getOrDefault("email", null);
        String nickname = (String) profile.getOrDefault("nickname", null);
        String profileImage = (String) profile.getOrDefault("profile_image_url", null)  ;

        return OAuthUserInfo.builder()
                .provider(OAuthProvider.KAKAO)
                .providerId(providerId)
                .email(email)
                .name(null)
                .nickname(nickname)
                .profileImage(profileImage)
                .build();
    }

    // ======================== 네이버 ========================

    private OAuthUserInfo fetchNaverProfile(String accessToken) {
        Map response = restClientConfig.restClient().get()
                .uri("https://openapi.naver.com/v1/nid/me")
                .header("Authorization","Bearer "+accessToken)
                .retrieve()
                .body(Map.class);

        Map<String, Object> naverResponse = (Map<String, Object>) response.getOrDefault("response",Map.of()); //네이버는 response 안에 바로 있음

        String providerId = (String) naverResponse.get("id");
        String email = (String) naverResponse.getOrDefault("email", null);
        String name = (String) naverResponse.getOrDefault("name",null);
        String nickname = (String) naverResponse.getOrDefault("nickname", null);
        String profileImage = (String) naverResponse.getOrDefault("profile_image", null);

        return OAuthUserInfo.builder()
                .provider(OAuthProvider.NAVER)
                .providerId(providerId)
                .email(email)
                .name(name)
                .nickname(nickname)
                .profileImage(profileImage)
                .build();
    }

}
