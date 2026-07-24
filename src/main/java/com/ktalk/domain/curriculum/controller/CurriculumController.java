package com.ktalk.domain.curriculum.controller;

import com.ktalk.domain.curriculum.service.CurriculumService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 유형 진단 결과에 맞춰 배정된 8주(56일) 커리큘럼의 "오늘 학습"을 내려주는 API.
 */
@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
@Slf4j
public class CurriculumController {

    private final CurriculumService curriculumService;

    // JwtAuthenticationFilter가 유효한 토큰의 사용자 ID를 principal로 세팅한다.
    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }

    /** 오늘 학습 내용(없으면 최근 진단 결과로 커리큘럼을 새로 배정). */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse> today() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인 후 이용할 수 있어요."));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(curriculumService.getToday(userId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 오늘 학습 완료 처리 후 다음 날 학습 내용을 돌려준다. */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse> complete() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인 후 이용할 수 있어요."));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(curriculumService.completeToday(userId), "오늘 학습을 완료했어요."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
