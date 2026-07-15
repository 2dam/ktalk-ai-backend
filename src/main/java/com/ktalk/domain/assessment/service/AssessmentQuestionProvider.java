package com.ktalk.domain.assessment.service;

import com.ktalk.domain.assessment.dto.AssessmentQuestion;

import java.util.List;

// 행동심리학 기반 학습자 유형 진단 설문 20문항(5개 영역 x 4문항, 5점 리커트 척도)
public final class AssessmentQuestionProvider {

    public static final List<AssessmentQuestion> QUESTIONS = List.of(
            AssessmentQuestion.of("A1", "나는 아침에 일어나서 1시간 이내에 가장 집중이 잘 된다"),
            AssessmentQuestion.of("A2", "나는 한 번에 2시간 이상 집중해서 공부하는 것이 어렵지 않다"),
            AssessmentQuestion.of("A3", "공부할 때 외부 소음이나 주변 환경에 쉽게 방해받는 편이다"),
            AssessmentQuestion.of("A4", "나는 학습 계획을 세우고 그대로 실행하는 편이다"),

            AssessmentQuestion.of("B1", "그림, 도표, 색깔 표시 등 시각적 자료가 있으면 내용 이해가 훨씬 빠르다"),
            AssessmentQuestion.of("B2", "강의를 듣거나 설명을 들을 때 내용이 잘 기억된다"),
            AssessmentQuestion.of("B3", "직접 필기하거나 요약 정리하면서 공부하는 것이 효과적이다"),
            AssessmentQuestion.of("B4", "직접 따라 해보거나 소리 내어 말하면서 공부할 때 더 잘 외워진다"),

            AssessmentQuestion.of("C1", "나는 목표 점수(급수)가 명확할 때 훨씬 더 열심히 공부하게 된다"),
            AssessmentQuestion.of("C2", "혼자 공부할 때보다 누군가와 함께할 때 학습 의욕이 더 생긴다"),
            AssessmentQuestion.of("C3", "어려운 문제를 만나면 포기하기보다 끝까지 풀어보려고 노력한다"),
            AssessmentQuestion.of("C4", "내가 계획한 학습량을 스스로 조절하고 관리하는 데 자신이 있다"),

            AssessmentQuestion.of("D1", "나는 스마트폰 앱이나 온라인 강의를 활용하는 것이 종이 교재보다 편하다"),
            AssessmentQuestion.of("D2", "공부 중에 스마트폰 알림이 오면 바로 확인하는 습관이 있다"),
            AssessmentQuestion.of("D3", "나는 유튜브나 SNS 등에서 학습 관련 정보를 찾아 활용하는 데 능숙하다"),
            AssessmentQuestion.of("D4", "디지털 기기보다는 종이에 인쇄된 교재로 공부하는 것이 더 집중된다"),

            AssessmentQuestion.of("E1", "나는 틀린 문제를 분석하고 오답 노트를 정리하는 습관이 있다"),
            AssessmentQuestion.of("E2", "공부하기 전에 오늘 무엇을 배울지, 어떻게 공부할지 미리 구체화한다"),
            AssessmentQuestion.of("E3", "나는 자신의 취약 영역(듣기/읽기/쓰기/어휘)이 무엇인지 정확히 알고 있다"),
            AssessmentQuestion.of("E4", "공부한 내용을 주기적으로 복습하고 점검하는 시간을 반드시 가진다")
    );

    private AssessmentQuestionProvider() {
    }
}
