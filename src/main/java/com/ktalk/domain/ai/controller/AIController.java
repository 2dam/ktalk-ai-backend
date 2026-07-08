package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.service.GeminiService;
import com.ktalk.domain.ai.service.TTSService;
import com.ktalk.domain.ai.service.YouTubeService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/videos/search")
    public ResponseEntity<ApiResponse> searchVideos(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int maxResults) {
        try {
            List<YouTubeService.VideoInfo> videos = youTubeService.searchVideos(query, maxResults);
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
    public ResponseEntity<ApiResponse> textToSpeech(@RequestBody Map<String, String> body) {
        try {
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
    public ResponseEntity<ApiResponse> textToSpeechDialogue(@RequestBody Map<String, Object> body) {
        try {
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
}