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
 * 청각적 교감형(AUDITORY_EMPATHETIC) 유형의 5~6급 "TOPIK 쉐도잉 리듬 노트" 커리큘럼을 심는다.
 * AuditoryEmpatheticLevel34CurriculumDataLoader(3~4급)와 동일한 골격(레코드/헬퍼, 8주 + 모의고사 2회 + Final 1회,
 * 총 2,450문항)을 쓰되, trapNote/strategyTip을 청각 신호(억양·어조·발음·반복) 언어로 계속 유지한다 —
 * LearnerType.AUDITORY_EMPATHETIC의 studyTip("쉐도잉과 받아쓰기, 1:1 강의를 통한 즉각적인 청각 피드백")을
 * 모든 문항에 반영한다. 1~2급/3~4급 과정과는 완전히 분리된 별도의 8주 과정으로, 같은 learner_type이라도
 * targetLevelFrom(LEVEL_5)으로 구분되는 별도 Curriculum 레코드를 갖는다.
 * WEEK1~4는 5급(기출문제 스타일, 학술·시사·정책·전문 담화 중심), WEEK5~8은 6급(시중교재 grammarUnit 스타일).
 */
@Component
@RequiredArgsConstructor
@Order(18)
public class AuditoryEmpatheticLevel56CurriculumDataLoader implements CommandLineRunner {

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
        curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.AUDITORY_EMPATHETIC, TopikLevel.LEVEL_5)
                .ifPresent(this::deleteExisting);

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.AUDITORY_EMPATHETIC);
        curriculum.setTitle("TOPIK 쉐도잉 리듬 노트");
        curriculum.setTargetLevelLabel("5~6급 전 과정");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_5);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_6);
        curriculum.setUsageNote(
                "모든 문제에 청각 신호 태그(🎧 억양·강세 / 💬 어조·의도 / 👂 발음·어휘 / 🔁 반복·상투구)로 함정 포인트를 "
                        + "구분합니다. 지문을 눈으로만 읽지 말고 소리 내어 따라 말하는 쉐도잉과 받아쓰기를 병행하세요. "
                        + "5~6급부터는 학술·시사·전문 담화의 복잡한 문장 구조가 등장하므로 끊어 읽기와 억양 변화를 "
                        + "더욱 세밀하게 소리로 익히세요.");

        List<WeekSeed> weeks = List.of(week1());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("🎧 TOPIK 커리큘럼(청각적 교감형, 5~6급) WEEK1 1차 시딩 완료!");
    }

    /** 재시딩 전 기존 커리큘럼을 지운다. day는 부모의 cascade 대상이 아니라 먼저 지워야 한다. */
    private void deleteExisting(Curriculum existing) {
        List<CurriculumDay> days = curriculumDayRepository.findByCurriculumId(existing.getId());
        curriculumDayRepository.deleteAll(days);
        userCurriculumProgressRepository.deleteByCurriculumId(existing.getId());
        curriculumRepository.delete(existing);
        curriculumRepository.flush();
    }

    // ===================== WEEK 1: 5~6급 쉐도잉 리듬 기초 다지기 =====================

    private static final String WEEK1_ANSWER_NOTE_TEMPLATE = """
            [🎧 오답 노트 템플릿 - WEEK1용]
            문제를 틀렸을 때 청각 신호 태그로 표시하며 나의 취약 유형을 확인해보세요.

            문제 번호(1~40) | 틀린 이유(해당 태그 동그라미) | 취약 유형 코드
            예) 3번 | 🎧 (강세 신호 오독) |

            [🎧 청각 신호별 취약 유형 코드 가이드]
            🎧 억양·강세 신호 오독: 대화나 글에 나온 시간, 장소, 숫자 등 핵심 정보를 강조하는 억양을 놓쳐 세부 내용을 잘못 판단함.
            💬 어조·의도 파악 실패: 화자나 글쓴이의 진짜 목적이나 주제, 태도를 놓침.
            👂 발음·어휘 혼동: 5~6급 수준의 전문·추상 어휘를 몰라 내용 이해에 어려움을 겪음.
            🔁 반복·상투구 놓침: 대화나 글에서 반복되는 핵심 표현이나 상투적 패턴을 놓침.

            같은 태그가 반복해서 표시된다면, 관련 지문을 소리 내어 세 번씩 따라 읽으며 다음 학습 때 우선 보완하세요.
            """;

    private WeekSeed week1() {
        List<PassageSeed> lv56w1_1st_l1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "남자: 이번 정책 공청회에서 발언 순서가 바뀌었다고 들었는데요.\n여자: 네, 원래 예정된 발제자의 일정이 갑자기 바뀌어서 부득이하게 조정했습니다.\n남자: 그렇군요, 미리 알려 주셔서 감사합니다.",
                        q("여자가 이렇게 말한 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("발언 순서 변경의 불가피한 사정을 설명하려고", "정답: '부득이하게 조정했다'는 발언에서 변경 사유를 해명하려는 의도가 드러납니다."),
                                opt("발제자의 자격을 문제 삼으려고", "💬 자격 문제는 언급되지 않았습니다."),
                                opt("공청회 자체를 취소하려고", "💬 취소가 아니라 순서 조정에 대한 설명입니다."),
                                opt("남자에게 발언 순서를 양보해 달라고 요청하려고", "💬 언급되지 않은 내용입니다.")
                        ), 0, "💬 단순 정보 전달을 다른 의도로 확대 해석하기 쉽습니다.", "[의도파악 소리단서] 부득이하게 조정(핵심 표현) → 사정 설명(의도). 소리 내어 따라 말하며 해명의 어조를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "의도·태도 파악",
                        "여자: 이번 논문 심사에서 제 연구 방법론에 대한 지적이 많았어요.\n남자: 그만큼 꼼꼼히 봐 주셨다는 뜻이니 오히려 발전의 기회로 삼으시면 좋을 것 같아요.\n여자: 그렇게 생각하니 마음이 한결 가벼워지네요.",
                        q("남자가 이렇게 말한 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부정적 지적을 긍정적으로 받아들이도록 격려하려고", "정답: '발전의 기회로 삼으라'는 발언에서 격려의 의도가 드러납니다."),
                                opt("심사위원의 지적이 부당했다고 비판하려고", "💬 심사위원을 비판하는 내용이 아닙니다."),
                                opt("여자에게 논문을 다시 쓰라고 권유하려고", "💬 언급되지 않은 내용입니다."),
                                opt("자신의 연구 경험을 자랑하려고", "💬 언급되지 않은 내용입니다.")
                        ), 0, "💬 격려의 의도를 비판이나 자랑으로 오해하기 쉽습니다.", "[의도파악 소리단서] 발전의 기회(핵심 표현) → 격려(의도). 소리 내어 따라 말하며 다독이는 어조를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 이번 국제 학술대회 참가 신청 마감이 언제죠?\n여자: 원래 이달 말이었는데, 참가 문의가 많아서 다음 달 둘째 주까지로 연장됐어요.",
                        q("참가 신청 마감일로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("다음 달 둘째 주", "정답: 신청이 많아 다음 달 둘째 주까지로 연장되었습니다."),
                                opt("이달 말", "🎧 연장 전 원래 마감일만 듣고 착각하기 쉽습니다."),
                                opt("이번 달 둘째 주", "🎧 시점을 혼동한 오답입니다."),
                                opt("다음 달 말", "🎧 대화에 없는 날짜입니다.")
                        ), 0, "🎧 원래 마감일과 연장된 마감일, 두 시점을 혼동하기 쉽습니다.", "[세부정보 소리단서] 이달 말(원래) → 연장(변경) → 다음 달 둘째 주(최종). 소리 내어 시점을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화제 고르기",
                        "최근 여러 대학이 학제 간 융합 연구를 장려하는 제도를 도입하고 있습니다. 서로 다른 전공의 연구자들이 협업할 경우 별도의 연구비를 지원하며, 이는 창의적인 연구 성과로 이어지고 있습니다.",
                        q("무엇에 대한 내용인지 가장 알맞은 것을 고르십시오.", List.of(
                                opt("학제 간 융합 연구 지원 제도", "정답: 융합 연구를 장려하는 제도의 도입과 효과를 설명하고 있습니다."),
                                opt("대학 등록금 인상 정책", "💬 언급되지 않은 내용입니다."),
                                opt("대학 입시 제도 개편", "💬 언급되지 않은 내용입니다."),
                                opt("교수 채용 절차 변화", "💬 언급되지 않은 내용입니다.")
                        ), 0, "💬 '연구비 지원'이라는 세부 언급만 듣고 다른 재정 정책으로 오해하기 쉽습니다.", "[화제파악 소리단서] 융합 연구 장려(제도) → 창의적 성과(효과). 소리 내어 읽으며 전체 화제를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동 고르기",
                        "여자: 이번 심포지엄 발표 자료에 통계 수치가 좀 부정확한 것 같아요.\n남자: 그럼 제가 원자료를 다시 확인하고 수정할게요.\n여자: 좋아요, 그럼 그렇게 준비해 주세요.",
                        q("남자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("원자료를 다시 확인하고 수치를 수정한다.", "정답: 여자가 남자의 제안을 승인했으므로 그대로 수행할 것입니다."),
                                opt("발표 자료를 폐기한다.", "🔁 확인·수정하자는 것이지 폐기가 아닙니다."),
                                opt("심포지엄을 취소한다.", "🔁 언급되지 않은 행동입니다."),
                                opt("새 발표 자료를 처음부터 만든다.", "🔁 기존 자료를 수정하는 것이지 새로 만드는 것이 아닙니다.")
                        ), 0, "🔁 '확인하고 수정하자'는 제안과 '다시 만들자'를 혼동하기 쉽습니다.", "[흐름추론 소리단서] 통계 부정확(문제) → 원자료 확인+수정(해결). 소리 내어 따라 말하며 수정 방법을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: 이번 국제 세미나는 이틀간 진행되는데, 첫날은 기조연설과 전체 세션으로, 둘째 날은 분과별 발표로 진행됩니다.\n여자: 그럼 통역은 첫날만 제공되나요?\n남자: 아니요, 이틀 모두 제공됩니다.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("통역은 세미나 이틀간 모두 제공된다.", "정답: 통역이 이틀 모두 제공된다고 했습니다."),
                                opt("세미나는 하루만 진행된다.", "👂 세미나는 이틀간 진행됩니다."),
                                opt("통역은 첫날만 제공된다.", "👂 이틀 모두 제공된다고 했습니다."),
                                opt("둘째 날은 기조연설이 진행된다.", "👂 기조연설은 첫날, 둘째 날은 분과별 발표입니다.")
                        ), 0, "🎧 '첫날'과 '둘째 날'에 해당하는 각각의 프로그램과 통역 제공 여부를 헷갈리기 쉽습니다.", "[일치판단 소리단서] 첫날(기조연설·전체 세션) vs 둘째 날(분과별 발표), 통역은 이틀 모두. 소리 내어 항목별로 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 요즘 연구자들이 논문 편수만 늘리려는 경향이 있는 것 같아요. 저는 오히려 질적으로 깊이 있는 연구 하나가 더 가치 있다고 생각하거든요.",
                        q("남자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("연구의 질적 깊이가 양보다 중요하다.", "정답: 질적으로 깊이 있는 연구가 더 가치 있다는 것이 남자의 핵심 주장입니다."),
                                opt("논문은 많이 쓸수록 좋다.", "💬 남자의 생각과 반대됩니다."),
                                opt("연구자들은 논문을 적게 쓴다.", "💬 오히려 편수를 늘리려는 경향이 있다고 했으므로 반대입니다."),
                                opt("연구의 질은 중요하지 않다.", "💬 남자의 생각과 정반대입니다.")
                        ), 0, "💬 '편수를 늘린다'는 현상에 대한 언급과 남자 자신의 생각을 혼동하기 쉽습니다.", "[중심생각 소리단서] 편수 중시 경향(현상) → 저는 반대로 생각함(대조 어조). 소리 내어 따라 말하며 대조를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "여자: 이번 학술지 투고 논문이 몇 편이나 접수됐어요?\n남자: 목표가 150편이었는데, 마감일까지 접수해 보니 187편이 들어왔어요.",
                        q("이번 학술지에 접수된 논문의 목표 초과분으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("37편", "정답: 목표 150편에서 187편이 접수되었으므로 37편이 초과되었습니다."),
                                opt("150편", "🎧 목표 편수만 듣고 착각하기 쉽습니다."),
                                opt("187편", "🎧 최종 접수 편수를 초과분으로 착각한 오답입니다."),
                                opt("50편", "🎧 대화에 없는 임의의 수치입니다.")
                        ), 0, "🎧 목표와 최종 접수 수치를 빼서 계산해야 하는데 하나만 듣고 판단하기 쉽습니다.", "[세부정보 소리단서] 목표 150편(기준) → 187편(최종 접수) → 37편(초과분). 소리 내어 계산하며 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "목적/주제 파악",
                        "여러분, 다음 주부터 도서관 전자자료실 이용 시간이 자정까지로 연장됩니다. 논문 마감 기간 학생들의 편의를 위한 조치이니 많은 이용 바랍니다.",
                        q("이 안내의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("전자자료실 이용 시간 연장을 알리려고", "정답: 이용 시간 연장 사실과 그 취지를 안내하는 글입니다."),
                                opt("도서관 폐쇄를 알리려고", "💬 이용 시간이 연장되는 것이지 폐쇄가 아닙니다."),
                                opt("전자자료실 이용료 인상을 알리려고", "💬 언급되지 않은 내용입니다."),
                                opt("논문 제출 마감을 연기하려고", "💬 언급되지 않은 내용입니다.")
                        ), 0, "💬 '마감 기간'이라는 세부 언급만 듣고 논문 제출 마감으로 오해하기 쉽습니다.", "[목적파악 소리단서] 이용 시간 연장(정보) → 학생 편의(취지). 소리 내어 읽으며 목적을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "흐름 추론",
                        "남자: 이번 실험 데이터에서 예상치 못한 이상값이 나왔어요.\n여자: 그러네요. 측정 오차인지 확인하고 재실험을 해 보는 게 좋겠어요.",
                        q("두 사람이 다음으로 할 일로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("측정 오차를 확인하고 재실험을 진행한다.", "정답: 여자의 제안이 곧 다음에 할 일입니다."),
                                opt("실험을 완전히 중단한다.", "🔁 언급되지 않은 성급한 행동입니다."),
                                opt("이상값을 그대로 발표한다.", "🔁 재확인 제안과 반대되는 행동입니다."),
                                opt("연구팀을 해체한다.", "🔁 대화 맥락과 무관한 행동입니다.")
                        ), 0, "🔁 '이 좋겠어요'라는 제안형 표현을 놓치고 이미 결정된 일로 착각하기 쉽습니다.", "[흐름추론 소리단서] 이상값 발견(문제) → 오차 확인+재실험(제안). 소리 내어 따라 말하며 제안 내용을 확인하세요."))
        );

        List<PassageSeed> lv56w1_1st_l11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "화제 고르기",
                        "최근 여러 기업이 탄소중립 목표 달성을 위해 재생에너지 전환을 서두르고 있습니다. 초기 투자 비용 부담에도 불구하고 장기적으로는 에너지 비용 절감과 기업 이미지 제고라는 이중 효과를 기대하고 있습니다.",
                        q("무엇에 대한 내용인지 가장 알맞은 것을 고르십시오.", List.of(
                                opt("기업의 재생에너지 전환과 기대 효과", "정답: 재생에너지 전환 배경과 그 효과를 설명하고 있습니다."),
                                opt("탄소세 부과 방식 변경", "💬 언급되지 않은 내용입니다."),
                                opt("기업 구조조정 계획", "💬 언급되지 않은 내용입니다."),
                                opt("에너지 가격 규제 정책", "💬 언급되지 않은 내용입니다.")
                        ), 0, "💬 '초기 투자 비용 부담'이라는 세부 언급만 듣고 부정적 내용으로만 오해하기 쉽습니다.", "[화제파악 소리단서] 재생에너지 전환(변화) → 비용 절감+이미지 제고(이중 효과). 소리 내어 읽으며 전체 화제를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동 고르기",
                        "여자: 이번 정책 보고서 초안에 통계 인용 출처가 누락된 부분이 있어요.\n남자: 그럼 제가 원 자료를 찾아서 출처를 보완할게요.\n여자: 좋아요, 그럼 그렇게 준비해 주세요.",
                        q("남자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("원 자료를 찾아 출처를 보완한다.", "정답: 여자가 남자의 제안을 승인했으므로 그대로 수행할 것입니다."),
                                opt("보고서를 폐기한다.", "🔁 보완하자는 것이지 폐기가 아닙니다."),
                                opt("보고서 제출을 포기한다.", "🔁 언급되지 않은 행동입니다."),
                                opt("새 보고서를 처음부터 다시 쓴다.", "🔁 기존 보고서를 보완하는 것이지 새로 쓰는 것이 아닙니다.")
                        ), 0, "🔁 '보완하자'는 제안과 '다시 쓰자'를 혼동하기 쉽습니다.", "[흐름추론 소리단서] 출처 누락(문제) → 원 자료 확인+보완(해결). 소리 내어 따라 말하며 보완 방법을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: 이번 공공기관 채용은 필기시험과 면접 두 단계로 이루어지는데, 필기시험 합격자만 면접에 응시할 수 있어요.\n여자: 그럼 필기시험 합격 기준은 어떻게 되나요?\n남자: 과목별 40점 이상, 전체 평균 60점 이상이에요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("필기시험에 합격해야 면접에 응시할 수 있다.", "정답: 필기시험 합격자만 면접에 응시할 수 있다고 했습니다."),
                                opt("면접만 통과하면 채용된다.", "👂 필기시험 합격이 선행 조건입니다."),
                                opt("과목별 기준 점수는 없다.", "👂 과목별 40점 이상이라는 기준이 있습니다."),
                                opt("전체 평균 기준은 40점이다.", "👂 전체 평균 기준은 60점입니다.")
                        ), 0, "🎧 '과목별 40점'과 '전체 평균 60점'이라는 두 기준을 혼동하기 쉽습니다.", "[일치판단 소리단서] 과목별 40점(개별 기준) + 전체 평균 60점(종합 기준). 소리 내어 두 기준을 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "여자: 요즘 정책 토론을 보면 다들 자기주장만 하고 상대 의견은 안 듣는 것 같아요. 저는 경청이 설득보다 먼저라고 생각하거든요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("경청이 설득보다 우선되어야 한다.", "정답: 경청이 설득보다 먼저라는 것이 여자의 핵심 주장입니다."),
                                opt("자기주장이 가장 중요하다.", "💬 여자의 생각과 반대됩니다."),
                                opt("정책 토론은 불필요하다.", "💬 언급되지 않은 내용입니다."),
                                opt("상대 의견은 들을 필요가 없다.", "💬 여자의 생각과 정반대입니다.")
                        ), 0, "💬 '자기주장만 한다'는 현상에 대한 언급과 여자 자신의 생각을 혼동하기 쉽습니다.", "[중심생각 소리단서] 자기주장 우선 경향(현상) → 저는 경청이 먼저라 생각함(대조 어조). 소리 내어 따라 말하며 대조를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 이번 국제 컨퍼런스 등록비가 얼마죠?\n여자: 원래 30만 원인데, 학생 할인을 받으면 40% 할인돼요.",
                        q("학생 할인을 받을 때 등록비로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("18만 원", "정답: 30만 원에서 40% 할인되면 18만 원입니다."),
                                opt("30만 원", "🎧 할인 전 원래 가격만 듣고 착각하기 쉽습니다."),
                                opt("12만 원", "🎧 할인된 금액만 듣고 등록비로 착각한 오답입니다."),
                                opt("20만 원", "🎧 대화에 없는 임의의 금액입니다.")
                        ), 0, "🎧 원래 가격과 할인율을 곱해서 계산해야 하는데 원래 가격만 듣고 판단하기 쉽습니다.", "[세부정보 소리단서] 30만 원(원래) - 40% 할인 → 18만 원(최종). 소리 내어 계산하며 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "목적 파악",
                        "안내 말씀드립니다. 다음 주 화요일 오전에 실시될 정기 소방 점검으로 인해 일부 강의동 전기 공급이 일시 중단됩니다. 해당 시간에는 전자기기 사용에 유의해 주시기 바랍니다.",
                        q("이 안내의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("소방 점검으로 인한 전기 중단을 예고하려고", "정답: 점검 일정과 그로 인한 전기 중단을 안내하는 글입니다."),
                                opt("화재 발생을 알리려고", "💬 '점검'이라고 명시했으므로 실제 화재가 아닙니다."),
                                opt("강의동 리모델링 공사를 알리려고", "💬 언급되지 않은 내용입니다."),
                                opt("정전 사고를 알리려고", "💬 계획된 점검이지 사고가 아닙니다.")
                        ), 0, "💬 '전기 중단'이라는 단어만 듣고 사고로 오해하면 '점검'이라는 핵심을 놓치기 쉽습니다.", "[목적파악 소리단서] 소방 점검 예고(정보) → 전기 공급 중단(영향). 소리 내어 읽으며 '점검'이라는 단서를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동 고르기",
                        "여자: 이번 학회 발표 순서가 어떻게 배정됐는지 확인해 봤어요?\n남자: 아직요, 지금 바로 학회 홈페이지에서 확인해 볼게요.",
                        q("남자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("학회 홈페이지에서 발표 순서를 확인한다.", "정답: 남자가 직접 '확인해 볼게요'라고 말했으므로 이어질 행동입니다."),
                                opt("발표를 취소한다.", "🔁 언급되지 않은 행동입니다."),
                                opt("학회 참가를 포기한다.", "🔁 대화 맥락과 무관한 행동입니다."),
                                opt("발표 자료를 처음부터 다시 만든다.", "🔁 순서 확인과 무관한 행동입니다.")
                        ), 0, "🔁 '확인해 볼게요'라는 짧은 의지 표현을 놓치고 다른 행동을 떠올리기 쉽습니다.", "[흐름추론 소리단서] 순서 미확인(현황) → 확인해 볼게요(행동 예고). 소리 내어 따라 말하며 의지 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "남자: 이번 학술 지원 사업은 두 개 트랙으로 운영되는데, 신진연구자 트랙과 중견연구자 트랙이 있고, 지원 금액은 신진연구자 트랙이 더 커요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("신진연구자 트랙의 지원 금액이 더 많다.", "정답: 신진연구자 트랙의 지원 금액이 더 크다고 했습니다."),
                                opt("두 트랙의 지원 금액은 동일하다.", "👂 신진연구자 트랙이 더 큽니다."),
                                opt("이 사업은 한 개 트랙으로만 운영된다.", "👂 두 개 트랙으로 운영됩니다."),
                                opt("중견연구자 트랙의 지원 금액이 더 많다.", "👂 신진연구자 트랙이 더 큽니다.")
                        ), 0, "🎧 '신진연구자'와 '중견연구자' 트랙 중 어느 쪽 지원 금액이 더 큰지 혼동하기 쉽습니다.", "[일치판단 소리단서] 신진연구자 트랙(더 큰 금액) vs 중견연구자 트랙. 소리 내어 트랙별로 조건을 짝지어 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "여자: 이 연구 보고서 분량이 얼마나 되나요?\n남자: 본문만 120쪽인데, 부록까지 합치면 총 180쪽이에요.",
                        q("보고서 부록의 분량으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("60쪽", "정답: 총 180쪽에서 본문 120쪽을 빼면 부록은 60쪽입니다."),
                                opt("120쪽", "🎧 본문 분량만 듣고 착각하기 쉽습니다."),
                                opt("180쪽", "🎧 전체 분량을 부록 분량으로 착각한 오답입니다."),
                                opt("300쪽", "🎧 대화에 없는 임의의 수치입니다.")
                        ), 0, "🎧 전체 분량과 본문 분량을 빼서 계산해야 하는데 하나만 듣고 판단하기 쉽습니다.", "[세부정보 소리단서] 총 180쪽(전체) - 본문 120쪽(일부) → 60쪽(부록). 소리 내어 계산하며 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 의도 고르기",
                        "남자: 이번 학과 세미나 발표 시간이 너무 짧은 것 같지 않아요? 발표 15분에 질의응답 20분을 배정하면 어떨까요?",
                        q("남자가 여자에게 말하는 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("세미나 시간 배분 개선을 제안하려고", "정답: 발표·질의응답 시간을 구체적으로 재배분하자는 제안을 하고 있습니다."),
                                opt("세미나를 아예 없애자고 주장하려고", "💬 세미나 자체를 없애자는 것이 아니라 시간 배분 개선을 제안하는 것입니다."),
                                opt("현재 시간 배분에 만족한다고 말하려고", "💬 '너무 짧은 것 같다'는 불만을 표현하고 있으므로 반대입니다."),
                                opt("다른 사람을 비난하려고", "💬 비난이 아니라 개선 제안입니다.")
                        ), 0, "💬 '어떨까요'라는 제안형 어조를 놓치면 단순 불만으로만 오해하기 쉽습니다.", "[의도파악 소리단서] 발표 시간 부족(문제 제기) → 구체적 시간 재배분(제안). 소리 내어 따라 말하며 제안의 어조를 확인하세요."))
        );

        List<PassageSeed> lv56w1_1st_r21to30 = List.of(
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 연구는 기존 이론의 한계를 (        ) 새로운 분석 틀을 제시했다는 점에서 학계의 주목을 받고 있다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("극복하며", "정답: 기존 이론의 한계를 뛰어넘는다는 문맥에 맞습니다."),
                                opt("답습하며", "👂 새로운 분석 틀 제시라는 문맥과 반대됩니다."),
                                opt("무시하며", "👂 한계를 다룬다는 문맥과 어울리지 않습니다."),
                                opt("반복하며", "👂 새로운 분석 틀 제시라는 문맥과 반대됩니다.")
                        ), 0, "👂 '극복하다'와 '답습하다/반복하다'처럼 상반된 태도를 나타내는 어휘를 혼동하기 쉽습니다.", "[빈칸추론 소리단서] 기존 이론 한계(문제) → 극복(해결 태도) → 새 분석 틀(결과). 소리 내어 읽으며 태도를 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 정책은 여러 이해관계자의 입장 차이로 (        ) 합의에 이르지 못하고 있다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("좀처럼", "정답: 쉽게 이루어지지 않는다는 부정적 문맥에 어울리는 부사입니다."),
                                opt("이미", "👂 아직 합의에 이르지 못했다는 문맥과 어울리지 않습니다."),
                                opt("마침내", "👂 결국 이루어졌다는 문맥에 어울리는 표현으로 반대됩니다."),
                                opt("어느새", "👂 시간의 경과를 나타내는 표현으로 문맥과 어울리지 않습니다.")
                        ), 0, "👂 '좀처럼'과 '이미/마침내'처럼 상반된 진행 상태를 나타내는 부사를 혼동하기 쉽습니다.", "[빈칸추론 소리단서] 입장 차이(원인) → 좀처럼(어려움 강조) → 합의 못함(결과). 소리 내어 읽으며 부사의 의미를 확인하세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "[학술 논문 투고 규정]\n투고 자격: 관련 분야 석사 이상 소지자\n심사 기간: 접수일로부터 8주 이내\n게재 확정 후 저작권은 학회에 귀속",
                        q("안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("게재가 확정되면 저작권이 학회로 넘어간다.", "정답: 게재 확정 후 저작권이 학회에 귀속된다는 내용과 일치합니다."),
                                opt("학사 학위 소지자도 투고할 수 있다.", "👂 석사 이상 소지자가 자격 조건입니다."),
                                opt("심사에는 기간 제한이 없다.", "👂 접수일로부터 8주 이내라는 기한이 있습니다."),
                                opt("저작권은 항상 저자에게 있다.", "👂 게재 확정 후 학회에 귀속됩니다.")
                        ), 0, "👂 '게재 확정 후'라는 시점 조건을 놓치면 저작권이 항상 저자에게 있다고 오해하기 쉽습니다.", "[안내문일치 소리단서] 석사 이상(자격) / 8주 이내(심사 기간) / 게재 확정 후 저작권 귀속(조건). 소리 내어 항목별로 확인하세요.")),
                onePassage(PassageCategory.READING, "실용문 독해",
                        "[국제 학술대회 발표자 안내]\n발표 자료는 발표일 3일 전까지 사무국에 제출해야 하며, 영문 초록을 함께 첨부해야 합니다. 미제출 시 발표 순서가 뒤로 조정될 수 있습니다.",
                        q("이 글의 내용과 같은 것을 고르십시오.", List.of(
                                opt("발표 자료를 늦게 제출하면 순서가 밀릴 수 있다.", "정답: 미제출 시 발표 순서가 뒤로 조정될 수 있다고 했습니다."),
                                opt("영문 초록은 제출하지 않아도 된다.", "👂 영문 초록을 함께 첨부해야 합니다."),
                                opt("발표 자료 제출 기한은 없다.", "👂 발표일 3일 전까지라는 기한이 있습니다."),
                                opt("늦게 제출해도 순서에 영향이 없다.", "👂 순서가 뒤로 조정될 수 있습니다.")
                        ), 0, "🎧 '3일 전'이라는 제출 기한을 놓치기 쉽습니다.", "[실용문 소리단서] 3일 전 제출(기한) → 영문 초록 첨부(조건) → 미제출 시 순서 조정(제재). 소리 내어 항목별로 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 정책의 실효성에 대해서는 전문가들 사이에서도 의견이 (        ) 있다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("갈리고", "정답: 의견이 서로 다르게 나뉜다는 문맥에 맞는 표현입니다."),
                                opt("일치하고", "👂 의견이 다르다는 문맥과 반대됩니다."),
                                opt("통일되고", "👂 의견이 다르다는 문맥과 반대됩니다."),
                                opt("합쳐지고", "👂 문맥과 어울리지 않는 표현입니다.")
                        ), 0, "👂 '갈리다'와 '일치하다/통일되다'처럼 상반된 의미를 나타내는 어휘를 혼동하기 쉽습니다.", "[빈칸추론 소리단서] 실효성 논쟁(주제) → 의견 갈림(전문가 반응). 소리 내어 읽으며 의미를 확인하세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "[대학원 장학금 지급 안내]\n지급 대상: 직전 학기 평점 3.5 이상\n지급 방식: 등록금의 50% 감면\n동일 학기 타 장학금과 중복 수혜 불가",
                        q("안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("이 장학금은 다른 장학금과 함께 받을 수 없다.", "정답: 동일 학기 타 장학금과 중복 수혜가 불가하다고 했습니다."),
                                opt("평점과 관계없이 누구나 받을 수 있다.", "👂 직전 학기 평점 3.5 이상이 조건입니다."),
                                opt("등록금 전액이 면제된다.", "👂 50% 감면입니다."),
                                opt("다른 장학금과 중복 수혜가 가능하다.", "👂 중복 수혜가 불가합니다.")
                        ), 0, "👂 '중복 수혜 불가'라는 조건을 놓치기 쉽습니다.", "[안내문일치 소리단서] 평점 3.5 이상(대상) / 50% 감면(방식) / 중복 수혜 불가(제한). 소리 내어 조건을 확인하세요.")),
                onePassage(PassageCategory.READING, "실용문 독해",
                        "[연구윤리 서약서 제출 안내]\n모든 연구 참여자는 연구 착수 전 연구윤리 서약서를 제출해야 합니다. 미제출 시 연구비 집행이 제한되며, 서약서는 매년 갱신해야 합니다.",
                        q("이 글의 내용과 같은 것을 고르십시오.", List.of(
                                opt("서약서를 매년 다시 제출해야 한다.", "정답: 서약서는 매년 갱신해야 한다고 했습니다."),
                                opt("서약서는 한 번만 제출하면 된다.", "👂 매년 갱신해야 합니다."),
                                opt("미제출해도 연구비 집행에 문제없다.", "👂 연구비 집행이 제한된다고 했습니다."),
                                opt("서약서 제출은 선택 사항이다.", "👂 모든 연구 참여자가 제출해야 합니다.")
                        ), 0, "👂 '매년 갱신'이라는 조건을 놓치고 한 번만 제출하면 된다고 오해하기 쉽습니다.", "[실용문 소리단서] 착수 전 제출(시점) → 미제출 시 집행 제한(제재) → 매년 갱신(주기). 소리 내어 항목별로 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "그의 연구는 학계의 (        ) 통념을 뒤집는 결과를 제시해 큰 반향을 일으켰다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("오랜", "정답: 오랫동안 굳어진 통념을 나타내는 문맥에 맞는 표현입니다."),
                                opt("새로운", "👂 뒤집을 대상인 통념은 오래된 것이어야 문맥에 맞습니다."),
                                opt("일시적인", "👂 통념은 지속적인 개념이므로 문맥과 어울리지 않습니다."),
                                opt("가벼운", "👂 문맥과 어울리지 않는 표현입니다.")
                        ), 0, "👂 '오랜'과 '새로운'처럼 시간의 지속성이 다른 형용사를 혼동하기 쉽습니다.", "[빈칸추론 소리단서] 학계 통념(대상) → 오랜(지속성) → 뒤집는 결과(반전). 소리 내어 읽으며 통념의 성격을 확인하세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "[학술 데이터베이스 이용 안내]\n이용 대상: 재학생 및 교직원\n동시 접속 인원: 최대 200명\n초과 시 대기열 등록 후 순차 이용",
                        q("안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("동시 접속 인원이 초과되면 순서대로 기다려야 한다.", "정답: 초과 시 대기열 등록 후 순차 이용이라는 내용과 일치합니다."),
                                opt("졸업생도 자유롭게 이용할 수 있다.", "👂 재학생 및 교직원이 대상입니다."),
                                opt("동시 접속 인원에 제한이 없다.", "👂 최대 200명으로 제한이 있습니다."),
                                opt("초과 시 이용이 완전히 차단된다.", "👂 대기열 등록 후 순차 이용이 가능합니다.")
                        ), 0, "👂 '대기열 등록 후 순차 이용'이라는 조건을 놓치면 완전히 차단된다고 오해하기 쉽습니다.", "[안내문일치 소리단서] 재학생·교직원(대상) / 최대 200명(제한) / 초과 시 대기열(대안). 소리 내어 조건을 확인하세요.")),
                onePassage(PassageCategory.READING, "실용문 독해",
                        "[연구 윤리 위반 신고 절차 안내]\n1단계: 온라인 신고 시스템 접수\n2단계: 예비 조사 (접수 후 2주 이내)\n3단계: 본조사 여부 결정 및 통보",
                        q("이 글의 내용과 같은 것을 고르십시오.", List.of(
                                opt("신고 접수 후 2주 이내에 예비 조사가 이루어진다.", "정답: 예비 조사가 접수 후 2주 이내에 이루어진다고 했습니다."),
                                opt("신고는 서면으로만 가능하다.", "👂 온라인 신고 시스템으로 접수합니다."),
                                opt("본조사는 신고 즉시 시작된다.", "👂 예비 조사 후 본조사 여부가 결정됩니다."),
                                opt("신고 절차에는 제한 시간이 없다.", "👂 예비 조사에 2주 이내라는 기한이 있습니다.")
                        ), 0, "🎧 '1단계→2단계→3단계'라는 절차 순서를 놓치기 쉽습니다.", "[실용문 소리단서] 온라인 접수(1단계) → 예비 조사(2단계) → 본조사 결정(3단계). 소리 내어 절차 순서를 확인하세요."))
        );

        List<PassageSeed> lv56w1_1st_r31to40 = List.of(
                onePassage(PassageCategory.READING, "문장 순서 배열",
                        "(가) 하지만 최근에는 빅데이터 분석 기술이 발전하면서 이런 한계가 극복되고 있다.\n(나) 예전에는 사회 현상을 정량적으로 분석하기가 매우 어려웠다.\n(다) 방대한 데이터를 실시간으로 처리하는 것이 가능해졌기 때문이다.\n(라) 이제는 사회과학 연구에서도 데이터 기반 분석이 보편화되고 있다.",
                        q("순서대로 배열한 것을 고르십시오.", List.of(
                                opt("(나)-(가)-(다)-(라)", "정답: 과거 한계(나) → 극복(가) → 이유(다) → 현재 상황(라) 순서가 자연스럽습니다."),
                                opt("(가)-(나)-(다)-(라)", "🔁 극복(가)이 먼저 나오면 앞선 한계(나)가 없어 어색합니다."),
                                opt("(다)-(라)-(나)-(가)", "🔁 이유(다)가 먼저 나오면 문맥의 흐름이 어색합니다."),
                                opt("(나)-(다)-(가)-(라)", "🔁 이유(다)가 극복(가)보다 먼저 나오면 순서가 부자연스럽습니다.")
                        ), 0, "🔁 '하지만'이라는 접속어가 이끄는 문장의 위치를 놓치기 쉽습니다.", "[순서배열 소리단서] 예전 한계(나) → 하지만 극복(가) → 이유(다) → 현재(라). 소리 내어 접속어를 확인하며 순서를 잡으세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 연구 결과는 표본의 대표성이 부족하여 일반화하기에는 (        ) 있다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("무리가", "정답: 표본이 부족해 일반화가 어렵다는 문맥에 맞는 표현입니다."),
                                opt("가치가", "👂 문맥상 자연스럽지 않은 표현입니다."),
                                opt("의의가", "👂 긍정적 평가를 나타내는 표현으로 한계를 지적하는 문맥과 어울리지 않습니다."),
                                opt("전망이", "👂 문맥과 무관한 표현입니다.")
                        ), 0, "👂 '무리가 있다'라는 한계 지적 표현을 놓치기 쉽습니다.", "[빈칸추론 소리단서] 표본 대표성 부족(한계) → 일반화 무리(결론). 소리 내어 읽으며 한계 지적의 어조를 확인하세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "저는 박사과정 초반에 논문 심사에서 좋은 평가만 받으려고 했습니다. 그러다 보니 오히려 방어적인 태도로 비판을 회피하게 되었습니다. 이제는 비판을 연구를 발전시키는 자산으로 받아들이는 것이 더 나은 연구자가 되는 길이라는 것을 알게 되었습니다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("비판을 열린 자세로 받아들이는 것이 연구 발전에 도움이 된다.", "정답: 비판을 자산으로 받아들이는 것이 더 나은 연구자가 되는 길이라는 깨달음이 중심 내용입니다."),
                                opt("좋은 평가만 받는 것이 가장 중요하다.", "💬 글쓴이의 깨달음과 반대되는 내용입니다."),
                                opt("비판은 무조건 피해야 한다.", "💬 언급된 내용과 반대입니다."),
                                opt("박사과정은 쉽게 마칠 수 있다.", "💬 언급되지 않은 내용입니다.")
                        ), 0, "💬 '좋은 평가만 받으려 했다'는 초반 언급과 실제 강조점인 '비판 수용'을 혼동하기 쉽습니다.", "[중심내용 소리단서] 좋은 평가만 추구(과거 실패) → 방어적 태도(문제) → 비판 수용(개선) → 나은 연구자(현재). 소리 내어 변화를 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 이론은 발표 당시에는 (        ) 받아들여지지 않았지만, 훗날 재조명되었다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("널리", "정답: 발표 당시에는 폭넓게 받아들여지지 않았다가 훗날 재조명되었다는 문맥에 맞습니다."),
                                opt("전혀", "👂 뒤에 '훗날 재조명되었다'는 내용과 이어지려면 완전한 부정보다는 정도의 부정이 더 자연스럽습니다."),
                                opt("드물게", "👂 문맥과 다소 어울리지 않는 표현입니다."),
                                opt("서서히", "👂 시간의 경과를 나타내는 표현으로 '당시에는'과 어울리지 않습니다.")
                        ), 0, "👂 '널리'와 '전혀/드물게'처럼 정도가 다른 부사를 혼동하기 쉽습니다.", "[빈칸추론 소리단서] 발표 당시(과거) → 널리 받아들여지지 않음(초기 반응) → 훗날 재조명(변화). 소리 내어 읽으며 시간 흐름을 확인하세요.")),
                onePassage(PassageCategory.READING, "실용문 독해",
                        "[대학원생 연구비 정산 안내]\n1. 정산 신청은 연구 종료 후 30일 이내에 완료해야 합니다.\n2. 영수증 원본을 첨부해야 하며, 사본은 인정되지 않습니다.\n3. 기한 초과 시 다음 연구비 신청이 제한됩니다.",
                        q("이 안내문을 따를 때 가장 알맞은 행동은?", List.of(
                                opt("연구 종료 후 30일 안에 영수증 원본을 첨부해 정산 신청을 한다.", "정답: 세 가지 조건(30일 이내, 원본 첨부, 기한 준수)을 모두 충족하는 행동입니다."),
                                opt("연구 종료 후 두 달 뒤에 신청한다.", "👂 30일 이내라는 기한을 넘깁니다."),
                                opt("영수증 사본을 첨부한다.", "👂 사본은 인정되지 않습니다."),
                                opt("기한을 넘겨도 다음 연구비 신청에 문제없다고 생각한다.", "👂 기한 초과 시 다음 신청이 제한됩니다.")
                        ), 0, "🔁 세 가지 조건 중 일부만 기억하고 나머지를 놓치기 쉽습니다.", "[실용문 소리단서] 30일 이내+원본 첨부+기한 준수(세 조건). 소리 내어 조건을 하나씩 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 정책 토론에서 참석자들은 (        ) 입장 차를 좁히지 못한 채 산회했다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("끝내", "정답: 결국 입장 차를 좁히지 못했다는 문맥에 맞는 부사입니다."),
                                opt("이미", "👂 결과를 나타내는 문맥과 시제상 어울리지 않습니다."),
                                opt("어느새", "👂 시간의 흐름을 나타내는 표현으로 문맥과 어울리지 않습니다."),
                                opt("갑자기", "👂 문맥과 무관한 표현입니다.")
                        ), 0, "👂 '끝내'라는 결과 강조 부사를 놓치기 쉽습니다.", "[빈칸추론 소리단서] 정책 토론(상황) → 끝내(결과 강조) → 좁히지 못함(결말). 소리 내어 읽으며 부사의 의미를 확인하세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "[학술 세미나 발표 신청 안내]\n신청 자격: 대학원 재학생 및 수료생\n초록 제출 마감: 발표일 4주 전\n채택 여부는 심사위원회에서 결정",
                        q("안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("초록은 발표일보다 4주 일찍 제출해야 한다.", "정답: 초록 제출 마감이 발표일 4주 전이라는 내용과 일치합니다."),
                                opt("학부생도 신청할 수 있다.", "👂 대학원 재학생 및 수료생이 자격 조건입니다."),
                                opt("신청하면 자동으로 채택된다.", "👂 심사위원회에서 채택 여부를 결정합니다."),
                                opt("초록 제출 기한은 없다.", "👂 발표일 4주 전이라는 기한이 있습니다.")
                        ), 0, "👂 '심사위원회 결정'이라는 조건을 놓치면 자동 채택된다고 오해하기 쉽습니다.", "[안내문일치 소리단서] 대학원생(자격) / 4주 전 마감(기한) / 심사위원회 결정(조건). 소리 내어 항목을 확인하세요.")),
                onePassage(PassageCategory.READING, "실용문 독해",
                        "[학술지 심사위원 위촉 안내]\n위촉 대상: 관련 분야 박사 학위 소지자로서 최근 5년 내 논문 3편 이상 게재자\n심사 건당 소정의 심사료가 지급되며, 심사 완료 후 익월 말에 정산됩니다.",
                        q("이 글의 내용과 같은 것을 고르십시오.", List.of(
                                opt("심사료는 심사가 끝난 다음 달 말에 지급된다.", "정답: 심사 완료 후 익월 말에 정산된다고 했습니다."),
                                opt("석사 학위 소지자도 위촉될 수 있다.", "👂 박사 학위 소지자가 대상입니다."),
                                opt("논문 게재 실적은 요구되지 않는다.", "👂 최근 5년 내 논문 3편 이상 게재자가 조건입니다."),
                                opt("심사료는 심사 즉시 지급된다.", "👂 익월 말에 정산됩니다.")
                        ), 0, "🎧 '심사 완료 후'와 '익월 말'이라는 두 시점 정보를 혼동하기 쉽습니다.", "[실용문 소리단서] 박사 학위+논문 3편(자격) → 심사료 지급(익월 말 정산). 소리 내어 조건과 시점을 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 정책 제안은 실현 가능성보다 이상적인 목표에 (        ) 있다는 비판을 받았다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("치우쳐", "정답: 한쪽으로 지나치게 기울어졌다는 비판적 문맥에 맞는 표현입니다."),
                                opt("근거하여", "👂 비판의 대상이 되는 문맥과 어울리지 않는 중립적 표현입니다."),
                                opt("부합하여", "👂 긍정적 평가를 나타내는 표현으로 비판의 문맥과 반대됩니다."),
                                opt("기반하여", "👂 중립적 표현으로 비판적 문맥과 어울리지 않습니다.")
                        ), 0, "👂 '치우치다'라는 비판적 어감의 동사를 놓치기 쉽습니다.", "[빈칸추론 소리단서] 실현 가능성 부족(문제) → 이상적 목표에 치우침(비판). 소리 내어 읽으며 비판의 어조를 확인하세요.")),
                onePassage(PassageCategory.READING, "안내문 일치",
                        "[연구실 안전교육 이수 안내]\n대상: 실험실 출입 예정인 전 연구원\n이수 방식: 온라인 교육 2시간 + 현장 실습 1시간\n미이수 시 실험실 출입증이 발급되지 않음",
                        q("안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("교육을 이수하지 않으면 실험실에 출입할 수 없다.", "정답: 미이수 시 출입증이 발급되지 않는다고 했습니다."),
                                opt("온라인 교육만으로 이수가 완료된다.", "👂 온라인 교육 2시간과 현장 실습 1시간이 모두 필요합니다."),
                                opt("이 교육은 특정 연구원에게만 해당된다.", "👂 실험실 출입 예정인 전 연구원이 대상입니다."),
                                opt("현장 실습은 선택 사항이다.", "👂 온라인 교육과 현장 실습 모두 필수입니다.")
                        ), 0, "👂 '온라인 교육 2시간 + 현장 실습 1시간'이라는 두 요소를 모두 놓치지 않아야 합니다.", "[안내문일치 소리단서] 온라인 2시간+현장 1시간(이수 방식) / 미이수 시 출입증 미발급(제재). 소리 내어 두 요소를 확인하세요."))
        );

        return new WeekSeed("WEEK 1: 5~6급 쉐도잉 리듬 기초 다지기",
                "5~6급 수준의 학술·시사·정책·전문 담화를 다루며 청각 신호(🎧억양·강세 / 💬어조·의도 / 👂발음·어휘 / 🔁반복·상투구) 태그로 함정 유형을 익힌다. "
                        + "3~4급보다 문장이 길고 복잡해지므로 끊어 읽기와 억양 변화를 소리 내어 세밀하게 확인하며 학습한다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 학술대회·논문심사·연구윤리·정책토론 소재. 청각 신호 태그를 확인하며 소리 내어 학습하세요.",
                                merge(lv56w1_1st_l1to10, lv56w1_1st_l11to20, lv56w1_1st_r21to30, lv56w1_1st_r31to40))
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
