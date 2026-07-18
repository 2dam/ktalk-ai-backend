package com.ktalk.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GuidedLearningRequest {

    @NotBlank
    private String interest;   // 학습자가 입력한 관심사 (예: "축구", "K-POP", "요리")
}
