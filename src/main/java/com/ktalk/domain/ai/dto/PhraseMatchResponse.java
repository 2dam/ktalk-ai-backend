package com.ktalk.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PhraseMatchResponse {

    private String inputText;          // 원본 입력 문장
    private String detectedLanguage;   // 감지된 입력 언어
    private List<KoreanPhrase> phrases; // 매칭된 한국어 표현 목록

    @Getter
    @AllArgsConstructor
    public static class KoreanPhrase {
        private String korean;         // 한국어 표현
        private String romanization;   // 로마자 표기
        private String meaning;        // 한국어 표현의 의미 (영어)
        private String source;         // 출처 (예: "BTS - Dynamite", "이태원 클라쓰 EP.1")
        private String sourceType;     // "KPOP" or "KDRAMA"
        private String usageContext;   // 어떤 상황에서 쓰이는지 설명
    }
}
