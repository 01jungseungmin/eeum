package com.eeum.eeum.application.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Provider 설정 바인딩.
 * 설정이 없어도 서버가 죽지 않도록 컴팩트 생성자에서 기본값을 채운다.
 * API Key는 실제 호출 시점에만 검증한다 — ai.provider=template이면 Key 없이 기동 가능.
 */
@ConfigurationProperties(prefix = "ai")
public record AiProviderProperties(
        String provider,
        String fallbackProvider,
        String finalFallback,
        Gemini gemini,
        Groq groq
) {
    public AiProviderProperties {
        provider = defaultIfBlank(provider, "template");
        fallbackProvider = defaultIfBlank(fallbackProvider, "groq");
        finalFallback = defaultIfBlank(finalFallback, "template");
        gemini = gemini != null ? gemini : new Gemini(null, null, null);
        groq = groq != null ? groq : new Groq(null, null, null);
    }

    public record Gemini(String apiKey, String baseUrl, Models models) {
        public Gemini {
            baseUrl = defaultIfBlank(baseUrl, "https://generativelanguage.googleapis.com/v1beta");
            models = models != null ? models : new Models(null, null);
        }

        public record Models(String flashLite, String flash) {
            public Models {
                flashLite = defaultIfBlank(flashLite, "gemini-2.5-flash-lite");
                flash = defaultIfBlank(flash, "gemini-2.5-flash");
            }
        }
    }

    public record Groq(String apiKey, String baseUrl, Models models) {
        public Groq {
            baseUrl = defaultIfBlank(baseUrl, "https://api.groq.com/openai/v1");
            models = models != null ? models : new Models(null, null);
        }

        public record Models(String fast, String quality) {
            public Models {
                fast = defaultIfBlank(fast, "llama-3.1-8b-instant");
                quality = defaultIfBlank(quality, "llama-3.3-70b-versatile");
            }
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
