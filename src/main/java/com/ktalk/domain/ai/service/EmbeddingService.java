package com.ktalk.domain.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * 단어/개념 간 유사어 매칭(예: "축구장" ≈ "운동장")을 위한 텍스트 임베딩 생성.
 * domain.block 쪽에서 태그 유사도를 계산할 때 사용한다.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final GeminiApiClient geminiApiClient;

    public List<Double> embed(String text) {
        return geminiApiClient.embedText(webClient, geminiApiKey, text);
    }
}
