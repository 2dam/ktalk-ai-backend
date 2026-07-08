package com.ktalk.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PronunciationFeedbackResponse {

    private String targetText;         // 목표 문장
    private String transcribedText;    // 사용자가 실제로 말한 것으로 인식된 문장
    private int score;                 // 0~100 발음 정확도 점수
    private String feedback;           // 총평 한 문장
    private List<String> tips;         // 구체적인 개선 팁
}
