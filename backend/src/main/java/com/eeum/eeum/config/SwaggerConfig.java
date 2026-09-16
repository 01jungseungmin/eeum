package com.eeum.eeum.config;

import io.swagger.v3.oas.models.Components; //보안 스키마 같은 공통 구성 요소
import io.swagger.v3.oas.models.OpenAPI; //전체 API 문서 객체
import io.swagger.v3.oas.models.info.Contact; //개발팀 연락처 정보
import io.swagger.v3.oas.models.info.Info; //API 제목, 설명, 버전 정보
import io.swagger.v3.oas.models.security.SecurityRequirement; //이 API 문서에서 어떤 인증 방식을 사용할지 지정
import io.swagger.v3.oas.models.security.SecurityScheme; //JWT 인증 방식 정의
import io.swagger.v3.oas.models.servers.Server; //API 서버 주소 정보
import org.springframework.context.annotation.Bean; //메서드가 반환하는 객체를 Spring Bean으로 등록
import org.springframework.context.annotation.Configuration; //설정 클래스

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "bearerAuth";

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList(jwtSchemeName);

        SecurityScheme securityScheme = new SecurityScheme()
                .name(jwtSchemeName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .description("Access Token을 입력하세요. Bearer는 자동으로 붙습니다.");

        return new OpenAPI()
                .info(new Info()
                        .title("이음(Eeum) API 명세서")
                        .description("""
                                ## 이음(Eeum) API
                                
                                ### 인증 방법
                                1. `/auth/login` 또는 `/auth/signup` 후 로그인
                                2. 응답으로 받은 Access Token 복사
                                3. 우측 상단 Authorize 버튼 클릭
                                4. `Bearer` 없이 Access Token 값만 입력
                                5. 인증이 필요한 API 테스트
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("이음 개발팀")
                                .email("rr22016@naver.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버"),
                        new Server().url("https://eeum.life/api").description("운영 서버")
                ))
                .addSecurityItem(securityRequirement)
                .components(new Components()
                        .addSecuritySchemes(jwtSchemeName, securityScheme));
    }
}