package com.ktalk.domain.curriculum.dto;

import java.util.List;

public record ProblemAnswerResponse(
        boolean correct,
        int correctAnswerIndex,
        List<String> optionExplanations,
        String trapNote,
        String strategyTip
) {}
