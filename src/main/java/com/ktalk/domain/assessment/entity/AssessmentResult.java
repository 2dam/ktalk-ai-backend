package com.ktalk.domain.assessment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "assessment_result")
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "learner_type", nullable = false)
    private LearnerType learnerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "sense_preference", nullable = false)
    private LearnerType.SensePreference sensePreference;

    @Column(name = "self_regulation_score", nullable = false)
    private double selfRegulationScore;

    @Column(name = "area_a_score", nullable = false)
    private double areaAScore;

    @Column(name = "area_b_score", nullable = false)
    private double areaBScore;

    @Column(name = "area_c_score", nullable = false)
    private double areaCScore;

    @Column(name = "area_d_score", nullable = false)
    private double areaDScore;

    @Column(name = "area_e_score", nullable = false)
    private double areaEScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
