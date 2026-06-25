package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.service.GeminiService;
import com.ktalk.domain.ai.service.YouTubeService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AIController {

    private final YouTubeService youTubeService;
    private final GeminiService geminiService;

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

    @GetMapping("/health")
    public ResponseEntity<ApiResponse> health() {
        return ResponseEntity.ok(ApiResponse.success("AI Server is running! 🚀"));
    }
}