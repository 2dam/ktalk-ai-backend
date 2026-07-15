package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.ai.dto.PhraseMatchRequest;
import com.ktalk.domain.ai.dto.PhraseMatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhraseMatchService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GeminiApiClient geminiApiClient;

    public PhraseMatchResponse matchPhrases(PhraseMatchRequest request) {
        String prompt = buildPrompt(request.getText(), request.getLanguage());

        String rawJson = callGemini(prompt);
        return parseResponse(request.getText(), rawJson);
    }

    private String buildPrompt(String text, String language) {
        String langHint = (language != null && !language.isBlank())
                ? "The input language is: " + language + "."
                : "Auto-detect the input language.";

        return """
                You are a Korean language assistant specializing in K-pop and K-drama expressions.

                Task: Given the following foreign language sentence, find Korean expressions from K-pop songs or K-dramas that convey a similar meaning or emotion.

                Input sentence: "%s"
                %s

                Return a JSON object with this exact structure (no markdown, pure JSON):
                {
                  "detectedLanguage": "<ISO 639-1 language code, e.g. en, ja, zh>",
                  "phrases": [
                    {
                      "korean": "<Korean expression>",
                      "romanization": "<romanized pronunciation>",
                      "meaning": "<English meaning of the Korean expression>",
                      "source": "<exact song title or drama title and episode/artist>",
                      "sourceType": "<KPOP or KDRAMA>",
                      "usageContext": "<brief explanation of when/how this expression is used>"
                    }
                  ]
                }

                Rules:
                - Return 3 to 5 phrases.
                - Only include real, well-known K-pop songs or K-dramas.
                - The "source" field must be specific (e.g. "BTS - Spring Day", "사랑의 불시착 EP.1").
                - Do NOT wrap output in markdown code blocks.
                """.formatted(text, langHint);
    }

    private String callGemini(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );
        return geminiApiClient.generateText(webClient, geminiApiKey, body);
    }

    private PhraseMatchResponse parseResponse(String inputText, String rawJson) {
        try {
            // Gemini가 가끔 ```json ... ``` 블록을 붙이는 경우 제거
            String cleaned = GeminiApiClient.stripMarkdownFences(rawJson);

            JsonNode root = objectMapper.readTree(cleaned);
            String detectedLanguage = root.path("detectedLanguage").asText("unknown");

            List<PhraseMatchResponse.KoreanPhrase> phrases = objectMapper.convertValue(
                    root.path("phrases"),
                    new TypeReference<>() {}
            );

            return new PhraseMatchResponse(inputText, detectedLanguage, phrases);
        } catch (Exception e) {
            log.error("Failed to parse phrase match response: {}", rawJson, e);
            throw new RuntimeException("응답 파싱 실패: " + e.getMessage());
        }
    }
}
