package com.ktalk.domain.block.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ktalk.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 여러 LearningBlock을 순서대로 엮은 학습 결과물 (관심사 하나로 진행하는 Learning Navigation 한 세션).
 * 지금 프런트에 하드코딩된 STAGES 순서를 데이터로 표현한다.
 */
@Entity
@Table(name = "assembly")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Assembly {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(name = "interest_tag", nullable = false)
    private String interestTag;

    @Column(name = "korean_level", nullable = false)
    private String koreanLevel;

    @Enumerated(EnumType.STRING)
    private AssemblyStatus status = AssemblyStatus.IN_PROGRESS;

    @OneToMany(mappedBy = "assembly", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<AssemblyBlock> blocks = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
