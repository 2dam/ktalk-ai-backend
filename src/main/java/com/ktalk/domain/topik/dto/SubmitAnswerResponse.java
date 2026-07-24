package com.ktalk.domain.topik.dto;

import com.ktalk.domain.topik.entity.TopikGroup;
import com.ktalk.domain.topik.entity.TopikLevel;

public record SubmitAnswerResponse(
        boolean correct,
        String correctAnswer,
        TopikLevel currentLevel,
        TopikGroup currentGroup,
        boolean levelChanged,
        int attemptCount,
        int correctCount
) {}
