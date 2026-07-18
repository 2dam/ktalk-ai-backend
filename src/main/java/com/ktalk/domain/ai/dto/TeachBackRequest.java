package com.ktalk.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Learning Navigation 2단계(패턴 응용): 학생이 선생님이 되어 문형 패턴을 설명하는 단계의 요청.
 */
@Getter
@NoArgsConstructor
public class TeachBackRequest {

    @NotBlank
    private String pattern;             // 원래 문형 패턴 (1단계 응답에서 그대로 전달)

    private String patternExplanation;  // 원래 패턴 설명 (참고용, 없어도 됨)

    @NotBlank
    private String studentExplanation;  // 학생이 자기 말로 설명한 내용

    private String studentExample;      // 학생이 같은 패턴으로 만든 예문 (선택)
}
