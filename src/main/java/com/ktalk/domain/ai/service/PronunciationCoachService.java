package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.ai.dto.PronunciationFeedbackResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PronunciationCoachService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PronunciationCoachService(@Qualifier("audioWebClient") WebClient webClient,
                                      ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public PronunciationFeedbackResponse evaluate(MultipartFile audioFile, String targetText) {
        try {
            byte[] audioBytes = audioFile.getBytes();
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
            String mimeType = resolveMimeType(audioFile.getOriginalFilename(), audioFile.getContentType());

            String prompt = buildPrompt(targetText);

            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(
                                    Map.of("inline_data", Map.of(
                                            "mime_type", mimeType,
                                            "data", audioBase64
                                    )),
                                    Map.of("text", prompt)
                            )
                    ))
            );

            String response = webClient.post()
                    .uri(GEMINI_URL + "?key=" + geminiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response, targetText);
        } catch (Exception e) {
            log.error("발음 평가 실패: {}", e.getMessage(), e);
            throw new RuntimeException("발음 평가 실패: " + e.getMessage());
        }
    }

    private String buildPrompt(String targetText) {
        return """
                You are a Korean pronunciation coach.

                The target Korean sentence the learner is trying to say is: "%s"

                Listen to the attached audio of the learner speaking Korean.

                Your tasks:
                1. Transcribe what the learner actually said, in Korean.
                2. Compare it to the target sentence and score pronunciation accuracy from 0 to 100
                   (consider correct syllables, intonation and fluency; do not penalize minor mic noise).
                3. Give one short overall feedback sentence, in Korean.
                4. Give 2 to 3 concrete, specific improvement tips, in Korean.

                Return ONLY pure JSON, no markdown formatting:
                {
                  "transcribedText": "<학습자가 실제로 말한 내용>",
                  "score": <0-100 정수>,
                  "feedback": "<총평 한 문장>",
                  "tips": ["<팁1>", "<팁2>"]
                }
                """.formatted(targetText);
    }

    private PronunciationFeedbackResponse parseResponse(String rawResponse, String targetText) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            String cleaned = text.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }

            JsonNode parsed = objectMapper.readTree(cleaned);
            String transcribedText = parsed.path("transcribedText").asText("");
            int score = parsed.path("score").asInt(0);
            String feedback = parsed.path("feedback").asText("");

            List<String> tips = new ArrayList<>();
            for (JsonNode tip : parsed.path("tips")) {
                tips.add(tip.asText());
            }

            return new PronunciationFeedbackResponse(targetText, transcribedText, score, feedback, tips);
        } catch (Exception e) {
            log.error("발음 평가 응답 파싱 실패: {}", rawResponse, e);
            throw new RuntimeException("응답 파싱 실패: " + e.getMessage());
        }
    }

    private String resolveMimeType(String filename, String contentType) {
        if (contentType != null && contentType.startsWith("audio/")) {
            return contentType;
        }
        if (filename != null) {
            if (filename.endsWith(".mp3")) return "audio/mp3";
            if (filename.endsWith(".wav")) return "audio/wav";
            if (filename.endsWith(".ogg")) return "audio/ogg";
            if (filename.endsWith(".m4a")) return "audio/m4a";
            if (filename.endsWith(".webm")) return "audio/webm";
        }
        return "audio/webm"; // 브라우저 기본 녹음 포맷
    }
}
