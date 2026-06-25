package com.ktalk.domain.learning.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_progress_id", nullable = false)
    @JsonIgnore
    private LearningProgress learningProgress;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @ElementCollection
    @CollectionTable(name = "quiz_options", joinColumns = @JoinColumn(name = "quiz_id"))
    @Column(name = "option_text")
    private List<String> options = new ArrayList<>();

    private Integer correctAnswerIndex;

    private Boolean answeredCorrectly;
    private LocalDateTime answeredAt;

    // 정답 확인 메서드
    public boolean checkAnswer(int selectedIndex) {
        boolean correct = selectedIndex == correctAnswerIndex;
        this.answeredCorrectly = correct;
        this.answeredAt = LocalDateTime.now();
        return correct;
    }
}