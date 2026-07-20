package com.ktalk.domain.review.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ktalk.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 구독자가 Learning Navigation에서 배운 문장을 "복습 알람"으로 다시 만나기 위해
 * 저장해 두는 항목이다.
 *
 * <p>복습 간격은 단순 랜덤이 아니라 SM-2(SuperMemo-2) 간격 반복 알고리즘으로 정한다.
 * 사용자가 복습 결과를 0~5점(quality)으로 평가하면 다음 복습 시각(nextReviewAt)이
 * 자동으로 계산되어, 잘 아는 문장은 점점 긴 간격으로, 헷갈리는 문장은 짧은 간격으로
 * 다시 알람이 뜬다. (에빙하우스 망각곡선 기반)
 */
@Entity
@Table(
        name = "review_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "sentence"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    // ----- 배운 내용 (알람으로 보낼 문장/뜻) -----
    private String interest;

    @Column(nullable = false, length = 1000)
    private String sentence;

    @Column(length = 1000)
    private String meaning;

    @Column(length = 1000)
    private String pattern;

    private String sensoryWord;

    // ----- SM-2 상태값 -----
    /** 연속으로 성공(quality>=3)한 횟수. 실패하면 0으로 초기화된다. */
    @Column(nullable = false)
    private int repetitions = 0;

    /** 현재 복습 간격(일). 0이면 아직 첫 복습 전 또는 방금 실패한 상태. */
    @Column(nullable = false)
    private int intervalDays = 0;

    /** 난이도 계수(easiness factor). 최소 1.3. 값이 클수록 간격이 빨리 늘어난다. */
    @Column(nullable = false)
    private double easeFactor = 2.5;

    /** 다음 복습(알람) 예정 시각. 이 값이 현재보다 과거이면 "지금 복습할 항목"이다. */
    @Column(nullable = false)
    private LocalDateTime nextReviewAt;

    private LocalDateTime lastReviewedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private int reviewCount = 0;

    @Column(nullable = false)
    private int correctCount = 0;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (nextReviewAt == null) {
            // 방금 배운 문장은 하루 뒤 첫 복습을 예약한다(첫 간격 = 1일).
            nextReviewAt = createdAt.plusDays(1);
        }
    }

    /**
     * SM-2 알고리즘으로 복습 결과를 반영하고 다음 복습 시각을 갱신한다.
     *
     * @param quality 0~5 (0~2: 실패/다시, 3: 어렵게 성공, 4: 무난, 5: 아주 쉬움)
     */
    public void applyReview(int quality) {
        int q = Math.max(0, Math.min(5, quality));
        LocalDateTime now = LocalDateTime.now();

        if (q < 3) {
            // 실패: 처음부터 다시 학습. 세션 안에서 곧 다시 뜨도록 10분 뒤로.
            repetitions = 0;
            intervalDays = 0;
            nextReviewAt = now.plusMinutes(10);
        } else {
            if (repetitions == 0) {
                intervalDays = 1;
            } else if (repetitions == 1) {
                intervalDays = 6;
            } else {
                intervalDays = (int) Math.round(intervalDays * easeFactor);
            }
            repetitions += 1;
            nextReviewAt = now.plusDays(intervalDays);
        }

        // easeFactor 갱신 (SM-2 표준 공식), 최소 1.3으로 하한
        double updated = easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));
        easeFactor = Math.max(1.3, updated);

        lastReviewedAt = now;
        reviewCount += 1;
        if (q >= 3) {
            correctCount += 1;
        }
    }
}
