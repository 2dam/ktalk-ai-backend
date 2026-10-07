package com.ktalk.domain.curriculum.service;

import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.assessment.repository.AssessmentResultRepository;
import com.ktalk.domain.curriculum.dto.CurriculumDayResponse;
import com.ktalk.domain.curriculum.dto.CurriculumWeekSummaryResponse;
import com.ktalk.domain.curriculum.dto.PassageResponse;
import com.ktalk.domain.curriculum.dto.PrintableSetResponse;
import com.ktalk.domain.curriculum.dto.ProblemAnswerResponse;
import com.ktalk.domain.curriculum.dto.WrongAnswerResponse;
import com.ktalk.domain.curriculum.entity.Curriculum;
import com.ktalk.domain.curriculum.entity.CurriculumDay;
import com.ktalk.domain.curriculum.entity.CurriculumProblem;
import com.ktalk.domain.curriculum.entity.UserCurriculumProgress;
import com.ktalk.domain.curriculum.entity.UserWrongAnswer;
import com.ktalk.domain.curriculum.repository.CurriculumDayRepository;
import com.ktalk.domain.curriculum.repository.CurriculumProblemRepository;
import com.ktalk.domain.curriculum.repository.CurriculumRepository;
import com.ktalk.domain.curriculum.repository.CurriculumWeekRepository;
import com.ktalk.domain.curriculum.repository.UserCurriculumProgressRepository;
import com.ktalk.domain.curriculum.repository.UserWrongAnswerRepository;
import com.ktalk.domain.topik.entity.TopikGroup;
import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.Word;
import com.ktalk.domain.topik.repository.UserTopikProgressRepository;
import com.ktalk.domain.topik.repository.WordRepository;
import com.ktalk.domain.user.entity.User;
import com.ktalk.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 학습 유형 진단(AssessmentResult.learnerType) 결과에 맞는 8주 커리큘럼을 하루
 * 단위로 내려주고 진행 상황을 추적한다. 사용자가 처음 오늘의 학습을 조회하는
 * 순간, 가장 최근 진단 결과로 커리큘럼을 배정한다(진단을 안 했으면 안내 메시지).
 */
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private static final int RECOMMENDED_WORD_LIMIT = 8;

    private final CurriculumRepository curriculumRepository;
    private final CurriculumDayRepository curriculumDayRepository;
    private final CurriculumProblemRepository curriculumProblemRepository;
    private final UserCurriculumProgressRepository progressRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final UserRepository userRepository;
    private final WordRepository wordRepository;
    private final UserWrongAnswerRepository wrongAnswerRepository;
    private final UserTopikProgressRepository topikProgressRepository;
    private final CurriculumWeekRepository curriculumWeekRepository;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public CurriculumDayResponse getToday(Long userId) {
        UserCurriculumProgress progress = getOrAssignProgress(userId);
        return buildResponse(progress);
    }

    @Transactional
    public CurriculumDayResponse completeToday(Long userId) {
        UserCurriculumProgress progress = progressRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("아직 배정된 커리큘럼이 없습니다. 먼저 오늘의 학습을 조회하세요."));
        int totalDays = totalDays(progress.getCurriculum());
        progress.completeToday(totalDays);
        progressRepository.save(progress);
        return buildResponse(progress);
    }

    /** 배정된 커리큘럼의 주차 목록(주차별 제목/목표 + 그 주의 일자 목록)을 훑어본다.
     * "오늘의 학습" 진행 상태와는 무관하게 아무 주/일이나 미리 볼 수 있다
     * (기출문제집·모의고사 화면이 여기서 원하는 주를 골라 peekDay로 상세를 연다). */
    @Transactional(readOnly = true)
    public List<CurriculumWeekSummaryResponse> getWeeks(Long userId) {
        UserCurriculumProgress progress = getOrAssignProgress(userId);
        Curriculum curriculum = progress.getCurriculum();

        Map<String, List<CurriculumDay>> daysByWeekId = curriculumDayRepository
                .findByCurriculumId(curriculum.getId()).stream()
                .collect(Collectors.groupingBy(day -> day.getWeek().getId()));

        // curriculum.getWeeks()는 지연 로딩 컬렉션이라, progress가 방금 배정됐다면(첫 접근 —
        // getOrAssignProgress가 별도 트랜잭션에서 커리큘럼을 새로 배정한 직후) 그 커리큘럼은
        // 이미 닫힌 트랜잭션의 세션에 묶여 있어 여기서 접근하면 LazyInitializationException이
        // 난다. 저장소로 직접 조회해 현재 세션에서 완전히 로딩된 리스트를 쓴다.
        return curriculumWeekRepository.findByCurriculumIdOrderByWeekNumberAsc(curriculum.getId()).stream()
                .map(week -> new CurriculumWeekSummaryResponse(
                        week.getWeekNumber(),
                        week.getTitle(),
                        week.getGoal(),
                        daysByWeekId.getOrDefault(week.getId(), List.of()).stream()
                                .sorted(Comparator.comparingInt(CurriculumDay::getDayInWeek))
                                .map(day -> new CurriculumWeekSummaryResponse.DaySummaryResponse(
                                        day.getDayNumber(), day.getDayInWeek(), day.getTask()))
                                .toList()
                ))
                .toList();
    }

    /** 특정 일자의 학습 내용을 "오늘의 학습" 진행과 무관하게 미리 본다(기출문제집/모의고사용). */
    @Transactional(readOnly = true)
    public CurriculumDayResponse peekDay(Long userId, int dayNumber) {
        UserCurriculumProgress progress = getOrAssignProgress(userId);
        Curriculum curriculum = progress.getCurriculum();
        CurriculumDay day = curriculumDayRepository.findByCurriculumIdAndDayNumber(curriculum.getId(), dayNumber)
                .orElseThrow(() -> new IllegalArgumentException("해당 학습 내용을 찾을 수 없습니다: " + dayNumber + "일째"));

        return toResponse(curriculum, progress, day, false);
    }

    /** 인쇄용 문제지/정답·해설지 데이터. weekNumber가 있으면 그 주차 전체, dayNumber가 있으면 그 하루치를
     * 정답·해설까지 포함해 내려준다(둘 중 정확히 하나만 지정). */
    @Transactional(readOnly = true)
    public PrintableSetResponse getPrintable(Long userId, Integer weekNumber, Integer dayNumber) {
        if ((weekNumber == null) == (dayNumber == null)) {
            throw new IllegalArgumentException("week 또는 day 중 하나만 지정해주세요.");
        }
        UserCurriculumProgress progress = getOrAssignProgress(userId);
        Curriculum curriculum = progress.getCurriculum();

        List<CurriculumDay> days;
        String title;
        if (weekNumber != null) {
            var week = curriculumWeekRepository.findByCurriculumIdOrderByWeekNumberAsc(curriculum.getId()).stream()
                    .filter(w -> w.getWeekNumber() == weekNumber)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("해당 주차를 찾을 수 없습니다: " + weekNumber + "주차"));
            days = curriculumDayRepository.findByCurriculumId(curriculum.getId()).stream()
                    .filter(d -> d.getWeek().getId().equals(week.getId()))
                    .sorted(Comparator.comparingInt(CurriculumDay::getDayNumber))
                    .toList();
            title = week.getTitle();
        } else {
            CurriculumDay day = curriculumDayRepository.findByCurriculumIdAndDayNumber(curriculum.getId(), dayNumber)
                    .orElseThrow(() -> new IllegalArgumentException("해당 학습 내용을 찾을 수 없습니다: " + dayNumber + "일째"));
            days = List.of(day);
            title = day.getWeek().getTitle() + " · " + day.getDayInWeek() + "회차";
        }

        List<PrintableSetResponse.PrintableDay> printableDays = days.stream()
                .map(day -> new PrintableSetResponse.PrintableDay(
                        day.getDayNumber(),
                        day.getTask(),
                        day.getPassages().stream()
                                .map(passage -> new PrintableSetResponse.PrintablePassage(
                                        passage.getCategory().name(),
                                        passage.getSubType(),
                                        passage.getPassageText(),
                                        passage.getProblems().stream()
                                                .map(problem -> new PrintableSetResponse.PrintableProblem(
                                                        problem.getQuestionText(),
                                                        problem.getOptions(),
                                                        problem.getCorrectAnswerIndex(),
                                                        problem.getOptionExplanations(),
                                                        problem.getTrapNote(),
                                                        problem.getStrategyTip()))
                                                .toList()))
                                .toList()))
                .toList();

        return new PrintableSetResponse(
                curriculum.getTitle(),
                curriculum.getLearnerType().getLabel(),
                curriculum.getTargetLevelLabel(),
                title,
                printableDays);
    }

    /** 지문 하나에 딸린 문제 하나를 채점한다. 로그인 없이도 풀 수 있는 정적 문제집이라
     * userId가 없어도(비로그인) 채점 자체는 그대로 동작하고, 로그인 상태면 틀린 문제를
     * 오답노트에 기록한다(다시 맞히면 오답노트에서 자동으로 지운다). */
    @Transactional
    public ProblemAnswerResponse submitAnswer(String problemId, int selectedIndex, Long userId) {
        CurriculumProblem problem = curriculumProblemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("문제를 찾을 수 없습니다: " + problemId));

        boolean correct = selectedIndex == problem.getCorrectAnswerIndex();
        if (userId != null) {
            recordWrongAnswer(userId, problem, selectedIndex, correct);
        }

        return new ProblemAnswerResponse(
                correct,
                problem.getCorrectAnswerIndex(),
                problem.getOptionExplanations(),
                problem.getTrapNote(),
                problem.getStrategyTip()
        );
    }

    private void recordWrongAnswer(Long userId, CurriculumProblem problem, int selectedIndex, boolean correct) {
        if (correct) {
            wrongAnswerRepository.deleteByUser_IdAndProblem_Id(userId, problem.getId());
            return;
        }
        UserWrongAnswer wrongAnswer = wrongAnswerRepository.findByUser_IdAndProblem_Id(userId, problem.getId())
                .orElseGet(() -> {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user == null) {
                        return null;
                    }
                    UserWrongAnswer created = new UserWrongAnswer();
                    created.setUser(user);
                    created.setProblem(problem);
                    return created;
                });
        if (wrongAnswer == null) {
            return;
        }
        wrongAnswer.setSelectedIndex(selectedIndex);
        wrongAnswerRepository.save(wrongAnswer);
    }

    /** 로그인한 사용자가 지금까지 틀린 문제 목록(오답노트). 최근 틀린 순. */
    @Transactional(readOnly = true)
    public List<WrongAnswerResponse> getWrongNotes(Long userId) {
        return wrongAnswerRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(WrongAnswerResponse::from)
                .toList();
    }

    /** 오답노트에서 항목 하나를 수동으로 지운다("복습 완료 처리"). */
    @Transactional
    public void removeWrongNote(Long userId, String problemId) {
        wrongAnswerRepository.deleteByUser_IdAndProblem_Id(userId, problemId);
    }

    private UserCurriculumProgress getOrAssignProgress(Long userId) {
        return progressRepository.findByUserId(userId).orElseGet(() -> assignCurriculumRaceSafe(userId));
    }

    /** assignCurriculum()의 INSERT를 별도 트랜잭션(REQUIRES_NEW)에서 시도한다. 같은
     * 사용자가 "오늘의 학습"을 동시에 두 번 요청하면(더블 클릭, 여러 탭 등) 둘 다
     * findByUserId에서 "아직 없음"을 보고 동시에 배정을 시도할 수 있는데, 이때 하나는
     * user_curriculum_progress의 user_id 유니크 제약을 어겨 실패한다. 같은 트랜잭션
     * 안에서 이 실패를 그냥 잡으면 PostgreSQL이 트랜잭션 전체를 이미 무효화한 뒤라
     * 이어지는 조회도 실패하므로, 실패 가능한 INSERT만 별도 트랜잭션으로 격리해
     * 실패해도 바깥 트랜잭션은 멀쩡하게 두고 그 안에서 다시 조회한다. */
    private UserCurriculumProgress assignCurriculumRaceSafe(Long userId) {
        TransactionTemplate isolatedInsert = new TransactionTemplate(transactionManager);
        isolatedInsert.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        try {
            return isolatedInsert.execute(status -> assignCurriculum(userId));
        } catch (DataIntegrityViolationException e) {
            return progressRepository.findByUserId(userId).orElseThrow(() -> e);
        }
    }

    private UserCurriculumProgress assignCurriculum(Long userId) {
        LearnerType learnerType = assessmentResultRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new IllegalStateException("먼저 학습 유형 진단을 완료해주세요."))
                .getLearnerType();

        Curriculum curriculum = resolveCurriculumForLevel(userId, learnerType);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));

        UserCurriculumProgress progress = new UserCurriculumProgress();
        progress.setUser(user);
        progress.setCurriculum(curriculum);
        progress.setCurrentDay(1);
        return progressRepository.save(progress);
    }

    /** TOPIK 적응형 퀴즈(UserTopikProgress)로 추정된 실력이 있으면 그 급수 구간
     * (1~2급/3~4급/5~6급)에 맞는 커리큘럼을 배정한다. 적응형 퀴즈를 아직 한 번도
     * 안 풀어본 신규 사용자이거나, 그 학습유형에 해당 급수 구간의 커리큘럼이 아직
     * 준비되지 않았으면(예: AdaptiveMixed는 5~6급 커리큘럼이 없음) 가장 낮은
     * 급수단계로 대신 배정한다. */
    private Curriculum resolveCurriculumForLevel(Long userId, LearnerType learnerType) {
        Optional<TopikLevel> desiredLevelFrom = topikProgressRepository.findByUserId(userId)
                .map(progress -> groupStartLevel(progress.getTopikLevel()));

        if (desiredLevelFrom.isPresent()) {
            Optional<Curriculum> matched = curriculumRepository
                    .findByLearnerTypeAndTargetLevelFrom(learnerType, desiredLevelFrom.get());
            if (matched.isPresent()) {
                return matched.get();
            }
        }

        return curriculumRepository.findFirstByLearnerTypeOrderByTargetLevelFromAsc(learnerType)
                .orElseThrow(() -> new IllegalStateException(
                        learnerType.getLabel() + " 유형의 상세 커리큘럼은 아직 준비 중이에요."));
    }

    /** TOPIK 등급이 속한 급수 구간의 시작 등급(하급→1급/중급→3급/상급→5급)을 돌려준다 —
     * 커리큘럼은 이 시작 등급을 targetLevelFrom으로 저장한다(각 CurriculumDataLoader 참고). */
    private TopikLevel groupStartLevel(TopikLevel level) {
        return switch (level.getGroup()) {
            case LOWER -> TopikLevel.LEVEL_1;
            case MIDDLE -> TopikLevel.LEVEL_3;
            case UPPER -> TopikLevel.LEVEL_5;
        };
    }

    /** 적응형 퀴즈에서 급수 구간이 실제로 바뀌었을 때(AdaptiveQuizService가 호출) 이미
     * 배정된 커리큘럼을 새 구간에 맞는 것으로 교체한다. 아직 커리큘럼을 배정받은 적이
     * 없거나, 이미 맞는 구간이거나, 그 학습유형에 새 구간의 커리큘럼이 없으면 아무것도
     * 하지 않는다(진행 중이던 하루 진도는 새 커리큘럼 내용이 완전히 다르므로 1일차로
     * 다시 시작한다 — 이어보기는 지원하지 않는다). */
    @Transactional
    public boolean syncCurriculumTier(Long userId) {
        UserCurriculumProgress progress = progressRepository.findByUserId(userId).orElse(null);
        if (progress == null) {
            return false;
        }

        TopikLevel desiredLevelFrom = topikProgressRepository.findByUserId(userId)
                .map(topikProgress -> groupStartLevel(topikProgress.getTopikLevel()))
                .orElse(null);
        if (desiredLevelFrom == null || desiredLevelFrom == progress.getCurriculum().getTargetLevelFrom()) {
            return false;
        }

        LearnerType learnerType = progress.getCurriculum().getLearnerType();
        return curriculumRepository.findByLearnerTypeAndTargetLevelFrom(learnerType, desiredLevelFrom)
                .map(newCurriculum -> {
                    progress.setCurriculum(newCurriculum);
                    progress.setCurrentDay(1);
                    progress.setCompletedDayCount(0);
                    progressRepository.save(progress);
                    return true;
                })
                .orElse(false);
    }

    /** 진단을 다시 받아 학습 유형이 바뀌었을 때(AssessmentService가 호출) 이미 배정된
     * 커리큘럼을 새 유형의 것으로 교체한다. 배정 로직이 "이미 있으면 그대로 사용"이라 이걸
     * 안 하면 재진단해도 예전 유형의 커리큘럼이 계속 나온다. 유형이 그대로면 아무것도 하지
     * 않고, 바뀌면 급수 구간은 현재 실력(적응형 퀴즈)에 맞춰 고르며 1일차부터 다시 시작한다. */
    @Transactional
    public boolean syncCurriculumLearnerType(Long userId) {
        UserCurriculumProgress progress = progressRepository.findByUserId(userId).orElse(null);
        if (progress == null) {
            return false;
        }
        LearnerType latest = assessmentResultRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(result -> result.getLearnerType())
                .orElse(null);
        if (latest == null || latest == progress.getCurriculum().getLearnerType()) {
            return false;
        }
        Curriculum newCurriculum;
        try {
            newCurriculum = resolveCurriculumForLevel(userId, latest);
        } catch (IllegalStateException noCurriculumYet) {
            return false;
        }
        progress.setCurriculum(newCurriculum);
        progress.setCurrentDay(1);
        progress.setCompletedDayCount(0);
        progressRepository.save(progress);
        return true;
    }

    private int totalDays(Curriculum curriculum) {
        return (int) curriculumDayRepository.countByCurriculumId(curriculum.getId());
    }

    private CurriculumDayResponse buildResponse(UserCurriculumProgress progress) {
        Curriculum curriculum = progress.getCurriculum();
        int totalDays = totalDays(curriculum);
        if (progress.isFinished(totalDays)) {
            return finishedResponse(curriculum, progress, totalDays);
        }

        CurriculumDay day = curriculumDayRepository
                .findByCurriculumIdAndDayNumber(curriculum.getId(), progress.getCurrentDay())
                .orElseThrow(() -> new IllegalStateException("커리큘럼 데이터가 손상됐습니다: day " + progress.getCurrentDay()));

        return toResponse(curriculum, progress, day, false);
    }

    private CurriculumDayResponse toResponse(Curriculum curriculum, UserCurriculumProgress progress, CurriculumDay day, boolean finished) {
        List<PassageResponse> passages = day.getPassages().stream().map(PassageResponse::from).toList();

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
                totalDays(curriculum),
                progress.getCompletedDayCount(),
                finished,
                recommendedWords(curriculum),
                passages
        );
    }

    private CurriculumDayResponse finishedResponse(Curriculum curriculum, UserCurriculumProgress progress, int totalDays) {
        return new CurriculumDayResponse(
                curriculum.getId(),
                curriculum.getTitle(),
                curriculum.getLearnerType().getLabel(),
                0,
                "완주",
                "",
                null,
                totalDays,
                0,
                "커리큘럼을 모두 완료했어요! 수고하셨습니다.",
                totalDays,
                progress.getCompletedDayCount(),
                true,
                List.of(),
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
