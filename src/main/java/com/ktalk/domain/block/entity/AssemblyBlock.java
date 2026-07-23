package com.ktalk.domain.block.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Assembly 안에서 LearningBlock 하나가 차지하는 순서(position)와 완료 여부를 담는 조인 엔티티.
 */
@Entity
@Table(name = "assembly_block")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assembly_id", nullable = false)
    @JsonIgnore
    private Assembly assembly;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    private LearningBlock block;

    @Column(nullable = false)
    private Integer position;

    private Boolean completed = false;

    private LocalDateTime completedAt;
}
