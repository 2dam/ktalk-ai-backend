package com.ktalk.domain.learning.controller;

import com.ktalk.domain.learning.entity.LearningProgress;
import com.ktalk.domain.learning.service.LearningService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
@Slf4j
public class LearningController {

    private final LearningService learningService;

    // 임시 사용자 ID (실제 구현에서는 SecurityContext에서 가져옴)
    private Long getCurrentUserId() {
        return 1L; // 임시: 첫 번째 사용자
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse> startLearning(@RequestBody StartLearningRequest request) {
        try {
            Long userId = getCurrentUserId();
            LearningProgress progress = learningService.startLearning(
                    userId,
                    request.videoId(),
                    request.videoTitle(),
                    request.thumbnailUrl(),
                    request.channelName()
            );
            return ResponseEntity.ok(ApiResponse.success(progress, "학습을 시작했습니다."));
        } catch (Exception e) {
            log.error("학습 시작 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("학습 시작 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/{progressId}/progress")
    public ResponseEntity<ApiResponse> updateProgress(
            @PathVariable String progressId,
            @RequestBody ProgressUpdateRequest request) {
        try {
            LearningProgress progress = learningService.updateProgress(
                    progressId,
                    request.watchTimeSeconds(),
                    request.totalVideoSeconds()
            );
            return ResponseEntity.ok(ApiResponse.success(progress, "진도가 업데이트되었습니다."));
        } catch (Exception e) {
            log.error("진도 업데이트 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("진도 업데이트 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/{progressId}/complete")
    public ResponseEntity<ApiResponse> completeLearning(@PathVariable String progressId) {
        try {
            LearningProgress progress = learningService.completeLearning(progressId);
            return ResponseEntity.ok(ApiResponse.success(progress, "학습을 완료했습니다! 🎉"));
        } catch (Exception e) {
            log.error("학습 완료 처리 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("학습 완료 처리 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<ApiResponse> getProgress() {
        try {
            Long userId = getCurrentUserId();
            List<LearningProgress> progress = learningService.getUserProgress(userId);
            return ResponseEntity.ok(ApiResponse.success(progress));
        } catch (Exception e) {
            log.error("진도 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("진도 조회 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse> getActiveLearning() {
        try {
            Long userId = getCurrentUserId();
            List<LearningProgress> active = learningService.getActiveLearning(userId);
            return ResponseEntity.ok(ApiResponse.success(active));
        } catch (Exception e) {
            log.error("활성 학습 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("활성 학습 조회 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getLearningStats() {
        try {
            Long userId = getCurrentUserId();
            long completedCount = learningService.getCompletedCount(userId);
            return ResponseEntity.ok(ApiResponse.success(
                    new LearningStats(completedCount),
                    "학습 통계를 조회했습니다."
            ));
        } catch (Exception e) {
            log.error("학습 통계 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("학습 통계 조회 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse> health() {
        return ResponseEntity.ok(ApiResponse.success("Learning Server is running! 🚀"));
    }

    // DTOs (요청/응답용)
    public record StartLearningRequest(
            String videoId,
            String videoTitle,
            String thumbnailUrl,
            String channelName
    ) {}

    public record ProgressUpdateRequest(
            int watchTimeSeconds,
            int totalVideoSeconds
    ) {}

    public record LearningStats(
            long completedCount
    ) {}
}