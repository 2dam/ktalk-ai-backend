package com.ktalk.domain.curriculum.dto;

import com.ktalk.domain.curriculum.entity.CurriculumPassage;
import com.ktalk.domain.curriculum.entity.CurriculumProblem;
import com.ktalk.domain.curriculum.entity.PassageCategory;
import com.ktalk.domain.curriculum.entity.UserWrongAnswer;

import java.time.LocalDateTime;
import java.util.List;

/** 오답노트 한 줄 — 틀렸던 문제를 지문·정답·해설과 함께 다시 볼 수 있게 담는다. */
public record WrongAnswerResponse(
        String problemId,
        PassageCategory passageCategory,
        String passageSubType,
        String passageText,
        String questionText,
        List<String> options,
        int selectedIndex,
        int correctAnswerIndex,
        List<String> optionExplanations,
        String trapNote,
        String strategyTip,
        LocalDateTime createdAt
) {
    public static WrongAnswerResponse from(UserWrongAnswer wrongAnswer) {
        CurriculumProblem problem = wrongAnswer.getProblem();
        CurriculumPassage passage = problem.getPassage();
        return new WrongAnswerResponse(
                problem.getId(),
                passage.getCategory(),
                passage.getSubType(),
                passage.getPassageText(),
                problem.getQuestionText(),
                problem.getOptions(),
                wrongAnswer.getSelectedIndex(),
                problem.getCorrectAnswerIndex(),
                problem.getOptionExplanations(),
                problem.getTrapNote(),
                problem.getStrategyTip(),
                wrongAnswer.getCreatedAt()
        );
    }
}
