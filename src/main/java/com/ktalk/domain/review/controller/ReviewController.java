package com.ktalk.domain.review.controller;

import com.ktalk.domain.review.dto.GradeReviewRequest;
import com.ktalk.domain.review.dto.ReviewItemResponse;
import com.ktalk.domain.review.dto.SaveReviewItemRequest;
import com.ktalk.domain.review.service.ReviewService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 복습 알람 API.
 *
 * <p>앱 안에서 동작하는 알람이라 별도 서버 스케줄러가 필요 없다. 프론트가
 * {@code GET /api/review/count} 로 "지금 복습할 개수"를 물어보면, 그 순간
 * nextReviewAt <= now 인 항목 수를 세서 알람 배지로 보여준다. 배포 서버가
 * 무료 플랜이라 잠들어도 알람이 누락되지 않는다.
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;

    // JwtAuthenticationFilter가 유효한 토큰의 사용자 ID를 principal로 세팅한다.
    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }

    /** 방금 배운 문장을 복습 목록에 추가 */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse> save(@RequestBody SaveReviewItemRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("복습 알람은 로그인 후 이용할 수 있어요."));
        }
        try {
            ReviewItemResponse item = reviewService.saveLearned(userId, request);
            return ResponseEntity.ok(ApiResponse.success(item, "복습 목록에 추가했어요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("복습 항목 저장 실패", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("저장 실패: " + e.getMessage()));
        }
    }

    /** 지금 복습할 항목(알람 대상) */
    @GetMapping("/due")
    public ResponseEntity<ApiResponse> due() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("복습 알람은 로그인 후 이용할 수 있어요."));
        }
        List<ReviewItemResponse> items = reviewService.getDue(userId);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    /** 전체 복습 항목(예정 포함) */
    @GetMapping("/items")
    public ResponseEntity<ApiResponse> all() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("복습 알람은 로그인 후 이용할 수 있어요."));
        }
        List<ReviewItemResponse> items = reviewService.getAll(userId);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    /** 알람 배지용: 지금 복습할 개수 */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse> count() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            // 비로그인은 알람 0개로 취급 (에러 대신 조용히 0)
            return ResponseEntity.ok(ApiResponse.success(Map.of("dueCount", 0)));
        }
        long dueCount = reviewService.dueCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("dueCount", dueCount)));
    }

    /** 복습 카드 채점 → SM-2로 다음 복습 시각 갱신 */
    @PostMapping("/items/{id}/grade")
    public ResponseEntity<ApiResponse> grade(@PathVariable String id, @RequestBody GradeReviewRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("복습 알람은 로그인 후 이용할 수 있어요."));
        }
        try {
            ReviewItemResponse item = reviewService.grade(userId, id, request.quality());
            return ResponseEntity.ok(ApiResponse.success(item, "복습 결과를 반영했어요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("복습 채점 실패", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("채점 실패: " + e.getMessage()));
        }
    }

    /** 복습 항목 삭제 */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<ApiResponse> delete(@PathVariable String id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("복습 알람은 로그인 후 이용할 수 있어요."));
        }
        try {
            reviewService.delete(userId, id);
            return ResponseEntity.ok(ApiResponse.success("삭제했어요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
