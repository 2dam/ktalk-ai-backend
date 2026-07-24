package com.ktalk.domain.curriculum.dto;

import java.util.List;

public record CurriculumDayResponse(
        String curriculumId,
        String curriculumTitle,
        String learnerTypeLabel,
        int weekNumber,
        String weekTitle,
        String weekGoal,
        String template,
        int dayNumber,
        int dayInWeek,
        String task,
        int totalDays,
        int completedDayCount,
        boolean finished,
        List<RecommendedWordResponse> recommendedWords
) {
    public record RecommendedWordResponse(String text, String meaning) {}
}
