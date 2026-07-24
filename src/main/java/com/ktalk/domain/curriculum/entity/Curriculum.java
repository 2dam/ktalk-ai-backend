package com.ktalk.domain.curriculum.entity;

import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.topik.entity.TopikLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 학습 유형 진단(LearnerType) 결과 하나당 8주(56일) 커리큘럼 하나가 대응된다.
 * 주차별 상세 내용은 CurriculumWeek, 일 단위로 쪼갠 실제 배분은 CurriculumDay가 갖는다.
 */
@Entity
@Table(name = "curriculum", uniqueConstraints = @UniqueConstraint(columnNames = "learner_type"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Curriculum {

    public static final int TOTAL_DAYS = 56;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "learner_type", nullable = false, unique = true)
    private LearnerType learnerType;

    @Column(nullable = false)
    private String title;

    @Column(name = "target_level_label", nullable = false)
    private String targetLevelLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level_from", nullable = false)
    private TopikLevel targetLevelFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_level_to", nullable = false)
    private TopikLevel targetLevelTo;

    @Column(length = 1000)
    private String usageNote;

    @OneToMany(mappedBy = "curriculum", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("weekNumber ASC")
    private List<CurriculumWeek> weeks = new ArrayList<>();
}
