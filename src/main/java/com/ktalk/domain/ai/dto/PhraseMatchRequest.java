package com.ktalk.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhraseMatchRequest {

    @NotBlank
    private String text;           // 외국어로 입력된 문장

    private String language;       // 입력 언어 코드 (e.g. "en", "ja", "zh") - null이면 자동 감지
}
