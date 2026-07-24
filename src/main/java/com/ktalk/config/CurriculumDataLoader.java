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
 * 학습 유형 진단(시각적 몰입형/체험적 실행형)에 연결되는 8주(56일) TOPIK 커리큘럼을 심어둔다.
 * 원고의 골격(주차 목표/활동/학습지 템플릿)만 데이터화하고, 일별 배분은 각 주차 활동
 * 목록을 7일에 걸쳐 순환 배치해서 만든다(원고에 일 단위 구분이 없어서 균등 배분).
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
        if (curriculumRepository.count() > 0) {
            return;
        }
        seedVisualImmersive();
        seedExperientialActor();
        System.out.println("✅ TOPIK 커리큘럼 2종(시각적 몰입형/체험적 실행형) 생성 완료");
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

    private void seedExperientialActor() {
        List<WeekSeed> weeks = List.of(
                new WeekSeed(
                        "기초 문형 30개 마스터",
                        "직접 쓰고 말하면서 기초 문형을 체화한다.",
                        List.of(
                                "오늘의 문법으로 예문 3개씩 직접 작성하기 (예: -아/어서 예문 3개)",
                                "25분 집중 + 5분 휴식 뽀모도로 기법으로 학습하기",
                                "작성한 예문을 소리 내어 읽어보기"
                        ),
                        null
                ),
                new WeekSeed(
                        "빈출 어휘 플래시카드",
                        "직접 만든 플래시카드로 어휘를 손과 입으로 익힌다.",
                        List.of(
                                "오늘 배운 단어로 플래시카드 직접 제작하기 (앞: 한국어 / 뒤: 뜻·예문)",
                                "단어를 보면 즉시 예문을 소리 내어 말하는 연습하기"
                        ),
                        null
                ),
                new WeekSeed(
                        "문법 통합 연습",
                        "여러 문법을 한 번에 활용해 짧은 글을 완성한다.",
                        List.of(
                                "이번 주 배운 문법 중 5개를 모두 사용해 5문장짜리 글 쓰기",
                                "빈칸 채우기 워크시트 직접 풀어보기"
                        ),
                        """
                        [문법 연습 - 연결어미]
                        1. -고 (순차적 나열): 아침에 일어나서 (        ) (        ) 학교에 갔어요.
                        2. -지만 (대조): 한국어는 어렵지만 (        ) (        ) (        ).
                        3. -을 때 (시간): (        ) (        ) (        ) 날씨가 가장 좋아요.
                        (빈칸을 직접 채워 문장을 완성하세요.)
                        """
                ),
                new WeekSeed(
                        "듣기 집중 훈련",
                        "쉐도잉과 받아쓰기로 듣기 근육을 직접 만든다.",
                        List.of(
                                "쉐도잉: 듣기 지문을 들으며 동시에 따라 말하기 (하루 10분)",
                                "받아쓰기: 1분 분량 지문을 받아쓰고 정답지와 비교해 오류 수정하기"
                        ),
                        """
                        [받아쓰기 연습지]
                        내가 들은 내용을 그대로 적어보세요 (최소 3회 반복 청취 후 작성):
                        ________________________________________
                        ________________________________________
                        [정답지와 비교 후 틀린 부분 기록]
                        1. ______________  2. ______________  3. ______________
                        """
                ),
                new WeekSeed(
                        "읽기 집중 훈련",
                        "소리 내어 읽고 직접 요약하며 독해력을 체화한다.",
                        List.of(
                                "읽기 지문 3회 반복해서 소리 내어 읽기 (1차 내용파악·2차 모르는 단어 표시·3차 문장구조 분석)",
                                "읽은 지문을 한국어 3문장으로 직접 요약하기"
                        ),
                        null
                ),
                new WeekSeed(
                        "모의고사 실전 풀이 (1)",
                        "실제 시험 시간에 맞춰 실전처럼 직접 풀어본다.",
                        List.of(
                                "실제 시험 시간(180분)에 맞춰 모의고사 풀이하기",
                                "풀면서 모르는 단어는 바로 형광펜으로 표시하기"
                        ),
                        null
                ),
                new WeekSeed(
                        "모의고사 실전 풀이 (2)",
                        "실전 감각을 유지하며 원고지 쓰기를 준비한다.",
                        List.of(
                                "실제 시험 시간(180분)에 맞춰 모의고사 1회분 더 풀이하기",
                                "지난 모의고사에서 표시한 모르는 단어 복습하기"
                        ),
                        null
                ),
                new WeekSeed(
                        "쓰기 원고지 연습 & 오답 액션 플랜",
                        "실제 원고지에 쓰기 답안을 작성하고 오답을 직접 분석한다.",
                        List.of(
                                "51번(2~3문장), 52번(3~4문장) 답안을 원고지에 직접 작성하기",
                                "53번(5~7문장), 54번(600~700자) 답안을 원고지에 직접 작성하기",
                                "틀린 문제 하나를 골라 '왜 틀렸을까' 3줄로 분석하기"
                        ),
                        """
                        [오답 액션 플랜]
                        - 문제 번호: ______
                        - 틀린 이유(예: 시간 부족 / 어휘 몰라 / 함정에 빠짐): ______
                        - 다음에 이 문제를 만나면 어떻게 풀 것인가? 3줄 요약:
                          1. ______________  2. ______________  3. ______________
                        """
                )
        );

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.EXPERIENTIAL_ACTOR);
        curriculum.setTitle("TOPIK 액티브 워크북");
        curriculum.setTargetLevelLabel("3~4급 목표");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_3);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_4);
        curriculum.setUsageNote(
                "모든 문제는 반드시 직접 풀고 빈칸을 채우며 학습하고, 25분 집중+5분 휴식 뽀모도로 기법을 지키세요. "
                        + "읽기 지문은 소리 내어 3번 읽고, 쓰기는 원고지에 직접 연습하세요.");

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
