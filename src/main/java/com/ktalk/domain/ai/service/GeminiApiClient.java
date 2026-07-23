package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
class GeminiApiClient {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";

    private static final String EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent";

    private final ObjectMapper objectMapper;

    String generateText(WebClient webClient, String apiKey, Object requestBody) {
        String response = webClient.post()
                .uri(GEMINI_URL + "?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", response, e);
            throw new RuntimeException("Gemini API 응답 파싱 실패", e);
        }
    }

    List<Double> embedText(WebClient webClient, String apiKey, String text) {
        Map<String, Object> body = Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );
        String response = webClient.post()
                .uri(EMBEDDING_URL + "?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode values = objectMapper.readTree(response).path("embedding").path("values");
            List<Double> vector = new ArrayList<>();
            values.forEach(v -> vector.add(v.asDouble()));
            return vector;
        } catch (Exception e) {
            log.error("Failed to parse Gemini embedding response: {}", response, e);
            throw new RuntimeException("Gemini 임베딩 응답 파싱 실패", e);
        }
    }

    static String stripMarkdownFences(String text) {
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
        }
        return cleaned;
    }
}
