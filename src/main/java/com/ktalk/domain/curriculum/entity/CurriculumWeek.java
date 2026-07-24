package com.ktalk.domain.curriculum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 커리큘럼 한 주 분량. activities는 그 주에 해야 할 일 목록(원고의 "시각화 전략"/
 * "체험형 활동" 칸)이고, template은 그 주에 딸린 학습지 샘플을 통 텍스트로 담는다
 * (개별 필드로 잘게 쪼개지 않고 원문 형태를 그대로 보여주기 위함).
 */
@Entity
@Table(name = "curriculum_week")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Column(nullable = false)
    private int weekNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String goal;

    @ElementCollection
    @CollectionTable(name = "curriculum_week_activity", joinColumns = @JoinColumn(name = "week_id"))
    @OrderColumn(name = "activity_index")
    @Column(name = "activity", length = 1000)
    private List<String> activities;

    @Column(length = 4000)
    private String template;
}
