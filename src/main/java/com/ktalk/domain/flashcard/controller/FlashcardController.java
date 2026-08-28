package com.ktalk.domain.flashcard.controller;

import com.ktalk.domain.flashcard.dto.FlashcardCreateRequest;
import com.ktalk.domain.flashcard.dto.FlashcardResponse;
import com.ktalk.domain.flashcard.dto.FlashcardReviewRequest;
import com.ktalk.domain.flashcard.service.FlashcardService;
import com.ktalk.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flashcard")
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;

    // 개발/로컬 편의: 인증 없이 userId를 헤더/쿼리로 받는다(운영 시 SecurityConfig에서 제한).
    private String resolveUserId(HttpServletRequest req) {
        String h = req.getHeader("X-User-Id");
        if (h != null && !h.isBlank()) return h;
        String q = req.getParameter("userId");
        return q != null && !q.isBlank() ? q : "anonymous";
    }

    @PostMapping
    public ApiResponse<FlashcardResponse> create(HttpServletRequest req, @RequestBody FlashcardCreateRequest body) {
        try {
            FlashcardResponse r = flashcardService.create(resolveUserId(req), body);
            return ApiResponse.success(r, "카드를 추가했어요.");
        } catch (Exception e) {
            return ApiResponse.error("카드 추가 실패: " + e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<FlashcardResponse>> list(HttpServletRequest req) {
        return ApiResponse.success(flashcardService.listByUser(resolveUserId(req)), "내 카드 목록");
    }

    @GetMapping("/due")
    public ApiResponse<List<FlashcardResponse>> due(HttpServletRequest req) {
        return ApiResponse.success(flashcardService.listDue(resolveUserId(req)), "복습할 카드");
    }

    @PostMapping("/{id}/review")
    public ApiResponse<FlashcardResponse> review(@PathVariable Long id, @RequestBody FlashcardReviewRequest body) {
        try {
            FlashcardResponse r = flashcardService.review(id, body.quality());
            return ApiResponse.success(r, "복습을 반영했어요.");
        } catch (Exception e) {
            return ApiResponse.error("복습 실패: " + e.getMessage());
        }
    }
}
