package com.ktalk.domain.curriculum.dto;

import java.util.List;

/**
 * 문제지/정답·해설지 인쇄용 데이터. 일반 학습 화면(CurriculumDayResponse)은 풀기 전에는 정답을
 * 숨기지만, 인쇄 화면은 정답·해설지를 같이 만들어야 하므로 정답과 해설을 함께 내려준다.
 */
public record PrintableSetResponse(
        String curriculumTitle,
        String learnerTypeLabel,
        String levelLabel,
        String title,
        List<PrintableDay> days
) {
    public record PrintableDay(int dayNumber, String task, List<PrintablePassage> passages) {}

    public record PrintablePassage(
            String category,
            String subType,
            String passageText,
            List<PrintableProblem> problems
    ) {}

    public record PrintableProblem(
            String questionText,
            List<String> options,
            int correctAnswerIndex,
            List<String> optionExplanations,
            String trapNote,
            String strategyTip
    ) {}
}
