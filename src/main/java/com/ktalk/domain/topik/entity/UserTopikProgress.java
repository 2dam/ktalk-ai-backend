package com.ktalk.domain.topik.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ktalk.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자별 TOPIK 실력 추정치. 문제를 최소 표본(MIN_ATTEMPTS_BEFORE_LEVEL_CHANGE)만큼 풀고 나면
 * 최근 정답률로 등급을 올리거나 내리고, 판단에 쓴 시도 횟수는 초기화한다(SM-2의 반복 초기화와
 * 같은 원리 — 옛날 정답률이 새 등급 판단을 계속 흐리지 않도록).
 */
@Entity
@Table(name = "topik_user_progress", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserTopikProgress {

    private static final int MIN_ATTEMPTS_BEFORE_LEVEL_CHANGE = 5;
    private static final double LEVEL_UP_THRESHOLD = 0.8;
    private static final double LEVEL_DOWN_THRESHOLD = 0.4;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopikLevel topikLevel = TopikLevel.LEVEL_1;

    @Column(nullable = false)
    private int attemptCount = 0;

    @Column(nullable = false)
    private int correctCount = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = LocalDateTime.now();
    }

    public double accuracy() {
        return attemptCount == 0 ? 0.0 : (double) correctCount / attemptCount;
    }

    /**
     * 정오답을 반영하고, 표본이 충분히 쌓이면 등급을 조정한다.
     *
     * @return 이번 호출로 등급이 바뀌었으면 true
     */
    public boolean recordAnswer(boolean correct) {
        attemptCount += 1;
        if (correct) {
            correctCount += 1;
        }

        if (attemptCount < MIN_ATTEMPTS_BEFORE_LEVEL_CHANGE) {
            return false;
        }

        double acc = accuracy();
        if (acc >= LEVEL_UP_THRESHOLD && topikLevel != TopikLevel.LEVEL_6) {
            topikLevel = topikLevel.up();
            resetWindow();
            return true;
        }
        if (acc <= LEVEL_DOWN_THRESHOLD && topikLevel != TopikLevel.LEVEL_1) {
            topikLevel = topikLevel.down();
            resetWindow();
            return true;
        }
        return false;
    }

    private void resetWindow() {
        attemptCount = 0;
        correctCount = 0;
    }
}
