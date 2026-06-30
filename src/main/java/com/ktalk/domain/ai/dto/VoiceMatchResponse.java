package com.ktalk.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class VoiceMatchResponse {

    private String transcription;           // 인식된 외국어 원문
    private String detectedLanguage;        // 감지된 언어 코드 (e.g. "en")
    private List<KoreanPhrase> phrases;     // Kpop/Kdrama 한국어 표현 목록
    private String audioContent;            // base64 인코딩된 한국어 TTS 음성 (MP3)

    @Getter
    @AllArgsConstructor
    public static class KoreanPhrase {
        private String korean;
        private String romanization;
        private String meaning;
        private String source;
        private String sourceType;          // "KPOP" or "KDRAMA"
        private String usageContext;
    }
}
