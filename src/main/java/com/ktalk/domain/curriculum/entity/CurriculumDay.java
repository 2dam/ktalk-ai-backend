package com.ktalk.domain.curriculum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 8주 커리큘럼을 56일로 고정 분배한 하루치 학습 항목. 한 주의 activities를 7일에
 * 걸쳐 순환 배치해서 만든다(CurriculumDataLoader 참고) — 원고에 일 단위 구분이
 * 없어서, 매일 학습량을 균등하게 나누는 기계적 배분이다.
 */
@Entity
@Table(name = "curriculum_day")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id", nullable = false)
    private CurriculumWeek week;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "day_in_week", nullable = false)
    private int dayInWeek;

    @Column(nullable = false, length = 1000)
    private String task;
}
