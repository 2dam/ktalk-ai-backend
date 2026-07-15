package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.dto.PronunciationFeedbackResponse;
import com.ktalk.domain.ai.service.PronunciationCoachService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * POST /api/ai/pronunciation-coach
 *
 * 학습자가 목표 한국어 문장을 녹음해서 보내면, Gemini가 발음 정확도를 채점하고
 * 개선 팁을 알려준다.
 *
 * Request: multipart/form-data
 *   - audio: 녹음된 음성 파일 (webm, mp3, wav, m4a, ogg 지원)
 *   - targetText: 학습자가 발음하려고 한 목표 한국어 문장
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class PronunciationCoachController {

    private final PronunciationCoachService pronunciationCoachService;

    @PostMapping(value = "/pronunciation-coach", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> evaluate(
            @RequestPart("audio") MultipartFile audioFile,
            @RequestPart("targetText") String targetText) {
        try {
            PronunciationFeedbackResponse response = pronunciationCoachService.evaluate(audioFile, targetText);
            return ResponseEntity.ok(ApiResponse.success(response, "발음 평가가 완료되었습니다."));
        } catch (Exception e) {
            log.error("발음 코칭 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("발음 평가 실패: " + e.getMessage()));
        }
    }
}
