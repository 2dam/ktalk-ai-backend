package com.ktalk.domain.block.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 태그(단어) 하나당 임베딩 벡터를 캐시한다. 같은 태그가 여러 블록에서 반복돼도
 * Gemini 임베딩 호출은 한 번만 하고, 이후엔 여기서 재사용한다.
 */
@Entity
@Table(name = "tag_embedding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String tag;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
