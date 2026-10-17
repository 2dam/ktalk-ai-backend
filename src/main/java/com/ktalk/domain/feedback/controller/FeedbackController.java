package com.ktalk.domain.feedback.controller;

import com.ktalk.domain.feedback.dto.FeedbackRequest;
import com.ktalk.domain.feedback.entity.Feedback;
import com.ktalk.domain.feedback.service.FeedbackService;
import com.ktalk.global.response.ApiResponse;
import com.ktalk.global.web.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 앱 안 "의견 보내기" 버튼이 호출하는 API. 로그인 없이도 보낼 수 있다. */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse> submit(@RequestBody FeedbackRequest request, HttpServletRequest http) {
        if (!feedbackService.tryAcquire(RequestUtils.clientKey(http))) {
            return ResponseEntity.status(429).body(ApiResponse.error("잠시 후 다시 보내 주세요."));
        }
        try {
            Feedback saved = feedbackService.submit(request, currentUserId(), http.getHeader(HttpHeaders.USER_AGENT));
            return ResponseEntity.ok(ApiResponse.success(Map.of("id", saved.getId()), "소중한 의견 고마워요!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("피드백 저장 실패", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("저장하지 못했어요. 잠시 후 다시 시도해 주세요."));
        }
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }
}
