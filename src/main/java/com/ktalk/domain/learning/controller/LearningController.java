package com.ktalk.domain.learning.controller;

import com.ktalk.domain.learning.entity.LearningProgress;
import com.ktalk.domain.learning.service.LearningService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
@Slf4j
public class LearningController {

    private final LearningService learningService;

    // JwtAuthenticationFilter가 유효한 토큰의 사용자 ID를 principal로 세팅해둔다.
    // 토큰이 없거나 유효하지 않으면 null을 반환한다.
    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse> startLearning(@RequestBody StartLearningRequest request) {
        try {
            Long userId = getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
            }
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
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
            }
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
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
            }
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
            if (userId == null) {
                return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
            }
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