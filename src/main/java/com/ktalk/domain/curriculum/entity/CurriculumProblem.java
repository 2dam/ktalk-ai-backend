package com.ktalk.domain.curriculum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * 지문 하나에 딸린 문제 하나(1차/2차/3차 중 하나). optionExplanations는 options와
 * 같은 순서로, 보기 하나하나에 대한 "왜 맞고 틀렸는지" 설명을 담는다(전략적 분석가
 * 유형처럼 오답 분석을 중시하는 콘텐츠에서 특히 중요).
 */
@Entity
@Table(name = "curriculum_problem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passage_id", nullable = false)
    private CurriculumPassage passage;

    // "order"는 PostgreSQL 예약어라 컬럼명으로 못 써서 order_index로 매핑한다.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false, length = 1000)
    private String questionText;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> options;

    @Column(nullable = false)
    private int correctAnswerIndex;

    /** options와 같은 순서. 보기별 오답 분석("①: ...", "②: ..." 등)이 여기 들어간다. */
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> optionExplanations;

    /** 🧠 함정 요약. */
    @Column(length = 500)
    private String trapNote;

    /** 💡 전략적 분석 팁 — 이 유형의 문제를 어떻게 접근해야 하는지에 대한 조언. */
    @Column(length = 500)
    private String strategyTip;

    // "위 모두"처럼 다른 보기나 위치에 의미가 걸린 보기가 있으면 섞지 않는다.
    private static final Pattern POSITION_DEPENDENT = Pattern.compile(".*(모두|둘 다|위의|위 ).*");

    /**
     * 시드 콘텐츠는 정답 보기를 항상 첫 번째로 써 두었다(전체의 97%가 ①번). 그대로 보여주면 "무조건 ①번"만
     * 눌러도 맞으므로 저장 직전에 보기 순서를 섞고 정답 번호와 보기별 설명을 같은 순서로 옮긴다. 문제 내용에서
     * 결정적으로 시드를 뽑아 같은 문제는 재시딩해도 항상 같은 순서가 된다. OX/스와이프처럼 보기가 2개인
     * 문항은 순서에 의미가 있어 섞지 않는다.
     */
    @PrePersist
    void shuffleOptions() {
        if (options == null || options.size() < 3 || correctAnswerIndex < 0 || correctAnswerIndex >= options.size()) {
            return;
        }
        if (options.stream().anyMatch(option -> option != null && POSITION_DEPENDENT.matcher(option).matches())) {
            return;
        }
        int size = options.size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            order.add(i);
        }
        long seed = (questionText == null ? 0 : questionText.hashCode()) * 31L + String.valueOf(options.get(0)).hashCode();
        Collections.shuffle(order, new Random(seed));

        List<String> shuffledOptions = new ArrayList<>();
        List<String> shuffledExplanations = optionExplanations != null && optionExplanations.size() == size
                ? new ArrayList<>() : null;
        int newCorrectIndex = correctAnswerIndex;
        for (int newIndex = 0; newIndex < size; newIndex++) {
            int oldIndex = order.get(newIndex);
            shuffledOptions.add(options.get(oldIndex));
            if (shuffledExplanations != null) {
                shuffledExplanations.add(optionExplanations.get(oldIndex));
            }
            if (oldIndex == correctAnswerIndex) {
                newCorrectIndex = newIndex;
            }
        }
        options = shuffledOptions;
        if (shuffledExplanations != null) {
            optionExplanations = shuffledExplanations;
        }
        correctAnswerIndex = newCorrectIndex;
    }
}
