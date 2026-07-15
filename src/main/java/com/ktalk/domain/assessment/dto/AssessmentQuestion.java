package com.ktalk.domain.assessment.dto;

// code 예: "A1" ~ "E4". 앞 글자가 영역(A~E)을 나타낸다.
public record AssessmentQuestion(String code, String area, String text) {

    public static AssessmentQuestion of(String code, String text) {
        return new AssessmentQuestion(code, code.substring(0, 1), text);
    }
}
