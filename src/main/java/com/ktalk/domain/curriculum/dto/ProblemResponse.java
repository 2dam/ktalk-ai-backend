package com.ktalk.domain.curriculum.dto;

import com.ktalk.domain.curriculum.entity.CurriculumProblem;

import java.util.List;

/** 채점 전까지 정답 인덱스와 해설은 내려주지 않는다. */
public record ProblemResponse(String id, int order, String questionText, List<String> options) {
    public static ProblemResponse from(CurriculumProblem problem) {
        return new ProblemResponse(problem.getId(), problem.getOrderIndex(), problem.getQuestionText(), problem.getOptions());
    }
}
