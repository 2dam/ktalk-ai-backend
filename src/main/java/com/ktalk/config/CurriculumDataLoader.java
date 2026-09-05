package com.ktalk.config;

import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.curriculum.entity.Curriculum;
import com.ktalk.domain.curriculum.entity.CurriculumDay;
import com.ktalk.domain.curriculum.entity.CurriculumWeek;
import com.ktalk.domain.curriculum.repository.CurriculumDayRepository;
import com.ktalk.domain.curriculum.repository.CurriculumRepository;
import com.ktalk.domain.topik.entity.TopikLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 학습 유형 진단(시각적 몰입형)에 연결되는 8주(56일) TOPIK 커리큘럼을 심어둔다.
 * 원고의 골격(주차 목표/활동/학습지 템플릿)만 데이터화하고, 일별 배분은 각 주차 활동
 * 목록을 7일에 걸쳐 순환 배치해서 만든다(원고에 일 단위 구분이 없어서 균등 배분).
 *
 * <p>체험적 실행형(EXPERIENTIAL_ACTOR) 시딩은 여기 있었으나, 이제 학습 단계별(1~2급/3~4급/5~6급)
 * 전용 로더({@code ExperientialActorCurriculumDataLoader} 등)로 완전히 대체되어 제거했다 —
 * 이 파일의 {@code findByLearnerType(EXPERIENTIAL_ACTOR)} 단일 결과 조회가 전용 로더들이 심어둔
 * 3개 행과 충돌해 {@code NonUniqueResultException}을 일으키고 있었다.</p>
 */
@Component
@RequiredArgsConstructor
public class CurriculumDataLoader implements CommandLineRunner {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumDayRepository curriculumDayRepository;

    private record WeekSeed(String title, String goal, List<String> activities, String template) {}

    @Override
    @Transactional
    public void run(String... args) {
        if (curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.VISUAL_IMMERSIVE, TopikLevel.LEVEL_4).isEmpty()) {
            seedVisualImmersive();
        }
        System.out.println("✅ TOPIK 커리큘럼(시각적 몰입형) 확인 완료");
    }

    private void seedVisualImmersive() {
        List<WeekSeed> weeks = List.of(
                new WeekSeed(
                        "컬러링 문법 맵",
                        "시각적 분류를 통해 문법을 색깔별로 입체적으로 이해한다.",
                        List.of(
                                "빨간색으로 연결어미(-고, -지만, -는데) 표시하며 정리하기",
                                "파란색으로 종결어미(-습니다, -아요, -ㄴ데요) 표시하며 정리하기",
                                "초록색으로 시간/조건 표현(-을 때, -면, -아서) 표시하며 정리하기",
                                "노란색으로 이유/원인 표현(-니까, -기 때문에) 표시하며 정리하기",
                                "문법별 관계도를 화살표로 그리며 암기하기 (예: -아서 → [이유]+[결과])"
                        ),
                        null
                ),
                new WeekSeed(
                        "어휘 카테고리 맵",
                        "주제별 마인드맵으로 어휘를 관계망으로 묶어 기억한다.",
                        List.of(
                                "주제 하나를 골라 마인드맵 작성하기 (예: '건강' → 병원, 증상, 치료, 약, 운동)",
                                "빈출 어휘를 관계도(연결선)로 묶어 정리하기",
                                "A3 종이에 마인드맵을 확대해 정리하고 벽에 부착하기",
                                "매일 10분씩 붙여둔 마인드맵 시각적으로 복습하기"
                        ),
                        """
                        [건강]
                           ├─ [병원] ─ 의사 / 간호사 / 진료받다
                           ├─ [증상] ─ 열 / 기침 / 아프다
                           └─ [치료] ─ 약 / 수술 / 낫다
                        (이 형태로 주제를 바꿔가며 직접 채워 넣어 연습하세요.)
                        """
                ),
                new WeekSeed(
                        "인포그래픽 쓰기 공식 - 51번",
                        "51번(실용문) 답안을 3단 색깔 박스 구조로 시각화해 쓴다.",
                        List.of(
                                "51번 템플릿 [제목]→[혜택/조건 박스]→[연락처/일정] 익히기",
                                "3가지 요소를 색깔 박스로 구분해 답안 작성 연습하기",
                                "기출 51번 문제 하나를 골라 색깔 박스 구조로 풀어보기"
                        ),
                        null
                ),
                new WeekSeed(
                        "인포그래픽 쓰기 공식 - 53·54번",
                        "53번(표 분석)과 54번(장문 논설문)을 색깔 구분 4단 구조로 쓴다.",
                        List.of(
                                "53번 템플릿 [최고값🔺]→[비교/대조 화살표]→[원인 추론💡] 익히고 3줄 요약 연습하기",
                                "54번 템플릿 서론🔵→본론1🟢→본론2🔴→결론🟣 구조로 매일 1개씩 연습하기",
                                "직접 쓴 답안에 4가지 색으로 단락 표시해보기"
                        ),
                        """
                        [서론-파란색] 현대 사회에서 [주제]는 중요한 이슈입니다.
                        [본론1-초록색] 첫째, [주장 A]는 [이유/근거] 때문에 필요합니다.
                        [본론2-빨간색] 하지만 [반론/보완점]도 고려해야 합니다.
                        [결론-보라색] 따라서 [주제]에 대한 나의 생각은 [종합적 의견]입니다.
                        """
                ),
                new WeekSeed(
                        "시각적 실전 모의고사 (1)",
                        "형광펜 3색으로 문제를 표시하며 실전 모의고사에 익숙해진다.",
                        List.of(
                                "노랑: 확실히 아는 문제 표시하며 풀기",
                                "초록: 찍어서 맞춘 문제 표시하며 풀기",
                                "분홍: 완전히 틀린 문제 표시하며 풀기",
                                "실전 모의고사 1회분 풀이하기"
                        ),
                        null
                ),
                new WeekSeed(
                        "시각적 실전 모의고사 (2)",
                        "형광펜 표시를 누적하며 모의고사 감각을 다진다.",
                        List.of(
                                "지난주와 같은 3색 형광펜 표시로 모의고사 1회분 풀이하기",
                                "3색 표시가 쌓인 문제지를 넘겨보며 패턴 확인하기"
                        ),
                        null
                ),
                new WeekSeed(
                        "오답 데이터 시각화",
                        "오답을 도넛 차트·막대 그래프로 바꿔 약점을 한눈에 본다.",
                        List.of(
                                "영역별 정답률을 도넛 차트로 그려보기",
                                "취약 유형(추론 문제, 세부정보 문제 등) 순위를 막대 그래프로 정리하기"
                        ),
                        """
                        [듣기 영역 정답률 : 68%]
                        총 50문제 중 34개 정답
                        [취약 유형 TOP 3]
                        1. 대화 추론 → 정답률 40%
                        2. 뉴스/강연 → 정답률 55%
                        3. 빠른 속도 대화 → 정답률 60%
                        """
                ),
                new WeekSeed(
                        "최종 점검",
                        "도식화된 오답 노트를 한 장으로 요약해 시험 직전 복습한다.",
                        List.of(
                                "지금까지의 오답 도식을 A1 사이즈 1장으로 요약하기",
                                "컬러 문법 요약집·어휘 마인드맵·비교 표현 도표 부록 훑어보기",
                                "시험 전날 요약본으로 10분간 최종 복습하기"
                        ),
                        null
                )
        );

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.VISUAL_IMMERSIVE);
        curriculum.setTitle("TOPIK 시각화 마스터 노트");
        curriculum.setTargetLevelLabel("4~5급 목표");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_4);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_5);
        curriculum.setUsageNote(
                "모든 학습 내용을 색깔 펜 3~4가지로 구분해 필기하고, 복잡한 문법은 마인드맵으로, "
                        + "어휘는 카테고리별 도식화로 정리하세요.");

        saveCurriculumWithDays(curriculum, weeks);
    }

    private void saveCurriculumWithDays(Curriculum curriculum, List<WeekSeed> weekSeeds) {
        List<CurriculumWeek> weeks = new ArrayList<>();
        for (int i = 0; i < weekSeeds.size(); i++) {
            WeekSeed seed = weekSeeds.get(i);
            CurriculumWeek week = new CurriculumWeek();
            week.setCurriculum(curriculum);
            week.setWeekNumber(i + 1);
            week.setTitle(seed.title());
            week.setGoal(seed.goal());
            week.setActivities(seed.activities());
            week.setTemplate(seed.template());
            weeks.add(week);
        }
        curriculum.setWeeks(weeks);
        Curriculum savedCurriculum = curriculumRepository.save(curriculum);

        List<CurriculumDay> days = new ArrayList<>();
        for (CurriculumWeek week : savedCurriculum.getWeeks()) {
            List<String> activities = week.getActivities();
            for (int dayInWeek = 1; dayInWeek <= 7; dayInWeek++) {
                CurriculumDay day = new CurriculumDay();
                day.setCurriculum(savedCurriculum);
                day.setWeek(week);
                day.setDayInWeek(dayInWeek);
                day.setDayNumber((week.getWeekNumber() - 1) * 7 + dayInWeek);
                day.setTask(activities.get((dayInWeek - 1) % activities.size()));
                days.add(day);
            }
        }
        curriculumDayRepository.saveAll(days);
    }
}
