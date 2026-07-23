package com.ktalk.domain.block.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 재사용 가능한 학습 콘텐츠 최소 단위 ("레고블록").
 * inputTags/outputTags로 다른 블록과의 조립 가능 여부를 표현한다 (Phase 2 자동 조립용 메타데이터).
 */
@Entity
@Table(name = "learning_block")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LearningBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "korean_level", nullable = false)
    private String koreanLevel;

    @Column(name = "interest_tag")
    private String interestTag;

    @ElementCollection
    @CollectionTable(name = "learning_block_input_tags", joinColumns = @JoinColumn(name = "block_id"))
    @Column(name = "tag")
    private List<String> inputTags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "learning_block_output_tags", joinColumns = @JoinColumn(name = "block_id"))
    @Column(name = "tag")
    private List<String> outputTags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockSource source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
