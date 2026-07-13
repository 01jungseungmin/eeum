package com.eeum.eeum.application.ai.config;

import com.eeum.eeum.application.ai.generator.AiInsightGenerator;
import com.eeum.eeum.application.ai.generator.AiTextGenerator;
import com.eeum.eeum.application.ai.generator.GeminiAiInsightGenerator;
import com.eeum.eeum.application.ai.generator.GeminiAiTextGenerator;
import com.eeum.eeum.application.ai.generator.TemplateAiInsightGenerator;
import com.eeum.eeum.application.ai.generator.TemplateAiTextGenerator;
import com.eeum.eeum.application.ai.router.AiModelRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ai.provider 설정에 따라 Generator 구현체를 선택한다.
 * - template          → Template 구현체 (로컬/CI, API Key 불필요)
 * - gemini/groq/mock  → LLM 구현체 (실패 시 Template 최종 fallback 내장)
 * Service 계층은 계속 인터페이스에만 의존하므로 코드 변경이 없다.
 */
@Slf4j
@Configuration
public class AiGeneratorConfig {

    @Bean
    @Primary
    public AiTextGenerator primaryAiTextGenerator(
            AiProviderProperties properties,
            AiModelRouter router,
            TemplateAiTextGenerator template,
            ObjectMapper objectMapper
    ) {
        if (isTemplate(properties)) {
            log.info("[AI-CONFIG] AiTextGenerator = Template (provider={})", properties.provider());
            return template;
        }
        log.info("[AI-CONFIG] AiTextGenerator = LLM 기반 (provider={}, fallback={})",
                properties.provider(), properties.fallbackProvider());
        return new GeminiAiTextGenerator(router, template, objectMapper);
    }

    @Bean
    @Primary
    public AiInsightGenerator primaryAiInsightGenerator(
            AiProviderProperties properties,
            AiModelRouter router,
            TemplateAiInsightGenerator template
    ) {
        if (isTemplate(properties)) {
            return template;
        }
        return new GeminiAiInsightGenerator(router, template);
    }

    private boolean isTemplate(AiProviderProperties properties) {
        return "template".equalsIgnoreCase(properties.provider());
    }
}
