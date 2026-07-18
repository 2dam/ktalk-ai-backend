package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.dto.GuidedLearningRequest;
import com.ktalk.domain.ai.dto.GuidedLearningResponse;
import com.ktalk.domain.ai.dto.TeachBackRequest;
import com.ktalk.domain.ai.dto.TeachBackResponse;
import com.ktalk.domain.ai.service.GuidedLearningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/guided-learning")
@RequiredArgsConstructor
public class GuidedLearningController {

    private final GuidedLearningService guidedLearningService;

    /**
     * POST /api/ai/guided-learning/generate
     *
     * "Learning Navigation" 1~3단계(유추 연습, 패턴 참고자료, 언어 감각 훈련)에 쓸 콘텐츠를
     * 관심사 하나로 한 번에 생성한다.
     *
     * Request body:
     *   { "interest": "축구" }
     */
    @PostMapping("/generate")
    public ResponseEntity<GuidedLearningResponse> generateLesson(
            @Valid @RequestBody GuidedLearningRequest request) {
        GuidedLearningResponse response = guidedLearningService.generateLesson(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/ai/guided-learning/teach-back
     *
     * "Learning Navigation" 2단계: 학생이 선생님이 되어 문형 패턴을 설명하면 AI가 피드백을 준다.
     *
     * Request body:
     *   { "pattern": "...", "patternExplanation": "...", "studentExplanation": "...", "studentExample": "..." }
     */
    @PostMapping("/teach-back")
    public ResponseEntity<TeachBackResponse> evaluateTeachBack(
            @Valid @RequestBody TeachBackRequest request) {
        TeachBackResponse response = guidedLearningService.evaluateTeachBack(request);
        return ResponseEntity.ok(response);
    }
}
