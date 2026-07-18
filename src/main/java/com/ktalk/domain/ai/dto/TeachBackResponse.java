package com.ktalk.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeachBackResponse {

    private String feedback;    // 학생의 설명/예문에 대한 따뜻하고 구체적인 선생님 피드백
    private boolean patternUnderstood;   // 학생이 패턴을 제대로 이해했다고 판단되는지
}
