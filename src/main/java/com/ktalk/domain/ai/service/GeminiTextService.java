package com.ktalk.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * 퀴즈 생성 전용인 GeminiService와 달리, 임의의 프롬프트를 그대로 던지고 텍스트 응답을
 * 받는 범용 래퍼. 다른 도메인(예: TOPIK 단어 난이도 분류)이 GeminiApiClient를 직접 쓸 수
 * 없으므로(패키지 전용 클래스) 이 서비스를 통해 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class GeminiTextService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final GeminiApiClient geminiApiClient;

    public String generate(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );
        return geminiApiClient.generateText(webClient, apiKey, requestBody);
    }
}
