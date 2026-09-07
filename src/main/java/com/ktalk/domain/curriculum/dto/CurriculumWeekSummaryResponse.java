package com.ktalk.domain.curriculum.dto;

import java.util.List;

/** 커리큘럼 한 주 요약 — 기출문제집/모의고사 화면에서 주차 목록을 훑어볼 때 쓴다. */
public record CurriculumWeekSummaryResponse(
        int weekNumber,
        String title,
        String goal,
        List<DaySummaryResponse> days
) {
    public record DaySummaryResponse(int dayNumber, int dayInWeek, String task) {}
}
