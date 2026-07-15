package com.ktalk.domain.assessment.dto;

import com.ktalk.domain.assessment.entity.LearnerType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AssessmentResultResponse(
        String learnerType,
        String label,
        String description,
        List<String> textbooks,
        List<String> courses,
        List<String> apps,
        String studyTip,
        List<LearnerType.CurriculumStage> curriculum,
        Map<String, Double> areaScores,
        double selfRegulationScore,
        String sensePreference,
        LocalDateTime createdAt
) {
    public static AssessmentResultResponse of(LearnerType type, Map<String, Double> areaScores,
                                               double selfRegulationScore, LearnerType.SensePreference sense,
                                               LocalDateTime createdAt) {
        return new AssessmentResultResponse(
                type.name(),
                type.getLabel(),
                type.getDescription(),
                type.getTextbooks(),
                type.getCourses(),
                type.getApps(),
                type.getStudyTip(),
                type.getCurriculum(),
                areaScores,
                selfRegulationScore,
                sense.name(),
                createdAt
        );
    }
}
