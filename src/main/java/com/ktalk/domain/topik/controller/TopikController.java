package com.ktalk.domain.topik.controller;

import com.ktalk.domain.topik.dto.SubmitAnswerRequest;
import com.ktalk.domain.topik.dto.WordCreateRequest;
import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.service.AdaptiveQuizService;
import com.ktalk.domain.topik.service.DistractorGeneratorService;
import com.ktalk.domain.topik.service.QuizItemService;
import com.ktalk.domain.topik.service.WordService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * TOPIK 학습 콘텐츠 API.
 * 1) 단어 난이도 자동 분류(상 5-6급 / 중 3-4급 / 하 1-2급)
 * 2) 임베딩 유사도 기반 오답 선택지 자동 생성
 * 3) 사용자 정답률 기반 적응형 문제 출제
 */
@RestController
@RequestMapping("/api/topik")
@RequiredArgsConstructor
@Slf4j
public class TopikController {

    private final WordService wordService;
    private final DistractorGeneratorService distractorGeneratorService;
    private final QuizItemService quizItemService;
    private final AdaptiveQuizService adaptiveQuizService;

    // JwtAuthenticationFilter가 유효한 토큰의 사용자 ID를 principal로 세팅한다.
    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof Long userId ? userId : null;
    }

    // ----- 단어(어휘) -----

    /** 단어 등록(수동 입력). 이미 있는 단어면 뜻/예문만 갱신한다. */
    @PostMapping("/words")
    public ResponseEntity<ApiResponse> createWord(@RequestBody WordCreateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(wordService.create(request), "단어를 등록했어요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 단어 목록. topikLevel 쿼리(예: LEVEL_3)로 필터링 가능. */
    @GetMapping("/words")
    public ResponseEntity<ApiResponse> listWords(@RequestParam(required = false) TopikLevel topikLevel) {
        return ResponseEntity.ok(ApiResponse.success(wordService.list(topikLevel)));
    }

    /** 단어를 상(5-6급)/중(3-4급)/하(1-2급)로 자동 분류한다. */
    @PostMapping("/words/{id}/classify")
    public ResponseEntity<ApiResponse> classify(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.success(wordService.classify(id), "난이도를 분류했어요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 임베딩 유사도로 헷갈리는 오답 후보를 찾는다(같은 등급대 우선). */
    @GetMapping("/words/{id}/distractors")
    public ResponseEntity<ApiResponse> distractors(
            @PathVariable String id, @RequestParam(defaultValue = "3") int count) {
        try {
            return ResponseEntity.ok(ApiResponse.success(distractorGeneratorService.generate(id, count)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 단어로부터 객관식 퀴즈 문항을 만든다(난이도 분류 + 오답 생성을 묶어서 실행). */
    @PostMapping("/words/{id}/quiz-item")
    public ResponseEntity<ApiResponse> createQuizItem(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.success(quizItemService.createFromWord(id), "퀴즈 문항을 만들었어요."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 아직 퀴즈 문항이 없는 단어를 전부 찾아 한 번에 문항으로 만든다(초기 문항 뱅크 부트스트랩용). */
    @PostMapping("/quiz-items/generate-all")
    public ResponseEntity<ApiResponse> generateAllQuizItems() {
        var result = quizItemService.generateAllMissing();
        String message = "문항 %d개를 만들었어요 (건너뜀 %d개).".formatted(result.created(), result.skipped());
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    // ----- 적응형 퀴즈 -----

    /** 현재 추정 등급에 맞는 다음 문제. */
    @GetMapping("/quiz/next")
    public ResponseEntity<ApiResponse> nextQuestion() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인 후 이용할 수 있어요."));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(adaptiveQuizService.nextQuestion(userId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 답안 채점. 정답률에 따라 등급이 자동으로 오르내린다. */
    @PostMapping("/quiz/{itemId}/answer")
    public ResponseEntity<ApiResponse> answer(
            @PathVariable String itemId, @RequestBody SubmitAnswerRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인 후 이용할 수 있어요."));
        }
        try {
            var result = adaptiveQuizService.submitAnswer(userId, itemId, request.selectedIndex());
            String message = result.correct() ? "정답이에요!" : "아쉬워요, 다시 도전해보세요.";
            return ResponseEntity.ok(ApiResponse.success(result, message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** 현재 추정 등급과 누적 정답률. */
    @GetMapping("/quiz/progress")
    public ResponseEntity<ApiResponse> progress() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("로그인 후 이용할 수 있어요."));
        }
        return ResponseEntity.ok(ApiResponse.success(adaptiveQuizService.getProgress(userId)));
    }
}
