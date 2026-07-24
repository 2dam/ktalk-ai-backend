package com.ktalk.domain.topik.dto;

import com.ktalk.domain.topik.entity.TopikGroup;
import com.ktalk.domain.topik.entity.TopikLevel;

public record ProgressResponse(
        TopikLevel topikLevel,
        TopikGroup topikGroup,
        int attemptCount,
        int correctCount,
        double accuracy
) {}
