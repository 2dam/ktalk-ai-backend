package com.ktalk.domain.curriculum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 8주 커리큘럼의 하루치 학습 항목. 주당 일수는 커리큘럼마다 다를 수 있다(예: 활동을
 * 7일에 순환 배치하는 커리큘럼도 있고, 실제 문제 세트를 요일별로 직접 배치하는
 * 커리큘럼도 있다 — CurriculumDataLoader 계열 참고). passages가 비어 있으면
 * task 문구만 있는 "활동 안내형" 날이고, 있으면 실제로 풀 수 있는 문제 세트가 딸린 날이다.
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

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<CurriculumPassage> passages = new ArrayList<>();
}
