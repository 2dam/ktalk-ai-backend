package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.ai.dto.GuidedLearningRequest;
import com.ktalk.domain.ai.dto.GuidedLearningResponse;
import com.ktalk.domain.ai.dto.TeachBackRequest;
import com.ktalk.domain.ai.dto.TeachBackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * "Learning Navigation": 관심사 기반 유추 -> 패턴 응용(티칭백) -> 언어 감각 훈련으로 이어지는
 * 4단계 가이드 학습 흐름을 위한 AI 생성 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuidedLearningService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final GeminiApiClient geminiApiClient;

    public GuidedLearningResponse generateLesson(GuidedLearningRequest request) {
        String prompt = buildLessonPrompt(request.getInterest());
        String rawJson = callGemini(prompt);
        return parseLessonResponse(request.getInterest(), rawJson);
    }

    public TeachBackResponse evaluateTeachBack(TeachBackRequest request) {
        String prompt = buildTeachBackPrompt(request);
        String rawJson = callGemini(prompt);
        return parseTeachBackResponse(rawJson);
    }

    private String buildLessonPrompt(String interest) {
        return """
                You are designing one guided Korean-learning micro-lesson for a learner, built around
                something they personally care about. This is called "Learning Navigation" and has three
                stages: (1) infer the meaning of a sentence about their interest from hints, (2) explain the
                grammar pattern back in their own words like a teacher, (3) a short sensory/embodied drill
                that anchors one key word using imagery and repeated speaking aloud.

                The learner's interest: "%s"

                Design the lesson content now. Requirements:
                - "sentence": one natural, useful Korean sentence that is clearly about the learner's interest.
                  Beginner-to-lower-intermediate level (TOPIK 1~3 grammar), one sentence only.
                - "hints": exactly 3 hints in Korean, ordered from vague to specific, that help the learner
                  GUESS the meaning without giving it away outright. Do not include the English meaning itself
                  in any hint.
                - "meaning": the accurate English translation of "sentence".
                - "vocab": 2 to 4 key words from "sentence", each with its base meaning in English.
                - "pattern": the core grammar pattern used in "sentence", written as a short reusable template
                  (e.g. "[시간 표현] + [명사]을/를 + [동사]요").
                - "patternExplanation": one or two sentences in Korean explaining when/why to use that pattern,
                  written simply enough for a beginner to later compare their own explanation against it.
                - "sensoryWord": ONE concrete, sensory/embodiable word from "sentence" (a word you can physically
                  mime, feel, or picture — avoid abstract grammar particles).
                - "sensoryImagery": one or two sentences in Korean inviting the learner to imagine a vivid
                  physical scene or sensation connected to "sensoryWord" (sight/sound/touch/movement), in the
                  style of "추운 겨울날 손을 호호 부는 상상을 해보세요."
                - "sensoryInstruction": one short Korean instruction telling the learner to say "sensoryWord"
                  out loud several times while holding that imagery in mind.

                Return ONLY pure JSON, no markdown formatting, with this exact structure:
                {
                  "sentence": "...",
                  "hints": ["...", "...", "..."],
                  "meaning": "...",
                  "vocab": [{"word": "...", "meaning": "..."}],
                  "pattern": "...",
                  "patternExplanation": "...",
                  "sensoryWord": "...",
                  "sensoryImagery": "...",
                  "sensoryInstruction": "..."
                }
                """.formatted(interest);
    }

    private String buildTeachBackPrompt(TeachBackRequest request) {
        String example = (request.getStudentExample() == null || request.getStudentExample().isBlank())
                ? "(학생이 예문을 만들지 않았습니다)"
                : request.getStudentExample();

        return """
                You are a warm, encouraging Korean teacher. A student just learned this grammar pattern:

                Pattern: "%s"
                Reference explanation: "%s"

                The student is now acting as the teacher and explaining the pattern in their own words,
                then giving their own example sentence using it.

                Student's explanation: "%s"
                Student's example sentence: "%s"

                Evaluate gently:
                - Is the core idea of the pattern correct, even if phrased loosely or imperfectly?
                - If they gave an example sentence, does it actually use the pattern correctly?

                Respond in Korean. Give ONE short paragraph of feedback (2-4 sentences): start with genuine
                encouragement about what they got right, then, only if needed, one gentle correction or
                clarification. Do not be harsh. Do not just repeat the reference explanation verbatim.

                Return ONLY pure JSON, no markdown formatting:
                {
                  "feedback": "...",
                  "patternUnderstood": <true or false>
                }
                """.formatted(request.getPattern(), request.getPatternExplanation(), request.getStudentExplanation(), example);
    }

    private String callGemini(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );
        return geminiApiClient.generateText(webClient, geminiApiKey, body);
    }

    private GuidedLearningResponse parseLessonResponse(String interest, String rawJson) {
        try {
            String cleaned = GeminiApiClient.stripMarkdownFences(rawJson);
            JsonNode root = objectMapper.readTree(cleaned);

            List<String> hints = objectMapper.convertValue(root.path("hints"), new TypeReference<>() {});
            List<GuidedLearningResponse.VocabItem> vocab = objectMapper.convertValue(
                    root.path("vocab"), new TypeReference<>() {});

            return new GuidedLearningResponse(
                    interest,
                    root.path("sentence").asText(""),
                    hints,
                    root.path("meaning").asText(""),
                    vocab,
                    root.path("pattern").asText(""),
                    root.path("patternExplanation").asText(""),
                    root.path("sensoryWord").asText(""),
                    root.path("sensoryImagery").asText(""),
                    root.path("sensoryInstruction").asText("")
            );
        } catch (Exception e) {
            log.error("Failed to parse guided learning lesson response: {}", rawJson, e);
            throw new RuntimeException("응답 파싱 실패: " + e.getMessage());
        }
    }

    private TeachBackResponse parseTeachBackResponse(String rawJson) {
        try {
            String cleaned = GeminiApiClient.stripMarkdownFences(rawJson);
            JsonNode root = objectMapper.readTree(cleaned);

            return new TeachBackResponse(
                    root.path("feedback").asText(""),
                    root.path("patternUnderstood").asBoolean(true)
            );
        } catch (Exception e) {
            log.error("Failed to parse teach-back response: {}", rawJson, e);
            throw new RuntimeException("응답 파싱 실패: " + e.getMessage());
        }
    }
}
