package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GeminiApiClient geminiApiClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    public List<QuizQuestion> generateQuiz(String videoTranscript, int questionCount) {
        try {
            String prompt = buildPrompt(videoTranscript, questionCount);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            String text = geminiApiClient.generateText(webClient, apiKey, requestBody);

            log.info("Gemini 응답 수신 완료");
            return parseQuizResponse(text, questionCount);
        } catch (Exception e) {
            log.error("Gemini API 호출 실패: {}", e.getMessage(), e);
            return generateFallbackQuizzes(questionCount);
        }
    }

    private String buildPrompt(String transcript, int count) {
        return """
            다음 한국어 학습용 비디오 스크립트를 기반으로 %d개의 객관식 퀴즈를 생성해주세요.

            스크립트: %s

            각 퀴즈는 다음 형식의 JSON 배열로 응답해주세요:
            [
                {
                    "question": "질문 내용",
                    "options": ["보기1", "보기2", "보기3", "보기4"],
                    "correctAnswerIndex": 0
                }
            ]

            correctAnswerIndex는 0부터 시작합니다.
            """.formatted(count, transcript.substring(0, Math.min(transcript.length(), 1000)));
    }

    private List<QuizQuestion> parseQuizResponse(String text, int count) {
        List<QuizQuestion> questions = new ArrayList<>();
        try {
            // JSON 부분 추출 (마크다운 코드 블록 제거)
            String jsonPart = text.replaceAll("```json\\n?", "").replaceAll("```", "").trim();
            JsonNode quizArray = objectMapper.readTree(jsonPart);

            for (JsonNode node : quizArray) {
                String question = node.path("question").asText();
                List<String> options = new ArrayList<>();
                for (JsonNode opt : node.path("options")) {
                    options.add(opt.asText());
                }
                int correctIndex = node.path("correctAnswerIndex").asInt();
                questions.add(new QuizQuestion(question, options, correctIndex));
            }
        } catch (Exception e) {
            log.error("퀴즈 응답 파싱 실패: {}", e.getMessage());
            return generateFallbackQuizzes(count);
        }
        return questions;
    }

    private List<QuizQuestion> generateFallbackQuizzes(int count) {
        List<QuizQuestion> fallback = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fallback.add(new QuizQuestion(
                    "한국어 학습 퀴즈 " + (i + 1) + ": 다음 중 올바른 표현은?",
                    List.of("안녕하세요", "안녕하세여", "안녕하새요", "안녕하세용"),
                    0
            ));
        }
        return fallback;
    }

    public record QuizQuestion(String question, List<String> options, int correctAnswerIndex) {}
}