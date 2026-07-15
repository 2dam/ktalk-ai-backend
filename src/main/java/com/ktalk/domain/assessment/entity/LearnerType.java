package com.ktalk.domain.assessment.entity;

import java.util.List;

public enum LearnerType {

    STRATEGIC_ANALYST(
            "전략적 분석가",
            "스스로 계획하고 오답을 분석하는 능력이 뛰어난 자기주도형 학습자입니다.",
            List.of("기출문제집 + 오답 노트 전용 교재", "TOPIK 쓰기의 모든 것"),
            List.of("시원스쿨 고급 반"),
            List.of("Migii TOPIK"),
            "기출문제 5회독과 영역별 약점 데이터화, 쓰기 첨삭 집중이 핵심입니다.",
            List.of(
                    new CurriculumStage("1~2주", "최근 기출 1~2회분으로 강약점 파악 및 취약 영역 보충"),
                    new CurriculumStage("3~6주", "쓰기 문제 유형별 전략 수립, 기출 지문 3독법으로 듣기/읽기 소화"),
                    new CurriculumStage("7~8주", "실전 시간에 맞춘 주 2~3회 모의고사와 최종 리허설")
            )
    ),
    VISUAL_IMMERSIVE(
            "시각적 몰입형",
            "그림, 도표, 색깔 등 시각 자료로 학습할 때 이해도가 극대화되는 유형입니다.",
            List.of("컬러 코딩된 어휘·문법 교재", "인포그래픽 TOPIK"),
            List.of("해커스 TOPIK (PPT·도표 활용 강의)"),
            List.of("SEEMILE TOPIK"),
            "도식화·마인드맵 정리와 색상별 어휘 분류로 시각적 기억을 강화하세요.",
            List.of(
                    new CurriculumStage("1~3주", "컬러 코딩 교재로 문법/어휘를 시각적으로 분류"),
                    new CurriculumStage("4~5주", "영상 강의 병행, 시각 자료가 풍부한 교재로 문제 풀이"),
                    new CurriculumStage("6~8주", "모의고사 풀이 시 색깔 표시, 오답을 도표로 재구성")
            )
    ),
    AUDITORY_EMPATHETIC(
            "청각적 교감형",
            "강의나 설명을 들을 때 학습 효과가 가장 높고, 외부 자극으로 동기부여를 얻는 유형입니다.",
            List.of("오디오 파일 동봉 교재", "TOPIK II 합격 레시피"),
            List.of("YBM 인강"),
            List.of("TTMIK TOPIK Prep"),
            "쉐도잉과 받아쓰기, 1:1 강의를 통한 즉각적인 청각 피드백이 효과적입니다.",
            List.of(
                    new CurriculumStage("1~4주", "생생한 설명 위주 강의 수강, 쉐도잉으로 발음/리듬 훈련"),
                    new CurriculumStage("5~9주", "받아쓰기와 녹음 반복 청취로 청취력 강화"),
                    new CurriculumStage("10~12주", "모의고사 후 음성 해설로 복습, 1:1 첨삭으로 실전 감각 확보")
            )
    ),
    EXPERIENTIAL_ACTOR(
            "체험적 실행형",
            "직접 풀고 쓰고 말해보며 학습하는 유형으로, 즉각적인 피드백이 필요합니다.",
            List.of("문제 풀이형 워크북", "스티커·카드식 단어장"),
            List.of("AmazingTalker 1:1 맞춤 수업"),
            List.of("thinkbig TOPIK (AI 쓰기 첨삭)"),
            "뽀모도로 기법(25분 집중·5분 휴식)과 즉시 채점·즉시 피드백을 반복하세요.",
            List.of(
                    new CurriculumStage("1~3주", "워크북과 카드식 단어장으로 직접 조작하며 학습"),
                    new CurriculumStage("4~5주", "소리 내어 읽기, 원고지 쓰기 연습으로 활동적 훈련"),
                    new CurriculumStage("6~8주", "모의고사 직후 즉시 채점 및 AI 첨삭으로 실전 감각 확보")
            )
    ),
    ADAPTIVE_MIXED(
            "혼합 적응형",
            "상황에 따라 학습 방식을 유연하게 전환하는 자기주도형 학습자입니다.",
            List.of("종합 기본서 1권", "실전 모의고사 1권"),
            List.of("시원스쿨 + 유튜브 무료 특강 병행"),
            List.of("TOPIK - 한국어능력시험 (종합 앱)"),
            "주간 단위로 학습 방식을 교차(월-영상, 화-필기, 수-청취)하며 자기 점검 루틴을 유지하세요.",
            List.of(
                    new CurriculumStage("1~3주", "종합 기본서로 전 영역 균형 학습, 방식 교차 시도"),
                    new CurriculumStage("4~5주", "취약 영역에 맞춰 학습 방식 조정, 여러 강의 병행"),
                    new CurriculumStage("6~8주", "주 1~2회 모의고사 후 자신에게 맞는 방식으로 오답 정리")
            )
    ),
    SNS_DEPENDENT(
            "SNS 의존형",
            "짧은 호흡의 콘텐츠와 동료 학습, 외부 자극을 통해 동기부여를 얻는 유형입니다.",
            List.of("미니 어휘장", "핵심 요약 노트"),
            List.of("유튜브 숏폼 강의 + 카톡 오픈채팅 스터디"),
            List.of("TOPIK Guide App"),
            "공부 시간대 스마트폰 알림을 차단하고, 스터디 그룹 의무 모의고사와 일일 인증제를 활용하세요.",
            List.of(
                    new CurriculumStage("1~3주", "미니 어휘장과 숏폼 강의로 부담 없이 시작"),
                    new CurriculumStage("4~5주", "스터디 그룹에서 매일 학습 인증 및 문제 공유"),
                    new CurriculumStage("6~8주", "주 1회 의무 모의고사와 그룹 내 상호 첨삭")
            )
    );

    private final String label;
    private final String description;
    private final List<String> textbooks;
    private final List<String> courses;
    private final List<String> apps;
    private final String studyTip;
    private final List<CurriculumStage> curriculum;

    LearnerType(String label, String description, List<String> textbooks, List<String> courses,
                List<String> apps, String studyTip, List<CurriculumStage> curriculum) {
        this.label = label;
        this.description = description;
        this.textbooks = textbooks;
        this.courses = courses;
        this.apps = apps;
        this.studyTip = studyTip;
        this.curriculum = curriculum;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTextbooks() {
        return textbooks;
    }

    public List<String> getCourses() {
        return courses;
    }

    public List<String> getApps() {
        return apps;
    }

    public String getStudyTip() {
        return studyTip;
    }

    public List<CurriculumStage> getCurriculum() {
        return curriculum;
    }

    // 자기조절 지수(주도형/반응형)와 VARK 감각 선호도(SensePreference)의 조합으로 6대 유형을 판정한다.
    // 원본 분류에는 없는 조합(예: 주도형+청각)은 성격이 가장 가까운 유형으로 대체 매핑한다.
    public static LearnerType classify(boolean selfDirected, SensePreference sense) {
        return switch (sense) {
            case READ_WRITE -> selfDirected ? STRATEGIC_ANALYST : SNS_DEPENDENT;
            case VISUAL -> selfDirected ? VISUAL_IMMERSIVE : SNS_DEPENDENT;
            case AUDITORY -> selfDirected ? ADAPTIVE_MIXED : AUDITORY_EMPATHETIC;
            case KINESTHETIC -> selfDirected ? ADAPTIVE_MIXED : EXPERIENTIAL_ACTOR;
            case MIXED -> selfDirected ? ADAPTIVE_MIXED : SNS_DEPENDENT;
        };
    }

    public enum SensePreference {
        VISUAL, AUDITORY, READ_WRITE, KINESTHETIC, MIXED
    }

    public record CurriculumStage(String period, String content) {
    }
}
