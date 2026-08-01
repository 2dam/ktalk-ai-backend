package com.ktalk.config;

import com.ktalk.domain.assessment.entity.LearnerType;
import com.ktalk.domain.curriculum.entity.*;
import com.ktalk.domain.curriculum.repository.CurriculumDayRepository;
import com.ktalk.domain.curriculum.repository.CurriculumRepository;
import com.ktalk.domain.curriculum.repository.UserCurriculumProgressRepository;
import com.ktalk.domain.topik.entity.TopikLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 시각적 몰입형(VISUAL_IMMERSIVE) 유형의 5~6급 "TOPIK 컬러맵 완전분석" 커리큘럼을 심는다.
 * VisualImmersiveLevel34CurriculumDataLoader(3~4급)와 동일한 골격(레코드/헬퍼, 8주 + 모의고사 2회 + Final 1회,
 * 총 2,450문항)을 쓰되, trapNote/strategyTip을 색깔 코딩·마인드맵·도식화 언어로 재구성한다 —
 * LearnerType.VISUAL_IMMERSIVE의 studyTip("도식화·마인드맵 정리와 색상별 어휘 분류")을 모든 문항에
 * 반영한다. 1~2급·3~4급 과정과는 완전히 분리된 별도의 8주 과정으로, 같은 learner_type이라도
 * targetLevelFrom(LEVEL_5)으로 구분되는 별도 Curriculum 레코드를 갖는다.
 * WEEK1~4는 5급(기출문제 스타일, 학술·시사·전문 담화 중심), WEEK5~8은 6급(시중교재 grammarUnit 스타일).
 */
@Component
@RequiredArgsConstructor
@Order(15)
public class VisualImmersiveLevel56CurriculumDataLoader implements CommandLineRunner {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumDayRepository curriculumDayRepository;
    private final UserCurriculumProgressRepository userCurriculumProgressRepository;

    private record OptionSeed(String text, String note) {}
    private record ProblemSeed(String question, List<OptionSeed> options, int correctIndex, String trapNote, String strategyTip) {}
    private record PassageSeed(PassageCategory category, String subType, String passageText, String diagramSvg, List<ProblemSeed> problems) {}
    private record DaySeed(String task, List<PassageSeed> passages) {}
    private record WeekSeed(String title, String goal, String template, List<DaySeed> days) {}

    private static OptionSeed opt(String text, String note) {
        return new OptionSeed(text, note);
    }

    private static ProblemSeed q(String question, List<OptionSeed> options, int correctIndex, String trapNote, String strategyTip) {
        return new ProblemSeed(question, options, correctIndex, trapNote, strategyTip);
    }

    private static PassageSeed onePassage(PassageCategory category, String subType, String passageText, ProblemSeed problem) {
        return new PassageSeed(category, subType, passageText, null, List.of(problem));
    }

    /** 문법/어휘 포인트 하나에 연습문제 여러 개가 딸린 "시중 교재" 스타일 유닛용. */
    private static PassageSeed grammarUnit(String subType, String explanation, ProblemSeed... problems) {
        return new PassageSeed(PassageCategory.READING, subType, explanation, null, List.of(problems));
    }

    /** 실전 모의고사에서 지문 하나에 문제 2개 이상이 딸린 실제 TOPIK 형식용. */
    private static PassageSeed multiQ(PassageCategory category, String subType, String passageText, ProblemSeed... problems) {
        return new PassageSeed(category, subType, passageText, null, List.of(problems));
    }

    private static DaySeed day(String task, PassageSeed... passages) {
        return new DaySeed(task, List.of(passages));
    }

    /** 한 "차"(40문항)를 이루는 여러 하위 목록을 하루 학습량 하나로 합칠 때 사용. */
    @SafeVarargs
    private static PassageSeed[] merge(List<PassageSeed>... lists) {
        List<PassageSeed> all = new ArrayList<>();
        for (List<PassageSeed> list : lists) {
            all.addAll(list);
        }
        return all.toArray(new PassageSeed[0]);
    }

    @Override
    @Transactional
    public void run(String... args) {
        curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.VISUAL_IMMERSIVE, TopikLevel.LEVEL_5)
                .ifPresent(this::deleteExisting);

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.VISUAL_IMMERSIVE);
        curriculum.setTitle("TOPIK 컬러맵 완전분석 노트");
        curriculum.setTargetLevelLabel("5~6급 전 과정");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_5);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_6);
        curriculum.setUsageNote(
                "모든 문제에 색깔 태그(🔴🟢🔵🟣)로 함정 포인트를 구분하고, 문법·어휘는 마인드맵 구조로 "
                        + "설명합니다. 색깔 펜 3~4자루를 준비해 오답 노트를 도식화하며 시각적으로 기억을 강화하세요.");

        List<WeekSeed> weeks = List.of(week1());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("🎨 TOPIK 커리큘럼(시각적 몰입형, 5~6급) WEEK1 신설 - 계속 진행 중!");
    }

    /** 재시딩 전 기존 커리큘럼을 지운다. day는 부모의 cascade 대상이 아니라 먼저 지워야 한다. */
    private void deleteExisting(Curriculum existing) {
        List<CurriculumDay> days = curriculumDayRepository.findByCurriculumId(existing.getId());
        curriculumDayRepository.deleteAll(days);
        userCurriculumProgressRepository.deleteByCurriculumId(existing.getId());
        curriculumRepository.delete(existing);
        curriculumRepository.flush();
    }

    // ===================== WEEK 1: 5~6급 컬러맵 기초 다지기 =====================

    private static final String WEEK1_ANSWER_NOTE_TEMPLATE = """
            [🎨 오답 노트 템플릿 - WEEK1용]
            문제를 틀렸을 때 색깔 펜으로 표시하며 나의 취약 유형을 도식화해보세요.

            문제 번호(1~40) | 틀린 이유(해당 색깔 동그라미) | 취약 유형 코드
            예) 3번 | 🔴 (시간/장소 혼동) |

            [🎨 색깔별 취약 유형 코드 가이드]
            🔴 빨강 - 시간/장소 혼동: 대화나 글에 나온 시간, 장소, 숫자 정보를 정확히 기억하지 못함.
            🟢 초록 - 의도 파악 실패: 화자나 글쓴이의 진짜 목적이나 주제를 놓침.
            🔵 파랑 - 어휘 부족: 5~6급 수준의 전문·추상 어휘를 몰라 내용 이해에 어려움을 겪음.
            🟣 보라 - 기타: 위에 해당하지 않는 오류(예: 부주의, 시간 부족).

            같은 색깔이 반복해서 칠해진다면, 그 색을 형광펜으로 표시해 다음 학습 때 우선 보완하세요.
            """;

    private WeekSeed week1() {
        List<PassageSeed> lv56w1_1st_l1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "남자: 이번 정책 토론회에서 발언 순서가 바뀌었다고 들었는데요.\n여자: 네, 원래 예정된 발제자의 일정이 갑자기 바뀌어서 부득이하게 조정했습니다.\n남자: 그렇군요. 미리 알려 주셔서 감사합니다.",
                        q("여자가 이렇게 말한 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("발언 순서 변경의 불가피한 사정을 설명하려고", "정답: '부득이하게 조정했다'는 발언에서 변경 사유를 해명하려는 의도가 드러납니다."),
                                opt("발제자의 자격을 문제 삼으려고", "자격 문제는 언급되지 않았습니다."),
                                opt("토론회 자체를 취소하려고", "취소가 아니라 순서 조정에 대한 설명입니다."),
                                opt("남자에게 발언 순서를 양보해 달라고 요청하려고", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 단순 정보 전달을 다른 의도로 확대 해석하기 쉽습니다.", "[의도파악 마인드맵] 부득이하게 조정(핵심 표현) → 사정 설명(의도). 해명 표현에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "여자: 이번 논문 심사에서 제 연구 방법론에 대한 지적이 많았어요.\n남자: 그만큼 꼼꼼히 봐 주셨다는 뜻이니 오히려 발전의 기회로 삼으시면 좋을 것 같아요.\n여자: 그렇게 생각하니 마음이 한결 가벼워지네요.",
                        q("남자가 이렇게 말한 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부정적 지적을 긍정적으로 받아들이도록 격려하려고", "정답: '발전의 기회로 삼으라'는 발언에서 격려의 의도가 드러납니다."),
                                opt("심사위원의 지적이 부당했다고 비판하려고", "심사위원을 비판하는 내용이 아닙니다."),
                                opt("여자에게 논문을 다시 쓰라고 권유하려고", "언급되지 않은 내용입니다."),
                                opt("자신의 연구 경험을 자랑하려고", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 격려의 의도를 비판이나 자랑으로 오해하기 쉽습니다.", "[의도파악 마인드맵] 발전의 기회(핵심 표현) → 격려(의도). 상대를 다독이는 표현에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "남자: 이번 보고서에 인용하신 통계 자료의 출처가 명확하지 않은 것 같습니다.\n여자: 아, 지적해 주셔서 감사합니다. 원 출처를 다시 확인해서 각주에 명시하도록 하겠습니다.\n남자: 네, 신뢰도를 높이는 데 도움이 될 겁니다.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("보고서의 완성도를 높이기 위해 건설적으로 지적하고 있다.", "정답: 출처 문제를 지적한 뒤 신뢰도 향상을 언급하는 것에서 건설적 태도를 알 수 있습니다."),
                                opt("보고서 전체를 신뢰하지 않고 있다.", "전체 신뢰를 부정하는 것이 아니라 특정 부분을 지적하는 것입니다."),
                                opt("여자의 연구 능력을 폄하하고 있다.", "폄하가 아니라 개선을 위한 지적입니다."),
                                opt("보고서 제출을 거부하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 건설적 지적을 전면 부정이나 폄하로 확대 해석하기 쉽습니다.", "[의도파악 마인드맵] 지적+신뢰도 향상(핵심 표현) → 건설적 태도(의도). 지적 뒤에 이어지는 말에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "여자: (라디오 시사 프로그램) 최근 발표된 인구 통계에 따르면, 생산 가능 인구는 지속적으로 감소하는 반면 고령 인구 비율은 빠르게 증가하고 있습니다. 전문가들은 이러한 추세가 향후 노동 시장 구조 전반에 걸쳐 근본적인 변화를 요구할 것이라고 전망합니다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("생산 가능 인구는 계속 줄어들고 있다.", "정답: '생산 가능 인구는 지속적으로 감소한다'고 언급되었습니다."),
                                opt("고령 인구 비율은 감소하고 있다.", "빠르게 증가하고 있다고 언급되었습니다."),
                                opt("노동 시장 구조는 변화가 필요 없다.", "근본적인 변화를 요구할 것이라고 언급되었습니다."),
                                opt("이 통계는 아직 발표되지 않았다.", "최근 발표되었다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 생산 가능 인구와 고령 인구의 증감 방향을 뒤바꿔 혼동하기 쉽습니다.", "[일치판단 마인드맵] 두 인구 지표의 증감 방향을 각각 정확히 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: (다큐멘터리 내레이션) 이 유적은 발굴 당시 학계의 통념을 뒤집는 증거로 주목받았다. 기존에는 이 지역에 정착 문화가 없었다고 여겨졌으나, 이번 발굴을 통해 대규모 정착 흔적이 확인되면서 관련 이론들이 전면 재검토되고 있다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("이 발굴로 정착 문화가 없었다는 기존 통념이 흔들렸다.", "정답: '기존 통념을 뒤집는 증거로 주목받았다'고 언급되었습니다."),
                                opt("이 지역은 원래부터 정착 문화가 있었다고 알려져 있었다.", "기존에는 정착 문화가 없었다고 여겨졌다고 언급되었습니다."),
                                opt("관련 이론들은 재검토 없이 그대로 유지되고 있다.", "전면 재검토되고 있다고 언급되었습니다."),
                                opt("이 유적은 발굴 당시 주목받지 못했다.", "주목받았다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 기존 통념과 새로운 증거의 내용을 뒤바꿔 혼동하기 쉽습니다.", "[일치판단 마인드맵] 기존에 알려진 내용과 이번 발굴로 새롭게 밝혀진 내용을 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 최근 기업들이 단기 실적에만 치중하는 경향이 강해지는 것 같아요.\n여자: 저는 장기적인 관점에서 지속 가능한 성장 전략을 세우는 것이 결국 기업의 경쟁력을 좌우한다고 봐요.\n남자: 일리 있는 말씀이네요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("장기적 관점의 지속 가능한 성장 전략이 기업 경쟁력을 좌우한다.", "정답: 여자의 발언에서 직접 드러납니다."),
                                opt("단기 실적에 집중하는 것이 가장 효과적인 전략이다.", "여자의 생각과 반대됩니다."),
                                opt("기업의 경쟁력은 실적과 무관하다.", "언급되지 않은 내용입니다."),
                                opt("장기 전략은 현실성이 없다.", "여자의 생각과 반대됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 남자가 지적한 현상(단기 실적 치중)을 여자의 생각으로 착각하기 쉽습니다.", "[중심생각 마인드맵] 여자의 직접 발언(장기적 관점, 지속 가능한 성장)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도·어조 파악",
                        "남자: (강연) 흔히 혁신이란 거창한 발명이라고 생각하기 쉽지만, 저는 오히려 기존의 것을 낯설게 바라보는 태도에서 혁신이 시작된다고 믿습니다. 익숙함에 안주하지 않는 시선, 그것이야말로 진정한 혁신의 출발점입니다.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("혁신에 대한 통념을 재해석하며 자신의 견해를 제시하고 있다.", "정답: 통념(거창한 발명)을 언급한 뒤 자신의 견해(낯설게 바라보는 태도)를 제시하고 있습니다."),
                                opt("혁신이라는 개념 자체를 부정하고 있다.", "부정이 아니라 재해석하는 것입니다."),
                                opt("청중의 의견에 전적으로 동의하고 있다.", "청중의 의견은 언급되지 않았습니다."),
                                opt("혁신은 불가능하다고 주장하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 통념 소개 부분만 보고 그것이 화자의 주장이라고 착각하기 쉽습니다.", "[태도파악 마인드맵] '하지만 저는 오히려'로 이어지는 화자 자신의 견해에 주목하세요.")),
                multiQ(PassageCategory.LISTENING, "강연(주제/세부 내용)",
                        "여자: (강연) 오늘은 도시 재생 사업의 명암에 대해 말씀드리려 합니다. 낙후된 지역에 활력을 불어넣는다는 긍정적 측면이 부각되곤 하지만, 실제로는 원주민들이 임대료 상승을 감당하지 못해 정든 터전을 떠나는 이른바 '젠트리피케이션' 현상이 동반되는 경우가 적지 않습니다. 재생 사업이 진정한 의미를 가지려면 이러한 부작용을 최소화할 제도적 장치가 함께 마련되어야 합니다.",
                        q("여자가 말하는 내용의 핵심 주제로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("도시 재생 사업의 부작용과 그 대책의 필요성", "정답: 젠트리피케이션이라는 부작용을 지적하고 제도적 장치의 필요성을 강조하고 있습니다."),
                                opt("도시 재생 사업의 성공 사례 소개", "성공 사례가 아니라 부작용을 다루고 있습니다."),
                                opt("젠트리피케이션의 어원과 역사", "어원이나 역사는 언급되지 않았습니다."),
                                opt("원주민 이주 지원 정책의 종류", "구체적인 정책 종류는 언급되지 않았습니다.")
                        ), 0, "🟢 의도 파악 실패 — 긍정적 측면 언급 부분만 듣고 전체 주제를 오해하기 쉽습니다.", "[담화파악 마인드맵] '하지만 실제로는'으로 이어지는 부작용과 제도적 장치 필요성에 주목하세요."),
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("도시 재생 사업은 원주민 이주 문제를 동반할 수 있다.", "정답: '원주민들이 정든 터전을 떠나는 현상이 동반되는 경우가 적지 않다'고 언급되었습니다."),
                                opt("도시 재생 사업은 항상 긍정적 효과만 가져온다.", "부작용도 있다고 언급되어 반대됩니다."),
                                opt("젠트리피케이션은 임대료 하락 때문에 발생한다.", "임대료 상승 때문이라고 언급되었습니다."),
                                opt("제도적 장치는 이미 충분히 마련되어 있다.", "함께 마련되어야 한다고 언급되어 아직 미비함을 알 수 있습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 임대료 상승과 하락, 제도 마련의 현재/필요 상태를 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 원인(임대료 상승)과 결과(이주), 현재 상태(미비)와 필요성을 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도·어조 파악",
                        "여자: (인터뷰) 실패를 두려워하지 않는 조직 문화를 만드는 것이 중요하다고 늘 강조해 왔습니다. 다만 실패로부터 배우지 않는다면 그것은 그저 반복되는 실수에 불과합니다. 실패를 자산으로 전환하는 체계적인 학습 과정이 뒷받침되어야 진정한 의미의 혁신적 조직이라 할 수 있습니다.",
                        q("여자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("실패 허용과 더불어 그로부터 배우는 체계가 함께 필요하다고 강조하고 있다.", "정답: 실패를 두려워하지 않는 문화와 더불어 학습 과정의 필요성을 함께 강조하고 있습니다."),
                                opt("실패는 무조건 피해야 한다고 주장하고 있다.", "여자의 생각과 반대됩니다."),
                                opt("실패로부터 배우는 것은 불필요하다고 말하고 있다.", "여자의 생각과 반대됩니다."),
                                opt("조직 문화는 바뀔 필요가 없다고 말하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 실패 허용이라는 부분만 듣고 배움의 필요성을 놓치기 쉽습니다.", "[태도파악 마인드맵] '다만'으로 이어지는 단서(배우지 않으면 반복되는 실수)에 주목하세요.")),
        );

        List<PassageSeed> lv56w1_1st_l11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: (뉴스) 정부는 오늘 발표한 대책에서 청년층의 주거 부담을 완화하기 위해 공공임대주택 공급을 대폭 확대하겠다고 밝혔습니다. 다만 재원 마련 방안에 대해서는 구체적인 언급이 없어 실효성에 의문이 제기되고 있습니다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("정부는 공공임대주택 공급 확대 방침을 발표했다.", "정답: '공공임대주택 공급을 대폭 확대하겠다고 밝혔다'고 언급되었습니다."),
                                opt("재원 마련 방안이 구체적으로 발표되었다.", "구체적인 언급이 없었다고 언급되었습니다."),
                                opt("이 대책의 실효성에 의문을 제기하는 사람이 없다.", "실효성에 의문이 제기되고 있다고 언급되었습니다."),
                                opt("이 대책은 청년층과 무관한 내용이다.", "청년층의 주거 부담 완화를 위한 대책이라고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 발표된 내용과 아직 밝혀지지 않은 내용을 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 발표된 것(공급 확대)과 언급되지 않은 것(재원 방안)을 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화제 고르기",
                        "여자: 요즘 기업들이 ESG 경영을 강조하고 있는데, 실제로 얼마나 실질적인 변화를 만들어 내고 있을까요?\n남자: 일부 기업은 홍보 수단으로만 활용하는 경우도 있지만, 진지하게 경영 전반을 재편하는 곳도 늘고 있습니다.",
                        q("두 사람이 무엇에 대해 이야기하고 있는지 고르십시오.", List.of(
                                opt("ESG 경영의 실질적 효과", "정답: 'ESG 경영', '실질적인 변화'라는 표현에서 ESG 경영의 실효성에 대해 이야기하고 있음을 알 수 있습니다."),
                                opt("기업의 주가 변동", "언급되지 않은 주제입니다."),
                                opt("신입사원 채용 기준", "언급되지 않은 주제입니다."),
                                opt("기업의 해외 진출 전략", "언급되지 않은 주제입니다.")
                        ), 0, "🔵 어휘 부족 — 'ESG 경영'이라는 용어를 모르면 주제를 정확히 파악하기 어렵습니다.", "[화제파악 마인드맵] ESG 경영+실질적 변화(핵심 표현) → ESG 경영의 실효성(화제). 핵심 표현을 모아 주제를 파악하세요.")),
                onePassage(PassageCategory.LISTENING, "화제 고르기",
                        "남자: 최근 인공지능 번역 기술이 발전하면서 외국어 학습의 필요성에 대한 논쟁이 다시 일고 있어요.\n여자: 저는 오히려 소통의 깊이를 위해서는 언어 학습이 여전히 중요하다고 생각해요.",
                        q("두 사람이 무엇에 대해 이야기하고 있는지 고르십시오.", List.of(
                                opt("인공지능 번역 시대의 외국어 학습 필요성", "정답: '인공지능 번역 기술', '외국어 학습의 필요성'이라는 표현에서 이 주제로 이야기하고 있음을 알 수 있습니다."),
                                opt("인공지능 개발 비용", "언급되지 않은 주제입니다."),
                                opt("해외여행 준비물", "언급되지 않은 주제입니다."),
                                opt("번역 프로그램 가격 비교", "언급되지 않은 주제입니다.")
                        ), 0, "🟢 의도 파악 실패 — '인공지능 번역'이라는 기술 언급만 듣고 다른 주제로 착각하기 쉽습니다.", "[화제파악 마인드맵] 번역 기술+학습 필요성 논쟁(핵심 표현) → 외국어 학습 논쟁(화제). 두 사람의 입장 차이에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "화제 고르기",
                        "여자: 최근 몇 년 사이 1인 가구가 빠르게 늘면서 주거 형태와 소비 패턴도 크게 달라지고 있대요.\n남자: 맞아요, 소형 가전이나 소포장 식품 시장이 커지는 것도 그런 흐름과 무관하지 않겠죠.",
                        q("두 사람이 무엇에 대해 이야기하고 있는지 고르십시오.", List.of(
                                opt("1인 가구 증가에 따른 사회·경제적 변화", "정답: '1인 가구', '주거 형태와 소비 패턴 변화'라는 표현에서 이 주제로 이야기하고 있음을 알 수 있습니다."),
                                opt("전통 시장 활성화 방안", "언급되지 않은 주제입니다."),
                                opt("가족 여행 계획", "언급되지 않은 주제입니다."),
                                opt("주택 대출 금리", "언급되지 않은 주제입니다.")
                        ), 0, "🔵 어휘 부족 — '소형 가전', '소포장 식품'이라는 구체적 사례만 보고 전체 주제를 놓치기 쉽습니다.", "[화제파악 마인드맵] 1인 가구 증가+소비 패턴 변화(핵심 표현) → 사회 변화(화제). 구체적 사례를 아우르는 전체 주제를 파악하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동 고르기",
                        "남자: 이번 학술대회 발표 원고를 아직 제출하지 못했는데, 마감이 얼마 안 남아서 걱정이에요.\n여자: 제가 초록 검토는 끝냈으니 본문 구성만 조언해 드릴게요. 지금 같이 볼까요?\n남자: 네, 정말 감사해요.",
                        q("여자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("남자의 원고 본문 구성에 대해 조언한다.", "정답: 여자는 '본문 구성만 조언해 드리겠다'고 말했습니다."),
                                opt("원고를 대신 제출한다.", "언급되지 않은 내용입니다."),
                                opt("초록을 다시 검토한다.", "초록 검토는 이미 끝났다고 언급되었습니다."),
                                opt("학술대회 참가를 취소한다.", "언급되지 않은 내용입니다.")
                        ), 0, "🔴 시간/장소 혼동 — 이미 끝난 작업(초록 검토)과 앞으로 할 작업(본문 조언)을 헷갈리기 쉽습니다.", "[행동추론 마인드맵] 여자의 마지막 말(본문 구성 조언) → 이어질 행동. 이미 끝난 일과 앞으로 할 일을 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동 고르기",
                        "여자: 이번 실험 데이터 분석에 통계 프로그램을 새로 배워야 할 것 같아요.\n남자: 제가 예전에 써 본 적이 있으니 기본 사용법을 알려 드릴게요.\n여자: 네, 그럼 오늘 오후에 시간 괜찮으세요?",
                        q("남자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("여자에게 통계 프로그램 사용법을 알려 준다.", "정답: 남자는 '기본 사용법을 알려 드리겠다'고 말했습니다."),
                                opt("데이터 분석을 대신 해 준다.", "언급되지 않은 내용입니다."),
                                opt("새로운 프로그램을 개발한다.", "언급되지 않은 내용입니다."),
                                opt("실험을 처음부터 다시 진행한다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — '사용법을 알려 준다'는 말을 다른 행동으로 착각하기 쉽습니다.", "[행동추론 마인드맵] 남자의 마지막 말(알려 드릴게요) → 이어질 행동. 마지막 발화자의 말에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: 이번 학회 등록 마감이 원래 이번 주 금요일이었는데 연장됐다고 들었어요.\n여자: 네, 다음 주 화요일까지로 연장됐어요. 다만 등록비는 조기 등록 할인이 끝나서 좀 올랐어요.\n남자: 아, 그럼 서둘러야겠네요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("학회 등록 마감이 연장되었다.", "정답: '다음 주 화요일까지로 연장됐다'고 언급되었습니다."),
                                opt("등록비가 더 저렴해졌다.", "조기 등록 할인이 끝나서 올랐다고 언급되었습니다."),
                                opt("등록 마감은 원래대로 이번 주 금요일이다.", "연장되었다고 언급되었습니다."),
                                opt("등록비는 변동이 없다.", "올랐다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 마감일 연장과 등록비 변동을 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 마감일 변화와 등록비 변화를 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "여자: 이번 공동 연구 프로젝트에 참여하는 기관이 세 곳에서 다섯 곳으로 늘었다고 들었어요.\n남자: 네, 그만큼 예산도 늘어서 연구 기간은 오히려 한 달 단축됐다고 하더라고요.\n여자: 효율적으로 진행되겠네요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("참여 기관 수가 늘어났다.", "정답: '세 곳에서 다섯 곳으로 늘었다'고 언급되었습니다."),
                                opt("연구 기간이 늘어났다.", "한 달 단축됐다고 언급되었습니다."),
                                opt("예산이 줄어들었다.", "예산이 늘었다고 언급되었습니다."),
                                opt("참여 기관 수는 변동이 없다.", "늘었다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 참여 기관 수 증가와 연구 기간 단축을 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 늘어난 것(기관 수, 예산)과 줄어든 것(기간)을 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 요즘 온라인 공론장에서 익명성이 오히려 무책임한 발언을 부추기는 것 같아요.\n여자: 저는 익명성 자체보다 그것을 악용하는 소수의 문제라고 봐요. 익명성 덕분에 약자들이 목소리를 낼 수 있는 순기능도 분명히 있으니까요.\n남자: 그런 측면도 있겠네요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("익명성 자체보다 이를 악용하는 문제에 주목해야 하며 순기능도 있다.", "정답: '악용하는 소수의 문제', '순기능도 분명히 있다'는 말에서 여자의 생각이 드러납니다."),
                                opt("온라인 공론장의 익명성은 전면 폐지해야 한다.", "여자의 생각과 반대됩니다."),
                                opt("익명성은 아무런 순기능이 없다.", "여자의 생각과 반대됩니다."),
                                opt("온라인 공론장 자체를 없애야 한다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 남자의 우려(익명성이 문제)를 여자의 생각으로 착각하기 쉽습니다.", "[중심생각 마인드맵] 여자의 직접 발언(악용 문제+순기능)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "여자: 최근 기업들이 재택근무를 다시 축소하는 추세인데, 저는 그게 아쉬워요. 성과만 명확히 관리된다면 근무 장소는 크게 중요하지 않다고 생각하거든요.\n남자: 저도 어느 정도 동의해요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("성과 관리가 명확하다면 근무 장소는 중요하지 않다.", "정답: '성과만 명확히 관리된다면 근무 장소는 크게 중요하지 않다'는 말에서 여자의 생각이 드러납니다."),
                                opt("재택근무는 반드시 확대되어야 한다.", "언급되지 않은 내용입니다."),
                                opt("사무실 근무만이 성과를 보장한다.", "여자의 생각과 반대됩니다."),
                                opt("근무 장소는 성과와 무관하지 않다고 본다.", "여자는 근무 장소가 중요하지 않다고 봅니다.")
                        ), 0, "🟢 의도 파악 실패 — 재택근무 축소라는 현상만 보고 여자의 진짜 생각을 놓치기 쉽습니다.", "[중심생각 마인드맵] 여자의 말(성과 관리+장소는 안 중요)에서 중심 생각을 확인하세요."))
        );

        List<PassageSeed> lv56w1_1st_l21to30 = List.of(
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "여자: (안내 방송) 이번 학술대회 참가자 여러분께 안내드립니다. 오후 세션 발표 장소가 3층 대강당에서 4층 세미나실로 변경되었습니다. 자료집은 등록 데스크에서 계속 배부하고 있으니 참고하시기 바랍니다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("오후 세션 발표 장소가 변경되었다.", "정답: '3층 대강당에서 4층 세미나실로 변경되었다'고 언급되었습니다."),
                                opt("발표는 3층에서 그대로 진행된다.", "4층으로 변경되었다고 언급되었습니다."),
                                opt("자료집 배부는 종료되었다.", "계속 배부하고 있다고 언급되었습니다."),
                                opt("오전 세션 장소가 변경되었다.", "오후 세션이 변경되었다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 변경 전 장소와 변경 후 장소, 오전과 오후 세션을 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 변경 전/후 장소와 세션 시간대를 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: 이번 국책 연구 과제 지원 규모가 지난해보다 20% 늘었다고 들었는데 맞아요?\n여자: 네, 맞아요. 다만 선정 기준은 더 까다로워져서 경쟁률은 오히려 높아졌다고 하더라고요.\n남자: 지원은 늘었지만 들어가기는 더 어려워졌군요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("지원 규모는 늘었지만 경쟁률은 높아졌다.", "정답: '지원 규모가 20% 늘었다'와 '경쟁률은 오히려 높아졌다'가 모두 언급되었습니다."),
                                opt("지원 규모와 경쟁률 모두 줄었다.", "둘 다 늘었다고 언급되었습니다."),
                                opt("선정 기준이 더 완화되었다.", "더 까다로워졌다고 언급되었습니다."),
                                opt("경쟁률은 지난해와 동일하다.", "높아졌다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 지원 규모 증가와 선정 난이도 상승을 별개로 헷갈리기 쉽습니다.", "[일치판단 마인드맵] 늘어난 것(지원 규모)과 어려워진 것(선정 기준, 경쟁률)을 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 의도 고르기",
                        "여자: 팀장님, 이번 분기 보고서에 시장 전망 부분을 좀 더 보강했으면 하는데 검토 부탁드려도 될까요?\n남자: 네, 오늘 중으로 살펴보고 의견 드릴게요.\n여자: 감사합니다. 갑자기 부탁드려서 죄송해요.",
                        q("여자가 남자에게 말하는 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("보고서 보강 부분에 대한 검토를 요청하기 위해서", "정답: '검토 부탁드려도 될까요'라는 표현에서 의도가 드러납니다."),
                                opt("보고서 제출을 취소하기 위해서", "언급되지 않은 내용입니다."),
                                opt("시장 전망 부분을 삭제해 달라고 요청하기 위해서", "보강을 요청하는 것이지 삭제 요청이 아닙니다."),
                                opt("보고서를 대신 작성해 달라고 요청하기 위해서", "검토를 부탁하는 것이지 대신 작성해 달라는 것이 아닙니다.")
                        ), 0, "🟢 의도 파악 실패 — 정중한 사과 표현 때문에 부탁이 아닌 다른 의도로 착각하기 쉽습니다.", "[의도파악 마인드맵] 보강+검토 부탁(핵심 표현) → 검토 요청(의도). 부탁의 핵심 내용에 집중하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 의도 고르기",
                        "남자: 소장님, 이번 실험 결과가 예상과 다르게 나왔다고 들었는데 정말인가요?\n여자: 네, 맞아요. 그래서 원인을 다시 분석해 보려고 합니다.\n남자: 그렇군요, 예상 못 한 결과일수록 더 자세히 들여다볼 필요가 있겠네요.",
                        q("여자가 말한 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("실험 결과가 예상과 다르다는 사실을 확인해 주기 위해서", "정답: 남자의 질문에 '네, 맞아요'라고 확인해 주고 있습니다."),
                                opt("실험을 중단하겠다고 알리기 위해서", "언급되지 않은 내용입니다."),
                                opt("실험 결과를 은폐하기 위해서", "언급되지 않은 내용입니다."),
                                opt("실험 자체를 부정하기 위해서", "언급되지 않은 내용입니다.")
                        ), 0, "🟣 기타(부주의) — 확인 응답을 다른 의도로 착각하기 쉽습니다.", "[의도파악 마인드맵] 질문에 대한 확인 응답(네, 맞아요)에서 의도를 파악하세요.")),
                multiQ(PassageCategory.LISTENING, "강연(주제/세부 내용)",
                        "여자: (강연) 오늘은 공유 경제의 이면에 대해 말씀드리려 합니다. 자원의 효율적 활용이라는 이상적인 취지로 출발했지만, 실제로는 소수 플랫폼 기업이 시장을 독점하며 기존 소상공인의 생계를 위협하는 부작용도 함께 나타나고 있습니다. 공유 경제가 본래의 취지를 살리려면 공정한 경쟁 환경을 조성할 제도적 보완이 반드시 필요합니다.",
                        q("여자가 말하는 내용의 핵심 주제로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("공유 경제의 부작용과 제도적 보완의 필요성", "정답: 플랫폼 독점과 소상공인 위협이라는 부작용을 지적하고 제도적 보완의 필요성을 강조하고 있습니다."),
                                opt("공유 경제의 역사와 발전 과정", "역사와 발전 과정은 언급되지 않았습니다."),
                                opt("공유 경제 플랫폼의 수익 구조", "수익 구조는 언급되지 않았습니다."),
                                opt("소상공인 지원 정책의 종류", "구체적인 정책 종류는 언급되지 않았습니다.")
                        ), 0, "🟢 의도 파악 실패 — 이상적인 취지 언급 부분만 듣고 전체 주제를 오해하기 쉽습니다.", "[담화파악 마인드맵] '하지만 실제로는'으로 이어지는 부작용과 제도적 보완 필요성에 주목하세요."),
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("공유 경제는 소상공인의 생계를 위협할 수 있다.", "정답: '기존 소상공인의 생계를 위협하는 부작용도 함께 나타나고 있다'고 언급되었습니다."),
                                opt("공유 경제는 항상 공정한 경쟁을 보장한다.", "공정한 경쟁 환경 조성이 필요하다고 언급되어 아직 미흡함을 알 수 있습니다."),
                                opt("소수 플랫폼 기업의 독점 문제는 없다.", "시장을 독점하는 문제가 있다고 언급되었습니다."),
                                opt("공유 경제는 자원 낭비를 조장한다.", "효율적 활용이라는 취지로 출발했다고 언급되어 반대됩니다.")
                        ), 0, "🔴 시간/장소 혼동 — 원래 취지(효율적 활용)와 실제 나타난 부작용(독점)을 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 원래 취지와 실제 나타난 부작용을 각각 구분해서 확인하세요.")),
                multiQ(PassageCategory.LISTENING, "강연(주제/세부 내용)",
                        "남자: (강연) 오늘은 빅데이터 시대의 개인정보 보호에 대해 이야기해 보겠습니다. 데이터 활용이 산업 혁신의 핵심 동력이라는 점은 분명하지만, 이 과정에서 개인의 프라이버시가 소홀히 다뤄지는 경우가 적지 않습니다. 데이터의 가치와 개인정보 보호라는 두 가치를 어떻게 조화시킬 것인가가 앞으로의 중요한 과제입니다.",
                        q("남자가 말하는 내용의 핵심 주제로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("데이터 활용과 개인정보 보호의 조화 과제", "정답: 데이터 가치와 개인정보 보호를 조화시키는 것이 과제라고 명시적으로 언급했습니다."),
                                opt("빅데이터 기술의 발전 역사", "발전 역사는 언급되지 않았습니다."),
                                opt("개인정보 유출 사건 사례", "구체적인 사건 사례는 언급되지 않았습니다."),
                                opt("데이터 저장 기술의 종류", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 데이터 활용의 긍정적 측면만 듣고 전체 주제를 오해하기 쉽습니다.", "[담화파악 마인드맵] 마지막 문장(두 가치를 조화시키는 과제)에서 전체 주제를 확인하세요."),
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("데이터 활용 과정에서 개인정보가 소홀히 다뤄지기도 한다.", "정답: '개인의 프라이버시가 소홀히 다뤄지는 경우가 적지 않다'고 언급되었습니다."),
                                opt("데이터 활용은 산업 혁신과 무관하다.", "산업 혁신의 핵심 동력이라고 언급되어 반대됩니다."),
                                opt("개인정보 보호는 이미 완벽하게 이루어지고 있다.", "소홀히 다뤄지는 경우가 있다고 언급되어 반대됩니다."),
                                opt("데이터 가치와 개인정보 보호는 무관한 별개의 문제이다.", "두 가치를 조화시켜야 할 과제로 언급되어 서로 관련이 있습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 데이터 활용의 순기능과 개인정보 보호 문제를 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 데이터 활용의 순기능과 개인정보 보호의 문제점을 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 최근 대학들이 융합 전공을 확대하고 있는데, 저는 오히려 한 분야를 깊이 파는 전문성이 더 중요하다고 생각해요.\n여자: 저는 다르게 생각해요. 복잡한 현실 문제는 한 분야의 지식만으로 풀기 어려운 경우가 많잖아요. 여러 분야를 넘나드는 융합적 사고가 오히려 경쟁력이 된다고 봐요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("복잡한 문제 해결을 위해서는 융합적 사고가 경쟁력이 된다.", "정답: '여러 분야를 넘나드는 융합적 사고가 오히려 경쟁력이 된다'는 말에서 여자의 생각이 드러납니다."),
                                opt("한 분야의 전문성만이 유일한 경쟁력이다.", "여자의 생각과 반대되며 남자의 입장에 가깝습니다."),
                                opt("융합 전공은 확대할 필요가 없다.", "여자의 생각과 반대됩니다."),
                                opt("현실 문제는 단일 분야 지식으로 충분히 해결된다.", "여자의 생각과 반대됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 남자의 주장(전문성이 중요)을 여자의 생각으로 착각하기 쉽습니다.", "[중심생각 마인드맵] 여자의 직접 발언(융합적 사고가 경쟁력)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도·어조 파악",
                        "여자: (토론) 저는 규제 완화가 무조건 시장 활성화로 이어진다는 주장에 동의하기 어렵습니다. 오히려 최소한의 안전장치 없이 이루어진 규제 완화가 더 큰 사회적 비용을 초래한 사례를 우리는 이미 여러 차례 목격했습니다.",
                        q("여자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("규제 완화를 무조건 긍정적으로 보는 시각에 반박하고 있다.", "정답: '동의하기 어렵다'는 표현과 이어지는 반례에서 반박하는 태도가 드러납니다."),
                                opt("모든 규제를 폐지해야 한다고 주장하고 있다.", "언급되지 않은 내용이며 여자의 취지와도 다릅니다."),
                                opt("규제 완화 자체를 전면 지지하고 있다.", "여자의 태도와 반대됩니다."),
                                opt("시장 활성화가 불가능하다고 단정하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 반박하는 발언을 일반적 주장 소개로 착각하기 쉽습니다.", "[태도파악 마인드맵] '동의하기 어렵다'는 표현에서 반박의 태도를 확인하세요."))
        );

        List<PassageSeed> lv56w1_1st_r31to40 = List.of(
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 조사 결과는 기존 통념과 ( ) 결과를 보여 주었다. 전문가들도 이 예상 밖의 결과에 놀라움을 표했다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("상반되는", "정답: '예상 밖의 결과'라는 뒤 문장과 자연스럽게 연결되는 것은 기존 통념과 반대되는 결과입니다."),
                                opt("부합하는", "예상 밖이라는 뒤 내용과 모순됩니다."),
                                opt("일치하는", "예상 밖이라는 뒤 내용과 모순됩니다."),
                                opt("유사한", "예상 밖이라는 뒤 내용과 어울리지 않습니다.")
                        ), 0, "🔵 어휘 부족 — '상반되다'라는 단어를 모르면 빈칸을 채우기 어렵습니다.", "[빈칸추론 마인드맵] 결과(예상 밖) ← 원인(통념과 상반됨). 뒤 문장에서 단서를 찾으세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 정책은 단기적 효과는 있었으나 장기적으로는 오히려 부작용을 ( ). 전문가들은 근본적인 대안이 필요하다고 지적한다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("초래했다", "정답: '근본적인 대안이 필요하다'는 뒤 문장과 자연스럽게 연결되는 것은 부작용을 낳았다는 의미입니다."),
                                opt("해소했다", "대안이 필요하다는 뒤 내용과 모순됩니다."),
                                opt("예방했다", "부작용이라는 앞 단어와 모순됩니다."),
                                opt("차단했다", "부작용이라는 앞 단어와 모순됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 단기적 효과만 보고 전체 결론을 놓치기 쉽습니다.", "[빈칸추론 마인드맵] 단기 효과(긍정) ↔ 장기 부작용(부정). 대조 구조에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 연구는 기존 이론의 한계를 ( ) 새로운 분석 틀을 제시했다는 점에서 학계의 주목을 받고 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("보완하는", "정답: '새로운 분석 틀을 제시했다'는 뒤 내용과 자연스럽게 연결되는 것은 한계를 보완한다는 의미입니다."),
                                opt("무시하는", "학계의 주목을 받는다는 긍정적 결과와 어울리지 않습니다."),
                                opt("확대하는", "한계를 확대한다는 것은 부정적 의미로 문맥과 맞지 않습니다."),
                                opt("반복하는", "새로운 분석 틀 제시라는 내용과 모순됩니다.")
                        ), 0, "🔵 어휘 부족 — '보완하다'라는 단어의 뜻을 정확히 몰라 혼동할 수 있습니다.", "[빈칸추론 마인드맵] 한계 보완(원인) → 새로운 분석 틀(결과) → 학계 주목(추가 결과). 뒤 문장까지 함께 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 개정안은 기존 규제를 ( ) 완화하는 방향으로 추진되고 있어 업계의 반응이 엇갈리고 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("대폭", "정답: '업계의 반응이 엇갈린다'는 결과와 자연스럽게 연결되는 것은 큰 폭의 완화입니다."),
                                opt("전혀", "완화 방향으로 추진된다는 내용과 모순됩니다."),
                                opt("거의", "완화 방향으로 추진된다는 내용과 다소 모순됩니다."),
                                opt("잠시", "지속적인 정책 방향과는 어울리지 않는 일시적 표현입니다.")
                        ), 0, "🟣 기타(부주의) — 정도 부사의 뉘앙스를 정확히 구분하지 못하면 혼동될 수 있습니다.", "[빈칸추론 마인드맵] 규제 완화(원인) → 반응 엇갈림(결과). 뒤 문장의 결과에서 정도를 추론하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "두 학파의 입장은 근본적으로 대립한다. 한쪽은 시장의 자율성을 중시하지만 다른 쪽은 ( ) 개입의 필요성을 강조한다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("정부의", "정답: '대립한다'고 했으므로 시장 자율성과 대비되는 정부 개입이 자연스럽습니다."),
                                opt("시장의", "시장 자율성과 같은 입장이 되어 '대립'과 모순됩니다."),
                                opt("개인의", "정부 개입의 맥락과 어울리지 않습니다."),
                                opt("기업의", "정부 개입의 맥락과 어울리지 않습니다.")
                        ), 0, "🟢 의도 파악 실패 — '대립한다'는 단어를 놓치면 비슷한 입장을 고르는 실수를 하기 쉽습니다.", "[빈칸추론 마인드맵] 대립(단서) → 시장 자율성의 반대(정부 개입). 앞 문장의 대조 관계에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 실험은 통제된 환경에서만 이루어져야 한다. 반드시 ( )을 거쳐야 실험 결과의 신뢰성을 확보할 수 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("엄격한 검증 절차", "정답: 통제된 환경과 신뢰성 확보라는 문맥에서 검증 절차가 자연스럽게 연결됩니다."),
                                opt("자유로운 토론", "실험의 신뢰성 확보와는 다소 무관한 절차입니다."),
                                opt("비공개 처리", "신뢰성 확보와 직접적인 관련이 없는 내용입니다."),
                                opt("무작위 배포", "실험 절차와 맞지 않는 표현입니다.")
                        ), 0, "🔴 시간/장소 혼동 — 통제 조건과 검증 절차를 혼동하기 쉽습니다.", "[빈칸추론 마인드맵] 통제된 환경(조건) → 검증 절차(방법) → 신뢰성 확보(결과). 앞뒤 문맥을 함께 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 조사는 표본 수가 지난번보다 훨씬 많아서 통계적 ( )이 더 높다고 평가된다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("신뢰도", "정답: 표본 수 증가와 자연스럽게 연결되는 통계 용어는 신뢰도입니다."),
                                opt("가격", "통계적 평가와 무관한 내용입니다."),
                                opt("난이도", "통계적 평가와 무관한 내용입니다."),
                                opt("속도", "통계적 평가와 무관한 내용입니다.")
                        ), 0, "🔵 어휘 부족 — '신뢰도'라는 통계 용어를 모르면 빈칸을 채우기 어렵습니다.", "[빈칸추론 마인드맵] 표본 수 증가(원인) → 신뢰도 향상(결과). 앞 문장에서 단서를 찾으세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 이론은 발표 당시에는 크게 주목받지 못했으나, 수십 년이 지난 지금에서야 그 ( )이 재평가되고 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("가치", "정답: 재평가된다는 문맥과 자연스럽게 연결되는 것은 이론의 가치입니다."),
                                opt("실패", "재평가라는 긍정적 흐름과 모순됩니다."),
                                opt("오류", "재평가라는 긍정적 흐름과 어울리지 않습니다."),
                                opt("무관심", "재평가라는 흐름과 반대되는 의미입니다.")
                        ), 0, "🟢 의도 파악 실패 — 발표 당시 주목받지 못했다는 내용만 보고 부정적 결론으로 착각하기 쉽습니다.", "[빈칸추론 마인드맵] 과거(주목받지 못함) → 현재(재평가). 시간 흐름에 따른 반전에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 협상은 양측이 팽팽히 맞서면서 예상보다 훨씬 ( ) 진행되고 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("더디게", "정답: '팽팽히 맞선다'는 상황과 자연스럽게 연결되는 것은 협상이 더디게 진행된다는 의미입니다."),
                                opt("신속하게", "팽팽히 맞선다는 상황과 모순됩니다."),
                                opt("순조롭게", "팽팽히 맞선다는 상황과 모순됩니다."),
                                opt("일찍", "협상 속도를 나타내는 문맥과 어울리지 않습니다.")
                        ), 0, "🔵 어휘 부족 — '더디다'라는 단어를 모르면 빈칸을 채우기 어렵습니다.", "[빈칸추론 마인드맵] 팽팽히 맞섬(원인) → 더디게 진행(결과). 앞 문장에서 단서를 찾으세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "[학술대회 안내]\n제15회 국제 학술대회가 다음 달 12일부터 14일까지 사흘간 개최됩니다. 발표 신청은 이번 달 말까지이며, 심사를 거쳐 채택된 논문에 한해 발표 기회가 주어집니다. 참가 등록은 학회 홈페이지를 통해서만 가능합니다.",
                        q("이 안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("이 학술대회는 사흘간 진행된다.", "정답: '다음 달 12일부터 14일까지 사흘간 개최된다'고 명시되어 있습니다."),
                                opt("모든 신청자에게 발표 기회가 주어진다.", "심사를 거쳐 채택된 논문만 발표 기회가 주어진다고 언급되었습니다."),
                                opt("참가 등록은 현장에서도 가능하다.", "홈페이지를 통해서만 가능하다고 언급되었습니다."),
                                opt("발표 신청 마감은 대회 당일이다.", "이번 달 말까지라고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 신청 마감일과 대회 개최일, 등록 방법을 뒤섞어 혼동하기 쉽습니다.", "[일치판단 마인드맵] 신청 마감, 대회 기간, 등록 방법을 각각 구분해서 확인하세요."))
        );

        return new WeekSeed("5~6급 컬러맵 기초 다지기",
                "5급 수준의 학술·시사·전문 담화를 색깔 태그와 마인드맵으로 도식화하며 고급 어휘·논리 구조에 익숙해진다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(의도·태도 파악, 일치하는 내용, 중심 생각, 화제, 이어질 행동, 강연) + 읽기 20(빈칸 추론, 안내문 일치). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(lv56w1_1st_l1to10, lv56w1_1st_l11to20, lv56w1_1st_l21to30, lv56w1_1st_r31to40))
                ));
    }

    // ===================== 저장 =====================

    private void saveCurriculumWithDays(Curriculum curriculum, List<WeekSeed> weekSeeds) {
        List<CurriculumWeek> weeks = new ArrayList<>();
        List<CurriculumDay> allDays = new ArrayList<>();
        int dayNumber = 0;

        for (int w = 0; w < weekSeeds.size(); w++) {
            WeekSeed weekSeed = weekSeeds.get(w);
            CurriculumWeek week = new CurriculumWeek();
            week.setCurriculum(curriculum);
            week.setWeekNumber(w + 1);
            week.setTitle(weekSeed.title());
            week.setGoal(weekSeed.goal());
            week.setTemplate(weekSeed.template());
            week.setActivities(List.of());
            weeks.add(week);

            List<DaySeed> daySeeds = weekSeed.days();
            for (int d = 0; d < daySeeds.size(); d++) {
                DaySeed daySeed = daySeeds.get(d);
                dayNumber++;
                CurriculumDay dayEntity = new CurriculumDay();
                dayEntity.setCurriculum(curriculum);
                dayEntity.setWeek(week);
                dayEntity.setDayInWeek(d + 1);
                dayEntity.setDayNumber(dayNumber);
                dayEntity.setTask(daySeed.task());
                dayEntity.setPassages(buildPassages(dayEntity, daySeed.passages()));
                allDays.add(dayEntity);
            }
        }

        curriculum.setWeeks(weeks);
        curriculumRepository.save(curriculum);
        curriculumDayRepository.saveAll(allDays);
    }

    private List<CurriculumPassage> buildPassages(CurriculumDay dayEntity, List<PassageSeed> passageSeeds) {
        List<CurriculumPassage> passages = new ArrayList<>();
        for (int p = 0; p < passageSeeds.size(); p++) {
            PassageSeed passageSeed = passageSeeds.get(p);
            CurriculumPassage passage = new CurriculumPassage();
            passage.setDay(dayEntity);
            passage.setCategory(passageSeed.category());
            passage.setOrderIndex(p + 1);
            passage.setSubType(passageSeed.subType());
            passage.setPassageText(passageSeed.passageText());
            passage.setDiagramSvg(passageSeed.diagramSvg());
            passage.setProblems(buildProblems(passage, passageSeed.problems()));
            passages.add(passage);
        }
        return passages;
    }

    private List<CurriculumProblem> buildProblems(CurriculumPassage passage, List<ProblemSeed> problemSeeds) {
        List<CurriculumProblem> problems = new ArrayList<>();
        for (int i = 0; i < problemSeeds.size(); i++) {
            ProblemSeed problemSeed = problemSeeds.get(i);
            CurriculumProblem problem = new CurriculumProblem();
            problem.setPassage(passage);
            problem.setOrderIndex(i + 1);
            problem.setQuestionText(problemSeed.question());
            problem.setOptions(problemSeed.options().stream().map(OptionSeed::text).toList());
            problem.setCorrectAnswerIndex(problemSeed.correctIndex());
            problem.setOptionExplanations(problemSeed.options().stream().map(OptionSeed::note).toList());
            problem.setTrapNote(problemSeed.trapNote());
            problem.setStrategyTip(problemSeed.strategyTip());
            problems.add(problem);
        }
        return problems;
    }
}
