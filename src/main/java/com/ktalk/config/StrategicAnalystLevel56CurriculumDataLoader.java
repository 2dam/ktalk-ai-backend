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
 * 전략적 분석가(STRATEGIC_ANALYST) 유형의 5~6급 "TOPIK 기출 완전분석" 커리큘럼을 심는다.
 * 1~2급(StrategicAnalystCurriculumDataLoader), 3~4급(StrategicAnalystLevel34CurriculumDataLoader)과
 * 완전히 분리된 별도의 8주 과정으로, 같은 learner_type이라도 targetLevelFrom(LEVEL_5)으로 구분되는
 * 별도 Curriculum 레코드를 갖는다. 하루 학습량은 "차" 단위(40문항)와 1:1로 대응한다.
 * WEEK1~4는 5급(기출문제 스타일), WEEK5~8은 6급(시중교재 grammarUnit 스타일), 이어서
 * 5~6급 실전 모의고사 2회 + Final 1회(각 70문항, 실제 TOPIK II를 참고하되 전 객관식으로 재구성)가 이어진다.
 */
@Component
@RequiredArgsConstructor
@Order(12)
public class StrategicAnalystLevel56CurriculumDataLoader implements CommandLineRunner {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumDayRepository curriculumDayRepository;
    private final UserCurriculumProgressRepository userCurriculumProgressRepository;

    private static final String WEEK1_ANSWER_NOTE_TEMPLATE = "[오답 노트 템플릿 - 1차 40문항용]\n문제를 틀렸을 때 아래 항목을 표시하며 나의 취약 유형을 데이터화해보세요.\n\n문제 번호(1~40) | 틀린 이유(해당 항목 체크) | 취약 유형 코드\n예) 3번 | ② 의도 파악 실패 |\n\n[취약 유형 코드 분석 가이드]\n① 시간/장소 혼동: 대화나 글에 나온 시간, 장소, 숫자 정보를 정확히 기억하지 못함.\n② 의도 파악 실패: 화자나 글쓴이의 진짜 목적이나 주제를 놓침.\n③ 어휘 부족: 5~6급 수준의 전문·추상 어휘를 몰라 내용 이해에 어려움을 겪음.\n④ 기타: 위에 해당하지 않는 오류(예: 부주의, 시간 부족).\n\n같은 코드가 반복해서 나온다면, 다음 학습 때 그 유형을 우선적으로 보완하세요.\n";
    private record OptionSeed(String text, String note) {}
    private record ProblemSeed(String question, List<OptionSeed> options, int correctIndex, String trapNote, String strategyTip) {}
    private record PassageSeed(PassageCategory category, String subType, String passageText, List<ProblemSeed> problems) {}
    private record DaySeed(String task, List<PassageSeed> passages) {}
    private record WeekSeed(String title, String goal, String template, List<DaySeed> days) {}

    private static OptionSeed opt(String text, String note) {
        return new OptionSeed(text, note);
    }

    private static ProblemSeed q(String question, List<OptionSeed> options, int correctIndex, String trapNote, String strategyTip) {
        return new ProblemSeed(question, options, correctIndex, trapNote, strategyTip);
    }

    /** 전략 팁 없이 오답 분석/함정 포인트만 있는 문항용. */
    private static ProblemSeed q(String question, List<OptionSeed> options, int correctIndex, String trapNote) {
        return new ProblemSeed(question, options, correctIndex, trapNote, null);
    }

    private static PassageSeed onePassage(PassageCategory category, String subType, String passageText, ProblemSeed problem) {
        return new PassageSeed(category, subType, passageText, List.of(problem));
    }

    /** 문법/어휘 포인트 하나에 연습문제 여러 개가 딸린 "시중 교재" 스타일 유닛용. */
    private static PassageSeed grammarUnit(String subType, String explanation, ProblemSeed... problems) {
        return new PassageSeed(PassageCategory.READING, subType, explanation, List.of(problems));
    }

    /** 실전 모의고사에서 지문 하나에 문제 2개 이상이 딸린 실제 TOPIK 형식용. */
    private static PassageSeed multiQ(PassageCategory category, String subType, String passageText, ProblemSeed... problems) {
        return new PassageSeed(category, subType, passageText, List.of(problems));
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
        curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.STRATEGIC_ANALYST, TopikLevel.LEVEL_5)
                .ifPresent(this::deleteExisting);

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.STRATEGIC_ANALYST);
        curriculum.setTitle("TOPIK 5~6급 기출 완전분석 노트");
        curriculum.setTargetLevelLabel("5~6급 전 과정");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_5);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_6);
        curriculum.setUsageNote(
                "모든 문제에 오답 선택지 분석과 함정 포인트를 상세히 제공합니다. 문법 규칙을 논리적으로 "
                        + "설명하고 문제 풀이 전략을 제시하며, 스스로 오답을 데이터화할 수 있도록 안내합니다.");

        List<WeekSeed> weeks = List.of(week1());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("✅ TOPIK 커리큘럼(전략적 분석가, 5~6급) 생성 시작 — WEEK1(5급 기초, 1차/40문항) 진행 중, WEEK1~4(5급)·WEEK5~8(6급)·모의고사 2회·Final 1회 예정");
    }

    /** 재시딩 전 기존 커리큘럼을 지운다. day는 부모의 cascade 대상이 아니라 먼저 지워야 한다. */
    private void deleteExisting(Curriculum existing) {
        List<CurriculumDay> days = curriculumDayRepository.findByCurriculumId(existing.getId());
        curriculumDayRepository.deleteAll(days);
        userCurriculumProgressRepository.deleteByCurriculumId(existing.getId());
        curriculumRepository.delete(existing);
        curriculumRepository.flush();
    }

    // ===================== WEEK 1: 5급 기초 다지기 (기출문제 스타일) =====================

    private WeekSeed week1() {
        // ----- 1차 40문항 (5급, 독립 세트): 학술·시사, 사회 비평, 전문 분야 담화 중심 -----
        List<PassageSeed> lv5_1st_l1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "남자: 이번 정책 토론회에서 발언 순서가 바뀌었다고 들었는데요.\n여자: 네, 원래 예정된 발제자의 일정이 갑자기 바뀌어서 부득이하게 조정했습니다.\n남자: 그렇군요. 미리 알려 주셔서 감사합니다.",
                        q("여자가 이렇게 말한 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("발언 순서 변경의 불가피한 사정을 설명하려고", "정답: '부득이하게 조정했다'는 발언에서 변경 사유를 해명하려는 의도가 드러납니다."),
                                opt("발제자의 자격을 문제 삼으려고", "자격 문제는 언급되지 않았습니다."),
                                opt("토론회 자체를 취소하려고", "취소가 아니라 순서 조정에 대한 설명입니다."),
                                opt("남자에게 발언 순서를 양보해 달라고 요청하려고", "언급되지 않은 내용입니다.")
                        ), 0, "단순 정보 전달을 다른 의도로 확대 해석하게 유도.", "'부득이하게 조정했다'는 표현에서 해명의 의도를 파악하세요.")),
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "여자: 이번 논문 심사에서 제 연구 방법론에 대한 지적이 많았어요.\n남자: 그만큼 꼼꼼히 봐 주셨다는 뜻이니 오히려 발전의 기회로 삼으시면 좋을 것 같아요.\n여자: 그렇게 생각하니 마음이 한결 가벼워지네요.",
                        q("남자가 이렇게 말한 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부정적 지적을 긍정적으로 받아들이도록 격려하려고", "정답: '발전의 기회로 삼으라'는 발언에서 격려의 의도가 드러납니다."),
                                opt("심사위원의 지적이 부당했다고 비판하려고", "심사위원을 비판하는 내용이 아닙니다."),
                                opt("여자에게 논문을 다시 쓰라고 권유하려고", "언급되지 않은 내용입니다."),
                                opt("자신의 연구 경험을 자랑하려고", "언급되지 않은 내용입니다.")
                        ), 0, "격려의 의도를 비판이나 자랑으로 오해하게 유도.", "'발전의 기회로 삼으라'는 말에서 상대를 격려하려는 의도를 파악하세요.")),
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "남자: 이번 보고서에 인용하신 통계 자료의 출처가 명확하지 않은 것 같습니다.\n여자: 아, 지적해 주셔서 감사합니다. 원 출처를 다시 확인해서 각주에 명시하도록 하겠습니다.\n남자: 네, 신뢰도를 높이는 데 도움이 될 겁니다.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("보고서의 완성도를 높이기 위해 건설적으로 지적하고 있다.", "정답: 출처 문제를 지적한 뒤 신뢰도 향상을 언급하는 것에서 건설적 태도를 알 수 있습니다."),
                                opt("보고서 전체를 신뢰하지 않고 있다.", "전체 신뢰를 부정하는 것이 아니라 특정 부분을 지적하는 것입니다."),
                                opt("여자의 연구 능력을 폄하하고 있다.", "폄하가 아니라 개선을 위한 지적입니다."),
                                opt("보고서 제출을 거부하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "건설적 지적을 전면 부정이나 폄하로 확대 해석하게 유도.", "지적 이후 이어지는 '신뢰도를 높이는 데 도움이 될 것'이라는 발언에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "여자: (라디오 시사 프로그램) 최근 발표된 인구 통계에 따르면, 생산 가능 인구는 지속적으로 감소하는 반면 고령 인구 비율은 빠르게 증가하고 있습니다. 전문가들은 이러한 추세가 향후 노동 시장 구조 전반에 걸쳐 근본적인 변화를 요구할 것이라고 전망합니다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("생산 가능 인구는 계속 줄어들고 있다.", "정답: '생산 가능 인구는 지속적으로 감소한다'고 언급되었습니다."),
                                opt("고령 인구 비율은 감소하고 있다.", "빠르게 증가하고 있다고 언급되었습니다."),
                                opt("노동 시장 구조는 변화가 필요 없다.", "근본적인 변화를 요구할 것이라고 언급되었습니다."),
                                opt("이 통계는 아직 발표되지 않았다.", "최근 발표되었다고 언급되었습니다.")
                        ), 0, "생산 가능 인구와 고령 인구의 증감 방향을 뒤바꿔 제시하여 혼동 유도.", "두 인구 지표의 증감 방향을 각각 정확히 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: (다큐멘터리 내레이션) 이 유적은 발굴 당시 학계의 통념을 뒤집는 증거로 주목받았다. 기존에는 이 지역에 정착 문화가 없었다고 여겨졌으나, 이번 발굴을 통해 대규모 정착 흔적이 확인되면서 관련 이론들이 전면 재검토되고 있다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("이 발굴로 정착 문화가 없었다는 기존 통념이 흔들렸다.", "정답: '기존 통념을 뒤집는 증거로 주목받았다'고 언급되었습니다."),
                                opt("이 지역은 원래부터 정착 문화가 있었다고 알려져 있었다.", "기존에는 정착 문화가 없었다고 여겨졌다고 언급되었습니다."),
                                opt("관련 이론들은 재검토 없이 그대로 유지되고 있다.", "전면 재검토되고 있다고 언급되었습니다."),
                                opt("이 유적은 발굴 당시 주목받지 못했다.", "주목받았다고 언급되었습니다.")
                        ), 0, "기존 통념과 새로운 증거의 내용을 뒤바꿔 제시하여 혼동 유도.", "기존에 알려진 내용과 이번 발굴로 새롭게 밝혀진 내용을 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 최근 기업들이 단기 실적에만 치중하는 경향이 강해지는 것 같아요.\n여자: 저는 장기적인 관점에서 지속 가능한 성장 전략을 세우는 것이 결국 기업의 경쟁력을 좌우한다고 봐요.\n남자: 일리 있는 말씀이네요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("장기적 관점의 지속 가능한 성장 전략이 기업 경쟁력을 좌우한다.", "정답: 여자의 발언에서 직접 드러납니다."),
                                opt("단기 실적에 집중하는 것이 가장 효과적인 전략이다.", "여자의 생각과 반대됩니다."),
                                opt("기업의 경쟁력은 실적과 무관하다.", "언급되지 않은 내용입니다."),
                                opt("장기 전략은 현실성이 없다.", "여자의 생각과 반대됩니다.")
                        ), 0, "남자가 지적한 현상(단기 실적 치중)을 여자의 생각으로 착각하게 유도.", "여자의 직접 발언(장기적 관점, 지속 가능한 성장)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도·어조 파악",
                        "남자: (강연) 흔히 혁신이란 거창한 발명이라고 생각하기 쉽지만, 저는 오히려 기존의 것을 낯설게 바라보는 태도에서 혁신이 시작된다고 믿습니다. 익숙함에 안주하지 않는 시선, 그것이야말로 진정한 혁신의 출발점입니다.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("혁신에 대한 통념을 재해석하며 자신의 견해를 제시하고 있다.", "정답: 통념(거창한 발명)을 언급한 뒤 자신의 견해(낯설게 바라보는 태도)를 제시하고 있습니다."),
                                opt("혁신이라는 개념 자체를 부정하고 있다.", "부정이 아니라 재해석하는 것입니다."),
                                opt("청중의 의견에 전적으로 동의하고 있다.", "청중의 의견은 언급되지 않았습니다."),
                                opt("혁신은 불가능하다고 주장하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "통념 소개 부분만 보고 그것이 화자의 주장이라고 착각하게 유도.", "'하지만 저는 오히려'로 이어지는 화자 자신의 견해에 주목하세요.")),
                multiQ(PassageCategory.LISTENING, "강연(주제/세부 내용)",
                        "여자: (강연) 오늘은 도시 재생 사업의 명암에 대해 말씀드리려 합니다. 낙후된 지역에 활력을 불어넣는다는 긍정적 측면이 부각되곤 하지만, 실제로는 원주민들이 임대료 상승을 감당하지 못해 정든 터전을 떠나는 이른바 '젠트리피케이션' 현상이 동반되는 경우가 적지 않습니다. 재생 사업이 진정한 의미를 가지려면 이러한 부작용을 최소화할 제도적 장치가 함께 마련되어야 합니다.",
                        q("여자가 말하는 내용의 핵심 주제로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("도시 재생 사업의 부작용과 그 대책의 필요성", "정답: 젠트리피케이션이라는 부작용을 지적하고 제도적 장치의 필요성을 강조하고 있습니다."),
                                opt("도시 재생 사업의 성공 사례 소개", "성공 사례가 아니라 부작용을 다루고 있습니다."),
                                opt("젠트리피케이션의 어원과 역사", "어원이나 역사는 언급되지 않았습니다."),
                                opt("원주민 이주 지원 정책의 종류", "구체적인 정책 종류는 언급되지 않았습니다.")
                        ), 0, "긍정적 측면 언급 부분만 듣고 전체 주제를 오해하게 유도.", "'하지만 실제로는'으로 이어지는 부작용과 제도적 장치 필요성에 주목하세요."),
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("도시 재생 사업은 원주민 이주 문제를 동반할 수 있다.", "정답: '원주민들이 정든 터전을 떠나는 현상이 동반되는 경우가 적지 않다'고 언급되었습니다."),
                                opt("도시 재생 사업은 항상 긍정적 효과만 가져온다.", "부작용도 있다고 언급되어 반대됩니다."),
                                opt("젠트리피케이션은 임대료 하락 때문에 발생한다.", "임대료 상승 때문이라고 언급되었습니다."),
                                opt("제도적 장치는 이미 충분히 마련되어 있다.", "함께 마련되어야 한다고 언급되어 아직 미흡함을 알 수 있습니다.")
                        ), 0, "임대료 상승과 하락, 제도적 장치의 현재 상태를 뒤바꿔 제시하여 혼동 유도.", "젠트리피케이션의 원인과 제도적 장치의 필요성을 각각 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도·어조 파악",
                        "남자: (인터뷰) 저는 실패를 두려워하기보다 실패로부터 무엇을 배울 것인가에 더 집중해 왔습니다. 지나고 보니 가장 뼈아팠던 실패가 오히려 지금의 저를 만든 가장 값진 자산이었다고 생각합니다.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("실패의 경험을 성장의 자산으로 긍정적으로 재해석하고 있다.", "정답: '가장 값진 자산'이라는 표현에서 긍정적 재해석의 태도를 알 수 있습니다."),
                                opt("실패를 극도로 두려워하고 있다.", "두려워하기보다 배움에 집중했다고 언급되었습니다."),
                                opt("과거의 실패를 후회하며 자책하고 있다.", "자책이 아니라 긍정적으로 평가하고 있습니다."),
                                opt("실패를 남의 탓으로 돌리고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "'실패'라는 단어만 보고 부정적 태도로 착각하게 유도.", "'가장 값진 자산'이라는 표현에서 긍정적 재해석의 태도를 파악하세요."))
        );

        List<PassageSeed> lv5_1st_l11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "여자: (뉴스) 정부는 오늘 발표한 경제 정책 방향에서 규제 완화보다는 취약 계층에 대한 선별적 지원을 우선 과제로 제시했습니다. 다만 일각에서는 이러한 선별적 지원이 형평성 논란을 불러올 수 있다는 우려도 제기되고 있습니다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("정부는 취약 계층 지원을 우선 과제로 삼았다.", "정답: '취약 계층에 대한 선별적 지원을 우선 과제로 제시했다'고 언급되었습니다."),
                                opt("정부는 규제 완화를 최우선 과제로 삼았다.", "규제 완화보다 선별적 지원을 우선했다고 언급되었습니다."),
                                opt("선별적 지원에 대한 우려는 전혀 없다.", "형평성 논란 우려가 제기되고 있다고 언급되었습니다."),
                                opt("이 정책은 아직 발표되지 않았다.", "오늘 발표했다고 언급되었습니다.")
                        ), 0, "우선 과제(선별적 지원 vs 규제 완화)를 뒤바꿔 제시하여 혼동 유도.", "정부가 무엇을 우선 과제로 제시했는지 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 요즘 온라인 익명성이 자유로운 의견 표출을 돕는다고들 하죠.\n여자: 저는 오히려 그 익명성이 무책임한 발언을 부추기는 측면도 크다고 봐요. 자유에는 반드시 책임이 따라야 하니까요.\n남자: 듣고 보니 그렇네요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("온라인 익명성은 무책임한 발언을 부추길 수 있어 책임이 뒤따라야 한다.", "정답: 여자의 발언에서 직접 드러납니다."),
                                opt("온라인 익명성은 표현의 자유를 위해 무조건 보장되어야 한다.", "여자의 생각과 반대됩니다."),
                                opt("온라인 익명성은 아무런 부작용이 없다.", "여자의 생각과 반대됩니다."),
                                opt("온라인 활동에는 규제가 전혀 필요 없다.", "언급되지 않은 내용입니다.")
                        ), 0, "남자의 초기 발언(자유로운 의견 표출)을 여자의 생각으로 착각하게 유도.", "여자의 반박 발언(무책임한 발언, 책임)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세부 내용 파악",
                        "여자: (다큐멘터리) 이 곤충은 극한의 환경에서도 생존할 수 있는 독특한 생리적 메커니즘을 지니고 있다. 체내 수분을 극단적으로 줄이는 대신, 손상된 세포를 스스로 복구하는 능력을 발달시켜 온 것으로 알려져 있다.",
                        q("이 곤충의 생존 방식으로 언급된 것을 고르십시오.", List.of(
                                opt("체내 수분을 줄이고 손상된 세포를 스스로 복구한다.", "정답: 두 가지 메커니즘이 그대로 언급되었습니다."),
                                opt("체내 수분을 늘려 극한 환경에 대비한다.", "수분을 줄인다고 언급되었습니다."),
                                opt("세포 손상을 방지하는 능력만 있고 복구 능력은 없다.", "손상된 세포를 스스로 복구한다고 언급되었습니다."),
                                opt("환경에 적응하지 못하고 대부분 죽는다.", "언급되지 않은 내용입니다.")
                        ), 0, "수분을 늘림/줄임, 방지/복구 등 유사한 개념을 뒤바꿔 제시하여 혼동 유도.", "두 가지 생존 메커니즘(수분 감소, 세포 복구)을 정확히 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도 파악",
                        "남자: (토론) 저는 이 법안이 시행되면 단기적으로는 혼란이 있겠지만, 장기적으로는 시장의 투명성을 높이는 데 기여할 것이라고 확신합니다. 물론 시행 초기의 부작용을 완화할 보완책도 함께 마련되어야 하겠지요.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("법안의 장기적 효과를 긍정하면서도 보완의 필요성을 인정하고 있다.", "정답: 장기적 기여를 확신하면서도 보완책 마련을 언급하는 균형 잡힌 태도입니다."),
                                opt("법안에 무조건적으로 반대하고 있다.", "장기적으로 긍정적이라고 언급되었습니다."),
                                opt("법안의 부작용에 대해서는 전혀 고려하지 않고 있다.", "보완책이 필요하다고 언급했습니다."),
                                opt("법안 시행을 무기한 연기해야 한다고 주장하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "긍정과 보완 필요성 인정을 무조건적 찬성이나 반대로 단순화하여 혼동 유도.", "'물론'으로 이어지는 단서 조항(보완책 필요)에도 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "여자: (강연) 전통 공예가 단순한 옛것의 재현에 머무르지 않으려면, 현대적 감각과의 접목이 필수적입니다. 실제로 최근 몇몇 공방에서는 전통 기법에 현대적 디자인을 결합한 작품으로 해외 시장에서도 호평을 받고 있습니다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("전통 기법과 현대적 디자인을 결합한 작품이 해외에서 호평받고 있다.", "정답: '전통 기법에 현대적 디자인을 결합한 작품으로 해외 시장에서도 호평을 받고 있다'고 언급되었습니다."),
                                opt("전통 공예는 옛것을 그대로 재현해야 가치가 있다.", "옛것의 재현에 머무르지 않아야 한다고 언급되어 반대됩니다."),
                                opt("현대적 감각과의 접목은 전통 공예에 불필요하다.", "필수적이라고 언급되어 반대됩니다."),
                                opt("해외 시장에서는 전통 공예에 관심이 없다.", "호평을 받고 있다고 언급되어 반대됩니다.")
                        ), 0, "전통 재현과 현대적 접목의 필요성을 뒤바꿔 제시하여 혼동 유도.", "'단순한 재현에 머무르지 않으려면'이라는 조건과 실제 성공 사례를 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 최근 채용 시장에서 자격증 개수를 중시하는 경향이 강한 것 같아요.\n여자: 저는 자격증 개수보다 실제 업무에 적용할 수 있는 실질적인 역량이 더 중요하다고 생각해요.\n남자: 맞는 말씀이네요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("자격증 개수보다 실질적인 역량이 채용에서 더 중요하다.", "정답: 여자의 발언에서 직접 드러납니다."),
                                opt("자격증은 채용에서 전혀 중요하지 않다.", "언급되지 않은 과도한 해석입니다."),
                                opt("자격증 개수가 많을수록 무조건 유리하다.", "여자의 생각과 반대됩니다."),
                                opt("실질적인 역량은 평가하기 어렵다.", "언급되지 않은 내용입니다.")
                        ), 0, "남자가 지적한 현상(자격증 중시)을 여자의 생각으로 착각하게 유도.", "여자의 직접 발언(실질적 역량이 더 중요하다)에서 중심 생각을 확인하세요.")),
                multiQ(PassageCategory.LISTENING, "대담(목적/세부 내용)",
                        "여자: 오늘은 기후 변화 대응을 위한 탄소중립 정책에 대해 전문가와 이야기 나눠 보겠습니다. 선생님, 탄소중립이 산업계에 미치는 영향은 어떻습니까?\n남자: 단기적으로는 전환 비용 부담이 크지만, 장기적으로는 새로운 친환경 산업 생태계를 여는 기회가 될 수 있습니다. 다만 중소기업에 대한 지원 없이는 이 전환 과정에서 격차가 커질 우려가 있습니다.",
                        q("이 대담의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("탄소중립 정책이 산업계에 미치는 영향을 전문가와 논의하기 위해서", "정답: 진행자가 산업계에 미치는 영향을 질문하고 전문가가 답하는 구조입니다."),
                                opt("탄소중립 정책을 전면 폐지하기 위해서", "폐지는 언급되지 않았습니다."),
                                opt("중소기업의 세금 감면을 홍보하기 위해서", "세금 감면은 언급되지 않았습니다."),
                                opt("친환경 산업의 역사를 설명하기 위해서", "역사는 언급되지 않았습니다.")
                        ), 0, "대담의 세부 내용(비용, 격차)에만 집중해 전체 목적을 놓치게 유도.", "진행자의 질문과 전체 대담의 흐름에서 목적을 파악하세요."),
                        q("남자의 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("중소기업 지원이 없으면 전환 과정에서 격차가 커질 수 있다.", "정답: '중소기업에 대한 지원 없이는 격차가 커질 우려가 있다'고 언급되었습니다."),
                                opt("탄소중립은 산업계에 아무런 영향을 주지 않는다.", "단기적 비용 부담과 장기적 기회 모두 언급되었습니다."),
                                opt("탄소중립 전환에는 비용이 전혀 들지 않는다.", "전환 비용 부담이 크다고 언급되었습니다."),
                                opt("중소기업은 지원 없이도 격차 없이 전환할 수 있다.", "지원이 없으면 격차가 커질 우려가 있다고 언급되어 반대됩니다.")
                        ), 0, "비용 부담 유무와 지원의 필요성을 뒤바꿔 제시하여 혼동 유도.", "남자가 강조한 단기 비용과 장기 기회, 지원의 필요성을 각각 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세부 내용 파악",
                        "여자: (강연) 이 화산은 수백 년간 휴면 상태였다가 최근 다시 활동 징후를 보이고 있다. 지질학자들은 주변 지각의 미세한 진동 패턴 변화를 근거로 향후 몇 년 내 소규모 분화 가능성을 제기하고 있다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("지질학자들은 진동 패턴 변화를 근거로 분화 가능성을 제기했다.", "정답: '진동 패턴 변화를 근거로 분화 가능성을 제기하고 있다'고 언급되었습니다."),
                                opt("이 화산은 계속 활발하게 활동해 왔다.", "수백 년간 휴면 상태였다고 언급되었습니다."),
                                opt("대규모 분화가 이미 발생했다.", "소규모 분화 가능성이 제기된 것이며 아직 발생하지 않았습니다."),
                                opt("지각 진동은 전혀 감지되지 않았다.", "미세한 진동 패턴 변화가 감지되었다고 언급되었습니다.")
                        ), 0, "휴면과 활동 상태, 분화 발생 여부를 뒤바꿔 제시하여 혼동 유도.", "화산의 과거 상태와 최근 징후, 미래 가능성을 각각 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 요즘 기업들이 데이터를 많이 수집하는 게 마케팅에 도움이 된다고들 해요.\n여자: 저는 데이터 수집이 개인정보 보호와 균형을 이루지 못하면 오히려 소비자 신뢰를 잃는 역효과를 낳는다고 봐요.\n남자: 일리 있는 지적이네요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("데이터 수집은 개인정보 보호와 균형을 이루어야 한다.", "정답: 여자의 발언에서 직접 드러납니다."),
                                opt("데이터 수집은 많을수록 무조건 좋다.", "여자의 생각과 반대됩니다."),
                                opt("개인정보 보호는 마케팅에 방해가 되므로 최소화해야 한다.", "언급되지 않은 내용이며 여자의 생각과 다릅니다."),
                                opt("소비자 신뢰는 데이터 수집과 무관하다.", "여자의 생각과 반대됩니다.")
                        ), 0, "남자의 초기 발언(데이터 수집이 도움이 된다)을 여자의 생각으로 착각하게 유도.", "여자의 반박 발언(균형, 신뢰 상실 역효과)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도 파악",
                        "여자: (서평 낭독) 이 책은 방대한 자료를 촘촘히 엮어 냈다는 점에서 학문적 성취가 돋보이지만, 지나치게 전문적인 서술 방식으로 인해 일반 독자에게는 다소 진입 장벽이 높다는 아쉬움도 남는다.",
                        q("여자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("책의 학문적 성과를 인정하면서도 대중성의 한계를 지적하고 있다.", "정답: 성취를 인정한 뒤 '아쉬움'을 언급하는 균형 잡힌 평가입니다."),
                                opt("책을 전적으로 부정적으로 평가하고 있다.", "학문적 성취를 인정하고 있으므로 전면 부정이 아닙니다."),
                                opt("책을 무조건적으로 극찬하고 있다.", "진입 장벽이 높다는 아쉬움도 언급하고 있습니다."),
                                opt("책의 내용에 대해 언급하지 않고 있다.", "학문적 성취와 서술 방식에 대해 구체적으로 언급하고 있습니다.")
                        ), 0, "긍정 평가 부분만 보고 극찬으로, 혹은 지적 부분만 보고 전면 부정으로 착각하게 유도.", "'~지만'으로 이어지는 균형 잡힌 평가 구조에 주목하세요."))
        );

        List<PassageSeed> lv5_1st_r1to10 = List.of(
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 연구는 기존 학설의 한계를 지적하는 데 ( ), 대안적 이론틀을 제시했다는 점에서 의의가 크다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("그치지 않고", "정답: 지적에만 머무르지 않고 더 나아가 대안을 제시했다는 문맥에서 자연스럽습니다."),
                                opt("만족하여", "지적에 만족했다는 뜻이 되어 대안 제시라는 다음 내용과 어색하게 이어집니다."),
                                opt("치우쳐", "한쪽으로 치우쳤다는 뜻이 되어 이 문맥과 맞지 않습니다."),
                                opt("좌우되어", "지적에 의해 좌우된다는 뜻이 되어 이 문맥과 맞지 않습니다.")
                        ), 0, "'그치지 않고'와 다른 어휘(만족·치우침·좌우)를 혼동하게 유도.", "지적에 머무르지 않고 더 나아갔다는 흐름을 파악하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "정부의 이번 조치는 단기적 효과에 ( ) 근본적인 구조 개혁으로 이어지지 못했다는 비판을 받고 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("그쳐", "정답: 단기적 효과에만 머물렀다는 뜻으로 '그쳐'가 자연스럽습니다."),
                                opt("힘입어", "덕분에라는 뜻이 되어 비판의 맥락과 맞지 않습니다."),
                                opt("근거하여", "단기적 효과를 근거로 삼았다는 뜻이 되어 이 문맥과 맞지 않습니다."),
                                opt("불구하고", "단기적 효과에도 불구하고라는 뜻이 되어 문맥이 어색해집니다.")
                        ), 0, "한계를 나타내는 '그쳐'와 다른 연결 표현을 혼동하게 유도.", "구조 개혁으로 이어지지 못했다는 비판의 이유를 생각해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 이론은 발표 당시 학계의 ( ) 받았으나, 후속 연구들을 통해 점차 그 타당성이 입증되었다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("냉대를", "정답: 처음에는 부정적으로 받아들여졌다가 나중에 인정받았다는 흐름에서 '냉대'가 자연스럽습니다."),
                                opt("환대를", "환영받았다는 뜻이 되어 뒤에 이어지는 '점차 타당성이 입증되었다'는 반전 구조와 어색하게 이어집니다."),
                                opt("지지를", "지지받았다는 뜻이 되어 반전 구조와 맞지 않습니다."),
                                opt("추앙을", "추앙받았다는 뜻이 되어 반전 구조와 맞지 않습니다.")
                        ), 0, "부정적 초기 반응과 긍정적 반응을 혼동하게 유도.", "'후속 연구를 통해 점차 타당성이 입증되었다'는 반전의 흐름에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 소설은 표면적으로는 평범한 가족사를 다루고 있지만, 그 이면에는 시대의 아픔이 ( ) 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("응축되어", "정답: 표면 아래에 깊은 의미가 압축되어 담겨 있다는 문맥에서 '응축되어'가 자연스럽습니다."),
                                opt("배제되어", "제외되어 있다는 뜻이 되어 이 문맥과 맞지 않습니다."),
                                opt("무마되어", "덮여 가라앉아 있다는 뜻이 되어 문학적 함축의 의미와는 다릅니다."),
                                opt("좌시되어", "가만히 보고만 있다는 뜻으로 이 문맥과 맞지 않습니다.")
                        ), 0, "함축의 의미를 나타내는 '응축'과 무관한 어휘(배제·무마·좌시)를 혼동하게 유도.", "'표면적으로는 ~지만 이면에는'이라는 대조 구조에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "그의 주장은 논리적 일관성이 부족하다는 지적을 ( ) 여전히 많은 지지자를 확보하고 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("받으면서도", "정답: 지적을 받고 있음에도 불구하고 지지를 얻고 있다는 대조 구조에서 자연스럽습니다."),
                                opt("받은 덕분에", "지적 덕분에 지지자를 확보했다는 뜻이 되어 어색합니다."),
                                opt("받지 않고", "지적을 받지 않았다는 뜻이 되어 문맥과 맞지 않습니다."),
                                opt("받을 리 없이", "지적을 받을 리 없다는 뜻이 되어 이미 지적을 받았다는 문맥과 모순됩니다.")
                        ), 0, "양보의 의미(받으면서도)와 원인·부정을 나타내는 표현을 혼동하게 유도.", "지적을 받으면서도 지지를 얻는다는 대조적 상황을 파악하세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "예술 작품의 가치는 창작 당시의 사회적 맥락을 이해할 때 온전히 드러난다. 시대적 배경을 배제한 채 작품을 감상하는 것은 작가가 담고자 했던 의도의 절반만을 이해하는 것과 다름없다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("예술 작품은 창작 당시의 사회적 맥락과 함께 이해해야 한다.", "정답: 첫 문장에서 중심 내용이 직접 드러납니다."),
                                opt("예술 작품은 시대적 배경과 무관하게 감상해야 한다.", "글쓴이의 주장과 반대됩니다."),
                                opt("작가의 의도는 파악할 수 없다.", "언급되지 않은 내용입니다."),
                                opt("사회적 맥락은 예술 감상에 방해가 된다.", "글쓴이의 주장과 반대됩니다.")
                        ), 0, "맥락 이해의 필요성과 불필요성을 뒤바꿔 제시하여 혼동 유도.", "첫 문장(사회적 맥락을 이해할 때 온전히 드러난다)에서 중심 내용을 확인하세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "언론의 역할은 단순히 사실을 전달하는 데 그치지 않는다. 복잡한 사회 현상의 이면을 파헤치고 다양한 관점을 균형 있게 제시함으로써, 시민들이 스스로 판단할 수 있는 토대를 마련해 주는 것이야말로 언론의 본질적 소임이다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("언론은 균형 잡힌 관점 제시로 시민의 판단을 돕는 역할을 해야 한다.", "정답: 마지막 문장에서 언론의 본질적 소임이 직접 드러납니다."),
                                opt("언론은 사실 전달에만 충실하면 된다.", "사실 전달에 그치지 않는다고 언급되어 반대됩니다."),
                                opt("언론은 시민의 판단에 개입해서는 안 된다.", "언급되지 않은 내용이며 글쓴이의 주장과 다릅니다."),
                                opt("언론은 하나의 관점만 제시해야 한다.", "다양한 관점을 균형 있게 제시해야 한다고 언급되어 반대됩니다.")
                        ), 0, "사실 전달에 그치는 것과 그 이상의 역할을 뒤바꿔 제시하여 혼동 유도.", "'단순히 ~ 그치지 않는다'는 표현 뒤에 이어지는 본질적 소임에 주목하세요.")),
                onePassage(PassageCategory.READING, "필자의 태도 파악",
                        "일각에서는 인문학이 실용성이 떨어진다는 이유로 그 가치를 폄하하지만, 나는 인문학적 사유야말로 기술 발전이 놓치기 쉬운 인간에 대한 근본적 성찰을 가능케 한다고 생각한다.",
                        q("필자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("인문학의 가치를 옹호하며 그 필요성을 강조하고 있다.", "정답: '인문학적 사유야말로 근본적 성찰을 가능케 한다'는 표현에서 옹호의 태도가 드러납니다."),
                                opt("인문학의 실용성 부족을 인정하며 비판하고 있다.", "일부의 시각을 소개했을 뿐 필자 본인의 입장은 옹호입니다."),
                                opt("기술 발전이 인문학보다 중요하다고 주장하고 있다.", "언급되지 않은 내용이며 필자의 주장과 다릅니다."),
                                opt("인문학과 기술 발전 모두에 무관심하다.", "무관심이 아니라 명확한 견해를 밝히고 있습니다.")
                        ), 0, "'일각에서는'으로 소개된 통념을 필자 본인의 입장으로 착각하게 유도.", "'나는'으로 시작하는 문장에서 필자 자신의 견해를 확인하세요.")),
                onePassage(PassageCategory.READING, "필자의 태도 파악",
                        "우리는 흔히 완벽한 제도를 설계하면 모든 문제가 해결될 것이라 기대하지만, 나는 아무리 정교한 제도라도 그것을 운용하는 사람들의 의식이 뒷받침되지 않으면 무용지물이 된다고 본다.",
                        q("필자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("제도의 완벽함보다 운용하는 사람들의 의식이 중요하다고 본다.", "정답: '사람들의 의식이 뒷받침되지 않으면 무용지물'이라는 표현에서 필자의 견해가 드러납니다."),
                                opt("완벽한 제도만 있으면 모든 문제가 해결된다고 확신한다.", "이는 통념으로 소개된 것이며 필자의 견해와 반대됩니다."),
                                opt("제도 설계는 무의미하다고 주장한다.", "제도 자체를 부정하는 것이 아니라 운용의 중요성을 강조하고 있습니다."),
                                opt("사람들의 의식은 제도와 무관하다고 본다.", "필자의 주장과 반대됩니다.")
                        ), 0, "'우리는 흔히'로 소개된 통념을 필자 본인의 생각으로 착각하게 유도.", "'나는'으로 시작하는 문장에서 필자 자신의 견해를 확인하세요.")),
                onePassage(PassageCategory.READING, "순서 배열하기",
                        "(가) 그 결과 관련 산업 전반에 걸쳐 구조적 변화가 촉진되었다.\n(나) 이 기술은 처음 등장했을 당시 대부분의 전문가들에게 회의적인 평가를 받았다.\n(다) 그러나 몇 년 지나지 않아 예상을 뛰어넘는 성과를 내며 주목받기 시작했다.\n(라) 특히 관련 특허 출원 건수가 기하급수적으로 늘어난 것이 이를 방증한다.",
                        q("순서대로 알맞게 배열한 것을 고르십시오.", List.of(
                                opt("(나)-(다)-(라)-(가)", "정답: 초기 회의적 평가 → 반전(성과로 주목) → 구체적 방증(특허 출원) → 최종 결과(구조적 변화) 순입니다."),
                                opt("(나)-(라)-(다)-(가)", "구체적 방증(라)이 반전(다)보다 먼저 나오면 순서가 어색합니다."),
                                opt("(다)-(나)-(라)-(가)", "초기 평가(나)보다 반전(다)이 먼저 나와 순서가 어색합니다."),
                                opt("(나)-(다)-(가)-(라)", "최종 결과(가)가 구체적 방증(라)보다 먼저 나오면 순서가 어색합니다.")
                        ), 0, "초기 평가와 반전, 방증과 결과의 순서를 뒤섞어 제시하여 혼동 유도.", "'그러나'로 시작하는 반전과 '이를 방증한다'는 표현의 순서를 따라가세요."))
        );

        List<PassageSeed> lv5_1st_r11to20 = List.of(
                onePassage(PassageCategory.READING, "순서 배열하기",
                        "(가) 이에 따라 관련 법규 개정 논의가 본격화되고 있다.\n(나) 최근 자율주행 기술의 상용화가 눈앞에 다가오면서 새로운 법적 쟁점들이 부상하고 있다.\n(다) 특히 사고 발생 시 책임 소재를 어떻게 규정할 것인가가 핵심 쟁점으로 떠올랐다.\n(라) 전문가들 사이에서도 아직 명확한 합의점을 찾지 못하고 있는 상황이다.",
                        q("순서대로 알맞게 배열한 것을 고르십시오.", List.of(
                                opt("(나)-(다)-(가)-(라)", "정답: 기술 상용화로 쟁점 부상 → 핵심 쟁점(책임 소재) → 그에 따른 조치(법규 개정 논의) → 현재 상황(합의 부재) 순입니다."),
                                opt("(나)-(가)-(다)-(라)", "조치(가)가 핵심 쟁점 설명(다)보다 먼저 나오면 순서가 어색합니다."),
                                opt("(다)-(나)-(가)-(라)", "기술 상용화 배경(나)보다 핵심 쟁점(다)이 먼저 나와 순서가 어색합니다."),
                                opt("(나)-(다)-(라)-(가)", "법규 개정 논의(가)가 합의 부재 상황(라)보다 뒤에 나오는 것이 자연스럽습니다.")
                        ), 0, "배경, 쟁점, 조치, 현재 상황의 순서를 뒤섞어 제시하여 혼동 유도.", "'이에 따라'가 가리키는 앞 내용을 먼저 찾으세요.")),
                onePassage(PassageCategory.READING, "순서 배열하기",
                        "(가) 그럼에도 불구하고 이 연구는 학계에 큰 반향을 일으켰다.\n(나) 이 연구는 방법론적 한계로 인해 초기에는 많은 비판에 직면했다.\n(다) 표본의 크기가 지나치게 작다는 지적이 대표적이었다.\n(라) 이는 연구가 제시한 결론의 신선함과 통찰력 덕분이었다.",
                        q("순서대로 알맞게 배열한 것을 고르십시오.", List.of(
                                opt("(나)-(다)-(가)-(라)", "정답: 초기 비판 → 구체적 비판 내용(표본 크기) → 그럼에도 반향 → 그 이유(신선함과 통찰력) 순입니다."),
                                opt("(나)-(가)-(다)-(라)", "구체적 비판(다)이 '그럼에도'(가)보다 먼저 나오는 것이 자연스럽습니다."),
                                opt("(다)-(나)-(가)-(라)", "초기 비판 전체 진술(나)보다 구체적 비판(다)이 먼저 나와 순서가 어색합니다."),
                                opt("(나)-(다)-(라)-(가)", "반향(가)이 그 이유(라)보다 먼저 나오는 것이 자연스럽습니다.")
                        ), 0, "전체 진술과 구체적 비판, 결과와 이유의 순서를 뒤섞어 제시하여 혼동 유도.", "'그럼에도 불구하고', '이는 ~ 덕분'이라는 표현의 순서를 따라가세요.")),
                multiQ(PassageCategory.READING, "빈칸/일치",
                        "우리는 흔히 전문가의 판단이 일반인의 직관보다 항상 우월하다고 믿는 경향이 있다. 그러나 복잡성이 높은 문제일수록 전문가조차 ( ) 오류를 범할 가능성이 있으며, 다양한 배경을 가진 사람들의 집단적 판단이 오히려 더 정확한 결과를 낳는 경우가 적지 않다는 연구 결과가 이어지고 있다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("예측하지 못한", "정답: 전문가조차 예상 못 한 오류를 범할 수 있다는 문맥에서 자연스럽습니다."),
                                opt("의도적으로 회피한", "고의로 피한다는 뜻이 되어 '오류를 범한다'는 문맥과 어색하게 이어집니다."),
                                opt("완벽하게 차단한", "오류를 막는다는 뜻이 되어 '오류를 범할 가능성'이라는 뒤 표현과 모순됩니다."),
                                opt("사전에 통보한", "오류를 미리 알린다는 뜻이 되어 이 문맥과 맞지 않습니다.")
                        ), 0, "예상치 못한 오류와 의도적 회피·차단을 혼동하게 유도.", "전문가도 오류를 범할 수 있다는 문맥에 자연스럽게 이어지는 표현을 찾으세요."),
                        q("이 글의 내용과 같은 것을 고르십시오.", List.of(
                                opt("집단적 판단이 전문가의 판단보다 더 정확한 경우가 있다.", "정답: '집단적 판단이 오히려 더 정확한 결과를 낳는 경우가 적지 않다'고 언급되었습니다."),
                                opt("전문가의 판단은 항상 일반인보다 우월하다.", "이는 통념으로 소개되었을 뿐 글쓴이가 반박하는 내용입니다."),
                                opt("복잡한 문제일수록 전문가의 판단이 더 정확하다.", "전문가조차 오류를 범할 가능성이 있다고 언급되어 단순화된 해석입니다."),
                                opt("집단적 판단에 대한 연구는 이루어진 적이 없다.", "관련 연구 결과가 이어지고 있다고 언급되었습니다.")
                        ), 0, "통념(전문가 우월)과 글쓴이의 반박(집단적 판단의 정확성)을 뒤바꿔 제시하여 혼동 유도.", "'우리는 흔히 ~ 믿는다'로 소개된 통념과 '그러나'로 이어지는 반박을 구분하세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "5~6급 과정 학술 세미나 안내\n대상: 5급 이상 학습자\n주제: 비평적 사고와 논증 글쓰기\n일시: 매월 둘째 주 토요일 오후 2시\n※ 사전 신청자에 한해 사후 자료집이 제공됩니다.",
                        q("안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("사전 신청자만 세미나 자료집을 받을 수 있다.", "정답: '사전 신청자에 한해 자료집이 제공된다'고 명시되어 있습니다."),
                                opt("세미나는 모든 급수 학습자를 대상으로 한다.", "5급 이상이 대상이라고 명시되어 있습니다."),
                                opt("세미나는 매주 진행된다.", "매월 둘째 주 토요일이라고 명시되어 있습니다."),
                                opt("자료집은 신청 여부와 관계없이 제공된다.", "사전 신청자에 한해 제공된다고 명시되어 있습니다.")
                        ), 0, "대상 범위, 세미나 주기, 자료집 제공 조건을 바꿔치기하여 혼동 유도.", "안내문의 대상, 일시, 자료집 제공 조건을 각각 확인하세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "5~6급 심화 과정 사전 진단 평가 안내\n대상: WEEK1 수강을 시작하는 전 학습자\n방식: 온라인 자가 진단(약 40분 소요)\n※ 진단 결과는 개인별 학습 경로 설계에만 활용되며, 별도의 합격·불합격 판정은 없습니다.",
                        q("안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("이 진단 평가에는 합격·불합격 판정이 없다.", "정답: '별도의 합격·불합격 판정은 없다'고 명시되어 있습니다."),
                                opt("진단 평가는 오프라인으로만 진행된다.", "온라인 자가 진단이라고 명시되어 있습니다."),
                                opt("진단 평가는 일부 학습자만 응시한다.", "WEEK1을 시작하는 전 학습자가 대상이라고 명시되어 있습니다."),
                                opt("진단 결과는 별도로 활용되지 않는다.", "학습 경로 설계에 활용된다고 명시되어 있습니다.")
                        ), 0, "판정 여부, 진행 방식, 결과 활용 여부를 바꿔치기하여 혼동 유도.", "안내문의 대상, 방식, 결과 활용 목적을 각각 확인하세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "혁신은 종종 기존 질서에 대한 파괴를 동반한다고 여겨지지만, 진정한 혁신은 파괴 그 자체가 목적이 아니라 더 나은 대안을 향한 창조적 재구성 과정이어야 한다. 파괴만 있고 대안이 없다면 그것은 혼란에 지나지 않는다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("진정한 혁신은 파괴가 아니라 더 나은 대안을 향한 창조적 재구성이어야 한다.", "정답: 글 중반부에서 중심 내용이 직접 드러납니다."),
                                opt("혁신은 무조건 기존 질서를 파괴해야 한다.", "글쓴이의 주장과 반대됩니다."),
                                opt("혼란과 혁신은 동일한 개념이다.", "대안 없는 파괴는 혼란에 불과하다고 언급되어 구분됩니다."),
                                opt("혁신에는 대안이 필요 없다.", "글쓴이의 주장과 반대됩니다.")
                        ), 0, "파괴와 혁신을 동일시하거나 대안의 필요성을 놓치게 유도.", "'파괴 그 자체가 목적이 아니라'는 표현 뒤의 핵심 주장에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "그의 논문은 방대한 실증 자료를 바탕으로 기존 이론의 맹점을 ( ) 점에서 학계의 주목을 받았다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("예리하게 짚어 냈다는", "정답: 실증 자료를 바탕으로 맹점을 정확히 지적했다는 문맥에서 자연스럽습니다."),
                                opt("전혀 다루지 않았다는", "맹점을 다루지 않았다는 뜻이 되어 학계의 주목을 받았다는 결과와 어색하게 이어집니다."),
                                opt("의도적으로 은폐했다는", "숨겼다는 뜻이 되어 주목받았다는 긍정적 평가와 맞지 않습니다."),
                                opt("무의미하게 반복했다는", "무의미한 반복이라는 뜻이 되어 주목받았다는 결과와 모순됩니다.")
                        ), 0, "예리한 지적과 은폐·무의미한 반복을 혼동하게 유도.", "학계의 주목을 받은 이유가 무엇인지 생각해 보세요.")),
                onePassage(PassageCategory.READING, "필자의 태도 파악",
                        "많은 이들이 세계화가 문화적 획일화를 초래한다고 우려하지만, 나는 오히려 세계화가 지역 문화의 고유성을 재발견하게 만드는 계기로 작용할 수 있다고 본다.",
                        q("필자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("세계화가 지역 문화의 고유성을 재발견하는 계기가 될 수 있다고 긍정적으로 본다.", "정답: '나는 오히려 ~ 계기로 작용할 수 있다'는 표현에서 필자의 견해가 드러납니다."),
                                opt("세계화가 문화적 획일화를 초래한다고 강하게 우려한다.", "이는 통념으로 소개된 것이며 필자가 반박하는 내용입니다."),
                                opt("지역 문화는 세계화와 무관하게 소멸할 것이라고 본다.", "언급되지 않은 내용입니다."),
                                opt("세계화에 대해 무관심한 태도를 보이고 있다.", "무관심이 아니라 명확한 견해를 밝히고 있습니다.")
                        ), 0, "통념(문화적 획일화 우려)을 필자 본인의 입장으로 착각하게 유도.", "'나는 오히려'로 시작하는 문장에서 필자 자신의 견해를 확인하세요."))
        );

        return new WeekSeed("5급 기초 다지기 (기출문제 스타일)",
                "TOPIK II 5급 수준의 학술·시사·전문 담화를 폭넓게 다루며 고급 어휘와 논리적 추론 능력을 기른다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 학술·시사, 사회 비평, 전문 분야 담화·지문 중심(듣기 20문항 + 읽기 20문항). 학습 후 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(lv5_1st_l1to10, lv5_1st_l11to20, lv5_1st_r1to10, lv5_1st_r11to20))
                ));
    }

    // ===================== 저장 =====================

    private void saveCurriculumWithDays(Curriculum curriculum, List<WeekSeed> weekSeeds) {
        List<CurriculumWeek> weeks = new ArrayList<>();
        List<CurriculumDay> allDays = new ArrayList<>();
        int dayNumber = 0; // 주차별 일수가 달라도(예: WEEK1만 10일) 정확히 이어지도록 누적 계산한다.

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
