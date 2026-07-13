package com.eeum.eeum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // 외부 API(FCM 등) 호출이 응답 없이 hang되면 async 스레드가 무한 대기로 잠식되므로 타임아웃을 강제한다.
    private ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(timeoutRequestFactory());
    }
}
