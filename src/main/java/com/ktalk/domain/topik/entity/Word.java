package com.ktalk.domain.topik.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * TOPIK 어휘 항목. topikLevel이 null이면 아직 난이도 분류 전 상태다
 * (TopikLevelClassifierService.classify로 채운다).
 */
@Entity
@Table(name = "topik_word")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String text;

    @Column(length = 1000)
    private String meaning;

    @Column(length = 1000)
    private String exampleSentence;

    @Enumerated(EnumType.STRING)
    private TopikLevel topikLevel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
