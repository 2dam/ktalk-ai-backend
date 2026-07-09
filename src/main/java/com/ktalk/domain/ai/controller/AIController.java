package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.service.AIService;
import com.ktalk.domain.ai.service.GeminiService;
import com.ktalk.domain.ai.service.TTSRateLimitService;
import com.ktalk.domain.ai.service.TTSService;
import com.ktalk.domain.ai.service.YouTubeService;
import com.ktalk.domain.content.entity.Content;
import com.ktalk.domain.content.repository.ContentRepository;
import com.ktalk.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {

    private final YouTubeService youTubeService;
    private final GeminiService geminiService;
    private final TTSService ttsService;
    private final TTSRateLimitService ttsRateLimitService;
    private final AIService aiService;
    private final ContentRepository contentRepository;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse> generateContent(@RequestBody GenerateRequest request) {
        try {
            if (request.topic() == null || request.topic().isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("주제를 입력해주세요."));
            }
            Content content = aiService.generateContent(request.topic());
            Content saved = contentRepository.save(content);
            return ResponseEntity.ok(ApiResponse.success(saved, "AI가 콘텐츠를 생성했습니다."));
        } catch (Exception e) {
            log.error("AI 콘텐츠 생성 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("AI 콘텐츠 생성 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/dialogue/{id}")
    public ResponseEntity<ApiResponse> generateDialogue(@PathVariable Long id) {
        try {
            Content content = contentRepository.findById(id).orElse(null);
            if (content == null) {
                return ResponseEntity.notFound().build();
            }
            String dialogue = aiService.generateDialogue(content);
            content.setDialogue(dialogue);
            Content saved = contentRepository.save(content);
            return ResponseEntity.ok(ApiResponse.success(saved, "AI가 대화문을 생성했습니다."));
        } catch (Exception e) {
            log.error("대화문 생성 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("대화문 생성 실패: " + e.getMessage()));
        }
    }

    public record GenerateRequest(String topic) {}

    @GetMapping("/videos/search")
    public ResponseEntity<ApiResponse> searchVideos(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int maxResults,
            @RequestParam(defaultValue = "false") boolean preferShort) {
        try {
            List<YouTubeService.VideoInfo> videos = youTubeService.searchVideos(query, maxResults, preferShort);
            return ResponseEntity.ok(ApiResponse.success(videos, "영상 검색이 완료되었습니다."));
        } catch (Exception e) {
            log.error("영상 검색 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("영상 검색 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/quiz/generate")
    public ResponseEntity<ApiResponse> generateQuiz(
            @RequestParam String transcript,
            @RequestParam(defaultValue = "5") int count) {
        try {
            List<GeminiService.QuizQuestion> quizzes = geminiService.generateQuiz(transcript, count);
            return ResponseEntity.ok(ApiResponse.success(quizzes, "퀴즈가 생성되었습니다."));
        } catch (Exception e) {
            log.error("퀴즈 생성 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("퀴즈 생성 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/tts")
    public ResponseEntity<ApiResponse> textToSpeech(@RequestBody Map<String, String> body,
                                                    HttpServletRequest request) {
        try {
            if (!ttsRateLimitService.tryAcquire(clientKey(request))) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error("음성 요청이 많습니다. 잠시 후 다시 시도해주세요."));
            }
            String text = body.get("text");
            String gender = body.getOrDefault("gender", "MALE");
            String audioContent = ttsService.synthesize(text, gender);
            return ResponseEntity.ok(ApiResponse.success(Map.of("audioContent", audioContent), "음성 합성이 완료되었습니다."));
        } catch (Exception e) {
            log.error("음성 합성 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("음성 합성 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/tts/dialogue")
    public ResponseEntity<ApiResponse> textToSpeechDialogue(@RequestBody Map<String, Object> body,
                                                            HttpServletRequest request) {
        try {
            if (!ttsRateLimitService.tryAcquire(clientKey(request))) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error("음성 요청이 많습니다. 잠시 후 다시 시도해주세요."));
            }
            String title = String.valueOf(body.getOrDefault("title", ""));
            String description = String.valueOf(body.getOrDefault("description", ""));
            String dialogue = String.valueOf(body.getOrDefault("dialogue", ""));
            boolean swap = Boolean.TRUE.equals(body.get("swap"));

            List<Map<String, String>> segments = ttsService.synthesizeDialogue(title, description, dialogue, swap);
            return ResponseEntity.ok(ApiResponse.success(Map.of("segments", segments), "음성 합성이 완료되었습니다."));
        } catch (Exception e) {
            log.error("대화문 음성 합성 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("대화문 음성 합성 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse> health() {
        return ResponseEntity.ok(ApiResponse.success("AI Server is running! 🚀"));
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
