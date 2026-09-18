package com.eeum.eeum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    // 실시간 중계가 이 매퍼로 채팅 DTO를 직렬화한다. 날짜 모듈이 없으면 sentAt에서 매번 실패하고,
    // 타임스탬프 배열로 쓰면 REST(ISO 문자열)와 형식이 달라져 클라이언트가 날짜를 못 읽는다.
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
