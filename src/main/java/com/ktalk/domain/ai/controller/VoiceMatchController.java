package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.dto.VoiceMatchResponse;
import com.ktalk.domain.ai.service.VoiceMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VoiceMatchController {

    private final VoiceMatchService voiceMatchService;

    /**
     * POST /api/ai/voice-match
     *
     * 외국어 음성 파일을 업로드하면:
     *   1. Gemini가 음성을 인식하고 Kpop/Kdrama 한국어 표현을 찾음
     *   2. Google TTS가 한국어 표현들을 음성으로 변환
     *
     * Request: multipart/form-data
     *   - audio: 음성 파일 (webm, mp3, wav, m4a, ogg 지원)
     *
     * Response:
     * {
     *   "transcription": "I miss you so much",
     *   "detectedLanguage": "en",
     *   "phrases": [
     *     {
     *       "korean": "보고 싶다",
     *       "romanization": "bogo sipda",
     *       "meaning": "I miss you",
     *       "source": "2NE1 - Missing You",
     *       "sourceType": "KPOP",
     *       "usageContext": "그리운 감정을 표현할 때 씁니다"
     *     }
     *   ],
     *   "audioContent": "<base64 MP3>"  ← 프론트에서 new Audio("data:audio/mp3;base64,...").play()
     * }
     */
    @PostMapping(value = "/voice-match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceMatchResponse> voiceMatch(
            @RequestPart("audio") MultipartFile audioFile) {
        try {
            VoiceMatchResponse response = voiceMatchService.processVoice(audioFile);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("voice-match 처리 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
