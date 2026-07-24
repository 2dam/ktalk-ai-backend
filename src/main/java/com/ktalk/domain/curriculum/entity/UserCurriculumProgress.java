package com.ktalk.domain.curriculum.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ktalk.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자별 커리큘럼 진행 상태. 사용자 하나당 활성 커리큘럼 하나만 둔다.
 */
@Entity
@Table(name = "user_curriculum_progress", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCurriculumProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Column(nullable = false)
    private int currentDay = 1;

    @Column(nullable = false)
    private int completedDayCount = 0;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime lastCompletedAt;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public boolean isFinished() {
        return completedDayCount >= Curriculum.TOTAL_DAYS;
    }

    /** 오늘 학습을 완료 처리하고 다음 날로 넘어간다. 이미 끝났으면 아무것도 하지 않는다. */
    public void completeToday() {
        if (isFinished()) {
            return;
        }
        completedDayCount += 1;
        lastCompletedAt = LocalDateTime.now();
        if (currentDay < Curriculum.TOTAL_DAYS) {
            currentDay += 1;
        }
    }
}
