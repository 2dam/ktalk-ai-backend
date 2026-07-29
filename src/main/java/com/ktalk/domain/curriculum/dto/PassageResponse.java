package com.ktalk.domain.curriculum.dto;

import com.ktalk.domain.curriculum.entity.CurriculumPassage;
import com.ktalk.domain.curriculum.entity.PassageCategory;

import java.util.List;

public record PassageResponse(
        String id,
        PassageCategory category,
        String subType,
        String passageText,
        boolean hasAudio,
        String diagramSvg,
        List<ProblemResponse> problems
) {
    public static PassageResponse from(CurriculumPassage passage) {
        return new PassageResponse(
                passage.getId(),
                passage.getCategory(),
                passage.getSubType(),
                passage.getPassageText(),
                passage.getCategory() == PassageCategory.LISTENING,
                passage.getDiagramSvg(),
                passage.getProblems().stream().map(ProblemResponse::from).toList()
        );
    }
}
