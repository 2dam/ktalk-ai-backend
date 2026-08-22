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
 * 혼합 적응형(ADAPTIVE_MIXED) 유형의 1~2급 "TOPIK 유연 적응 워크북" 커리큘럼을 심는다.
 * STRATEGIC_ANALYST/VISUAL_IMMERSIVE/AUDITORY_EMPATHETIC/EXPERIENTIAL_ACTOR와 동일한 골격(레코드/헬퍼,
 * 8주 + 모의고사 2회 + Final 1회, 총 2,450문항)을 쓰되, trapNote/strategyTip을 완전히 새로운
 * "모드 전환" 언어로 설계한다 — LearnerType.ADAPTIVE_MIXED의 studyTip("주간 단위로 학습 방식을
 * 교차(월-영상, 화-필기, 수-청취)하며 자기 점검 루틴을 유지하세요")을 모든 문항에 반영한다.
 * 다른 유형들이 하나의 고정된 인지 스타일(색깔 코딩, 청각 신호, 오답 코드 분석, 신체 활동)을
 * 밀고 나가는 것과 달리, ADAPTIVE_MIXED는 "상황에 따라 접근법을 유연하게 바꾸는 능력" 자체가
 * 학습 목표다. 이를 위해 두 가지 원칙을 지킨다.
 * ① 소재 다양성: 매 문항마다 서로 다른 실생활 상황(식당·병원·교통·쇼핑·학교·직장·우체국·은행·
 *    미용실·도서관·헬스장·여행·관공서·부동산 등)을 최대한 겹치지 않게 순환시켜, 특정 주제에
 *    치우치지 않고 "어떤 상황이 나와도 대응할 수 있는" 감각을 기른다.
 * ② 유형 다양성: 문제 유형(이어질 대답/행동 고르기/장소 고르기/세부 정보/이유 추론/의도 파악/
 *    일치 내용/중심 생각/목적 파악 등)도 최대한 골고루 섞어 특정 유형에만 익숙해지지 않게 한다.
 * 1~2급 초급 수준이므로 짧고 단순한 대화와 쉬운 어휘로 구성하되, 위 두 다양성 원칙은 동일하게 적용한다.
 *
 * [모드 전환 태그 설계 - ADAPTIVE_MIXED 고유 4종]
 * 🎬 장면모드 함정: 상황을 하나의 장면처럼 머릿속에 그려보지 않아 인물·장소·역할 관계를 혼동함.
 * ✏️ 구조화모드 함정: 여러 정보(숫자·시간·조건 등)를 표나 목록으로 정리해보지 않아 서로 혼동함.
 * 🎧 소리모드 함정: 소리 내어 다시 읽어보지 않아 어조·의도·부정어 등 청각적 단서를 놓침.
 * 🔄 전환모드 함정: 하나의 접근법이나 앞선 정보에만 머물러 상황이 바뀐 지점을 재확인하지 않아 놓친 함정.
 * strategyTip은 항상 "[적응전략: ○○모드] ..." 형식으로 시작해 그 모드에 맞는 구체적 확인 행동으로 끝난다.
 */
@Component
@RequiredArgsConstructor
@Order(22)
public class AdaptiveMixedCurriculumDataLoader implements CommandLineRunner {

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
        curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.ADAPTIVE_MIXED, TopikLevel.LEVEL_1)
                .ifPresent(this::deleteExisting);

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.ADAPTIVE_MIXED);
        curriculum.setTitle("TOPIK 유연 적응 워크북");
        curriculum.setTargetLevelLabel("1~2급 전 과정");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_1);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_2);
        curriculum.setUsageNote(
                "모든 문제에 모드 전환 태그(🎬 장면모드 / ✏️ 구조화모드 / 🎧 소리모드 / 🔄 전환모드)로 "
                        + "함정 포인트를 구분합니다. 문제를 풀고 정답을 확인한 뒤에는 반드시 strategyTip의 적응전략을 "
                        + "그 자리에서 직접 실행하세요 — 상황마다 가장 잘 맞는 모드로 전환하며 점검하는 연습이 "
                        + "핵심입니다. 매일 다른 상황(식당·병원·교통·쇼핑 등)과 다른 문제 유형이 골고루 섞여 "
                        + "나오니, 특정 유형에만 익숙해지지 말고 어떤 상황이 나와도 유연하게 대응하는 감각을 "
                        + "기르세요. 주간 단위로 학습 방식을 교차(월-영상, 화-필기, 수-청취)하며 자기 점검 "
                        + "루틴을 유지하세요.");

        List<WeekSeed> weeks = List.of(week1());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("🔄 TOPIK 커리큘럼(혼합 적응형, 1~2급) WEEK1 1차 시딩 완료!");
    }

    /** 재시딩 전 기존 커리큘럼을 지운다. day는 부모의 cascade 대상이 아니라 먼저 지워야 한다. */
    private void deleteExisting(Curriculum existing) {
        List<CurriculumDay> days = curriculumDayRepository.findByCurriculumId(existing.getId());
        curriculumDayRepository.deleteAll(days);
        userCurriculumProgressRepository.deleteByCurriculumId(existing.getId());
        curriculumRepository.delete(existing);
        curriculumRepository.flush();
    }

    // ===================== WEEK 1: 1~2급 적응 기초 다지기 =====================

    private static final String WEEK1_ANSWER_NOTE_TEMPLATE = """
            [🔄 적응 기록장 - 1차 40문항용]
            문제를 틀렸을 때 모드 전환 태그로 표시하며 나의 취약 유형을 확인해보세요.
            그리고 반드시 strategyTip의 적응전략을 그 자리에서 직접 실행하세요.

            문제 번호(1~40) | 틀린 이유(해당 태그 동그라미) | 적응전략 실행 여부(V표시)
            예) 3번 | 🎬 (장면 그려보기 놓침) | V

            [🔄 모드 전환 태그별 취약 유형 가이드]
            🎬 장면모드 함정: 상황을 장면처럼 그려보지 않아 인물·장소·역할 관계를 혼동함.
            ✏️ 구조화모드 함정: 숫자·시간·조건 등 여러 정보를 표로 정리해보지 않아 혼동함.
            🎧 소리모드 함정: 소리 내어 다시 읽어보지 않아 어조·의도·부정어를 놓침.
            🔄 전환모드 함정: 앞선 정보나 한 가지 접근법에 머물러 상황이 바뀐 지점을 놓침.

            같은 태그가 반복해서 표시된다면, 그 모드의 적응전략을 다음 학습 때 우선적으로 실행하세요.
            매일 다른 상황·다른 유형이 섞여 나오니, 정답을 맞혔어도 왜 그 모드가 맞았는지 되짚어보세요.
            """;

    private WeekSeed week1() {
        List<PassageSeed> listening1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "이어질 대답 고르기",
                        "여자: 여기요, 주문할게요.\n남자: 네, 뭘 드시겠어요?\n여자: 김치찌개 하나 주세요.",
                        q("남자의 대답으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("네, 알겠습니다.", "정답: 주문을 받았다는 응답이 자연스럽습니다."),
                                opt("얼마예요?", "🔄 손님이 물을 질문이지 직원이 할 질문이 아닙니다."),
                                opt("저는 안 먹어요.", "🔄 상황과 무관한 대답입니다."),
                                opt("여기 앉으세요.", "🔄 이미 자리에 앉아 주문하는 상황이므로 순서가 맞지 않습니다.")
                        ), 0, "🎬 장면모드 함정: 식당에서 '손님이 주문 → 직원이 응답'하는 장면을 그려보지 않으면 역할이 뒤바뀐 답을 고르기 쉽습니다.",
                                "[적응전략: 장면모드] 이 대화를 식당 장면처럼 머릿속으로 그려보고, 누가 손님이고 누가 직원인지 표시해 보세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 대답 고르기",
                        "여자: 어디가 아파서 오셨어요?\n남자: 목이 아프고 열이 나요.\n여자: 언제부터 그러셨어요?",
                        q("남자의 대답으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("어제부터요.", "정답: '언제부터'라는 질문에 시점으로 답하는 것이 자연스럽습니다."),
                                opt("네, 목이 아파요.", "🔄 이미 앞에서 말한 증상을 반복할 뿐 시점 질문에 답하지 않았습니다."),
                                opt("병원이 멀어요.", "🔄 질문과 무관한 대답입니다."),
                                opt("약을 먹었어요.", "🔄 아직 진료 중인 상황과 맞지 않습니다.")
                        ), 0, "✏️ 구조화모드 함정: '증상'과 '시작 시점'을 표로 나눠 정리하지 않으면 이미 나온 증상 정보로 다시 답하기 쉽습니다.",
                                "[적응전략: 구조화모드] 노트에 '증상 | 시작 시점' 두 칸을 그리고 대화 내용을 각각 채워 넣어 보세요.")),
                onePassage(PassageCategory.LISTENING, "행동 고르기",
                        "여자: 이 버스가 시청 앞에 가요?\n남자: 아니요, 저 버스를 타셔야 해요. 3번 버스요.\n여자: 아, 감사합니다.",
                        q("여자가 다음에 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("3번 버스를 탄다.", "정답: 남자가 알려준 버스를 타는 것이 자연스러운 다음 행동입니다."),
                                opt("지금 타고 있는 버스를 계속 탄다.", "🔄 이 버스는 시청에 가지 않는다고 했으므로 반대됩니다."),
                                opt("택시를 부른다.", "🔄 언급되지 않은 행동입니다."),
                                opt("걸어서 간다.", "🔄 언급되지 않은 행동입니다.")
                        ), 0, "🎧 소리모드 함정: '아니요'라는 부정 응답을 놓치고 원래 타고 있던 버스가 맞다고 착각하기 쉽습니다.",
                                "[적응전략: 소리모드] '아니요, 저 버스를 타셔야 해요'라는 문장을 소리 내어 다시 읽고 부정어에 강세를 주어 말해 보세요.")),
                onePassage(PassageCategory.LISTENING, "행동 고르기",
                        "남자: 이 옷 좀 더 큰 사이즈 있어요?\n여자: 네, 잠시만요. 이거 한번 입어 보세요.\n남자: 감사합니다.",
                        q("남자가 다음에 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("옷을 입어 본다.", "정답: 여자가 건넨 옷을 입어 보라고 했으므로 자연스러운 다음 행동입니다."),
                                opt("옷을 계산한다.", "🔄 아직 입어보지 않았으므로 순서가 이릅니다."),
                                opt("가게를 나간다.", "🔄 옷을 살펴보는 중이므로 맞지 않습니다."),
                                opt("다른 가게로 간다.", "🔄 언급되지 않은 행동입니다.")
                        ), 0, "🎬 장면모드 함정: '옷을 건네고 입어보라고 권하는' 장면을 그려보지 않으면 계산이나 퇴장 등 앞선 단계를 건너뛴 답을 고르기 쉽습니다.",
                                "[적응전략: 장면모드] '큰 사이즈 요청→옷 건넴→입어보기 권유'라는 순서를 장면으로 그려보고 다음 순서를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 대답 고르기",
                        "여자: 이 소포를 부치고 싶은데요.\n남자: 네, 어디로 보내세요?\n여자: 부산으로 보내 주세요.",
                        q("남자의 다음 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("무게를 좀 재 볼게요.", "정답: 소포를 부치는 절차상 무게 확인이 자연스럽게 이어집니다."),
                                opt("네, 소포가 왔어요.", "🔄 상황과 맞지 않는 응답입니다."),
                                opt("이건 제 거예요.", "🔄 언급되지 않은 내용입니다."),
                                opt("부산에 살아요.", "🔄 질문과 무관한 대답입니다.")
                        ), 0, "✏️ 구조화모드 함정: 소포 발송 절차(목적지 확인→무게 측정→요금 안내)를 순서대로 정리하지 않으면 엉뚱한 단계의 답을 고르기 쉽습니다.",
                                "[적응전략: 구조화모드] '목적지 확인 → 무게 측정 → 요금 안내'라는 절차를 노트에 화살표로 순서대로 써 보세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 대답 고르기",
                        "남자: 머리를 좀 짧게 자르고 싶은데요.\n여자: 어느 정도로 잘라 드릴까요?\n남자: 귀가 보일 정도로 잘라 주세요.",
                        q("여자의 다음 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("네, 알겠습니다.", "정답: 손님의 요청을 확인하는 자연스러운 응답입니다."),
                                opt("머리가 길어요.", "🔄 손님이 이미 짧게 자르고 싶다고 했으므로 맞지 않습니다."),
                                opt("감사합니다, 다음에 또 오세요.", "🔄 아직 자르기 전이므로 마무리 인사는 순서가 이릅니다."),
                                opt("저는 미용사가 아니에요.", "🔄 상황과 전혀 맞지 않습니다.")
                        ), 0, "🎧 소리모드 함정: '귀가 보일 정도로'라는 구체적 요청을 소리 내어 확인하지 않으면 마무리 인사 등 엉뚱한 순서의 답을 고르기 쉽습니다.",
                                "[적응전략: 소리모드] '귀가 보일 정도로 잘라 주세요'를 소리 내어 다시 읽고, 미용사가 다음에 할 확인 응답을 말해 보세요.")),
                onePassage(PassageCategory.LISTENING, "장소 고르기",
                        "여자: 여기가 어디예요? 책 냄새가 좋네요.\n남자: 여기는 학교 도서관이에요. 책이 정말 많죠?\n여자: 네, 정말 조용하고 좋아요.",
                        q("두 사람이 있는 장소로 가장 알맞은 곳을 고르십시오.", List.of(
                                opt("도서관", "정답: '학교 도서관'이라고 직접 언급했습니다."),
                                opt("서점", "🔄 책을 파는 곳이 아니라 학교 시설이라고 했으므로 다릅니다."),
                                opt("교실", "🔄 언급되지 않은 장소입니다."),
                                opt("카페", "🔄 언급되지 않은 장소입니다.")
                        ), 0, "🎬 장면모드 함정: '책 냄새', '조용함'이라는 분위기 단서만 보고 서점이나 카페로 착각하기 쉽습니다.",
                                "[적응전략: 장면모드] '학교 도서관'이라는 직접 언급을 장면 속에 표시하고, 분위기 단서와 헷갈리지 않도록 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 회의가 몇 시에 시작해요?\n여자: 두 시부터예요. 회의실은 3층이에요.\n남자: 네, 알겠습니다.",
                        q("남자가 알게 된 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("회의는 두 시에 3층에서 시작한다.", "정답: 시간과 장소 정보가 모두 포함된 정확한 요약입니다."),
                                opt("회의는 세 시에 시작한다.", "🔄 두 시라고 했으므로 틀린 정보입니다."),
                                opt("회의실은 2층이다.", "🔄 3층이라고 했으므로 틀린 정보입니다."),
                                opt("회의는 취소되었다.", "🔄 언급되지 않은 내용입니다.")
                        ), 0, "✏️ 구조화모드 함정: '시간'과 '장소' 두 정보를 표로 나눠 적지 않으면 숫자를 혼동하기 쉽습니다.",
                                "[적응전략: 구조화모드] '시간: 두 시 | 장소: 3층' 형태로 표를 그려 정보를 정리해 보세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 대답 고르기",
                        "여자: 이 책 빌리고 싶은데요.\n남자: 학생증 있으세요?\n여자: 네, 여기 있어요.",
                        q("남자의 다음 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("일주일 동안 빌리실 수 있어요.", "정답: 대출 절차가 이어지는 자연스러운 안내입니다."),
                                opt("학생증이 없으시네요.", "🔄 여자가 이미 학생증을 건넸으므로 맞지 않습니다."),
                                opt("책을 반납해 주세요.", "🔄 아직 빌리는 중이므로 반납 안내는 순서가 맞지 않습니다."),
                                opt("여기는 도서관이 아니에요.", "🔄 상황과 전혀 맞지 않습니다.")
                        ), 0, "🔄 전환모드 함정: 대출 신청 상황에서 반납이나 안내 거절 등 다른 상황의 응답으로 착각하기 쉽습니다.",
                                "[적응전략: 전환모드] 이 대화가 '대출 신청' 상황임을 다시 확인하고, 반납이나 거절 상황과 헷갈리지 않았는지 점검해 보세요.")),
                onePassage(PassageCategory.LISTENING, "행동 고르기",
                        "남자: 저 오늘부터 운동하려고 하는데요.\n여자: 네, 먼저 이 신청서를 작성해 주세요.\n남자: 네, 알겠습니다.",
                        q("남자가 다음에 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("신청서를 작성한다.", "정답: 여자의 안내에 따라 다음에 할 행동입니다."),
                                opt("운동을 시작한다.", "🔄 신청서 작성이 먼저이므로 순서가 맞지 않습니다."),
                                opt("집으로 돌아간다.", "🔄 언급되지 않은 행동입니다."),
                                opt("회비를 낸다.", "🔄 아직 언급되지 않은 절차입니다.")
                        ), 0, "🎬 장면모드 함정: '신청서 작성 → 운동 시작'이라는 순서를 장면으로 그려보지 않으면 순서를 건너뛴 답을 고르기 쉽습니다.",
                                "[적응전략: 장면모드] '신청서 작성(먼저)→운동 시작(나중)'이라는 순서를 장면으로 그려보고 확인하세요."))
        );

        return new WeekSeed("WEEK 1: 1~2급 적응 기초 다지기 (1차, 다양한 상황 순환)",
                "매일 다른 실생활 상황(식당·병원·교통·쇼핑·우체국·미용실·학교·직장·도서관·헬스장 등)과 "
                        + "다른 문제 유형을 골고루 섞어 풀며, 모드 전환 태그(🎬 장면모드 / ✏️ 구조화모드 / "
                        + "🎧 소리모드 / 🔄 전환모드)로 나에게 맞는 적응전략을 찾는다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("WEEK1 1차 - 듣기 1~10번 (이어질 대답 / 행동 고르기 / 장소 고르기 / 세부 정보 파악) - 식당·병원·교통·쇼핑·우체국·미용실·학교·직장·도서관·헬스장",
                                merge(listening1to10))
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
            week.setAnswerNoteTemplate(weekSeed.template());
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
