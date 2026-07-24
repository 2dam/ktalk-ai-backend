package com.ktalk.domain.curriculum.controller;

import com.ktalk.domain.curriculum.dto.SubmitProblemAnswerRequest;
import com.ktalk.domain.curriculum.service.CurriculumService;
import com.ktalk.domain.curriculum.service.PassageAudioService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 학습 유형 진단 결과에 맞춰 배정된 8주 커리큘럼의 "오늘 학습"을 내려주는 API.
 */
@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
@Slf4j
public class CurriculumController {

    private final CurriculumService curriculumService;
    private final PassageAudioService passageAudioService;

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

    /** 지문 하나의 문제 하나를 채점하고 해설(보기별 오답 분석 + 함정 포인트)을 돌려준다. */
    @PostMapping("/problems/{problemId}/answer")
    public ResponseEntity<ApiResponse> answer(
            @PathVariable String problemId, @RequestBody SubmitProblemAnswerRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(curriculumService.submitAnswer(problemId, request.selectedIndex())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 듣기 지문을 화자별 음성 세그먼트로 합성해서 돌려준다(순서대로 재생). */
    @GetMapping("/passages/{passageId}/audio")
    public ResponseEntity<ApiResponse> passageAudio(@PathVariable String passageId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(passageAudioService.generateAudio(passageId)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("듣기 음성 합성 실패: passageId={}", passageId, e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("음성 합성 실패: " + e.getMessage()));
        }
    }
}
