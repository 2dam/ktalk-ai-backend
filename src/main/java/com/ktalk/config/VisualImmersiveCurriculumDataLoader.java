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
 * 시각적 몰입형(VISUAL_IMMERSIVE) 유형의 1~2급 "TOPIK 컬러맵 완전분석" 커리큘럼을 심는다.
 * StrategicAnalystCurriculumDataLoader와 동일한 골격(레코드/헬퍼, 8주 + 모의고사 2회 + Final 1회,
 * 총 2,450문항)을 쓰되, trapNote/strategyTip을 색깔 코딩·마인드맵·도식화 언어로 재구성한다 —
 * LearnerType.VISUAL_IMMERSIVE의 studyTip("도식화·마인드맵 정리와 색상별 어휘 분류")을 모든 문항에
 * 반영한다. 3~4급 과정(VisualImmersiveLevel34CurriculumDataLoader)과는 완전히 분리된 별도의
 * 8주 과정으로, 같은 learner_type이라도 targetLevelFrom(LEVEL_1)으로 구분되는 별도 Curriculum
 * 레코드를 갖는다.
 */
@Component
@RequiredArgsConstructor
@Order(13)
public class VisualImmersiveCurriculumDataLoader implements CommandLineRunner {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumDayRepository curriculumDayRepository;
    private final UserCurriculumProgressRepository userCurriculumProgressRepository;

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

    /** 색상 태그 설명 없이 함정 포인트만 있는 문항용. */
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
        curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.VISUAL_IMMERSIVE, TopikLevel.LEVEL_1)
                .ifPresent(this::deleteExisting);

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.VISUAL_IMMERSIVE);
        curriculum.setTitle("TOPIK 컬러맵 완전분석 노트");
        curriculum.setTargetLevelLabel("1~2급 전 과정");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_1);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_2);
        curriculum.setUsageNote(
                "모든 문제에 색깔 태그(🔴🟢🔵🟣)로 함정 포인트를 구분하고, 문법·어휘는 마인드맵 구조로 "
                        + "설명합니다. 색깔 펜 3~4자루를 준비해 오답 노트를 도식화하며 시각적으로 기억을 강화하세요.");

        List<WeekSeed> weeks = List.of(week1());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("🎨 TOPIK 커리큘럼(시각적 몰입형, 1~2급) WEEK1 1차 신설(40문항) - 전략적 분석가와 같은 패턴으로 착수!");
    }

    /** 재시딩 전 기존 커리큘럼을 지운다. day는 부모의 cascade 대상이 아니라 먼저 지워야 한다. */
    private void deleteExisting(Curriculum existing) {
        List<CurriculumDay> days = curriculumDayRepository.findByCurriculumId(existing.getId());
        curriculumDayRepository.deleteAll(days);
        userCurriculumProgressRepository.deleteByCurriculumId(existing.getId());
        curriculumRepository.delete(existing);
        curriculumRepository.flush();
    }

    // ===================== WEEK 1: 1~2급 컬러맵 기초 다지기 =====================

    private static final String WEEK1_ANSWER_NOTE_TEMPLATE = """
            [🎨 오답 노트 템플릿 - 1차 40문항용]
            문제를 틀렸을 때 색깔 펜으로 표시하며 나의 취약 유형을 도식화해보세요.

            문제 번호(1~40) | 틀린 이유(해당 색깔 동그라미) | 취약 유형 코드
            예) 3번 | 🔴 (시간/장소 혼동) |

            [🎨 색깔별 취약 유형 코드 가이드]
            🔴 빨강 - 시간/장소 혼동: 대화나 글에 나온 시간, 장소, 숫자 정보를 정확히 기억하지 못함.
            🟢 초록 - 의도 파악 실패: 화자나 글쓴이의 진짜 목적이나 주제를 놓침.
            🔵 파랑 - 어휘 부족: 모르는 단어가 있어 내용 이해에 어려움을 겪음.
            🟣 보라 - 기타: 위에 해당하지 않는 오류(예: 부주의, 시간 부족).

            같은 색깔이 반복해서 칠해진다면, 그 색을 형광펜으로 표시해 다음 학습 때 우선 보완하세요.
            """;

    private WeekSeed week1() {
        List<PassageSeed> listening1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "상황 응답",
                        "여자: 오늘 날씨가 정말 좋네요.\n남자: (        )",
                        q("이어질 남자의 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("네, 산책하기 좋은 날씨예요.", "정답: 날씨가 좋다는 말에 자연스럽게 동의하며 이어갑니다."),
                                opt("네, 저는 학생이에요.", "🔵 파랑 - 질문과 무관한 신분 설명입니다."),
                                opt("아니요, 여기가 아니에요.", "🔵 파랑 - 장소 질문에 대한 답이라 문맥과 안 맞습니다."),
                                opt("네, 안녕히 가세요.", "🔵 파랑 - 작별 인사라 대화 흐름과 안 맞습니다.")
                        ), 0, "🔴 마인드맵 중심어 [날씨]에서 곁가지로 안 뻗어나가는 보기들을 답처럼 보이게 배치합니다.",
                                "[날씨] ── 좋다 → 공감·제안 으로 가지를 뻗어보세요. 정답만 이 가지 위에 있습니다.")),
                onePassage(PassageCategory.LISTENING, "행동/장소 파악",
                        "남자: 여기 우산 파는 곳이 어디예요?\n여자: 편의점에서 팔아요. 저기 모퉁이에 있어요.",
                        q("두 사람이 대화하는 장소로 가장 알맞은 곳을 고르십시오.", List.of(
                                opt("길거리", "정답: 우산 파는 곳의 위치를 묻는 상황은 길에서의 대화입니다."),
                                opt("편의점 안", "🔴 빨강 - 편의점은 '안내받는 목적지'일 뿐 현재 위치가 아닙니다."),
                                opt("우산 가게", "🔴 빨강 - 대화 속 장소가 아니라 목적지로 언급된 곳입니다."),
                                opt("모퉁이 카페", "🔴 빨강 - '모퉁이'라는 단어만 듣고 만든 틀린 조합입니다.")
                        ), 0, "🔴 장소 키워드 조각(편의점, 모퉁이)을 따로 떼어 새 장소처럼 조합하게 합니다.",
                                "[장소 마인드맵] 중심 = 현재 대화 위치, 가지 = 언급된 다른 장소. 편의점/모퉁이는 가지일 뿐입니다.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "여자: 회의가 몇 시에 시작해요?\n남자: 원래 3시였는데 4시로 바뀌었어요.",
                        q("회의가 시작하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("4시", "정답: '바뀌었어요'라는 말 뒤의 최종 시간이 정답입니다."),
                                opt("3시", "🔴 빨강 - 원래 시간(변경 전)에만 꽂혀 최종 정보를 놓치게 합니다."),
                                opt("3시 반", "🔴 빨강 - 대화에 없는 시간을 임의로 만든 오답입니다."),
                                opt("5시", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 숫자가 두 번 나올 때 먼저 들린 숫자를 정답처럼 착각하게 합니다.",
                                "[시간 마인드맵] 원래 시간 → 화살표 → 바뀐 시간. 화살표 뒤의 값만 최종 답으로 남기세요.")),
                onePassage(PassageCategory.LISTENING, "목적/주제 파악",
                        "여자: 이 서류에 이름하고 전화번호를 써 주세요.\n남자: 네, 여기 쓰면 되죠?",
                        q("여자가 남자에게 요청하는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("서류 작성", "정답: 이름과 전화번호를 쓰라는 요청은 서류 작성 요청입니다."),
                                opt("전화 통화", "🟢 초록 - '전화번호'라는 단어만 보고 통화로 착각하게 합니다."),
                                opt("이름 확인", "🟢 초록 - 부분 정보(이름)만으로 전체 목적을 좁혀 오해하게 합니다."),
                                opt("서명 거부", "🟢 초록 - 대화의 흐름과 반대되는 내용입니다.")
                        ), 0, "🟢 세부 단어(전화번호)에 집중시켜 전체 요청 목적(서류 작성)을 놓치게 합니다.",
                                "[목적 마인드맵] 중심 = 요청의 큰 그림, 가지 = 세부 항목(이름·전화번호). 가지 두 개를 합쳐야 중심이 보입니다.")),
                onePassage(PassageCategory.LISTENING, "흐름 추론",
                        "남자: 오늘 저녁에 뭐 할 거예요?\n여자: 집에서 좀 쉬려고요. 요즘 너무 피곤해서요.",
                        q("여자의 다음 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("집에서 휴식을 취한다.", "정답: '집에서 좀 쉬려고요'가 다음 행동의 직접적 근거입니다."),
                                opt("친구를 만나러 나간다.", "🟢 초록 - 언급되지 않은 행동을 임의로 추가한 오답입니다."),
                                opt("회사에서 야근을 한다.", "🟢 초록 - 대화에 없는 상황입니다."),
                                opt("운동을 하러 간다.", "🟢 초록 - '피곤해서'라는 이유와 반대되는 행동입니다.")
                        ), 0, "🟢 '피곤하다'는 이유만 보고 엉뚱한 해소 행동(운동)을 연결하게 합니다.",
                                "[흐름 마인드맵] 이유(피곤함) → 화살표 → 행동(휴식). 이유와 행동이 같은 방향인지 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "상황 응답",
                        "남자: 죄송한데 여기 자리 있어요?\n여자: (        )",
                        q("이어질 여자의 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("아니요, 앉으세요.", "정답: 자리가 비어있다는 뜻으로 자연스럽게 이어집니다."),
                                opt("네, 반가워요.", "🔵 파랑 - 자리 유무 질문과 무관한 인사말입니다."),
                                opt("아니요, 괜찮아요.", "🔵 파랑 - 질문 의도와 어긋나는 답변입니다."),
                                opt("네, 여기가 맞아요.", "🔵 파랑 - 위치 확인 질문에 대한 답처럼 보이지만 문맥과 안 맞습니다.")
                        ), 0, "🔵 '네/아니요' 뒤에 붙는 말만 보고 앞뒤 논리를 확인 안 하게 합니다.",
                                "[대화 마인드맵] 질문 = 자리 있어요? → 대답은 '있다/없다' 가지 중 하나로만 이어져야 합니다.")),
                onePassage(PassageCategory.LISTENING, "행동/장소 파악",
                        "여자: 다음 역에서 내려서 2번 출구로 나가세요.\n남자: 네, 알겠습니다. 감사합니다.",
                        q("두 사람이 이야기하는 상황으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("길을 안내하고 있다.", "정답: 역, 출구 안내는 길 안내 상황입니다."),
                                opt("표를 구매하고 있다.", "🔴 빨강 - '역'이라는 단어만 보고 연상한 오답입니다."),
                                opt("음식을 주문하고 있다.", "🔴 빨강 - 대화 내용과 관련이 없습니다."),
                                opt("전화를 걸고 있다.", "🔴 빨강 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🔴 장소 단어(역)에서 곁가지로 다른 상황(매표소)을 만들어내게 합니다.",
                                "[상황 마인드맵] 중심 단어 '출구·내리다'는 오직 [길 안내] 가지에만 연결됩니다.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 이 옷 얼마예요?\n여자: 원래 3만 원인데 지금 세일해서 2만 원이에요.",
                        q("이 옷의 현재 가격으로 알맞은 것을 고르십시오.", List.of(
                                opt("2만 원", "정답: 세일 후 가격이 현재 최종 가격입니다."),
                                opt("3만 원", "🔴 빨강 - 세일 전 가격(원래 가격)에 꽂힌 오답입니다."),
                                opt("5만 원", "🔴 빨강 - 두 숫자를 더해 만든 오답입니다."),
                                opt("1만 원", "🔴 빨강 - 대화에 없는 숫자입니다.")
                        ), 0, "🔴 두 가격이 연속으로 나올 때 앞의 숫자를 정답처럼 착각하게 합니다.",
                                "[가격 마인드맵] 원래 가격 → 화살표(세일) → 지금 가격. 화살표 뒤 숫자만 최종 답입니다.")),
                onePassage(PassageCategory.LISTENING, "목적/주제 파악",
                        "여자: 내일 소풍 가는데 비가 온대요. 우산 챙기세요.\n남자: 네, 알려줘서 고마워요.",
                        q("여자가 남자에게 말하는 목적으로 알맞은 것을 고르십시오.", List.of(
                                opt("날씨 정보를 알려주려고", "정답: 비 소식과 준비물 안내가 핵심 목적입니다."),
                                opt("소풍을 취소하려고", "🟢 초록 - 언급되지 않은 결론을 임의로 추가한 오답입니다."),
                                opt("우산을 사 달라고 하려고", "🟢 초록 - '우산'이라는 단어만 보고 만든 오답입니다."),
                                opt("고맙다고 인사하려고", "🟢 초록 - 남자의 반응을 여자의 목적으로 착각하게 합니다.")
                        ), 0, "🟢 마지막 문장(고마워요)의 화자를 헷갈려 목적을 반대로 연결하게 합니다.",
                                "[목적 마인드맵] 말한 사람 = 여자, 목적 가지 = 정보 전달. 누가 말했는지부터 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "흐름 추론",
                        "남자: 배가 너무 고픈데 뭐 먹을까요?\n여자: 저기 새로 생긴 식당 어때요? 한번 가 봐요.",
                        q("두 사람이 다음에 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("새로 생긴 식당에 간다.", "정답: 여자의 제안에 이어질 자연스러운 다음 행동입니다."),
                                opt("집에서 요리를 한다.", "🟢 초록 - 대화 내용과 반대되는 행동입니다."),
                                opt("마트에서 장을 본다.", "🟢 초록 - 언급되지 않은 행동입니다."),
                                opt("커피를 마시러 간다.", "🟢 초록 - '식당'을 '카페'로 바꿔치기한 오답입니다.")
                        ), 0, "🟢 제안한 장소의 종류(식당)를 비슷한 다른 장소(카페)로 슬쩍 바꿔치기합니다.",
                                "[흐름 마인드맵] 제안(식당 어때요?) → 화살표 → 동의·행동. 장소 단어를 색칠해 원문과 대조하세요."))
        );

        List<PassageSeed> listening11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "상황 응답",
                        "여자: 이 책 좀 빌려도 될까요?\n남자: (        )",
                        q("이어질 남자의 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("네, 편하게 보세요.", "정답: 허락을 구하는 질문에 자연스럽게 승낙하는 답입니다."),
                                opt("네, 잘 다녀왔어요.", "🔵 파랑 - 문맥과 전혀 관련 없는 인사말입니다."),
                                opt("아니요, 여기 없어요.", "🔵 파랑 - 위치를 묻는 질문에 대한 답처럼 보입니다."),
                                opt("네, 얼마예요?", "🔵 파랑 - 구매 상황에 어울리는 답이라 문맥과 안 맞습니다.")
                        ), 0, "🔵 '빌리다'와 '사다'를 혼동하게 하는 보기를 섞어둡니다.",
                                "[동사 마인드맵] 빌리다 → 허락/승낙 가지, 사다 → 가격 가지. 동사부터 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "행동/장소 파악",
                        "남자: 손님, 몇 분이세요?\n여자: 네 명이요. 창가 자리로 부탁드려요.",
                        q("두 사람이 대화하는 장소로 가장 알맞은 곳을 고르십시오.", List.of(
                                opt("식당", "정답: '손님, 몇 분' 표현은 식당 입구 안내에서 씁니다."),
                                opt("병원", "🔴 빨강 - '몇 분'이라는 단어만 보고 진료 순서로 착각하게 합니다."),
                                opt("영화관", "🔴 빨강 - 자리 관련 단어만 보고 만든 오답입니다."),
                                opt("미용실", "🔴 빨강 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🔴 '자리', '몇 분' 같은 공통 단어로 다른 장소를 연상시킵니다.",
                                "[장소 마인드맵] 핵심 표현 '몇 분이세요+창가 자리'는 오직 [식당] 가지에만 연결됩니다.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "여자: 다음 주 화요일에 시험이 있는데 수요일로 연기됐어요.\n남자: 아, 그렇군요.",
                        q("시험을 보는 요일로 알맞은 것을 고르십시오.", List.of(
                                opt("수요일", "정답: '연기됐어요' 뒤에 나오는 요일이 최종 정보입니다."),
                                opt("화요일", "🔴 빨강 - 원래 예정일(변경 전)에 꽂힌 오답입니다."),
                                opt("목요일", "🔴 빨강 - 대화에 없는 요일입니다."),
                                opt("월요일", "🔴 빨강 - 대화에 없는 요일입니다.")
                        ), 0, "🔴 요일이 두 번 등장할 때 먼저 들린 요일을 답처럼 착각하게 합니다.",
                                "[일정 마인드맵] 원래 일정 → 화살표(연기) → 바뀐 일정. 화살표 뒤 요일만 최종 답입니다.")),
                onePassage(PassageCategory.LISTENING, "목적/주제 파악",
                        "남자: 이 프린터가 자꾸 종이가 걸려요. 좀 봐 주실 수 있어요?\n여자: 네, 제가 확인해 볼게요.",
                        q("남자가 여자에게 요청하는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("프린터 수리 확인", "정답: 고장 상태를 봐 달라는 요청이 핵심입니다."),
                                opt("종이 구매", "🟢 초록 - '종이'라는 단어만 보고 만든 오답입니다."),
                                opt("문서 출력", "🟢 초록 - 프린터라는 단어에서 연상한 오답입니다."),
                                opt("이메일 전송", "🟢 초록 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🟢 기기 이름(프린터)만 보고 엉뚱한 사용 목적을 연결하게 합니다.",
                                "[요청 마인드맵] 문제(종이 걸림) → 화살표 → 요청(봐 주세요). 문제와 요청을 한 쌍으로 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "흐름 추론",
                        "여자: 감기에 걸려서 목이 너무 아파요.\n남자: 그럼 따뜻한 차라도 마셔요. 제가 타 줄게요.",
                        q("남자의 다음 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("따뜻한 차를 타 준다.", "정답: '제가 타 줄게요'가 다음 행동의 직접적 근거입니다."),
                                opt("병원에 데려간다.", "🟢 초록 - 언급되지 않은 행동을 임의로 추가한 오답입니다."),
                                opt("약을 사러 간다.", "🟢 초록 - 대화에 없는 행동입니다."),
                                opt("창문을 연다.", "🟢 초록 - 대화 내용과 반대되는 행동입니다.")
                        ), 0, "🟢 '아프다'는 증상만 보고 병원행 같은 더 큰 행동을 상상하게 합니다.",
                                "[흐름 마인드맵] 마지막 말(제가 ~줄게요)이 곧 다음 행동입니다. 마지막 문장에 형광펜을 치세요.")),
                onePassage(PassageCategory.LISTENING, "상황 응답",
                        "남자: 늦어서 죄송합니다.\n여자: (        )",
                        q("이어질 여자의 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("괜찮아요, 방금 왔어요.", "정답: 사과에 대한 자연스러운 수용 응답입니다."),
                                opt("네, 처음 뵙겠습니다.", "🔵 파랑 - 첫 만남 인사라 문맥과 안 맞습니다."),
                                opt("아니요, 안 늦었어요.", "🔵 파랑 - 상대의 사과를 부정하는 어색한 응답입니다."),
                                opt("감사합니다.", "🔵 파랑 - 사과에 대한 반응으로 어울리지 않습니다.")
                        ), 0, "🔵 사과-수용 짝을 다른 인사 표현으로 바꿔치기합니다.",
                                "[대화 짝 마인드맵] 사과 → 괜찮다/이해한다 가지로만 자연스럽게 이어집니다.")),
                onePassage(PassageCategory.LISTENING, "행동/장소 파악",
                        "여자: 여기 비행기표 좀 바꾸고 싶은데요.\n남자: 네, 여권 좀 보여 주시겠어요?",
                        q("두 사람이 대화하는 장소로 가장 알맞은 곳을 고르십시오.", List.of(
                                opt("공항 카운터", "정답: 비행기표 변경, 여권 확인은 공항 카운터에서 이루어집니다."),
                                opt("여행사 사무실", "🔴 빨강 - 비슷한 업무를 하는 다른 장소로 헷갈리게 합니다."),
                                opt("은행", "🔴 빨강 - 대화 내용과 관련이 없습니다."),
                                opt("우체국", "🔴 빨강 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🔴 비슷한 업무(서류 처리)를 하는 여러 장소를 나열해 헷갈리게 합니다.",
                                "[장소 마인드맵] 핵심 표현 '비행기표+여권'은 오직 [공항 카운터] 가지에만 연결됩니다.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 이 가방 몇 개 남았어요?\n여자: 원래 5개였는데 방금 2개 팔려서 3개 남았어요.",
                        q("가방의 현재 남은 개수로 알맞은 것을 고르십시오.", List.of(
                                opt("3개", "정답: 판매 후 남은 최종 수량이 정답입니다."),
                                opt("5개", "🔴 빨강 - 처음 수량(변경 전)에 꽂힌 오답입니다."),
                                opt("2개", "🔴 빨강 - 팔린 개수를 남은 개수로 착각하게 합니다."),
                                opt("7개", "🔴 빨강 - 두 숫자를 더해 만든 오답입니다.")
                        ), 0, "🔴 숫자 세 개(5, 2, 3)가 연속으로 나올 때 계산을 틀리게 유도합니다.",
                                "[수량 마인드맵] 처음 수량 → 화살표(판매) → 지금 수량. 화살표 뒤 숫자만 최종 답입니다.")),
                onePassage(PassageCategory.LISTENING, "목적/주제 파악",
                        "여자: 내일 회의 자료 준비 다 됐어요? 혹시 도와줄까요?\n남자: 아니요, 거의 다 끝났어요. 감사합니다.",
                        q("여자가 남자에게 말하는 목적으로 알맞은 것을 고르십시오.", List.of(
                                opt("도움을 제안하려고", "정답: '도와줄까요'가 핵심 목적입니다."),
                                opt("회의를 취소하려고", "🟢 초록 - 언급되지 않은 내용을 임의로 추가한 오답입니다."),
                                opt("자료를 요청하려고", "🟢 초록 - '자료'라는 단어만 보고 만든 오답입니다."),
                                opt("감사 인사를 하려고", "🟢 초록 - 남자의 반응을 여자의 목적으로 착각하게 합니다.")
                        ), 0, "🟢 마지막 화자(남자)의 말을 여자의 목적으로 뒤바꿔 놓습니다.",
                                "[목적 마인드맵] 말한 사람=여자, 목적 가지=제안. 화자 이름부터 색으로 표시하는 습관을 들이세요.")),
                onePassage(PassageCategory.LISTENING, "흐름 추론",
                        "남자: 이 짐이 너무 무거워서 혼자 못 들겠어요.\n여자: 제가 반대쪽 잡을게요. 같이 들어요.",
                        q("여자의 다음 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("짐의 반대쪽을 함께 든다.", "정답: '제가 반대쪽 잡을게요'가 다음 행동의 직접적 근거입니다."),
                                opt("다른 사람을 불러온다.", "🟢 초록 - 언급되지 않은 행동을 임의로 추가한 오답입니다."),
                                opt("짐을 대신 다 든다.", "🟢 초록 - '같이'라는 표현과 어긋나는 오답입니다."),
                                opt("짐을 내려놓는다.", "🟢 초록 - 대화 내용과 반대되는 행동입니다.")
                        ), 0, "🟢 '같이'라는 표현을 무시하고 혼자 다 하는 행동으로 과장하게 합니다.",
                                "[흐름 마인드맵] 마지막 말(제가 ~할게요)에 형광펜. '같이'라는 단어도 놓치지 말고 표시하세요."))
        );

        List<PassageSeed> reading21to30 = List.of(
                onePassage(PassageCategory.READING, "핵심 정보 파악",
                        "저는 매일 아침 7시에 일어나서 운동을 합니다. 그리고 8시에 아침을 먹습니다.",
                        q("이 사람이 아침 7시에 하는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("운동하기", "정답: 7시에 일어나서 운동을 한다고 명시되어 있습니다."),
                                opt("아침 먹기", "🔴 빨강 - 8시 활동을 7시 활동으로 착각하게 합니다."),
                                opt("잠자기", "🔴 빨강 - 글의 내용과 반대됩니다."),
                                opt("출근하기", "🔴 빨강 - 글에 없는 내용입니다.")
                        ), 0, "🔴 시간표에서 시각(7시/8시)과 행동을 서로 바꿔 배치한 오답을 넣습니다.",
                                "[시간표 마인드맵] 7시 ── 운동, 8시 ── 아침. 시각마다 다른 색으로 행동을 이어보세요.")),
                onePassage(PassageCategory.READING, "실용문 이해",
                        "○○마트 여름 세일\n기간: 7월 20일 ~ 7월 31일\n전 품목 20% 할인\n장소: 1층 매장",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("세일은 7월에 진행된다.", "정답: 기간이 7월 20일~31일로 명시되어 있습니다."),
                                opt("세일은 2층에서 한다.", "🔵 파랑 - 안내문의 '1층'을 잘못 읽게 만든 오답입니다."),
                                opt("일부 품목만 할인한다.", "🔵 파랑 - '전 품목'이라는 표현과 반대됩니다."),
                                opt("할인율은 30%이다.", "🔵 파랑 - 안내문의 '20%'를 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 숫자(기간/층수/할인율)를 하나씩 슬쩍 바꿔서 정답처럼 보이게 합니다.",
                                "[안내문 마인드맵] 기간·장소·할인율 세 가지를 각각 다른 색 밑줄로 표시하고 보기와 대조하세요.")),
                onePassage(PassageCategory.READING, "목적 추론",
                        "안녕하세요. 다음 주 월요일부터 수요일까지 개인 사정으로 휴가를 사용하고자 합니다. 확인 부탁드립니다.",
                        q("이 글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("휴가를 신청하려고", "정답: 휴가 사용을 알리고 확인을 부탁하는 글입니다."),
                                opt("퇴사를 알리려고", "🟢 초록 - 글에 없는 내용을 추가한 오답입니다."),
                                opt("회의를 요청하려고", "🟢 초록 - 글의 목적과 관련이 없습니다."),
                                opt("사과를 하려고", "🟢 초록 - 글의 어조와 맞지 않는 오답입니다.")
                        ), 0, "🟢 '개인 사정'이라는 단어만 보고 더 큰 사건(퇴사)을 상상하게 합니다.",
                                "[목적 마인드맵] 중심 = 글쓴이의 의도. '휴가'라는 핵심어를 색칠하고 그 단어의 가지만 따라가세요.")),
                onePassage(PassageCategory.READING, "문장 순서 배열",
                        "ㄱ. 그래서 우산을 챙겼습니다.\nㄴ. 아침에 하늘이 흐렸습니다.\nㄷ. 비가 올 것 같았습니다.\nㄹ. 밖에 나가니 정말 비가 왔습니다.",
                        q("다음을 순서대로 맞게 배열한 것을 고르십시오.", List.of(
                                opt("ㄴ → ㄷ → ㄱ → ㄹ", "정답: 흐림 관찰 → 예상 → 대비 → 결과의 순서와 일치합니다."),
                                opt("ㄴ → ㄱ → ㄷ → ㄹ", "🟣 보라 - 대비(ㄱ)가 예상(ㄷ)보다 먼저 나와 인과가 어긋납니다."),
                                opt("ㄷ → ㄴ → ㄱ → ㄹ", "🟣 보라 - 예상(ㄷ)이 관찰(ㄴ)보다 먼저 나와 순서가 어긋납니다."),
                                opt("ㄹ → ㄴ → ㄷ → ㄱ", "🟣 보라 - 결과(ㄹ)가 맨 앞에 와서 인과관계가 거꾸로입니다.")
                        ), 0, "🟣 인과관계 화살표 순서를 무시하고 문장만 뒤섞어 배열하게 합니다.",
                                "[순서 마인드맵] 관찰 → 예상 → 대비 → 결과. 네 단계를 각각 다른 색 번호로 매겨보세요.")),
                onePassage(PassageCategory.READING, "공통 주제 파악",
                        "ㄱ. 지하철을 타고 학교에 갑니다.\nㄴ. 버스로 회사에 갈 때도 있습니다.",
                        q("다음 두 문장의 공통된 주제를 고르십시오.", List.of(
                                opt("교통/이동수단", "정답: 지하철과 버스 모두 이동수단입니다."),
                                opt("학교생활", "🔵 파랑 - 첫 문장의 목적지만 보고 만든 오답입니다."),
                                opt("직장 업무", "🔵 파랑 - 두 번째 문장의 목적지만 보고 만든 오답입니다."),
                                opt("날씨 변화", "🔵 파랑 - 글의 내용과 관련이 없습니다.")
                        ), 0, "🔵 각 문장의 목적지(학교/회사)에 집중시켜 공통된 상위 개념(이동수단)을 놓치게 합니다.",
                                "[주제 마인드맵] 중심 = 공통점. 지하철·버스를 같은 색 동그라미로 묶으면 중심 단어가 보입니다.")),
                onePassage(PassageCategory.READING, "핵심 정보 파악",
                        "저는 고향이 부산입니다. 지금은 서울에서 회사를 다니고 있습니다.",
                        q("이 사람이 지금 사는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("서울", "정답: '지금은 서울에서'라고 명시되어 있습니다."),
                                opt("부산", "🔴 빨강 - 고향(과거/출신지)을 현재 거주지로 착각하게 합니다."),
                                opt("대구", "🔴 빨강 - 글에 없는 지명입니다."),
                                opt("인천", "🔴 빨강 - 글에 없는 지명입니다.")
                        ), 0, "🔴 지명이 두 개 나올 때 먼저 나온 지명(고향)을 정답처럼 착각하게 합니다.",
                                "[장소 마인드맵] 고향 ── 부산(과거), 현재 ── 서울. 시제 표현('지금은')에 형광펜을 치세요.")),
                onePassage(PassageCategory.READING, "실용문 이해",
                        "도서관 이용 안내\n대출 기간: 14일\n1인당 최대 5권까지 대출 가능\n반납은 1층 반납함 이용",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("한 사람이 5권까지 빌릴 수 있다.", "정답: '1인당 최대 5권'이라고 명시되어 있습니다."),
                                opt("대출 기간은 7일이다.", "🔵 파랑 - '14일'을 잘못 읽게 만든 오답입니다."),
                                opt("반납은 2층에서 한다.", "🔵 파랑 - '1층'을 잘못 읽게 만든 오답입니다."),
                                opt("최대 10권까지 빌릴 수 있다.", "🔵 파랑 - '5권'을 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 안내문의 숫자를 하나씩 바꿔서 그럴듯한 오답을 만듭니다.",
                                "[안내문 마인드맵] 기간·권수·장소 세 항목을 각각 다른 색으로 밑줄 긋고 대조하세요.")),
                onePassage(PassageCategory.READING, "목적 추론",
                        "이번 주 토요일에 동네 청소 봉사활동이 있습니다. 관심 있으신 분들은 신청서를 작성해 주세요.",
                        q("이 글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("봉사활동 참가자를 모집하려고", "정답: 신청서 작성을 요청하는 모집 안내입니다."),
                                opt("동네를 홍보하려고", "🟢 초록 - 글에 없는 목적입니다."),
                                opt("청소 방법을 알려주려고", "🟢 초록 - '청소'라는 단어만 보고 만든 오답입니다."),
                                opt("토요일 일정을 취소하려고", "🟢 초록 - 글의 내용과 반대됩니다.")
                        ), 0, "🟢 '청소'라는 단어에 집중시켜 실제 목적(모집)을 놓치게 합니다.",
                                "[목적 마인드맵] 핵심 동사 '신청해 주세요'를 색칠하면 목적(모집)이 바로 드러납니다.")),
                onePassage(PassageCategory.READING, "문장 순서 배열",
                        "ㄱ. 처음에는 낯설고 힘들었습니다.\nㄴ. 한국에 온 지 벌써 1년이 됐습니다.\nㄷ. 지금은 많이 익숙해졌습니다.\nㄹ. 하지만 친구들의 도움으로 적응했습니다.",
                        q("다음을 순서대로 맞게 배열한 것을 고르십시오.", List.of(
                                opt("ㄴ → ㄱ → ㄹ → ㄷ", "정답: 시간 경과 → 초기 어려움 → 극복 → 현재 상태의 흐름과 일치합니다."),
                                opt("ㄴ → ㄷ → ㄱ → ㄹ", "🟣 보라 - 현재(ㄷ)가 초기 어려움(ㄱ)보다 먼저 나와 순서가 어긋납니다."),
                                opt("ㄱ → ㄴ → ㄷ → ㄹ", "🟣 보라 - 도입 문장 없이 어려움(ㄱ)부터 시작해 흐름이 끊깁니다."),
                                opt("ㄹ → ㄱ → ㄴ → ㄷ", "🟣 보라 - 극복(ㄹ)이 어려움(ㄱ)보다 먼저 나와 인과가 거꾸로입니다.")
                        ), 0, "🟣 '하지만'이라는 접속어의 방향을 무시하고 문장을 배열하게 합니다.",
                                "[순서 마인드맵] 도입 → 어려움 → 하지만(반전) → 결과. 접속어 '하지만'을 다른 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "공통 주제 파악",
                        "ㄱ. 김치를 만들어 먹었습니다.\nㄴ. 된장찌개도 끓여 봤습니다.",
                        q("다음 두 문장의 공통된 주제를 고르십시오.", List.of(
                                opt("한국 음식/요리", "정답: 김치와 된장찌개 모두 한국 음식입니다."),
                                opt("여행 계획", "🔵 파랑 - 글의 내용과 관련이 없습니다."),
                                opt("건강 관리", "🔵 파랑 - 글의 내용과 관련이 없습니다."),
                                opt("쇼핑 목록", "🔵 파랑 - 글의 내용과 관련이 없습니다.")
                        ), 0, "🔵 구체적 메뉴(김치, 된장찌개)에 집중시켜 공통된 상위 개념(한국 음식)을 놓치게 합니다.",
                                "[주제 마인드맵] 김치·된장찌개를 같은 색 동그라미로 묶으면 상위 개념(한국 음식)이 보입니다."))
        );

        List<PassageSeed> reading31to40 = List.of(
                onePassage(PassageCategory.READING, "핵심 정보 파악",
                        "저는 주말마다 등산을 갑니다. 산에 올라가면 마음이 편안해지기 때문입니다.",
                        q("이 사람이 등산을 가는 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("마음이 편안해져서", "정답: '마음이 편안해지기 때문'이라고 명시되어 있습니다."),
                                opt("운동이 되어서", "🟢 초록 - 글에 없는 이유를 임의로 추가한 오답입니다."),
                                opt("친구를 만나려고", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("경치가 아름다워서", "🟢 초록 - 글에 없는 이유입니다.")
                        ), 0, "🟢 등산이라는 활동에서 연상되는 일반적 이유(운동, 경치)를 답처럼 넣습니다.",
                                "[이유 마인드맵] 결과(편안해짐) ← 이유. 글에 직접 쓰인 이유만 정답 가지에 놓으세요.")),
                onePassage(PassageCategory.READING, "실용문 이해",
                        "문화센터 요가 강좌 안내\n요일: 매주 화, 목요일\n시간: 오전 10시 ~ 11시\n수강료: 5만 원(4주)",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("일주일에 두 번 수업이 있다.", "정답: 화요일과 목요일, 주 2회로 명시되어 있습니다."),
                                opt("수업은 오후에 진행된다.", "🔵 파랑 - '오전 10시'를 잘못 읽게 만든 오답입니다."),
                                opt("수강료는 10만 원이다.", "🔵 파랑 - '5만 원'을 잘못 읽게 만든 오답입니다."),
                                opt("월, 수요일에 수업이 있다.", "🔵 파랑 - '화, 목요일'을 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 안내문의 요일·시간·금액을 하나씩 바꿔서 그럴듯한 오답을 만듭니다.",
                                "[안내문 마인드맵] 요일·시간·수강료 세 항목을 각각 다른 색으로 밑줄 긋고 보기와 대조하세요.")),
                onePassage(PassageCategory.READING, "목적 추론",
                        "고객님, 주문하신 상품이 품절되어 배송이 어렵게 되었습니다. 죄송합니다. 환불 처리해 드리겠습니다.",
                        q("이 글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("배송 불가와 환불을 안내하려고", "정답: 품절로 인한 환불 처리 안내가 핵심입니다."),
                                opt("새 상품을 홍보하려고", "🟢 초록 - 글에 없는 목적입니다."),
                                opt("주문을 요청하려고", "🟢 초록 - 글의 내용과 반대됩니다."),
                                opt("배송지를 확인하려고", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 '주문'이라는 단어만 보고 반대 목적(주문 요청)으로 착각하게 합니다.",
                                "[목적 마인드맵] 핵심 문장 '환불 처리해 드리겠습니다'를 색칠하면 목적이 바로 드러납니다.")),
                multiQ(PassageCategory.READING, "안내문 복합 문제",
                        "실내 체육관 이용 안내\n이용 시간: 오전 6시 ~ 밤 10시\n이용료: 1회 3천 원(월 회원권 5만 원)\n※ 운동화 착용 필수, 음식물 반입 금지",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("음식물을 가지고 들어갈 수 없다.", "정답: '음식물 반입 금지'라고 명시되어 있습니다."),
                                opt("아무 신발이나 신어도 된다.", "🔵 파랑 - '운동화 착용 필수'와 반대됩니다."),
                                opt("이용 시간은 24시간이다.", "🔵 파랑 - '오전 6시~밤 10시'와 다릅니다."),
                                opt("1회 이용료는 5천 원이다.", "🔵 파랑 - '3천 원'을 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 안내문의 금지 항목과 허용 항목을 뒤바꿔서 헷갈리게 합니다.",
                                "[체크리스트 마인드맵] 필수(운동화) vs 금지(음식물)를 서로 다른 색으로 표시하세요."),
                        q("월 회원권을 이용할 때의 장점으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("여러 번 이용해도 추가 요금이 없다.", "정답: 5만 원으로 한 달 내내 이용 가능해 1회권보다 경제적입니다."),
                                opt("운동화 없이 이용할 수 있다.", "🟣 보라 - 안내문에 없는 혜택을 임의로 추가한 오답입니다."),
                                opt("음식물을 가져갈 수 있다.", "🟣 보라 - 금지 규정과 반대되는 오답입니다."),
                                opt("이용 시간이 더 길어진다.", "🟣 보라 - 안내문에 없는 내용입니다.")
                        ), 0, "🟣 회원권 혜택을 규정 위반(음식물 반입 등)과 섞어 헷갈리게 합니다.",
                                "[비교 마인드맵] 1회 3천 원 × 여러 번 vs 월 5만 원. 두 금액을 색으로 비교해보면 장점이 보입니다.")),
                onePassage(PassageCategory.READING, "문장 순서 배열",
                        "ㄱ. 그래서 병원에 갔습니다.\nㄴ. 어제부터 배가 아팠습니다.\nㄷ. 의사 선생님이 약을 처방해 주셨습니다.\nㄹ. 약을 먹고 나니 좀 나아졌습니다.",
                        q("다음을 순서대로 맞게 배열한 것을 고르십시오.", List.of(
                                opt("ㄴ → ㄱ → ㄷ → ㄹ", "정답: 증상 → 병원 방문 → 처방 → 호전의 순서와 일치합니다."),
                                opt("ㄴ → ㄷ → ㄱ → ㄹ", "🟣 보라 - 처방(ㄷ)이 병원 방문(ㄱ)보다 먼저 나와 순서가 어긋납니다."),
                                opt("ㄱ → ㄴ → ㄷ → ㄹ", "🟣 보라 - 병원 방문(ㄱ)이 증상(ㄴ)보다 먼저 나와 인과가 거꾸로입니다."),
                                opt("ㄹ → ㄴ → ㄱ → ㄷ", "🟣 보라 - 결과(ㄹ)가 맨 앞에 와서 인과관계가 거꾸로입니다.")
                        ), 0, "🟣 인과관계 화살표 순서를 무시하고 문장을 뒤섞어 배열하게 합니다.",
                                "[순서 마인드맵] 증상 → 병원 → 처방 → 호전. 네 단계에 번호를 매겨 색으로 연결해보세요.")),
                onePassage(PassageCategory.READING, "공통 주제 파악",
                        "ㄱ. 도서관에서 책을 읽었습니다.\nㄴ. 서점에서 새 책도 샀습니다.",
                        q("다음 두 문장의 공통된 주제를 고르십시오.", List.of(
                                opt("책/독서", "정답: 도서관과 서점 모두 책과 관련된 장소입니다."),
                                opt("공부 계획", "🔵 파랑 - 도서관이라는 단어만 보고 만든 오답입니다."),
                                opt("쇼핑 목록", "🔵 파랑 - 서점이라는 단어만 보고 만든 오답입니다."),
                                opt("여행 준비", "🔵 파랑 - 글의 내용과 관련이 없습니다.")
                        ), 0, "🔵 각 장소(도서관/서점)에 집중시켜 공통된 상위 개념(책)을 놓치게 합니다.",
                                "[주제 마인드맵] 도서관·서점을 같은 색 동그라미로 묶으면 공통 중심어(책)가 드러납니다.")),
                onePassage(PassageCategory.READING, "핵심 정보 파악",
                        "저는 회사에서 점심시간에 동료들과 함께 식사를 합니다. 그리고 잠깐 산책도 합니다.",
                        q("이 사람이 점심시간에 하는 일이 아닌 것을 고르십시오.", List.of(
                                opt("혼자 낮잠을 잔다.", "정답: 글에 언급되지 않은 행동입니다."),
                                opt("동료들과 식사를 한다.", "🟢 초록 - 글에 명시된 행동이라 '아닌 것'이 아닙니다."),
                                opt("산책을 한다.", "🟢 초록 - 글에 명시된 행동이라 '아닌 것'이 아닙니다."),
                                opt("점심시간을 보낸다.", "🟢 초록 - 전체 상황을 요약한 것이라 '아닌 것'이 아닙니다.")
                        ), 0, "🟢 '아닌 것을 고르십시오' 유형에서 실수로 '맞는 것'을 찾게 유도합니다.",
                                "[체크 마인드맵] 글에 있는 행동은 ✅, 없는 행동은 ❌로 표시. ❌가 정답입니다.")),
                onePassage(PassageCategory.READING, "실용문 이해",
                        "분리수거 안내\n종이류: 월, 수, 금 배출\n플라스틱류: 화, 목 배출\n일요일은 배출 금지",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("일요일에는 쓰레기를 버릴 수 없다.", "정답: '일요일은 배출 금지'라고 명시되어 있습니다."),
                                opt("종이류는 화요일에 버린다.", "🔵 파랑 - 종이류(월,수,금)와 플라스틱류(화,목)를 바꿔치기한 오답입니다."),
                                opt("플라스틱류는 월요일에 버린다.", "🔵 파랑 - 요일이 서로 바뀐 오답입니다."),
                                opt("토요일에도 배출 금지다.", "🔵 파랑 - 안내문에 없는 내용입니다.")
                        ), 0, "🔵 종이류와 플라스틱류의 요일을 서로 바꿔서 헷갈리게 합니다.",
                                "[분리수거 마인드맵] 종이류=월/수/금(초록), 플라스틱류=화/목(파랑)으로 색을 구분해 외우세요.")),
                onePassage(PassageCategory.READING, "목적 추론",
                        "이번 학기 한국어 수업 시간표가 변경되었습니다. 자세한 내용은 게시판을 확인해 주세요.",
                        q("이 글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("시간표 변경을 알리려고", "정답: 시간표 변경 사실과 확인 방법을 안내하는 글입니다."),
                                opt("수업을 취소하려고", "🟢 초록 - 글에 없는 내용을 임의로 추가한 오답입니다."),
                                opt("새 학생을 모집하려고", "🟢 초록 - 글의 목적과 관련이 없습니다."),
                                opt("게시판을 홍보하려고", "🟢 초록 - '게시판'이라는 단어만 보고 만든 오답입니다.")
                        ), 0, "🟢 확인 방법으로 언급된 단어(게시판)를 목적 자체로 착각하게 합니다.",
                                "[목적 마인드맵] 핵심 문장 '시간표가 변경되었습니다'를 색칠하면 목적이 바로 드러납니다."))
        );

        return new WeekSeed("1~2급 컬러맵 기초 다지기",
                "TOPIK I 수준의 듣기·읽기 기본기를 색깔 코딩과 마인드맵으로 시각화하며 다진다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(상황 응답, 행동/장소 파악, 세부 정보, 목적/주제, 흐름 추론) + 읽기 20(핵심 정보, 실용문, 목적 추론, 문장 순서, 공통 주제). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening1to10, listening11to20, reading21to30, reading31to40))
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
