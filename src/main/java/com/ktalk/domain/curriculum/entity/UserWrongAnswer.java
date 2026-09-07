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
 * 사용자가 틀린 문제를 오답노트용으로 기록한다. 문제 하나당 사용자별로 최대 한 행만
 * 남기고(같은 문제를 다시 틀리면 selectedIndex/createdAt만 갱신), 정답을 다시 맞히면
 * 해당 행을 삭제해 "복습 완료"로 처리한다(CurriculumService 참고).
 */
@Entity
@Table(name = "user_wrong_answer", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "problem_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserWrongAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private CurriculumProblem problem;

    @Column(nullable = false)
    private int selectedIndex;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
