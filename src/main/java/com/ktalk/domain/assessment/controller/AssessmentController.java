package com.ktalk.domain.assessment.controller;

import com.ktalk.domain.assessment.dto.AssessmentQuestion;
import com.ktalk.domain.assessment.dto.AssessmentResultResponse;
import com.ktalk.domain.assessment.dto.AssessmentSubmitRequest;
import com.ktalk.domain.assessment.service.AssessmentQuestionProvider;
import com.ktalk.domain.assessment.service.AssessmentService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
@Slf4j
public class AssessmentController {

    private final AssessmentService assessmentService;

    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }

    @GetMapping("/questions")
    public ResponseEntity<ApiResponse> getQuestions() {
        List<AssessmentQuestion> questions = AssessmentQuestionProvider.QUESTIONS;
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse> submit(@RequestBody AssessmentSubmitRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
        }
        try {
            AssessmentResultResponse result = assessmentService.submit(userId, request.answers());
            return ResponseEntity.ok(ApiResponse.success(result, "학습 유형 진단이 완료되었습니다."));
        } catch (Exception e) {
            log.error("학습 유형 진단 실패", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("진단 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/result")
    public ResponseEntity<ApiResponse> getResult() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인이 필요합니다."));
        }
        try {
            AssessmentResultResponse result = assessmentService.getLatestResult(userId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("아직 진단 결과가 없습니다."));
        }
    }
}
