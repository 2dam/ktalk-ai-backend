package com.ktalk.domain.learning.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ktalk.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "learning_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class LearningProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false)
    private String videoId;

    private String videoTitle;
    private String thumbnailUrl;
    private String channelName;

    @Enumerated(EnumType.STRING)
    private Status status = Status.NOT_STARTED;

    @CreatedDate
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @LastModifiedDate
    private LocalDateTime lastViewedAt;

    private Integer progressPercent = 0;
    private Integer watchTimeSeconds = 0;

    @OneToMany(mappedBy = "learningProgress", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Quiz> quizzes = new ArrayList<>();

    public enum Status {
        NOT_STARTED("학습 전"),
        IN_PROGRESS("학습 중"),
        COMPLETED("학습 완료");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 비즈니스 메서드
    public void startLearning() {
        this.status = Status.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
        this.lastViewedAt = LocalDateTime.now();
    }

    // 🔥 수정된 부분: 진행률 계산 추가
    public void updateProgress(int watchTimeSeconds, int totalVideoSeconds) {
        // 1. 시청 시간 저장
        this.watchTimeSeconds = watchTimeSeconds;

        // 2. 진행률 계산 (퍼센트)
        this.progressPercent = Math.min(100, (watchTimeSeconds * 100) / totalVideoSeconds);

        // 3. 마지막 시청 시간 업데이트
        this.lastViewedAt = LocalDateTime.now();

        // 4. 상태 변경
        if (this.progressPercent >= 100) {
            completeLearning(); // 완료 처리
        } else if (this.status == Status.NOT_STARTED) {
            this.status = Status.IN_PROGRESS; // 진행 중으로 변경
        }
    }

    public void completeLearning() {
        this.status = Status.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.progressPercent = 100;
    }
}