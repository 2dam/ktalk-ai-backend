package com.ktalk.domain.topik.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 단어 하나로 만든 객관식 문항. attemptCount/correctCount는 이 문항이 실제로 얼마나
 * 어려운지(성공률)를 추정하는 데 쓰이고, AdaptiveQuizService가 이 성공률을 기준으로
 * 다음 출제 문항을 고른다.
 */
@Entity
@Table(name = "topik_quiz_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(nullable = false, length = 1000)
    private String question;

    @ElementCollection
    @CollectionTable(name = "topik_quiz_item_options", joinColumns = @JoinColumn(name = "quiz_item_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "option_text", length = 1000)
    private List<String> options;

    @Column(nullable = false)
    private int correctAnswerIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopikLevel topikLevel;

    @Column(nullable = false)
    private int attemptCount = 0;

    @Column(nullable = false)
    private int correctCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** 아직 아무도 안 풀었으면 0.5(정보 없음)로 취급한다. */
    public double successRate() {
        return attemptCount == 0 ? 0.5 : (double) correctCount / attemptCount;
    }

    public void recordAttempt(boolean correct) {
        attemptCount += 1;
        if (correct) {
            correctCount += 1;
        }
    }
}
