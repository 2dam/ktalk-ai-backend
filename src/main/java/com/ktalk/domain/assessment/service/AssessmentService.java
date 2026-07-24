package com.ktalk.domain.assessment.service;

import com.ktalk.domain.assessment.dto.AssessmentAnswer;
import com.ktalk.domain.assessment.dto.AssessmentResultResponse;
import com.ktalk.domain.assessment.entity.AssessmentResult;
import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.assessment.repository.AssessmentResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final double SELF_DIRECTED_THRESHOLD = 3.5;

    private final AssessmentResultRepository resultRepository;

    @Transactional
    public AssessmentResultResponse submit(Long userId, List<AssessmentAnswer> answers) {
        Map<String, Integer> scores = answers.stream()
                .collect(java.util.stream.Collectors.toMap(AssessmentAnswer::code, AssessmentAnswer::score));

        Map<String, Double> areaScores = Map.of(
                "A", areaAverage(scores, "A"),
                "B", areaAverage(scores, "B"),
                "C", areaAverage(scores, "C"),
                "D", areaAverage(scores, "D"),
                "E", areaAverage(scores, "E")
        );

        double selfRegulationScore = average(scores, "A4", "C4", "E2", "E4");
        boolean selfDirected = selfRegulationScore >= SELF_DIRECTED_THRESHOLD;

        LearnerType.SensePreference sense = determineSensePreference(scores);
        LearnerType learnerType = LearnerType.classify(selfDirected, sense);

        AssessmentResult result = new AssessmentResult();
        result.setUserId(userId);
        result.setLearnerType(learnerType);
        result.setSensePreference(sense);
        result.setSelfRegulationScore(selfRegulationScore);
        result.setAreaAScore(areaScores.get("A"));
        result.setAreaBScore(areaScores.get("B"));
        result.setAreaCScore(areaScores.get("C"));
        result.setAreaDScore(areaScores.get("D"));
        result.setAreaEScore(areaScores.get("E"));
        resultRepository.save(result);

        return AssessmentResultResponse.of(learnerType, areaScores, selfRegulationScore, sense, result.getCreatedAt());
    }

    /**
     * 20문항 설문 없이 학습 유형을 바로 지정한다. 커리큘럼 콘텐츠를 테스트할 때 진단을
     * 반복해서 풀지 않아도 되도록 만든 지름길이다 — 점수 필드는 중립값으로 채운다.
     */
    @Transactional
    public AssessmentResultResponse quickAssign(Long userId, LearnerType learnerType) {
        AssessmentResult result = new AssessmentResult();
        result.setUserId(userId);
        result.setLearnerType(learnerType);
        result.setSensePreference(LearnerType.SensePreference.MIXED);
        result.setSelfRegulationScore(3.5);
        result.setAreaAScore(3.0);
        result.setAreaBScore(3.0);
        result.setAreaCScore(3.0);
        result.setAreaDScore(3.0);
        result.setAreaEScore(3.0);
        resultRepository.save(result);

        Map<String, Double> areaScores = Map.of("A", 3.0, "B", 3.0, "C", 3.0, "D", 3.0, "E", 3.0);
        return AssessmentResultResponse.of(learnerType, areaScores, 3.5,
                LearnerType.SensePreference.MIXED, result.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public AssessmentResultResponse getLatestResult(Long userId) {
        AssessmentResult result = resultRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new RuntimeException("아직 진단 결과가 없습니다."));

        Map<String, Double> areaScores = Map.of(
                "A", result.getAreaAScore(),
                "B", result.getAreaBScore(),
                "C", result.getAreaCScore(),
                "D", result.getAreaDScore(),
                "E", result.getAreaEScore()
        );

        return AssessmentResultResponse.of(result.getLearnerType(), areaScores, result.getSelfRegulationScore(),
                result.getSensePreference(), result.getCreatedAt());
    }

    private double areaAverage(Map<String, Integer> scores, String area) {
        return average(scores, area + "1", area + "2", area + "3", area + "4");
    }

    private double average(Map<String, Integer> scores, String... codes) {
        return java.util.Arrays.stream(codes)
                .mapToInt(code -> requireScore(scores, code))
                .average()
                .orElseThrow();
    }

    private int requireScore(Map<String, Integer> scores, String code) {
        Integer score = scores.get(code);
        if (score == null) {
            throw new RuntimeException("문항 " + code + "에 대한 응답이 없습니다.");
        }
        return score;
    }

    // VARK 감각 선호도: B1(시각)/B2(청각)/B3(읽기·쓰기)/B4(운동감각) 중 최고점 영역.
    // 최고점이 둘 이상이면(동점) 혼합(MIXED)으로 분류한다.
    private LearnerType.SensePreference determineSensePreference(Map<String, Integer> scores) {
        Map<LearnerType.SensePreference, Integer> senseScores = Map.of(
                LearnerType.SensePreference.VISUAL, requireScore(scores, "B1"),
                LearnerType.SensePreference.AUDITORY, requireScore(scores, "B2"),
                LearnerType.SensePreference.READ_WRITE, requireScore(scores, "B3"),
                LearnerType.SensePreference.KINESTHETIC, requireScore(scores, "B4")
        );

        int max = senseScores.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        List<LearnerType.SensePreference> top = senseScores.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();

        return top.size() == 1 ? top.get(0) : LearnerType.SensePreference.MIXED;
    }
}
