package com.ktalk.domain.curriculum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 지문 하나에 딸린 문제 하나(1차/2차/3차 중 하나). optionExplanations는 options와
 * 같은 순서로, 보기 하나하나에 대한 "왜 맞고 틀렸는지" 설명을 담는다(전략적 분석가
 * 유형처럼 오답 분석을 중시하는 콘텐츠에서 특히 중요).
 */
@Entity
@Table(name = "curriculum_problem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passage_id", nullable = false)
    private CurriculumPassage passage;

    // "order"는 PostgreSQL 예약어라 컬럼명으로 못 써서 order_index로 매핑한다.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false, length = 1000)
    private String questionText;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> options;

    @Column(nullable = false)
    private int correctAnswerIndex;

    /** options와 같은 순서. 보기별 오답 분석("①: ...", "②: ..." 등)이 여기 들어간다. */
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> optionExplanations;

    /** 🧠 함정 요약. */
    @Column(length = 500)
    private String trapNote;

    /** 💡 전략적 분석 팁 — 이 유형의 문제를 어떻게 접근해야 하는지에 대한 조언. */
    @Column(length = 500)
    private String strategyTip;
}
