package com.ktalk.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@AllArgsConstructor
public class VoiceMatchResponse {

    private String transcription;           // 인식된 외국어 원문
    private String detectedLanguage;        // 감지된 언어 코드 (e.g. "en")
    private List<KoreanPhrase> phrases;     // Kpop/Kdrama 한국어 표현 목록
    private String audioContent;            // base64 인코딩된 한국어 TTS 음성 (MP3)

    // Jackson이 Gemini 응답(JsonNode)을 convertValue로 역직렬화하려면
    // 기본 생성자 + setter가 필요하다 (AllArgsConstructor만으로는 인식 못 함)
    @Getter
    @Setter
    @NoArgsConstructor
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
