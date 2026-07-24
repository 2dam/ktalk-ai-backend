package com.ktalk.domain.curriculum.service;

import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.assessment.repository.AssessmentResultRepository;
import com.ktalk.domain.curriculum.dto.CurriculumDayResponse;
import com.ktalk.domain.curriculum.entity.Curriculum;
import com.ktalk.domain.curriculum.entity.CurriculumDay;
import com.ktalk.domain.curriculum.entity.UserCurriculumProgress;
import com.ktalk.domain.curriculum.repository.CurriculumDayRepository;
import com.ktalk.domain.curriculum.repository.CurriculumRepository;
import com.ktalk.domain.curriculum.repository.UserCurriculumProgressRepository;
import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.Word;
import com.ktalk.domain.topik.repository.WordRepository;
import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 학습 유형 진단(AssessmentResult.learnerType) 결과에 맞는 8주(56일) 커리큘럼을
 * 하루 단위로 내려주고 진행 상황을 추적한다. 사용자가 처음 오늘의 학습을 조회하는
 * 순간, 가장 최근 진단 결과로 커리큘럼을 배정한다(진단을 안 했으면 안내 메시지).
 */
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private static final int RECOMMENDED_WORD_LIMIT = 8;

    private final CurriculumRepository curriculumRepository;
    private final CurriculumDayRepository curriculumDayRepository;
    private final UserCurriculumProgressRepository progressRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final UserRepository userRepository;
    private final WordRepository wordRepository;

    @Transactional
    public CurriculumDayResponse getToday(Long userId) {
        UserCurriculumProgress progress = getOrAssignProgress(userId);
        return buildResponse(progress);
    }

    @Transactional
    public CurriculumDayResponse completeToday(Long userId) {
        UserCurriculumProgress progress = progressRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("아직 배정된 커리큘럼이 없습니다. 먼저 오늘의 학습을 조회하세요."));
        progress.completeToday();
        progressRepository.save(progress);
        return buildResponse(progress);
    }

    private UserCurriculumProgress getOrAssignProgress(Long userId) {
        return progressRepository.findByUserId(userId).orElseGet(() -> assignCurriculum(userId));
    }

    private UserCurriculumProgress assignCurriculum(Long userId) {
        LearnerType learnerType = assessmentResultRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new IllegalStateException("먼저 학습 유형 진단을 완료해주세요."))
                .getLearnerType();

        Curriculum curriculum = curriculumRepository.findByLearnerType(learnerType)
                .orElseThrow(() -> new IllegalStateException(
                        learnerType.getLabel() + " 유형의 상세 커리큘럼은 아직 준비 중이에요."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));

        UserCurriculumProgress progress = new UserCurriculumProgress();
        progress.setUser(user);
        progress.setCurriculum(curriculum);
        progress.setCurrentDay(1);
        return progressRepository.save(progress);
    }

    private CurriculumDayResponse buildResponse(UserCurriculumProgress progress) {
        Curriculum curriculum = progress.getCurriculum();
        if (progress.isFinished()) {
            return finishedResponse(curriculum, progress);
        }

        CurriculumDay day = curriculumDayRepository
                .findByCurriculumIdAndDayNumber(curriculum.getId(), progress.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("커리큘럼 데이터가 손상됐습니다: day " + progress.getCurrentDay()));

        return new CurriculumDayResponse(
                curriculum.getId(),
                curriculum.getTitle(),
                curriculum.getLearnerType().getLabel(),
                day.getWeek().getWeekNumber(),
                day.getWeek().getTitle(),
                day.getWeek().getGoal(),
                day.getWeek().getTemplate(),
                day.getDayNumber(),
                day.getDayInWeek(),
                day.getTask(),
                Curriculum.TOTAL_DAYS,
                progress.getCompletedDayCount(),
                false,
                recommendedWords(curriculum)
        );
    }

    private CurriculumDayResponse finishedResponse(Curriculum curriculum, UserCurriculumProgress progress) {
        return new CurriculumDayResponse(
                curriculum.getId(),
                curriculum.getTitle(),
                curriculum.getLearnerType().getLabel(),
                0,
                "완주",
                "",
                null,
                Curriculum.TOTAL_DAYS,
                7,
                "8주 커리큘럼을 모두 완료했어요! 수고하셨습니다.",
                Curriculum.TOTAL_DAYS,
                progress.getCompletedDayCount(),
                true,
                List.of()
        );
    }

    private List<CurriculumDayResponse.RecommendedWordResponse> recommendedWords(Curriculum curriculum) {
        List<TopikLevel> levels = Arrays.stream(TopikLevel.values())
                .filter(level -> level.getGrade() >= curriculum.getTargetLevelFrom().getGrade()
                        && level.getGrade() <= curriculum.getTargetLevelTo().getGrade())
                .toList();

        return wordRepository.findByTopikLevelIn(levels).stream()
                .limit(RECOMMENDED_WORD_LIMIT)
                .map(word -> new CurriculumDayResponse.RecommendedWordResponse(word.getText(), word.getMeaning()))
                .toList();
    }
}
