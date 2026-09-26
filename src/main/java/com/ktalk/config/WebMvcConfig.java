package com.ktalk.config;

import com.ktalk.global.web.AiUsageInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AiUsageInterceptor aiUsageInterceptor;

    // 비용이 드는 AI 호출만 월간 한도 대상이다. TTS는 TTSRateLimitService(분당)가 따로 막고,
    // 그 외 학습/조회 API는 제한하지 않는다.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiUsageInterceptor).addPathPatterns(
                "/api/ai/generate",
                "/api/ai/dialogue/**",
                "/api/ai/quiz/generate",
                "/api/ai/guided-learning/**",
                "/api/ai/phrase-match",
                "/api/ai/pronunciation-coach",
                "/api/ai/voice-match");
    }
}
