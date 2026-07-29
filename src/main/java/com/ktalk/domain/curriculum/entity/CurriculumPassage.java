package com.ktalk.domain.curriculum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 하루 학습에 딸린 지문 하나(듣기 대화문 또는 읽기 지문). 지문 하나에 문제가 여러 개
 * (1차/2차/3차) 달릴 수 있어 problems를 리스트로 둔다. LISTENING이면 passageText가
 * 화자 표시("남자:"/"여자:")가 포함된 대화 스크립트이고, PassageAudioService가 이를
 * 음성으로 합성한다.
 */
@Entity
@Table(name = "curriculum_passage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumPassage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private CurriculumDay day;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassageCategory category;

    // "order"는 PostgreSQL 예약어라 컬럼명으로 못 써서 order_index로 매핑한다.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /** 문제 유형 라벨. 예: "이어질 대답 고르기", "문장 순서 배열". */
    private String subType;

    @Column(nullable = false, length = 2000)
    private String passageText;

    /** 시각적 몰입형 등 시각 자료가 필요한 유형을 위한 인라인 SVG 마인드맵/도표. 없으면 null. */
    @Column(name = "diagram_svg", columnDefinition = "TEXT")
    private String diagramSvg;

    @OneToMany(mappedBy = "passage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<CurriculumProblem> problems = new ArrayList<>();
}
