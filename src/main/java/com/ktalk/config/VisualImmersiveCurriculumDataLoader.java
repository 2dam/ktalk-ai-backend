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
    private record PassageSeed(PassageCategory category, String subType, String passageText, String diagramSvg, List<ProblemSeed> problems) {}
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

    /** 위 헬퍼로 만든 지문에 인라인 SVG 마인드맵/색상 도표를 붙일 때 사용(시각적 몰입형 전용). */
    private static PassageSeed withDiagram(PassageSeed seed, String diagramSvg) {
        return new PassageSeed(seed.category(), seed.subType(), seed.passageText(), diagramSvg, seed.problems());
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

        List<WeekSeed> weeks = List.of(week1(), week2(), week3(), week4(), week5());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("🎨 TOPIK 커리큘럼(시각적 몰입형, 1~2급) WEEK1~4 완료(1,120문항) - WEEK5~8(문법·어휘 심화) 예정!");
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

    private static final String WEATHER_MINDMAP_SVG = """
            <svg viewBox="0 0 300 100" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <marker id="arrow1" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
                  <path d="M0,0 L8,4 L0,8 z" fill="#6b7280"/>
                </marker>
              </defs>
              <rect x="10" y="30" width="70" height="40" rx="8" fill="#fef3c7" stroke="#f59e0b" stroke-width="2"/>
              <text x="45" y="55" text-anchor="middle" font-size="14" fill="#92400e">날씨</text>
              <line x1="80" y1="50" x2="130" y2="50" stroke="#f59e0b" stroke-width="2" marker-end="url(#arrow1)"/>
              <rect x="130" y="30" width="70" height="40" rx="8" fill="#d1fae5" stroke="#10b981" stroke-width="2"/>
              <text x="165" y="55" text-anchor="middle" font-size="14" fill="#065f46">좋다</text>
              <line x1="200" y1="50" x2="250" y2="50" stroke="#10b981" stroke-width="2" marker-end="url(#arrow1)"/>
              <text x="270" y="35" text-anchor="middle" font-size="11" fill="#065f46">공감</text>
              <text x="270" y="65" text-anchor="middle" font-size="11" fill="#065f46">제안</text>
            </svg>
            """;

    private static final String COLOR_LEGEND_SVG = """
            <svg viewBox="0 0 300 110" xmlns="http://www.w3.org/2000/svg">
              <circle cx="20" cy="15" r="8" fill="#ef4444"/>
              <text x="35" y="19" font-size="12" fill="#374151">시간/장소 혼동</text>
              <circle cx="20" cy="40" r="8" fill="#22c55e"/>
              <text x="35" y="44" font-size="12" fill="#374151">의도 파악 실패</text>
              <circle cx="20" cy="65" r="8" fill="#3b82f6"/>
              <text x="35" y="69" font-size="12" fill="#374151">어휘 부족</text>
              <circle cx="20" cy="90" r="8" fill="#a855f7"/>
              <text x="35" y="94" font-size="12" fill="#374151">기타(부주의 등)</text>
            </svg>
            """;

    private WeekSeed week1() {
        List<PassageSeed> listening1to10 = List.of(
                withDiagram(onePassage(PassageCategory.LISTENING, "상황 응답",
                        "여자: 오늘 날씨가 정말 좋네요.\n남자: (        )",
                        q("이어질 남자의 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("네, 산책하기 좋은 날씨예요.", "정답: 날씨가 좋다는 말에 자연스럽게 동의하며 이어갑니다."),
                                opt("네, 저는 학생이에요.", "🔵 파랑 - 질문과 무관한 신분 설명입니다."),
                                opt("아니요, 여기가 아니에요.", "🔵 파랑 - 장소 질문에 대한 답이라 문맥과 안 맞습니다."),
                                opt("네, 안녕히 가세요.", "🔵 파랑 - 작별 인사라 대화 흐름과 안 맞습니다.")
                        ), 0, "🔴 마인드맵 중심어 [날씨]에서 곁가지로 안 뻗어나가는 보기들을 답처럼 보이게 배치합니다.",
                                "[날씨] ── 좋다 → 공감·제안 으로 가지를 뻗어보세요. 정답만 이 가지 위에 있습니다.")),
                        WEATHER_MINDMAP_SVG),
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
                withDiagram(onePassage(PassageCategory.READING, "핵심 정보 파악",
                        "저는 매일 아침 7시에 일어나서 운동을 합니다. 그리고 8시에 아침을 먹습니다.",
                        q("이 사람이 아침 7시에 하는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("운동하기", "정답: 7시에 일어나서 운동을 한다고 명시되어 있습니다."),
                                opt("아침 먹기", "🔴 빨강 - 8시 활동을 7시 활동으로 착각하게 합니다."),
                                opt("잠자기", "🔴 빨강 - 글의 내용과 반대됩니다."),
                                opt("출근하기", "🔴 빨강 - 글에 없는 내용입니다.")
                        ), 0, "🔴 시간표에서 시각(7시/8시)과 행동을 서로 바꿔 배치한 오답을 넣습니다.",
                                "[시간표 마인드맵] 7시 ── 운동, 8시 ── 아침. 시각마다 다른 색으로 행동을 이어보세요.")),
                        COLOR_LEGEND_SVG),
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

        List<PassageSeed> listening2nd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "직장 생활",
                        "여자: 이 보고서 오늘까지 제출해야 하는데 좀 도와주시겠어요?\n남자: 네, 제가 검토해 드릴게요.",
                        q("남자가 여자에게 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("보고서를 검토해 준다.", "정답: '제가 검토해 드릴게요'가 다음 행동의 직접적 근거입니다."),
                                opt("보고서를 새로 쓴다.", "🟢 초록 - '검토'와 '작성'을 혼동하게 만든 오답입니다."),
                                opt("회의를 연기한다.", "🟢 초록 - 대화에 없는 행동입니다."),
                                opt("퇴근을 한다.", "🟢 초록 - 대화 내용과 반대되는 행동입니다.")
                        ), 0, "🟢 '검토'와 '작성'처럼 비슷한 사무 동사를 섞어 헷갈리게 합니다.",
                                "[직장 마인드맵] 요청(도와주세요) → 화살표 → 응답 동사(검토할게요). 동사만 색칠해도 답이 보입니다.")),
                onePassage(PassageCategory.LISTENING, "가족 행사",
                        "남자: 다음 주 토요일이 할머니 생신인데 다 같이 모이기로 했어요.\n여자: 저도 갈게요. 몇 시에 모여요?",
                        q("두 사람이 이야기하는 주제로 알맞은 것을 고르십시오.", List.of(
                                opt("가족 모임 일정", "정답: 생신 모임에 대한 대화입니다."),
                                opt("여행 계획", "🟢 초록 - 글의 내용과 관련이 없습니다."),
                                opt("생일 선물 종류", "🟢 초록 - '생신'이라는 단어만 보고 만든 오답입니다."),
                                opt("음식 준비 방법", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 '생신'이라는 단어에서 선물·음식 같은 세부 사항을 상상하게 합니다.",
                                "[가족 마인드맵] 중심 = 모임 자체. 선물/음식은 아직 나오지 않은 가지이니 색칠하지 마세요.")),
                onePassage(PassageCategory.LISTENING, "교통/이동",
                        "여자: 공항까지 어떻게 가는 게 제일 빨라요?\n남자: 지금 시간에는 공항철도가 제일 빨라요. 버스는 막힐 수 있어요.",
                        q("공항까지 가는 가장 빠른 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("공항철도", "정답: '지금 시간에는 공항철도가 제일 빨라요'라고 명시되어 있습니다."),
                                opt("버스", "🔴 빨강 - 대화에 언급됐지만 '막힐 수 있다'는 단점이 있는 교통수단입니다."),
                                opt("택시", "🔴 빨강 - 대화에 없는 교통수단입니다."),
                                opt("도보", "🔴 빨강 - 대화에 없는 방법입니다.")
                        ), 0, "🔴 두 교통수단이 함께 나올 때 단점이 언급된 쪽을 정답처럼 착각하게 합니다.",
                                "[교통 마인드맵] 공항철도(빠름, 초록) vs 버스(막힘, 빨강). 색으로 장단점을 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "계절/자연",
                        "남자: 요즘 단풍이 정말 예쁘던데요. 주말에 산에 가 볼까요?\n여자: 좋아요, 가을 산은 정말 아름답잖아요.",
                        q("두 사람이 이야기하는 계절로 알맞은 것을 고르십시오.", List.of(
                                opt("가을", "정답: '단풍', '가을 산'이라는 표현이 직접적 근거입니다."),
                                opt("봄", "🔵 파랑 - 산이라는 단어에서 연상한 오답입니다."),
                                opt("여름", "🔵 파랑 - 대화 내용과 관련이 없습니다."),
                                opt("겨울", "🔵 파랑 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🔵 '산'이라는 장소 단어만 보고 계절 단서(단풍, 가을)를 놓치게 합니다.",
                                "[계절 마인드맵] 핵심 단서 '단풍·가을 산'을 초록색으로 밑줄 치면 계절이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "취미/여가",
                        "여자: 요즘 뭐 배우세요?\n남자: 사진 찍는 걸 배우고 있어요. 주말마다 출사도 다녀요.",
                        q("남자의 취미로 알맞은 것을 고르십시오.", List.of(
                                opt("사진 촬영", "정답: '사진 찍는 걸 배우고 있어요'가 직접적 근거입니다."),
                                opt("등산", "🔵 파랑 - '출사'라는 단어에서 잘못 연상한 오답입니다."),
                                opt("그림 그리기", "🔵 파랑 - 대화에 없는 취미입니다."),
                                opt("여행", "🔵 파랑 - '주말마다 다닌다'는 표현만 보고 만든 오답입니다.")
                        ), 0, "🔵 '출사'라는 낯선 어휘를 등산이나 여행으로 잘못 연결하게 합니다.",
                                "[취미 마인드맵] 중심 단어 '사진 찍는 걸 배워요'를 파란색으로 표시하고 뜻 모르는 단어(출사)는 옆에 적어두세요.")),
                onePassage(PassageCategory.LISTENING, "직장 생활",
                        "남자: 이번 회식은 몇 시에 시작해요?\n여자: 원래 7시였는데 6시 반으로 당겨졌어요.",
                        q("회식이 시작하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("6시 반", "정답: '당겨졌어요' 뒤에 나오는 시간이 최종 정보입니다."),
                                opt("7시", "🔴 빨강 - 원래 시간(변경 전)에 꽂힌 오답입니다."),
                                opt("6시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("7시 반", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 시간이 두 번 나올 때 먼저 들린 시간을 답처럼 착각하게 합니다.",
                                "[시간 마인드맵] 원래 시간 → 화살표(당김) → 바뀐 시간. 화살표 뒤 숫자만 최종 답입니다.")),
                onePassage(PassageCategory.LISTENING, "가족 행사",
                        "여자: 이번 명절에 고향에 내려가세요?\n남자: 네, 이번엔 기차표를 미리 예매해 뒀어요.",
                        q("남자가 명절에 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("기차를 타고 고향에 간다.", "정답: '기차표를 예매해 뒀어요'가 직접적 근거입니다."),
                                opt("비행기를 타고 간다.", "🔴 빨강 - 교통수단을 바꿔치기한 오답입니다."),
                                opt("고향에 안 간다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("직접 운전해서 간다.", "🔴 빨강 - 대화에 없는 방법입니다.")
                        ), 0, "🔴 교통수단 단어(기차)를 다른 교통수단(비행기)으로 슬쩍 바꾼 오답을 넣습니다.",
                                "[명절 마인드맵] 핵심 단어 '기차표'를 색칠하면 교통수단이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "교통/이동",
                        "남자: 이 버스가 시청 앞을 지나가나요?\n여자: 아니요, 이 버스는 안 가고 파란색 버스를 타셔야 해요.",
                        q("시청 앞을 지나가는 버스로 알맞은 것을 고르십시오.", List.of(
                                opt("파란색 버스", "정답: '파란색 버스를 타셔야 해요'가 직접적 근거입니다."),
                                opt("지금 이 버스", "🔴 빨강 - '아니요'라는 부정 답변을 놓치게 합니다."),
                                opt("빨간색 버스", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("초록색 버스", "🔴 빨강 - 대화에 없는 색입니다.")
                        ), 0, "🔴 부정 표현('아니요') 뒤에 나오는 진짜 정보를 놓치게 합니다.",
                                "[버스 마인드맵] 이 버스(❌) → 파란색 버스(✅). 부정/긍정을 다른 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "계절/자연",
                        "여자: 오늘 눈이 많이 온다고 하던데 우산 대신 뭘 챙겨야 할까요?\n남자: 장화하고 두꺼운 외투를 챙기세요.",
                        q("남자가 여자에게 챙기라고 한 것이 아닌 것을 고르십시오.", List.of(
                                opt("우산", "정답: 남자는 우산이 아니라 장화와 외투를 챙기라고 했습니다."),
                                opt("장화", "🟣 보라 - 남자가 언급한 물건이라 '아닌 것'이 아닙니다."),
                                opt("두꺼운 외투", "🟣 보라 - 남자가 언급한 물건이라 '아닌 것'이 아닙니다."),
                                opt("겨울 용품", "🟣 보라 - 장화·외투를 포괄하는 표현이라 '아닌 것'이 아닙니다.")
                        ), 0, "🟣 '아닌 것을 고르십시오' 유형에서 언급된 물건을 답으로 착각하게 합니다.",
                                "[체크 마인드맵] 언급된 물건은 ✅, 언급 안 된 물건(우산)은 ❌. ❌가 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "취미/여가",
                        "남자: 주말에 시간 있으면 같이 자전거 탈래요? 한강에서요.\n여자: 좋아요! 저도 요즘 자전거에 관심 많았어요.",
                        q("두 사람이 주말에 하려는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("한강에서 자전거 타기", "정답: 남자의 제안에 여자가 동의한 내용입니다."),
                                opt("한강에서 산책하기", "🟢 초록 - '자전거'를 '산책'으로 바꿔치기한 오답입니다."),
                                opt("집에서 쉬기", "🟢 초록 - 대화 내용과 반대됩니다."),
                                opt("영화 보러 가기", "🟢 초록 - 대화에 없는 활동입니다.")
                        ), 0, "🟢 활동 단어(자전거)를 비슷한 야외 활동(산책)으로 슬쩍 바꿔치기합니다.",
                                "[활동 마인드맵] 제안(자전거 탈래요?) → 동의(좋아요). 활동 단어를 색칠해 원문과 대조하세요."))
        );

        List<PassageSeed> listening2nd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "직장 생활",
                        "여자: 신입사원 환영회는 어디서 하기로 했어요?\n남자: 회사 근처 식당으로 예약해 놨어요.",
                        q("신입사원 환영회 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("회사 근처 식당", "정답: '회사 근처 식당으로 예약'이 직접적 근거입니다."),
                                opt("회사 회의실", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("신입사원 집", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("근처 카페", "🔴 빨강 - '식당'을 '카페'로 바꿔치기한 오답입니다.")
                        ), 0, "🔴 장소 단어(식당)를 비슷한 다른 장소(카페)로 슬쩍 바꾼 오답을 넣습니다.",
                                "[장소 마인드맵] 핵심 단어 '식당으로 예약'을 색칠하면 장소가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "가족 행사",
                        "남자: 조카 돌잔치가 다음 달 초에 있어요. 선물로 뭐가 좋을까요?\n여자: 요즘은 금반지보다 실용적인 옷이나 장난감을 많이 해요.",
                        q("여자가 추천한 돌잔치 선물로 알맞은 것을 고르십시오.", List.of(
                                opt("옷이나 장난감", "정답: '옷이나 장난감을 많이 해요'가 직접적 근거입니다."),
                                opt("금반지", "🔴 빨강 - 여자가 '보다'로 비교하며 언급만 한 것을 정답으로 착각하게 합니다."),
                                opt("현금", "🔴 빨강 - 대화에 없는 선물입니다."),
                                opt("책", "🔴 빨강 - 대화에 없는 선물입니다.")
                        ), 0, "🔴 비교 표현('A보다 B')에서 앞의 A(금반지)를 정답처럼 착각하게 합니다.",
                                "[비교 마인드맵] 금반지(과거 유행, 빨강) → 화살표 → 옷·장난감(요즘 추천, 초록). 화살표 뒤가 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "교통/이동",
                        "여자: 이 근처에 주차할 곳이 있을까요?\n남자: 건물 뒤쪽에 공영 주차장이 있어요. 거기 이용하세요.",
                        q("남자가 여자에게 추천한 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("건물 뒤쪽 공영 주차장", "정답: '거기 이용하세요'가 직접적 근거입니다."),
                                opt("건물 앞 도로", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("지하 주차장", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("길 건너 마트", "🔴 빨강 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🔴 위치 표현(뒤쪽)을 다른 위치(앞, 지하)로 슬쩍 바꾼 오답을 넣습니다.",
                                "[위치 마인드맵] 핵심 표현 '건물 뒤쪽+공영 주차장'을 색칠하면 위치가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "계절/자연",
                        "남자: 벌써 매미 소리가 들리네요. 여름이 왔나 봐요.\n여자: 맞아요, 에어컨을 미리 점검해야겠어요.",
                        q("여자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("에어컨을 점검한다.", "정답: '에어컨을 미리 점검해야겠어요'가 직접적 근거입니다."),
                                opt("난방기를 점검한다.", "🟢 초록 - 계절(여름)과 반대되는 가전제품입니다."),
                                opt("창문을 닫는다.", "🟢 초록 - 대화에 없는 행동입니다."),
                                opt("옷을 산다.", "🟢 초록 - 대화에 없는 행동입니다.")
                        ), 0, "🟢 계절과 반대되는 가전제품(난방기)을 답처럼 섞어 놓습니다.",
                                "[계절 마인드맵] 여름(초록) ── 에어컨, 겨울(파랑) ── 난방기. 계절과 가전을 색으로 짝지으세요.")),
                onePassage(PassageCategory.LISTENING, "취미/여가",
                        "여자: 이 소설 정말 재미있어요. 한번 읽어 보세요.\n남자: 오, 그래요? 저도 요즘 읽을 책을 찾고 있었어요.",
                        q("남자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("그 소설을 읽어 본다.", "정답: 여자의 추천에 남자가 관심을 보인 내용입니다."),
                                opt("소설을 쓴다.", "🟢 초록 - '읽다'와 '쓰다'를 혼동하게 만든 오답입니다."),
                                opt("도서관에서 일한다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("책을 판다.", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 '읽다'와 '쓰다'처럼 비슷한 책 관련 동사를 섞어 헷갈리게 합니다.",
                                "[취미 마인드맵] 추천(재미있어요) → 관심(찾고 있었어요) → 행동(읽는다). 동사를 색칠하며 흐름을 따라가세요.")),
                onePassage(PassageCategory.LISTENING, "직장 생활",
                        "남자: 이 서류 결재는 부장님한테 받아야 하나요?\n여자: 아니요, 이번 건은 과장님 선에서 처리하시면 돼요.",
                        q("서류 결재를 받아야 할 사람으로 알맞은 것을 고르십시오.", List.of(
                                opt("과장님", "정답: '과장님 선에서 처리하시면 돼요'가 직접적 근거입니다."),
                                opt("부장님", "🔴 빨강 - '아니요'라는 부정 답변을 놓치게 합니다."),
                                opt("사장님", "🔴 빨강 - 대화에 없는 직급입니다."),
                                opt("팀장님", "🔴 빨강 - 대화에 없는 직급입니다.")
                        ), 0, "🔴 부정 표현('아니요') 뒤에 나오는 진짜 정보를 놓치게 합니다.",
                                "[직급 마인드맵] 부장님(❌) → 과장님(✅). 부정/긍정을 다른 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "가족 행사",
                        "여자: 이번 결혼식 축의금은 얼마 정도가 적당할까요?\n남자: 저는 보통 5만 원 하는데, 친한 사이면 10만 원도 괜찮아요.",
                        q("두 사람이 아주 친한 사이일 때 적당한 축의금으로 알맞은 것을 고르십시오.", List.of(
                                opt("10만 원", "정답: '친한 사이면 10만 원도 괜찮아요'가 직접적 근거입니다."),
                                opt("5만 원", "🔴 빨강 - 보통의 경우(친하지 않을 때) 금액에 꽂힌 오답입니다."),
                                opt("15만 원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("3만 원", "🔴 빨강 - 대화에 없는 금액입니다.")
                        ), 0, "🔴 조건(친한 사이)을 무시하고 앞에 나온 일반 금액을 답처럼 착각하게 합니다.",
                                "[금액 마인드맵] 보통(5만 원, 파랑) vs 친한 사이(10만 원, 초록). 조건에 맞는 색만 정답으로 고르세요.")),
                onePassage(PassageCategory.LISTENING, "교통/이동",
                        "남자: 제주도까지 비행기로 얼마나 걸려요?\n여자: 한 시간 정도 걸려요. 배로 가면 훨씬 오래 걸리고요.",
                        q("제주도까지 비행기로 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("한 시간 정도", "정답: '한 시간 정도 걸려요'가 직접적 근거입니다."),
                                opt("세 시간 정도", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("배로 가는 시간", "🔴 빨강 - 비행기가 아닌 다른 교통수단의 시간을 착각하게 합니다."),
                                opt("30분 정도", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 두 교통수단의 소요 시간을 서로 바꿔서 헷갈리게 합니다.",
                                "[시간 마인드맵] 비행기(한 시간, 초록) vs 배(오래 걸림, 빨강). 교통수단과 시간을 색으로 짝지으세요.")),
                onePassage(PassageCategory.LISTENING, "계절/자연",
                        "여자: 이번 봄에는 벚꽃 축제에 꼭 가 보고 싶어요.\n남자: 저도요. 4월 초가 절정이라고 하더라고요.",
                        q("벚꽃이 절정인 시기로 알맞은 것을 고르십시오.", List.of(
                                opt("4월 초", "정답: '4월 초가 절정'이라고 명시되어 있습니다."),
                                opt("3월 초", "🔴 빨강 - 대화에 없는 시기입니다."),
                                opt("5월 초", "🔴 빨강 - 대화에 없는 시기입니다."),
                                opt("4월 말", "🔴 빨강 - '초'와 '말'을 바꿔치기한 오답입니다.")
                        ), 0, "🔴 '초'와 '말' 같은 세부 시점 단어를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[시기 마인드맵] 핵심 단어 '4월 초'를 색칠하면 시기가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "취미/여가",
                        "남자: 요즘 기타를 배우고 있는데 생각보다 어려워요.\n여자: 저도 처음엔 그랬어요. 3개월 정도 지나니까 좀 늘더라고요.",
                        q("여자가 기타 실력이 늘기 시작했다고 말한 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("3개월 정도 지난 후", "정답: '3개월 정도 지나니까 좀 늘더라고요'가 직접적 근거입니다."),
                                opt("처음 배울 때", "🔴 빨강 - '처음엔 그랬어요(어려웠다)'는 반대 시점입니다."),
                                opt("1개월 정도 지난 후", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("6개월 정도 지난 후", "🔴 빨강 - 대화에 없는 기간입니다.")
                        ), 0, "🔴 '처음'과 '3개월 후'라는 두 시점을 헷갈리게 배치합니다.",
                                "[시점 마인드맵] 처음(어려움, 빨강) → 화살표 → 3개월 후(늘었음, 초록). 화살표 뒤가 정답입니다."))
        );

        List<PassageSeed> reading2nd21to30 = List.of(
                onePassage(PassageCategory.READING, "공공장소 안내문",
                        "지하철 이용 안내\n임산부 배려석은 비워 두시기 바랍니다.\n에스컬레이터에서는 한 줄로 서 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("임산부 배려석은 비워 둬야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("에스컬레이터에서 두 줄로 서야 한다.", "🔵 파랑 - '한 줄로'를 잘못 읽게 만든 오답입니다."),
                                opt("배려석에 아무나 앉아도 된다.", "🔵 파랑 - 안내문 내용과 반대됩니다."),
                                opt("에스컬레이터 이용은 금지다.", "🔵 파랑 - 안내문에 없는 내용입니다.")
                        ), 0, "🔵 안내문의 핵심 지시(한 줄, 비워두기)를 반대로 바꾼 오답을 넣습니다.",
                                "[안내문 마인드맵] 배려석(초록: 비우기) / 에스컬레이터(파랑: 한 줄). 규칙마다 색을 다르게 표시하세요.")),
                onePassage(PassageCategory.READING, "일기/편지",
                        "오늘은 회사에서 힘든 일이 있었지만, 저녁에 가족들과 맛있는 밥을 먹으니 기분이 좋아졌다.",
                        q("글쓴이의 오늘 하루 기분 변화로 알맞은 것을 고르십시오.", List.of(
                                opt("힘들었다가 좋아짐", "정답: '힘든 일'에서 '기분이 좋아졌다'로 변화한 내용입니다."),
                                opt("좋았다가 힘들어짐", "🟣 보라 - 감정 변화의 순서를 거꾸로 만든 오답입니다."),
                                opt("하루 종일 힘들었다.", "🟣 보라 - 마지막 감정(좋아짐)을 반영하지 못한 오답입니다."),
                                opt("하루 종일 좋았다.", "🟣 보라 - 처음 감정(힘듦)을 반영하지 못한 오답입니다.")
                        ), 0, "🟣 감정 변화의 방향(힘듦→좋음)을 거꾸로 배치한 오답을 넣습니다.",
                                "[감정 마인드맵] 빨강(힘듦) → 화살표 → 초록(좋음). 화살표의 방향을 놓치지 마세요.")),
                onePassage(PassageCategory.READING, "여행/관광",
                        "경주는 신라의 옛 수도로, 불국사와 석굴암 같은 유적지가 유명합니다. 가을에는 단풍도 아름답습니다.",
                        q("경주에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("신라의 옛 수도였다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("백제의 옛 수도였다.", "🔵 파랑 - '신라'를 다른 나라로 바꾼 오답입니다."),
                                opt("불국사가 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("봄에 유명한 곳이다.", "🔵 파랑 - '가을'을 다른 계절로 바꾼 오답입니다.")
                        ), 0, "🔵 지명·계절 같은 고유 정보를 슬쩍 바꿔서 그럴듯한 오답을 만듭니다.",
                                "[여행 마인드맵] 경주 ── 신라(초록) ── 불국사·석굴암(파랑) ── 가을 단풍(주황). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "건강/운동",
                        "매일 30분씩 걷는 것만으로도 심장 건강에 큰 도움이 됩니다. 무리한 운동보다 꾸준함이 중요합니다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("꾸준한 걷기 운동이 건강에 좋다.", "정답: 글 전체를 요약하는 핵심 내용입니다."),
                                opt("무리한 운동이 건강에 좋다.", "🟢 초록 - 글의 내용과 반대됩니다."),
                                opt("운동은 30분 이상 하면 안 된다.", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("심장병은 걷기로 치료된다.", "🟢 초록 - 글에 없는 과장된 내용입니다.")
                        ), 0, "🟢 세부 단어(30분, 심장)를 과장하거나 반대로 바꿔 그럴듯한 오답을 만듭니다.",
                                "[건강 마인드맵] 중심 = 꾸준함. '무리한 운동보다'라는 비교 표현을 색칠해 반대 뜻을 놓치지 마세요.")),
                onePassage(PassageCategory.READING, "한국 문화/관습",
                        "한국에서는 어른과 함께 식사할 때 어른이 먼저 수저를 든 후에 식사를 시작하는 것이 예의입니다.",
                        q("한국의 식사 예절로 맞는 것을 고르십시오.", List.of(
                                opt("어른이 먼저 수저를 든 후 시작한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("아이가 먼저 수저를 들어야 한다.", "🔵 파랑 - 글의 내용과 반대입니다."),
                                opt("순서는 상관없다.", "🔵 파랑 - 글의 내용과 반대입니다."),
                                opt("음식을 먼저 다 차린 후 어른을 부른다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 순서(어른 먼저)를 반대로 바꾸거나 무의미하다고 왜곡한 오답을 넣습니다.",
                                "[예절 마인드맵] 어른(1번, 초록) → 아이(2번, 파랑). 순서에 번호를 매기면 헷갈리지 않습니다.")),
                onePassage(PassageCategory.READING, "공공장소 안내문",
                        "공원 이용 안내\n반려동물은 목줄을 착용해야 합니다.\n오후 10시 이후에는 출입이 제한됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("반려동물은 목줄을 해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("반려동물 출입은 전면 금지다.", "🔵 파랑 - 안내문에 없는 과장된 내용입니다."),
                                opt("24시간 출입 가능하다.", "🔵 파랑 - '오후 10시 이후 제한'과 반대됩니다."),
                                opt("목줄 없이도 괜찮다.", "🔵 파랑 - 안내문 내용과 반대됩니다.")
                        ), 0, "🔵 안내문의 조건부 규칙을 전면 금지/허용처럼 과장한 오답을 넣습니다.",
                                "[안내문 마인드맵] 반려동물(초록: 목줄 필수) / 시간(파랑: 밤 10시 이후 제한). 색으로 규칙을 나눠보세요.")),
                onePassage(PassageCategory.READING, "일기/편지",
                        "선생님께, 그동안 가르쳐 주셔서 정말 감사했습니다. 배운 것들을 잊지 않고 열심히 살겠습니다.",
                        q("이 편지를 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("감사 인사를 전하려고", "정답: '가르쳐 주셔서 정말 감사했습니다'가 핵심 목적입니다."),
                                opt("질문을 하려고", "🟢 초록 - 글의 내용과 관련이 없습니다."),
                                opt("불만을 표현하려고", "🟢 초록 - 글의 어조와 반대됩니다."),
                                opt("수업을 신청하려고", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 편지의 어조(감사)를 반대되는 목적(불만)으로 착각하게 합니다.",
                                "[편지 마인드맵] 핵심 문장 '정말 감사했습니다'를 색칠하면 목적이 바로 드러납니다.")),
                onePassage(PassageCategory.READING, "여행/관광",
                        "제주도는 화산 활동으로 만들어진 섬으로, 한라산과 성산일출봉 같은 자연 경관이 유명합니다.",
                        q("제주도에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("화산 활동으로 만들어졌다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("지진으로 만들어졌다.", "🔵 파랑 - '화산'을 다른 자연 현상으로 바꾼 오답입니다."),
                                opt("한라산이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("사람이 만든 인공 섬이다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 자연 현상 단어(화산)를 다른 현상(지진)으로 바꾼 그럴듯한 오답을 만듭니다.",
                                "[여행 마인드맵] 제주도 ── 화산(초록) ── 한라산·성산일출봉(파랑). 형성 원인과 명소를 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "건강/운동",
                        "물을 자주 마시는 것은 신진대사에 도움이 되지만, 한 번에 너무 많이 마시는 것은 오히려 좋지 않습니다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("물은 적당히 자주 마시는 것이 좋다.", "정답: 글 전체를 요약하는 핵심 내용입니다."),
                                opt("물은 많이 마실수록 좋다.", "🟢 초록 - '한 번에 너무 많이는 안 좋다'와 반대됩니다."),
                                opt("물을 마시면 안 된다.", "🟢 초록 - 글의 내용과 반대입니다."),
                                opt("신진대사와 물은 관련 없다.", "🟢 초록 - 글의 내용과 반대입니다.")
                        ), 0, "🟢 '자주'라는 단어만 보고 '많이'와 혼동해 정반대 결론을 내리게 합니다.",
                                "[건강 마인드맵] 자주(초록, 좋음) vs 한 번에 많이(빨강, 안 좋음). 두 표현을 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "한국 문화/관습",
                        "설날에는 어른들께 세배를 드리고 덕담을 듣습니다. 아이들은 세배 후 세뱃돈을 받기도 합니다.",
                        q("설날 풍습으로 맞는 것을 고르십시오.", List.of(
                                opt("아이들이 어른께 세배를 드린다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("어른들이 아이들께 세배를 드린다.", "🔵 파랑 - 세배하는 주체를 반대로 바꾼 오답입니다."),
                                opt("세뱃돈은 어른이 받는다.", "🔵 파랑 - 받는 주체를 반대로 바꾼 오답입니다."),
                                opt("설날에는 아무 풍습이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 세배를 드리는 주체와 받는 주체를 서로 바꾼 오답을 넣습니다.",
                                "[풍습 마인드맵] 아이(세배 드림, 초록) → 어른(덕담·세뱃돈 줌, 파랑). 화살표 방향을 색으로 표시하세요."))
        );

        List<PassageSeed> reading2nd31to40 = List.of(
                onePassage(PassageCategory.READING, "공공장소 안내문",
                        "도서관 열람실 이용 안내\n음식물 반입 금지\n휴대전화는 무음으로 설정해 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("음식물을 가지고 들어갈 수 없다.", "정답: '음식물 반입 금지'라고 명시되어 있습니다."),
                                opt("휴대전화 사용은 전면 금지다.", "🔵 파랑 - '무음 설정'과 다른 과장된 오답입니다."),
                                opt("음료수는 반입 가능하다.", "🔵 파랑 - 안내문에 없는 내용입니다."),
                                opt("휴대전화는 소리를 크게 해도 된다.", "🔵 파랑 - 안내문 내용과 반대됩니다.")
                        ), 0, "🔵 '무음'이라는 조건부 규칙을 '전면 금지'처럼 과장한 오답을 넣습니다.",
                                "[안내문 마인드맵] 음식물(초록: 금지) / 휴대전화(파랑: 무음). 규칙마다 색을 다르게 표시하세요.")),
                onePassage(PassageCategory.READING, "일기/편지",
                        "오랜만이야. 잘 지내고 있지? 다음 달에 한국에 갈 예정인데 그때 꼭 만나서 밥 한번 먹자.",
                        q("이 편지를 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("만남을 제안하려고", "정답: '만나서 밥 한번 먹자'가 핵심 목적입니다."),
                                opt("이사를 알리려고", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("사과를 하려고", "🟢 초록 - 글의 어조와 맞지 않습니다."),
                                opt("돈을 빌리려고", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 안부 인사 부분에 집중시켜 실제 목적(만남 제안)을 놓치게 합니다.",
                                "[편지 마인드맵] 핵심 문장 '꼭 만나서 밥 한번 먹자'를 색칠하면 목적이 바로 드러납니다.")),
                onePassage(PassageCategory.READING, "여행/관광",
                        "전주 한옥마을에서는 한복을 입고 거리를 걸으며 전통 가옥의 아름다움을 느낄 수 있습니다.",
                        q("전주 한옥마을에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("한복을 입고 거리를 걸을 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("한복 체험이 불가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("현대식 건물만 있다.", "🔵 파랑 - '전통 가옥'과 반대되는 오답입니다."),
                                opt("서울에 위치해 있다.", "🔵 파랑 - '전주'를 다른 지명으로 바꾼 오답입니다.")
                        ), 0, "🔵 지명이나 건물 양식 같은 핵심 정보를 반대로 바꾼 오답을 넣습니다.",
                                "[여행 마인드맵] 전주 ── 한옥마을(초록) ── 한복 체험(파랑). 지명과 특징을 색으로 구분해 정리하세요.")),
                multiQ(PassageCategory.READING, "건강 안내문 복합 문제",
                        "건강검진 안내\n대상: 만 20세 이상 전 국민\n검진 항목: 혈액검사, 시력·청력검사, 문진\n※ 검진 전날 저녁 9시 이후 금식 필수",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("검진 전날 밤 9시 이후 음식을 먹으면 안 된다.", "정답: '저녁 9시 이후 금식 필수'라고 명시되어 있습니다."),
                                opt("만 20세 미만도 검진 대상이다.", "🔵 파랑 - '만 20세 이상'과 반대됩니다."),
                                opt("혈액검사는 포함되지 않는다.", "🔵 파랑 - 안내문 내용과 반대됩니다."),
                                opt("금식은 필요 없다.", "🔵 파랑 - 안내문 내용과 반대됩니다.")
                        ), 0, "🔵 대상 연령과 금식 규정을 반대로 바꾼 오답을 넣습니다.",
                                "[검진 마인드맵] 대상(초록: 20세 이상) / 금식(파랑: 밤 9시 이후). 규칙마다 색을 다르게 표시하세요."),
                        q("이 안내문에서 검진 항목으로 언급되지 않은 것을 고르십시오.", List.of(
                                opt("치과 검진", "정답: 안내문에 언급되지 않은 항목입니다."),
                                opt("혈액검사", "🟣 보라 - 안내문에 언급된 항목이라 '아닌 것'이 아닙니다."),
                                opt("시력·청력검사", "🟣 보라 - 안내문에 언급된 항목이라 '아닌 것'이 아닙니다."),
                                opt("문진", "🟣 보라 - 안내문에 언급된 항목이라 '아닌 것'이 아닙니다.")
                        ), 0, "🟣 '아닌 것을 고르십시오' 유형에서 언급된 항목을 답으로 착각하게 합니다.",
                                "[체크 마인드맵] 언급된 항목은 ✅, 언급 안 된 항목(치과)은 ❌. ❌가 정답입니다.")),
                onePassage(PassageCategory.READING, "한국 문화/관습",
                        "한국에서는 신발을 신은 채로 집 안에 들어가지 않습니다. 현관에서 신발을 벗는 것이 기본 예절입니다.",
                        q("한국의 주거 문화로 맞는 것을 고르십시오.", List.of(
                                opt("집에 들어갈 때 신발을 벗는다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("신발을 신은 채로 들어가도 된다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("거실에서만 신발을 벗는다.", "🔵 파랑 - '현관에서'라는 정보와 다릅니다."),
                                opt("손님만 신발을 벗는다.", "🔵 파랑 - 글에 없는 조건을 추가한 오답입니다.")
                        ), 0, "🔵 규칙의 장소(현관)나 대상(누구나)을 슬쩍 바꾼 오답을 넣습니다.",
                                "[문화 마인드맵] 핵심 문장 '현관에서 신발을 벗는다'를 색칠하면 장소와 규칙이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "공공장소 안내문",
                        "영화관 이용 안내\n상영 중에는 휴대전화 사용을 자제해 주세요.\n음식물은 지정된 매점 판매 상품만 반입 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("매점에서 산 음식만 가지고 들어갈 수 있다.", "정답: '매점 판매 상품만 반입 가능'이 직접적 근거입니다."),
                                opt("모든 음식물 반입이 가능하다.", "🔵 파랑 - 안내문 내용과 반대됩니다."),
                                opt("휴대전화는 자유롭게 사용해도 된다.", "🔵 파랑 - 안내문 내용과 반대됩니다."),
                                opt("음식물 반입은 전면 금지다.", "🔵 파랑 - 조건부 허용을 전면 금지로 과장한 오답입니다.")
                        ), 0, "🔵 조건부 허용 규정을 전면 허용/금지로 과장한 오답을 넣습니다.",
                                "[영화관 마인드맵] 음식물(파랑: 매점 상품만) / 휴대전화(초록: 자제). 조건을 색으로 정확히 구분하세요.")),
                onePassage(PassageCategory.READING, "여행/관광",
                        "여수 밤바다는 화려한 야경으로 유명해 많은 관광객이 저녁 시간에 방문합니다.",
                        q("여수에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("밤바다 야경이 유명하다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("낮 경치만 유명하다.", "🔵 파랑 - '밤바다'와 반대되는 오답입니다."),
                                opt("내륙 도시라 바다가 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("관광객이 거의 없다.", "🔵 파랑 - '많은 관광객'과 반대되는 오답입니다.")
                        ), 0, "🔵 핵심 정보(밤바다, 관광객 많음)를 반대로 바꾼 오답을 넣습니다.",
                                "[여행 마인드맵] 여수 ── 밤바다(초록) ── 야경(파랑) ── 관광객 많음(주황). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "건강/운동",
                        "충분한 수면은 집중력과 기억력 향상에 도움을 줍니다. 하루 7~8시간 수면을 권장합니다.",
                        q("이 글에서 권장하는 하루 수면 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("7~8시간", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("4~5시간", "🔴 빨강 - 글에 없는 시간입니다."),
                                opt("10시간 이상", "🔴 빨강 - 글에 없는 시간입니다."),
                                opt("2~3시간", "🔴 빨강 - 글에 없는 시간입니다.")
                        ), 0, "🔴 글에 없는 다른 숫자들을 섞어 정확한 숫자를 놓치게 합니다.",
                                "[수면 마인드맵] 핵심 숫자 '7~8시간'을 빨간색으로 동그라미 치고 다른 보기와 비교하세요.")),
                onePassage(PassageCategory.READING, "한국 문화/관습",
                        "한국에서는 상대방에게 물건을 줄 때 두 손으로 주는 것이 예의 바른 행동으로 여겨집니다.",
                        q("한국의 예절로 맞는 것을 고르십시오.", List.of(
                                opt("물건을 줄 때 두 손으로 준다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("한 손으로 주는 것이 예의다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("물건을 던져서 전달한다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("어른에게만 두 손으로 준다.", "🔵 파랑 - 글에 없는 조건을 추가한 오답입니다.")
                        ), 0, "🔵 규칙의 방법(두 손)이나 대상(누구나)을 슬쩍 바꾼 오답을 넣습니다.",
                                "[예절 마인드맵] 핵심 문장 '두 손으로 주는 것이 예의'를 색칠하면 방법이 바로 보입니다."))
        );

        List<PassageSeed> listening3rd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "전화 대화",
                        "여자: 여보세요, 김민수 씨 계세요?\n남자: 죄송한데 지금 자리에 안 계세요. 메모 남겨 드릴까요?",
                        q("남자가 여자에게 하려는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("메모를 남겨주겠다고 제안한다.", "정답: '메모 남겨 드릴까요?'가 직접적 근거입니다."),
                                opt("전화를 끊는다.", "🟢 초록 - 대화 내용과 관련이 없습니다."),
                                opt("김민수 씨를 바꿔준다.", "🟢 초록 - '자리에 안 계세요'와 반대됩니다."),
                                opt("다시 전화하라고 한다.", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 부재중 상황에서 제안하는 대안 행동(메모)을 다른 행동으로 착각하게 합니다.",
                                "[전화 마인드맵] 부재중 → 대안 제시(메모). 화살표 뒤의 제안 문장을 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "약속/스케줄",
                        "남자: 우리 몇 시에 만날까요?\n여자: 3시에 만나요. 늦지 않게 와 주세요.",
                        q("두 사람이 만나기로 한 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("3시", "정답: '3시에 만나요'가 직접적 근거입니다."),
                                opt("2시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("4시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("3시 반", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 글에 없는 다른 시간을 섞어 정확한 숫자를 놓치게 합니다.",
                                "[약속 마인드맵] 핵심 숫자 '3시'를 빨간색으로 동그라미 치고 다른 보기와 비교하세요.")),
                onePassage(PassageCategory.LISTENING, "쇼핑/가격",
                        "여자: 이거 좀 깎아 주실 수 있어요?\n남자: 죄송한데 이건 정가라서 할인이 어려워요.",
                        q("남자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("할인이 안 된다.", "정답: '할인이 어려워요'가 직접적 근거입니다."),
                                opt("할인이 된다.", "🟢 초록 - 대화 내용과 반대됩니다."),
                                opt("가격을 다시 알려준다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("다른 물건을 추천한다.", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 완곡한 거절 표현('어려워요')을 승낙으로 착각하게 합니다.",
                                "[가격 마인드맵] 요청(깎아주세요) → 거절(어려워요). 완곡 표현도 거절로 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "음식 주문",
                        "남자: 주문하시겠어요?\n여자: 네, 저는 비빔밥 하나랑 냉면 하나 주세요.",
                        q("여자가 주문한 음식으로 알맞은 것을 고르십시오.", List.of(
                                opt("비빔밥과 냉면", "정답: '비빔밥 하나랑 냉면 하나'가 직접적 근거입니다."),
                                opt("비빔밥만", "🔴 빨강 - 두 메뉴 중 하나만 놓친 오답입니다."),
                                opt("냉면만", "🔴 빨강 - 두 메뉴 중 하나만 놓친 오답입니다."),
                                opt("김밥과 냉면", "🔴 빨강 - '비빔밥'을 다른 메뉴로 바꾼 오답입니다.")
                        ), 0, "🔴 메뉴가 두 개 나열될 때 하나만 듣고 놓치게 합니다.",
                                "[주문 마인드맵] 비빔밥 + 냉면. 두 메뉴 모두 다른 색으로 밑줄 쳐서 빠뜨리지 마세요.")),
                withDiagram(onePassage(PassageCategory.LISTENING, "길 묻기",
                        "여자: 실례합니다, 우체국이 어디에 있어요?\n남자: 이 길로 쭉 가시다가 사거리에서 왼쪽으로 도세요.",
                        q("우체국으로 가는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("직진하다가 사거리에서 왼쪽", "정답: '쭉 가시다가 사거리에서 왼쪽'이 직접적 근거입니다."),
                                opt("직진하다가 사거리에서 오른쪽", "🔴 빨강 - '왼쪽'을 '오른쪽'으로 바꾼 오답입니다."),
                                opt("처음부터 왼쪽으로", "🔴 빨강 - '쭉 가시다가'라는 정보를 놓치게 합니다."),
                                opt("사거리에서 직진", "🔴 빨강 - '왼쪽으로 도세요'와 반대됩니다.")
                        ), 0, "🔴 방향(왼쪽/오른쪽)을 슬쩍 바꾸거나 순서(직진 후 회전)를 무시하게 합니다.",
                                "[길찾기 마인드맵] 직진 → 사거리 → 왼쪽. 순서와 방향을 색으로 함께 표시하세요.")),
                        "<svg viewBox=\"0 0 300 100\" xmlns=\"http://www.w3.org/2000/svg\">"
                                + "<line x1=\"20\" y1=\"70\" x2=\"120\" y2=\"70\" stroke=\"#3b82f6\" stroke-width=\"3\"/>"
                                + "<text x=\"70\" y=\"90\" text-anchor=\"middle\" font-size=\"11\" fill=\"#1d4ed8\">직진</text>"
                                + "<circle cx=\"120\" cy=\"70\" r=\"6\" fill=\"#f59e0b\"/>"
                                + "<text x=\"120\" y=\"50\" text-anchor=\"middle\" font-size=\"11\" fill=\"#92400e\">사거리</text>"
                                + "<line x1=\"120\" y1=\"70\" x2=\"120\" y2=\"20\" stroke=\"#10b981\" stroke-width=\"3\" marker-end=\"url(#arrow2)\"/>"
                                + "<text x=\"90\" y=\"15\" text-anchor=\"middle\" font-size=\"11\" fill=\"#065f46\">왼쪽(우체국)</text>"
                                + "<defs><marker id=\"arrow2\" markerWidth=\"8\" markerHeight=\"8\" refX=\"4\" refY=\"6\" orient=\"auto\">"
                                + "<path d=\"M0,8 L4,0 L8,8 z\" fill=\"#10b981\"/></marker></defs>"
                                + "</svg>"),
                onePassage(PassageCategory.LISTENING, "전화 대화",
                        "남자: 여보세요, 거기 한국식당이죠? 예약하고 싶은데요.\n여자: 네, 몇 분이서 오실 예정이세요?",
                        q("여자가 남자에게 묻는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("예약 인원", "정답: '몇 분이서 오실 예정이세요?'가 직접적 근거입니다."),
                                opt("예약 시간", "🟢 초록 - 대화에 아직 나오지 않은 정보입니다."),
                                opt("메뉴 선택", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("결제 방법", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 예약 관련 세부 질문(시간/메뉴/결제)을 섞어 실제 질문 내용을 놓치게 합니다.",
                                "[전화 마인드맵] 예약 요청 → 인원 확인. 첫 질문이 무엇인지 색칠하며 순서를 따라가세요.")),
                onePassage(PassageCategory.LISTENING, "약속/스케줄",
                        "여자: 이번 모임 장소를 카페로 바꿨어요. 시간은 그대로예요.\n남자: 네, 알겠습니다.",
                        q("이 대화에서 바뀌지 않은 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("모임 시간", "정답: '시간은 그대로예요'가 직접적 근거입니다."),
                                opt("모임 장소", "🟣 보라 - 장소는 바뀌었으므로 '바뀌지 않은 것'이 아닙니다."),
                                opt("모임 날짜", "🟣 보라 - 대화에 언급되지 않아 알 수 없습니다."),
                                opt("모임 인원", "🟣 보라 - 대화에 언급되지 않아 알 수 없습니다.")
                        ), 0, "🟣 '바뀌지 않은 것'을 묻는 유형에서 바뀐 항목(장소)을 답으로 착각하게 합니다.",
                                "[변경 마인드맵] 장소(변경, 빨강) / 시간(그대로, 초록). 색으로 바뀐 것과 안 바뀐 것을 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "쇼핑/가격",
                        "남자: 이 신발 260 사이즈 있어요?\n여자: 죄송한데 그 사이즈는 품절이고 265는 있어요.",
                        q("여자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("260은 없고 265는 있다.", "정답: '260은 품절, 265는 있어요'가 직접적 근거입니다."),
                                opt("260과 265 모두 있다.", "🔴 빨강 - 260이 품절이라는 정보를 놓치게 합니다."),
                                opt("260과 265 모두 없다.", "🔴 빨강 - 265가 있다는 정보와 반대됩니다."),
                                opt("260은 있고 265는 없다.", "🔴 빨강 - 두 사이즈의 재고 여부를 서로 바꾼 오답입니다.")
                        ), 0, "🔴 두 사이즈의 재고 여부를 서로 바꿔서 헷갈리게 합니다.",
                                "[재고 마인드맵] 260(품절, 빨강) / 265(있음, 초록). 사이즈와 재고를 색으로 짝지으세요.")),
                onePassage(PassageCategory.LISTENING, "음식 주문",
                        "여자: 여기 이 메뉴 맵지 않게 해 주실 수 있어요?\n남자: 네, 맵지 않게 조리해 드릴게요.",
                        q("여자가 남자에게 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("맵지 않게 조리해 달라는 것", "정답: '맵지 않게 해 주실 수 있어요?'가 직접적 근거입니다."),
                                opt("맵게 조리해 달라는 것", "🔴 빨강 - 요청 내용과 반대입니다."),
                                opt("양을 늘려 달라는 것", "🔴 빨강 - 대화에 없는 내용입니다."),
                                opt("가격을 깎아 달라는 것", "🔴 빨강 - 대화에 없는 내용입니다.")
                        ), 0, "🔴 부정 표현('맵지 않게')을 놓치고 반대(맵게)로 착각하게 합니다.",
                                "[주문 마인드맵] 핵심 표현 '맵지 않게'에 형광펜을 치고 부정어를 놓치지 마세요.")),
                onePassage(PassageCategory.LISTENING, "길 묻기",
                        "남자: 여기서 지하철역까지 걸어서 얼마나 걸려요?\n여자: 한 5분 정도면 도착할 거예요.",
                        q("지하철역까지 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("약 5분", "정답: '한 5분 정도'가 직접적 근거입니다."),
                                opt("약 10분", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("약 15분", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("약 30분", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 글에 없는 다른 시간을 섞어 정확한 숫자를 놓치게 합니다.",
                                "[시간 마인드맵] 핵심 숫자 '5분'을 빨간색으로 동그라미 치고 다른 보기와 비교하세요."))
        );

        List<PassageSeed> listening3rd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "전화 대화",
                        "여자: 여보세요, 배송 문의 좀 드리려고요. 주문한 물건이 아직 안 왔어요.\n남자: 죄송합니다. 확인 후 다시 연락드릴게요.",
                        q("남자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("확인 후 다시 연락한다.", "정답: '확인 후 다시 연락드릴게요'가 직접적 근거입니다."),
                                opt("바로 환불해 준다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("새 상품을 보낸다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("주문을 취소한다.", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 배송 문제 상황에서 흔히 예상되는 다른 해결책(환불, 재발송)을 답처럼 넣습니다.",
                                "[문의 마인드맵] 문제 제기(안 왔어요) → 응답(확인+연락). 마지막 문장만 정답 가지입니다.")),
                onePassage(PassageCategory.LISTENING, "약속/스케줄",
                        "남자: 다음 모임은 언제로 할까요?\n여자: 2주 후 토요일 어때요? 다들 시간 괜찮을 것 같아요.",
                        q("여자가 제안한 모임 시기로 알맞은 것을 고르십시오.", List.of(
                                opt("2주 후 토요일", "정답: '2주 후 토요일 어때요?'가 직접적 근거입니다."),
                                opt("1주 후 토요일", "🔴 빨강 - 대화에 없는 시기입니다."),
                                opt("2주 후 일요일", "🔴 빨강 - '토요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("3주 후 토요일", "🔴 빨강 - 대화에 없는 시기입니다.")
                        ), 0, "🔴 기간과 요일 중 하나를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[일정 마인드맵] 핵심 표현 '2주 후+토요일'을 색칠하면 시기가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "쇼핑/가격",
                        "여자: 이 티셔츠 두 장 사면 할인되나요?\n남자: 네, 두 장 이상 사시면 10% 할인해 드려요.",
                        q("할인을 받을 수 있는 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("두 장 이상 구매", "정답: '두 장 이상 사시면'이 직접적 근거입니다."),
                                opt("세 장 이상 구매", "🔴 빨강 - '두 장'을 다른 수량으로 바꾼 오답입니다."),
                                opt("한 장만 구매", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("현금으로 구매", "🔴 빨강 - 대화에 없는 조건입니다.")
                        ), 0, "🔴 할인 조건의 수량(두 장)을 슬쩍 바꾼 오답을 넣습니다.",
                                "[할인 마인드맵] 조건(두 장 이상) → 결과(10% 할인). 조건 숫자를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "음식 주문",
                        "남자: 포장이세요, 아니면 여기서 드시고 가세요?\n여자: 포장해 주세요. 좀 급해서요.",
                        q("여자가 선택한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("포장", "정답: '포장해 주세요'가 직접적 근거입니다."),
                                opt("매장 식사", "🔴 빨강 - 대화 내용과 반대입니다."),
                                opt("배달", "🔴 빨강 - 대화에 없는 선택지입니다."),
                                opt("예약", "🔴 빨강 - 대화에 없는 내용입니다.")
                        ), 0, "🔴 두 선택지(포장/매장) 중 언급 안 된 것을 답처럼 헷갈리게 합니다.",
                                "[선택 마인드맵] 포장(✅) vs 매장 식사(❌). 선택된 쪽만 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "길 묻기",
                        "여자: 이 버스 정류장에서 시청까지 몇 정거장이에요?\n남자: 세 정거장만 가시면 돼요.",
                        q("시청까지 가는 정거장 수로 알맞은 것을 고르십시오.", List.of(
                                opt("세 정거장", "정답: '세 정거장만 가시면 돼요'가 직접적 근거입니다."),
                                opt("두 정거장", "🔴 빨강 - 대화에 없는 숫자입니다."),
                                opt("네 정거장", "🔴 빨강 - 대화에 없는 숫자입니다."),
                                opt("다섯 정거장", "🔴 빨강 - 대화에 없는 숫자입니다.")
                        ), 0, "🔴 글에 없는 다른 숫자를 섞어 정확한 숫자를 놓치게 합니다.",
                                "[정거장 마인드맵] 핵심 숫자 '세 정거장'을 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.LISTENING, "전화 대화",
                        "남자: 여보세요, 오늘 예약한 시간을 좀 미룰 수 있을까요?\n여자: 네, 몇 시로 바꿔 드릴까요?",
                        q("남자가 여자에게 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("예약 시간 변경", "정답: '예약한 시간을 좀 미룰 수 있을까요?'가 직접적 근거입니다."),
                                opt("예약 취소", "🟢 초록 - 대화 내용과 반대입니다."),
                                opt("새 예약", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("장소 변경", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 '변경'을 '취소'나 '새 예약'으로 착각하게 합니다.",
                                "[전화 마인드맵] 핵심 동사 '미루다(변경)'를 색칠하면 요청 내용이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "약속/스케줄",
                        "여자: 저녁 약속 시간을 7시에서 8시로 늦출 수 있을까요?\n남자: 네, 저는 괜찮아요.",
                        q("바뀐 저녁 약속 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("8시", "정답: '7시에서 8시로 늦출'의 최종 시간이 정답입니다."),
                                opt("7시", "🔴 빨강 - 변경 전 시간에 꽂힌 오답입니다."),
                                opt("9시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("6시", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 시간이 두 번 나올 때 먼저 들린 시간을 답처럼 착각하게 합니다.",
                                "[시간 마인드맵] 7시 → 화살표(변경) → 8시. 화살표 뒤 숫자만 최종 답입니다.")),
                onePassage(PassageCategory.LISTENING, "쇼핑/가격",
                        "남자: 이 컵 세트 얼마예요?\n여자: 원래 4만 원인데 흠집이 있어서 3만 원에 드릴게요.",
                        q("컵 세트의 실제 판매 가격으로 알맞은 것을 고르십시오.", List.of(
                                opt("3만 원", "정답: '흠집이 있어서 3만 원에 드릴게요'가 직접적 근거입니다."),
                                opt("4만 원", "🔴 빨강 - 원래 가격(정상가)에 꽂힌 오답입니다."),
                                opt("5만 원", "🔴 빨강 - 대화에 없는 가격입니다."),
                                opt("2만 원", "🔴 빨강 - 대화에 없는 가격입니다.")
                        ), 0, "🔴 두 가격이 연속으로 나올 때 앞의 숫자를 정답처럼 착각하게 합니다.",
                                "[가격 마인드맵] 원래 가격 → 화살표(흠집 할인) → 실제 가격. 화살표 뒤 숫자만 최종 답입니다.")),
                onePassage(PassageCategory.LISTENING, "음식 주문",
                        "여자: 음료는 어떤 걸로 하시겠어요?\n남자: 저는 아이스 아메리카노로 주세요.",
                        q("남자가 주문한 음료로 알맞은 것을 고르십시오.", List.of(
                                opt("아이스 아메리카노", "정답: '아이스 아메리카노로 주세요'가 직접적 근거입니다."),
                                opt("따뜻한 아메리카노", "🔴 빨강 - '아이스'를 '따뜻한'으로 바꾼 오답입니다."),
                                opt("아이스 라떼", "🔴 빨강 - '아메리카노'를 다른 음료로 바꾼 오답입니다."),
                                opt("따뜻한 라떼", "🔴 빨강 - 두 요소 모두 바뀐 오답입니다.")
                        ), 0, "🔴 온도(아이스/따뜻한)와 메뉴명을 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[주문 마인드맵] 온도(아이스, 파랑) + 메뉴(아메리카노, 초록). 두 요소를 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "길 묻기",
                        "남자: 이 근처에 화장실이 어디 있어요?\n여자: 이 건물 지하 1층에 있어요.",
                        q("화장실의 위치로 알맞은 것을 고르십시오.", List.of(
                                opt("이 건물 지하 1층", "정답: '이 건물 지하 1층에 있어요'가 직접적 근거입니다."),
                                opt("이 건물 1층", "🔴 빨강 - '지하 1층'을 '1층'으로 바꾼 오답입니다."),
                                opt("옆 건물 지하 1층", "🔴 빨강 - '이 건물'을 다른 건물로 바꾼 오답입니다."),
                                opt("이 건물 2층", "🔴 빨강 - 대화에 없는 위치입니다.")
                        ), 0, "🔴 위치 정보(건물, 층수)를 하나씩 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[위치 마인드맵] 핵심 표현 '이 건물+지하 1층'을 색칠하면 위치가 바로 보입니다."))
        );

        List<PassageSeed> reading3rd21to30 = List.of(
                onePassage(PassageCategory.READING, "가정통신문",
                        "가정통신문\n다음 주 화요일은 학부모 상담 주간입니다. 상담을 원하시면 담임 선생님께 미리 연락 주세요.",
                        q("이 가정통신문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("학부모 상담은 담임 선생님께 미리 연락해야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("상담 없이 방문하면 된다.", "🔵 파랑 - '미리 연락'과 반대됩니다."),
                                opt("상담 주간은 이번 주다.", "🔵 파랑 - '다음 주'를 잘못 읽게 만든 오답입니다."),
                                opt("상담은 학생과만 한다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 시점(다음 주)이나 절차(미리 연락)를 슬쩍 바꾼 오답을 넣습니다.",
                                "[가정통신문 마인드맵] 시점(초록: 다음 주 화요일) / 절차(파랑: 미리 연락). 색으로 항목을 나누세요.")),
                onePassage(PassageCategory.READING, "할인 쿠폰",
                        "○○카페 할인 쿠폰\n아메리카노 1잔 무료\n유효기간: 2026년 8월 31일까지\n1인 1매 사용 가능",
                        q("이 쿠폰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("아메리카노 한 잔을 무료로 받을 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("모든 음료가 무료다.", "🔵 파랑 - '아메리카노 1잔'만 해당되는데 과장한 오답입니다."),
                                opt("한 사람이 여러 장 쓸 수 있다.", "🔵 파랑 - '1인 1매'와 반대됩니다."),
                                opt("유효기간이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 조건(1잔, 1인 1매)을 과장하거나 무시한 오답을 넣습니다.",
                                "[쿠폰 마인드맵] 대상(아메리카노 1잔) / 조건(1인 1매) / 기한(8월 31일). 세 항목을 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "전단지",
                        "새로 오픈한 헬스장\n오픈 기념 3개월 회원권 반값 할인\n문의: 02-123-4567",
                        q("이 전단지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("3개월 회원권이 반값이다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("1개월 회원권이 반값이다.", "🔵 파랑 - '3개월'을 다른 기간으로 바꾼 오답입니다."),
                                opt("모든 회원권이 무료다.", "🔵 파랑 - '반값 할인'과 다른 과장된 오답입니다."),
                                opt("오래된 헬스장이다.", "🔵 파랑 - '새로 오픈한'과 반대됩니다.")
                        ), 0, "🔵 할인 대상 기간이나 개업 여부를 슬쩍 바꾼 오답을 넣습니다.",
                                "[전단지 마인드맵] 대상(3개월 회원권) / 혜택(반값). 두 정보를 색으로 짝지어 기억하세요.")),
                onePassage(PassageCategory.READING, "공지사항",
                        "엘리베이터 점검 공지\n내일 오전 9시~11시 엘리베이터 점검이 있습니다. 계단을 이용해 주세요.",
                        q("이 공지사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("내일 오전에 엘리베이터를 점검한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("오늘 오전에 점검한다.", "🔵 파랑 - '내일'을 '오늘'로 바꾼 오답입니다."),
                                opt("오후에 점검한다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("엘리베이터는 계속 이용 가능하다.", "🔵 파랑 - '계단을 이용해 주세요'와 반대됩니다.")
                        ), 0, "🔵 시점(내일/오늘, 오전/오후)을 슬쩍 바꾼 오답을 넣습니다.",
                                "[공지 마인드맵] 핵심 표현 '내일 오전 9~11시'를 색칠하면 시점이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "SMS/편지/SNS",
                        "[문자] 오늘 회식 장소가 바뀌었어요! 원래 식당 대신 2층 고깃집으로 오세요.",
                        q("이 문자의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("회식 장소가 고깃집으로 바뀌었다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("회식이 취소되었다.", "🔵 파랑 - 글에 없는 내용입니다."),
                                opt("장소는 그대로다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("1층 고깃집이다.", "🔵 파랑 - '2층'을 다른 층으로 바꾼 오답입니다.")
                        ), 0, "🔵 변경 여부나 세부 위치(층수)를 슬쩍 바꾼 오답을 넣습니다.",
                                "[문자 마인드맵] 원래 장소(빨강, 취소) → 새 장소(초록, 2층 고깃집). 화살표로 변경을 표시하세요.")),
                onePassage(PassageCategory.READING, "가정통신문",
                        "가정통신문\n이번 주 금요일은 현장체험학습으로 도시락을 준비해 주세요. 급식은 없습니다.",
                        q("이 가정통신문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("금요일에는 도시락을 준비해야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("금요일에 급식이 나온다.", "🔵 파랑 - '급식은 없습니다'와 반대됩니다."),
                                opt("월요일 행사다.", "🔵 파랑 - '금요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("도시락은 학교에서 준다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 요일이나 급식 제공 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[가정통신문 마인드맵] 요일(금요일) / 준비물(도시락) / 급식(없음). 색으로 세 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "할인 쿠폰",
                        "○○마트 생일 쿠폰\n생일 당일에만 사용 가능\n전 품목 15% 할인",
                        q("이 쿠폰의 사용 조건으로 맞는 것을 고르십시오.", List.of(
                                opt("생일 당일에만 쓸 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("생일 달 내내 쓸 수 있다.", "🔵 파랑 - '당일'을 '달 내내'로 과장한 오답입니다."),
                                opt("특정 품목만 할인된다.", "🔵 파랑 - '전 품목'과 반대됩니다."),
                                opt("할인율은 30%다.", "🔵 파랑 - '15%'를 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 사용 기간을 과장하거나 할인율 숫자를 슬쩍 바꾼 오답을 넣습니다.",
                                "[쿠폰 마인드맵] 조건(생일 당일) / 혜택(15% 할인). 숫자와 조건을 다른 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "전단지",
                        "이사 전문 업체\n포장부터 정리까지 한 번에\n견적 문의는 무료입니다",
                        q("이 전단지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("견적 문의는 무료다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("견적 문의도 유료다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("포장 서비스는 없다.", "🔵 파랑 - '포장부터 정리까지'와 반대됩니다."),
                                opt("정리 서비스는 없다.", "🔵 파랑 - '포장부터 정리까지'와 반대됩니다.")
                        ), 0, "🔵 서비스 범위나 요금 관련 정보를 반대로 바꾼 오답을 넣습니다.",
                                "[전단지 마인드맵] 서비스(포장+정리) / 요금(견적 무료). 색으로 두 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "공지사항",
                        "주차장 이용 공지\n다음 달부터 방문객 주차 요금이 시간당 2천 원으로 변경됩니다.",
                        q("이 공지사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("다음 달부터 주차 요금이 오른다.", "정답: 요금이 새로 시간당 2천 원으로 '변경'된다고 명시되어 있습니다."),
                                opt("이번 달부터 요금이 바뀐다.", "🔵 파랑 - '다음 달'을 '이번 달'로 바꾼 오답입니다."),
                                opt("주차 요금이 무료가 된다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("요금은 변화가 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 시행 시점(다음 달)이나 변경 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[공지 마인드맵] 핵심 표현 '다음 달부터+시간당 2천 원'을 색칠하면 내용이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "SMS/편지/SNS",
                        "[SNS 게시글] 오늘 드디어 자격증 시험에 합격했어요! 그동안 도와주신 분들께 감사드려요.",
                        q("이 게시글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("합격 소식을 알리고 감사를 전하려고", "정답: '합격했어요', '감사드려요'가 핵심 목적입니다."),
                                opt("시험 일정을 안내하려고", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("불합격을 알리려고", "🟢 초록 - 글의 내용과 반대됩니다."),
                                opt("도움을 요청하려고", "🟢 초록 - 글의 내용과 반대(이미 도움을 받음)입니다.")
                        ), 0, "🟢 '도와주신 분들' 표현만 보고 아직 도움이 필요하다고 착각하게 합니다.",
                                "[SNS 마인드맵] 핵심 문장 '합격했어요+감사드려요'를 색칠하면 목적이 바로 드러납니다."))
        );

        List<PassageSeed> reading3rd31to40 = List.of(
                onePassage(PassageCategory.READING, "가정통신문",
                        "가정통신문\n다음 달 초 예방접종이 진행됩니다. 참여를 원하시면 동의서를 작성해 제출해 주세요.",
                        q("이 가정통신문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("참여하려면 동의서를 제출해야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("동의서 없이 참여 가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("예방접종은 이번 달이다.", "🔵 파랑 - '다음 달'을 잘못 읽게 만든 오답입니다."),
                                opt("전원 의무 참여다.", "🔵 파랑 - '참여를 원하시면'과 다른 과장된 오답입니다.")
                        ), 0, "🔵 절차(동의서)나 시점(다음 달)을 슬쩍 바꾼 오답을 넣습니다.",
                                "[가정통신문 마인드맵] 시점(다음 달 초) / 절차(동의서 제출). 색으로 항목을 나누세요.")),
                onePassage(PassageCategory.READING, "할인 쿠폰",
                        "○○서점 적립 쿠폰\n5만 원 이상 구매 시 5천 원 적립\n적립금은 다음 구매 시 사용 가능",
                        q("이 쿠폰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("5만 원 이상 사면 5천 원이 적립된다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("1만 원 이상 사면 적립된다.", "🔵 파랑 - '5만 원'을 다른 금액으로 바꾼 오답입니다."),
                                opt("적립금은 즉시 현금으로 준다.", "🔵 파랑 - '다음 구매 시 사용'과 반대됩니다."),
                                opt("적립 조건이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 적립 조건 금액이나 사용 방법을 슬쩍 바꾼 오답을 넣습니다.",
                                "[적립 마인드맵] 조건(5만 원 이상) → 혜택(5천 원 적립) → 사용(다음 구매). 화살표로 순서를 표시하세요.")),
                onePassage(PassageCategory.READING, "전단지",
                        "동네 빵집 오픈 이벤트\n첫 방문 고객에게 쿠키 한 개 증정\n선착순 100명 한정",
                        q("이 전단지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("첫 방문 고객은 선착순으로 쿠키를 받는다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("모든 고객이 쿠키를 받는다.", "🔵 파랑 - '선착순 100명 한정'과 반대됩니다."),
                                opt("재방문 고객도 받을 수 있다.", "🔵 파랑 - '첫 방문 고객'과 반대됩니다."),
                                opt("빵을 무료로 준다.", "🔵 파랑 - '쿠키'를 '빵'으로 바꾼 오답입니다.")
                        ), 0, "🔵 증정 대상이나 수량 제한 조건을 슬쩍 바꾼 오답을 넣습니다.",
                                "[이벤트 마인드맵] 대상(첫 방문) / 한정(100명) / 혜택(쿠키). 세 항목을 색으로 구분하세요.")),
                multiQ(PassageCategory.READING, "공지사항 복합 문제",
                        "아파트 관리사무소 공지\n다음 주 월요일 오전 10시부터 오후 2시까지 단수 예정입니다.\n미리 물을 받아두시기 바랍니다.\n※ 문의: 관리사무소(02-000-0000)",
                        q("이 공지사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("월요일 오전부터 오후까지 물이 안 나온다.", "정답: '오전 10시부터 오후 2시까지 단수'가 직접적 근거입니다."),
                                opt("화요일에 단수된다.", "🔵 파랑 - '월요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("하루 종일 단수된다.", "🔵 파랑 - '10시~2시'라는 구체적 시간과 다릅니다."),
                                opt("단수 안내가 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 요일이나 시간 범위를 슬쩍 바꾸거나 과장한 오답을 넣습니다.",
                                "[공지 마인드맵] 요일(월요일) / 시간(10시~2시). 두 정보를 다른 색으로 표시하세요."),
                        q("이 공지사항을 보고 주민이 해야 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("미리 물을 받아둔다.", "정답: '미리 물을 받아두시기 바랍니다'가 직접적 근거입니다."),
                                opt("관리사무소에 항의한다.", "🟣 보라 - 글에 없는 행동입니다."),
                                opt("이사를 준비한다.", "🟣 보라 - 글에 없는 과장된 행동입니다."),
                                opt("아무것도 하지 않는다.", "🟣 보라 - 글의 권고와 반대됩니다.")
                        ), 0, "🟣 공지의 권고 사항을 무시하거나 과장된 행동으로 착각하게 합니다.",
                                "[행동 마인드맵] 공지(단수 예정) → 권고(물 받아두기). 화살표 뒤 문장이 할 일입니다.")),
                onePassage(PassageCategory.READING, "SMS/편지/SNS",
                        "[문자] 내일 비가 많이 온다고 하니 우산 꼭 챙기시고, 학교 앞 도로 공사로 등굣길이 막힐 수 있으니 일찍 출발하세요.",
                        q("이 문자에서 안내한 내용이 아닌 것을 고르십시오.", List.of(
                                opt("학교 휴교", "정답: 문자에 언급되지 않은 내용입니다."),
                                opt("우산 챙기기", "🟣 보라 - 문자에 언급된 내용이라 '아닌 것'이 아닙니다."),
                                opt("도로 공사로 인한 정체", "🟣 보라 - 문자에 언급된 내용이라 '아닌 것'이 아닙니다."),
                                opt("일찍 출발하기", "🟣 보라 - 문자에 언급된 내용이라 '아닌 것'이 아닙니다.")
                        ), 0, "🟣 '아닌 것을 고르십시오' 유형에서 언급된 내용을 답으로 착각하게 합니다.",
                                "[체크 마인드맵] 언급된 내용은 ✅, 언급 안 된 내용(휴교)은 ❌. ❌가 정답입니다.")),
                onePassage(PassageCategory.READING, "가정통신문",
                        "가정통신문\n2학기 교과서 대금 납부 기한이 이번 달 말일까지입니다. 기한 내 미납 시 별도 안내드립니다.",
                        q("이 가정통신문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("교과서 대금 납부 기한은 이번 달 말일이다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("납부 기한은 다음 달 말일이다.", "🔵 파랑 - '이번 달'을 다른 달로 바꾼 오답입니다."),
                                opt("납부하지 않아도 된다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("교과서는 무료다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 납부 기한 시점을 슬쩍 바꾼 오답을 넣습니다.",
                                "[가정통신문 마인드맵] 핵심 표현 '이번 달 말일까지'를 색칠하면 기한이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "할인 쿠폰",
                        "○○영화관 조조할인 쿠폰\n오전 9시 이전 상영 영화에 한해 30% 할인\n주말 포함 매일 사용 가능",
                        q("이 쿠폰의 사용 조건으로 맞는 것을 고르십시오.", List.of(
                                opt("오전 9시 이전 상영작에만 쓸 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("오후 상영작에도 쓸 수 있다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("평일에만 쓸 수 있다.", "🔵 파랑 - '주말 포함 매일'과 반대됩니다."),
                                opt("할인율은 50%다.", "🔵 파랑 - '30%'를 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 사용 가능 시간대나 요일, 할인율 숫자를 슬쩍 바꾼 오답을 넣습니다.",
                                "[쿠폰 마인드맵] 조건(오전 9시 이전) / 혜택(30% 할인) / 요일(매일). 색으로 세 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "전단지",
                        "중고 서점 오픈\n책을 팔면 현금으로 바로 매입\n판매도 구매도 가능",
                        q("이 전단지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("책을 팔면 바로 현금을 받을 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("책은 팔 수만 있다.", "🔵 파랑 - '판매도 구매도 가능'과 반대됩니다."),
                                opt("현금이 아닌 포인트로 준다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("매입은 며칠 걸린다.", "🔵 파랑 - '바로 매입'과 반대됩니다.")
                        ), 0, "🔵 지급 방식(현금/포인트)이나 소요 시간을 반대로 바꾼 오답을 넣습니다.",
                                "[전단지 마인드맵] 핵심 표현 '현금으로 바로 매입'을 색칠하면 조건이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "공지사항",
                        "헬스장 휴관 공지\n이번 주 일요일은 시설 점검으로 휴관합니다. 양해 부탁드립니다.",
                        q("이 공지사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("일요일에는 헬스장을 이용할 수 없다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("토요일에 휴관한다.", "🔵 파랑 - '일요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("이번 주는 정상 운영한다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("영구 휴관이다.", "🔵 파랑 - '이번 주 일요일'과 다른 과장된 오답입니다.")
                        ), 0, "🔵 휴관 요일이나 기간(일시적/영구)을 슬쩍 바꾼 오답을 넣습니다.",
                                "[공지 마인드맵] 핵심 표현 '이번 주 일요일+휴관'을 색칠하면 내용이 바로 보입니다."))
        );

        List<PassageSeed> listening4th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "초대/약속 변경",
                        "여자: 이번 주말 제 생일 파티에 오실 수 있어요?\n남자: 네, 몇 시까지 가면 돼요?",
                        q("남자가 여자에게 묻는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("파티 시작 시간", "정답: '몇 시까지 가면 돼요?'가 직접적 근거입니다."),
                                opt("파티 장소", "🟢 초록 - 대화에 없는 질문입니다."),
                                opt("선물 종류", "🟢 초록 - 대화에 없는 질문입니다."),
                                opt("참석 인원", "🟢 초록 - 대화에 없는 질문입니다.")
                        ), 0, "🟢 초대 상황에서 흔히 궁금할 만한 다른 질문(장소, 선물)을 답처럼 넣습니다.",
                                "[초대 마인드맵] 수락(갈 수 있어요) → 질문(몇 시까지). 마지막 질문 문장만 정답 가지입니다.")),
                onePassage(PassageCategory.LISTENING, "물건 찾기/분실",
                        "남자: 혹시 여기서 파란색 지갑 못 보셨어요?\n여자: 아, 그거 저기 안내 데스크에 맡겨져 있어요.",
                        q("파란색 지갑이 있는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("안내 데스크", "정답: '안내 데스크에 맡겨져 있어요'가 직접적 근거입니다."),
                                opt("분실물 창고", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("경찰서", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("남자의 가방", "🔴 빨강 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🔴 비슷한 성격의 다른 장소(분실물 창고, 경찰서)로 슬쩍 바꾼 오답을 넣습니다.",
                                "[분실물 마인드맵] 핵심 표현 '안내 데스크에 맡겨져 있어요'를 색칠하면 위치가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "병원/약국",
                        "여자: 어디가 아파서 오셨어요?\n남자: 어제부터 목이 아프고 기침이 나요.",
                        q("남자의 증상으로 알맞은 것을 고르십시오.", List.of(
                                opt("목이 아프고 기침이 난다.", "정답: '목이 아프고 기침이 나요'가 직접적 근거입니다."),
                                opt("배가 아프다.", "🔴 빨강 - 대화에 없는 증상입니다."),
                                opt("머리가 아프다.", "🔴 빨강 - 대화에 없는 증상입니다."),
                                opt("열이 난다.", "🔴 빨강 - 대화에 없는 증상입니다.")
                        ), 0, "🔴 언급되지 않은 다른 증상을 답처럼 섞어 놓습니다.",
                                "[증상 마인드맵] 목 아픔 + 기침. 두 증상 모두 다른 색으로 밑줄 쳐서 빠뜨리지 마세요.")),
                onePassage(PassageCategory.LISTENING, "학교생활",
                        "남자: 다음 주 시험 범위가 어디까지예요?\n여자: 1과부터 5과까지예요.",
                        q("시험 범위로 알맞은 것을 고르십시오.", List.of(
                                opt("1과부터 5과까지", "정답: '1과부터 5과까지예요'가 직접적 근거입니다."),
                                opt("1과부터 3과까지", "🔴 빨강 - 대화에 없는 범위입니다."),
                                opt("3과부터 5과까지", "🔴 빨강 - 시작 지점을 슬쩍 바꾼 오답입니다."),
                                opt("1과부터 10과까지", "🔴 빨강 - 대화에 없는 범위입니다.")
                        ), 0, "🔴 범위의 시작/끝 숫자를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[시험 마인드맵] 시작(1과) → 끝(5과). 두 숫자 모두 색칠해 범위를 확인하세요.")),
                withDiagram(onePassage(PassageCategory.LISTENING, "대중교통 이용",
                        "여자: 이 지하철 몇 호선이에요?\n남자: 2호선이에요. 3호선으로 갈아타려면 다음 역에서 내리세요.",
                        q("3호선으로 갈아타는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 역에서 내려서 환승한다.", "정답: '다음 역에서 내리세요'가 직접적 근거입니다."),
                                opt("이번 역에서 바로 환승한다.", "🔴 빨강 - '다음 역'을 '이번 역'으로 바꾼 오답입니다."),
                                opt("종점까지 가서 환승한다.", "🔴 빨강 - 대화에 없는 내용입니다."),
                                opt("2호선을 계속 탄다.", "🔴 빨강 - 대화 내용과 반대됩니다.")
                        ), 0, "🔴 환승 시점(다음 역/이번 역)을 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[환승 마인드맵] 2호선(현재, 파랑) → 다음 역 → 3호선(환승, 초록). 화살표로 환승 순서를 표시하세요.")),
                        "<svg viewBox=\"0 0 300 90\" xmlns=\"http://www.w3.org/2000/svg\">"
                                + "<circle cx=\"40\" cy=\"45\" r=\"22\" fill=\"#dbeafe\" stroke=\"#3b82f6\" stroke-width=\"2\"/>"
                                + "<text x=\"40\" y=\"49\" text-anchor=\"middle\" font-size=\"12\" fill=\"#1d4ed8\">2호선</text>"
                                + "<line x1=\"65\" y1=\"45\" x2=\"160\" y2=\"45\" stroke=\"#6b7280\" stroke-width=\"2\" marker-end=\"url(#arrow3)\"/>"
                                + "<text x=\"110\" y=\"35\" text-anchor=\"middle\" font-size=\"11\" fill=\"#374151\">다음 역</text>"
                                + "<circle cx=\"185\" cy=\"45\" r=\"22\" fill=\"#d1fae5\" stroke=\"#10b981\" stroke-width=\"2\"/>"
                                + "<text x=\"185\" y=\"49\" text-anchor=\"middle\" font-size=\"12\" fill=\"#065f46\">3호선</text>"
                                + "<defs><marker id=\"arrow3\" markerWidth=\"8\" markerHeight=\"8\" refX=\"6\" refY=\"4\" orient=\"auto\">"
                                + "<path d=\"M0,0 L8,4 L0,8 z\" fill=\"#6b7280\"/></marker></defs>"
                                + "</svg>"),
                onePassage(PassageCategory.LISTENING, "초대/약속 변경",
                        "남자: 내일 회식 참석하실 거예요?\n여자: 죄송한데 갑자기 일이 생겨서 못 갈 것 같아요.",
                        q("여자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("회식에 참석하지 못한다.", "정답: '못 갈 것 같아요'가 직접적 근거입니다."),
                                opt("회식에 참석한다.", "🟢 초록 - 대화 내용과 반대됩니다."),
                                opt("회식 장소를 바꾸고 싶어한다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("회식 시간을 늦추고 싶어한다.", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 완곡한 거절 표현('못 갈 것 같아요')을 참석 의사로 착각하게 합니다.",
                                "[약속 마인드맵] 요청(참석하실 거예요?) → 거절(못 갈 것 같아요). 부정 표현을 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "물건 찾기/분실",
                        "여자: 제 우산을 여기 두고 간 것 같은데 혹시 보셨어요?\n남자: 검은색 우산이면 제가 보관하고 있어요.",
                        q("남자가 보관하고 있는 우산의 색으로 알맞은 것을 고르십시오.", List.of(
                                opt("검은색", "정답: '검은색 우산이면 제가 보관하고 있어요'가 직접적 근거입니다."),
                                opt("파란색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("빨간색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("노란색", "🔴 빨강 - 대화에 없는 색입니다.")
                        ), 0, "🔴 글에 없는 다른 색을 섞어 정확한 색을 놓치게 합니다.",
                                "[분실물 마인드맵] 핵심 단어 '검은색'을 색칠하면 답이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "병원/약국",
                        "남자: 이 약은 하루에 몇 번 먹어야 해요?\n여자: 하루 세 번, 식사 후에 드세요.",
                        q("약을 먹는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("하루 세 번 식사 후", "정답: '하루 세 번, 식사 후에 드세요'가 직접적 근거입니다."),
                                opt("하루 두 번 식사 전", "🔴 빨강 - 횟수와 시점을 모두 바꾼 오답입니다."),
                                opt("하루 한 번 자기 전", "🔴 빨강 - 대화에 없는 내용입니다."),
                                opt("하루 세 번 식사 전", "🔴 빨강 - '식사 후'를 '식사 전'으로 바꾼 오답입니다.")
                        ), 0, "🔴 횟수는 맞지만 시점(식사 전/후)을 슬쩍 바꾼 오답을 넣어 헷갈리게 합니다.",
                                "[복약 마인드맵] 횟수(세 번, 파랑) + 시점(식사 후, 초록). 두 요소를 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "학교생활",
                        "여자: 발표 순서가 어떻게 돼요?\n남자: 저희 조는 세 번째예요. 두 조 끝나면 바로 시작해요.",
                        q("남자의 조가 발표하는 순서로 알맞은 것을 고르십시오.", List.of(
                                opt("세 번째", "정답: '저희 조는 세 번째예요'가 직접적 근거입니다."),
                                opt("첫 번째", "🔴 빨강 - 대화에 없는 순서입니다."),
                                opt("두 번째", "🔴 빨강 - 대화에 없는 순서입니다."),
                                opt("네 번째", "🔴 빨강 - 대화에 없는 순서입니다.")
                        ), 0, "🔴 글에 없는 다른 순서를 섞어 정확한 순서를 놓치게 합니다.",
                                "[발표 마인드맵] 핵심 숫자 '세 번째'를 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.LISTENING, "대중교통 이용",
                        "남자: 이 표는 당일만 쓸 수 있는 거예요?\n여자: 아니요, 일주일 동안 쓰실 수 있어요.",
                        q("표의 사용 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("일주일", "정답: '일주일 동안 쓰실 수 있어요'가 직접적 근거입니다."),
                                opt("당일만", "🔴 빨강 - '아니요'라는 부정 답변을 놓치게 합니다."),
                                opt("한 달", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("하루", "🔴 빨강 - 대화에 없는 기간입니다.")
                        ), 0, "🔴 부정 표현('아니요') 뒤에 나오는 진짜 정보를 놓치게 합니다.",
                                "[승차권 마인드맵] 당일만(❌) → 일주일(✅). 부정/긍정을 다른 색으로 표시하세요."))
        );

        List<PassageSeed> listening4th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "초대/약속 변경",
                        "여자: 오늘 모임 장소를 학교 앞 카페로 정했어요.\n남자: 네, 몇 시에 만날지도 알려주세요.",
                        q("남자가 여자에게 추가로 요청한 정보로 알맞은 것을 고르십시오.", List.of(
                                opt("모임 시간", "정답: '몇 시에 만날지도 알려주세요'가 직접적 근거입니다."),
                                opt("모임 장소", "🟢 초록 - 이미 언급된 정보라 추가 요청이 아닙니다."),
                                opt("참석 인원", "🟢 초록 - 대화에 없는 요청입니다."),
                                opt("모임 목적", "🟢 초록 - 대화에 없는 요청입니다.")
                        ), 0, "🟢 이미 언급된 정보(장소)를 추가 요청 내용으로 착각하게 합니다.",
                                "[모임 마인드맵] 이미 앎(장소, 초록) vs 추가 요청(시간, 빨강). 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "물건 찾기/분실",
                        "남자: 핸드폰을 택시에 두고 내린 것 같아요.\n여자: 택시 회사에 전화해서 차량 번호를 알려주세요.",
                        q("여자가 남자에게 제안한 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("택시 회사에 전화하기", "정답: '택시 회사에 전화해서'가 직접적 근거입니다."),
                                opt("경찰서에 신고하기", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("새 핸드폰 사기", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("친구에게 연락하기", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 분실 상황에서 흔히 예상되는 다른 대처(경찰 신고)를 답처럼 넣습니다.",
                                "[분실물 마인드맵] 문제(핸드폰 분실) → 해결책(택시 회사 전화). 마지막 문장만 정답 가지입니다.")),
                onePassage(PassageCategory.LISTENING, "병원/약국",
                        "여자: 이 약을 먹으면 졸릴 수 있어요. 운전은 피해 주세요.\n남자: 네, 알겠습니다.",
                        q("여자가 남자에게 주의를 준 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("약 복용 후 운전을 피할 것", "정답: '운전은 피해 주세요'가 직접적 근거입니다."),
                                opt("약을 물 없이 먹을 것", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("공복에 먹을 것", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("하루 종일 잠을 잘 것", "🟢 초록 - '졸릴 수 있다'는 부작용을 다른 지시로 착각하게 합니다.")
                        ), 0, "🟢 부작용 설명('졸릴 수 있다')을 지시사항으로 착각하게 합니다.",
                                "[복약주의 마인드맵] 부작용(졸림) → 주의사항(운전 피하기). 화살표로 인과관계를 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "학교생활",
                        "남자: 동아리 신청은 어디서 해요?\n여자: 학생회관 2층 게시판 옆에서 신청서를 받을 수 있어요.",
                        q("동아리 신청서를 받을 수 있는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("학생회관 2층 게시판 옆", "정답: '학생회관 2층 게시판 옆'이 직접적 근거입니다."),
                                opt("학생회관 1층 안내데스크", "🔴 빨강 - 층수를 슬쩍 바꾼 오답입니다."),
                                opt("도서관 2층", "🔴 빨강 - 건물을 다른 곳으로 바꾼 오답입니다."),
                                opt("교무실", "🔴 빨강 - 대화에 없는 장소입니다.")
                        ), 0, "🔴 위치 정보(건물, 층수)를 하나씩 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[동아리 마인드맵] 핵심 표현 '학생회관 2층+게시판 옆'을 색칠하면 위치가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "대중교통 이용",
                        "여자: 이 버스가 막차예요?\n남자: 아니요, 막차는 30분 후에 한 대 더 있어요.",
                        q("막차가 오는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("30분 후", "정답: '막차는 30분 후에 한 대 더 있어요'가 직접적 근거입니다."),
                                opt("지금 이 버스", "🔴 빨강 - '아니요'라는 부정 답변을 놓치게 합니다."),
                                opt("1시간 후", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("10분 후", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 부정 표현('아니요') 뒤에 나오는 진짜 정보를 놓치게 합니다.",
                                "[막차 마인드맵] 이 버스(❌ 막차 아님) → 30분 후(✅ 막차). 부정/긍정을 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "초대/약속 변경",
                        "남자: 이번 송년회는 12월 20일 저녁 6시예요. 참석 여부 알려주세요.\n여자: 네, 꼭 참석할게요.",
                        q("송년회 날짜와 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("12월 20일 저녁 6시", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("12월 20일 저녁 7시", "🔴 빨강 - 시간을 슬쩍 바꾼 오답입니다."),
                                opt("12월 25일 저녁 6시", "🔴 빨강 - 날짜를 슬쩍 바꾼 오답입니다."),
                                opt("11월 20일 저녁 6시", "🔴 빨강 - 월을 슬쩍 바꾼 오답입니다.")
                        ), 0, "🔴 날짜와 시간 중 하나를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[일정 마인드맵] 핵심 표현 '12월 20일+저녁 6시'를 색칠하면 정보가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "물건 찾기/분실",
                        "여자: 지하철에서 책을 놓고 내렸어요. 어떻게 해야 하죠?\n남자: 유실물센터에 문의해 보세요. 홈페이지에서도 신고할 수 있어요.",
                        q("남자가 여자에게 제안한 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("유실물센터", "정답: '유실물센터에 문의해 보세요'가 직접적 근거입니다."),
                                opt("역무실", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("경찰서", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("서점", "🔴 빨강 - '책'이라는 단어에서 잘못 연상한 오답입니다.")
                        ), 0, "🔴 '책'이라는 물건 단어에서 다른 장소(서점)를 연상하게 만드는 오답을 넣습니다.",
                                "[분실물 마인드맵] 핵심 단어 '유실물센터'를 색칠하면 답이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "병원/약국",
                        "남자: 예약 안 하고 왔는데 진료 볼 수 있을까요?\n여자: 네, 다만 대기 시간이 좀 있을 수 있어요.",
                        q("여자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("예약 없이도 진료가 가능하다.", "정답: '네, 다만...'이 직접적 근거입니다."),
                                opt("예약 없이는 진료가 불가능하다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("오늘은 휴진이다.", "🔴 빨강 - 대화에 없는 내용입니다."),
                                opt("예약금이 필요하다.", "🔴 빨강 - 대화에 없는 내용입니다.")
                        ), 0, "🔴 '다만'이라는 조건 표현 앞의 '네'를 놓치고 반대로 착각하게 합니다.",
                                "[병원 마인드맵] 가능(네) + 조건(대기 시간). 두 정보 모두 색칠해 놓치지 마세요.")),
                onePassage(PassageCategory.LISTENING, "학교생활",
                        "여자: 이번 학기 장학금 신청 기간이 언제까지예요?\n남자: 이번 주 금요일까지예요. 서둘러 신청하세요.",
                        q("장학금 신청 마감일로 알맞은 것을 고르십시오.", List.of(
                                opt("이번 주 금요일", "정답: '이번 주 금요일까지예요'가 직접적 근거입니다."),
                                opt("다음 주 금요일", "🔴 빨강 - '이번 주'를 '다음 주'로 바꾼 오답입니다."),
                                opt("이번 주 월요일", "🔴 빨강 - 요일을 슬쩍 바꾼 오답입니다."),
                                opt("이번 달 말일", "🔴 빨강 - 대화에 없는 시점입니다.")
                        ), 0, "🔴 주(이번 주/다음 주)나 요일을 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[장학금 마인드맵] 핵심 표현 '이번 주 금요일'을 색칠하면 마감일이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "대중교통 이용",
                        "남자: 교통카드 잔액이 부족한데 어디서 충전해요?\n여자: 편의점이나 지하철역 충전기에서 하시면 돼요.",
                        q("교통카드를 충전할 수 있는 곳이 아닌 것을 고르십시오.", List.of(
                                opt("은행 ATM", "정답: 대화에 언급되지 않은 곳입니다."),
                                opt("편의점", "🟣 보라 - 대화에 언급된 곳이라 '아닌 것'이 아닙니다."),
                                opt("지하철역 충전기", "🟣 보라 - 대화에 언급된 곳이라 '아닌 것'이 아닙니다."),
                                opt("충전 가능한 곳", "🟣 보라 - 전체를 포괄하는 표현이라 '아닌 것'이 아닙니다.")
                        ), 0, "🟣 '아닌 것을 고르십시오' 유형에서 언급된 장소를 답으로 착각하게 합니다.",
                                "[체크 마인드맵] 언급된 장소는 ✅, 언급 안 된 장소(은행)는 ❌. ❌가 정답입니다."))
        );

        List<PassageSeed> reading4th21to30 = List.of(
                onePassage(PassageCategory.READING, "초대장",
                        "초대장\n저희 결혼식에 초대합니다.\n일시: 10월 5일 토요일 오후 1시\n장소: ○○웨딩홀 3층",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("결혼식은 10월 5일 토요일이다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("결혼식은 일요일이다.", "🔵 파랑 - '토요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("장소는 2층이다.", "🔵 파랑 - '3층'을 다른 층으로 바꾼 오답입니다."),
                                opt("시간은 오전이다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다.")
                        ), 0, "🔵 날짜, 요일, 층수, 오전/오후 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[초대장 마인드맵] 날짜(10/5) / 요일(토) / 시간(오후 1시) / 장소(3층). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "메뉴판/식당안내",
                        "○○식당 메뉴\n김치찌개 8,000원\n된장찌개 7,000원\n포장 주문 시 500원 할인",
                        q("이 메뉴판의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("포장 주문하면 500원 할인된다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("매장 식사가 더 저렴하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("김치찌개가 더 저렴하다.", "🔵 파랑 - '8,000원'이 '7,000원'보다 비쌉니다."),
                                opt("할인은 1,000원이다.", "🔵 파랑 - '500원'을 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 가격 비교나 할인 금액 숫자를 슬쩍 바꾼 오답을 넣습니다.",
                                "[메뉴판 마인드맵] 김치찌개(8천 원) / 된장찌개(7천 원) / 포장 할인(500원). 색으로 숫자를 구분하세요.")),
                onePassage(PassageCategory.READING, "일정표",
                        "문화센터 8월 일정표\n1일: 요가, 8일: 요리교실, 15일: 서예, 22일: 사진教室",
                        q("8월 15일에 진행되는 프로그램으로 알맞은 것을 고르십시오.", List.of(
                                opt("서예", "정답: '15일: 서예'가 직접적 근거입니다."),
                                opt("요가", "🔴 빨강 - 1일 프로그램을 15일로 착각하게 합니다."),
                                opt("요리교실", "🔴 빨강 - 8일 프로그램을 15일로 착각하게 합니다."),
                                opt("사진교실", "🔴 빨강 - 22일 프로그램을 15일로 착각하게 합니다.")
                        ), 0, "🔴 일정표에서 날짜와 프로그램을 서로 바꿔 헷갈리게 합니다.",
                                "[일정표 마인드맵] 1일(요가) → 8일(요리) → 15일(서예) → 22일(사진). 날짜별로 다른 색을 매겨보세요.")),
                onePassage(PassageCategory.READING, "게시판 댓글",
                        "[게시판] 이 강좌 정말 유익했어요! 다음 학기에도 꼭 개설해 주세요.",
                        q("이 댓글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("강좌 재개설을 요청하려고", "정답: '다음 학기에도 꼭 개설해 주세요'가 핵심 목적입니다."),
                                opt("강좌에 불만을 제기하려고", "🟢 초록 - 글의 어조(유익했다)와 반대됩니다."),
                                opt("환불을 요청하려고", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("강사를 소개하려고", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 긍정적 어조를 무시하고 반대 목적(불만)으로 착각하게 합니다.",
                                "[댓글 마인드맵] 핵심 문장 '꼭 개설해 주세요'를 색칠하면 목적이 바로 드러납니다.")),
                onePassage(PassageCategory.READING, "설명서/사용법",
                        "전자레인지 사용법\n1. 음식을 그릇에 담는다.\n2. 시간을 설정한다.\n3. 시작 버튼을 누른다.",
                        q("전자레인지 사용 순서로 맞는 것을 고르십시오.", List.of(
                                opt("담기 → 시간 설정 → 시작 버튼", "정답: 글의 순서 그대로입니다."),
                                opt("시간 설정 → 담기 → 시작 버튼", "🟣 보라 - 1번과 2번의 순서를 바꾼 오답입니다."),
                                opt("시작 버튼 → 담기 → 시간 설정", "🟣 보라 - 순서가 완전히 뒤바뀐 오답입니다."),
                                opt("담기 → 시작 버튼 → 시간 설정", "🟣 보라 - 2번과 3번의 순서를 바꾼 오답입니다.")
                        ), 0, "🟣 번호가 매겨진 순서를 서로 바꿔서 헷갈리게 합니다.",
                                "[사용법 마인드맵] ①담기 → ②시간설정 → ③시작. 번호에 맞춰 색으로 순서를 매겨보세요.")),
                onePassage(PassageCategory.READING, "초대장",
                        "초대장\n돌잔치에 초대합니다.\n일시: 9월 14일 일요일 낮 12시\n장소: ○○호텔 그랜드볼룸",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("돌잔치는 일요일 낮 12시다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("돌잔치는 저녁에 열린다.", "🔵 파랑 - '낮'을 '저녁'으로 바꾼 오답입니다."),
                                opt("돌잔치는 토요일이다.", "🔵 파랑 - '일요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("장소는 컨벤션센터다.", "🔵 파랑 - '호텔'을 다른 장소로 바꾼 오답입니다.")
                        ), 0, "🔵 요일, 시간대, 장소 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[초대장 마인드맵] 날짜(9/14) / 요일(일) / 시간(낮 12시) / 장소(호텔). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "메뉴판/식당안내",
                        "○○분식 안내\n영업시간: 11:00~21:00\n매주 월요일 휴무",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("월요일에는 영업하지 않는다.", "정답: '매주 월요일 휴무'가 직접적 근거입니다."),
                                opt("화요일에 휴무한다.", "🔵 파랑 - '월요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("24시간 영업한다.", "🔵 파랑 - '11:00~21:00'과 반대됩니다."),
                                opt("휴무일이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 휴무 요일이나 영업 시간을 반대로 바꾼 오답을 넣습니다.",
                                "[식당안내 마인드맵] 영업시간(11~21시) / 휴무(월요일). 색으로 두 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "일정표",
                        "동아리 활동 일정\n화요일: 축구, 목요일: 농구, 토요일: 등산",
                        q("목요일에 하는 활동으로 알맞은 것을 고르십시오.", List.of(
                                opt("농구", "정답: '목요일: 농구'가 직접적 근거입니다."),
                                opt("축구", "🔴 빨강 - 화요일 활동을 목요일로 착각하게 합니다."),
                                opt("등산", "🔴 빨강 - 토요일 활동을 목요일로 착각하게 합니다."),
                                opt("수영", "🔴 빨강 - 일정표에 없는 활동입니다.")
                        ), 0, "🔴 요일과 활동을 서로 바꿔 배치한 오답을 넣습니다.",
                                "[일정표 마인드맵] 화(축구) → 목(농구) → 토(등산). 요일별로 다른 색을 매겨보세요.")),
                onePassage(PassageCategory.READING, "게시판 댓글",
                        "[게시판] 상품이 사진과 너무 달라요. 색상도 다르고 사이즈도 작아요. 환불 요청합니다.",
                        q("이 댓글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("환불을 요청하려고", "정답: '환불 요청합니다'가 핵심 목적입니다."),
                                opt("상품을 칭찬하려고", "🟢 초록 - 글의 어조(불만)와 반대됩니다."),
                                opt("배송을 재촉하려고", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("추가 구매를 하려고", "🟢 초록 - 글의 내용과 반대됩니다.")
                        ), 0, "🟢 불만 어조를 무시하고 반대 목적(칭찬)으로 착각하게 합니다.",
                                "[댓글 마인드맵] 핵심 문장 '환불 요청합니다'를 색칠하면 목적이 바로 드러납니다.")),
                onePassage(PassageCategory.READING, "설명서/사용법",
                        "세탁기 사용법\n1. 세제를 넣는다.\n2. 옷을 넣는다.\n3. 코스를 선택하고 시작 버튼을 누른다.",
                        q("세탁기 사용 순서로 맞는 것을 고르십시오.", List.of(
                                opt("세제 → 옷 → 코스 선택", "정답: 글의 순서 그대로입니다."),
                                opt("옷 → 세제 → 코스 선택", "🟣 보라 - 1번과 2번의 순서를 바꾼 오답입니다."),
                                opt("코스 선택 → 세제 → 옷", "🟣 보라 - 순서가 완전히 뒤바뀐 오답입니다."),
                                opt("세제 → 코스 선택 → 옷", "🟣 보라 - 2번과 3번의 순서를 바꾼 오답입니다.")
                        ), 0, "🟣 번호가 매겨진 순서를 서로 바꿔서 헷갈리게 합니다.",
                                "[사용법 마인드맵] ①세제 → ②옷 → ③코스선택. 번호에 맞춰 색으로 순서를 매겨보세요."))
        );

        List<PassageSeed> reading4th31to40 = List.of(
                onePassage(PassageCategory.READING, "초대장",
                        "초대장\n개업식에 초대합니다.\n일시: 11월 3일 화요일 오전 11시\n장소: ○○카페 1호점",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("개업식은 화요일 오전이다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("개업식은 수요일이다.", "🔵 파랑 - '화요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("시간은 오후다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("장소는 2호점이다.", "🔵 파랑 - '1호점'을 다른 지점으로 바꾼 오답입니다.")
                        ), 0, "🔵 요일, 시간대, 지점 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[초대장 마인드맵] 날짜(11/3) / 요일(화) / 시간(오전 11시) / 장소(1호점). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "메뉴판/식당안내",
                        "○○피자 안내\n라지 사이즈 주문 시 콜라 1병 무료 증정\n배달은 30분 이내",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("라지 사이즈를 시키면 콜라를 준다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("스몰 사이즈도 콜라를 준다.", "🔵 파랑 - '라지 사이즈'라는 조건을 무시한 오답입니다."),
                                opt("배달은 한 시간 걸린다.", "🔵 파랑 - '30분 이내'와 반대됩니다."),
                                opt("콜라는 유료다.", "🔵 파랑 - '무료 증정'과 반대됩니다.")
                        ), 0, "🔵 증정 조건이나 배달 시간을 슬쩍 바꾼 오답을 넣습니다.",
                                "[피자안내 마인드맵] 조건(라지 사이즈) → 혜택(콜라 무료). 화살표로 조건과 혜택을 연결하세요.")),
                onePassage(PassageCategory.READING, "일정표",
                        "체육대회 일정표\n오전 9시: 개회식, 오전 10시: 축구, 오후 1시: 계주, 오후 3시: 폐회식",
                        q("계주가 진행되는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("오후 1시", "정답: '오후 1시: 계주'가 직접적 근거입니다."),
                                opt("오전 9시", "🔴 빨강 - 개회식 시간을 계주 시간으로 착각하게 합니다."),
                                opt("오전 10시", "🔴 빨강 - 축구 시간을 계주 시간으로 착각하게 합니다."),
                                opt("오후 3시", "🔴 빨강 - 폐회식 시간을 계주 시간으로 착각하게 합니다.")
                        ), 0, "🔴 일정표에서 시간과 종목을 서로 바꿔 배치한 오답을 넣습니다.",
                                "[체육대회 마인드맵] 9시(개회식) → 10시(축구) → 1시(계주) → 3시(폐회식). 시간별로 색을 매겨보세요.")),
                multiQ(PassageCategory.READING, "설명서 복합 문제",
                        "공기청정기 사용법\n1. 전원 버튼을 눌러 켠다.\n2. 원하는 풍량을 선택한다(약/중/강).\n3. 필터는 3개월마다 교체한다.\n※ 젖은 손으로 만지지 마세요.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("필터는 3개월마다 교체해야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("필터는 1년에 한 번 교체한다.", "🔵 파랑 - '3개월'을 다른 기간으로 바꾼 오답입니다."),
                                opt("풍량 선택 기능이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("젖은 손으로 만져도 된다.", "🔵 파랑 - '만지지 마세요'와 반대됩니다.")
                        ), 0, "🔵 필터 교체 주기나 안전 수칙을 반대로 바꾼 오답을 넣습니다.",
                                "[설명서 마인드맵] 사용(전원→풍량) / 관리(필터 3개월) / 주의(젖은 손 금지). 색으로 세 영역을 구분하세요."),
                        q("공기청정기 사용 순서로 맞는 것을 고르십시오.", List.of(
                                opt("전원 켜기 → 풍량 선택", "정답: 글의 순서 그대로입니다."),
                                opt("풍량 선택 → 전원 켜기", "🟣 보라 - 1번과 2번의 순서를 바꾼 오답입니다."),
                                opt("필터 교체 → 전원 켜기", "🟣 보라 - 관리 항목과 사용 순서를 혼동시킨 오답입니다."),
                                opt("풍량 선택 → 필터 교체", "🟣 보라 - 사용 순서가 아닌 항목을 조합한 오답입니다.")
                        ), 0, "🟣 번호가 매겨진 사용 순서를 관리 항목(필터 교체)과 섞어 헷갈리게 합니다.",
                                "[사용법 마인드맵] ①전원 → ②풍량. 필터 교체는 순서가 아닌 별도 관리 항목임을 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "게시판 댓글",
                        "[게시판] 이 앱 업데이트 후 자꾸 오류가 나요. 빨리 고쳐주세요.",
                        q("이 댓글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("오류 수정을 요청하려고", "정답: '빨리 고쳐주세요'가 핵심 목적입니다."),
                                opt("앱을 칭찬하려고", "🟢 초록 - 글의 어조(불만)와 반대됩니다."),
                                opt("새 기능을 제안하려고", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("사용법을 문의하려고", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 불만 어조를 무시하고 다른 목적(문의, 제안)으로 착각하게 합니다.",
                                "[댓글 마인드맵] 핵심 문장 '빨리 고쳐주세요'를 색칠하면 목적이 바로 드러납니다.")),
                onePassage(PassageCategory.READING, "초대장",
                        "초대장\n전시회 오픈식에 초대합니다.\n일시: 6월 20일 목요일 오후 4시\n장소: ○○갤러리 2관",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("전시회는 목요일 오후에 시작한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("전시회는 금요일이다.", "🔵 파랑 - '목요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("시간은 오전이다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다."),
                                opt("장소는 1관이다.", "🔵 파랑 - '2관'을 다른 관으로 바꾼 오답입니다.")
                        ), 0, "🔵 요일, 시간대, 관 번호 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[초대장 마인드맵] 날짜(6/20) / 요일(목) / 시간(오후 4시) / 장소(2관). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "메뉴판/식당안내",
                        "○○카페 안내\n오후 3시~5시는 브레이크타임으로 음료만 주문 가능합니다.",
                        q("오후 4시에 주문할 수 있는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("음료만 가능", "정답: '브레이크타임으로 음료만 주문 가능'이 직접적 근거입니다."),
                                opt("음식과 음료 모두 가능", "🔵 파랑 - 브레이크타임 제한과 반대됩니다."),
                                opt("아무것도 주문 불가", "🔵 파랑 - 음료는 가능하다는 내용과 반대됩니다."),
                                opt("디저트만 가능", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 브레이크타임의 제한 범위를 반대로 바꾸거나 과장한 오답을 넣습니다.",
                                "[카페안내 마인드맵] 시간(3~5시) / 가능 메뉴(음료만). 색으로 시간과 조건을 함께 표시하세요.")),
                onePassage(PassageCategory.READING, "일정표",
                        "독서 모임 일정\n1주차: 소설, 2주차: 에세이, 3주차: 시, 4주차: review 모임",
                        q("4주차에 하는 활동으로 알맞은 것을 고르십시오.", List.of(
                                opt("review 모임", "정답: '4주차: review 모임'이 직접적 근거입니다."),
                                opt("소설 읽기", "🔴 빨강 - 1주차 활동을 4주차로 착각하게 합니다."),
                                opt("에세이 읽기", "🔴 빨강 - 2주차 활동을 4주차로 착각하게 합니다."),
                                opt("시 읽기", "🔴 빨강 - 3주차 활동을 4주차로 착각하게 합니다.")
                        ), 0, "🔴 주차와 활동을 서로 바꿔 배치한 오답을 넣습니다.",
                                "[독서모임 마인드맵] 1주(소설) → 2주(에세이) → 3주(시) → 4주(review). 주차별로 다른 색을 매겨보세요.")),
                onePassage(PassageCategory.READING, "게시판 댓글",
                        "[게시판] 강의 자료 링크가 열리지 않아요. 다시 업로드해 주실 수 있나요?",
                        q("이 댓글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("자료 재업로드를 요청하려고", "정답: '다시 업로드해 주실 수 있나요?'가 핵심 목적입니다."),
                                opt("강의를 신청하려고", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("강의를 칭찬하려고", "🟢 초록 - 글의 어조와 관련이 없습니다."),
                                opt("환불을 요청하려고", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 문제 상황(링크 오류)만 보고 다른 목적(환불)으로 확대 해석하게 합니다.",
                                "[댓글 마인드맵] 핵심 문장 '다시 업로드해 주실 수 있나요?'를 색칠하면 목적이 바로 드러납니다."))
        );

        List<PassageSeed> listening5th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "우체국/택배",
                        "여자: 이 소포를 부산으로 보내려고 하는데요.\n남자: 무게를 재 볼게요. 여기 저울 위에 올려주세요.",
                        q("남자가 여자에게 요청한 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("소포를 저울 위에 올리기", "정답: '저울 위에 올려주세요'가 직접적 근거입니다."),
                                opt("주소를 써주기", "🟢 초록 - 대화에 없는 요청입니다."),
                                opt("요금을 내기", "🟢 초록 - 대화에 없는 요청입니다."),
                                opt("소포를 열어보기", "🟢 초록 - 대화에 없는 요청입니다.")
                        ), 0, "🟢 우체국에서 흔히 일어나는 다른 절차(요금 지불, 주소 기재)를 답처럼 넣습니다.",
                                "[택배 마인드맵] 무게 측정(현재 요청) → 이후 절차(요금 등). 지금 요청된 것만 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "은행 업무",
                        "남자: 통장을 만들고 싶은데요.\n여자: 신분증 가지고 오셨어요? 신분증이 있어야 만들 수 있어요.",
                        q("통장을 만들기 위해 필요한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("신분증", "정답: '신분증이 있어야 만들 수 있어요'가 직접적 근거입니다."),
                                opt("도장", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("현금", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("휴대폰", "🔴 빨강 - 대화에 없는 준비물입니다.")
                        ), 0, "🔴 실제로 필요한 것 외에 흔히 예상되는 다른 준비물을 답처럼 섞어 놓습니다.",
                                "[은행 마인드맵] 핵심 단어 '신분증'을 색칠하면 필요한 것이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "미용실",
                        "여자: 어떻게 잘라 드릴까요?\n남자: 옆머리는 짧게, 윗머리는 길게 남겨주세요.",
                        q("남자가 요청한 머리 스타일로 알맞은 것을 고르십시오.", List.of(
                                opt("옆머리는 짧게, 윗머리는 길게", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("옆머리는 길게, 윗머리는 짧게", "🔴 빨강 - 두 부위의 길이를 서로 바꾼 오답입니다."),
                                opt("전체적으로 짧게", "🔴 빨강 - 대화 내용과 다릅니다."),
                                opt("전체적으로 길게", "🔴 빨강 - 대화 내용과 다릅니다.")
                        ), 0, "🔴 두 부위(옆머리/윗머리)의 길이 지시를 서로 바꿔서 헷갈리게 합니다.",
                                "[미용실 마인드맵] 옆머리(짧게, 파랑) / 윗머리(길게, 초록). 부위와 길이를 색으로 짝지으세요.")),
                onePassage(PassageCategory.LISTENING, "날씨 예보",
                        "남자: 내일 날씨 어때요?\n여자: 오전엔 맑다가 오후부터 비가 온대요.",
                        q("내일 오후 날씨로 알맞은 것을 고르십시오.", List.of(
                                opt("비", "정답: '오후부터 비가 온대요'가 직접적 근거입니다."),
                                opt("맑음", "🔴 빨강 - 오전 날씨를 오후 날씨로 착각하게 합니다."),
                                opt("눈", "🔴 빨강 - 대화에 없는 날씨입니다."),
                                opt("흐림", "🔴 빨강 - 대화에 없는 날씨입니다.")
                        ), 0, "🔴 오전과 오후의 날씨를 서로 바꿔서 헷갈리게 합니다.",
                                "[날씨 마인드맵] 오전(맑음, 노랑) → 오후(비, 파랑). 시간대별로 색을 다르게 매기세요.")),
                withDiagram(onePassage(PassageCategory.LISTENING, "회의/발표",
                        "여자: 발표 자료는 다 준비되셨어요?\n남자: 네, PPT는 끝났는데 유인물은 아직 인쇄 중이에요.",
                        q("아직 준비되지 않은 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("유인물", "정답: '유인물은 아직 인쇄 중이에요'가 직접적 근거입니다."),
                                opt("PPT", "🟣 보라 - 'PPT는 끝났다'고 했으므로 준비된 것입니다."),
                                opt("발표 원고", "🟣 보라 - 대화에 언급되지 않아 알 수 없습니다."),
                                opt("회의실 예약", "🟣 보라 - 대화에 언급되지 않아 알 수 없습니다.")
                        ), 0, "🟣 완료된 것(PPT)과 진행 중인 것(유인물)을 헷갈리게 배치합니다.",
                                "[준비상황 마인드맵] PPT(완료, 초록) / 유인물(진행 중, 주황). 상태별로 색을 다르게 표시하세요.")),
                        "<svg viewBox=\"0 0 300 90\" xmlns=\"http://www.w3.org/2000/svg\">"
                                + "<rect x=\"15\" y=\"25\" width=\"120\" height=\"40\" rx=\"8\" fill=\"#d1fae5\" stroke=\"#10b981\" stroke-width=\"2\"/>"
                                + "<text x=\"75\" y=\"49\" text-anchor=\"middle\" font-size=\"13\" fill=\"#065f46\">PPT 완료 ✓</text>"
                                + "<rect x=\"165\" y=\"25\" width=\"120\" height=\"40\" rx=\"8\" fill=\"#ffedd5\" stroke=\"#f97316\" stroke-width=\"2\"/>"
                                + "<text x=\"225\" y=\"49\" text-anchor=\"middle\" font-size=\"13\" fill=\"#9a3412\">유인물 진행중</text>"
                                + "</svg>"),
                onePassage(PassageCategory.LISTENING, "우체국/택배",
                        "남자: 이 택배 오늘 안에 도착할 수 있어요?\n여자: 일반 배송은 이틀 걸리는데 빠른 배송을 이용하시면 내일 도착해요.",
                        q("택배가 가장 빨리 도착할 수 있는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("빠른 배송 이용", "정답: '빠른 배송을 이용하시면 내일 도착해요'가 직접적 근거입니다."),
                                opt("일반 배송 이용", "🔴 빨강 - '이틀 걸린다'는 더 느린 방법입니다."),
                                opt("오늘 안에는 불가능", "🔴 빨강 - 대화 내용과 반대입니다."),
                                opt("직접 방문 수령", "🔴 빨강 - 대화에 없는 방법입니다.")
                        ), 0, "🔴 더 느린 방법(일반 배송)을 정답처럼 착각하게 합니다.",
                                "[배송 마인드맵] 일반(이틀, 빨강) vs 빠른 배송(내일, 초록). 두 방법을 색으로 비교하세요.")),
                onePassage(PassageCategory.LISTENING, "은행 업무",
                        "여자: 해외 송금을 하려고 하는데 수수료가 얼마나 드나요?\n남자: 금액에 따라 다른데 보통 5천 원에서 만 원 정도예요.",
                        q("해외 송금 수수료로 알맞은 것을 고르십시오.", List.of(
                                opt("5천 원에서 만 원 정도", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("항상 만 원", "🔴 빨강 - '보통'이라는 표현을 무시한 오답입니다."),
                                opt("무료", "🔴 빨강 - 대화 내용과 반대입니다."),
                                opt("5천 원 고정", "🔴 빨강 - 범위를 고정 금액으로 착각하게 합니다.")
                        ), 0, "🔴 범위로 제시된 정보(5천~만 원)를 고정 금액으로 착각하게 합니다.",
                                "[수수료 마인드맵] 범위 표현 '5천 원에서 만 원'을 통째로 색칠해 범위임을 기억하세요.")),
                onePassage(PassageCategory.LISTENING, "미용실",
                        "남자: 파마하는 데 시간이 얼마나 걸려요?\n여자: 두 시간 정도 걸려요. 오래 기다리셔야 해요.",
                        q("파마에 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("두 시간 정도", "정답: '두 시간 정도 걸려요'가 직접적 근거입니다."),
                                opt("한 시간 정도", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("30분 정도", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("세 시간 정도", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 글에 없는 다른 시간을 섞어 정확한 시간을 놓치게 합니다.",
                                "[미용실 마인드맵] 핵심 표현 '두 시간 정도'를 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.LISTENING, "날씨 예보",
                        "여자: 이번 주말에 등산 가려는데 날씨 괜찮을까요?\n남자: 토요일은 흐리고 일요일은 맑대요.",
                        q("일요일 날씨로 알맞은 것을 고르십시오.", List.of(
                                opt("맑음", "정답: '일요일은 맑대요'가 직접적 근거입니다."),
                                opt("흐림", "🔴 빨강 - 토요일 날씨를 일요일로 착각하게 합니다."),
                                opt("비", "🔴 빨강 - 대화에 없는 날씨입니다."),
                                opt("눈", "🔴 빨강 - 대화에 없는 날씨입니다.")
                        ), 0, "🔴 토요일과 일요일의 날씨를 서로 바꿔서 헷갈리게 합니다.",
                                "[날씨 마인드맵] 토(흐림, 회색) → 일(맑음, 노랑). 요일별로 색을 다르게 매기세요.")),
                onePassage(PassageCategory.LISTENING, "회의/발표",
                        "남자: 회의 시작 전에 자료 좀 나눠주시겠어요?\n여자: 네, 지금 바로 나눠드릴게요.",
                        q("여자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("자료를 나눠준다.", "정답: '지금 바로 나눠드릴게요'가 직접적 근거입니다."),
                                opt("회의를 시작한다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("자료를 인쇄한다.", "🟢 초록 - 이미 완료된 것으로 추정되는 단계입니다."),
                                opt("회의를 연기한다.", "🟢 초록 - 대화 내용과 반대됩니다.")
                        ), 0, "🟢 요청받은 행동(나눠주기)과 이전 단계(인쇄하기)를 혼동하게 합니다.",
                                "[회의 마인드맵] 요청(나눠주세요) → 응답(나눠드릴게요). 마지막 문장만 정답 가지입니다."))
        );

        List<PassageSeed> listening5th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "우체국/택배",
                        "여자: 이 상자 안에 깨지기 쉬운 물건이 있어요.\n남자: 그럼 취급주의 스티커를 붙여 드릴게요.",
                        q("남자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("취급주의 스티커를 붙인다.", "정답: '취급주의 스티커를 붙여 드릴게요'가 직접적 근거입니다."),
                                opt("상자를 다시 포장한다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("배송을 취소한다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("무게를 다시 잰다.", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 요청 상황(깨지기 쉬움)에서 예상되는 다른 조치를 답처럼 넣습니다.",
                                "[택배 마인드맵] 상황(깨지기 쉬움) → 조치(스티커 부착). 화살표 뒤 문장이 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "은행 업무",
                        "남자: 카드 비밀번호를 잊어버렸어요.\n여자: 신분증을 가지고 오시면 재발급 도와드릴게요.",
                        q("카드 비밀번호를 잊어버렸을 때 필요한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("신분증", "정답: '신분증을 가지고 오시면'이 직접적 근거입니다."),
                                opt("기존 카드", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("통장", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("도장", "🔴 빨강 - 대화에 없는 준비물입니다.")
                        ), 0, "🔴 실제 필요한 것 외에 흔히 예상되는 다른 준비물을 답처럼 섞어 놓습니다.",
                                "[은행 마인드맵] 핵심 단어 '신분증'을 색칠하면 필요한 것이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "미용실",
                        "여자: 염색도 같이 하실 건가요?\n남자: 아니요, 오늘은 커트만 할게요.",
                        q("남자가 오늘 받으려는 서비스로 알맞은 것을 고르십시오.", List.of(
                                opt("커트만", "정답: '오늘은 커트만 할게요'가 직접적 근거입니다."),
                                opt("염색만", "🔴 빨강 - '아니요'라는 부정 답변을 놓치게 합니다."),
                                opt("커트와 염색 모두", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("파마만", "🔴 빨강 - 대화에 없는 서비스입니다.")
                        ), 0, "🔴 부정 표현('아니요') 뒤에 나오는 진짜 정보를 놓치게 합니다.",
                                "[미용실 마인드맵] 염색(❌) → 커트(✅). 부정/긍정을 다른 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "날씨 예보",
                        "여자: 이번 주 내내 더울까요?\n남자: 수요일까지는 덥다가 목요일부터 선선해진대요.",
                        q("목요일부터의 날씨 변화로 알맞은 것을 고르십시오.", List.of(
                                opt("선선해진다.", "정답: '목요일부터 선선해진대요'가 직접적 근거입니다."),
                                opt("더 더워진다.", "🔴 빨강 - 대화 내용과 반대입니다."),
                                opt("계속 똑같다.", "🔴 빨강 - 대화 내용과 반대입니다."),
                                opt("비가 온다.", "🔴 빨강 - 대화에 없는 내용입니다.")
                        ), 0, "🔴 변화의 방향(더움→선선함)을 반대로 착각하게 합니다.",
                                "[날씨 마인드맵] 수요일까지(더움, 빨강) → 목요일부터(선선, 파랑). 화살표 방향을 놓치지 마세요.")),
                onePassage(PassageCategory.LISTENING, "회의/발표",
                        "남자: 오늘 회의 몇 시에 끝날 것 같아요?\n여자: 안건이 많아서 한 시간은 더 걸릴 것 같아요.",
                        q("여자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("회의가 예상보다 길어질 것 같다.", "정답: '한 시간은 더 걸릴 것 같아요'가 직접적 근거입니다."),
                                opt("회의가 곧 끝난다.", "🟢 초록 - 대화 내용과 반대됩니다."),
                                opt("회의가 취소되었다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("안건이 없다.", "🟢 초록 - '안건이 많아서'와 반대됩니다.")
                        ), 0, "🟢 '더 걸린다'는 표현을 '곧 끝난다'로 반대 해석하게 합니다.",
                                "[회의 마인드맵] 안건 많음 → 시간 지연. 원인과 결과를 화살표로 색칠해 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "우체국/택배",
                        "여자: 등기로 보내주세요.\n남자: 네, 등기는 일반 우편보다 요금이 조금 더 비싸요.",
                        q("등기 우편에 대한 설명으로 알맞은 것을 고르십시오.", List.of(
                                opt("일반 우편보다 비싸다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("일반 우편보다 저렴하다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("일반 우편과 요금이 같다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("무료로 이용 가능하다.", "🔴 빨강 - 대화에 없는 내용입니다.")
                        ), 0, "🔴 요금 비교(더 비쌈)를 반대로 바꾼 오답을 넣습니다.",
                                "[우편 마인드맵] 일반 우편(저렴) vs 등기(비쌈). 두 방식을 색으로 비교하세요.")),
                onePassage(PassageCategory.LISTENING, "은행 업무",
                        "남자: 이 통장으로 자동이체를 설정하고 싶어요.\n여자: 네, 이체할 계좌 정보를 알려주시겠어요?",
                        q("여자가 남자에게 요청한 정보로 알맞은 것을 고르십시오.", List.of(
                                opt("이체할 계좌 정보", "정답: '이체할 계좌 정보를 알려주시겠어요?'가 직접적 근거입니다."),
                                opt("신분증 번호", "🟢 초록 - 대화에 없는 요청입니다."),
                                opt("비밀번호", "🟢 초록 - 대화에 없는 요청입니다."),
                                opt("연락처", "🟢 초록 - 대화에 없는 요청입니다.")
                        ), 0, "🟢 은행 업무에서 흔히 요구되는 다른 정보(신분증, 비밀번호)를 답처럼 넣습니다.",
                                "[자동이체 마인드맵] 핵심 표현 '이체할 계좌 정보'를 색칠하면 요청 내용이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "미용실",
                        "여자: 머리 감겨 드릴까요?\n남자: 네, 부탁드려요. 시원한 물로 해 주세요.",
                        q("남자가 추가로 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("시원한 물로 감기", "정답: '시원한 물로 해 주세요'가 직접적 근거입니다."),
                                opt("따뜻한 물로 감기", "🔴 빨강 - '시원한'을 '따뜻한'으로 바꾼 오답입니다."),
                                opt("머리 안 감기", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("두 번 감기", "🔴 빨강 - 대화에 없는 내용입니다.")
                        ), 0, "🔴 온도 표현(시원한/따뜻한)을 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[미용실 마인드맵] 핵심 단어 '시원한 물'을 색칠하면 요청이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "날씨 예보",
                        "남자: 오늘 미세먼지가 심하대요. 마스크 챙기세요.\n여자: 네, 알려줘서 고마워요.",
                        q("남자가 여자에게 말하는 목적으로 알맞은 것을 고르십시오.", List.of(
                                opt("미세먼지 정보를 알려주려고", "정답: 미세먼지 소식과 준비물 안내가 핵심 목적입니다."),
                                opt("외출을 취소하라고 하려고", "🟢 초록 - 언급되지 않은 결론을 임의로 추가한 오답입니다."),
                                opt("마스크를 사 달라고 하려고", "🟢 초록 - '마스크'라는 단어만 보고 만든 오답입니다."),
                                opt("고맙다고 인사하려고", "🟢 초록 - 남자의 말이 아니라 여자의 반응입니다.")
                        ), 0, "🟢 마지막 문장(고마워요)의 화자를 헷갈려 목적을 반대로 연결하게 합니다.",
                                "[정보전달 마인드맵] 말한 사람 = 남자, 목적 가지 = 정보 전달. 누가 말했는지부터 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "회의/발표",
                        "여자: 발표할 때 마이크 써야 하나요?\n남자: 네, 뒤에 앉은 분들도 잘 들리게 마이크를 사용해 주세요.",
                        q("남자가 여자에게 마이크 사용을 요청한 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("뒤에 앉은 사람들도 잘 들리게 하려고", "정답: '뒤에 앉은 분들도 잘 들리게'가 직접적 근거입니다."),
                                opt("마이크가 고장 나서", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("녹음을 하려고", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("발표 시간을 줄이려고", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 실제 이유(잘 들리게)를 다른 이유(녹음, 고장)로 착각하게 합니다.",
                                "[발표 마인드맵] 이유(뒤까지 들리게) → 요청(마이크 사용). 화살표로 인과관계를 표시하세요."))
        );

        List<PassageSeed> reading5th21to30 = List.of(
                onePassage(PassageCategory.READING, "설문조사",
                        "설문조사\n귀하께서 가장 선호하는 학습 방법은 무엇입니까?\n① 동영상 강의 ② 책 ③ 스터디 그룹 ④ 개인 과외",
                        q("이 설문조사가 묻고 있는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("선호하는 학습 방법", "정답: '가장 선호하는 학습 방법은 무엇입니까?'가 직접적 근거입니다."),
                                opt("선호하는 과목", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("학습 시간", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("학습 장소", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 보기(동영상, 책 등)에서 다른 주제(과목, 시간)를 연상하게 합니다.",
                                "[설문 마인드맵] 핵심 질문 '선호하는 학습 방법'을 색칠하면 주제가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "초대 답장",
                        "답장\n초대해 주셔서 감사합니다. 아쉽지만 그날 다른 일정이 있어서 참석이 어려울 것 같습니다.",
                        q("이 답장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초대에 참석할 수 없다.", "정답: '참석이 어려울 것 같습니다'가 직접적 근거입니다."),
                                opt("초대에 참석한다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("초대 자체를 거절한다.", "🔵 파랑 - 초대는 감사히 받되 참석만 못하는 것입니다."),
                                opt("일정을 변경해 달라고 한다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 '참석 불가'와 '초대 거절'을 혼동하게 만드는 오답을 넣습니다.",
                                "[답장 마인드맵] 감사(초록) + 불참(빨강). 두 감정을 색으로 구분해 정리하세요.")),
                onePassage(PassageCategory.READING, "분실물 안내",
                        "분실물 안내\n검은색 백팩을 습득했습니다. 안에 노트북과 지갑이 있습니다. 주인은 관리실로 연락 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("검은색 백팩이 습득되었다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("흰색 백팩이 습득되었다.", "🔵 파랑 - '검은색'을 다른 색으로 바꾼 오답입니다."),
                                opt("가방 안에는 아무것도 없다.", "🔵 파랑 - '노트북과 지갑'과 반대됩니다."),
                                opt("경찰서로 연락해야 한다.", "🔵 파랑 - '관리실'을 다른 곳으로 바꾼 오답입니다.")
                        ), 0, "🔵 색깔, 내용물, 연락처 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[분실물 마인드맵] 물건(검은색 백팩) / 내용물(노트북+지갑) / 연락처(관리실). 색으로 항목을 나누세요.")),
                onePassage(PassageCategory.READING, "채용 공고",
                        "채용 공고\n모집 분야: 카페 아르바이트\n근무 시간: 평일 오후 2시~6시\n지원 방법: 이력서를 매장에 제출",
                        q("이 채용 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("근무 시간은 평일 오후 2시부터 6시까지다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("주말 근무다.", "🔵 파랑 - '평일'을 '주말'로 바꾼 오답입니다."),
                                opt("오전 근무다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다."),
                                opt("온라인으로만 지원 가능하다.", "🔵 파랑 - '매장에 제출'과 반대됩니다.")
                        ), 0, "🔵 근무 요일, 시간대, 지원 방법 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[채용공고 마인드맵] 분야(카페) / 시간(평일 2~6시) / 지원(매장 제출). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "이용후기",
                        "[이용후기] 배송이 정말 빨랐고 포장도 꼼꼼했어요. 다음에 또 이용하고 싶어요.",
                        q("이 이용후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("배송과 포장에 만족했다.", "정답: '배송이 빨랐고 포장도 꼼꼼했다'가 직접적 근거입니다."),
                                opt("배송이 느려서 불만이다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("포장이 엉망이었다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("다시는 이용하지 않겠다.", "🔵 파랑 - '또 이용하고 싶어요'와 반대됩니다.")
                        ), 0, "🔵 긍정적 후기를 반대(불만)로 왜곡한 오답을 넣습니다.",
                                "[후기 마인드맵] 핵심 문장 '빨랐고+꼼꼼했다+또 이용'을 색칠하면 만족도가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "설문조사",
                        "설문조사\n이 서비스를 친구에게 추천하시겠습니까?\n① 매우 그렇다 ② 그렇다 ③ 보통이다 ④ 아니다",
                        q("이 설문조사가 조사하려는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("서비스 추천 의향", "정답: '친구에게 추천하시겠습니까?'가 직접적 근거입니다."),
                                opt("서비스 가격 만족도", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("서비스 이용 빈도", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("서비스 이용 기간", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 보기의 형식(만족도 척도)만 보고 다른 조사 주제로 착각하게 합니다.",
                                "[설문 마인드맵] 핵심 질문 '추천하시겠습니까?'를 색칠하면 조사 주제가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "초대 답장",
                        "답장\n초대해 주셔서 감사합니다. 꼭 참석하겠습니다. 그날 뵙겠습니다.",
                        q("이 답장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초대에 참석하겠다는 의사를 밝혔다.", "정답: '꼭 참석하겠습니다'가 직접적 근거입니다."),
                                opt("참석이 어렵다고 했다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("초대를 거절했다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("일정을 다시 확인하겠다고 했다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 수락 답장을 거절 답장으로 반대로 바꾼 오답을 넣습니다.",
                                "[답장 마인드맵] 핵심 문장 '꼭 참석하겠습니다'를 색칠하면 의사가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "분실물 안내",
                        "분실물 안내\n지난주 화요일 강의실에서 우산을 분실했습니다. 찾으신 분은 학생회실로 연락 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("우산을 찾으면 학생회실로 연락해야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("교무실로 연락해야 한다.", "🔵 파랑 - '학생회실'을 다른 곳으로 바꾼 오답입니다."),
                                opt("분실 요일은 수요일이다.", "🔵 파랑 - '화요일'을 다른 요일로 바꾼 오답입니다."),
                                opt("분실물은 지갑이다.", "🔵 파랑 - '우산'을 다른 물건으로 바꾼 오답입니다.")
                        ), 0, "🔵 물건, 요일, 연락처 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[분실물 마인드맵] 물건(우산) / 요일(화요일) / 연락처(학생회실). 색으로 항목을 나누세요.")),
                onePassage(PassageCategory.READING, "채용 공고",
                        "채용 공고\n모집 분야: 편의점 야간 아르바이트\n우대 사항: 경력자 우대, 초보자도 지원 가능",
                        q("이 채용 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초보자도 지원할 수 있다.", "정답: '초보자도 지원 가능'이 직접적 근거입니다."),
                                opt("경력자만 지원 가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("주간 근무다.", "🔵 파랑 - '야간'을 '주간'으로 바꾼 오답입니다."),
                                opt("모집 분야가 카페다.", "🔵 파랑 - '편의점'을 다른 업종으로 바꾼 오답입니다.")
                        ), 0, "🔵 지원 자격이나 근무 시간대, 업종을 슬쩍 바꾼 오답을 넣습니다.",
                                "[채용공고 마인드맵] 분야(편의점 야간) / 자격(초보 가능). 색으로 두 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "이용후기",
                        "[이용후기] 사진과 실물이 너무 달라요. 색상도 흐릿하고 재질도 별로예요. 실망했습니다.",
                        q("이 이용후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("상품에 실망했다.", "정답: '실망했습니다'가 직접적 근거입니다."),
                                opt("상품에 매우 만족했다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("사진과 실물이 똑같다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("재구매 의사를 밝혔다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 부정적 후기를 긍정적으로 왜곡한 오답을 넣습니다.",
                                "[후기 마인드맵] 핵심 문장 '너무 달라요+실망했습니다'를 색칠하면 감정이 바로 보입니다."))
        );

        List<PassageSeed> reading5th31to40 = List.of(
                onePassage(PassageCategory.READING, "설문조사",
                        "설문조사\n이 앱을 하루에 몇 시간 정도 사용하십니까?\n① 1시간 미만 ② 1~3시간 ③ 3~5시간 ④ 5시간 이상",
                        q("이 설문조사가 조사하려는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("앱 사용 시간", "정답: '하루에 몇 시간 정도 사용하십니까?'가 직접적 근거입니다."),
                                opt("앱 만족도", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("앱 가격", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("앱 사용 목적", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 숫자 보기(1시간, 3시간 등)만 보고 다른 조사 주제로 착각하게 합니다.",
                                "[설문 마인드맵] 핵심 질문 '몇 시간 사용'을 색칠하면 조사 주제가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "초대 답장",
                        "답장\n초대 감사합니다. 다만 아이가 아파서 급하게 병원에 가야 할 것 같아 참석이 힘들 것 같습니다.",
                        q("참석이 어려운 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("아이가 아파서", "정답: '아이가 아파서'가 직접적 근거입니다."),
                                opt("본인이 아파서", "🔴 빨강 - 아픈 대상을 바꾼 오답입니다."),
                                opt("일이 바빠서", "🔴 빨강 - 글에 없는 이유입니다."),
                                opt("다른 약속이 있어서", "🔴 빨강 - 글에 없는 이유입니다.")
                        ), 0, "🔴 아픈 대상(아이/본인)을 슬쩍 바꾼 오답을 넣습니다.",
                                "[답장 마인드맵] 핵심 표현 '아이가 아파서'를 색칠하면 이유가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "분실물 안내",
                        "분실물 안내\n도서관 3층 열람실에서 안경을 습득했습니다. 안내데스크에서 확인해 가세요.",
                        q("안경을 습득한 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("도서관 3층 열람실", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("도서관 2층 열람실", "🔵 파랑 - '3층'을 다른 층으로 바꾼 오답입니다."),
                                opt("도서관 로비", "🔵 파랑 - 글에 없는 장소입니다."),
                                opt("학생 식당", "🔵 파랑 - 글에 없는 장소입니다.")
                        ), 0, "🔵 습득 장소의 층수를 슬쩍 바꾼 오답을 넣습니다.",
                                "[분실물 마인드맵] 핵심 표현 '3층 열람실'을 색칠하면 장소가 바로 보입니다.")),
                multiQ(PassageCategory.READING, "채용 공고 복합 문제",
                        "채용 공고\n모집 분야: 물류창고 포장 아르바이트\n근무 요일: 주 3일(월/수/금)\n시급: 12,000원\n지원 방법: 전화 문의(010-0000-0000) 후 방문 면접",
                        q("이 채용 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("월, 수, 금요일에 근무한다.", "정답: '주 3일(월/수/금)'이 직접적 근거입니다."),
                                opt("매일 근무한다.", "🔵 파랑 - '주 3일'과 반대됩니다."),
                                opt("시급은 만 원이다.", "🔵 파랑 - '12,000원'을 잘못 읽게 만든 오답입니다."),
                                opt("이메일로만 지원 가능하다.", "🔵 파랑 - '전화 문의 후 방문 면접'과 다릅니다.")
                        ), 0, "🔵 근무 요일, 시급, 지원 방법 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[채용공고 마인드맵] 요일(월수금) / 시급(12,000원) / 지원(전화+방문). 색으로 세 항목을 구분하세요."),
                        q("이 채용 공고에 지원하기 위한 첫 단계로 알맞은 것을 고르십시오.", List.of(
                                opt("전화로 문의하기", "정답: '전화 문의 후 방문 면접'에서 전화가 첫 단계입니다."),
                                opt("바로 방문 면접 보기", "🟣 보라 - 전화 문의 없이 바로 방문하는 것은 순서가 틀립니다."),
                                opt("이메일 보내기", "🟣 보라 - 글에 없는 지원 방법입니다."),
                                opt("이력서 우편 발송", "🟣 보라 - 글에 없는 지원 방법입니다.")
                        ), 0, "🟣 지원 절차의 순서(전화 먼저, 면접 나중)를 무시하게 합니다.",
                                "[지원절차 마인드맵] ①전화 문의 → ②방문 면접. 번호를 매겨 순서를 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "이용후기",
                        "[이용후기] 가격 대비 품질이 훌륭해요. 배송도 예상보다 빨랐고요. 강력 추천합니다!",
                        q("이 이용후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("가격 대비 품질에 만족했다.", "정답: '가격 대비 품질이 훌륭해요'가 직접적 근거입니다."),
                                opt("가격이 너무 비싸다고 느꼈다.", "🔵 파랑 - 글에 없는 내용입니다."),
                                opt("배송이 예상보다 느렸다.", "🔵 파랑 - '예상보다 빨랐다'와 반대됩니다."),
                                opt("다른 사람에게 추천하지 않는다.", "🔵 파랑 - '강력 추천합니다'와 반대됩니다.")
                        ), 0, "🔵 긍정적 후기를 반대로 왜곡한 오답을 넣습니다.",
                                "[후기 마인드맵] 핵심 문장 '훌륭해요+강력 추천'을 색칠하면 만족도가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "설문조사",
                        "설문조사\n이번 행사에서 가장 만족스러웠던 프로그램은 무엇입니까?\n① 특강 ② 체험 부스 ③ 공연 ④ 경품 추첨",
                        q("이 설문조사가 묻고 있는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("가장 만족스러운 프로그램", "정답: '가장 만족스러웠던 프로그램은 무엇입니까?'가 직접적 근거입니다."),
                                opt("행사 참석 인원", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("행사 진행 시간", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("행사 장소", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 보기(특강, 공연 등)만 보고 다른 조사 주제(인원, 시간)로 착각하게 합니다.",
                                "[설문 마인드맵] 핵심 질문 '가장 만족스러운 프로그램'을 색칠하면 주제가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "초대 답장",
                        "답장\n초대해 주셔서 감사합니다. 시간을 확인해 보고 다시 연락드리겠습니다.",
                        q("이 답장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("참석 여부가 아직 정해지지 않았다.", "정답: '확인해 보고 다시 연락드리겠습니다'가 직접적 근거입니다."),
                                opt("참석이 확정되었다.", "🔵 파랑 - 글의 내용과 다릅니다."),
                                opt("참석이 거절되었다.", "🔵 파랑 - 글의 내용과 다릅니다."),
                                opt("초대를 못 받았다고 했다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 '보류' 상태를 '확정'이나 '거절'로 단정한 오답을 넣습니다.",
                                "[답장 마인드맵] 핵심 문장 '확인해 보고 다시 연락'을 색칠하면 아직 미정임을 알 수 있습니다.")),
                onePassage(PassageCategory.READING, "분실물 안내",
                        "분실물 안내\n체육관 탈의실에서 시계를 습득했습니다. 은색 시계이며 로비 안내데스크에 맡겼습니다.",
                        q("습득된 시계의 색으로 알맞은 것을 고르십시오.", List.of(
                                opt("은색", "정답: '은색 시계'가 직접적 근거입니다."),
                                opt("금색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("검은색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("파란색", "🔴 빨강 - 대화에 없는 색입니다.")
                        ), 0, "🔴 글에 없는 다른 색을 섞어 정확한 색을 놓치게 합니다.",
                                "[분실물 마인드맵] 핵심 단어 '은색'을 색칠하면 답이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "채용 공고",
                        "채용 공고\n모집 분야: 서점 정직원\n복지: 4대 보험, 도서 할인, 연차 15일",
                        q("이 채용 공고에 언급된 복지가 아닌 것을 고르십시오.", List.of(
                                opt("교통비 지원", "정답: 공고에 언급되지 않은 복지입니다."),
                                opt("4대 보험", "🟣 보라 - 공고에 언급된 복지라 '아닌 것'이 아닙니다."),
                                opt("도서 할인", "🟣 보라 - 공고에 언급된 복지라 '아닌 것'이 아닙니다."),
                                opt("연차 15일", "🟣 보라 - 공고에 언급된 복지라 '아닌 것'이 아닙니다.")
                        ), 0, "🟣 '아닌 것을 고르십시오' 유형에서 언급된 복지를 답으로 착각하게 합니다.",
                                "[체크 마인드맵] 언급된 복지는 ✅, 언급 안 된 복지(교통비)는 ❌. ❌가 정답입니다."))
        );

        List<PassageSeed> listening6th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "관공서 민원",
                        "남자: 주민등록등본을 발급받고 싶은데요.\n여자: 신분증 주시면 바로 발급해 드릴게요.",
                        q("등본을 발급받기 위해 필요한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("신분증", "정답: '신분증 주시면 바로 발급해 드릴게요'가 직접적 근거입니다."),
                                opt("도장", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("가족관계증명서", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("현금", "🔴 빨강 - 대화에 없는 준비물입니다.")
                        ), 0, "🔴 실제 필요한 것 외에 흔히 예상되는 다른 준비물을 답처럼 섞어 놓습니다.",
                                "[민원 마인드맵] 핵심 단어 '신분증'을 색칠하면 필요한 것이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "마트 계산",
                        "여자: 봉투 필요하세요?\n남자: 네, 큰 걸로 하나 주세요.",
                        q("남자가 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("큰 봉투 하나", "정답: '큰 걸로 하나 주세요'가 직접적 근거입니다."),
                                opt("작은 봉투 하나", "🔴 빨강 - '큰'을 '작은'으로 바꾼 오답입니다."),
                                opt("봉투 두 개", "🔴 빨강 - 개수를 슬쩍 바꾼 오답입니다."),
                                opt("봉투가 필요 없음", "🔴 빨강 - 대화 내용과 반대됩니다.")
                        ), 0, "🔴 크기(큰/작은)나 개수를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[계산대 마인드맵] 핵심 표현 '큰 걸로 하나'를 색칠하면 요청이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "사진관/증명사진",
                        "남자: 증명사진 찍으려고 하는데 얼마나 걸려요?\n여자: 촬영은 5분이면 되고, 인화는 10분 정도 더 걸려요.",
                        q("촬영부터 인화까지 걸리는 총 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("약 15분", "정답: 촬영 5분 + 인화 10분 = 15분입니다."),
                                opt("약 5분", "🔴 빨강 - 촬영 시간만 계산한 오답입니다."),
                                opt("약 10분", "🔴 빨강 - 인화 시간만 계산한 오답입니다."),
                                opt("약 20분", "🔴 빨강 - 잘못 계산한 오답입니다.")
                        ), 0, "🔴 두 시간(촬영/인화)을 더하지 않고 하나만 답하게 유도합니다.",
                                "[사진관 마인드맵] 촬영(5분) + 인화(10분) = 15분. 두 숫자를 더하는 계산을 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "이사",
                        "여자: 이사 날짜를 다음 주 금요일로 잡았어요.\n남자: 그럼 이삿짐센터는 예약하셨어요?",
                        q("남자가 여자에게 확인한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("이삿짐센터 예약 여부", "정답: '이삿짐센터는 예약하셨어요?'가 직접적 근거입니다."),
                                opt("이사 날짜", "🟢 초록 - 이미 언급된 정보라 확인 질문이 아닙니다."),
                                opt("이사 비용", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("새 집 주소", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 이미 언급된 정보(날짜)를 확인 질문 내용으로 착각하게 합니다.",
                                "[이사 마인드맵] 이미 앎(날짜, 초록) vs 확인 질문(예약 여부, 빨강). 색으로 구분하세요.")),
                withDiagram(onePassage(PassageCategory.LISTENING, "아이 돌보기",
                        "남자: 아이가 열이 좀 있는 것 같아요.\n여자: 체온계로 재 보고 38도 넘으면 병원에 데려가세요.",
                        q("여자가 남자에게 제안한 기준으로 알맞은 것을 고르십시오.", List.of(
                                opt("체온이 38도를 넘으면 병원에 간다.", "정답: '38도 넘으면 병원에 데려가세요'가 직접적 근거입니다."),
                                opt("체온과 상관없이 무조건 병원에 간다.", "🔴 빨강 - 조건('38도 넘으면')을 무시한 오답입니다."),
                                opt("37도 넘으면 병원에 간다.", "🔴 빨강 - 기준 숫자를 슬쩍 바꾼 오답입니다."),
                                opt("병원에 갈 필요 없다.", "🔴 빨강 - 대화 내용과 반대됩니다.")
                        ), 0, "🔴 조건의 기준 숫자(38도)를 슬쩍 바꾸거나 무시하게 합니다.",
                                "[돌봄 마인드맵] 체온 측정 → 38도 초과 → 병원. 기준 숫자를 빨간색으로 강조하세요.")),
                        "<svg viewBox=\"0 0 300 90\" xmlns=\"http://www.w3.org/2000/svg\">"
                                + "<rect x=\"15\" y=\"30\" width=\"90\" height=\"35\" rx=\"6\" fill=\"#fee2e2\" stroke=\"#ef4444\" stroke-width=\"2\"/>"
                                + "<text x=\"60\" y=\"52\" text-anchor=\"middle\" font-size=\"12\" fill=\"#991b1b\">38도 초과</text>"
                                + "<line x1=\"105\" y1=\"47\" x2=\"160\" y2=\"47\" stroke=\"#6b7280\" stroke-width=\"2\" marker-end=\"url(#arrow4)\"/>"
                                + "<rect x=\"160\" y=\"30\" width=\"90\" height=\"35\" rx=\"6\" fill=\"#dbeafe\" stroke=\"#3b82f6\" stroke-width=\"2\"/>"
                                + "<text x=\"205\" y=\"52\" text-anchor=\"middle\" font-size=\"12\" fill=\"#1d4ed8\">병원 방문</text>"
                                + "<defs><marker id=\"arrow4\" markerWidth=\"8\" markerHeight=\"8\" refX=\"6\" refY=\"4\" orient=\"auto\">"
                                + "<path d=\"M0,0 L8,4 L0,8 z\" fill=\"#6b7280\"/></marker></defs>"
                                + "</svg>"),
                onePassage(PassageCategory.LISTENING, "관공서 민원",
                        "여자: 여권 재발급을 신청하려고 하는데요.\n남자: 여권용 사진 한 장 가지고 오셨어요?",
                        q("남자가 여자에게 확인하는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("여권용 사진 지참 여부", "정답: '여권용 사진 한 장 가지고 오셨어요?'가 직접적 근거입니다."),
                                opt("여권 분실 이유", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("신청 수수료", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("여권 유효기간", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 여권 재발급과 관련된 다른 정보(수수료, 유효기간)를 답처럼 넣습니다.",
                                "[민원 마인드맵] 핵심 표현 '여권용 사진 가지고 오셨어요?'를 색칠하면 확인 내용이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "마트 계산",
                        "남자: 이 카드로 포인트 적립되나요?\n여자: 죄송한데 이 카드는 적립이 안 되고 현금영수증만 가능해요.",
                        q("남자의 카드로 가능한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("현금영수증", "정답: '현금영수증만 가능해요'가 직접적 근거입니다."),
                                opt("포인트 적립", "🔴 빨강 - '적립이 안 되고'와 반대됩니다."),
                                opt("포인트 적립과 현금영수증 모두", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("아무것도 불가능", "🔴 빨강 - '현금영수증만 가능'과 반대됩니다.")
                        ), 0, "🔴 '적립 불가+현금영수증 가능'이라는 조합을 반대로 착각하게 합니다.",
                                "[계산대 마인드맵] 적립(❌, 빨강) / 현금영수증(✅, 초록). 가능/불가능을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "사진관/증명사진",
                        "여자: 배경색은 어떤 걸로 하시겠어요?\n남자: 흰색으로 해 주세요. 여권용이라서요.",
                        q("남자가 선택한 배경색으로 알맞은 것을 고르십시오.", List.of(
                                opt("흰색", "정답: '흰색으로 해 주세요'가 직접적 근거입니다."),
                                opt("파란색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("회색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("빨간색", "🔴 빨강 - 대화에 없는 색입니다.")
                        ), 0, "🔴 글에 없는 다른 색을 섞어 정확한 색을 놓치게 합니다.",
                                "[사진관 마인드맵] 핵심 단어 '흰색'을 색칠하면 답이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "이사",
                        "남자: 이삿짐이 얼마나 되세요?\n여자: 원룸이라 짐이 많지 않아요. 트럭 한 대면 충분할 것 같아요.",
                        q("여자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("짐이 많지 않아 트럭 한 대면 된다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("짐이 많아 트럭 두 대가 필요하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("아파트라서 짐이 많다.", "🔵 파랑 - '원룸'을 다른 주거 형태로 바꾼 오답입니다."),
                                opt("이삿짐센터를 이용하지 않는다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 짐의 양이나 주거 형태를 반대로 바꾼 오답을 넣습니다.",
                                "[이사 마인드맵] 핵심 표현 '원룸+짐 많지 않음+트럭 한 대'를 색칠하면 상황이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "아이 돌보기",
                        "여자: 아이가 밥을 잘 안 먹으려고 해요.\n남자: 좋아하는 반찬을 조금씩 섞어서 줘 보세요.",
                        q("남자가 여자에게 제안한 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("좋아하는 반찬을 섞어서 준다.", "정답: '좋아하는 반찬을 조금씩 섞어서 줘 보세요'가 직접적 근거입니다."),
                                opt("억지로 먹인다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("식사를 거른다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("간식을 많이 준다.", "🟢 초록 - 대화에 없는 내용입니다.")
                        ), 0, "🟢 편식 상황에서 흔히 예상되는 다른 방법(억지로 먹이기)을 답처럼 넣습니다.",
                                "[돌봄 마인드맵] 문제(밥 안 먹음) → 해결책(반찬 섞기). 마지막 문장만 정답 가지입니다."))
        );

        List<PassageSeed> listening6th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "관공서 민원",
                        "남자: 전입신고를 하려고 하는데 어디서 해요?\n여자: 동주민센터 2번 창구에서 하시면 돼요.",
                        q("전입신고를 할 수 있는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("동주민센터 2번 창구", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("동주민센터 1번 창구", "🔴 빨강 - 창구 번호를 슬쩍 바꾼 오답입니다."),
                                opt("구청 민원실", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("경찰서", "🔴 빨강 - 대화에 없는 장소입니다.")
                        ), 0, "🔴 창구 번호나 장소를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[민원 마인드맵] 핵심 표현 '동주민센터 2번 창구'를 색칠하면 위치가 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "마트 계산",
                        "여자: 이 상품 할인쿠폰 있으신가요?\n남자: 아니요, 없어요. 그냥 계산해 주세요.",
                        q("남자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("쿠폰 없이 계산한다.", "정답: '없어요. 그냥 계산해 주세요'가 직접적 근거입니다."),
                                opt("쿠폰을 사용한다.", "🔴 빨강 - '아니요'라는 부정 답변과 반대됩니다."),
                                opt("계산을 취소한다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("쿠폰을 나중에 쓴다.", "🔴 빨강 - 대화에 없는 내용입니다.")
                        ), 0, "🔴 부정 표현('아니요') 뒤에 나오는 진짜 정보를 놓치게 합니다.",
                                "[계산대 마인드맵] 쿠폰(❌) → 그냥 계산(✅). 부정/긍정을 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "사진관/증명사진",
                        "남자: 사진 보정도 해 주시나요?\n여자: 네, 기본 보정은 무료로 해 드려요.",
                        q("여자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("기본 보정이 무료다.", "정답: '기본 보정은 무료로 해 드려요'가 직접적 근거입니다."),
                                opt("보정 서비스가 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("모든 보정이 유료다.", "🔵 파랑 - '기본 보정은 무료'와 다릅니다."),
                                opt("보정에 추가 요금이 항상 붙는다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 무료 범위(기본 보정)를 무시하거나 과장한 오답을 넣습니다.",
                                "[사진관 마인드맵] 핵심 표현 '기본 보정+무료'를 색칠하면 조건이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "이사",
                        "여자: 사다리차가 필요할까요?\n남자: 5층인데 엘리베이터가 없어서 필요할 것 같아요.",
                        q("사다리차가 필요한 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("엘리베이터가 없어서", "정답: '엘리베이터가 없어서'가 직접적 근거입니다."),
                                opt("짐이 너무 무거워서", "🟢 초록 - 대화에 없는 이유입니다."),
                                opt("계단이 좁아서", "🟢 초록 - 대화에 없는 이유입니다."),
                                opt("이사 시간이 부족해서", "🟢 초록 - 대화에 없는 이유입니다.")
                        ), 0, "🟢 사다리차 사용의 다른 일반적 이유(짐 무게 등)를 답처럼 넣습니다.",
                                "[이사 마인드맵] 이유(엘리베이터 없음) → 결과(사다리차 필요). 화살표로 인과관계를 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "아이 돌보기",
                        "남자: 어린이집은 몇 시에 데리러 가면 돼요?\n여자: 보통 5시까지인데 오늘은 행사가 있어서 6시래요.",
                        q("오늘 어린이집에서 아이를 데리러 가야 하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("6시", "정답: '오늘은 행사가 있어서 6시래요'가 직접적 근거입니다."),
                                opt("5시", "🔴 빨강 - 평소 시간(변경 전)에 꽂힌 오답입니다."),
                                opt("4시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("7시", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 평소 시간과 오늘의 변경된 시간을 헷갈리게 배치합니다.",
                                "[어린이집 마인드맵] 평소(5시) → 화살표(오늘 행사) → 6시. 화살표 뒤 숫자만 오늘의 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "관공서 민원",
                        "여자: 증명서 발급 수수료가 얼마예요?\n남자: 한 통에 500원이에요.",
                        q("증명서 발급 수수료로 알맞은 것을 고르십시오.", List.of(
                                opt("500원", "정답: '한 통에 500원이에요'가 직접적 근거입니다."),
                                opt("1,000원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("무료", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("5,000원", "🔴 빨강 - 대화에 없는 금액입니다.")
                        ), 0, "🔴 글에 없는 다른 금액을 섞어 정확한 숫자를 놓치게 합니다.",
                                "[민원 마인드맵] 핵심 숫자 '500원'을 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.LISTENING, "마트 계산",
                        "남자: 이거 세일 상품 맞아요? 가격표랑 다르게 찍혔는데요.\n여자: 확인해 볼게요. 죄송합니다, 바로 수정해 드릴게요.",
                        q("여자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("가격을 수정해 준다.", "정답: '바로 수정해 드릴게요'가 직접적 근거입니다."),
                                opt("상품을 교환해 준다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("환불해 준다.", "🟢 초록 - 대화에 없는 내용입니다."),
                                opt("사과만 하고 끝낸다.", "🟢 초록 - '수정해 드릴게요'라는 후속 조치를 놓치게 합니다.")
                        ), 0, "🟢 사과 표현만 보고 실제 조치(가격 수정)를 놓치게 합니다.",
                                "[계산대 마인드맵] 문제 제기(가격 다름) → 확인 → 수정. 마지막 문장이 실제 조치입니다.")),
                onePassage(PassageCategory.LISTENING, "사진관/증명사진",
                        "여자: 사진 몇 장 뽑아 드릴까요?\n남자: 여권용 2장, 이력서용 4장 부탁드려요.",
                        q("남자가 요청한 사진 장수로 알맞은 것을 고르십시오.", List.of(
                                opt("여권용 2장, 이력서용 4장", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("여권용 4장, 이력서용 2장", "🔴 빨강 - 두 용도의 장수를 서로 바꾼 오답입니다."),
                                opt("여권용 2장만", "🔴 빨강 - 이력서용을 놓친 오답입니다."),
                                opt("이력서용 4장만", "🔴 빨강 - 여권용을 놓친 오답입니다.")
                        ), 0, "🔴 두 용도의 장수를 서로 바꾸거나 하나만 듣고 놓치게 합니다.",
                                "[사진관 마인드맵] 여권용(2장, 파랑) + 이력서용(4장, 초록). 두 용도를 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "이사",
                        "남자: 이사 갈 집 인터넷 설치는 언제 돼요?\n여자: 이사 당일에는 어렵고 다음 날 오전에 가능해요.",
                        q("인터넷 설치가 가능한 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("이사 다음 날 오전", "정답: '다음 날 오전에 가능해요'가 직접적 근거입니다."),
                                opt("이사 당일", "🔴 빨강 - '어렵고'라는 부정 표현을 놓치게 합니다."),
                                opt("이사 다음 날 오후", "🔴 빨강 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("이사 일주일 후", "🔴 빨강 - 대화에 없는 시점입니다.")
                        ), 0, "🔴 '당일은 어렵다'는 부정 표현을 놓치고 반대로 착각하게 합니다.",
                                "[이사 마인드맵] 당일(❌) → 다음 날 오전(✅). 부정/긍정을 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "아이 돌보기",
                        "여자: 아이 예방접종 언제 하셨어요?\n남자: 지난달에 했는데 다음 접종은 3개월 후래요.",
                        q("다음 예방접종 시기로 알맞은 것을 고르십시오.", List.of(
                                opt("3개월 후", "정답: '다음 접종은 3개월 후래요'가 직접적 근거입니다."),
                                opt("지난달", "🔴 빨강 - 이전 접종 시점을 다음 접종으로 착각하게 합니다."),
                                opt("1개월 후", "🔴 빨강 - 대화에 없는 시점입니다."),
                                opt("6개월 후", "🔴 빨강 - 대화에 없는 시점입니다.")
                        ), 0, "🔴 이전 접종 시점과 다음 접종 시점을 헷갈리게 배치합니다.",
                                "[돌봄 마인드맵] 지난 접종(지난달) → 화살표 → 다음 접종(3개월 후). 화살표 뒤가 정답입니다."))
        );

        List<PassageSeed> reading6th21to30 = List.of(
                onePassage(PassageCategory.READING, "관공서 안내문",
                        "관공서 안내문\n주민센터 민원실은 평일 오전 9시부터 오후 6시까지 운영합니다. 점심시간(12시~1시)에도 운영합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("점심시간에도 민원실을 이용할 수 있다.", "정답: '점심시간에도 운영합니다'가 직접적 근거입니다."),
                                opt("점심시간에는 문을 닫는다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("주말에도 운영한다.", "🔵 파랑 - '평일'과 반대됩니다."),
                                opt("오전에만 운영한다.", "🔵 파랑 - '오후 6시까지'와 반대됩니다.")
                        ), 0, "🔵 운영 요일이나 점심시간 운영 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[민원실 마인드맵] 시간(9시~6시) / 점심시간(운영). 색으로 두 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "마트 전단",
                        "○○마트 주말 특가\n삼겹살 100g당 1,500원\n토요일, 일요일 한정 판매",
                        q("이 전단의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("삼겹살 특가는 주말에만 판매한다.", "정답: '토요일, 일요일 한정 판매'가 직접적 근거입니다."),
                                opt("평일에도 같은 가격이다.", "🔵 파랑 - '한정 판매'와 반대됩니다."),
                                opt("100g당 3,000원이다.", "🔵 파랑 - '1,500원'을 잘못 읽게 만든 오답입니다."),
                                opt("삼겹살만 할인한다는 내용이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 판매 기간이나 가격 숫자를 슬쩍 바꾼 오답을 넣습니다.",
                                "[전단 마인드맵] 상품(삼겹살) / 가격(1,500원) / 기간(주말 한정). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 이번 제주도 여행은 날씨가 좋아서 더 즐거웠어요. 다음엔 가족들과 다시 오고 싶어요.",
                        q("이 여행 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("제주도 여행에 만족했다.", "정답: '더 즐거웠어요', '다시 오고 싶어요'가 직접적 근거입니다."),
                                opt("날씨가 안 좋아서 힘들었다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("다시는 가고 싶지 않다.", "🔵 파랑 - '다시 오고 싶어요'와 반대됩니다."),
                                opt("혼자 여행을 갔다.", "🔵 파랑 - '가족들과'라는 표현과 다릅니다.")
                        ), 0, "🔵 긍정적 후기를 반대로 왜곡한 오답을 넣습니다.",
                                "[후기 마인드맵] 핵심 문장 '즐거웠어요+다시 오고 싶어요'를 색칠하면 만족도가 바로 보입니다.")),
                onePassage(PassageCategory.READING, "병원 예약 안내",
                        "병원 예약 안내\n예약 시간보다 10분 일찍 도착해 주세요. 늦으실 경우 순서가 밀릴 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("예약 시간보다 일찍 도착해야 한다.", "정답: '10분 일찍 도착해 주세요'가 직접적 근거입니다."),
                                opt("늦게 가도 순서에 영향이 없다.", "🔵 파랑 - '순서가 밀릴 수 있다'와 반대됩니다."),
                                opt("예약 시간 정각에 가면 된다.", "🔵 파랑 - '10분 일찍'과 다릅니다."),
                                opt("예약은 필요 없다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 도착 시점(일찍/정각)이나 지각의 영향 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[예약 마인드맵] 핵심 표현 '10분 일찍+순서 밀림'을 색칠하면 안내 내용이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "학원 안내",
                        "학원 안내\n겨울방학 특강이 12월 26일부터 시작합니다. 등록은 선착순 30명입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("등록 인원이 30명으로 제한된다.", "정답: '선착순 30명'이 직접적 근거입니다."),
                                opt("인원 제한이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("특강은 1월에 시작한다.", "🔵 파랑 - '12월 26일'을 다른 달로 바꾼 오답입니다."),
                                opt("여름방학 특강이다.", "🔵 파랑 - '겨울방학'을 다른 계절로 바꾼 오답입니다.")
                        ), 0, "🔵 인원 제한 여부나 시작 시기를 슬쩍 바꾼 오답을 넣습니다.",
                                "[학원안내 마인드맵] 시작일(12/26) / 정원(30명 선착순). 색으로 두 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "관공서 안내문",
                        "관공서 안내문\n분리배출 요일이 변경됩니다. 앞으로 재활용은 매주 화요일과 금요일에 배출해 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("재활용은 화요일과 금요일에 배출한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("재활용은 월요일과 목요일에 배출한다.", "🔵 파랑 - 요일을 슬쩍 바꾼 오답입니다."),
                                opt("배출 요일에 변경이 없다.", "🔵 파랑 - '변경됩니다'와 반대됩니다."),
                                opt("매일 배출 가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 배출 요일을 슬쩍 바꾸거나 변경 여부를 반대로 서술한 오답을 넣습니다.",
                                "[분리배출 마인드맵] 핵심 표현 '화요일+금요일'을 색칠하면 요일이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "마트 전단",
                        "○○마트 창고 정리 세일\n의류 전 품목 40% 할인\n행사 기간: 이번 주 금요일까지",
                        q("이 전단의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("의류는 모두 40% 할인된다.", "정답: '의류 전 품목 40% 할인'이 직접적 근거입니다."),
                                opt("일부 의류만 할인된다.", "🔵 파랑 - '전 품목'과 반대됩니다."),
                                opt("행사는 다음 주까지다.", "🔵 파랑 - '이번 주 금요일까지'와 다릅니다."),
                                opt("할인율은 20%다.", "🔵 파랑 - '40%'를 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 할인 범위나 기간, 할인율 숫자를 슬쩍 바꾼 오답을 넣습니다.",
                                "[전단 마인드맵] 대상(의류 전 품목) / 할인율(40%) / 기간(이번 주 금요일까지). 색으로 세 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 숙소 위치가 별로였어요. 관광지까지 이동하는 데 시간이 너무 오래 걸렸어요.",
                        q("이 여행 후기에서 불만족스러운 점으로 알맞은 것을 고르십시오.", List.of(
                                opt("숙소 위치", "정답: '숙소 위치가 별로였어요'가 직접적 근거입니다."),
                                opt("음식 맛", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("날씨", "🟢 초록 - 글에 없는 내용입니다."),
                                opt("가격", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 언급되지 않은 다른 불만(음식, 가격)을 답처럼 넣습니다.",
                                "[후기 마인드맵] 핵심 문장 '숙소 위치가 별로'를 색칠하면 불만 지점이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "병원 예약 안내",
                        "병원 예약 안내\n예약 변경은 방문 하루 전까지 전화로 가능합니다. 당일 취소는 불가합니다.",
                        q("예약 변경이 가능한 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("방문 하루 전까지", "정답: '방문 하루 전까지 전화로 가능합니다'가 직접적 근거입니다."),
                                opt("당일에도 가능", "🔵 파랑 - '당일 취소는 불가합니다'와 반대됩니다."),
                                opt("일주일 전까지만", "🔵 파랑 - 글에 없는 조건입니다."),
                                opt("변경 자체가 불가능", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 변경 가능 시점을 반대로 바꾸거나 다른 조건으로 왜곡한 오답을 넣습니다.",
                                "[예약 마인드맵] 핵심 표현 '하루 전까지 가능+당일 불가'를 색칠하면 조건이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "학원 안내",
                        "학원 안내\n레벨테스트 결과에 따라 반이 배정됩니다. 테스트는 매주 월요일에 진행됩니다.",
                        q("반 배정 기준으로 알맞은 것을 고르십시오.", List.of(
                                opt("레벨테스트 결과", "정답: '레벨테스트 결과에 따라 반이 배정됩니다'가 직접적 근거입니다."),
                                opt("나이", "🟢 초록 - 글에 없는 기준입니다."),
                                opt("등록 순서", "🟢 초록 - 글에 없는 기준입니다."),
                                opt("희망 반", "🟢 초록 - 글에 없는 기준입니다.")
                        ), 0, "🟢 흔히 예상되는 다른 배정 기준(나이, 순서)을 답처럼 넣습니다.",
                                "[학원안내 마인드맵] 핵심 문장 '레벨테스트 결과에 따라'를 색칠하면 기준이 바로 보입니다."))
        );

        List<PassageSeed> reading6th31to40 = List.of(
                onePassage(PassageCategory.READING, "관공서 안내문",
                        "관공서 안내문\n건강보험료 납부 방법이 자동이체와 지로 두 가지로 안내됩니다. 자동이체 시 소정의 감면 혜택이 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("자동이체를 하면 감면 혜택이 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("지로 납부만 가능하다.", "🔵 파랑 - '두 가지'와 반대됩니다."),
                                opt("감면 혜택이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("지로 납부가 더 저렴하다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 납부 방법의 종류나 감면 혜택 대상을 반대로 바꾼 오답을 넣습니다.",
                                "[보험료 마인드맵] 방법(자동이체/지로) / 혜택(자동이체 시 감면). 색으로 두 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "마트 전단",
                        "○○마트 신선식품 특가\n국내산 계란 한 판 4,500원\n오늘 하루만 특가",
                        q("이 전단의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("계란 특가는 오늘 하루만 적용된다.", "정답: '오늘 하루만 특가'가 직접적 근거입니다."),
                                opt("일주일 내내 특가다.", "🔵 파랑 - '오늘 하루만'과 반대됩니다."),
                                opt("수입산 계란이다.", "🔵 파랑 - '국내산'을 다른 산지로 바꾼 오답입니다."),
                                opt("가격은 9,000원이다.", "🔵 파랑 - '4,500원'을 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 특가 기간, 원산지, 가격 숫자 중 하나를 슬쩍 바꾼 오답을 넣습니다.",
                                "[전단 마인드맵] 상품(계란) / 가격(4,500원) / 기간(오늘만). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 가이드 설명이 정말 자세하고 재미있었어요. 역사에 대해 많이 배울 수 있었습니다.",
                        q("이 여행 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("가이드 설명에 만족했다.", "정답: '자세하고 재미있었어요'가 직접적 근거입니다."),
                                opt("가이드 설명이 지루했다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("가이드가 없었다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("역사에 관심이 없다고 했다.", "🔵 파랑 - '많이 배울 수 있었다'와 반대됩니다.")
                        ), 0, "🔵 긍정적 후기를 반대로 왜곡한 오답을 넣습니다.",
                                "[후기 마인드맵] 핵심 문장 '자세하고 재미있었어요'를 색칠하면 만족도가 바로 보입니다.")),
                multiQ(PassageCategory.READING, "병원 예약 복합 문제",
                        "병원 예약 안내\n초진 환자는 접수 시 문진표를 작성해야 합니다.\n재진 환자는 문진표 작성 없이 바로 진료 가능합니다.\n※ 진료 카드를 꼭 지참해 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초진 환자는 문진표를 작성해야 한다.", "정답: '초진 환자는 문진표를 작성해야 합니다'가 직접적 근거입니다."),
                                opt("재진 환자도 문진표를 작성해야 한다.", "🔵 파랑 - '작성 없이'와 반대됩니다."),
                                opt("모든 환자가 문진표 작성이 필요 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("진료 카드는 필요 없다.", "🔵 파랑 - '꼭 지참해 주세요'와 반대됩니다.")
                        ), 0, "🔵 초진/재진 환자의 절차를 서로 바꾸거나 준비물 필요 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[병원 마인드맵] 초진(문진표 작성, 빨강) / 재진(생략, 초록). 환자 유형과 절차를 색으로 짝지으세요."),
                        q("진료를 받기 위해 반드시 지참해야 하는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("진료 카드", "정답: '진료 카드를 꼭 지참해 주세요'가 직접적 근거입니다."),
                                opt("문진표", "🟣 보라 - 초진 환자만 작성하는 것으로, 지참물이 아니라 현장 작성 서류입니다."),
                                opt("신분증", "🟣 보라 - 안내문에 언급되지 않았습니다."),
                                opt("건강보험증", "🟣 보라 - 안내문에 언급되지 않았습니다.")
                        ), 0, "🟣 현장에서 작성하는 서류(문진표)와 미리 지참해야 하는 것(진료 카드)을 혼동하게 합니다.",
                                "[병원 마인드맵] 지참물(진료 카드) vs 현장 작성(문진표). 색으로 두 종류를 구분하세요.")),
                onePassage(PassageCategory.READING, "학원 안내",
                        "학원 안내\n중간에 결석한 수업은 다른 반 같은 과목 수업으로 보강 가능합니다. 보강은 한 달 이내에 신청해 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("결석 시 다른 반에서 보강을 받을 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("보강은 불가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("보강 신청 기한이 없다.", "🔵 파랑 - '한 달 이내'와 반대됩니다."),
                                opt("보강은 다른 과목으로만 가능하다.", "🔵 파랑 - '같은 과목'과 반대됩니다.")
                        ), 0, "🔵 보강 가능 여부나 신청 기한, 과목 조건을 반대로 바꾼 오답을 넣습니다.",
                                "[학원안내 마인드맵] 조건(같은 과목) / 기한(한 달 이내). 색으로 두 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "관공서 안내문",
                        "관공서 안내문\n도서관 회원증은 무료로 발급됩니다. 신분증만 지참하시면 즉시 발급 가능합니다.",
                        q("도서관 회원증 발급에 필요한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("신분증", "정답: '신분증만 지참하시면'이 직접적 근거입니다."),
                                opt("발급 비용", "🔵 파랑 - '무료로 발급됩니다'와 반대됩니다."),
                                opt("사진", "🔵 파랑 - 글에 없는 준비물입니다."),
                                opt("추천서", "🔵 파랑 - 글에 없는 준비물입니다.")
                        ), 0, "🔵 무료라는 정보를 무시하거나 없는 준비물을 넣은 오답을 만듭니다.",
                                "[회원증 마인드맵] 핵심 표현 '신분증만+무료+즉시'를 색칠하면 조건이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "마트 전단",
                        "○○마트 회원 전용 할인\n회원카드 제시 시 전 품목 5% 추가 할인\n비회원은 해당 없음",
                        q("이 전단의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("회원카드가 있어야 추가 할인을 받는다.", "정답: '비회원은 해당 없음'이 직접적 근거입니다."),
                                opt("비회원도 추가 할인을 받는다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("회원카드 없이도 5% 할인된다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("할인율은 10%다.", "🔵 파랑 - '5%'를 잘못 읽게 만든 오답입니다.")
                        ), 0, "🔵 할인 대상(회원/비회원)이나 할인율 숫자를 반대로 바꾼 오답을 넣습니다.",
                                "[전단 마인드맵] 조건(회원카드) → 혜택(5% 추가 할인). 화살표로 조건과 혜택을 연결하세요.")),
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 교통이 불편해서 걷는 시간이 많았어요. 다음엔 렌터카를 이용해야겠어요.",
                        q("이 여행 후기에서 다음 여행을 위한 계획으로 알맞은 것을 고르십시오.", List.of(
                                opt("렌터카를 이용하겠다.", "정답: '다음엔 렌터카를 이용해야겠어요'가 직접적 근거입니다."),
                                opt("대중교통만 이용하겠다.", "🟢 초록 - 글의 내용과 반대됩니다."),
                                opt("걷기 여행을 계속하겠다.", "🟢 초록 - 글의 내용과 반대됩니다."),
                                opt("여행을 가지 않겠다.", "🟢 초록 - 글에 없는 내용입니다.")
                        ), 0, "🟢 개선하려는 계획(렌터카)을 반대로 착각하게 합니다.",
                                "[후기 마인드맵] 문제(교통 불편) → 계획(렌터카 이용). 화살표로 문제와 해결책을 연결하세요.")),
                onePassage(PassageCategory.READING, "병원 예약 안내",
                        "병원 예약 안내\n온라인 예약 시스템이 오픈되었습니다. 홈페이지에서 24시간 예약이 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("온라인으로 언제든지 예약할 수 있다.", "정답: '24시간 예약이 가능합니다'가 직접적 근거입니다."),
                                opt("전화로만 예약 가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("정해진 시간에만 예약 가능하다.", "🔵 파랑 - '24시간'과 반대됩니다."),
                                opt("예약 시스템이 아직 없다.", "🔵 파랑 - '오픈되었습니다'와 반대됩니다.")
                        ), 0, "🔵 예약 가능 시간이나 시스템 오픈 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[예약 마인드맵] 핵심 표현 '온라인+24시간'을 색칠하면 조건이 바로 보입니다."))
        );

        List<PassageSeed> listening7th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "도서관 이용",
                        "여자: 이 책 대출 기간이 얼마나 돼요?\n남자: 2주예요. 연장은 한 번만 가능해요.",
                        q("이 책의 대출 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2주", "정답: '2주예요'가 직접적 근거입니다."),
                                opt("1주", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("3주", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("한 달", "🔴 빨강 - 대화에 없는 기간입니다.")
                        ), 0, "🔴 글에 없는 다른 기간을 섞어 정확한 숫자를 놓치게 합니다.",
                                "[도서관 마인드맵] 핵심 숫자 '2주'를 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.LISTENING, "인터넷/휴대폰 개통",
                        "남자: 휴대폰을 새로 개통하려고 하는데요.\n여자: 신분증이랑 통신사 변경이면 기존 유심도 필요해요.",
                        q("휴대폰 개통을 위해 필요한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("신분증", "정답: '신분증이랑'이 직접적 근거입니다."),
                                opt("현금만", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("여권", "🔴 빨강 - 대화에 없는 준비물입니다."),
                                opt("영수증", "🔴 빨강 - 대화에 없는 준비물입니다.")
                        ), 0, "🔴 실제 필요한 것 외에 흔히 예상되는 다른 준비물을 답처럼 섞어 놓습니다.",
                                "[개통 마인드맵] 핵심 단어 '신분증'을 색칠하면 필요한 것이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "반려동물",
                        "여자: 강아지 예방접종은 언제 해야 해요?\n남자: 생후 6주부터 시작해서 몇 차례 나눠서 맞춰야 해요.",
                        q("강아지 예방접종을 시작하는 시기로 알맞은 것을 고르십시오.", List.of(
                                opt("생후 6주부터", "정답: '생후 6주부터 시작해서'가 직접적 근거입니다."),
                                opt("생후 1주부터", "🔴 빨강 - 대화에 없는 시기입니다."),
                                opt("생후 1년부터", "🔴 빨강 - 대화에 없는 시기입니다."),
                                opt("생후 12주부터", "🔴 빨강 - 대화에 없는 시기입니다.")
                        ), 0, "🔴 글에 없는 다른 시기를 섞어 정확한 숫자를 놓치게 합니다.",
                                "[반려동물 마인드맵] 핵심 숫자 '생후 6주'를 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.LISTENING, "운동/헬스장",
                        "남자: PT 등록하려고 하는데 몇 회짜리가 있어요?\n여자: 10회, 20회, 30회 패키지가 있어요.",
                        q("PT 패키지 종류로 알맞은 것을 고르십시오.", List.of(
                                opt("10회, 20회, 30회", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("5회, 10회, 15회", "🔴 빨강 - 대화에 없는 숫자입니다."),
                                opt("10회, 30회만", "🔴 빨강 - '20회'를 놓친 오답입니다."),
                                opt("20회만", "🔴 빨강 - 다른 패키지를 놓친 오답입니다.")
                        ), 0, "🔴 세 가지 숫자 중 일부만 듣고 놓치게 합니다.",
                                "[헬스장 마인드맵] 10회 + 20회 + 30회. 세 숫자 모두 다른 색으로 표시해 빠뜨리지 마세요.")),
                withDiagram(onePassage(PassageCategory.LISTENING, "부동산/집 구하기",
                        "여자: 이 원룸 보증금이 얼마예요?\n남자: 보증금 500만 원에 월세 40만 원이에요.",
                        q("이 원룸의 보증금과 월세로 알맞은 것을 고르십시오.", List.of(
                                opt("보증금 500만 원, 월세 40만 원", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("보증금 40만 원, 월세 500만 원", "🔴 빨강 - 보증금과 월세를 서로 바꾼 오답입니다."),
                                opt("보증금 1000만 원, 월세 40만 원", "🔴 빨강 - 보증금 숫자를 슬쩍 바꾼 오답입니다."),
                                opt("보증금 500만 원, 월세 50만 원", "🔴 빨강 - 월세 숫자를 슬쩍 바꾼 오답입니다.")
                        ), 0, "🔴 보증금과 월세 숫자를 서로 바꾸거나 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[부동산 마인드맵] 보증금(500만 원, 파랑) + 월세(40만 원, 초록). 두 숫자를 각각 색칠하세요.")),
                        "<svg viewBox=\"0 0 300 90\" xmlns=\"http://www.w3.org/2000/svg\">"
                                + "<rect x=\"15\" y=\"25\" width=\"120\" height=\"40\" rx=\"8\" fill=\"#dbeafe\" stroke=\"#3b82f6\" stroke-width=\"2\"/>"
                                + "<text x=\"75\" y=\"45\" text-anchor=\"middle\" font-size=\"12\" fill=\"#1d4ed8\">보증금</text>"
                                + "<text x=\"75\" y=\"60\" text-anchor=\"middle\" font-size=\"12\" fill=\"#1d4ed8\">500만원</text>"
                                + "<rect x=\"165\" y=\"25\" width=\"120\" height=\"40\" rx=\"8\" fill=\"#d1fae5\" stroke=\"#10b981\" stroke-width=\"2\"/>"
                                + "<text x=\"225\" y=\"45\" text-anchor=\"middle\" font-size=\"12\" fill=\"#065f46\">월세</text>"
                                + "<text x=\"225\" y=\"60\" text-anchor=\"middle\" font-size=\"12\" fill=\"#065f46\">40만원</text>"
                                + "</svg>"),
                onePassage(PassageCategory.LISTENING, "도서관 이용",
                        "남자: 이 자리는 노트북 사용 가능한 자리인가요?\n여자: 네, 콘센트가 있는 자리는 노트북 이용 가능해요.",
                        q("노트북을 사용할 수 있는 자리의 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("콘센트가 있는 자리", "정답: '콘센트가 있는 자리는 노트북 이용 가능'이 직접적 근거입니다."),
                                opt("창가 자리", "🟢 초록 - 대화에 없는 조건입니다."),
                                opt("조용한 자리", "🟢 초록 - 대화에 없는 조건입니다."),
                                opt("모든 자리", "🟢 초록 - 조건을 무시한 과장된 오답입니다.")
                        ), 0, "🟢 조건('콘센트 있는 자리')을 무시하고 전체로 확대 해석하게 합니다.",
                                "[도서관 마인드맵] 핵심 표현 '콘센트 있는 자리'를 색칠하면 조건이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "인터넷/휴대폰 개통",
                        "여자: 인터넷 설치 기사님이 언제 오세요?\n남자: 내일 오후 2시에서 4시 사이에 방문하실 거예요.",
                        q("인터넷 설치 기사가 방문하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("내일 오후 2시~4시", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("오늘 오후 2시~4시", "🔴 빨강 - '내일'을 '오늘'로 바꾼 오답입니다."),
                                opt("내일 오전 2시~4시", "🔴 빨강 - '오후'를 '오전'으로 바꾼 오답입니다."),
                                opt("내일 오후 4시~6시", "🔴 빨강 - 시간대를 슬쩍 바꾼 오답입니다.")
                        ), 0, "🔴 날짜나 시간대를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[개통 마인드맵] 핵심 표현 '내일 오후 2~4시'를 색칠하면 방문 시간이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "반려동물",
                        "남자: 고양이 사료는 어떤 걸로 바꿔야 할까요?\n여자: 나이대에 맞는 사료로 서서히 바꿔주세요.",
                        q("여자가 남자에게 제안한 방법으로 알맞은 것으로 고르십시오.", List.of(
                                opt("나이대에 맞는 사료로 서서히 바꾸기", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("사료를 한번에 바꾸기", "🔴 빨강 - '서서히'와 반대됩니다."),
                                opt("사료를 바꾸지 않기", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("아무 사료나 주기", "🔴 빨강 - '나이대에 맞는'이라는 조건을 무시한 오답입니다.")
                        ), 0, "🔴 '서서히'라는 방식이나 '나이대에 맞는'이라는 조건을 무시한 오답을 넣습니다.",
                                "[반려동물 마인드맵] 조건(나이대) + 방식(서서히). 두 요소를 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "운동/헬스장",
                        "여자: 러닝머신은 어떻게 써요?\n남자: 이 버튼으로 속도 조절하시고, 빨간 버튼은 비상정지예요.",
                        q("비상정지에 사용하는 버튼의 색으로 알맞은 것을 고르십시오.", List.of(
                                opt("빨간색", "정답: '빨간 버튼은 비상정지'가 직접적 근거입니다."),
                                opt("파란색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("초록색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("노란색", "🔴 빨강 - 대화에 없는 색입니다.")
                        ), 0, "🔴 글에 없는 다른 색을 섞어 정확한 색을 놓치게 합니다.",
                                "[헬스장 마인드맵] 핵심 단어 '빨간 버튼=비상정지'를 색칠하면 답이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "부동산/집 구하기",
                        "남자: 이 집은 반려동물 키울 수 있어요?\n여자: 죄송한데 이 건물은 반려동물 금지예요.",
                        q("이 집에서 반려동물을 키울 수 있는지로 알맞은 것을 고르십시오.", List.of(
                                opt("키울 수 없다.", "정답: '반려동물 금지예요'가 직접적 근거입니다."),
                                opt("키울 수 있다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("소형 동물만 가능하다.", "🔴 빨강 - 대화에 없는 조건입니다."),
                                opt("허락받으면 가능하다.", "🔴 빨강 - 대화에 없는 조건입니다.")
                        ), 0, "🔴 완전 금지를 조건부 허용으로 착각하게 하는 오답을 넣습니다.",
                                "[부동산 마인드맵] 핵심 표현 '반려동물 금지'를 색칠하면 답이 바로 보입니다."))
        );

        List<PassageSeed> listening7th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "도서관 이용",
                        "여자: 이 책이 다른 분한테 대출 중인데 예약할 수 있어요?\n남자: 네, 예약하시면 반납되는 대로 연락드려요.",
                        q("남자가 여자에게 안내한 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("예약하면 반납 시 연락해 준다.", "정답: '예약하시면 반납되는 대로 연락드려요'가 직접적 근거입니다."),
                                opt("예약이 불가능하다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("다른 책을 추천해 준다.", "🔴 빨강 - 대화에 없는 내용입니다."),
                                opt("바로 대출해 준다.", "🔴 빨강 - '대출 중'이라는 상황과 반대됩니다.")
                        ), 0, "🔴 예약 가능 여부와 절차를 반대로 바꾸거나 무시한 오답을 넣습니다.",
                                "[도서관 마인드맵] 예약(가능) → 반납 시 연락. 화살표로 절차를 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "인터넷/휴대폰 개통",
                        "남자: 요금제를 바꾸고 싶은데 위약금이 있나요?\n여자: 2년 약정이 끝나셔서 위약금 없이 변경 가능해요.",
                        q("여자의 대답으로 알 수 있는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("위약금 없이 요금제를 변경할 수 있다.", "정답: '위약금 없이 변경 가능해요'가 직접적 근거입니다."),
                                opt("위약금을 내야 한다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("아직 약정 기간이 남았다.", "🔵 파랑 - '약정이 끝나셔서'와 반대됩니다."),
                                opt("요금제 변경이 불가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 약정 종료 여부나 위약금 발생 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[요금제 마인드맵] 약정 종료(초록) → 위약금 없음(초록). 두 초록 정보를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "반려동물",
                        "여자: 강아지 산책은 하루에 몇 번 시켜야 해요?\n남자: 최소 하루 한 번, 가능하면 두 번이 좋아요.",
                        q("권장되는 강아지 산책 횟수로 알맞은 것을 고르십시오.", List.of(
                                opt("최소 하루 한 번, 가능하면 두 번", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("하루 세 번 이상", "🔴 빨강 - 대화에 없는 횟수입니다."),
                                opt("일주일에 한 번", "🔴 빨강 - 대화에 없는 빈도입니다."),
                                opt("산책은 필요 없다.", "🔴 빨강 - 대화 내용과 반대됩니다.")
                        ), 0, "🔴 글에 없는 다른 횟수를 섞어 정확한 정보를 놓치게 합니다.",
                                "[반려동물 마인드맵] 최소(1번, 파랑) → 권장(2번, 초록). 두 숫자를 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "운동/헬스장",
                        "남자: 헬스장 회원권 환불 가능해요?\n여자: 이용 기간이 지난 만큼 위약금 제외하고 환불해 드려요.",
                        q("헬스장 회원권 환불에 대한 설명으로 알맞은 것을 고르십시오.", List.of(
                                opt("위약금을 제외하고 환불받을 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("환불이 전혀 불가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("전액 환불된다.", "🔵 파랑 - '위약금 제외'와 반대됩니다."),
                                opt("위약금이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 환불 가능 여부나 위약금 존재 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[헬스장 마인드맵] 환불(가능) - 위약금(제외). 두 조건을 함께 색칠해 기억하세요.")),
                onePassage(PassageCategory.LISTENING, "부동산/집 구하기",
                        "여자: 이 집은 관리비가 따로 있어요?\n남자: 네, 월세 외에 관리비 5만 원이 추가돼요.",
                        q("이 집의 관리비로 알맞은 것을 고르십시오.", List.of(
                                opt("5만 원", "정답: '관리비 5만 원이 추가돼요'가 직접적 근거입니다."),
                                opt("월세에 포함", "🔴 빨강 - '월세 외에'라는 표현과 반대됩니다."),
                                opt("10만 원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("관리비 없음", "🔴 빨강 - 대화 내용과 반대됩니다.")
                        ), 0, "🔴 관리비의 포함 여부나 금액을 슬쩍 바꾼 오답을 넣습니다.",
                                "[부동산 마인드맵] 핵심 표현 '월세 외+관리비 5만 원'을 색칠하면 조건이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "도서관 이용",
                        "남자: 스터디룸을 예약하고 싶은데요.\n여자: 최대 4명까지, 2시간 단위로 예약 가능해요.",
                        q("스터디룸 예약 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("최대 4명, 2시간 단위", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("최대 6명, 1시간 단위", "🔴 빨강 - 인원과 시간 단위를 모두 바꾼 오답입니다."),
                                opt("최대 4명, 1시간 단위", "🔴 빨강 - 시간 단위를 슬쩍 바꾼 오답입니다."),
                                opt("최대 2명, 2시간 단위", "🔴 빨강 - 인원을 슬쩍 바꾼 오답입니다.")
                        ), 0, "🔴 인원 수나 시간 단위 중 하나를 슬쩍 바꿔서 헷갈리게 합니다.",
                                "[도서관 마인드맵] 인원(4명, 파랑) + 시간(2시간 단위, 초록). 두 조건을 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "인터넷/휴대폰 개통",
                        "여자: 데이터가 부족한데 추가할 수 있어요?\n남자: 네, 앱에서 데이터 충전하시면 바로 적용돼요.",
                        q("데이터를 추가하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("앱에서 충전하기", "정답: '앱에서 데이터 충전하시면'이 직접적 근거입니다."),
                                opt("매장 방문하기", "🟢 초록 - 대화에 없는 방법입니다."),
                                opt("고객센터 전화하기", "🟢 초록 - 대화에 없는 방법입니다."),
                                opt("문자로 신청하기", "🟢 초록 - 대화에 없는 방법입니다.")
                        ), 0, "🟢 흔히 예상되는 다른 방법(매장, 전화)을 답처럼 넣습니다.",
                                "[데이터 마인드맵] 핵심 표현 '앱에서 충전'을 색칠하면 방법이 바로 보입니다.")),
                onePassage(PassageCategory.LISTENING, "반려동물",
                        "남자: 이 미용실은 고양이도 되나요?\n여자: 죄송한데 저희는 강아지 전문이라 고양이는 안 받아요.",
                        q("이 미용실에서 가능한 서비스로 알맞은 것을 고르십시오.", List.of(
                                opt("강아지 미용만 가능", "정답: '강아지 전문'과 '고양이는 안 받아요'가 직접적 근거입니다."),
                                opt("고양이 미용만 가능", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("강아지와 고양이 모두 가능", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("둘 다 불가능", "🔴 빨강 - '강아지 전문'과 반대됩니다.")
                        ), 0, "🔴 가능한 동물과 불가능한 동물을 서로 바꾸거나 섞은 오답을 넣습니다.",
                                "[미용실 마인드맵] 강아지(✅, 초록) / 고양이(❌, 빨강). 색으로 가능/불가능을 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "운동/헬스장",
                        "여자: 수영장 자유수영 시간이 언제예요?\n남자: 오전 6시부터 9시, 저녁 7시부터 10시예요.",
                        q("자유수영이 가능한 시간대로 알맞은 것을 고르십시오.", List.of(
                                opt("오전 6~9시, 저녁 7~10시", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("오전 6~9시만", "🔴 빨강 - 저녁 시간대를 놓친 오답입니다."),
                                opt("저녁 7~10시만", "🔴 빨강 - 오전 시간대를 놓친 오답입니다."),
                                opt("하루 종일", "🔴 빨강 - 대화 내용과 다른 과장된 오답입니다.")
                        ), 0, "🔴 두 시간대(오전/저녁) 중 하나만 듣고 놓치게 합니다.",
                                "[수영장 마인드맵] 오전(6~9시) + 저녁(7~10시). 두 시간대 모두 색칠해 빠뜨리지 마세요.")),
                onePassage(PassageCategory.LISTENING, "부동산/집 구하기",
                        "남자: 이 집은 지하철역에서 얼마나 걸려요?\n여자: 도보로 10분 정도, 버스 타면 5분이면 가요.",
                        q("도보로 지하철역까지 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("10분", "정답: '도보로 10분 정도'가 직접적 근거입니다."),
                                opt("5분", "🔴 빨강 - 버스 시간을 도보 시간으로 착각하게 합니다."),
                                opt("15분", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("20분", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 도보 시간과 버스 시간을 서로 바꿔서 헷갈리게 합니다.",
                                "[부동산 마인드맵] 도보(10분, 파랑) vs 버스(5분, 초록). 이동 수단과 시간을 색으로 짝지으세요."))
        );

        List<PassageSeed> reading7th21to30 = List.of(
                onePassage(PassageCategory.READING, "도서관 이용안내",
                        "도서관 이용안내\n전자책은 앱에서 무제한 대출 가능합니다. 종이책은 1인 5권까지 대출 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("전자책은 무제한으로 대출할 수 있다.", "정답: '전자책은 앱에서 무제한 대출 가능'이 직접적 근거입니다."),
                                opt("종이책도 무제한이다.", "🔵 파랑 - '1인 5권까지'와 반대됩니다."),
                                opt("전자책은 5권까지다.", "🔵 파랑 - 종이책 조건을 전자책에 잘못 적용한 오답입니다."),
                                opt("전자책은 대출이 안 된다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 전자책과 종이책의 대출 조건을 서로 바꾼 오답을 넣습니다.",
                                "[도서관 마인드맵] 전자책(무제한, 파랑) / 종이책(5권, 초록). 색으로 두 조건을 구분하세요.")),
                onePassage(PassageCategory.READING, "통신사 안내문",
                        "통신사 안내문\n이번 달부터 데이터 요금제가 개편됩니다. 기존 고객은 6개월간 기존 요금제를 유지할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("기존 고객은 6개월간 기존 요금제를 쓸 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("기존 고객도 즉시 새 요금제로 바뀐다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("신규 고객만 요금제 개편의 영향을 받는다.", "🔵 파랑 - 글에 없는 내용입니다."),
                                opt("유지 기간은 1년이다.", "🔵 파랑 - '6개월'을 다른 기간으로 바꾼 오답입니다.")
                        ), 0, "🔵 유지 기간이나 적용 대상을 슬쩍 바꾼 오답을 넣습니다.",
                                "[통신사 마인드맵] 핵심 표현 '기존 고객+6개월 유지'를 색칠하면 조건이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "반려동물 관련 공지",
                        "반려동물 관련 공지\n아파트 단지 내에서는 반려동물 목줄과 배변봉투 지참이 필수입니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("목줄과 배변봉투를 모두 지참해야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("목줄만 있으면 된다.", "🔵 파랑 - '배변봉투'도 필수인데 하나만 언급한 오답입니다."),
                                opt("배변봉투만 있으면 된다.", "🔵 파랑 - '목줄'도 필수인데 하나만 언급한 오답입니다."),
                                opt("둘 다 필요 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 두 필수 항목(목줄, 배변봉투) 중 하나만 언급해 헷갈리게 합니다.",
                                "[반려동물 마인드맵] 목줄(파랑) + 배변봉투(초록). 두 항목 모두 필수임을 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "운동시설 이용안내",
                        "운동시설 이용안내\n체육관 이용은 회원증 태그로만 입장 가능합니다. 방문증은 발급되지 않습니다.",
                        q("체육관 입장 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("회원증 태그", "정답: '회원증 태그로만 입장 가능'이 직접적 근거입니다."),
                                opt("방문증 발급", "🔵 파랑 - '방문증은 발급되지 않습니다'와 반대됩니다."),
                                opt("신분증 제시", "🔵 파랑 - 글에 없는 방법입니다."),
                                opt("아무나 입장 가능", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 입장 방법을 다른 방법으로 바꾸거나 조건 없이 가능하다고 왜곡한 오답을 넣습니다.",
                                "[체육관 마인드맵] 핵심 표현 '회원증 태그로만'을 색칠하면 입장 방법이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "부동산 매물 정보",
                        "부동산 매물 정보\n투룸, 전세 1억 5천만 원, 역세권, 즉시 입주 가능",
                        q("이 매물 정보의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("즉시 입주가 가능하다.", "정답: '즉시 입주 가능'이 직접적 근거입니다."),
                                opt("입주까지 한 달 걸린다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("월세 매물이다.", "🔵 파랑 - '전세'를 다른 형태로 바꾼 오답입니다."),
                                opt("원룸 매물이다.", "🔵 파랑 - '투룸'을 다른 형태로 바꾼 오답입니다.")
                        ), 0, "🔵 매물 유형이나 입주 가능 시점을 슬쩍 바꾼 오답을 넣습니다.",
                                "[부동산 마인드맵] 유형(투룸) / 금액(전세 1.5억) / 입주(즉시). 항목마다 색을 다르게 정리하세요.")),
                onePassage(PassageCategory.READING, "도서관 이용안내",
                        "도서관 이용안내\n반납이 연체되면 연체 일수만큼 대출이 정지됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("연체하면 연체 일수만큼 대출이 정지된다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("연체해도 불이익이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("연체하면 영구적으로 대출이 정지된다.", "🔵 파랑 - '연체 일수만큼'과 다른 과장된 오답입니다."),
                                opt("연체료를 내야 한다.", "🔵 파랑 - 글에 없는 내용입니다.")
                        ), 0, "🔵 불이익 여부를 반대로 바꾸거나 정지 기간을 과장한 오답을 넣습니다.",
                                "[도서관 마인드맵] 핵심 표현 '연체 일수만큼 대출 정지'를 색칠하면 규정이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "통신사 안내문",
                        "통신사 안내문\n와이파이 공유기 무상 임대 서비스가 종료됩니다. 이후에는 구매만 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("공유기 무상 임대가 끝난다.", "정답: '무상 임대 서비스가 종료됩니다'가 직접적 근거입니다."),
                                opt("공유기 임대가 계속된다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("공유기를 무료로 준다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("구매도 불가능하다.", "🔵 파랑 - '구매만 가능합니다'와 반대됩니다.")
                        ), 0, "🔵 임대 종료 여부나 구매 가능 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[통신사 마인드맵] 임대(종료, 빨강) → 구매(가능, 초록). 화살표로 변화를 표시하세요.")),
                onePassage(PassageCategory.READING, "반려동물 관련 공지",
                        "반려동물 관련 공지\n엘리베이터에서는 반려동물을 안거나 케이지에 넣어 이동해 주세요.",
                        q("엘리베이터 이용 시 지켜야 할 규칙으로 알맞은 것을 고르십시오.", List.of(
                                opt("안거나 케이지에 넣어야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("목줄만 하면 된다.", "🔵 파랑 - 글에 없는 조건입니다."),
                                opt("자유롭게 걸어 다녀도 된다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("엘리베이터 이용이 금지된다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 규칙 내용을 다른 조건으로 바꾸거나 완전 금지로 과장한 오답을 넣습니다.",
                                "[반려동물 마인드맵] 핵심 표현 '안거나+케이지'를 색칠하면 규칙이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "운동시설 이용안내",
                        "운동시설 이용안내\n샤워실 수건은 개인 지참이 원칙이나, 데스크에서 대여도 가능합니다(1회 1,000원).",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("수건을 대여할 수도 있다.", "정답: '데스크에서 대여도 가능합니다'가 직접적 근거입니다."),
                                opt("수건은 무조건 개인 지참해야 한다.", "🔵 파랑 - '대여도 가능'과 반대됩니다."),
                                opt("수건 대여는 무료다.", "🔵 파랑 - '1,000원'과 반대됩니다."),
                                opt("수건 대여가 불가능하다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 지참 원칙을 절대적 규칙으로 과장하거나 대여 가능 여부를 반대로 바꾼 오답을 넣습니다.",
                                "[운동시설 마인드맵] 원칙(개인 지참) / 대안(대여 가능, 1,000원). 색으로 두 옵션을 구분하세요.")),
                onePassage(PassageCategory.READING, "부동산 매물 정보",
                        "부동산 매물 정보\n오피스텔, 월세 보증금 300만 원에 월 55만 원, 관리비 별도",
                        q("이 매물의 월세로 알맞은 것을 고르십시오.", List.of(
                                opt("55만 원", "정답: '월 55만 원'이 직접적 근거입니다."),
                                opt("300만 원", "🔴 빨강 - 보증금을 월세로 착각하게 합니다."),
                                opt("35만 원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("관리비 포함 55만 원", "🔴 빨강 - '관리비 별도'와 반대됩니다.")
                        ), 0, "🔴 보증금과 월세를 헷갈리게 하거나 관리비 포함 여부를 반대로 서술한 오답을 넣습니다.",
                                "[부동산 마인드맵] 보증금(300만 원) / 월세(55만 원) / 관리비(별도). 색으로 세 항목을 구분하세요."))
        );

        List<PassageSeed> reading7th31to40 = List.of(
                onePassage(PassageCategory.READING, "도서관 이용안내",
                        "도서관 이용안내\n노트북 대여 서비스가 신설되었습니다. 1인 1대, 하루 최대 4시간 이용 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("노트북은 하루 최대 4시간까지 빌릴 수 있다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("한 사람이 두 대까지 빌릴 수 있다.", "🔵 파랑 - '1인 1대'와 반대됩니다."),
                                opt("이용 시간 제한이 없다.", "🔵 파랑 - '최대 4시간'과 반대됩니다."),
                                opt("노트북 대여는 예전부터 있었다.", "🔵 파랑 - '신설되었습니다'와 반대됩니다.")
                        ), 0, "🔵 대여 대수, 시간 제한, 서비스 시작 시점 중 하나를 반대로 바꾼 오답을 넣습니다.",
                                "[도서관 마인드맵] 대수(1인 1대) / 시간(최대 4시간). 색으로 두 조건을 구분하세요.")),
                onePassage(PassageCategory.READING, "통신사 안내문",
                        "통신사 안내문\n해외 로밍 서비스는 출국 전 앱에서 미리 신청해야 합니다. 공항 신청은 불가능합니다.",
                        q("해외 로밍 서비스 신청 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("출국 전 앱에서 신청", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("공항에서 신청", "🔵 파랑 - '공항 신청은 불가능합니다'와 반대됩니다."),
                                opt("귀국 후 신청", "🔵 파랑 - 글에 없는 내용입니다."),
                                opt("신청 없이 자동 적용", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 신청 장소나 시점을 반대로 바꾼 오답을 넣습니다.",
                                "[로밍 마인드맵] 핵심 표현 '출국 전+앱에서'를 색칠하면 신청 방법이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "반려동물 관련 공지",
                        "반려동물 관련 공지\n공용 정원에서는 반려동물의 배변을 즉시 처리해 주시기 바랍니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("배변은 즉시 처리해야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("배변 처리는 나중에 해도 된다.", "🔵 파랑 - '즉시'와 반대됩니다."),
                                opt("공용 정원 출입이 금지된다.", "🔵 파랑 - 글에 없는 내용입니다."),
                                opt("배변 처리 규정이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 처리 시점('즉시')을 무시하거나 출입 금지처럼 과장한 오답을 넣습니다.",
                                "[반려동물 마인드맵] 핵심 표현 '즉시 처리'를 색칠하면 규칙이 바로 보입니다.")),
                multiQ(PassageCategory.READING, "운동시설 복합 문제",
                        "운동시설 이용안내\n단체 강습은 최소 5명 이상 신청 시 개설됩니다.\n개인 강습은 인원 제한 없이 신청 가능합니다.\n※ 강습 시작 3일 전까지 신청해 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("단체 강습은 5명 이상이어야 개설된다.", "정답: '최소 5명 이상 신청 시 개설'이 직접적 근거입니다."),
                                opt("단체 강습은 인원 제한이 없다.", "🔵 파랑 - 개인 강습 조건을 단체 강습에 잘못 적용한 오답입니다."),
                                opt("개인 강습은 5명이 있어야 한다.", "🔵 파랑 - 단체 강습 조건을 개인 강습에 잘못 적용한 오답입니다."),
                                opt("신청 기한이 없다.", "🔵 파랑 - '3일 전까지'와 반대됩니다.")
                        ), 0, "🔵 단체 강습과 개인 강습의 조건을 서로 바꿔서 헷갈리게 합니다.",
                                "[강습 마인드맵] 단체(5명 이상, 빨강) / 개인(제한 없음, 초록). 색으로 두 유형을 구분하세요."),
                        q("강습을 신청해야 하는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("강습 시작 3일 전까지", "정답: '강습 시작 3일 전까지 신청해 주세요'가 직접적 근거입니다."),
                                opt("강습 당일까지", "🟣 보라 - '3일 전까지'와 반대됩니다."),
                                opt("강습 시작 일주일 전까지", "🟣 보라 - 안내문에 없는 기한입니다."),
                                opt("신청 기한이 없다.", "🟣 보라 - 글의 내용과 반대됩니다.")
                        ), 0, "🟣 신청 기한 숫자를 슬쩍 바꾸거나 없는 것처럼 왜곡한 오답을 넣습니다.",
                                "[강습 마인드맵] 핵심 숫자 '3일 전'을 색칠하면 신청 기한이 바로 보입니다.")),
                onePassage(PassageCategory.READING, "부동산 매물 정보",
                        "부동산 매물 정보\n원룸, 월세, 보증금 없이 월 45만 원, 풀옵션(냉장고·세탁기·에어컨 포함)",
                        q("이 매물의 특징으로 맞는 것을 고르십시오.", List.of(
                                opt("보증금 없이 월세만 낸다.", "정답: '보증금 없이 월 45만 원'이 직접적 근거입니다."),
                                opt("보증금이 필요하다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("가전제품이 없다.", "🔵 파랑 - '풀옵션'과 반대됩니다."),
                                opt("전세 매물이다.", "🔵 파랑 - '월세'를 다른 형태로 바꾼 오답입니다.")
                        ), 0, "🔵 보증금 유무나 옵션 포함 여부, 계약 형태를 반대로 바꾼 오답을 넣습니다.",
                                "[부동산 마인드맵] 보증금(없음) / 월세(45만 원) / 옵션(풀옵션). 색으로 세 항목을 구분하세요.")),
                onePassage(PassageCategory.READING, "도서관 이용안내",
                        "도서관 이용안내\n분실한 책은 정가의 1.5배를 배상해야 합니다.",
                        q("책을 분실했을 때 배상 금액으로 알맞은 것을 고르십시오.", List.of(
                                opt("정가의 1.5배", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("정가와 동일한 금액", "🔴 빨강 - '1.5배'를 잘못 읽게 만든 오답입니다."),
                                opt("정가의 2배", "🔴 빨강 - 대화에 없는 배율입니다."),
                                opt("배상하지 않아도 된다.", "🔴 빨강 - 글의 내용과 반대됩니다.")
                        ), 0, "🔴 배율 숫자를 슬쩍 바꾸거나 배상 의무를 무시한 오답을 넣습니다.",
                                "[도서관 마인드맵] 핵심 표현 '정가의 1.5배'를 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.READING, "통신사 안내문",
                        "통신사 안내문\n멤버십 등급이 높을수록 통신사 제휴 매장 할인율이 커집니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("멤버십 등급이 높으면 할인율이 커진다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("등급과 할인율은 관계없다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("등급이 높으면 할인율이 작아진다.", "🔵 파랑 - 글의 내용과 반대됩니다."),
                                opt("모든 등급의 할인율이 같다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 등급과 할인율의 비례 관계를 반대나 무관하게 왜곡한 오답을 넣습니다.",
                                "[멤버십 마인드맵] 등급 ↑ → 할인율 ↑. 화살표 방향이 같은 방향임을 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "반려동물 관련 공지",
                        "반려동물 관련 공지\n밤 10시 이후에는 반려동물이 짖지 않도록 각별히 신경 써 주세요.",
                        q("이 공지에서 요청하는 시간대로 알맞은 것을 고르십시오.", List.of(
                                opt("밤 10시 이후", "정답: '밤 10시 이후에는'이 직접적 근거입니다."),
                                opt("아침 10시 이후", "🔴 빨강 - '밤'을 '아침'으로 바꾼 오답입니다."),
                                opt("낮 12시 이후", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("하루 종일", "🔴 빨강 - 대화 내용과 다른 과장된 오답입니다.")
                        ), 0, "🔴 시간대(밤/아침)를 슬쩍 바꾸거나 하루 종일로 과장한 오답을 넣습니다.",
                                "[반려동물 마인드맵] 핵심 표현 '밤 10시 이후'를 빨간색으로 동그라미 치세요.")),
                onePassage(PassageCategory.READING, "운동시설 이용안내",
                        "운동시설 이용안내\n사물함은 무료로 이용 가능하나, 개인 자물쇠를 지참해야 합니다.",
                        q("사물함 이용에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("무료지만 자물쇠는 직접 가져와야 한다.", "정답: 글에 그대로 명시되어 있습니다."),
                                opt("사물함 이용에 요금이 든다.", "🔵 파랑 - '무료'와 반대됩니다."),
                                opt("자물쇠를 대여해 준다.", "🔵 파랑 - '개인 자물쇠 지참'과 반대됩니다."),
                                opt("사물함이 없다.", "🔵 파랑 - 글의 내용과 반대됩니다.")
                        ), 0, "🔵 요금 여부나 자물쇠 준비 방법을 반대로 바꾼 오답을 넣습니다.",
                                "[사물함 마인드맵] 요금(무료, 초록) / 자물쇠(개인 지참, 파랑). 색으로 두 조건을 구분하세요."))
        );

        return new WeekSeed("1~2급 컬러맵 기초 다지기",
                "TOPIK I 수준의 듣기·읽기 기본기를 색깔 코딩과 마인드맵으로 시각화하며 다진다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(상황 응답, 행동/장소 파악, 세부 정보, 목적/주제, 흐름 추론) + 읽기 20(핵심 정보, 실용문, 목적 추론, 문장 순서, 공통 주제). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening1to10, listening11to20, reading21to30, reading31to40)),
                        day("2차(40문항) - 듣기 20(직장 생활, 가족 행사, 교통/이동, 계절/자연, 취미/여가) + 읽기 20(공공장소 안내문, 일기/편지, 여행/관광, 건강/운동, 한국 문화/관습). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening2nd1to10, listening2nd11to20, reading2nd21to30, reading2nd31to40)),
                        day("3차(40문항) - 듣기 20(전화 대화, 약속/스케줄, 쇼핑/가격, 음식 주문, 길 묻기) + 읽기 20(가정통신문, 할인 쿠폰, 전단지, 공지사항, SMS/편지/SNS). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening3rd1to10, listening3rd11to20, reading3rd21to30, reading3rd31to40)),
                        day("4차(40문항) - 듣기 20(초대/약속 변경, 물건 찾기/분실, 병원/약국, 학교생활, 대중교통 이용) + 읽기 20(초대장, 메뉴판/식당안내, 일정표, 게시판 댓글, 설명서/사용법). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening4th1to10, listening4th11to20, reading4th21to30, reading4th31to40)),
                        day("5차(40문항) - 듣기 20(우체국/택배, 은행 업무, 미용실, 날씨 예보, 회의/발표) + 읽기 20(설문조사, 초대 답장, 분실물 안내, 채용 공고, 이용후기). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening5th1to10, listening5th11to20, reading5th21to30, reading5th31to40)),
                        day("6차(40문항) - 듣기 20(관공서 민원, 마트 계산, 사진관/증명사진, 이사, 아이 돌보기) + 읽기 20(관공서 안내문, 마트 전단, 여행 후기, 병원 예약 안내, 학원 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening6th1to10, listening6th11to20, reading6th21to30, reading6th31to40)),
                        day("7차(40문항) - 듣기 20(도서관 이용, 인터넷/휴대폰 개통, 반려동물, 운동/헬스장, 부동산/집 구하기) + 읽기 20(도서관 이용안내, 통신사 안내문, 반려동물 관련 공지, 운동시설 이용안내, 부동산 매물 정보). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening7th1to10, listening7th11to20, reading7th21to30, reading7th31to40))
                ));
    }

    // ===================== WEEK 2: 1~2급 컬러맵 심화(일상 확장) =====================

    private static final String WEEK2_ANSWER_NOTE_TEMPLATE = """
            [🎨 오답 노트 템플릿 - WEEK2용]
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

    private WeekSeed week2() {
        List<PassageSeed> listening1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "감정 표현",
                        "여자: 시험 결과가 나왔는데 너무 기뻐요!\n남자: 정말요? 축하해요!",
                        q("여자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("기쁘다", "정답: '너무 기뻐요'라는 말에 감정이 직접 드러납니다."),
                                opt("슬프다", "🟢 초록 - 대화 속 감정과 반대되는 오답입니다."),
                                opt("화가 나다", "🟢 초록 - 언급되지 않은 감정입니다."),
                                opt("무섭다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 감정을 나타내는 핵심 단어를 반대되는 감정으로 바꿔 오답을 만듭니다.",
                                "[감정 마인드맵] 중심 = 시험 결과, 가지 = 기쁨. 감정 단어에 형광펜을 칠해두세요.")),
                onePassage(PassageCategory.LISTENING, "의견 제시",
                        "남자: 저는 이 영화가 정말 재미있었다고 생각해요.\n여자: 저도 그렇게 생각해요.",
                        q("두 사람의 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("영화가 재미있었다.", "정답: 남자의 의견에 여자도 동의하고 있습니다."),
                                opt("영화가 지루했다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("영화를 안 봤다.", "🟢 초록 - 대화 내용과 관련이 없습니다."),
                                opt("영화가 너무 길었다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 '그렇게 생각해요'라는 동의 표현을 반대 의견으로 착각하게 합니다.",
                                "[의견 마인드맵] 남자 의견 → 화살표(동의) → 여자 의견. 같은 방향이면 같은 결론입니다.")),
                onePassage(PassageCategory.LISTENING, "비교/선택",
                        "여자: 이 가방하고 저 가방 중에 어느 게 나아요?\n남자: 저는 이 가방이 더 실용적인 것 같아요.",
                        q("남자가 선택한 가방으로 알맞은 것을 고르십시오.", List.of(
                                opt("이 가방", "정답: 남자는 '이 가방'이 더 실용적이라고 답했습니다."),
                                opt("저 가방", "🔴 빨강 - 여자가 먼저 언급한 것과 헷갈리게 하는 오답입니다."),
                                opt("두 가방 다", "🔴 빨강 - 대화에 없는 선택입니다."),
                                opt("아무 가방도 아니다", "🔴 빨강 - 대화 내용과 반대됩니다.")
                        ), 0, "🔴 '이것/저것' 지시어가 헷갈리도록 두 대상을 연속 배치합니다.",
                                "[비교 마인드맵] 이 가방(실용적, 정답) ↔ 저 가방. 지시어에 색을 칠해 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "부탁/거절",
                        "남자: 이번 주말에 이사하는데 좀 도와줄 수 있어요?\n여자: 미안한데 이번 주말에는 선약이 있어서 어려울 것 같아요.",
                        q("여자의 대답으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부탁을 거절했다.", "정답: '어려울 것 같아요'는 완곡한 거절 표현입니다."),
                                opt("부탁을 수락했다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("다른 사람을 추천했다.", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("시간을 다시 정하자고 했다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 완곡한 거절 표현(어려울 것 같아요)을 수락으로 착각하게 합니다.",
                                "[대답 마인드맵] 부탁 → 미안하다+이유 → 거절. 이 조합이 나오면 항상 거절입니다.")),
                onePassage(PassageCategory.LISTENING, "위로/격려",
                        "여자: 시험에 떨어져서 너무 속상해요.\n남자: 다음에 더 잘할 수 있을 거예요. 힘내세요.",
                        q("남자가 여자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("위로하고 있다.", "정답: '힘내세요'는 대표적인 위로 표현입니다."),
                                opt("축하하고 있다.", "🟢 초록 - 대화 상황(속상함)과 반대되는 오답입니다."),
                                opt("꾸짖고 있다.", "🟢 초록 - 대화 분위기와 맞지 않습니다."),
                                opt("부탁하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🟢 격려 표현을 무관한 다른 말하기 목적(부탁 등)으로 헷갈리게 합니다.",
                                "[위로 마인드맵] 속상함(여자) → 화살표 → 힘내라(남자). 감정 반응 방향을 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "감정 표현",
                        "남자: 갑자기 소나기가 쏟아져서 옷이 다 젖었어요.\n여자: 어머, 짜증 났겠어요.",
                        q("남자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("짜증 나다", "정답: 여자의 반응과 상황을 통해 남자의 짜증을 알 수 있습니다."),
                                opt("행복하다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("편안하다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("자랑스럽다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 부정적 상황(옷이 젖음)을 긍정적 감정과 짝지어 헷갈리게 합니다.",
                                "[감정 마인드맵] 상황(비 맞음, 부정) → 감정(짜증, 부정). 같은 색 계열끼리 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "의견 제시",
                        "여자: 저는 이 계획에 반대해요. 시간이 너무 부족해요.\n남자: 음, 듣고 보니 그러네요.",
                        q("여자의 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("계획에 반대한다.", "정답: '반대해요'라고 직접 말했습니다."),
                                opt("계획에 찬성한다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("계획을 모른다.", "🟢 초록 - 대화 내용과 맞지 않습니다."),
                                opt("계획을 취소했다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 반대 의견을 남자의 동조 반응과 섞어 찬성처럼 보이게 합니다.",
                                "[의견 마인드맵] 여자(반대) → 이유(시간 부족) → 남자(공감). 첫 화자의 입장이 핵심입니다.")),
                onePassage(PassageCategory.LISTENING, "비교/선택",
                        "남자: 빨간색하고 파란색 중에 뭐가 더 예뻐요?\n여자: 저는 파란색이 더 마음에 들어요.",
                        q("여자가 선택한 색으로 알맞은 것을 고르십시오.", List.of(
                                opt("파란색", "정답: '파란색이 더 마음에 들어요'라고 답했습니다."),
                                opt("빨간색", "🔴 빨강 - 먼저 언급된 색과 헷갈리게 하는 오답입니다."),
                                opt("노란색", "🔴 빨강 - 대화에 없는 색입니다."),
                                opt("초록색", "🔴 빨강 - 대화에 없는 색입니다.")
                        ), 0, "🔴 색깔 두 개를 나열한 뒤 나중에 말한 색을 놓치게 합니다.",
                                "[선택 마인드맵] 빨간색 ↔ 파란색(정답). 마지막에 언급된 선호 대상이 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "부탁/거절",
                        "여자: 내일 발표 자료 좀 검토해 주실 수 있어요?\n남자: 네, 오늘 저녁에 보내 주세요.",
                        q("남자의 대답으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부탁을 수락했다.", "정답: '네'라고 답하며 구체적인 방법을 알려줬습니다."),
                                opt("부탁을 거절했다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("다른 사람에게 부탁하라고 했다.", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("시간이 없다고 했다.", "🟢 초록 - 대화 내용과 반대됩니다.")
                        ), 0, "🟢 수락 뒤에 붙는 구체적 요청(자료 보내기)을 거절 신호로 착각하게 합니다.",
                                "[대답 마인드맵] 부탁 → 네+구체적 방법 → 수락. '네'로 시작하면 대부분 수락입니다.")),
                onePassage(PassageCategory.LISTENING, "위로/격려",
                        "남자: 발표를 망친 것 같아서 너무 창피해요.\n여자: 그렇게 나쁘지 않았어요. 잘하셨어요.",
                        q("여자가 남자에게 하는 말의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("위로하려고", "정답: 창피해하는 남자를 안심시키는 위로의 말입니다."),
                                opt("비판하려고", "🟢 초록 - 대화 분위기와 반대되는 오답입니다."),
                                opt("자랑하려고", "🟢 초록 - 언급되지 않은 목적입니다."),
                                opt("놀리려고", "🟢 초록 - 대화 분위기와 맞지 않습니다.")
                        ), 0, "🟢 위로의 말을 무관한 부정적 목적(비판, 놀림)으로 착각하게 합니다.",
                                "[위로 마인드맵] 창피함(남자) → 화살표 → 잘했다(여자, 위로). 감정과 반응이 짝을 이룹니다."))
        );

        List<PassageSeed> listening11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "감정 표현",
                        "여자: 오랜만에 옛 친구를 만나서 정말 반가웠어요.\n남자: 좋았겠어요.",
                        q("여자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("반갑다", "정답: '정말 반가웠어요'라고 직접 표현했습니다."),
                                opt("지루하다", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("불안하다", "🟢 초록 - 언급되지 않은 감정입니다."),
                                opt("피곤하다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 긍정적인 재회 상황을 무관한 감정으로 바꿔치기합니다.",
                                "[감정 마인드맵] 상황(오랜만에 만남) → 감정(반가움). 상황과 감정을 같은 색으로 이으세요.")),
                onePassage(PassageCategory.LISTENING, "의견 제시",
                        "남자: 저는 이 제품보다 저 제품이 더 품질이 좋다고 생각해요.\n여자: 저는 잘 모르겠어요.",
                        q("남자의 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("저 제품이 더 좋다.", "정답: 남자는 '저 제품'의 품질이 더 좋다고 말했습니다."),
                                opt("이 제품이 더 좋다.", "🔴 빨강 - 비교 대상을 반대로 바꾼 오답입니다."),
                                opt("두 제품 다 나쁘다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("여자의 의견과 같다.", "🔴 빨강 - 여자는 '잘 모르겠다'고 했으므로 틀립니다.")
                        ), 0, "🔴 '이것/저것' 비교 대상을 뒤바꿔 정답처럼 보이게 합니다.",
                                "[의견 마인드맵] 이 제품 ↔ 저 제품(정답, 품질 좋음). 지시어마다 다른 색을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "비교/선택",
                        "여자: 기차하고 버스 중에 뭘 타고 갈까요?\n남자: 기차가 더 빠르니까 기차로 가요.",
                        q("두 사람이 타기로 한 교통수단으로 알맞은 것을 고르십시오.", List.of(
                                opt("기차", "정답: 남자가 '기차로 가요'라고 제안했습니다."),
                                opt("버스", "🔴 빨강 - 먼저 언급된 교통수단과 헷갈리게 하는 오답입니다."),
                                opt("지하철", "🔴 빨강 - 대화에 없는 교통수단입니다."),
                                opt("비행기", "🔴 빨강 - 대화에 없는 교통수단입니다.")
                        ), 0, "🔴 이유(빠르다)와 최종 선택을 분리해 헷갈리게 배치합니다.",
                                "[선택 마인드맵] 이유(빠름) → 화살표 → 선택(기차). 이유 뒤에 나온 대상이 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "부탁/거절",
                        "남자: 제 컴퓨터 좀 봐 주실 수 있어요? 갑자기 안 켜져요.\n여자: 지금은 회의 중이라 이따가 봐 드릴게요.",
                        q("여자의 대답으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("나중에 도와주겠다고 했다.", "정답: '이따가 봐 드릴게요'는 미루어 수락하는 표현입니다."),
                                opt("바로 도와주겠다고 했다.", "🔴 빨강 - '이따가'라는 시간 표현을 놓치게 합니다."),
                                opt("도와줄 수 없다고 했다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("다른 사람을 불러 주겠다고 했다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 '이따가'라는 시간 부사를 놓치고 '지금 바로'로 착각하게 합니다.",
                                "[시간 마인드맵] 지금(회의 중) → 화살표 → 이따가(도움). 시간 순서를 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "위로/격려",
                        "여자: 이사한 지 얼마 안 돼서 아직 적응이 안 돼요.\n남자: 시간이 지나면 괜찮아질 거예요.",
                        q("남자가 여자에게 하는 말의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("격려하려고", "정답: '괜찮아질 거예요'는 대표적인 격려 표현입니다."),
                                opt("걱정을 키우려고", "🟢 초록 - 대화 목적과 반대되는 오답입니다."),
                                opt("이사를 권하려고", "🟢 초록 - 언급되지 않은 목적입니다."),
                                opt("이유를 물으려고", "🟢 초록 - 대화 내용과 맞지 않습니다.")
                        ), 0, "🟢 격려의 말을 무관한 목적(질문, 걱정 증폭)으로 바꿔 오답을 만듭니다.",
                                "[격려 마인드맵] 힘든 상황(적응 안 됨) → 화살표 → 격려(괜찮아질 것). 방향이 항상 긍정으로 흐릅니다.")),
                onePassage(PassageCategory.LISTENING, "감정 표현",
                        "남자: 지갑을 잃어버려서 너무 당황스러워요.\n여자: 저런, 큰일이네요.",
                        q("남자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("당황스럽다", "정답: '너무 당황스러워요'라고 직접 표현했습니다."),
                                opt("기쁘다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("감사하다", "🟢 초록 - 언급되지 않은 감정입니다."),
                                opt("자랑스럽다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 부정적 상황(지갑 분실)에 긍정적 감정을 짝지어 헷갈리게 합니다.",
                                "[감정 마인드맵] 상황(분실) → 감정(당황). 부정 상황엔 부정 감정, 색을 통일하세요.")),
                onePassage(PassageCategory.LISTENING, "의견 제시",
                        "여자: 저는 이 디자인이 너무 복잡하다고 생각해요.\n남자: 저도 동의해요. 좀 더 단순했으면 좋겠어요.",
                        q("두 사람의 공통된 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("디자인이 복잡하다.", "정답: 여자의 의견에 남자도 동의했습니다."),
                                opt("디자인이 단순하다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("디자인이 마음에 든다.", "🟢 초록 - 대화 내용과 반대됩니다."),
                                opt("디자인을 안 봤다.", "🟢 초록 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🟢 '동의해요' 뒤에 이어지는 추가 의견까지 반대로 왜곡합니다.",
                                "[의견 마인드맵] 여자(복잡함) → 화살표(동의) → 남자(같은 의견). 동의 표현 뒤도 같은 색입니다.")),
                onePassage(PassageCategory.LISTENING, "비교/선택",
                        "남자: 여행은 봄이랑 가을 중에 언제가 좋을까요?\n여자: 저는 날씨가 선선한 가을이 좋을 것 같아요.",
                        q("여자가 선호하는 계절로 알맞은 것을 고르십시오.", List.of(
                                opt("가을", "정답: '가을이 좋을 것 같아요'라고 직접 말했습니다."),
                                opt("봄", "🔴 빨강 - 먼저 언급된 계절과 헷갈리게 하는 오답입니다."),
                                opt("여름", "🔴 빨강 - 대화에 없는 계절입니다."),
                                opt("겨울", "🔴 빨강 - 대화에 없는 계절입니다.")
                        ), 0, "🔴 두 계절을 나란히 제시한 뒤 이유가 붙은 계절을 놓치게 합니다.",
                                "[선택 마인드맵] 봄 ↔ 가을(정답, 선선함). 이유가 붙은 쪽이 항상 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "부탁/거절",
                        "여자: 이 서류 번역 좀 도와주실 수 있어요?\n남자: 죄송해요, 제가 지금 다른 일이 너무 많아서요.",
                        q("남자의 대답으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부탁을 거절했다.", "정답: '죄송해요'와 이유는 완곡한 거절 표현입니다."),
                                opt("부탁을 수락했다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("나중에 해 주겠다고 했다.", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("다른 사람을 소개했다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 사과 표현(죄송해요)이 붙으면 무조건 수락으로 착각하기 쉽습니다.",
                                "[대답 마인드맵] 부탁 → 죄송해요+이유 → 거절. 사과+이유 조합은 항상 거절입니다.")),
                onePassage(PassageCategory.LISTENING, "위로/격려",
                        "남자: 이번 대회에서 상을 못 받아서 아쉬워요.\n여자: 그래도 최선을 다했잖아요. 다음 기회가 있을 거예요.",
                        q("여자가 남자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("격려하고 있다.", "정답: '다음 기회가 있을 거예요'는 대표적인 격려입니다."),
                                opt("비난하고 있다.", "🟢 초록 - 대화 분위기와 반대되는 오답입니다."),
                                opt("축하하고 있다.", "🟢 초록 - 상황(아쉬움)과 맞지 않습니다."),
                                opt("경고하고 있다.", "🟢 초록 - 대화 분위기와 맞지 않습니다.")
                        ), 0, "🟢 격려의 말을 부정적 말하기 목적(비난, 경고)으로 착각하게 합니다.",
                                "[격려 마인드맵] 아쉬움(남자) → 화살표 → 격려(다음 기회, 여자). 위로 대화는 항상 긍정으로 이어집니다."))
        );

        List<PassageSeed> reading21to30 = List.of(
                onePassage(PassageCategory.READING, "온라인 게시글",
                        "[자유게시판] 제목: 요즘 다니기 좋은 산책 코스 추천해요\n작성자: 산책러버\n내용: 강변을 따라 걷는 코스인데 벚꽃이 예쁘게 피어 있어요. 주말에 사람이 많으니 평일 저녁을 추천합니다.",
                        q("이 글의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("산책 코스를 추천하려고", "정답: 글 제목과 내용 모두 코스 추천이 목적입니다."),
                                opt("날씨를 알려주려고", "🔵 파랑 - 벚꽃 언급을 날씨 정보로 착각하게 합니다."),
                                opt("사람이 많다고 불평하려고", "🔵 파랑 - 부수적 정보를 주된 목적으로 착각하게 합니다."),
                                opt("주말 약속을 잡으려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 부수적으로 언급된 정보(사람 많음, 시간대)를 글의 중심 목적으로 착각하게 합니다.",
                                "[게시글 마인드맵] 제목(추천) = 중심, 본문 세부사항은 가지. 제목이 목적을 가장 잘 알려줍니다.")),
                onePassage(PassageCategory.READING, "뉴스 단신",
                        "[뉴스 단신] 오늘 오후 서울 지역에 강한 바람을 동반한 소나기가 예보되어 있습니다. 외출 시 우산을 챙기시기 바랍니다.",
                        q("이 뉴스의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("오늘 오후에 비가 올 것이다.", "정답: 소나기 예보 내용이 그대로 담겨 있습니다."),
                                opt("오늘 오후에 눈이 올 것이다.", "🔵 파랑 - '소나기'를 '눈'으로 바꾼 오답입니다."),
                                opt("내일 비가 올 것이다.", "🔵 파랑 - '오늘'을 '내일'로 바꾼 오답입니다."),
                                opt("날씨가 맑을 것이다.", "🔵 파랑 - 뉴스 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 시간(오늘/내일)이나 날씨 종류(비/눈)를 슬쩍 바꿔 오답을 만듭니다.",
                                "[뉴스 마인드맵] 시간(오늘 오후) + 날씨(소나기). 두 정보를 각각 다른 색으로 확인하세요.")),
                onePassage(PassageCategory.READING, "광고 문구",
                        "[신학기 특별 할인] 노트북 전 제품 20% 할인! 3월 한 달간 진행되는 이벤트를 놓치지 마세요.",
                        q("이 광고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("노트북을 할인해서 판매한다.", "정답: '20% 할인'이 광고의 핵심 내용입니다."),
                                opt("노트북을 무료로 준다.", "🔵 파랑 - '할인'을 '무료'로 착각하게 하는 오답입니다."),
                                opt("이벤트는 1년 내내 진행된다.", "🔵 파랑 - '3월 한 달간'이라는 기간을 놓치게 합니다."),
                                opt("휴대폰을 할인 판매한다.", "🔵 파랑 - 상품 종류를 바꾼 오답입니다.")
                        ), 0, "🔵 할인율이나 기간, 상품 종류 중 하나를 슬쩍 바꿔 오답을 만듭니다.",
                                "[광고 마인드맵] 상품(노트북) + 조건(20%, 3월). 숫자와 기간에 각각 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "그래프/도표 설명",
                        "[조사 결과] 직장인 대상 설문 조사 결과, 점심시간에 가장 선호하는 활동은 '휴식'(45%)이었고 그다음은 '산책'(30%), '독서'(15%) 순이었다.",
                        q("이 조사 결과로 맞는 것을 고르십시오.", List.of(
                                opt("가장 선호하는 활동은 휴식이다.", "정답: 45%로 가장 높은 비율을 차지합니다."),
                                opt("가장 선호하는 활동은 산책이다.", "🔵 파랑 - 두 번째로 높은 비율을 1위로 착각하게 합니다."),
                                opt("독서를 선호하는 사람이 가장 많다.", "🔵 파랑 - 가장 낮은 비율을 1위로 착각하게 합니다."),
                                opt("휴식과 산책의 비율이 같다.", "🔵 파랑 - 서로 다른 두 수치를 같다고 착각하게 합니다.")
                        ), 0, "🔵 순위나 비율 수치를 뒤바꿔 1위와 2위를 헷갈리게 합니다.",
                                "[도표 마인드맵] 휴식(45%, 1위) > 산책(30%, 2위) > 독서(15%, 3위). 숫자 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "신청서 안내",
                        "[문화센터 강좌 신청 안내] 신청 기간: 3월 2일~3월 8일. 신청은 홈페이지에서만 가능하며, 선착순 마감됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신청은 홈페이지에서만 할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("신청은 전화로도 할 수 있다.", "🔵 파랑 - '홈페이지에서만'이라는 제한을 놓치게 합니다."),
                                opt("신청 기간에 제한이 없다.", "🔵 파랑 - 명시된 기간(3월 2일~8일)과 반대됩니다."),
                                opt("신청자는 모두 등록된다.", "🔵 파랑 - '선착순 마감'이라는 조건과 반대됩니다.")
                        ), 0, "🔵 '~에서만'처럼 제한을 나타내는 표현을 빼고 읽어 오답을 만듭니다.",
                                "[안내문 마인드맵] 신청 방법(홈페이지만) + 마감 조건(선착순). 제한 표현에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "온라인 게시글",
                        "[중고거래] 제목: 책상 팝니다 (거의 새것)\n내용: 작년에 구매했는데 이사 때문에 팝니다. 직거래만 가능하고 가격은 5만 원입니다.",
                        q("이 글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("직거래로만 거래할 수 있다.", "정답: '직거래만 가능'이라고 명시되어 있습니다."),
                                opt("택배로도 보내준다.", "🔵 파랑 - '직거래만'이라는 제한과 반대됩니다."),
                                opt("책상은 새 제품이다.", "🔵 파랑 - '거의 새것'을 '새 제품'으로 착각하게 합니다."),
                                opt("가격은 10만 원이다.", "🔵 파랑 - 실제 가격(5만 원)과 다른 숫자입니다.")
                        ), 0, "🔵 '거의'라는 정도 부사를 빼고 읽어 완전히 새 것으로 착각하게 합니다.",
                                "[게시글 마인드맵] 상태(거의 새것) + 거래 방법(직거래만) + 가격(5만 원). 세 정보를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "뉴스 단신",
                        "[뉴스 단신] 다음 주부터 지하철 요금이 100원 인상될 예정이라고 서울시가 밝혔습니다.",
                        q("이 뉴스의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("지하철 요금이 오를 것이다.", "정답: '100원 인상'이 핵심 내용입니다."),
                                opt("지하철 요금이 내릴 것이다.", "🔵 파랑 - '인상'을 '인하'로 착각하게 하는 오답입니다."),
                                opt("버스 요금이 오를 것이다.", "🔵 파랑 - '지하철'을 '버스'로 바꾼 오답입니다."),
                                opt("이번 주부터 요금이 오른다.", "🔵 파랑 - '다음 주'를 '이번 주'로 바꾼 오답입니다.")
                        ), 0, "🔵 인상/인하, 시점, 교통수단 중 하나를 바꿔 오답을 만듭니다.",
                                "[뉴스 마인드맵] 시점(다음 주) + 변화(인상, 100원) + 대상(지하철). 세 요소를 다른 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "광고 문구",
                        "[헬스장 회원 모집] 3개월 등록 시 1개월 무료! 이달 말까지만 진행되는 특별 혜택입니다.",
                        q("이 광고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("3개월을 등록하면 1개월을 더 준다.", "정답: '3개월 등록 시 1개월 무료'라는 조건이 그대로 담겨 있습니다."),
                                opt("1개월만 등록해도 무료다.", "🔵 파랑 - 등록 조건(3개월)을 빼고 읽게 하는 오답입니다."),
                                opt("혜택은 언제나 받을 수 있다.", "🔵 파랑 - '이달 말까지만'이라는 기한을 놓치게 합니다."),
                                opt("모든 회원이 무료로 이용한다.", "🔵 파랑 - 조건 없는 전면 무료로 과장한 오답입니다.")
                        ), 0, "🔵 혜택을 받기 위한 조건(3개월 등록)을 빼고 결과만 읽게 합니다.",
                                "[광고 마인드맵] 조건(3개월 등록) → 화살표 → 혜택(1개월 무료). 조건과 혜택을 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "그래프/도표 설명",
                        "[설문 결과] 최근 1년간 온라인 쇼핑 이용률을 조사한 결과, 20대가 60%로 가장 높았고 30대가 25%로 뒤를 이었다.",
                        q("이 조사 결과로 맞는 것을 고르십시오.", List.of(
                                opt("20대의 온라인 쇼핑 이용률이 가장 높다.", "정답: 60%로 가장 높은 수치입니다."),
                                opt("30대의 이용률이 가장 높다.", "🔵 파랑 - 두 번째로 높은 비율을 1위로 착각하게 합니다."),
                                opt("20대와 30대의 이용률이 같다.", "🔵 파랑 - 60%와 25%를 같다고 착각하게 합니다."),
                                opt("이용률 조사는 5년간 진행됐다.", "🔵 파랑 - '1년간'을 '5년간'으로 바꾼 오답입니다.")
                        ), 0, "🔵 조사 기간이나 순위 수치를 슬쩍 바꿔 오답을 만듭니다.",
                                "[도표 마인드맵] 20대(60%, 1위) > 30대(25%, 2위). 기간(1년)과 순위를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "신청서 안내",
                        "[도서관 대출증 발급 안내] 신분증을 지참하시면 즉시 발급됩니다. 발급 비용은 무료입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("대출증 발급은 무료다.", "정답: '발급 비용은 무료'라고 명시되어 있습니다."),
                                opt("대출증 발급에는 비용이 든다.", "🔵 파랑 - '무료'와 반대되는 오답입니다."),
                                opt("발급까지 며칠이 걸린다.", "🔵 파랑 - '즉시 발급'과 반대되는 오답입니다."),
                                opt("신분증이 없어도 발급된다.", "🔵 파랑 - '신분증 지참'이라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 조건(신분증 지참)을 빼거나 무료/유료를 바꿔 오답을 만듭니다.",
                                "[안내문 마인드맵] 조건(신분증) → 결과(즉시, 무료 발급). 조건과 결과를 서로 다른 색으로 잇습니다."))
        );

        List<PassageSeed> reading31to40 = List.of(
                onePassage(PassageCategory.READING, "온라인 게시글",
                        "[동네 커뮤니티] 제목: 근처에 괜찮은 미용실 있나요?\n내용: 이사 온 지 얼마 안 돼서 잘 아는 미용실이 없어요. 추천 부탁드려요!",
                        q("이 글을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("미용실을 추천받으려고", "정답: '추천 부탁드려요'가 글의 핵심 목적입니다."),
                                opt("미용실을 홍보하려고", "🔵 파랑 - 글쓴이의 입장(질문자)을 반대로 착각하게 합니다."),
                                opt("이사 정보를 알리려고", "🔵 파랑 - 부수적 정보를 목적으로 착각하게 합니다."),
                                opt("미용실 불만을 제기하려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 배경 설명(이사)을 글의 목적으로 착각하게 합니다.",
                                "[게시글 마인드맵] 배경(이사 옴) → 요청(추천). 마지막 문장이 진짜 목적을 알려줍니다.")),
                onePassage(PassageCategory.READING, "뉴스 단신",
                        "[뉴스 단신] 이번 주말 전국적으로 미세먼지 농도가 높을 것으로 예상되어 외출 시 마스크 착용이 권고됩니다.",
                        q("이 뉴스의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("주말에 미세먼지가 심할 것이다.", "정답: '미세먼지 농도가 높을 것'이 핵심 내용입니다."),
                                opt("주말에 공기가 매우 깨끗할 것이다.", "🔵 파랑 - 뉴스 내용과 반대되는 오답입니다."),
                                opt("평일에 미세먼지가 심할 것이다.", "🔵 파랑 - '주말'을 '평일'로 바꾼 오답입니다."),
                                opt("마스크 착용이 의무화된다.", "🔵 파랑 - '권고'를 '의무화'로 과장한 오답입니다.")
                        ), 0, "🔵 '권고'라는 단어를 '의무'로 과장하거나 요일을 바꿔 오답을 만듭니다.",
                                "[뉴스 마인드맵] 시점(주말) + 상태(미세먼지 높음) + 권고(마스크). 세 정보를 색칠해 구분하세요.")),
                onePassage(PassageCategory.READING, "광고 문구",
                        "[신규 카페 오픈] 오픈 기념 아메리카노 1+1 이벤트! 첫 방문 고객에 한해 적용됩니다.",
                        q("이 광고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("첫 방문 고객만 1+1 혜택을 받는다.", "정답: '첫 방문 고객에 한해'라는 조건이 명시되어 있습니다."),
                                opt("모든 고객이 1+1 혜택을 받는다.", "🔵 파랑 - '첫 방문 고객에 한해'라는 제한을 빼고 읽게 합니다."),
                                opt("이벤트는 커피 종류와 상관없이 적용된다.", "🔵 파랑 - '아메리카노'라는 특정 메뉴 제한을 놓치게 합니다."),
                                opt("이벤트 기간은 정해져 있지 않다.", "🔵 파랑 - '오픈 기념'이라는 한정 기간을 빼고 읽게 합니다.")
                        ), 0, "🔵 '~에 한해'처럼 대상을 제한하는 표현을 빼고 읽어 오답을 만듭니다.",
                                "[광고 마인드맵] 대상 제한(첫 방문) + 메뉴 제한(아메리카노). 제한 표현마다 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "그래프/도표 설명",
                        "[조사 결과] 대학생 대상 조사에서 진로 고민의 이유로 '취업 정보 부족'(40%)이 가장 많았고, '적성 파악 어려움'(35%), '경제적 부담'(25%)이 뒤를 이었다.",
                        q("이 조사 결과로 맞는 것을 고르십시오.", List.of(
                                opt("가장 큰 고민은 취업 정보 부족이다.", "정답: 40%로 가장 높은 비율을 차지합니다."),
                                opt("가장 큰 고민은 경제적 부담이다.", "🔵 파랑 - 가장 낮은 비율을 1위로 착각하게 합니다."),
                                opt("적성 파악 어려움이 취업 정보 부족보다 크다.", "🔵 파랑 - 35%와 40%의 순서를 바꾼 오답입니다."),
                                opt("세 가지 이유의 비율이 모두 같다.", "🔵 파랑 - 서로 다른 세 수치를 같다고 착각하게 합니다.")
                        ), 0, "🔵 1위와 2위 수치를 바꿔치기하거나 모두 같다고 착각하게 합니다.",
                                "[도표 마인드맵] 취업 정보(40%,1위)>적성(35%,2위)>경제(25%,3위). 숫자 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "신청서 안내",
                        "[체육관 강좌 신청 안내] 정원 20명 마감, 대기 신청은 홈페이지에서 접수합니다. 수강료는 강좌 시작일에 납부합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("수강료는 강좌 시작일에 낸다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("수강료는 신청할 때 바로 낸다.", "🔵 파랑 - 납부 시점(시작일)을 신청 시점으로 착각하게 합니다."),
                                opt("정원이 다 차면 신청할 수 없다.", "🔵 파랑 - '대기 신청' 안내를 놓치고 완전 마감으로 착각하게 합니다."),
                                opt("대기 신청은 전화로만 가능하다.", "🔵 파랑 - '홈페이지에서 접수'라는 방법을 바꾼 오답입니다.")
                        ), 0, "🔵 납부 시점을 헷갈리게 하거나 신청 방법(홈페이지/전화)을 바꿔 오답을 만듭니다.",
                                "[안내문 마인드맵] 정원(20명) → 마감 → 대기신청(홈페이지) → 납부(시작일). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "온라인 게시글",
                        "[맘카페] 제목: 아이 예방접종 어디서 맞히세요?\n내용: 근처 소아과 추천해 주시면 감사하겠습니다. 대기 시간이 짧은 곳이면 더 좋아요.",
                        q("이 글쓴이가 원하는 병원 조건으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("대기 시간이 짧은 병원", "정답: '대기 시간이 짧은 곳이면 더 좋아요'라고 명시했습니다."),
                                opt("규모가 큰 병원", "🔵 파랑 - 언급되지 않은 조건입니다."),
                                opt("진료비가 비싼 병원", "🔵 파랑 - 언급되지 않은 조건입니다."),
                                opt("멀리 있는 병원", "🔵 파랑 - '근처'라는 조건과 반대됩니다.")
                        ), 0, "🔵 명시되지 않은 조건(규모, 비용)을 만들어 오답을 구성합니다.",
                                "[게시글 마인드맵] 조건1(근처) + 조건2(대기 짧음). 조건 두 개를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "뉴스 단신",
                        "[뉴스 단신] 정부는 다음 달부터 대중교통 이용 시 환승 할인 시간을 40분에서 60분으로 확대한다고 발표했습니다.",
                        q("이 뉴스의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("환승 할인 시간이 늘어난다.", "정답: '40분에서 60분으로 확대'가 핵심 내용입니다."),
                                opt("환승 할인 시간이 줄어든다.", "🔵 파랑 - '확대'를 '축소'로 착각하게 하는 오답입니다."),
                                opt("환승 할인 제도가 없어진다.", "🔵 파랑 - 뉴스 내용과 반대되는 오답입니다."),
                                opt("이번 달부터 바로 시행된다.", "🔵 파랑 - '다음 달부터'라는 시점을 바꾼 오답입니다.")
                        ), 0, "🔵 확대/축소 방향이나 시행 시점을 바꿔 오답을 만듭니다.",
                                "[뉴스 마인드맵] 시점(다음 달) + 변화(40분→60분, 확대). 화살표 방향에 주목하세요.")),
                onePassage(PassageCategory.READING, "광고 문구",
                        "[여름 특가] 에어컨 구매 시 선풍기 증정! 5월 한 달간 온라인 매장에서만 진행됩니다.",
                        q("이 광고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("에어컨을 사면 선풍기를 준다.", "정답: '구매 시 선풍기 증정'이 광고의 핵심입니다."),
                                opt("선풍기를 사면 에어컨을 준다.", "🔵 파랑 - 증정 대상과 구매 대상을 뒤바꾼 오답입니다."),
                                opt("오프라인 매장에서도 진행된다.", "🔵 파랑 - '온라인 매장에서만'이라는 제한을 놓치게 합니다."),
                                opt("행사 기간에 제한이 없다.", "🔵 파랑 - '5월 한 달간'이라는 기간을 빼고 읽게 합니다.")
                        ), 0, "🔵 구매 대상과 증정품을 뒤바꾸거나 매장·기간 제한을 빼고 읽게 합니다.",
                                "[광고 마인드맵] 구매(에어컨) → 화살표 → 증정(선풍기). 화살표 방향이 바뀌면 오답입니다.")),
                onePassage(PassageCategory.READING, "그래프/도표 설명",
                        "[설문 결과] 직장인 재택근무 선호도 조사에서 '선호한다'는 응답이 55%, '선호하지 않는다'는 응답이 30%, '상관없다'는 응답이 15%였다.",
                        q("이 조사 결과로 맞는 것을 고르십시오.", List.of(
                                opt("재택근무를 선호하는 응답이 가장 많다.", "정답: 55%로 가장 높은 비율입니다."),
                                opt("재택근무를 선호하지 않는 응답이 가장 많다.", "🔵 파랑 - 두 번째로 높은 비율을 1위로 착각하게 합니다."),
                                opt("상관없다는 응답이 가장 많다.", "🔵 파랑 - 가장 낮은 비율을 1위로 착각하게 합니다."),
                                opt("선호와 비선호 응답 비율이 같다.", "🔵 파랑 - 55%와 30%를 같다고 착각하게 합니다.")
                        ), 0, "🔵 순위나 비율을 뒤바꾸거나 서로 다른 수치를 같다고 착각하게 합니다.",
                                "[도표 마인드맵] 선호(55%,1위)>비선호(30%,2위)>상관없음(15%,3위). 숫자 크기 순으로 색칠하세요.")),
                onePassage(PassageCategory.READING, "신청서 안내",
                        "[주민센터 프로그램 신청 안내] 만 65세 이상만 신청 가능하며, 신청서는 방문 접수만 받습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신청서는 방문해서만 낼 수 있다.", "정답: '방문 접수만 받습니다'라고 명시되어 있습니다."),
                                opt("신청서는 우편으로도 받는다.", "🔵 파랑 - '방문 접수만'이라는 제한을 놓치게 합니다."),
                                opt("나이 제한 없이 신청할 수 있다.", "🔵 파랑 - '만 65세 이상'이라는 조건과 반대됩니다."),
                                opt("만 65세 미만도 신청 가능하다.", "🔵 파랑 - 나이 조건을 반대로 착각하게 합니다.")
                        ), 0, "🔵 나이 조건이나 접수 방법 제한을 빼고 읽어 오답을 만듭니다.",
                                "[안내문 마인드맵] 조건(만 65세 이상) + 방법(방문만). 두 제한 조건에 각각 색을 칠하세요."))
        );

        List<PassageSeed> listening2nd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "계획 세우기",
                        "여자: 이번 방학에 뭐 할 거예요?\n남자: 저는 운전면허를 따려고 학원에 등록했어요.",
                        q("남자의 방학 계획으로 알맞은 것을 고르십시오.", List.of(
                                opt("운전면허를 딴다.", "정답: '운전면허를 따려고 학원에 등록했어요'가 계획입니다."),
                                opt("여행을 간다.", "🟢 초록 - 언급되지 않은 계획입니다."),
                                opt("아르바이트를 한다.", "🟢 초록 - 언급되지 않은 계획입니다."),
                                opt("운전을 가르친다.", "🟢 초록 - '배우다'를 '가르치다'로 뒤바꾼 오답입니다.")
                        ), 0, "🟢 배우는 입장과 가르치는 입장을 뒤바꿔 오답을 만듭니다.",
                                "[계획 마인드맵] 중심 = 방학, 가지 = 학원 등록(면허). 목적어의 주체가 누구인지 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "후회/아쉬움",
                        "남자: 어제 우산을 안 가져가서 비를 다 맞았어요.\n여자: 저런, 일기예보를 미리 확인했어야 했는데요.",
                        q("남자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("후회스럽다", "정답: 우산을 안 가져가 비를 맞은 상황은 후회를 나타냅니다."),
                                opt("만족스럽다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("자랑스럽다", "🟢 초록 - 언급되지 않은 감정입니다."),
                                opt("편안하다", "🟢 초록 - 상황과 반대되는 감정입니다.")
                        ), 0, "🟢 부정적 상황(비를 맞음)에 긍정적 감정을 짝지어 헷갈리게 합니다.",
                                "[후회 마인드맵] 상황(우산 안 가져감) → 결과(비 맞음) → 감정(후회). 색으로 인과관계를 이으세요.")),
                onePassage(PassageCategory.LISTENING, "칭찬/감사",
                        "여자: 발표 준비를 정말 꼼꼼하게 하셨네요. 도움이 많이 됐어요.\n남자: 별말씀을요.",
                        q("여자가 남자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("칭찬하고 있다.", "정답: '꼼꼼하게 하셨네요'는 칭찬 표현입니다."),
                                opt("비판하고 있다.", "🟢 초록 - 대화 분위기와 반대되는 오답입니다."),
                                opt("사과하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다."),
                                opt("거절하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🟢 칭찬 표현을 무관한 말하기 목적(사과, 거절)으로 착각하게 합니다.",
                                "[칭찬 마인드맵] 행동(꼼꼼한 준비) → 화살표 → 칭찬(도움 됨). 긍정 평가는 항상 칭찬입니다.")),
                onePassage(PassageCategory.LISTENING, "놀람/걱정",
                        "남자: 내일 시험인데 아직 공부를 하나도 못 했어요.\n여자: 네? 큰일이네요.",
                        q("여자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("걱정스럽다", "정답: '큰일이네요'는 걱정을 나타내는 표현입니다."),
                                opt("기쁘다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("무관심하다", "🟢 초록 - '큰일이네요'라는 반응과 반대됩니다."),
                                opt("자랑스럽다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 걱정 표현을 무관심이나 긍정적 감정으로 착각하게 합니다.",
                                "[걱정 마인드맵] 상황(공부 못함) → 반응(큰일이네요, 걱정). 반응 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "추천/조언",
                        "여자: 요즘 잠이 잘 안 와요.\n남자: 자기 전에 휴대폰을 보지 않는 게 좋아요.",
                        q("남자가 여자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("조언하고 있다.", "정답: '~는 게 좋아요'는 대표적인 조언 표현입니다."),
                                opt("명령하고 있다.", "🟢 초록 - 어조와 맞지 않는 오답입니다."),
                                opt("불평하고 있다.", "🟢 초록 - 대화 분위기와 맞지 않습니다."),
                                opt("자랑하고 있다.", "🟢 초록 - 언급되지 않은 목적입니다.")
                        ), 0, "🟢 부드러운 조언 표현(~는 게 좋아요)을 명령으로 착각하게 합니다.",
                                "[조언 마인드맵] 문제(잠이 안 옴) → 화살표 → 조언(휴대폰 그만 보기). 원인과 해결책을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "계획 세우기",
                        "남자: 다음 달에 이사할 집을 알아보고 있어요.\n여자: 벌써요? 준비를 일찍 시작하시네요.",
                        q("남자가 현재 하고 있는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("이사할 집을 알아보고 있다.", "정답: '집을 알아보고 있어요'가 현재 하는 일입니다."),
                                opt("이미 이사를 마쳤다.", "🔴 빨강 - '다음 달'이라는 미래 시점을 놓치게 합니다."),
                                opt("집을 팔고 있다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("이사를 포기했다.", "🔴 빨강 - 대화 내용과 반대됩니다.")
                        ), 0, "🔴 '다음 달'이라는 미래 시점을 놓치고 이미 끝난 일로 착각하게 합니다.",
                                "[계획 마인드맵] 지금(알아보는 중) → 다음 달(이사 예정). 시제 표현에 색을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "후회/아쉬움",
                        "여자: 그때 그 회사 제안을 받아들였어야 했는데 후회돼요.\n남자: 지난 일은 어쩔 수 없죠.",
                        q("여자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("후회스럽다", "정답: '후회돼요'라고 직접 표현했습니다."),
                                opt("만족스럽다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("자신만만하다", "🟢 초록 - 언급되지 않은 감정입니다."),
                                opt("설렌다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 명확한 후회 표현을 무관한 긍정적 감정으로 바꿔 오답을 만듭니다.",
                                "[후회 마인드맵] 과거 선택(거절함) → 결과 → 후회. 명시적 감정 단어를 우선 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "칭찬/감사",
                        "남자: 지난번에 빌려주신 책 덕분에 시험을 잘 봤어요. 정말 감사해요.\n여자: 도움이 됐다니 다행이에요.",
                        q("남자가 여자에게 하는 말의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("감사를 표현하려고", "정답: '정말 감사해요'가 핵심 목적입니다."),
                                opt("책을 돌려달라고 하려고", "🟢 초록 - 언급되지 않은 목적입니다."),
                                opt("불만을 말하려고", "🟢 초록 - 대화 분위기와 반대됩니다."),
                                opt("책을 더 빌리려고", "🟢 초록 - 언급되지 않은 목적입니다.")
                        ), 0, "🟢 감사 표현을 무관한 요청 목적(반납, 추가 대출)으로 착각하게 합니다.",
                                "[감사 마인드맵] 도움(책 빌림) → 결과(시험 잘 봄) → 감사. 결과와 감사 표현을 같은 색으로 이으세요.")),
                onePassage(PassageCategory.LISTENING, "놀람/걱정",
                        "여자: 방금 지갑에서 이상한 소리가 났어요. 휴대폰이 울린 건가?\n남자: 어? 정말요? 깜짝 놀랐어요.",
                        q("남자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("놀랍다", "정답: '깜짝 놀랐어요'라고 직접 표현했습니다."),
                                opt("지루하다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("편안하다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("자랑스럽다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 놀람의 감정을 무관한 감정(편안함, 자랑스러움)으로 바꿔 오답을 만듭니다.",
                                "[놀람 마인드맵] 예상 밖 소리 → 반응(깜짝 놀람). 의성어·감탄사 뒤의 감정 단어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "추천/조언",
                        "남자: 요즘 살이 좀 찐 것 같아요.\n여자: 저녁 식사량을 조금 줄여 보는 게 어때요?",
                        q("여자가 남자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("조언하고 있다.", "정답: '~는 게 어때요'는 제안·조언 표현입니다."),
                                opt("명령하고 있다.", "🟢 초록 - 어조와 맞지 않는 오답입니다."),
                                opt("놀리고 있다.", "🟢 초록 - 대화 분위기와 맞지 않습니다."),
                                opt("동의를 구하고 있다.", "🟢 초록 - 대화 목적과 맞지 않습니다.")
                        ), 0, "🟢 부드러운 제안 표현(~는 게 어때요)을 명령이나 동의 요청으로 착각하게 합니다.",
                                "[조언 마인드맵] 문제(살이 찜) → 화살표 → 조언(식사량 줄이기). 문제와 해결책의 색을 맞추세요."))
        );

        List<PassageSeed> listening2nd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "계획 세우기",
                        "여자: 다음 학기에는 어떤 수업을 들을 거예요?\n남자: 저는 경제학 수업을 신청할까 해요.",
                        q("남자의 다음 학기 계획으로 알맞은 것을 고르십시오.", List.of(
                                opt("경제학 수업을 신청하려고 한다.", "정답: '경제학 수업을 신청할까 해요'가 계획입니다."),
                                opt("이미 경제학 수업을 들었다.", "🔴 빨강 - '다음 학기'라는 미래 시점을 놓치게 합니다."),
                                opt("수업을 하나도 안 듣는다.", "🔴 빨강 - 대화 내용과 반대됩니다."),
                                opt("경제학을 가르친다.", "🔴 빨강 - '듣다'를 '가르치다'로 뒤바꾼 오답입니다.")
                        ), 0, "🔴 미래 계획 표현(~할까 해요)을 이미 끝난 일로 착각하게 합니다.",
                                "[계획 마인드맵] 다음 학기(미래) → 신청 예정(경제학). 시제 표현에 색을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "후회/아쉬움",
                        "남자: 좀 더 일찍 출발했으면 안 막혔을 텐데 아쉽네요.\n여자: 다음엔 여유 있게 출발해요.",
                        q("남자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("아쉽다", "정답: '아쉽네요'라고 직접 표현했습니다."),
                                opt("만족스럽다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("무섭다", "🟢 초록 - 언급되지 않은 감정입니다."),
                                opt("자랑스럽다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 아쉬움 표현을 무관한 감정(무서움, 자랑스러움)으로 바꿔 오답을 만듭니다.",
                                "[아쉬움 마인드맵] 상황(늦게 출발) → 결과(막힘) → 감정(아쉬움). 조건문 뒤의 감정을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "칭찬/감사",
                        "여자: 이 그림 정말 잘 그리셨네요. 색感이 뛰어나세요.\n남자: 감사합니다. 열심히 연습했어요.",
                        q("여자가 남자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("칭찬하고 있다.", "정답: '잘 그리셨네요', '뛰어나세요'는 칭찬 표현입니다."),
                                opt("충고하고 있다.", "🟢 초록 - 대화 분위기와 맞지 않습니다."),
                                opt("부탁하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다."),
                                opt("사과하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🟢 칭찬 표현을 무관한 말하기 목적(충고, 부탁)으로 착각하게 합니다.",
                                "[칭찬 마인드맵] 결과물(그림) → 평가(잘함) → 칭찬. 긍정 형용사에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "놀람/걱정",
                        "남자: 아이가 갑자기 열이 많이 나요. 어떡하죠?\n여자: 얼른 병원에 데려가 보세요.",
                        q("남자의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("걱정스럽다", "정답: 아이의 갑작스러운 발열 상황에 대한 걱정이 드러납니다."),
                                opt("기쁘다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("편안하다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("지루하다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 걱정스러운 응급 상황을 무관한 감정으로 바꿔 오답을 만듭니다.",
                                "[걱정 마인드맵] 상황(아이 발열) → 반응(어떡하죠, 걱정). 응급 상황 표현에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "추천/조언",
                        "여자: 발표할 때 너무 긴장돼요.\n남자: 미리 여러 번 연습해 보면 도움이 될 거예요.",
                        q("남자가 여자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("조언하고 있다.", "정답: '~해 보면 도움이 될 거예요'는 조언 표현입니다."),
                                opt("걱정하고 있다.", "🟢 초록 - 남자의 발화 목적과 다릅니다."),
                                opt("칭찬하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다."),
                                opt("거절하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🟢 해결책 제시(조언)를 단순 공감(걱정)으로 착각하게 합니다.",
                                "[조언 마인드맵] 문제(긴장됨) → 화살표 → 조언(연습하기). 해결책이 나오면 조언입니다.")),
                onePassage(PassageCategory.LISTENING, "계획 세우기",
                        "남자: 이번 여름에 자격증 시험을 준비하려고 해요.\n여자: 좋은 계획이네요.",
                        q("남자의 여름 계획으로 알맞은 것을 고르십시오.", List.of(
                                opt("자격증 시험을 준비한다.", "정답: '준비하려고 해요'가 계획입니다."),
                                opt("여행을 간다.", "🟢 초록 - 언급되지 않은 계획입니다."),
                                opt("자격증을 이미 땄다.", "🟢 초록 - 미래 계획을 완료로 착각하게 하는 오답입니다."),
                                opt("아무 계획이 없다.", "🟢 초록 - 대화 내용과 반대됩니다.")
                        ), 0, "🟢 미래 계획(~하려고 해요)을 이미 완료한 일로 착각하게 합니다.",
                                "[계획 마인드맵] 이번 여름(미래) → 준비 예정(자격증). 계획 표현에 색을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "후회/아쉬움",
                        "여자: 어제 그 콘서트 표를 못 사서 너무 아쉬워요.\n남자: 저도 그 얘기 듣고 아쉬웠어요.",
                        q("두 사람의 공통된 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("아쉽다", "정답: 두 사람 모두 '아쉽다'고 표현했습니다."),
                                opt("기쁘다", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("화가 나다", "🟢 초록 - 언급되지 않은 감정입니다."),
                                opt("무섭다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 공통된 아쉬움을 무관한 감정으로 바꿔 오답을 만듭니다.",
                                "[아쉬움 마인드맵] 여자(아쉬움) = 화살표(동감) = 남자(아쉬움). 감정이 같은 방향입니다.")),
                onePassage(PassageCategory.LISTENING, "칭찬/감사",
                        "남자: 지난주에 도와주셔서 이번 프로젝트를 잘 마칠 수 있었어요. 정말 고마워요.\n여자: 별말씀을요, 저도 즐거웠어요.",
                        q("남자가 여자에게 하는 말의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("감사를 표현하려고", "정답: '정말 고마워요'가 핵심 목적입니다."),
                                opt("다시 도와달라고 부탁하려고", "🟢 초록 - 언급되지 않은 목적입니다."),
                                opt("프로젝트를 취소하려고", "🟢 초록 - 대화 내용과 반대됩니다."),
                                opt("불만을 제기하려고", "🟢 초록 - 대화 분위기와 반대됩니다.")
                        ), 0, "🟢 감사 표현을 무관한 목적(추가 부탁, 불만)으로 착각하게 합니다.",
                                "[감사 마인드맵] 도움 → 결과(프로젝트 성공) → 감사. 인과관계를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "놀람/걱정",
                        "여자: 방금 밖에서 큰 소리가 났는데 무슨 일이죠?\n남자: 저도 몰라요. 걱정되네요.",
                        q("두 사람의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("걱정스럽다", "정답: '걱정되네요'라고 직접 표현했습니다."),
                                opt("즐겁다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("편안하다", "🟢 초록 - 상황과 반대되는 감정입니다."),
                                opt("자랑스럽다", "🟢 초록 - 언급되지 않은 감정입니다.")
                        ), 0, "🟢 걱정 표현을 무관한 긍정적 감정으로 바꿔 오답을 만듭니다.",
                                "[걱정 마인드맵] 예상 밖 소리 → 반응(걱정됨). 정체불명 상황엔 걱정이 자연스럽습니다.")),
                onePassage(PassageCategory.LISTENING, "추천/조언",
                        "남자: 요즘 집중이 잘 안 돼요.\n여자: 잠깐씩 쉬면서 하는 게 좋을 것 같아요.",
                        q("여자가 남자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("조언하고 있다.", "정답: '~는 게 좋을 것 같아요'는 조언 표현입니다."),
                                opt("명령하고 있다.", "🟢 초록 - 어조와 맞지 않는 오답입니다."),
                                opt("자랑하고 있다.", "🟢 초록 - 언급되지 않은 목적입니다."),
                                opt("사과하고 있다.", "🟢 초록 - 대화 내용과 관련이 없습니다.")
                        ), 0, "🟢 부드러운 조언 표현을 명령이나 무관한 목적으로 착각하게 합니다.",
                                "[조언 마인드맵] 문제(집중 안 됨) → 화살표 → 조언(휴식). 문제-해결책 구조를 색칠하세요."))
        );

        List<PassageSeed> reading2nd21to30 = List.of(
                onePassage(PassageCategory.READING, "레시피",
                        "[김치볶음밥 만들기] 1. 팬에 기름을 두르고 김치를 볶는다. 2. 밥을 넣고 함께 볶는다. 3. 계란 프라이를 올려 완성한다.",
                        q("이 글의 조리 순서로 맞는 것을 고르십시오.", List.of(
                                opt("김치를 볶은 후 밥을 넣는다.", "정답: 순서 1번과 2번에 그대로 나와 있습니다."),
                                opt("밥을 먼저 볶은 후 김치를 넣는다.", "🔴 빨강 - 조리 순서를 뒤바꾼 오답입니다."),
                                opt("계란을 가장 먼저 넣는다.", "🔴 빨강 - 순서상 계란은 마지막입니다."),
                                opt("김치와 계란만 볶는다.", "🔴 빨강 - '밥'이라는 재료를 빠뜨린 오답입니다.")
                        ), 0, "🔴 조리 순서(숫자)를 뒤바꾸거나 재료를 빠뜨려 오답을 만듭니다.",
                                "[레시피 마인드맵] 1(김치)→2(밥)→3(계란). 숫자 순서대로 색을 다르게 칠하세요.")),
                onePassage(PassageCategory.READING, "사용후기",
                        "[상품 후기] ★★★★☆ 배송이 빨라서 좋았어요. 다만 생각보다 사이즈가 작게 나온 것 같아요. 한 치수 크게 주문하세요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("사이즈가 작게 나온 편이다.", "정답: '사이즈가 작게 나온 것 같아요'라고 명시했습니다."),
                                opt("사이즈가 크게 나온 편이다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("배송이 느렸다.", "🔵 파랑 - '배송이 빨라서 좋았다'와 반대됩니다."),
                                opt("품질에 대한 언급이 없다.", "🔵 파랑 - 별점(★★★★☆)이라는 품질 평가가 있습니다.")
                        ), 0, "🔵 긍정/부정 평가를 뒤바꾸거나 언급된 내용을 없다고 착각하게 합니다.",
                                "[후기 마인드맵] 장점(배송 빠름) + 단점(사이즈 작음). 장점과 단점을 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "모집 공고",
                        "[봉사자 모집] 지역아동센터에서 함께할 봉사자를 모집합니다. 매주 토요일 오전, 초등학생 학습 지도를 도와주실 분을 찾습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("토요일 오전에 활동한다.", "정답: '매주 토요일 오전'이라고 명시되어 있습니다."),
                                opt("평일 저녁에 활동한다.", "🔵 파랑 - 명시된 요일·시간과 반대되는 오답입니다."),
                                opt("중학생을 가르친다.", "🔵 파랑 - '초등학생'을 '중학생'으로 바꾼 오답입니다."),
                                opt("한 번만 참여하면 된다.", "🔵 파랑 - '매주'라는 반복성을 놓치게 합니다.")
                        ), 0, "🔵 요일·시간, 대상, 반복 여부 중 하나를 슬쩍 바꿔 오답을 만듭니다.",
                                "[공고 마인드맵] 시간(토요일 오전) + 대상(초등학생) + 반복(매주). 세 정보를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "안내 방송문",
                        "[안내 방송] 잠시 후 5번 출구 방향 엘리베이터 점검이 있을 예정입니다. 이용에 참고하시기 바랍니다.",
                        q("이 방송의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("엘리베이터를 점검할 예정이다.", "정답: '엘리베이터 점검'이 방송의 핵심 내용입니다."),
                                opt("에스컬레이터를 점검할 예정이다.", "🔵 파랑 - '엘리베이터'를 '에스컬레이터'로 바꾼 오답입니다."),
                                opt("점검이 이미 끝났다.", "🔵 파랑 - '잠시 후'라는 미래 시점을 놓치게 합니다."),
                                opt("전체 출구가 점검 대상이다.", "🔵 파랑 - '5번 출구'라는 특정 위치를 확대한 오답입니다.")
                        ), 0, "🔵 시설 종류나 시점, 범위를 슬쩍 바꿔 오답을 만듭니다.",
                                "[방송 마인드맵] 시점(잠시 후) + 위치(5번 출구) + 대상(엘리베이터). 세 요소를 색칠해 구분하세요.")),
                onePassage(PassageCategory.READING, "인터뷰 기사",
                        "[인터뷰] \"매일 아침 30분씩 책을 읽는 습관이 제 인생을 바꿨어요.\" 한 유명 작가가 성공 비결을 이렇게 밝혔다.",
                        q("이 작가가 밝힌 성공 비결로 알맞은 것을 고르십시오.", List.of(
                                opt("매일 아침 독서하는 습관", "정답: 인용문에 그대로 나와 있습니다."),
                                opt("매일 저녁 운동하는 습관", "🔵 파랑 - 시간대와 활동을 모두 바꾼 오답입니다."),
                                opt("일주일에 한 번 책을 읽는 습관", "🔵 파랑 - '매일'을 '일주일에 한 번'으로 바꾼 오답입니다."),
                                opt("책을 쓰는 습관", "🔵 파랑 - '읽다'를 '쓰다'로 바꾼 오답입니다.")
                        ), 0, "🔵 시간대, 빈도, 행동(읽기/쓰기) 중 하나를 바꿔 오답을 만듭니다.",
                                "[인터뷰 마인드맵] 시간(매일 아침) + 행동(독서 30분). 인용문 속 핵심 단어에 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "레시피",
                        "[샌드위치 만들기] 1. 식빵에 버터를 바른다. 2. 양상추와 햄을 올린다. 3. 식빵을 덮어 반으로 자른다.",
                        q("이 글의 조리 순서로 맞는 것을 고르십시오.", List.of(
                                opt("버터를 바른 후 재료를 올린다.", "정답: 순서 1번과 2번 그대로입니다."),
                                opt("재료를 먼저 올린 후 버터를 바른다.", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("자른 후에 재료를 올린다.", "🔴 빨강 - 마지막 단계를 중간으로 착각하게 합니다."),
                                opt("버터 없이 만든다.", "🔴 빨강 - 재료(버터)를 빠뜨린 오답입니다.")
                        ), 0, "🔴 조리 순서를 뒤바꾸거나 재료를 빠뜨려 오답을 만듭니다.",
                                "[레시피 마인드맵] 1(버터)→2(재료)→3(자르기). 숫자 순서를 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "사용후기",
                        "[상품 후기] ★★★☆☆ 기능은 만족스러운데 소음이 좀 있어요. 밤에 쓰기엔 조금 불편할 수 있어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("소음이 있는 편이다.", "정답: '소음이 좀 있어요'라고 명시했습니다."),
                                opt("소음이 전혀 없다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("기능이 불만족스럽다.", "🔵 파랑 - '기능은 만족스럽다'와 반대되는 오답입니다."),
                                opt("낮에만 사용할 수 있다.", "🔵 파랑 - '밤에 쓰기엔 불편할 수 있다'를 과장한 오답입니다.")
                        ), 0, "🔵 장점과 단점을 뒤바꾸거나 표현 강도를 과장해 오답을 만듭니다.",
                                "[후기 마인드맵] 장점(기능 만족) + 단점(소음). 서로 다른 색으로 표시해 헷갈리지 않게 하세요.")),
                onePassage(PassageCategory.READING, "모집 공고",
                        "[서포터즈 모집] 대학생 온라인 서포터즈를 모집합니다. 활동 기간은 6개월이며, 우수 활동자에게는 수료증이 발급됩니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("우수 활동자는 수료증을 받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 지원자가 수료증을 받는다.", "🔵 파랑 - '우수 활동자'라는 조건을 빼고 읽게 합니다."),
                                opt("활동 기간은 1년이다.", "🔵 파랑 - '6개월'을 '1년'으로 바꾼 오답입니다."),
                                opt("고등학생도 지원할 수 있다.", "🔵 파랑 - '대학생'이라는 대상을 바꾼 오답입니다.")
                        ), 0, "🔵 조건(우수 활동자)을 빼거나 기간·대상을 바꿔 오답을 만듭니다.",
                                "[공고 마인드맵] 대상(대학생) + 기간(6개월) + 조건(우수자만 수료증). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "안내 방송문",
                        "[안내 방송] 잃어버린 우산을 습득하신 분은 안내데스크로 가져다주시기 바랍니다. 감사합니다.",
                        q("이 방송의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("분실물을 안내데스크로 전달해 달라고 요청하려고", "정답: 방송 내용 그대로 요청하고 있습니다."),
                                opt("우산을 판매하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("우산 사용법을 알려주려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("안내데스크 위치를 알려주려고", "🔵 파랑 - 부수적 정보를 주된 목적으로 착각하게 합니다.")
                        ), 0, "🔵 부수적으로 언급된 장소(안내데스크)를 방송의 주된 목적으로 착각하게 합니다.",
                                "[방송 마인드맵] 상황(분실물) → 요청(전달). 요청 동사(가져다주세요)가 목적을 알려줍니다.")),
                onePassage(PassageCategory.READING, "인터뷰 기사",
                        "[인터뷰] \"실패를 두려워하지 않고 계속 도전한 것이 지금의 저를 만들었습니다.\" 한 스타트업 대표가 이렇게 말했다.",
                        q("이 대표가 말한 성공 비결로 알맞은 것을 고르십시오.", List.of(
                                opt("실패를 두려워하지 않고 도전한 것", "정답: 인용문에 그대로 나와 있습니다."),
                                opt("실패를 철저히 피한 것", "🔵 파랑 - '두려워하지 않는다'와 반대되는 오답입니다."),
                                opt("한 번의 도전으로 성공한 것", "🔵 파랑 - '계속 도전'이라는 반복성을 놓치게 합니다."),
                                opt("다른 사람의 도움을 받은 것", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 두려워함/두려워하지 않음을 뒤바꾸거나 반복성을 놓치게 합니다.",
                                "[인터뷰 마인드맵] 태도(두려워 안 함) + 행동(계속 도전). 핵심 표현에 형광펜을 칠하세요."))
        );

        List<PassageSeed> reading2nd31to40 = List.of(
                onePassage(PassageCategory.READING, "레시피",
                        "[미역국 끓이기] 1. 불린 미역을 참기름에 볶는다. 2. 물을 넣고 끓인다. 3. 소고기와 국간장을 넣어 마무리한다.",
                        q("이 글의 조리 순서로 맞는 것을 고르십시오.", List.of(
                                opt("미역을 볶은 후 물을 넣는다.", "정답: 순서 1번과 2번 그대로입니다."),
                                opt("물을 먼저 넣은 후 미역을 볶는다.", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("소고기를 가장 먼저 넣는다.", "🔴 빨강 - 소고기는 마지막 단계입니다."),
                                opt("미역만 넣고 끓인다.", "🔴 빨강 - 다른 재료(소고기, 국간장)를 빠뜨린 오답입니다.")
                        ), 0, "🔴 조리 순서를 뒤바꾸거나 재료를 빠뜨려 오답을 만듭니다.",
                                "[레시피 마인드맵] 1(미역 볶기)→2(물)→3(소고기·간장). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "사용후기",
                        "[상품 후기] ★★★★★ 가격 대비 성능이 정말 좋아요. 재구매 의사 100%입니다!",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("작성자는 매우 만족했다.", "정답: 별 5개와 재구매 의사가 만족을 나타냅니다."),
                                opt("작성자는 실망했다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("가격이 비싸다고 느꼈다.", "🔵 파랑 - '가격 대비 성능이 좋다'와 반대되는 오답입니다."),
                                opt("재구매하지 않을 것이다.", "🔵 파랑 - '재구매 의사 100%'와 반대되는 오답입니다.")
                        ), 0, "🔵 만족/불만족 표현을 뒤바꿔 오답을 만듭니다.",
                                "[후기 마인드맵] 별점(★★★★★) + 재구매 의사(100%). 모두 강한 긍정 신호입니다.")),
                onePassage(PassageCategory.READING, "모집 공고",
                        "[체험단 모집] 신제품 체험단을 모집합니다. 선정된 분께는 제품을 무료로 드리며, 사용 후기 작성이 필수입니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("선정되면 후기를 작성해야 한다.", "정답: '사용 후기 작성이 필수'라고 명시되어 있습니다."),
                                opt("후기 작성은 선택 사항이다.", "🔵 파랑 - '필수'라는 조건과 반대되는 오답입니다."),
                                opt("제품 구매 비용을 내야 한다.", "🔵 파랑 - '무료로 드린다'는 내용과 반대됩니다."),
                                opt("아무나 지원 없이 받을 수 있다.", "🔵 파랑 - '선정된 분'이라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 필수/선택을 뒤바꾸거나 무료/유료, 선정 조건을 빼고 읽게 합니다.",
                                "[공고 마인드맵] 혜택(무료 제품) + 의무(후기 필수). 혜택과 의무를 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "안내 방송문",
                        "[안내 방송] 오늘 영업시간이 평소보다 1시간 단축되어 오후 9시에 마감합니다. 양해 부탁드립니다.",
                        q("이 방송의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("오늘은 평소보다 일찍 마감한다.", "정답: '1시간 단축'이 핵심 내용입니다."),
                                opt("오늘은 평소보다 늦게 마감한다.", "🔵 파랑 - '단축'을 '연장'으로 착각하게 하는 오답입니다."),
                                opt("내일부터 영업시간이 바뀐다.", "🔵 파랑 - '오늘'을 '내일'로 바꾼 오답입니다."),
                                opt("영업을 하지 않는다.", "🔵 파랑 - 방송 내용과 반대되는 과장된 오답입니다.")
                        ), 0, "🔵 단축/연장을 뒤바꾸거나 시점, 휴업 여부를 왜곡해 오답을 만듭니다.",
                                "[방송 마인드맵] 오늘(시점) + 단축(1시간) + 9시 마감. 세 요소를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "인터뷰 기사",
                        "[인터뷰] \"팀원들과의 신뢰가 가장 중요합니다. 혼자서는 절대 여기까지 올 수 없었어요.\" 한 운동선수가 이렇게 말했다.",
                        q("이 선수가 강조한 성공 비결로 알맞은 것을 고르십시오.", List.of(
                                opt("팀원들과의 신뢰", "정답: 인용문에 그대로 나와 있습니다."),
                                opt("개인의 타고난 재능", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("철저한 개인 훈련", "🔵 파랑 - '혼자서는 할 수 없다'는 말과 반대됩니다."),
                                opt("풍부한 경제적 지원", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 협력의 중요성을 개인 역량으로 바꿔치기해 오답을 만듭니다.",
                                "[인터뷰 마인드맵] 핵심 단어(신뢰, 팀원). '혼자서는 안 된다'는 협력 강조 문장을 색칠하세요.")),
                onePassage(PassageCategory.READING, "레시피",
                        "[계란찜 만들기] 1. 계란을 풀어 체에 거른다. 2. 물과 소금을 넣고 섞는다. 3. 약한 불에서 저으며 익힌다.",
                        q("이 글의 조리 순서로 맞는 것을 고르십시오.", List.of(
                                opt("계란을 거른 후 물을 넣는다.", "정답: 순서 1번과 2번 그대로입니다."),
                                opt("물을 먼저 넣은 후 계란을 거른다.", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("센 불에서 빠르게 익힌다.", "🔴 빨강 - '약한 불'과 반대되는 오답입니다."),
                                opt("소금 없이 만든다.", "🔴 빨강 - 재료(소금)를 빠뜨린 오답입니다.")
                        ), 0, "🔴 조리 순서나 불 세기를 뒤바꿔 오답을 만듭니다.",
                                "[레시피 마인드맵] 1(거르기)→2(물,소금)→3(약불 익히기). 순서와 조건에 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "사용후기",
                        "[상품 후기] ★★☆☆☆ 사진과 실제 색상이 많이 다르네요. 디자인은 예쁜데 아쉬워요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("실제 색상이 사진과 다르다.", "정답: '사진과 실제 색상이 많이 다르다'고 명시했습니다."),
                                opt("색상이 사진과 똑같다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("디자인이 마음에 안 든다.", "🔵 파랑 - '디자인은 예쁘다'와 반대되는 오답입니다."),
                                opt("매우 만족스럽다는 평가다.", "🔵 파랑 - 낮은 별점(★★☆☆☆)과 반대되는 오답입니다.")
                        ), 0, "🔵 색상 일치 여부나 디자인 평가를 뒤바꿔 오답을 만듭니다.",
                                "[후기 마인드맵] 장점(디자인) + 단점(색상 차이). 별점이 낮으면 단점에 주목하세요.")),
                onePassage(PassageCategory.READING, "모집 공고",
                        "[아르바이트 모집] 카페 아르바이트생을 모집합니다. 근무 요일은 협의 가능하며, 경력자를 우대합니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("근무 요일은 협의할 수 있다.", "정답: '근무 요일은 협의 가능'이라고 명시되어 있습니다."),
                                opt("근무 요일이 고정되어 있다.", "🔵 파랑 - '협의 가능'이라는 조건과 반대됩니다."),
                                opt("경력자만 지원할 수 있다.", "🔵 파랑 - '우대'를 '필수 조건'으로 과장한 오답입니다."),
                                opt("무경력자는 지원할 수 없다.", "🔵 파랑 - '우대'와 '필수'를 혼동한 오답입니다.")
                        ), 0, "🔵 '우대'를 '필수'로 과장하거나 협의 가능 여부를 반대로 바꿉니다.",
                                "[공고 마인드맵] 조건(요일 협의) + 우대(경력자). '우대'는 필수가 아님을 기억하세요.")),
                onePassage(PassageCategory.READING, "안내 방송문",
                        "[안내 방송] 곧 영화가 시작됩니다. 상영관 내에서는 휴대폰 전원을 꺼 주시기 바랍니다.",
                        q("이 방송의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("휴대폰 전원을 꺼 달라고 요청하려고", "정답: 방송 내용 그대로 요청하고 있습니다."),
                                opt("영화 시간을 안내하려고", "🔵 파랑 - 부수적 정보를 주된 목적으로 착각하게 합니다."),
                                opt("휴대폰을 판매하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("영화관 위치를 안내하려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 부수적 정보(영화 시작)를 주된 목적으로 착각하게 합니다.",
                                "[방송 마인드맵] 상황(영화 시작) → 요청(전원 끄기). 마지막 요청 문장이 목적입니다.")),
                onePassage(PassageCategory.READING, "인터뷰 기사",
                        "[인터뷰] \"매번 실패할 때마다 기록을 남기고 원인을 분석했습니다. 그게 성장의 밑거름이 됐어요.\" 한 요리사가 이렇게 말했다.",
                        q("이 요리사가 밝힌 성공 비결로 알맞은 것을 고르십시오.", List.of(
                                opt("실패를 기록하고 분석하는 것", "정답: 인용문에 그대로 나와 있습니다."),
                                opt("실패를 빨리 잊는 것", "🔵 파랑 - '기록하고 분석한다'와 반대되는 오답입니다."),
                                opt("실패를 아예 하지 않는 것", "🔵 파랑 - '매번 실패할 때마다'와 반대되는 오답입니다."),
                                opt("다른 사람의 실패를 참고하는 것", "🔵 파랑 - '자신의 실패'를 '타인의 실패'로 바꾼 오답입니다.")
                        ), 0, "🔵 자신의 경험을 타인의 경험으로 바꾸거나 반대되는 태도로 왜곡합니다.",
                                "[인터뷰 마인드맵] 행동(기록+분석) → 결과(성장). 핵심 동사 두 개에 형광펜을 칠하세요."))
        );

        List<PassageSeed> listening3rd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "예약 변경/취소",
                        "여자: 예약한 시간을 3시에서 5시로 바꾸고 싶은데요.\n남자: 네, 5시로 변경해 드렸습니다.",
                        q("여자가 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("예약 시간 변경", "정답: '시간을 바꾸고 싶다'는 것이 요청 내용입니다."),
                                opt("예약 취소", "🟢 초록 - '취소'가 아니라 '변경'입니다."),
                                opt("새 예약", "🟢 초록 - 기존 예약을 바꾸는 것이지 새 예약이 아닙니다."),
                                opt("환불 요청", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 '변경'과 '취소'라는 비슷한 요청 유형을 혼동하게 합니다.",
                                "[요청 마인드맵] 기존 예약(3시) → 화살표 → 변경(5시). 취소가 아니라 변경임을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "배달 주문",
                        "남자: 짜장면 두 그릇 배달 되나요?\n여자: 네, 30분 정도 걸립니다.",
                        q("배달이 도착하는 데 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("30분", "정답: '30분 정도 걸립니다'라고 명시했습니다."),
                                opt("10분", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("1시간", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("2시간", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 대화에 없는 임의의 숫자를 넣어 오답을 만듭니다.",
                                "[배달 마인드맵] 주문(짜장면 2그릇) → 소요 시간(30분). 숫자 정보만 정확히 골라내세요.")),
                onePassage(PassageCategory.LISTENING, "고장/수리 신고",
                        "여자: 세탁기가 갑자기 안 돌아가요. 좀 봐 주실 수 있어요?\n남자: 네, 내일 오전에 방문하겠습니다.",
                        q("남자가 방문할 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("내일 오전", "정답: '내일 오전에 방문하겠습니다'라고 답했습니다."),
                                opt("오늘 오후", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("내일 저녁", "🔴 빨강 - 시간대를 바꾼 오답입니다."),
                                opt("모레 오전", "🔴 빨강 - 날짜를 바꾼 오답입니다.")
                        ), 0, "🔴 방문 시점(날짜·시간대)을 슬쩍 바꿔 오답을 만듭니다.",
                                "[수리 마인드맵] 신고(세탁기 고장) → 방문 예정(내일 오전). 날짜와 시간대를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "분실물 신고",
                        "남자: 지하철에서 가방을 잃어버렸는데 어떻게 해야 하나요?\n여자: 분실물 센터에 문의해 보세요.",
                        q("여자가 남자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("분실물 센터에 문의하기", "정답: '분실물 센터에 문의해 보세요'가 제안 내용입니다."),
                                opt("경찰서에 신고하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("새 가방을 사기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("역무원에게 화내기", "🟢 초록 - 대화 분위기와 맞지 않습니다.")
                        ), 0, "🟢 실제 제안과 무관한 행동(신고, 구매)을 제안으로 착각하게 합니다.",
                                "[분실물 마인드맵] 문제(가방 분실) → 해결책(분실물 센터). 제안 문장의 목적어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "환불/교환",
                        "여자: 이 옷 사이즈가 안 맞아서 교환하고 싶어요.\n남자: 영수증 가지고 계시면 바로 교환해 드릴게요.",
                        q("여자가 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("옷 교환", "정답: '교환하고 싶어요'가 요청 내용입니다."),
                                opt("환불", "🟢 초록 - '교환'이 아니라 '환불'로 착각하게 하는 오답입니다."),
                                opt("사이즈 문의", "🟢 초록 - 단순 문의가 아니라 교환 요청입니다."),
                                opt("추가 구매", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 '교환'과 '환불'이라는 비슷한 요청 유형을 혼동하게 합니다.",
                                "[요청 마인드맵] 문제(사이즈 안 맞음) → 요청(교환). 환불이 아니라 교환임을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "예약 변경/취소",
                        "남자: 내일 예약한 거 취소하고 싶어요.\n여자: 네, 취소 처리해 드렸습니다.",
                        q("남자가 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("예약 취소", "정답: '취소하고 싶어요'가 요청 내용입니다."),
                                opt("예약 변경", "🟢 초록 - '취소'가 아니라 '변경'으로 착각하게 하는 오답입니다."),
                                opt("예약 확인", "🟢 초록 - 단순 확인이 아니라 취소 요청입니다."),
                                opt("새로운 예약", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 '취소'와 '변경'이라는 비슷한 요청 유형을 혼동하게 합니다.",
                                "[요청 마인드맵] 기존 예약 → 취소 요청 → 처리 완료. 변경이 아니라 취소임을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "배달 주문",
                        "여자: 피자 라지 사이즈 하나 주문할게요. 주소는 문자로 보내드릴게요.\n남자: 네, 결제는 어떻게 하시겠어요?",
                        q("여자가 주문한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("피자 라지 사이즈 하나", "정답: '피자 라지 사이즈 하나'라고 명시했습니다."),
                                opt("피자 미디엄 사이즈 하나", "🔴 빨강 - 사이즈를 바꾼 오답입니다."),
                                opt("피자 라지 사이즈 두 개", "🔴 빨강 - 수량을 바꾼 오답입니다."),
                                opt("치킨 한 마리", "🔴 빨강 - 메뉴를 바꾼 오답입니다.")
                        ), 0, "🔴 사이즈, 수량, 메뉴 중 하나를 바꿔 오답을 만듭니다.",
                                "[주문 마인드맵] 메뉴(피자) + 사이즈(라지) + 수량(1). 세 정보를 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "고장/수리 신고",
                        "남자: 인터넷이 갑자기 안 돼요.\n여자: 확인해 보니 지역 전체에 문제가 있어서 2시간 후에 복구될 예정입니다.",
                        q("인터넷이 복구되는 데 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2시간", "정답: '2시간 후에 복구될 예정'이라고 명시했습니다."),
                                opt("30분", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("1시간", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("하루", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 대화에 없는 임의의 숫자로 오답을 만듭니다.",
                                "[수리 마인드맵] 문제(인터넷 장애) → 원인(지역 전체) → 복구 시간(2시간). 숫자만 정확히 찾으세요.")),
                onePassage(PassageCategory.LISTENING, "분실물 신고",
                        "여자: 어제 카페에서 우산을 두고 온 것 같아요.\n남자: 카페에 전화해서 확인해 보세요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("카페에 전화해서 확인하기", "정답: '카페에 전화해서 확인해 보세요'가 제안입니다."),
                                opt("직접 카페에 가 보기", "🟢 초록 - '전화'를 '방문'으로 바꾼 오답입니다."),
                                opt("새 우산을 사기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("경찰서에 신고하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 '전화'라는 구체적 방법을 다른 행동(방문, 신고)으로 바꿔 오답을 만듭니다.",
                                "[분실물 마인드맵] 문제(우산 분실) → 해결책(전화 확인). 제안 방법의 수단을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "환불/교환",
                        "남자: 이 제품 불량인 것 같은데 환불받을 수 있을까요?\n여자: 네, 영수증 확인 후 바로 환불해 드리겠습니다.",
                        q("남자가 요청한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("환불", "정답: '환불받을 수 있을까요'가 요청 내용입니다."),
                                opt("교환", "🟢 초록 - '환불'이 아니라 '교환'으로 착각하게 하는 오답입니다."),
                                opt("수리", "🟢 초록 - 언급되지 않은 요청입니다."),
                                opt("재구매", "🟢 초록 - 언급되지 않은 요청입니다.")
                        ), 0, "🟢 '환불'과 '교환'이라는 비슷한 요청 유형을 혼동하게 합니다.",
                                "[요청 마인드맵] 문제(불량) → 요청(환불). 교환이 아니라 환불임을 확인하세요."))
        );

        List<PassageSeed> listening3rd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "예약 변경/취소",
                        "여자: 다음 주 화요일 예약을 목요일로 옮기고 싶어요.\n남자: 목요일 오후는 예약이 다 찼어요. 오전은 가능합니다.",
                        q("남자가 안내한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("목요일 오전 예약이 가능하다.", "정답: '오전은 가능합니다'라고 안내했습니다."),
                                opt("목요일 오전도 예약이 다 찼다.", "🔴 빨강 - '오후'와 '오전'을 혼동하게 하는 오답입니다."),
                                opt("화요일 예약도 취소해야 한다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("목요일은 아예 예약할 수 없다.", "🔴 빨강 - 오전은 가능하다는 내용과 반대됩니다.")
                        ), 0, "🔴 오전/오후처럼 비슷한 시간대를 뒤바꿔 오답을 만듭니다.",
                                "[예약 마인드맵] 오후(마감) ↔ 오전(가능). 시간대별로 다른 색을 칠해 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "배달 주문",
                        "남자: 최소 주문 금액이 얼마예요?\n여자: 만오천 원 이상부터 배달 가능합니다.",
                        q("배달 가능한 최소 금액으로 알맞은 것을 고르십시오.", List.of(
                                opt("만오천 원", "정답: '만오천 원 이상부터 배달 가능'이라고 명시했습니다."),
                                opt("만 원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("이만 원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("오천 원", "🔴 빨강 - 대화에 없는 금액입니다.")
                        ), 0, "🔴 대화에 없는 임의의 금액으로 오답을 만듭니다.",
                                "[주문 마인드맵] 질문(최소 금액) → 답(만오천 원). 숫자 단위(만/천)를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "고장/수리 신고",
                        "여자: 에어컨에서 이상한 소리가 나요.\n남자: 언제부터 그러셨어요? 필터부터 확인해 보겠습니다.",
                        q("남자가 가장 먼저 하려는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("필터 확인", "정답: '필터부터 확인해 보겠습니다'라고 말했습니다."),
                                opt("에어컨 교체", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("부품 주문", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("소리 녹음", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 '~부터'라는 순서 표현을 놓치고 다른 행동을 정답처럼 만듭니다.",
                                "[수리 마인드맵] 증상(이상한 소리) → 첫 확인(필터). '~부터'라는 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "분실물 신고",
                        "남자: 버스에 지갑을 두고 내린 것 같아요.\n여자: 버스 회사에 몇 번 버스였는지 알려주고 문의하세요.",
                        q("여자가 남자에게 요구한 정보로 알맞은 것을 고르십시오.", List.of(
                                opt("버스 번호", "정답: '몇 번 버스였는지 알려주라'고 했습니다."),
                                opt("탑승 시간", "🔵 파랑 - 언급되지 않은 정보입니다."),
                                opt("지갑 색깔", "🔵 파랑 - 언급되지 않은 정보입니다."),
                                opt("운전기사 이름", "🔵 파랑 - 언급되지 않은 정보입니다.")
                        ), 0, "🔵 실제 언급된 정보(버스 번호)와 무관한 다른 정보를 만들어 오답을 구성합니다.",
                                "[분실물 마인드맵] 요청 정보 = 버스 번호. 대화에서 실제 언급된 단어만 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "환불/교환",
                        "여자: 온라인으로 주문한 옷을 반품하고 싶은데 어떻게 하나요?\n남자: 마이페이지에서 반품 신청을 하시면 됩니다.",
                        q("남자가 안내한 반품 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("마이페이지에서 신청하기", "정답: '마이페이지에서 반품 신청'이 안내 내용입니다."),
                                opt("고객센터에 전화하기", "🔵 파랑 - 언급되지 않은 방법입니다."),
                                opt("매장에 직접 방문하기", "🔵 파랑 - 언급되지 않은 방법입니다."),
                                opt("이메일로 문의하기", "🔵 파랑 - 언급되지 않은 방법입니다.")
                        ), 0, "🔵 실제 안내된 방법(마이페이지)과 다른 방법을 만들어 오답을 구성합니다.",
                                "[반품 마인드맵] 방법 = 마이페이지 신청. 안내 문장의 장소·수단 단어를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "예약 변경/취소",
                        "남자: 예약 인원을 2명에서 4명으로 늘리고 싶어요.\n여자: 네, 4명으로 변경해 드렸습니다.",
                        q("변경된 예약 인원으로 알맞은 것을 고르십시오.", List.of(
                                opt("4명", "정답: '4명으로 변경해 드렸습니다'라고 확인했습니다."),
                                opt("2명", "🔴 빨강 - 변경 전 인원과 헷갈리게 하는 오답입니다."),
                                opt("6명", "🔴 빨강 - 대화에 없는 인원입니다."),
                                opt("3명", "🔴 빨강 - 대화에 없는 인원입니다.")
                        ), 0, "🔴 변경 전 숫자와 변경 후 숫자를 혼동하게 합니다.",
                                "[예약 마인드맵] 2명(변경 전) → 화살표 → 4명(변경 후). 화살표 뒤 숫자가 최종 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "배달 주문",
                        "여자: 혹시 배달비도 따로 있나요?\n남자: 네, 3천 원 추가됩니다.",
                        q("배달비로 알맞은 것을 고르십시오.", List.of(
                                opt("3천 원", "정답: '3천 원 추가됩니다'라고 명시했습니다."),
                                opt("무료", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("5천 원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("1천 원", "🔴 빨강 - 대화에 없는 금액입니다.")
                        ), 0, "🔴 무료라고 착각하게 하거나 임의의 금액으로 오답을 만듭니다.",
                                "[주문 마인드맵] 질문(배달비 유무) → 답(3천 원 추가). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "고장/수리 신고",
                        "남자: 냉장고 문이 잘 안 닫혀요.\n여자: 부품 교체가 필요할 것 같은데, 비용은 무료로 처리해 드릴게요. 보증기간 내라서요.",
                        q("수리 비용에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("보증기간 내라서 무료다.", "정답: '보증기간 내라서' 무료 처리한다고 했습니다."),
                                opt("비용을 따로 청구한다.", "🔵 파랑 - '무료로 처리해 드릴게요'와 반대되는 오답입니다."),
                                opt("부품 값만 청구한다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("보증기간이 끝나서 유료다.", "🔵 파랑 - '보증기간 내'와 반대되는 오답입니다.")
                        ), 0, "🔵 무료/유료를 뒤바꾸거나 보증기간 여부를 반대로 착각하게 합니다.",
                                "[수리 마인드맵] 조건(보증기간 내) → 결과(무료). 조건과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "분실물 신고",
                        "여자: 공원 벤치에 휴대폰을 두고 온 것 같아요.\n남자: 관리사무소에 가서 물어보는 게 빠를 거예요.",
                        q("남자가 여자에게 제안한 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("관리사무소", "정답: '관리사무소에 가서 물어보라'고 제안했습니다."),
                                opt("경찰서", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("공원 입구", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("분실물 센터", "🔵 파랑 - 언급되지 않은 장소입니다.")
                        ), 0, "🔵 실제 언급된 장소(관리사무소)와 비슷한 다른 장소를 만들어 오답을 구성합니다.",
                                "[분실물 마인드맵] 장소 = 관리사무소. 제안 문장에 나온 장소 단어를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "환불/교환",
                        "남자: 교환하려는데 다른 색상으로도 바꿀 수 있나요?\n여자: 네, 재고가 있으면 다른 색상으로도 교환 가능합니다.",
                        q("여자의 대답으로 맞는 것을 고르십시오.", List.of(
                                opt("재고가 있으면 색상 교환이 가능하다.", "정답: 조건(재고)과 함께 가능하다고 답했습니다."),
                                opt("색상 교환은 불가능하다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("무조건 색상 교환이 가능하다.", "🔵 파랑 - '재고가 있으면'이라는 조건을 빼고 읽게 합니다."),
                                opt("같은 색상으로만 교환 가능하다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(재고 여부)을 빼고 읽거나 가능/불가능을 뒤바꿔 오답을 만듭니다.",
                                "[교환 마인드맵] 조건(재고 있음) → 결과(색상 교환 가능). 조건 표현에 밑줄을 그으세요."))
        );

        List<PassageSeed> reading3rd21to30 = List.of(
                onePassage(PassageCategory.READING, "영수증/명세서",
                        "[영수증] 아메리카노 4,500원, 카페라떼 5,000원, 합계 9,500원. 결제수단: 카드",
                        q("이 영수증의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("카드로 결제했다.", "정답: '결제수단: 카드'라고 명시되어 있습니다."),
                                opt("현금으로 결제했다.", "🔵 파랑 - '카드'를 '현금'으로 바꾼 오답입니다."),
                                opt("합계 금액은 4,500원이다.", "🔵 파랑 - 개별 금액을 합계로 착각하게 합니다."),
                                opt("음료를 세 잔 주문했다.", "🔵 파랑 - 실제로는 두 잔(아메리카노, 라떼)입니다.")
                        ), 0, "🔵 개별 금액과 합계를 혼동하거나 결제수단, 수량을 바꿔 오답을 만듭니다.",
                                "[영수증 마인드맵] 항목별 금액 + 합계(9,500원) + 결제수단(카드). 세 정보를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "계약서 요약",
                        "[임대 계약 요약] 임대 기간: 2년. 월세는 매달 5일까지 납부해야 하며, 계약 만료 1개월 전까지 갱신 여부를 통보해야 합니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("월세는 매달 5일까지 내야 한다.", "정답: 계약서에 그대로 명시되어 있습니다."),
                                opt("월세는 매달 말일까지 내면 된다.", "🔵 파랑 - 납부 기한(5일)을 바꾼 오답입니다."),
                                opt("갱신 여부는 계약 만료 당일 통보한다.", "🔵 파랑 - '1개월 전까지'라는 기한을 놓치게 합니다."),
                                opt("임대 기간은 1년이다.", "🔵 파랑 - '2년'을 '1년'으로 바꾼 오답입니다.")
                        ), 0, "🔵 기한이나 기간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[계약서 마인드맵] 기간(2년) + 납부일(매달 5일) + 통보 기한(1개월 전). 숫자마다 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "초대 메시지",
                        "[문자] 이번 주 토요일 오후 6시에 집들이를 하려고 해요. 시간 되시면 놀러 오세요! 장소는 이전에 알려드린 그 주소예요.",
                        q("이 메시지의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("집들이에 초대하려고", "정답: '놀러 오세요'가 초대의 핵심입니다."),
                                opt("이사를 도와달라고 하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("주소를 물어보려고", "🔵 파랑 - '이전에 알려드린'과 반대되는 오답입니다."),
                                opt("약속을 취소하려고", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 초대 목적을 무관한 다른 목적(취소, 문의)으로 착각하게 합니다.",
                                "[메시지 마인드맵] 시간(토요일 6시) + 목적(초대). '놀러 오세요'라는 핵심 문장에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "공지 이메일",
                        "[이메일] 제목: 시스템 점검 안내\n내용: 오는 금요일 밤 12시부터 2시간 동안 시스템 점검이 진행되어 서비스 이용이 제한됩니다.",
                        q("이 이메일의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("금요일 밤에 서비스 이용이 제한된다.", "정답: 점검 시간 동안 이용 제한이라고 명시되어 있습니다."),
                                opt("토요일 밤에 점검이 진행된다.", "🔵 파랑 - '금요일'을 '토요일'로 바꾼 오답입니다."),
                                opt("점검은 하루 종일 진행된다.", "🔵 파랑 - '2시간 동안'이라는 기간을 과장한 오답입니다."),
                                opt("점검 중에도 정상 이용이 가능하다.", "🔵 파랑 - '이용이 제한된다'와 반대되는 오답입니다.")
                        ), 0, "🔵 요일이나 점검 시간을 바꾸거나 이용 가능 여부를 반대로 착각하게 합니다.",
                                "[이메일 마인드맵] 시점(금요일 밤) + 기간(2시간) + 영향(이용 제한). 세 요소를 색칠하세요.")),
                onePassage(PassageCategory.READING, "지도/약도 설명",
                        "[약도 설명] 지하철역 2번 출구로 나와서 직진하다가 편의점에서 오른쪽으로 꺾으면 왼쪽에 건물이 보입니다.",
                        q("이 약도 설명에 따른 이동 경로로 맞는 것을 고르십시오.", List.of(
                                opt("2번 출구 → 직진 → 편의점에서 우회전", "정답: 설명 순서 그대로입니다."),
                                opt("2번 출구 → 직진 → 편의점에서 좌회전", "🔴 빨강 - '오른쪽'을 '왼쪽'으로 바꾼 오답입니다."),
                                opt("1번 출구 → 직진 → 편의점에서 우회전", "🔴 빨강 - 출구 번호를 바꾼 오답입니다."),
                                opt("2번 출구 → 좌회전 → 편의점", "🔴 빨강 - 순서와 방향을 모두 바꾼 오답입니다.")
                        ), 0, "🔴 방향(좌/우)이나 출구 번호를 슬쩍 바꿔 오답을 만듭니다.",
                                "[약도 마인드맵] 출구(2번) → 직진 → 회전(오른쪽). 방향 표시에 화살표 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "영수증/명세서",
                        "[관리비 명세서] 전기료 35,000원, 수도료 15,000원, 인터넷료 20,000원, 합계 70,000원.",
                        q("이 명세서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("합계 금액은 70,000원이다.", "정답: '합계 70,000원'이라고 명시되어 있습니다."),
                                opt("전기료가 가장 저렴하다.", "🔵 파랑 - 실제로는 전기료가 가장 비쌉니다."),
                                opt("수도료가 인터넷료보다 비싸다.", "🔵 파랑 - 15,000원과 20,000원의 크기를 뒤바꾼 오답입니다."),
                                opt("항목은 두 가지뿐이다.", "🔵 파랑 - 실제로는 세 항목(전기, 수도, 인터넷)입니다.")
                        ), 0, "🔵 개별 항목의 금액 크기를 뒤바꾸거나 항목 개수를 착각하게 합니다.",
                                "[명세서 마인드맵] 전기(35,000)>인터넷(20,000)>수도(15,000). 금액 크기 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "계약서 요약",
                        "[매매 계약 요약] 계약금은 전체 금액의 10%이며, 계약 당일 지급합니다. 잔금은 한 달 후에 지급합니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("계약금은 계약 당일 지급한다.", "정답: 계약서에 그대로 명시되어 있습니다."),
                                opt("계약금은 한 달 후에 지급한다.", "🔵 파랑 - 계약금과 잔금의 지급 시점을 뒤바꾼 오답입니다."),
                                opt("계약금은 전체 금액의 50%다.", "🔵 파랑 - '10%'를 '50%'로 바꾼 오답입니다."),
                                opt("잔금은 계약 당일 지급한다.", "🔵 파랑 - 잔금과 계약금의 지급 시점을 뒤바꾼 오답입니다.")
                        ), 0, "🔵 계약금과 잔금의 지급 시점을 뒤바꾸거나 비율 숫자를 바꿔 오답을 만듭니다.",
                                "[계약서 마인드맵] 계약금(10%, 당일) + 잔금(한 달 후). 시점과 비율을 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "초대 메시지",
                        "[문자] 다음 달 5일에 결혼식을 합니다. 참석이 어려우시면 미리 알려주시면 감사하겠습니다.",
                        q("이 메시지의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("결혼식에 초대하려고", "정답: 결혼식 날짜를 알리며 초대하는 메시지입니다."),
                                opt("결혼식을 취소하려고", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("축의금을 요청하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("날짜를 변경하려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 초대 목적을 무관한 다른 목적(취소, 요청)으로 착각하게 합니다.",
                                "[메시지 마인드맵] 날짜(다음 달 5일) + 목적(초대, 참석 여부 확인). 핵심 문장에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "공지 이메일",
                        "[이메일] 제목: 비밀번호 변경 안내\n내용: 보안 강화를 위해 다음 주까지 비밀번호를 변경해 주시기 바랍니다. 변경하지 않으면 계정이 일시 정지됩니다.",
                        q("이 이메일의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("비밀번호를 변경하지 않으면 계정이 정지된다.", "정답: 이메일에 그대로 명시되어 있습니다."),
                                opt("비밀번호 변경은 선택 사항이다.", "🔵 파랑 - '정지된다'는 경고와 반대되는 오답입니다."),
                                opt("이번 주까지 변경해야 한다.", "🔵 파랑 - '다음 주까지'라는 기한을 바꾼 오답입니다."),
                                opt("계정이 영구 삭제된다.", "🔵 파랑 - '일시 정지'를 '영구 삭제'로 과장한 오답입니다.")
                        ), 0, "🔵 기한을 바꾸거나 '일시 정지'를 '영구 삭제'로 과장해 오답을 만듭니다.",
                                "[이메일 마인드맵] 기한(다음 주까지) + 미이행 결과(일시 정지). 기한과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "지도/약도 설명",
                        "[약도 설명] 정문으로 들어와서 왼쪽 계단으로 올라가면 2층에 사무실이 있습니다. 엘리베이터는 오른쪽에 있습니다.",
                        q("사무실로 가는 방법으로 맞는 것을 고르십시오.", List.of(
                                opt("정문 → 왼쪽 계단 → 2층", "정답: 설명 순서 그대로입니다."),
                                opt("정문 → 오른쪽 계단 → 2층", "🔴 빨강 - '왼쪽'을 '오른쪽'으로 바꾼 오답입니다."),
                                opt("후문 → 왼쪽 계단 → 2층", "🔴 빨강 - '정문'을 '후문'으로 바꾼 오답입니다."),
                                opt("정문 → 왼쪽 계단 → 3층", "🔴 빨강 - 층수를 바꾼 오답입니다.")
                        ), 0, "🔴 방향, 출입구, 층수 중 하나를 슬쩍 바꿔 오답을 만듭니다.",
                                "[약도 마인드맵] 입구(정문) → 계단(왼쪽) → 층(2층). 세 정보를 각각 색칠해 구분하세요."))
        );

        List<PassageSeed> reading3rd31to40 = List.of(
                onePassage(PassageCategory.READING, "영수증/명세서",
                        "[영수증] 티셔츠 15,000원 × 2장, 할인 5,000원 적용, 최종 결제금액 25,000원.",
                        q("이 영수증의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("최종 결제금액은 25,000원이다.", "정답: '최종 결제금액 25,000원'이라고 명시되어 있습니다."),
                                opt("할인 전 금액이 25,000원이다.", "🔵 파랑 - 할인 전(30,000원)과 할인 후 금액을 혼동하게 합니다."),
                                opt("티셔츠를 한 장 샀다.", "🔵 파랑 - '2장'을 '1장'으로 바꾼 오답입니다."),
                                opt("할인 금액은 15,000원이다.", "🔵 파랑 - 개별 상품가와 할인 금액을 혼동하게 합니다.")
                        ), 0, "🔵 할인 전/후 금액이나 수량, 할인액 숫자를 혼동하게 합니다.",
                                "[영수증 마인드맵] 원가(30,000)-할인(5,000)=최종(25,000). 계산 순서를 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "계약서 요약",
                        "[근로 계약 요약] 근무 시간은 오전 9시부터 오후 6시까지이며, 수습 기간 3개월 동안은 급여의 90%를 지급합니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("수습 기간에는 급여의 90%를 받는다.", "정답: 계약서에 그대로 명시되어 있습니다."),
                                opt("수습 기간에는 급여의 100%를 받는다.", "🔵 파랑 - '90%'를 '100%'로 바꾼 오답입니다."),
                                opt("수습 기간은 6개월이다.", "🔵 파랑 - '3개월'을 '6개월'로 바꾼 오답입니다."),
                                opt("근무는 오후 9시에 끝난다.", "🔵 파랑 - '오후 6시'를 '오후 9시'로 바꾼 오답입니다.")
                        ), 0, "🔵 비율, 기간, 근무 시간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[계약서 마인드맵] 근무 시간(9시~6시) + 수습(3개월, 90%). 숫자마다 색을 다르게 칠하세요.")),
                onePassage(PassageCategory.READING, "초대 메시지",
                        "[문자] 이번 동창회는 다음 주 금요일 저녁 7시, 학교 앞 식당에서 열립니다. 회비는 3만 원입니다.",
                        q("이 메시지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("회비는 3만 원이다.", "정답: '회비는 3만 원입니다'라고 명시했습니다."),
                                opt("회비는 무료다.", "🔵 파랑 - 명시된 회비와 반대되는 오답입니다."),
                                opt("모임은 다음 주 토요일이다.", "🔵 파랑 - '금요일'을 '토요일'로 바꾼 오답입니다."),
                                opt("모임 장소는 학교 안이다.", "🔵 파랑 - '학교 앞'을 '학교 안'으로 바꾼 오답입니다.")
                        ), 0, "🔵 요일, 장소, 회비 유무를 슬쩍 바꿔 오답을 만듭니다.",
                                "[메시지 마인드맵] 시간(금요일 7시) + 장소(학교 앞) + 회비(3만 원). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "공지 이메일",
                        "[이메일] 제목: 배송 지연 안내\n내용: 주문량 폭주로 인해 배송이 2~3일 지연될 수 있습니다. 양해 부탁드립니다.",
                        q("이 이메일의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("배송이 예정보다 늦어질 수 있다.", "정답: '배송이 지연될 수 있다'는 내용입니다."),
                                opt("배송이 예정보다 빨라진다.", "🔵 파랑 - '지연'을 '단축'으로 착각하게 하는 오답입니다."),
                                opt("주문이 취소되었다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("배송비가 추가된다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 지연/단축을 뒤바꾸거나 언급되지 않은 내용을 추가해 오답을 만듭니다.",
                                "[이메일 마인드맵] 원인(주문 폭주) → 결과(배송 지연). 원인과 결과를 화살표로 연결해 색칠하세요.")),
                onePassage(PassageCategory.READING, "지도/약도 설명",
                        "[약도 설명] 버스 정류장에서 내려서 횡단보도를 건넌 후 오른쪽으로 100미터 정도 가면 병원이 보입니다.",
                        q("병원으로 가는 방법으로 맞는 것을 고르십시오.", List.of(
                                opt("정류장 → 횡단보도 → 오른쪽 100미터", "정답: 설명 순서 그대로입니다."),
                                opt("정류장 → 횡단보도 → 왼쪽 100미터", "🔴 빨강 - '오른쪽'을 '왼쪽'으로 바꾼 오답입니다."),
                                opt("정류장 → 오른쪽 100미터 → 횡단보도", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("정류장 → 횡단보도 → 오른쪽 500미터", "🔴 빨강 - 거리를 바꾼 오답입니다.")
                        ), 0, "🔴 방향, 순서, 거리 중 하나를 슬쩍 바꿔 오답을 만듭니다.",
                                "[약도 마인드맵] 정류장 → 횡단보도 → 방향(오른쪽) → 거리(100m). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "영수증/명세서",
                        "[영수증] 회원 할인 10% 적용, 상품 금액 40,000원, 최종 결제금액 36,000원.",
                        q("이 영수증의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("최종 결제금액은 36,000원이다.", "정답: '최종 결제금액 36,000원'이라고 명시되어 있습니다."),
                                opt("할인율은 20%다.", "🔵 파랑 - '10%'를 '20%'로 바꾼 오답입니다."),
                                opt("상품 금액은 36,000원이다.", "🔵 파랑 - 할인 전 금액(40,000원)과 혼동하게 합니다."),
                                opt("회원이 아니어도 할인을 받는다.", "🔵 파랑 - '회원 할인'이라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 할인율, 할인 전/후 금액, 회원 조건을 헷갈리게 합니다.",
                                "[영수증 마인드맵] 원가(40,000) - 할인 10% = 최종(36,000). 조건과 계산을 색칠하세요.")),
                onePassage(PassageCategory.READING, "계약서 요약",
                        "[렌트 계약 요약] 렌트 기간은 3일이며, 반납 시 주유는 처음 상태로 채워 반납해야 합니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("반납할 때 주유를 처음 상태로 채워야 한다.", "정답: 계약서에 그대로 명시되어 있습니다."),
                                opt("주유 없이 반납해도 된다.", "🔵 파랑 - '처음 상태로 채워야 한다'와 반대되는 오답입니다."),
                                opt("렌트 기간은 일주일이다.", "🔵 파랑 - '3일'을 '일주일'로 바꾼 오답입니다."),
                                opt("업체가 대신 주유해 준다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 조건(주유 상태)을 빼고 읽거나 기간을 바꿔 오답을 만듭니다.",
                                "[계약서 마인드맵] 기간(3일) + 반납 조건(주유 채우기). 조건 문장에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "초대 메시지",
                        "[문자] 다음 주 화요일 오전 10시에 신제품 발표회를 진행합니다. 참석 여부를 이번 주 금요일까지 회신해 주세요.",
                        q("이 메시지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("참석 여부는 이번 주 금요일까지 회신해야 한다.", "정답: 메시지에 그대로 명시되어 있습니다."),
                                opt("회신 기한은 다음 주 화요일이다.", "🔵 파랑 - 발표회 날짜와 회신 기한을 혼동하게 합니다."),
                                opt("발표회는 오후에 진행된다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("회신은 필요하지 않다.", "🔵 파랑 - '회신해 주세요'와 반대되는 오답입니다.")
                        ), 0, "🔵 회신 기한과 행사 날짜를 혼동하거나 회신 필요 여부를 반대로 착각하게 합니다.",
                                "[메시지 마인드맵] 행사(화요일 10시) + 회신 기한(금요일까지). 두 날짜를 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "공지 이메일",
                        "[이메일] 제목: 휴무 안내\n내용: 다음 주 월요일은 창립기념일로 휴무입니다. 문의사항은 화요일부터 처리 가능합니다.",
                        q("이 이메일의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("월요일은 휴무다.", "정답: '창립기념일로 휴무'라고 명시되어 있습니다."),
                                opt("화요일도 휴무다.", "🔵 파랑 - '화요일부터 처리 가능'과 반대되는 오답입니다."),
                                opt("휴무 이유는 공사 때문이다.", "🔵 파랑 - '창립기념일'을 다른 이유로 바꾼 오답입니다."),
                                opt("문의는 월요일부터 가능하다.", "🔵 파랑 - '화요일부터'라는 시점을 바꾼 오답입니다.")
                        ), 0, "🔵 휴무 요일이나 사유, 문의 가능 시점을 바꿔 오답을 만듭니다.",
                                "[이메일 마인드맵] 휴무(월요일, 창립기념일) + 재개(화요일). 두 시점을 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "지도/약도 설명",
                        "[약도 설명] 건물 1층 로비에서 안내데스크를 지나 오른쪽 복도 끝까지 가면 회의실이 있습니다.",
                        q("회의실로 가는 방법으로 맞는 것을 고르십시오.", List.of(
                                opt("로비 → 안내데스크 → 오른쪽 복도 끝", "정답: 설명 순서 그대로입니다."),
                                opt("로비 → 안내데스크 → 왼쪽 복도 끝", "🔴 빨강 - '오른쪽'을 '왼쪽'으로 바꾼 오답입니다."),
                                opt("2층 로비 → 안내데스크 → 복도 끝", "🔴 빨강 - '1층'을 '2층'으로 바꾼 오답입니다."),
                                opt("로비에서 바로 회의실이 보인다.", "🔴 빨강 - 중간 경로(안내데스크, 복도)를 생략한 오답입니다.")
                        ), 0, "🔴 방향, 층수를 바꾸거나 중간 경로를 생략해 오답을 만듭니다.",
                                "[약도 마인드맵] 로비 → 안내데스크 → 방향(오른쪽) → 회의실. 경로를 순서대로 색칠하세요."))
        );

        List<PassageSeed> listening4th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "여행 계획",
                        "여자: 이번 휴가에 제주도 가려고 하는데 같이 갈래요?\n남자: 좋아요! 언제 출발할 예정이에요?",
                        q("두 사람이 이야기하는 여행지로 알맞은 것을 고르십시오.", List.of(
                                opt("제주도", "정답: '제주도 가려고 한다'고 명시했습니다."),
                                opt("부산", "🔵 파랑 - 대화에 없는 지역입니다."),
                                opt("서울", "🔵 파랑 - 대화에 없는 지역입니다."),
                                opt("강릉", "🔵 파랑 - 대화에 없는 지역입니다.")
                        ), 0, "🔵 대화에 없는 다른 지역명을 넣어 오답을 만듭니다.",
                                "[여행 마인드맵] 중심 = 휴가, 가지 = 제주도. 지명 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "숙소 예약",
                        "남자: 2박 3일로 숙소를 예약하고 싶은데요.\n여자: 네, 몇 분이서 묵으실 예정이세요?",
                        q("남자가 예약하려는 숙박 일수로 알맞은 것을 고르십시오.", List.of(
                                opt("2박 3일", "정답: '2박 3일로 예약하고 싶다'고 말했습니다."),
                                opt("1박 2일", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("3박 4일", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("당일치기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 숙박 일수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[예약 마인드맵] 요청(숙소) + 기간(2박 3일). 숫자 조합을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "항공/기차 안내",
                        "[안내 방송] 부산행 KTX가 5분 후 3번 승강장에서 출발합니다. 승객 여러분께서는 서둘러 탑승해 주시기 바랍니다.",
                        q("이 안내 방송의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("KTX는 3번 승강장에서 출발한다.", "정답: '3번 승강장에서 출발'이라고 명시했습니다."),
                                opt("KTX는 5번 승강장에서 출발한다.", "🔴 빨강 - 승강장 번호를 바꾼 오답입니다."),
                                opt("KTX는 이미 출발했다.", "🔴 빨강 - '5분 후'라는 시점을 놓치게 합니다."),
                                opt("KTX는 서울행이다.", "🔴 빨강 - '부산행'을 '서울행'으로 바꾼 오답입니다.")
                        ), 0, "🔴 승강장 번호, 시점, 행선지 중 하나를 바꿔 오답을 만듭니다.",
                                "[안내 마인드맵] 시간(5분 후) + 승강장(3번) + 행선지(부산). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "관광지 설명",
                        "여자: 이 성은 조선 시대에 지어졌는데 지금도 원래 모습을 잘 간직하고 있어요.\n남자: 정말 오래된 곳이네요.",
                        q("여자가 설명하는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("조선 시대에 지어진 성", "정답: '조선 시대에 지어졌다'고 설명했습니다."),
                                opt("고려 시대에 지어진 절", "🔵 파랑 - 시대와 건물 종류를 모두 바꾼 오답입니다."),
                                opt("최근에 지어진 박물관", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("일제강점기에 지어진 학교", "🔵 파랑 - 시대와 건물 종류를 모두 바꾼 오답입니다.")
                        ), 0, "🔵 시대나 건물 종류를 바꿔 오답을 만듭니다.",
                                "[관광지 마인드맵] 시대(조선) + 종류(성) + 특징(원형 보존). 세 요소를 색칠해 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "환전/환율",
                        "남자: 오늘 환율이 얼마인지 아세요?\n여자: 오늘은 1달러에 1,300원이에요.",
                        q("오늘의 환율로 알맞은 것을 고르십시오.", List.of(
                                opt("1달러에 1,300원", "정답: '1달러에 1,300원'이라고 명시했습니다."),
                                opt("1달러에 1,200원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("1달러에 1,400원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("1달러에 1,000원", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 환율 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[환전 마인드맵] 통화(달러) + 환율(1,300원). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "여행 계획",
                        "여자: 이번엔 국내 말고 해외로 가 볼까요?\n남자: 좋아요, 일본은 어때요? 가깝고 물가도 괜찮아요.",
                        q("남자가 제안한 여행지로 알맞은 것을 고르십시오.", List.of(
                                opt("일본", "정답: '일본은 어때요'라고 제안했습니다."),
                                opt("태국", "🟢 초록 - 대화에 없는 지역입니다."),
                                opt("중국", "🟢 초록 - 대화에 없는 지역입니다."),
                                opt("베트남", "🟢 초록 - 대화에 없는 지역입니다.")
                        ), 0, "🟢 실제 언급된 지역과 무관한 다른 국가로 오답을 만듭니다.",
                                "[여행 마인드맵] 국내 → 화살표(전환) → 해외(일본). 제안 문장의 지명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "숙소 예약",
                        "남자: 조식이 포함된 방으로 예약할 수 있나요?\n여자: 네, 조식 포함 요금은 1박에 12만 원입니다.",
                        q("조식 포함 1박 요금으로 알맞은 것을 고르십시오.", List.of(
                                opt("12만 원", "정답: '조식 포함 요금은 1박에 12만 원'이라고 명시했습니다."),
                                opt("10만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("15만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("8만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 요금 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[예약 마인드맵] 조건(조식 포함) + 요금(12만 원). 조건과 숫자를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "항공/기차 안내",
                        "[안내 방송] 인천공항행 리무진 버스가 2번 게이트에서 10분 간격으로 운행되고 있습니다.",
                        q("이 안내 방송의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("리무진 버스는 10분 간격으로 운행된다.", "정답: '10분 간격으로 운행'이라고 명시했습니다."),
                                opt("리무진 버스는 30분 간격으로 운행된다.", "🔴 빨강 - 간격 숫자를 바꾼 오답입니다."),
                                opt("리무진 버스는 1번 게이트에서 탄다.", "🔴 빨강 - 게이트 번호를 바꾼 오답입니다."),
                                opt("리무진 버스는 오늘 운행하지 않는다.", "🔴 빨강 - 방송 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 운행 간격이나 게이트 번호를 바꿔 오답을 만듭니다.",
                                "[안내 마인드맵] 게이트(2번) + 간격(10분) + 행선지(인천공항). 숫자마다 색을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "관광지 설명",
                        "남자: 이 해변은 일몰이 특히 아름답기로 유명해요.\n여자: 그럼 저녁에 다시 와야겠네요.",
                        q("여자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("저녁에 해변에 다시 온다.", "정답: '저녁에 다시 와야겠네요'라고 말했습니다."),
                                opt("아침에 해변에 다시 온다.", "🟢 초록 - '저녁'을 '아침'으로 바꾼 오답입니다."),
                                opt("해변에 다시 오지 않는다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("다른 해변으로 이동한다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 시간대를 바꾸거나 반대되는 행동으로 오답을 만듭니다.",
                                "[관광지 마인드맵] 특징(일몰 아름다움) → 화살표 → 행동(저녁 재방문). 시간대 단어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "환전/환율",
                        "여자: 환전 수수료가 따로 있나요?\n남자: 네, 환전 금액의 1%가 수수료로 붙습니다.",
                        q("환전 수수료로 알맞은 것을 고르십시오.", List.of(
                                opt("환전 금액의 1%", "정답: '환전 금액의 1%가 수수료'라고 명시했습니다."),
                                opt("환전 금액의 5%", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("고정 수수료 1만 원", "🔴 빨강 - 대화에 없는 방식입니다."),
                                opt("수수료가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 수수료 비율이나 방식을 바꿔 오답을 만듭니다.",
                                "[환전 마인드맵] 질문(수수료 유무) → 답(1%). 비율 숫자를 정확히 확인하세요."))
        );

        List<PassageSeed> listening4th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "여행 계획",
                        "여자: 여행 경비는 어느 정도로 잡을까요?\n남자: 1인당 50만 원 정도면 충분할 것 같아요.",
                        q("남자가 제안한 1인 여행 경비로 알맞은 것을 고르십시오.", List.of(
                                opt("50만 원", "정답: '1인당 50만 원 정도'라고 제안했습니다."),
                                opt("30만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("100만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("20만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 경비 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[여행 마인드맵] 단위(1인당) + 금액(50만 원). 단위와 숫자를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "숙소 예약",
                        "남자: 체크인은 몇 시부터 가능한가요?\n여자: 오후 3시부터 가능합니다. 그전에는 짐만 맡기실 수 있어요.",
                        q("체크인이 가능한 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("오후 3시", "정답: '오후 3시부터 가능'이라고 명시했습니다."),
                                opt("오전 3시", "🔴 빨강 - '오후'를 '오전'으로 바꾼 오답입니다."),
                                opt("오후 1시", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("아무 때나 가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 오전/오후를 바꾸거나 시간을 슬쩍 바꿔 오답을 만듭니다.",
                                "[예약 마인드맵] 체크인(오후 3시) + 조건(그전엔 짐만). 시간과 조건을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "항공/기차 안내",
                        "[안내 방송] 기상 악화로 인해 오늘 저녁 항공편이 1시간 지연될 예정입니다. 탑승객께서는 안내를 참고해 주시기 바랍니다.",
                        q("이 안내 방송의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("항공편이 1시간 지연된다.", "정답: '1시간 지연될 예정'이라고 명시했습니다."),
                                opt("항공편이 1시간 앞당겨진다.", "🔴 빨강 - '지연'을 '단축'으로 착각하게 하는 오답입니다."),
                                opt("항공편이 취소되었다.", "🔴 빨강 - '지연'과 '취소'를 혼동하게 하는 오답입니다."),
                                opt("기상은 맑다.", "🔴 빨강 - '기상 악화'와 반대되는 오답입니다.")
                        ), 0, "🔴 지연/단축, 지연/취소를 혼동하게 하거나 날씨를 반대로 착각하게 합니다.",
                                "[안내 마인드맵] 원인(기상 악화) → 결과(1시간 지연). 원인과 결과를 화살표로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "관광지 설명",
                        "여자: 이 시장은 100년 넘는 역사를 가진 전통 시장이에요.\n남자: 그럼 오래된 가게들도 많겠네요.",
                        q("여자가 설명하는 장소의 특징으로 알맞은 것을 고르십시오.", List.of(
                                opt("100년 넘는 역사를 가진 전통 시장", "정답: 설명 그대로입니다."),
                                opt("최근에 생긴 신식 시장", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("50년 정도 된 시장", "🔵 파랑 - 연도 숫자를 바꾼 오답입니다."),
                                opt("외국인만 가는 시장", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 연도 숫자를 바꾸거나 신식/전통을 반대로 착각하게 합니다.",
                                "[관광지 마인드맵] 역사(100년) + 종류(전통 시장). 숫자와 특징을 색으로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "환전/환율",
                        "남자: 은행이랑 공항 중에 어디서 환전하는 게 더 유리해요?\n여자: 은행이 수수료가 더 저렴해요.",
                        q("여자가 추천한 환전 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("은행", "정답: '은행이 수수료가 더 저렴하다'고 추천했습니다."),
                                opt("공항", "🔴 빨강 - 비교 대상을 뒤바꾼 오답입니다."),
                                opt("환전소", "🔴 빨강 - 대화에 없는 장소입니다."),
                                opt("어디든 상관없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 비교 대상(은행/공항)을 뒤바꿔 오답을 만듭니다.",
                                "[환전 마인드맵] 은행(저렴, 정답) ↔ 공항. 비교 표현 뒤의 대상이 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "여행 계획",
                        "여자: 여행 갈 때 렌터카를 빌릴까요, 대중교통을 이용할까요?\n남자: 짐도 많으니까 렌터카가 편할 것 같아요.",
                        q("남자가 선택한 이동 수단으로 알맞은 것을 고르십시오.", List.of(
                                opt("렌터카", "정답: '렌터카가 편할 것 같다'고 답했습니다."),
                                opt("대중교통", "🔴 빨강 - 먼저 언급된 선택지와 헷갈리게 하는 오답입니다."),
                                opt("자전거", "🔴 빨강 - 대화에 없는 수단입니다."),
                                opt("도보", "🔴 빨강 - 대화에 없는 수단입니다.")
                        ), 0, "🔴 두 선택지 중 나중에 언급된 것을 놓치게 합니다.",
                                "[선택 마인드맵] 렌터카(정답) ↔ 대중교통. 이유(짐이 많음) 뒤에 나온 선택이 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "숙소 예약",
                        "남자: 취소하면 환불이 되나요?\n여자: 3일 전까지는 전액 환불되고, 이후에는 50%만 환불됩니다.",
                        q("3일 전 취소 시 환불 비율로 알맞은 것을 고르십시오.", List.of(
                                opt("전액(100%)", "정답: '3일 전까지는 전액 환불'이라고 명시했습니다."),
                                opt("50%", "🔴 빨강 - 3일 이후 조건과 헷갈리게 하는 오답입니다."),
                                opt("환불 불가", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("30%", "🔴 빨강 - 대화에 없는 숫자입니다.")
                        ), 0, "🔴 조건(3일 전/후)에 따른 환불 비율을 뒤바꿔 오답을 만듭니다.",
                                "[예약 마인드맵] 3일 전(전액) ↔ 3일 이후(50%). 조건과 비율을 색으로 짝지으세요.")),
                onePassage(PassageCategory.LISTENING, "항공/기차 안내",
                        "[안내 방송] 다음 정차역은 대전역입니다. 내리실 문은 왼쪽입니다.",
                        q("이 안내 방송의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("다음 역은 대전역이다.", "정답: '다음 정차역은 대전역'이라고 명시했습니다."),
                                opt("다음 역은 대구역이다.", "🔴 빨강 - 역 이름을 바꾼 오답입니다."),
                                opt("내리는 문은 오른쪽이다.", "🔴 빨강 - '왼쪽'을 '오른쪽'으로 바꾼 오답입니다."),
                                opt("이 역이 종착역이다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 역 이름이나 문 방향을 바꿔 오답을 만듭니다.",
                                "[안내 마인드맵] 다음 역(대전) + 문 방향(왼쪽). 두 정보를 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "관광지 설명",
                        "여자: 이 박물관은 매주 월요일에 휴관이에요.\n남자: 그럼 화요일에 가야겠네요.",
                        q("이 박물관의 휴관일로 알맞은 것을 고르십시오.", List.of(
                                opt("월요일", "정답: '매주 월요일에 휴관'이라고 명시했습니다."),
                                opt("화요일", "🔴 빨강 - 남자가 언급한 방문 예정일과 헷갈리게 합니다."),
                                opt("일요일", "🔴 빨강 - 대화에 없는 요일입니다."),
                                opt("휴관일이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 휴관일과 방문 예정일(화요일)을 혼동하게 합니다.",
                                "[관광지 마인드맵] 휴관(월요일) + 방문 예정(화요일). 두 요일을 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "환전/환율",
                        "남자: 환율이 어제보다 올랐어요, 내렸어요?\n여자: 어제보다 조금 올랐어요. 지금 환전하면 손해예요.",
                        q("여자의 조언으로 알맞은 것을 고르십시오.", List.of(
                                opt("지금 환전하면 손해다.", "정답: '지금 환전하면 손해예요'라고 말했습니다."),
                                opt("지금 환전하면 이득이다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("환율은 어제와 같다.", "🟢 초록 - '조금 올랐다'와 반대되는 오답입니다."),
                                opt("환율이 내렸다.", "🟢 초록 - '올랐다'와 반대되는 오답입니다.")
                        ), 0, "🟢 환율 상승/하락이나 이득/손해를 뒤바꿔 오답을 만듭니다.",
                                "[환전 마인드맵] 환율(상승) → 조언(지금은 손해). 방향과 결론을 색으로 연결하세요."))
        );

        List<PassageSeed> reading4th21to30 = List.of(
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 경주는 곳곳에 유적지가 많아서 걷기만 해도 역사 공부가 되는 것 같았어요. 다만 여름이라 너무 더웠던 게 아쉬웠어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("경주에는 유적지가 많다.", "정답: '유적지가 많다'고 명시했습니다."),
                                opt("경주에는 유적지가 거의 없다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("겨울에 방문해서 추웠다.", "🔵 파랑 - '여름'을 '겨울'로 바꾼 오답입니다."),
                                opt("날씨가 좋아서 만족했다.", "🔵 파랑 - '더웠던 게 아쉬웠다'와 반대되는 오답입니다.")
                        ), 0, "🔵 계절이나 만족도를 반대로 바꿔 오답을 만듭니다.",
                                "[후기 마인드맵] 장점(유적지 많음) + 단점(더위). 장단점을 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "숙박 안내문",
                        "[숙박 안내] 체크아웃은 오전 11시까지이며, 늦은 체크아웃은 프런트에 별도로 문의해 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("체크아웃은 오전 11시까지다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("체크아웃은 오후 11시까지다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("늦은 체크아웃은 불가능하다.", "🔵 파랑 - '별도로 문의'라는 가능성을 놓치게 합니다."),
                                opt("체크아웃 시간 제한이 없다.", "🔵 파랑 - 명시된 시간 제한과 반대됩니다.")
                        ), 0, "🔵 오전/오후를 바꾸거나 별도 문의 가능성을 빼고 읽게 합니다.",
                                "[안내문 마인드맵] 기본(11시까지) + 예외(문의 시 연장 가능). 기본과 예외를 색칠하세요.")),
                onePassage(PassageCategory.READING, "교통 시간표",
                        "[시간표] 서울행 고속버스는 매시 정각과 30분에 출발합니다. 마지막 차는 밤 11시입니다.",
                        q("이 시간표의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("버스는 30분 간격으로 출발한다.", "정답: '매시 정각과 30분'이 30분 간격을 의미합니다."),
                                opt("버스는 1시간 간격으로 출발한다.", "🔵 파랑 - 실제 간격(30분)의 두 배로 착각하게 합니다."),
                                opt("마지막 차는 자정이다.", "🔵 파랑 - '밤 11시'를 '자정'으로 바꾼 오답입니다."),
                                opt("버스는 하루 종일 운행하지 않는다.", "🔵 파랑 - 시간표 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 배차 간격을 두 배로 착각하게 하거나 마지막 시간을 바꿔 오답을 만듭니다.",
                                "[시간표 마인드맵] 간격(30분) + 막차(밤 11시). 두 정보를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "관광 안내 책자",
                        "[관광 안내] 이 미술관은 사진 촬영이 가능하지만, 플래시 사용은 금지되어 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("사진은 찍을 수 있지만 플래시는 안 된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("사진 촬영이 전면 금지되어 있다.", "🔵 파랑 - '촬영 가능'과 반대되는 오답입니다."),
                                opt("플래시를 사용해도 된다.", "🔵 파랑 - '플래시 금지'와 반대되는 오답입니다."),
                                opt("동영상 촬영만 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 촬영 가능 여부나 플래시 사용 가능 여부를 반대로 착각하게 합니다.",
                                "[안내문 마인드맵] 허용(사진) + 금지(플래시). 허용과 금지를 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "환전소 안내",
                        "[환전소 안내] 영업시간은 오전 9시부터 오후 6시까지이며, 여권 지참 시에만 환전이 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("여권이 있어야 환전할 수 있다.", "정답: '여권 지참 시에만 환전 가능'이라고 명시되어 있습니다."),
                                opt("여권 없이도 환전할 수 있다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("영업시간은 24시간이다.", "🔵 파랑 - 명시된 영업시간과 반대되는 오답입니다."),
                                opt("오후 6시 이후에도 환전할 수 있다.", "🔵 파랑 - 명시된 영업 종료 시간과 반대되는 오답입니다.")
                        ), 0, "🔵 필수 조건(여권)을 빼고 읽거나 영업시간을 왜곡해 오답을 만듭니다.",
                                "[환전소 마인드맵] 시간(9시~6시) + 조건(여권 지참). 조건 문장에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 부산 해운대는 밤에 야경이 정말 예뻤어요. 낮에는 사람이 너무 많아서 좀 복잡했어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("밤에 야경이 아름다웠다.", "정답: '밤에 야경이 정말 예뻤다'고 명시했습니다."),
                                opt("낮에 야경이 아름다웠다.", "🔵 파랑 - '밤'을 '낮'으로 바꾼 오답입니다."),
                                opt("낮에는 한산했다.", "🔵 파랑 - '사람이 너무 많다'와 반대되는 오답입니다."),
                                opt("밤에는 사람이 아무도 없었다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 낮/밤을 뒤바꾸거나 혼잡도를 반대로 착각하게 합니다.",
                                "[후기 마인드맵] 밤(야경 좋음) ↔ 낮(복잡함). 시간대별로 다른 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "숙박 안내문",
                        "[숙박 안내] 반려동물 동반이 가능한 객실은 별도로 예약해야 하며, 추가 요금 2만 원이 부과됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("반려동물 동반 시 추가 요금이 있다.", "정답: '추가 요금 2만 원이 부과된다'고 명시했습니다."),
                                opt("반려동물 동반은 무료다.", "🔵 파랑 - 명시된 추가 요금과 반대되는 오답입니다."),
                                opt("반려동물은 동반할 수 없다.", "🔵 파랑 - '동반 가능'과 반대되는 오답입니다."),
                                opt("모든 객실에서 반려동물이 가능하다.", "🔵 파랑 - '별도 예약' 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 무료/유료를 뒤바꾸거나 동반 가능 여부, 조건을 빼고 읽게 합니다.",
                                "[안내문 마인드맵] 조건(별도 예약) + 요금(2만 원 추가). 조건과 요금을 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "교통 시간표",
                        "[시간표] 공항버스 첫차는 오전 5시 30분, 막차는 밤 10시 30분입니다. 소요 시간은 약 1시간입니다.",
                        q("이 시간표의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("소요 시간은 약 1시간이다.", "정답: '소요 시간은 약 1시간'이라고 명시했습니다."),
                                opt("소요 시간은 약 30분이다.", "🔵 파랑 - 소요 시간을 바꾼 오답입니다."),
                                opt("첫차는 오전 6시 30분이다.", "🔵 파랑 - 첫차 시간을 바꾼 오답입니다."),
                                opt("막차는 밤 11시 30분이다.", "🔵 파랑 - 막차 시간을 바꾼 오답입니다.")
                        ), 0, "🔵 소요 시간, 첫차·막차 시간 중 하나를 바꿔 오답을 만듭니다.",
                                "[시간표 마인드맵] 첫차(5:30) + 막차(22:30) + 소요(1시간). 세 숫자를 색칠하세요.")),
                onePassage(PassageCategory.READING, "관광 안내 책자",
                        "[관광 안내] 이 전망대는 입장료가 있지만, 만 65세 이상은 무료입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("만 65세 이상은 입장료가 없다.", "정답: '만 65세 이상은 무료'라고 명시했습니다."),
                                opt("모든 사람이 무료로 입장한다.", "🔵 파랑 - 나이 조건을 빼고 읽게 하는 오답입니다."),
                                opt("만 65세 미만도 무료다.", "🔵 파랑 - 나이 조건과 반대되는 오답입니다."),
                                opt("입장료가 전혀 없는 곳이다.", "🔵 파랑 - '입장료가 있다'와 반대되는 오답입니다.")
                        ), 0, "🔵 나이 조건을 빼고 읽거나 입장료 유무를 반대로 착각하게 합니다.",
                                "[안내문 마인드맵] 기본(입장료 있음) + 예외(65세 이상 무료). 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "환전소 안내",
                        "[환전소 안내] 100만 원 이상 환전 시 사전 예약이 필요합니다. 예약 없이 방문하시면 대기 시간이 길어질 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("100만 원 이상 환전은 사전 예약이 필요하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 환전에 사전 예약이 필요하다.", "🔵 파랑 - 금액 조건(100만 원 이상)을 빼고 읽게 합니다."),
                                opt("예약 없이 가도 대기 시간이 없다.", "🔵 파랑 - '대기 시간이 길어질 수 있다'와 반대되는 오답입니다."),
                                opt("100만 원 미만은 예약이 필요하다.", "🔵 파랑 - 금액 조건을 반대로 착각하게 합니다.")
                        ), 0, "🔵 금액 조건을 빼거나 반대로 착각하게 합니다.",
                                "[환전소 마인드맵] 조건(100만 원 이상) + 결과(사전 예약 필요). 금액 기준에 색을 칠하세요."))
        );

        List<PassageSeed> reading4th31to40 = List.of(
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 강릉에서 먹은 회가 정말 신선하고 맛있었어요. 가격도 서울보다 저렴해서 좋았어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("강릉의 회는 가격이 저렴한 편이다.", "정답: '서울보다 저렴했다'고 명시했습니다."),
                                opt("강릉의 회는 서울보다 비쌌다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("회의 맛이 별로였다.", "🔵 파랑 - '신선하고 맛있었다'와 반대되는 오답입니다."),
                                opt("서울에서 회를 먹었다.", "🔵 파랑 - 장소를 혼동하게 하는 오답입니다.")
                        ), 0, "🔵 가격 비교나 맛 평가를 반대로 착각하게 합니다.",
                                "[후기 마인드맵] 장소(강릉) + 평가(신선함, 저렴함). 비교 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "숙박 안내문",
                        "[숙박 안내] 주차는 1박당 1대 무료이며, 추가 차량은 1일 5천 원의 요금이 부과됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("1대는 무료로 주차할 수 있다.", "정답: '1박당 1대 무료'라고 명시했습니다."),
                                opt("모든 차량이 무료다.", "🔵 파랑 - '1대 무료'라는 제한을 빼고 읽게 합니다."),
                                opt("주차는 전면 유료다.", "🔵 파랑 - '1대 무료'와 반대되는 오답입니다."),
                                opt("추가 차량 요금은 1만 원이다.", "🔵 파랑 - 요금 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 무료 대수 제한을 빼거나 추가 요금 숫자를 바꿔 오답을 만듭니다.",
                                "[안내문 마인드맵] 기본(1대 무료) + 추가(5천 원). 숫자 두 개를 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "교통 시간표",
                        "[시간표] 지하철 막차는 평일 자정, 주말은 밤 11시 30분에 출발합니다.",
                        q("이 시간표의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("주말 막차가 평일보다 이르다.", "정답: 주말(23:30)이 평일(자정)보다 이릅니다."),
                                opt("평일 막차가 주말보다 이르다.", "🔵 파랑 - 두 시간을 뒤바꾼 오답입니다."),
                                opt("평일과 주말 막차 시간이 같다.", "🔵 파랑 - 서로 다른 시간을 같다고 착각하게 합니다."),
                                opt("주말에는 지하철이 운행하지 않는다.", "🔵 파랑 - 시간표 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 평일/주말 막차 시간을 뒤바꾸거나 같다고 착각하게 합니다.",
                                "[시간표 마인드맵] 평일(자정) vs 주말(23:30). 두 시간을 비교하는 색을 다르게 칠하세요.")),
                onePassage(PassageCategory.READING, "관광 안내 책자",
                        "[관광 안내] 이 온천은 외국인 관광객을 위해 영어 안내판을 함께 제공합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("영어 안내판이 있다.", "정답: '영어 안내판을 함께 제공한다'고 명시했습니다."),
                                opt("안내판은 한국어로만 되어 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("외국인은 입장할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("일본어 안내판만 제공된다.", "🔵 파랑 - '영어'를 '일본어'로 바꾼 오답입니다.")
                        ), 0, "🔵 언어 종류를 바꾸거나 언급되지 않은 내용을 추가해 오답을 만듭니다.",
                                "[안내문 마인드맵] 대상(외국인) + 서비스(영어 안내판). 언어 단어에 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "환전소 안내",
                        "[환전소 안내] 소액 환전(10만 원 이하)은 예약 없이도 바로 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("10만 원 이하는 예약 없이 환전 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("10만 원 이하도 예약이 필요하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("10만 원 이상만 예약 없이 가능하다.", "🔵 파랑 - 금액 조건을 반대로 착각하게 합니다."),
                                opt("모든 환전에 예약이 필요 없다.", "🔵 파랑 - 금액 조건(10만 원 이하)을 빼고 읽게 합니다.")
                        ), 0, "🔵 금액 조건을 빼거나 반대로 착각하게 합니다.",
                                "[환전소 마인드맵] 조건(10만 원 이하) + 결과(예약 불필요). 금액 기준에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "여행 후기",
                        "[여행 후기] 전주 한옥마을에서 한복을 빌려 입고 사진을 찍었는데 정말 특별한 추억이 됐어요.",
                        q("이 글쓴이가 한옥마을에서 한 일로 알맞은 것을 고르십시오.", List.of(
                                opt("한복을 빌려 입고 사진을 찍었다.", "정답: 후기에 그대로 나와 있습니다."),
                                opt("한복을 구매했다.", "🔵 파랑 - '빌리다'를 '구매하다'로 바꾼 오답입니다."),
                                opt("전통 음식을 만들었다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("한옥에서 하룻밤 묵었다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 빌리다/구매하다를 혼동하거나 언급되지 않은 활동을 추가해 오답을 만듭니다.",
                                "[후기 마인드맵] 활동(한복 대여, 촬영). 핵심 동사(빌리다, 찍다)에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "숙박 안내문",
                        "[숙박 안내] 흡연은 지정된 흡연구역에서만 가능하며, 객실 내 흡연 시 벌금 10만 원이 부과됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("객실 내 흡연 시 벌금을 낸다.", "정답: '벌금 10만 원이 부과된다'고 명시했습니다."),
                                opt("객실 어디서나 흡연이 가능하다.", "🔵 파랑 - '지정된 흡연구역에서만'과 반대되는 오답입니다."),
                                opt("흡연은 완전히 금지되어 있다.", "🔵 파랑 - '지정 구역에서는 가능하다'는 내용을 놓치게 합니다."),
                                opt("벌금은 5만 원이다.", "🔵 파랑 - 벌금 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 흡연 가능 여부나 벌금 숫자를 바꿔 오답을 만듭니다.",
                                "[안내문 마인드맵] 허용(지정 구역) + 금지(객실 내, 벌금 10만 원). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "교통 시간표",
                        "[시간표] 셔틀버스는 30분마다 운행되며, 공휴일에는 운행하지 않습니다.",
                        q("이 시간표의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("공휴일에는 셔틀버스가 운행하지 않는다.", "정답: '공휴일에는 운행하지 않는다'고 명시했습니다."),
                                opt("공휴일에도 정상 운행한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("셔틀버스는 1시간마다 운행한다.", "🔵 파랑 - '30분'을 '1시간'으로 바꾼 오답입니다."),
                                opt("주말에만 운행하지 않는다.", "🔵 파랑 - '공휴일'을 '주말'로 바꾼 오답입니다.")
                        ), 0, "🔵 배차 간격이나 운행 중단 조건(공휴일/주말)을 바꿔 오답을 만듭니다.",
                                "[시간표 마인드맵] 간격(30분) + 예외(공휴일 운행 안 함). 예외 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "관광 안내 책자",
                        "[관광 안내] 이 동굴은 내부 온도가 낮으니 여름에도 겉옷을 챙겨 오시기 바랍니다.",
                        q("이 안내문의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("겉옷을 챙기라고 안내하려고", "정답: '겉옷을 챙겨 오시기 바랍니다'가 핵심입니다."),
                                opt("동굴 입장을 금지하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("여름철 휴관을 안내하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("동굴의 역사를 설명하려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 실제 요청과 무관한 다른 목적(금지, 휴관 안내)으로 착각하게 합니다.",
                                "[안내문 마인드맵] 특징(낮은 온도) → 요청(겉옷 챙기기). 요청 문장이 목적을 알려줍니다.")),
                onePassage(PassageCategory.READING, "환전소 안내",
                        "[환전소 안내] 환전한 금액은 당일 환불이 가능하며, 환불 시 수수료가 별도로 부과됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("환불 시 수수료가 있다.", "정답: '수수료가 별도로 부과된다'고 명시했습니다."),
                                opt("환불은 불가능하다.", "🔵 파랑 - '환불이 가능하다'와 반대되는 오답입니다."),
                                opt("환불 수수료는 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("환불은 일주일 후에 가능하다.", "🔵 파랑 - '당일'을 '일주일 후'로 바꾼 오답입니다.")
                        ), 0, "🔵 환불 가능 여부나 수수료 유무, 시점을 바꿔 오답을 만듭니다.",
                                "[환전소 마인드맵] 환불(당일 가능) + 수수료(별도 부과). 두 조건을 각각 색칠하세요."))
        );

        List<PassageSeed> listening5th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "건강 검진",
                        "여자: 이번 건강검진은 언제 예약했어요?\n남자: 다음 주 화요일 오전 10시로 예약했어요.",
                        q("남자의 건강검진 일정으로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 주 화요일 오전 10시", "정답: '다음 주 화요일 오전 10시로 예약했다'고 답했습니다."),
                                opt("이번 주 화요일 오전 10시", "🔴 빨강 - '다음 주'를 '이번 주'로 바꾼 오답입니다."),
                                opt("다음 주 화요일 오후 10시", "🔴 빨강 - 오전/오후를 바꾼 오답입니다."),
                                opt("다음 주 수요일 오전 10시", "🔴 빨강 - 요일을 바꾼 오답입니다.")
                        ), 0, "🔴 시점, 요일, 오전/오후 중 하나를 슬쩍 바꿔 오답을 만듭니다.",
                                "[검진 마인드맵] 시점(다음 주) + 요일(화요일) + 시간(오전 10시). 세 요소를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "다이어트/운동",
                        "남자: 요즘 다이어트한다면서요? 어떻게 하고 있어요?\n여자: 매일 저녁에 30분씩 걷고 있어요.",
                        q("여자가 하고 있는 운동으로 알맞은 것을 고르십시오.", List.of(
                                opt("매일 저녁 30분씩 걷기", "정답: '매일 저녁에 30분씩 걷고 있다'고 답했습니다."),
                                opt("매일 아침 30분씩 걷기", "🔴 빨강 - '저녁'을 '아침'으로 바꾼 오답입니다."),
                                opt("매일 저녁 1시간씩 뛰기", "🔴 빨강 - 시간과 운동 종류를 모두 바꾼 오답입니다."),
                                opt("일주일에 한 번 걷기", "🔴 빨강 - 빈도를 바꾼 오답입니다.")
                        ), 0, "🔴 시간대, 소요 시간, 빈도 중 하나를 바꿔 오답을 만듭니다.",
                                "[운동 마인드맵] 빈도(매일) + 시간대(저녁) + 시간(30분). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "스트레스 관리",
                        "여자: 요즘 스트레스를 어떻게 풀어요?\n남자: 저는 주말마다 등산을 가요. 그러면 마음이 편해져요.",
                        q("남자가 스트레스를 푸는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("주말마다 등산 가기", "정답: '주말마다 등산을 간다'고 답했습니다."),
                                opt("주말마다 영화 보기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("매일 등산 가기", "🟢 초록 - '주말마다'를 '매일'로 바꾼 오답입니다."),
                                opt("집에서 잠자기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 빈도를 바꾸거나 언급되지 않은 다른 방법으로 오답을 만듭니다.",
                                "[스트레스 마인드맵] 방법(등산) + 빈도(주말마다) + 효과(마음 편해짐). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "수면 습관",
                        "남자: 요즘 잠을 잘 못 자요.\n여자: 자기 전에 카페인 섭취를 줄여 보는 게 어때요?",
                        q("여자가 남자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("카페인 섭취 줄이기", "정답: '카페인 섭취를 줄여 보라'고 제안했습니다."),
                                opt("운동량 늘리기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("일찍 일어나기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("낮잠 자기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(카페인 줄이기)과 무관한 다른 조언으로 오답을 만듭니다.",
                                "[수면 마인드맵] 문제(잠 못 잠) → 제안(카페인 줄이기). 제안 문장의 목적어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 상황",
                        "여자: 갑자기 손을 베였어요! 피가 많이 나요.\n남자: 우선 깨끗한 천으로 눌러서 지혈부터 하세요.",
                        q("남자가 여자에게 먼저 하라고 한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("천으로 눌러 지혈하기", "정답: '천으로 눌러서 지혈부터 하라'고 말했습니다."),
                                opt("병원에 바로 가기", "🔴 빨강 - 언급되지 않은 순서입니다."),
                                opt("소독약 바르기", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("붕대 감기", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 '우선/먼저'라는 순서 표현을 놓치고 다른 행동을 정답처럼 만듭니다.",
                                "[응급 마인드맵] 상황(출혈) → 첫 조치(지혈). '우선'이라는 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "건강 검진",
                        "남자: 검진 결과는 언제 나와요?\n여자: 보통 일주일 정도 걸려요.",
                        q("검진 결과가 나오는 데 걸리는 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("일주일", "정답: '일주일 정도 걸린다'고 답했습니다."),
                                opt("하루", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("한 달", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("3일", "🔴 빨강 - 대화에 없는 기간입니다.")
                        ), 0, "🔴 대화에 없는 임의의 기간으로 오답을 만듭니다.",
                                "[검진 마인드맵] 질문(결과 시점) → 답(일주일). 기간 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "다이어트/운동",
                        "여자: 헬스장 등록했다면서요? 얼마나 자주 가요?\n남자: 일주일에 세 번 정도 가려고 해요.",
                        q("남자가 헬스장에 가려는 빈도로 알맞은 것을 고르십시오.", List.of(
                                opt("일주일에 세 번", "정답: '일주일에 세 번 정도 가려고 한다'고 답했습니다."),
                                opt("일주일에 한 번", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("매일", "🔴 빨강 - 대화 내용과 다른 오답입니다."),
                                opt("한 달에 세 번", "🔴 빨강 - 기간 단위를 바꾼 오답입니다.")
                        ), 0, "🔴 빈도 숫자나 기간 단위를 바꿔 오답을 만듭니다.",
                                "[운동 마인드맵] 기간(일주일) + 횟수(세 번). 단위와 숫자를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "스트레스 관리",
                        "남자: 명상이 스트레스 해소에 정말 도움이 되나요?\n여자: 네, 저도 매일 아침 10분씩 명상해요.",
                        q("여자가 명상을 하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("매일 아침 10분", "정답: '매일 아침 10분씩 명상한다'고 답했습니다."),
                                opt("매일 저녁 10분", "🔴 빨강 - '아침'을 '저녁'으로 바꾼 오답입니다."),
                                opt("매일 아침 30분", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("일주일에 한 번 10분", "🔴 빨강 - 빈도를 바꾼 오답입니다.")
                        ), 0, "🔴 시간대나 소요 시간, 빈도를 바꿔 오답을 만듭니다.",
                                "[스트레스 마인드맵] 방법(명상) + 시간대(아침) + 시간(10분). 세 요소를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "수면 습관",
                        "여자: 요즘 몇 시에 주무세요?\n남자: 보통 밤 11시쯤 자려고 노력해요.",
                        q("남자가 자려고 노력하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("밤 11시", "정답: '밤 11시쯤 자려고 노력한다'고 답했습니다."),
                                opt("밤 9시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("자정", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("새벽 1시", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 대화에 없는 임의의 시간으로 오답을 만듭니다.",
                                "[수면 마인드맵] 질문(취침 시간) → 답(밤 11시). 시간 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 상황",
                        "남자: 어지러워서 쓰러질 것 같아요.\n여자: 일단 앉아서 물을 좀 마셔 보세요.",
                        q("여자가 남자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("앉아서 물 마시기", "정답: '앉아서 물을 마셔 보라'고 제안했습니다."),
                                opt("바로 눕기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("빨리 걷기", "🟢 초록 - 상황과 반대되는 오답입니다."),
                                opt("찬물로 세수하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(앉기, 물 마시기)과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[응급 마인드맵] 증상(어지러움) → 조치(앉기+물). 제안 동사 두 개에 형광펜을 칠하세요."))
        );

        List<PassageSeed> listening5th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "건강 검진",
                        "여자: 검진 전에 금식해야 하나요?\n남자: 네, 검진 8시간 전부터 물 외에는 아무것도 드시면 안 됩니다.",
                        q("금식해야 하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("검진 8시간 전부터", "정답: '검진 8시간 전부터 금식'이라고 안내했습니다."),
                                opt("검진 4시간 전부터", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("검진 당일 아침부터", "🔴 빨강 - 구체적 시간을 놓치게 하는 오답입니다."),
                                opt("금식할 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시간 숫자를 바꾸거나 금식이 필요 없다고 착각하게 합니다.",
                                "[검진 마인드맵] 조건(8시간 전) + 예외(물은 가능). 숫자와 예외를 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "다이어트/운동",
                        "남자: 요즘 살이 빠졌네요? 비결이 뭐예요?\n여자: 저녁 6시 이후엔 아무것도 안 먹었어요.",
                        q("여자의 다이어트 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("저녁 6시 이후 금식", "정답: '저녁 6시 이후엔 아무것도 안 먹었다'고 답했습니다."),
                                opt("아침 6시 이후 금식", "🔴 빨강 - '저녁'을 '아침'으로 바꾼 오답입니다."),
                                opt("저녁 8시 이후 금식", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("하루 한 끼만 먹기", "🔴 빨강 - 언급되지 않은 방법입니다.")
                        ), 0, "🔴 시간대나 시각 숫자를 바꿔 오답을 만듭니다.",
                                "[다이어트 마인드맵] 기준 시간(저녁 6시) + 행동(금식). 시간 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "스트레스 관리",
                        "여자: 일이 너무 많아서 스트레스가 심해요.\n남자: 잠깐이라도 휴식 시간을 가지는 게 중요해요.",
                        q("남자가 여자에게 하는 말로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("조언하고 있다.", "정답: '휴식 시간을 가지는 게 중요해요'는 조언 표현입니다."),
                                opt("비판하고 있다.", "🟢 초록 - 대화 분위기와 반대되는 오답입니다."),
                                opt("질문하고 있다.", "🟢 초록 - 대화 형식과 맞지 않습니다."),
                                opt("무시하고 있다.", "🟢 초록 - 대화 분위기와 반대되는 오답입니다.")
                        ), 0, "🟢 조언 표현을 무관한 말하기 목적(비판, 무시)으로 착각하게 합니다.",
                                "[스트레스 마인드맵] 문제(일 많음) → 조언(휴식). 조언 표현(~는 게 중요해요)에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "수면 습관",
                        "남자: 낮잠을 자면 밤에 잠이 안 와요.\n여자: 낮잠은 20분 이내로 짧게 자는 게 좋대요.",
                        q("여자가 제안한 낮잠 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("20분 이내", "정답: '20분 이내로 짧게 자는 게 좋다'고 말했습니다."),
                                opt("1시간 이내", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("낮잠은 자지 않는 게 좋다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("30분 이상", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시간 숫자를 바꾸거나 아예 자지 말라고 왜곡합니다.",
                                "[수면 마인드맵] 문제(밤에 못 잠) → 조언(짧은 낮잠, 20분). 시간 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 상황",
                        "여자: 화상을 입었어요! 어떡하죠?\n남자: 일단 찬물로 15분 정도 식혀 주세요.",
                        q("남자가 제안한 화상 응급처치 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("15분 정도", "정답: '찬물로 15분 정도 식혀 주세요'라고 말했습니다."),
                                opt("5분 정도", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("1시간 정도", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("식힐 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시간 숫자를 바꾸거나 조치가 필요 없다고 왜곡합니다.",
                                "[응급 마인드맵] 상황(화상) → 조치(찬물, 15분). 숫자와 방법을 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "건강 검진",
                        "남자: 이번엔 위 내시경도 하시나요?\n여자: 네, 수면 내시경으로 예약했어요.",
                        q("여자가 예약한 내시경 종류로 알맞은 것을 고르십시오.", List.of(
                                opt("수면 내시경", "정답: '수면 내시경으로 예약했다'고 답했습니다."),
                                opt("일반 내시경", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("대장 내시경", "🔴 빨강 - '위 내시경'을 다른 종류로 바꾼 오답입니다."),
                                opt("검사를 안 받는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 검사 종류를 바꾸거나 검사를 안 받는다고 왜곡합니다.",
                                "[검진 마인드맵] 종류(위 내시경) + 방식(수면). 두 정보를 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "다이어트/운동",
                        "여자: 운동 전에 스트레칭 꼭 해야 하나요?\n남자: 네, 부상 예방을 위해 꼭 필요해요.",
                        q("남자가 스트레칭을 해야 하는 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("부상 예방을 위해", "정답: '부상 예방을 위해 꼭 필요하다'고 답했습니다."),
                                opt("체중 감량을 위해", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("시간을 절약하기 위해", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("근육을 키우기 위해", "🟢 초록 - 언급되지 않은 이유입니다.")
                        ), 0, "🟢 실제 언급된 이유(부상 예방)와 무관한 다른 목적으로 오답을 만듭니다.",
                                "[운동 마인드맵] 행동(스트레칭) → 이유(부상 예방). 이유를 나타내는 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "스트레스 관리",
                        "남자: 회사에서 받는 스트레스, 어떻게 관리하세요?\n여자: 퇴근 후에는 일 생각을 아예 안 하려고 해요.",
                        q("여자의 스트레스 관리 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("퇴근 후 일 생각 안 하기", "정답: '퇴근 후에는 일 생각을 아예 안 한다'고 답했습니다."),
                                opt("퇴근 후에도 일을 계속 한다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("동료와 상담한다.", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("일찍 퇴근한다.", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 방법과 반대되거나 무관한 다른 행동으로 오답을 만듭니다.",
                                "[스트레스 마인드맵] 시점(퇴근 후) + 방법(일 생각 안 함). 시점과 행동을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "수면 습관",
                        "여자: 잠자리에 들기 전에 뭐 하세요?\n남자: 따뜻한 물로 샤워를 해요. 그러면 잠이 잘 와요.",
                        q("남자가 자기 전에 하는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("따뜻한 물로 샤워하기", "정답: '따뜻한 물로 샤워를 한다'고 답했습니다."),
                                opt("찬물로 샤워하기", "🔴 빨강 - '따뜻한 물'을 '찬물'로 바꾼 오답입니다."),
                                opt("가벼운 운동하기", "🔴 빨강 - 언급되지 않은 행동입니다."),
                                opt("책 읽기", "🔴 빨강 - 언급되지 않은 행동입니다.")
                        ), 0, "🔴 물의 온도를 바꾸거나 언급되지 않은 다른 행동으로 오답을 만듭니다.",
                                "[수면 마인드맵] 행동(따뜻한 물 샤워) → 효과(잠이 잘 옴). 온도 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 상황",
                        "남자: 벌에 쏘였어요! 너무 아파요.\n여자: 침이 남아 있으면 먼저 빼내고 얼음으로 찜질하세요.",
                        q("여자가 안내한 응급처치 순서로 알맞은 것을 고르십시오.", List.of(
                                opt("침 제거 후 얼음찜질", "정답: '침을 먼저 빼내고 얼음으로 찜질하라'고 안내했습니다."),
                                opt("얼음찜질 후 침 제거", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("병원에 바로 가기", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("따뜻한 찜질하기", "🔴 빨강 - '얼음'을 '따뜻한 찜질'로 바꾼 오답입니다.")
                        ), 0, "🔴 처치 순서를 뒤바꾸거나 찜질 종류(얼음/따뜻함)를 바꿔 오답을 만듭니다.",
                                "[응급 마인드맵] 1(침 제거) → 2(얼음찜질). 순서 표현(먼저, 그다음)에 색을 칠하세요."))
        );

        List<PassageSeed> reading5th21to30 = List.of(
                onePassage(PassageCategory.READING, "건강 정보 기사",
                        "[건강 정보] 하루 30분씩 걷기만 해도 심혈관 질환 위험을 크게 줄일 수 있다는 연구 결과가 나왔다.",
                        q("이 기사의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("걷기가 심혈관 질환 위험을 줄인다.", "정답: 기사 내용 그대로입니다."),
                                opt("걷기가 심혈관 질환 위험을 높인다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다."),
                                opt("매일 1시간 이상 걸어야 효과가 있다.", "🔵 파랑 - '30분'을 '1시간 이상'으로 바꾼 오답입니다."),
                                opt("걷기는 효과가 없다는 연구다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 위험 증가/감소를 뒤바꾸거나 시간 숫자를 과장해 오답을 만듭니다.",
                                "[기사 마인드맵] 행동(30분 걷기) → 효과(위험 감소). 숫자와 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "운동 프로그램 안내",
                        "[운동 프로그램 안내] 초급반은 매주 화, 목요일 오전 10시에 진행되며, 준비물은 개인 매트입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초급반은 화, 목요일에 진행된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("초급반은 월, 수요일에 진행된다.", "🔵 파랑 - 요일을 바꾼 오답입니다."),
                                opt("준비물이 필요 없다.", "🔵 파랑 - '개인 매트'라는 준비물과 반대됩니다."),
                                opt("초급반은 오후에 진행된다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다.")
                        ), 0, "🔵 요일, 시간대, 준비물 유무를 바꿔 오답을 만듭니다.",
                                "[프로그램 마인드맵] 요일(화,목) + 시간(오전 10시) + 준비물(매트). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "병원 예약 확인서",
                        "[예약 확인서] 예약일: 3월 15일 오후 2시. 진료과: 내과. 예약 변경은 하루 전까지 가능합니다.",
                        q("이 확인서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("예약 변경은 하루 전까지 가능하다.", "정답: 확인서에 그대로 명시되어 있습니다."),
                                opt("예약 변경은 당일에도 가능하다.", "🔵 파랑 - '하루 전까지'라는 기한과 반대됩니다."),
                                opt("진료과는 외과다.", "🔵 파랑 - '내과'를 '외과'로 바꾼 오답입니다."),
                                opt("예약 시간은 오전 2시다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다.")
                        ), 0, "🔵 변경 가능 기한, 진료과, 오전/오후를 바꿔 오답을 만듭니다.",
                                "[확인서 마인드맵] 일시(3/15 오후 2시) + 진료과(내과) + 변경 기한(하루 전). 색칠하세요.")),
                onePassage(PassageCategory.READING, "약 복용법 설명서",
                        "[복용법 설명서] 이 약은 하루 3번, 식후 30분에 복용하십시오. 공복에 복용하면 속이 쓰릴 수 있습니다.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이 약은 식후에 복용해야 한다.", "정답: '식후 30분에 복용'이라고 명시했습니다."),
                                opt("이 약은 공복에 복용해야 한다.", "🔵 파랑 - '공복에 복용하면 속이 쓰릴 수 있다'와 반대됩니다."),
                                opt("하루 한 번만 복용한다.", "🔵 파랑 - '하루 3번'을 '하루 한 번'으로 바꾼 오답입니다."),
                                opt("복용 시간은 상관없다.", "🔵 파랑 - '식후 30분'이라는 조건과 반대됩니다.")
                        ), 0, "🔵 식전/식후, 복용 횟수를 바꿔 오답을 만듭니다.",
                                "[복용법 마인드맵] 횟수(하루 3번) + 시점(식후 30분). 조건 표현에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "응급처치 안내문",
                        "[응급처치 안내] 골절이 의심될 때는 무리하게 움직이지 말고, 부목으로 고정한 후 병원으로 이동하세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("골절 의심 시 무리하게 움직이면 안 된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("골절 의심 시 최대한 많이 움직여야 한다.", "🔵 파랑 - 안내문 내용과 반대되는 오답입니다."),
                                opt("부목 없이 바로 병원에 가야 한다.", "🔵 파랑 - '부목으로 고정한 후'라는 순서를 놓치게 합니다."),
                                opt("골절은 저절로 낫는다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 움직임 제한 여부를 반대로 착각하게 하거나 순서를 빼고 읽게 합니다.",
                                "[응급처치 마인드맵] 금지(무리한 움직임) → 조치(부목 고정) → 이동(병원). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "건강 정보 기사",
                        "[건강 정보] 물을 충분히 마시는 것이 피부 건강에도 도움이 된다는 사실이 다시 한번 확인됐다.",
                        q("이 기사의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("물을 충분히 마시면 피부에 좋다.", "정답: 기사 내용 그대로입니다."),
                                opt("물을 많이 마시면 피부에 나쁘다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다."),
                                opt("물과 피부 건강은 관련이 없다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다."),
                                opt("물 대신 우유를 마셔야 한다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 인과관계를 반대로 착각하게 하거나 무관한 내용을 추가합니다.",
                                "[기사 마인드맵] 행동(물 충분히) → 효과(피부 건강). 인과관계를 화살표로 표시하세요.")),
                onePassage(PassageCategory.READING, "운동 프로그램 안내",
                        "[운동 프로그램 안내] 요가 수업은 선착순 15명 마감이며, 신청은 홈페이지에서만 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신청은 홈페이지에서만 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("신청은 전화로도 가능하다.", "🔵 파랑 - '홈페이지에서만'이라는 제한을 놓치게 합니다."),
                                opt("정원 제한이 없다.", "🔵 파랑 - '선착순 15명'이라는 조건과 반대됩니다."),
                                opt("정원은 30명이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 신청 방법 제한을 빼거나 정원 숫자를 바꿔 오답을 만듭니다.",
                                "[프로그램 마인드맵] 정원(15명) + 신청 방법(홈페이지만). 두 조건을 색칠하세요.")),
                onePassage(PassageCategory.READING, "병원 예약 확인서",
                        "[예약 확인서] 예약 시간보다 10분 일찍 도착해 접수를 완료해 주시기 바랍니다.",
                        q("이 확인서의 요청 사항으로 알맞은 것을 고르십시오.", List.of(
                                opt("예약 시간보다 10분 일찍 도착하기", "정답: 확인서에 그대로 명시되어 있습니다."),
                                opt("예약 시간보다 늦게 도착해도 된다.", "🔵 파랑 - 요청 사항과 반대되는 오답입니다."),
                                opt("접수는 생략해도 된다.", "🔵 파랑 - '접수를 완료해 주세요'와 반대되는 오답입니다."),
                                opt("30분 일찍 도착해야 한다.", "🔵 파랑 - 시간 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 시간 숫자를 바꾸거나 요청 여부를 반대로 착각하게 합니다.",
                                "[확인서 마인드맵] 요청(10분 일찍) + 목적(접수 완료). 숫자와 목적을 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "약 복용법 설명서",
                        "[복용법 설명서] 이 약은 졸음을 유발할 수 있으니 복용 후 운전을 삼가시기 바랍니다.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("복용 후 운전을 하면 안 된다.", "정답: '운전을 삼가시기 바랍니다'라고 명시했습니다."),
                                opt("복용 후 운전해도 괜찮다.", "🔵 파랑 - 안내문 내용과 반대되는 오답입니다."),
                                opt("이 약은 졸음을 방지한다.", "🔵 파랑 - '졸음을 유발할 수 있다'와 반대되는 오답입니다."),
                                opt("운전 전에만 복용해야 한다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 운전 가능 여부나 졸음 유발/방지를 반대로 착각하게 합니다.",
                                "[복용법 마인드맵] 부작용(졸음) → 주의사항(운전 금지). 원인과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "응급처치 안내문",
                        "[응급처치 안내] 이물질이 목에 걸렸을 때는 등을 강하게 두드리는 응급처치법을 시도해 보세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("등을 두드리는 방법을 안내하고 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("가만히 기다리라고 안내한다.", "🔵 파랑 - 안내문 내용과 반대되는 오답입니다."),
                                opt("물을 마시라고 안내한다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("배를 두드리라고 안내한다.", "🔵 파랑 - '등'을 '배'로 바꾼 오답입니다.")
                        ), 0, "🔵 신체 부위를 바꾸거나 실제 조치와 무관한 다른 행동으로 오답을 만듭니다.",
                                "[응급처치 마인드맵] 상황(이물질) → 조치(등 두드리기). 신체 부위 단어를 확인하세요."))
        );

        List<PassageSeed> reading5th31to40 = List.of(
                onePassage(PassageCategory.READING, "건강 정보 기사",
                        "[건강 정보] 규칙적인 식사 시간이 소화 건강 유지에 중요하다는 전문가들의 의견이 나왔다.",
                        q("이 기사의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("규칙적인 식사 시간이 소화에 좋다.", "정답: 기사 내용 그대로입니다."),
                                opt("불규칙한 식사 시간이 소화에 좋다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다."),
                                opt("식사 시간은 소화와 관련이 없다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다."),
                                opt("하루 한 끼만 먹어야 한다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 규칙성의 좋고 나쁨을 반대로 착각하게 합니다.",
                                "[기사 마인드맵] 행동(규칙적 식사) → 효과(소화 건강). 인과관계를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "운동 프로그램 안내",
                        "[운동 프로그램 안내] 수영 강습은 4주 과정이며, 수강료는 1주 단위로 환불 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("수강료는 1주 단위로 환불 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("수강료 환불이 전혀 안 된다.", "🔵 파랑 - 명시된 환불 규정과 반대되는 오답입니다."),
                                opt("강습 기간은 8주다.", "🔵 파랑 - '4주'를 '8주'로 바꾼 오답입니다."),
                                opt("환불은 전체 금액만 가능하다.", "🔵 파랑 - '1주 단위'라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 환불 가능 여부나 기간, 환불 단위를 바꿔 오답을 만듭니다.",
                                "[프로그램 마인드맵] 기간(4주) + 환불(1주 단위). 두 조건을 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "병원 예약 확인서",
                        "[예약 확인서] 예약 취소는 홈페이지 또는 전화로 가능하며, 당일 취소 시 수수료가 발생할 수 있습니다.",
                        q("이 확인서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("당일 취소 시 수수료가 발생할 수 있다.", "정답: 확인서에 그대로 명시되어 있습니다."),
                                opt("취소는 언제 해도 수수료가 없다.", "🔵 파랑 - '당일 취소 시 수수료 발생'과 반대되는 오답입니다."),
                                opt("취소는 전화로만 가능하다.", "🔵 파랑 - '홈페이지 또는 전화'라는 내용을 놓치게 합니다."),
                                opt("예약 취소가 불가능하다.", "🔵 파랑 - 확인서 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 취소 방법 제한이나 수수료 발생 여부를 바꿔 오답을 만듭니다.",
                                "[확인서 마인드맵] 방법(홈페이지/전화) + 조건(당일 취소 시 수수료). 색칠하세요.")),
                onePassage(PassageCategory.READING, "약 복용법 설명서",
                        "[복용법 설명서] 다른 약과 함께 복용 시 부작용이 있을 수 있으니, 복용 중인 약이 있다면 의사와 상담하세요.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("다른 약 복용 중이면 의사와 상담해야 한다.", "정답: 설명서에 그대로 명시되어 있습니다."),
                                opt("다른 약과 함께 먹어도 안전하다.", "🔵 파랑 - '부작용이 있을 수 있다'와 반대되는 오답입니다."),
                                opt("의사 상담은 필요 없다.", "🔵 파랑 - '의사와 상담하세요'와 반대되는 오답입니다."),
                                opt("이 약만 복용해야 한다는 뜻이다.", "🔵 파랑 - 언급되지 않은 해석입니다.")
                        ), 0, "🔵 상담 필요 여부나 안전성 여부를 반대로 착각하게 합니다.",
                                "[복용법 마인드맵] 조건(다른 약 복용 중) → 행동(의사 상담). 조건문에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "응급처치 안내문",
                        "[응급처치 안내] 심정지 상황에서는 즉시 119에 신고하고, 가능하다면 심폐소생술을 실시하세요.",
                        q("이 안내문에서 가장 먼저 해야 할 일로 알맞은 것을 고르십시오.", List.of(
                                opt("119에 신고하기", "정답: '즉시 119에 신고하라'는 것이 첫 번째 행동입니다."),
                                opt("심폐소생술 실시하기", "🔴 빨강 - 신고보다 나중에 언급된 행동입니다."),
                                opt("환자를 옮기기", "🔴 빨강 - 언급되지 않은 행동입니다."),
                                opt("가족에게 연락하기", "🔴 빨강 - 언급되지 않은 행동입니다.")
                        ), 0, "🔴 순서상 나중에 언급된 행동을 첫 번째로 착각하게 합니다.",
                                "[응급처치 마인드맵] 1(신고) → 2(심폐소생술). '즉시'라는 단어가 붙은 행동이 최우선입니다.")),
                onePassage(PassageCategory.READING, "건강 정보 기사",
                        "[건강 정보] 스마트폰을 오래 사용하면 눈 건강에 좋지 않으므로 50분 사용 후 10분씩 휴식하는 것이 좋다.",
                        q("이 기사에서 권장하는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("50분 사용 후 10분 휴식", "정답: 기사에 그대로 명시되어 있습니다."),
                                opt("30분 사용 후 5분 휴식", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("휴식 없이 계속 사용", "🔵 파랑 - 기사 내용과 반대되는 오답입니다."),
                                opt("스마트폰을 아예 사용하지 말 것", "🔵 파랑 - 기사 내용을 과장한 오답입니다.")
                        ), 0, "🔵 숫자를 바꾸거나 권장 사항을 과장해 오답을 만듭니다.",
                                "[기사 마인드맵] 사용(50분) + 휴식(10분). 두 숫자를 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "운동 프로그램 안내",
                        "[운동 프로그램 안내] 필라테스 수업은 임산부도 참여 가능하나, 사전에 담당 강사와 상담이 필요합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("임산부는 사전 상담 후 참여할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("임산부는 참여할 수 없다.", "🔵 파랑 - '참여 가능하나'와 반대되는 오답입니다."),
                                opt("상담 없이 누구나 참여 가능하다.", "🔵 파랑 - '사전 상담 필요' 조건을 빼고 읽게 합니다."),
                                opt("임산부만 참여할 수 있다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 참여 가능 여부나 상담 필요 조건을 반대로 착각하게 합니다.",
                                "[프로그램 마인드맵] 대상(임산부) + 조건(사전 상담). 조건 문장에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "병원 예약 확인서",
                        "[예약 확인서] 진료 전 문진표를 작성해야 하며, 문진표는 병원 도착 후 접수처에서 받을 수 있습니다.",
                        q("이 확인서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("문진표는 접수처에서 받는다.", "정답: 확인서에 그대로 명시되어 있습니다."),
                                opt("문진표는 미리 집에서 작성해 온다.", "🔵 파랑 - '병원 도착 후'라는 시점과 반대됩니다."),
                                opt("문진표 작성은 선택 사항이다.", "🔵 파랑 - '작성해야 한다'는 필수 조건과 반대됩니다."),
                                opt("문진표는 온라인으로만 제공된다.", "🔵 파랑 - '접수처에서'라는 장소를 바꾼 오답입니다.")
                        ), 0, "🔵 작성 시점이나 필수 여부, 제공 장소를 바꿔 오답을 만듭니다.",
                                "[확인서 마인드맵] 순서(도착 → 접수처 → 문진표). 시점과 장소를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "약 복용법 설명서",
                        "[복용법 설명서] 이 약은 서늘하고 건조한 곳에 보관하며, 어린이 손이 닿지 않는 곳에 두어야 합니다.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("어린이 손이 닿지 않는 곳에 보관해야 한다.", "정답: 설명서에 그대로 명시되어 있습니다."),
                                opt("어린이가 보관 장소에 접근해도 된다.", "🔵 파랑 - 안내문 내용과 반대되는 오답입니다."),
                                opt("냉장 보관해야 한다.", "🔵 파랑 - '서늘하고 건조한 곳'을 '냉장'으로 바꾼 오답입니다."),
                                opt("습한 곳에 보관해도 된다.", "🔵 파랑 - '건조한 곳'과 반대되는 오답입니다.")
                        ), 0, "🔵 보관 장소 조건(온도, 습도, 접근성)을 반대로 착각하게 합니다.",
                                "[복용법 마인드맵] 보관(서늘, 건조) + 안전(어린이 접근 금지). 조건을 색칠하세요.")),
                onePassage(PassageCategory.READING, "응급처치 안내문",
                        "[응급처치 안내] 눈에 이물질이 들어갔을 때는 비비지 말고 깨끗한 물로 씻어내세요.",
                        q("이 안내문에서 하지 말아야 할 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("눈을 비비는 것", "정답: '비비지 말고'라고 금지했습니다."),
                                opt("물로 씻어내는 것", "🔴 빨강 - 안내문에서 권장하는 행동입니다."),
                                opt("병원에 가는 것", "🔴 빨강 - 언급되지 않은 금지 행동입니다."),
                                opt("눈을 감는 것", "🔴 빨강 - 언급되지 않은 금지 행동입니다.")
                        ), 0, "🔴 금지 행동과 권장 행동을 뒤바꿔 오답을 만듭니다.",
                                "[응급처치 마인드맵] 금지(비비기) ↔ 권장(물로 씻기). 반대되는 두 행동을 다른 색으로 칠하세요."))
        );

        List<PassageSeed> listening6th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "진로/직업",
                        "여자: 앞으로 어떤 일을 하고 싶어요?\n남자: 저는 어릴 때부터 선생님이 되고 싶었어요.",
                        q("남자의 장래 희망으로 알맞은 것을 고르십시오.", List.of(
                                opt("선생님", "정답: '선생님이 되고 싶었다'고 답했습니다."),
                                opt("의사", "🟢 초록 - 언급되지 않은 직업입니다."),
                                opt("요리사", "🟢 초록 - 언급되지 않은 직업입니다."),
                                opt("경찰관", "🟢 초록 - 언급되지 않은 직업입니다.")
                        ), 0, "🟢 실제 언급된 직업과 무관한 다른 직업으로 오답을 만듭니다.",
                                "[진로 마인드맵] 중심 = 장래 희망, 가지 = 선생님. 직업명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "이직/취업",
                        "남자: 이번에 새 회사로 옮기신다면서요?\n여자: 네, 다음 달부터 출근하기로 했어요.",
                        q("여자가 새 회사에 출근하는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 달부터", "정답: '다음 달부터 출근하기로 했다'고 답했습니다."),
                                opt("이번 달부터", "🔴 빨강 - '다음 달'을 '이번 달'로 바꾼 오답입니다."),
                                opt("내년부터", "🔴 빨강 - 시점을 과장한 오답입니다."),
                                opt("이미 출근했다.", "🔴 빨강 - 미래 시점을 이미 끝난 일로 착각하게 합니다.")
                        ), 0, "🔴 미래 시점을 바꾸거나 이미 끝난 일로 착각하게 합니다.",
                                "[이직 마인드맵] 지금 → 다음 달(출근 예정). 시제 표현에 색을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "회의 결과 보고",
                        "여자: 오늘 회의 결과가 어떻게 됐어요?\n남자: 새 프로젝트를 다음 분기에 시작하기로 결정됐어요.",
                        q("회의에서 결정된 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("새 프로젝트를 다음 분기에 시작한다.", "정답: '다음 분기에 시작하기로 결정됐다'고 보고했습니다."),
                                opt("새 프로젝트를 이번 분기에 시작한다.", "🔴 빨강 - '다음 분기'를 '이번 분기'로 바꾼 오답입니다."),
                                opt("새 프로젝트가 취소됐다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("새 프로젝트는 이미 시작됐다.", "🔴 빨강 - 미래 계획을 이미 끝난 일로 착각하게 합니다.")
                        ), 0, "🔴 시점을 바꾸거나 취소/완료로 왜곡해 오답을 만듭니다.",
                                "[보고 마인드맵] 결정(프로젝트 시작) + 시점(다음 분기). 시점 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "프로젝트 진행",
                        "남자: 프로젝트 진행 상황이 어때요?\n여자: 절반 정도 완료됐고, 다음 주까지 마무리할 예정이에요.",
                        q("프로젝트의 현재 진행 상황으로 알맞은 것을 고르십시오.", List.of(
                                opt("절반 정도 완료됐다.", "정답: '절반 정도 완료됐다'고 답했습니다."),
                                opt("전부 완료됐다.", "🔴 빨강 - '절반'을 '전부'로 과장한 오답입니다."),
                                opt("아직 시작하지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("완료 시점이 정해지지 않았다.", "🔴 빨강 - '다음 주까지'라는 명시된 시점과 반대됩니다.")
                        ), 0, "🔴 진행률을 과장하거나 시작 여부, 완료 시점을 왜곡합니다.",
                                "[프로젝트 마인드맵] 현재(절반 완료) → 목표(다음 주 완료). 진행률 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "협업/팀워크",
                        "여자: 이번 프로젝트는 혼자 하기 힘들 것 같아요.\n남자: 그럼 다른 팀원들과 역할을 나눠서 해 봐요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("역할을 나눠서 하기", "정답: '역할을 나눠서 해 보자'고 제안했습니다."),
                                opt("혼자 계속 하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("프로젝트를 포기하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("외부 업체에 맡기기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(역할 분담)과 무관한 다른 해결책으로 오답을 만듭니다.",
                                "[협업 마인드맵] 문제(혼자 힘듦) → 해결책(역할 분담). 제안 문장의 핵심 동사를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "진로/직업",
                        "남자: 요즘 진로 때문에 고민이 많아요.\n여자: 적성 검사를 한번 받아 보는 건 어때요?",
                        q("여자가 남자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("적성 검사 받기", "정답: '적성 검사를 받아 보라'고 제안했습니다."),
                                opt("휴학하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("아르바이트 해보기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("부모님과 상담하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(적성 검사)과 무관한 다른 조언으로 오답을 만듭니다.",
                                "[진로 마인드맵] 고민(진로) → 제안(적성 검사). 제안 문장의 목적어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이직/취업",
                        "여자: 면접 결과는 언제 나온대요?\n남자: 이번 주 금요일에 연락 준다고 했어요.",
                        q("면접 결과 발표일로 알맞은 것을 고르십시오.", List.of(
                                opt("이번 주 금요일", "정답: '이번 주 금요일에 연락 준다'고 답했습니다."),
                                opt("다음 주 금요일", "🔴 빨강 - '이번 주'를 '다음 주'로 바꾼 오답입니다."),
                                opt("이번 주 월요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("발표일이 정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시점이나 요일을 바꿔 오답을 만듭니다.",
                                "[취업 마인드맵] 질문(결과 시점) → 답(이번 주 금요일). 시점 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "회의 결과 보고",
                        "남자: 이번 회의에서 예산안이 통과됐나요?\n여자: 아니요, 다음 회의에서 다시 논의하기로 했어요.",
                        q("예산안에 대한 결과로 알맞은 것을 고르십시오.", List.of(
                                opt("예산안 논의가 다음 회의로 미뤄졌다.", "정답: '다음 회의에서 다시 논의하기로 했다'고 답했습니다."),
                                opt("예산안이 통과됐다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("예산안이 완전히 폐기됐다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("예산안 논의가 오늘 끝났다.", "🔴 빨강 - '다음 회의'라는 시점을 놓치게 합니다.")
                        ), 0, "🔴 통과/미결정/폐기를 혼동하게 하거나 시점을 왜곡합니다.",
                                "[보고 마인드맵] 오늘(미통과) → 다음 회의(재논의). 시점과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "프로젝트 진행",
                        "여자: 이번 프로젝트 마감일이 언제였죠?\n남자: 원래 이번 달 말이었는데 다음 달 초로 연기됐어요.",
                        q("프로젝트의 최종 마감일로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 달 초", "정답: '다음 달 초로 연기됐다'는 것이 최종 마감일입니다."),
                                opt("이번 달 말", "🔴 빨강 - 변경 전 날짜와 헷갈리게 하는 오답입니다."),
                                opt("이번 달 초", "🔴 빨강 - 대화에 없는 시점입니다."),
                                opt("다음 달 말", "🔴 빨강 - 대화에 없는 시점입니다.")
                        ), 0, "🔴 변경 전/후 날짜를 혼동하게 합니다.",
                                "[프로젝트 마인드맵] 이번 달 말(변경 전) → 화살표 → 다음 달 초(변경 후). 화살표 뒤가 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "협업/팀워크",
                        "남자: 팀 회의에서 제 의견이 잘 안 받아들여지는 것 같아요.\n여자: 더 구체적인 자료를 준비해서 설명해 보는 게 어때요?",
                        q("여자가 남자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("구체적인 자료 준비하기", "정답: '구체적인 자료를 준비해 보라'고 제안했습니다."),
                                opt("의견을 포기하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("팀을 바꾸기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("회의에 불참하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(자료 준비)과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[협업 마인드맵] 문제(의견 안 받아들여짐) → 해결책(자료 준비). 제안 동사를 확인하세요."))
        );

        List<PassageSeed> listening6th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "진로/직업",
                        "여자: 대학원에 진학할까 고민 중이에요.\n남자: 먼저 관심 있는 분야의 교수님과 상담해 보는 게 좋을 것 같아요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("교수님과 상담하기", "정답: '교수님과 상담해 보라'고 제안했습니다."),
                                opt("바로 진학 신청하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("취업을 먼저 하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("유학 준비하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(상담)과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[진로 마인드맵] 고민(대학원 진학) → 제안(교수 상담). 제안 문장의 대상을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이직/취업",
                        "남자: 이번 채용 공고에 지원 자격이 어떻게 되나요?\n여자: 경력 3년 이상이면 지원 가능합니다.",
                        q("지원 가능한 경력 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("경력 3년 이상", "정답: '경력 3년 이상이면 지원 가능하다'고 답했습니다."),
                                opt("경력 5년 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("신입만 가능", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("경력 제한 없음", "🔴 빨강 - 명시된 조건과 반대되는 오답입니다.")
                        ), 0, "🔴 경력 연차 숫자를 바꾸거나 조건 자체를 왜곡합니다.",
                                "[채용 마인드맵] 조건(경력 3년 이상). 숫자와 조건 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "회의 결과 보고",
                        "여자: 회의에서 신제품 출시일이 정해졌나요?\n남자: 네, 다음 달 15일로 확정됐어요.",
                        q("신제품 출시일로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 달 15일", "정답: '다음 달 15일로 확정됐다'고 답했습니다."),
                                opt("이번 달 15일", "🔴 빨강 - '다음 달'을 '이번 달'로 바꾼 오답입니다."),
                                opt("다음 달 25일", "🔴 빨강 - 날짜 숫자를 바꾼 오답입니다."),
                                opt("아직 미정이다.", "🔴 빨강 - '확정됐다'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 날짜나 월을 바꾸거나 확정 여부를 반대로 착각하게 합니다.",
                                "[보고 마인드맵] 결과(출시일 확정) + 날짜(다음 달 15일). 날짜 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "프로젝트 진행",
                        "남자: 이번 프로젝트에서 제일 어려운 부분이 뭐였어요?\n여자: 일정 관리가 가장 힘들었어요.",
                        q("여자가 가장 어려웠다고 말한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("일정 관리", "정답: '일정 관리가 가장 힘들었다'고 답했습니다."),
                                opt("예산 관리", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("팀원 소통", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("자료 조사", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 실제 언급된 어려움과 무관한 다른 항목으로 오답을 만듭니다.",
                                "[프로젝트 마인드맵] 질문(가장 어려운 점) → 답(일정 관리). 핵심 명사에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "협업/팀워크",
                        "여자: 이번 프로젝트, 다른 부서와 협업해야 한다면서요?\n남자: 네, 다음 주부터 마케팅팀과 함께 진행해요.",
                        q("남자가 함께 일하게 될 부서로 알맞은 것을 고르십시오.", List.of(
                                opt("마케팅팀", "정답: '마케팅팀과 함께 진행한다'고 답했습니다."),
                                opt("영업팀", "🔵 파랑 - 대화에 없는 부서입니다."),
                                opt("개발팀", "🔵 파랑 - 대화에 없는 부서입니다."),
                                opt("인사팀", "🔵 파랑 - 대화에 없는 부서입니다.")
                        ), 0, "🔵 대화에 없는 다른 부서명으로 오답을 만듭니다.",
                                "[협업 마인드맵] 시점(다음 주) + 대상(마케팅팀). 부서명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "진로/직업",
                        "남자: 졸업 후에 바로 취업할 거예요, 아니면 유학 갈 거예요?\n여자: 저는 취업을 먼저 하려고요.",
                        q("여자의 졸업 후 계획으로 알맞은 것을 고르십시오.", List.of(
                                opt("취업을 먼저 한다.", "정답: '취업을 먼저 하려고요'라고 답했습니다."),
                                opt("유학을 먼저 간다.", "🔴 빨강 - 두 선택지 중 반대되는 것을 정답처럼 만듭니다."),
                                opt("둘 다 하지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("취업과 유학을 동시에 한다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 두 선택지 중 하나를 뒤바꿔 오답을 만듭니다.",
                                "[진로 마인드맵] 취업(정답) ↔ 유학. '먼저'라는 표현 뒤의 선택이 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "이직/취업",
                        "여자: 새 직장은 어때요? 적응은 잘 되고 있어요?\n남자: 아직 3주밖에 안 됐는데 벌써 편해졌어요.",
                        q("남자가 새 직장에 다닌 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("3주", "정답: '아직 3주밖에 안 됐다'고 답했습니다."),
                                opt("3일", "🔴 빨강 - 단위(주/일)를 바꾼 오답입니다."),
                                opt("3개월", "🔴 빨강 - 단위를 바꾼 오답입니다."),
                                opt("1년", "🔴 빨강 - 대화에 없는 기간입니다.")
                        ), 0, "🔴 시간 단위(일/주/개월)를 바꿔 오답을 만듭니다.",
                                "[취업 마인드맵] 기간(3주) + 상태(편해짐). 숫자와 단위를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "회의 결과 보고",
                        "남자: 오늘 회의는 몇 시간이나 했어요?\n여자: 원래 1시간 예정이었는데 2시간 넘게 했어요.",
                        q("실제 회의가 진행된 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2시간 넘게", "정답: '2시간 넘게 했다'고 답했습니다."),
                                opt("1시간", "🔴 빨강 - 원래 예정 시간과 헷갈리게 하는 오답입니다."),
                                opt("30분", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("3시간", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 예정 시간과 실제 시간을 혼동하게 합니다.",
                                "[보고 마인드맵] 예정(1시간) → 실제(2시간 넘음). 화살표 뒤 숫자가 실제 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "프로젝트 진행",
                        "여자: 프로젝트 최종 보고서는 누가 작성하나요?\n남자: 이번엔 제가 작성하기로 했어요.",
                        q("보고서를 작성하기로 한 사람으로 알맞은 것을 고르십시오.", List.of(
                                opt("남자", "정답: '제가 작성하기로 했다'고 답했습니다."),
                                opt("여자", "🟢 초록 - 질문한 사람과 답한 사람을 혼동하게 하는 오답입니다."),
                                opt("팀장", "🟢 초록 - 언급되지 않은 인물입니다."),
                                opt("외부 업체", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 화자(남자/여자)를 혼동하게 하는 오답을 만듭니다.",
                                "[프로젝트 마인드맵] 질문(누가) → 답(제가=남자). 화자를 정확히 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "협업/팀워크",
                        "남자: 이번 팀 프로젝트 결과가 좋아서 다행이에요.\n여자: 다 함께 노력한 덕분이죠.",
                        q("여자가 프로젝트 성공 요인으로 말한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("모두가 함께 노력한 것", "정답: '다 함께 노력한 덕분'이라고 답했습니다."),
                                opt("운이 좋았던 것", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("리더 한 사람의 능력", "🟢 초록 - '다 함께'와 반대되는 오답입니다."),
                                opt("충분한 예산", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 협력의 결과를 개인의 능력이나 무관한 요인으로 왜곡합니다.",
                                "[협업 마인드맵] 결과(성공) → 원인(함께 노력). '다 함께'라는 표현에 형광펜을 칠하세요."))
        );

        List<PassageSeed> reading6th21to30 = List.of(
                onePassage(PassageCategory.READING, "채용 정보",
                        "[채용 공고] 마케팅 신입사원 모집. 지원 자격: 4년제 대학 졸업(예정)자. 접수 기간: 4월 1일~4월 15일.",
                        q("이 채용 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("접수 기간은 4월 1일부터 15일까지다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("접수 기간에 제한이 없다.", "🔵 파랑 - 명시된 기간과 반대되는 오답입니다."),
                                opt("경력자만 지원 가능하다.", "🔵 파랑 - '신입사원 모집'과 반대되는 오답입니다."),
                                opt("대학원 졸업자만 지원 가능하다.", "🔵 파랑 - '4년제 대학'을 '대학원'으로 바꾼 오답입니다.")
                        ), 0, "🔵 접수 기간이나 지원 자격(신입/경력, 학력)을 바꿔 오답을 만듭니다.",
                                "[채용 마인드맵] 직무(마케팅) + 자격(대졸) + 기간(4/1~4/15). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "사내 공지",
                        "[사내 공지] 다음 주 수요일은 전 직원 워크숍으로 정상 근무하지 않습니다. 참석은 필수입니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("워크숍 참석은 필수다.", "정답: '참석은 필수입니다'라고 명시했습니다."),
                                opt("워크숍 참석은 선택이다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("수요일은 정상 근무한다.", "🔵 파랑 - '정상 근무하지 않는다'와 반대되는 오답입니다."),
                                opt("워크숍은 이번 주다.", "🔵 파랑 - '다음 주'를 '이번 주'로 바꾼 오답입니다.")
                        ), 0, "🔵 필수/선택을 뒤바꾸거나 시점, 근무 여부를 바꿔 오답을 만듭니다.",
                                "[공지 마인드맵] 시점(다음 주 수요일) + 참석(필수). 두 정보를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "업무 이메일",
                        "[이메일] 제목: 회의 일정 변경 안내\n내용: 내일 예정된 회의가 모레 오후 3시로 변경되었습니다. 참고 부탁드립니다.",
                        q("이 이메일의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("회의는 모레로 변경됐다.", "정답: '모레 오후 3시로 변경'이라고 명시했습니다."),
                                opt("회의는 예정대로 내일 진행된다.", "🔵 파랑 - 변경 사실과 반대되는 오답입니다."),
                                opt("회의가 취소됐다.", "🔵 파랑 - '변경'을 '취소'로 착각하게 하는 오답입니다."),
                                opt("회의 시간은 오전이다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다.")
                        ), 0, "🔵 변경/취소를 혼동하거나 날짜, 시간대를 바꿔 오답을 만듭니다.",
                                "[이메일 마인드맵] 변경 전(내일) → 변경 후(모레, 오후 3시). 화살표 뒤가 최종 정답입니다.")),
                onePassage(PassageCategory.READING, "회의록 요약",
                        "[회의록 요약] 안건: 신규 서비스 출시 일정. 결론: 마케팅 자료 준비 완료 후 6월 초 출시하기로 결정.",
                        q("이 회의록의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("6월 초에 서비스를 출시하기로 했다.", "정답: 회의록에 그대로 명시되어 있습니다."),
                                opt("5월 말에 출시하기로 했다.", "🔵 파랑 - 시점을 바꾼 오답입니다."),
                                opt("출시를 무기한 연기하기로 했다.", "🔵 파랑 - 회의록 내용과 반대되는 오답입니다."),
                                opt("마케팅 자료 없이 바로 출시한다.", "🔵 파랑 - '준비 완료 후'라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 출시 시점을 바꾸거나 조건(자료 준비)을 빼고 읽게 합니다.",
                                "[회의록 마인드맵] 조건(자료 준비 완료) → 결과(6월 초 출시). 조건과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "팀 프로젝트 안내",
                        "[팀 프로젝트 안내] 4인 1조로 팀을 구성하며, 중간 발표는 다음 달 첫째 주에 진행됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("팀은 4명으로 구성된다.", "정답: '4인 1조'라고 명시했습니다."),
                                opt("팀은 2명으로 구성된다.", "🔵 파랑 - 인원수를 바꾼 오답입니다."),
                                opt("중간 발표는 이번 달이다.", "🔵 파랑 - '다음 달'을 '이번 달'로 바꾼 오답입니다."),
                                opt("중간 발표는 없다.", "🔵 파랑 - 안내문 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 인원수나 발표 시점을 바꿔 오답을 만듭니다.",
                                "[프로젝트 마인드맵] 팀 구성(4인) + 발표 시점(다음 달 첫째 주). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "채용 정보",
                        "[채용 공고] 개발자 경력직 모집. 근무지는 서울이며, 재택근무는 주 2회 가능합니다.",
                        q("이 채용 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("재택근무가 주 2회 가능하다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("재택근무가 전혀 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("매일 재택근무가 가능하다.", "🔵 파랑 - '주 2회'라는 제한을 빼고 읽게 합니다."),
                                opt("근무지는 부산이다.", "🔵 파랑 - '서울'을 '부산'으로 바꾼 오답입니다.")
                        ), 0, "🔵 재택근무 가능 횟수나 근무지를 바꿔 오답을 만듭니다.",
                                "[채용 마인드맵] 근무지(서울) + 재택(주 2회). 숫자와 지역을 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "사내 공지",
                        "[사내 공지] 사무실 이전으로 인해 이번 주 금요일부터 새 주소로 우편물을 보내주시기 바랍니다.",
                        q("이 공지의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("새 주소로 우편물을 보내 달라고 요청하려고", "정답: 공지 내용 그대로 요청하고 있습니다."),
                                opt("사무실을 폐쇄하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("직원을 채용하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("회의를 취소하려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 실제 요청(주소 변경 안내)과 무관한 다른 목적으로 오답을 만듭니다.",
                                "[공지 마인드맵] 상황(이전) → 요청(새 주소로 발송). 요청 문장이 목적을 알려줍니다.")),
                onePassage(PassageCategory.READING, "업무 이메일",
                        "[이메일] 제목: 자료 요청\n내용: 다음 주 발표를 위해 이번 주 목요일까지 관련 자료를 보내 주시면 감사하겠습니다.",
                        q("이 이메일에서 요청한 마감 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("이번 주 목요일", "정답: '이번 주 목요일까지'라고 명시했습니다."),
                                opt("다음 주 목요일", "🔵 파랑 - '이번 주'를 '다음 주'로 바꾼 오답입니다."),
                                opt("이번 주 금요일", "🔵 파랑 - 요일을 바꾼 오답입니다."),
                                opt("기한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다.")
                        ), 0, "🔵 기한 요일이나 주(이번/다음)를 바꿔 오답을 만듭니다.",
                                "[이메일 마인드맵] 목적(발표) + 기한(이번 주 목요일). 기한 표현에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "회의록 요약",
                        "[회의록 요약] 안건: 예산 편성. 결론: 마케팅 예산을 기존보다 20% 늘리기로 합의했다.",
                        q("이 회의록의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("마케팅 예산이 20% 증가한다.", "정답: '20% 늘리기로 합의했다'고 명시했습니다."),
                                opt("마케팅 예산이 20% 감소한다.", "🔵 파랑 - 증가/감소를 뒤바꾼 오답입니다."),
                                opt("예산은 그대로 유지된다.", "🔵 파랑 - 회의록 내용과 반대되는 오답입니다."),
                                opt("예산이 50% 늘어난다.", "🔵 파랑 - 비율 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 증가/감소를 뒤바꾸거나 비율 숫자를 바꿔 오답을 만듭니다.",
                                "[회의록 마인드맵] 항목(마케팅 예산) + 변화(20% 증가). 비율에 색을 칠하세요.")),
                onePassage(PassageCategory.READING, "팀 프로젝트 안내",
                        "[팀 프로젝트 안내] 최종 결과물은 PPT 형식으로 제출하며, 분량은 20페이지 이내로 제한됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("결과물 분량은 20페이지 이내다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("분량 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("결과물은 워드 파일로 제출한다.", "🔵 파랑 - 'PPT 형식'을 '워드 파일'로 바꾼 오답입니다."),
                                opt("30페이지까지 가능하다.", "🔵 파랑 - 페이지 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 분량 숫자나 제출 형식을 바꿔 오답을 만듭니다.",
                                "[프로젝트 마인드맵] 형식(PPT) + 분량(20페이지 이내). 형식과 숫자를 색칠하세요."))
        );

        List<PassageSeed> reading6th31to40 = List.of(
                onePassage(PassageCategory.READING, "채용 정보",
                        "[채용 공고] 디자이너 인턴 모집. 근무 기간은 3개월이며, 우수 인턴은 정규직 전환 기회가 주어집니다.",
                        q("이 채용 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("우수 인턴은 정규직 전환 기회가 있다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("모든 인턴이 정규직으로 전환된다.", "🔵 파랑 - '우수 인턴'이라는 조건을 빼고 읽게 합니다."),
                                opt("근무 기간은 6개월이다.", "🔵 파랑 - '3개월'을 '6개월'로 바꾼 오답입니다."),
                                opt("정규직 전환 기회가 전혀 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(우수 인턴)을 빼거나 기간, 전환 가능성을 바꿔 오답을 만듭니다.",
                                "[채용 마인드맵] 기간(3개월) + 조건부 혜택(정규직 전환). 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "사내 공지",
                        "[사내 공지] 냉방기 점검으로 인해 오늘 오후 2시부터 4시까지 사무실 냉방이 일시 중단됩니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("오후 2시부터 4시까지 냉방이 중단된다.", "정답: 공지에 그대로 명시되어 있습니다."),
                                opt("냉방은 하루 종일 중단된다.", "🔵 파랑 - 시간 범위를 과장한 오답입니다."),
                                opt("난방기를 점검한다.", "🔵 파랑 - '냉방기'를 '난방기'로 바꾼 오답입니다."),
                                opt("점검은 내일 진행된다.", "🔵 파랑 - '오늘'을 '내일'로 바꾼 오답입니다.")
                        ), 0, "🔵 시간 범위를 과장하거나 냉방/난방, 날짜를 바꿔 오답을 만듭니다.",
                                "[공지 마인드맵] 시간(2시~4시) + 대상(냉방기). 시작·종료 시간을 각각 색칠하세요.")),
                onePassage(PassageCategory.READING, "업무 이메일",
                        "[이메일] 제목: 회식 안내\n내용: 이번 주 금요일 저녁 6시에 회식이 있습니다. 불참하시는 분은 미리 알려주세요.",
                        q("이 이메일의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("불참자는 미리 알려야 한다.", "정답: '미리 알려주세요'라고 명시했습니다."),
                                opt("회식은 필수 참석이다.", "🔵 파랑 - '불참하시는 분'이라는 표현과 반대되는 과장된 오답입니다."),
                                opt("회식은 다음 주다.", "🔵 파랑 - '이번 주'를 '다음 주'로 바꾼 오답입니다."),
                                opt("회식 장소가 정해지지 않았다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 참석 필수 여부를 과장하거나 시점을 바꿔 오답을 만듭니다.",
                                "[이메일 마인드맵] 일정(금요일 6시) + 요청(불참 시 사전 통보). 두 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "회의록 요약",
                        "[회의록 요약] 안건: 고객 불만 대응. 결론: 전담 상담팀을 신설하여 다음 달부터 운영하기로 했다.",
                        q("이 회의록의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("전담 상담팀이 다음 달부터 운영된다.", "정답: 회의록에 그대로 명시되어 있습니다."),
                                opt("전담 상담팀은 이미 운영 중이다.", "🔵 파랑 - 미래 계획을 이미 완료로 착각하게 합니다."),
                                opt("전담 상담팀 신설이 무산됐다.", "🔵 파랑 - 회의록 내용과 반대되는 오답입니다."),
                                opt("기존 팀이 그대로 유지된다.", "🔵 파랑 - '신설'과 반대되는 오답입니다.")
                        ), 0, "🔵 미래 계획을 완료로 착각하게 하거나 신설/유지를 뒤바꿉니다.",
                                "[회의록 마인드맵] 결정(팀 신설) + 시점(다음 달부터). 결정과 시점을 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "팀 프로젝트 안내",
                        "[팀 프로젝트 안내] 팀원 간 역할 분담표는 이번 주 안에 제출해야 하며, 담당 교수의 승인이 필요합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("역할 분담표는 교수의 승인이 필요하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("역할 분담표는 승인 없이 제출만 하면 된다.", "🔵 파랑 - '승인이 필요하다'는 조건을 빼고 읽게 합니다."),
                                opt("분담표 제출 기한은 다음 달이다.", "🔵 파랑 - '이번 주'를 '다음 달'로 바꾼 오답입니다."),
                                opt("역할 분담은 자유롭게 해도 된다.", "🔵 파랑 - 승인 절차가 있다는 내용과 반대됩니다.")
                        ), 0, "🔵 승인 필요 여부를 빼거나 제출 기한을 바꿔 오답을 만듭니다.",
                                "[프로젝트 마인드맵] 기한(이번 주) + 조건(교수 승인). 조건 문장에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "채용 정보",
                        "[채용 공고] 고객 상담직 모집. 외국어(영어 또는 중국어) 가능자 우대. 근무 형태는 교대 근무입니다.",
                        q("이 채용 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("외국어 가능자는 우대받는다.", "정답: '외국어 가능자 우대'라고 명시했습니다."),
                                opt("외국어를 못 하면 지원할 수 없다.", "🔵 파랑 - '우대'를 '필수'로 과장한 오답입니다."),
                                opt("근무는 고정 근무제다.", "🔵 파랑 - '교대 근무'와 반대되는 오답입니다."),
                                opt("일본어 가능자만 우대한다.", "🔵 파랑 - 언급된 언어(영어, 중국어)를 바꾼 오답입니다.")
                        ), 0, "🔵 '우대'를 '필수'로 과장하거나 근무 형태, 언어를 바꿔 오답을 만듭니다.",
                                "[채용 마인드맵] 우대 조건(외국어) + 근무 형태(교대). '우대'는 필수가 아님을 기억하세요.")),
                onePassage(PassageCategory.READING, "사내 공지",
                        "[사내 공지] 신입사원 오리엔테이션은 3층 대회의실에서 오전 9시에 시작됩니다. 10분 전까지 착석해 주세요.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("10분 전까지 착석해야 한다.", "정답: 공지에 그대로 명시되어 있습니다."),
                                opt("늦게 도착해도 상관없다.", "🔵 파랑 - '10분 전 착석'이라는 요청과 반대되는 오답입니다."),
                                opt("장소는 2층이다.", "🔵 파랑 - '3층'을 '2층'으로 바꾼 오답입니다."),
                                opt("오후에 시작한다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다.")
                        ), 0, "🔵 층수, 시간대를 바꾸거나 요청 사항을 반대로 착각하게 합니다.",
                                "[공지 마인드맵] 장소(3층) + 시간(오전 9시) + 요청(10분 전 착석). 세 요소를 색칠하세요.")),
                onePassage(PassageCategory.READING, "업무 이메일",
                        "[이메일] 제목: 출장 보고서 제출\n내용: 지난주 출장 보고서를 아직 받지 못했습니다. 오늘 중으로 제출 부탁드립니다.",
                        q("이 이메일에서 요청한 제출 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("오늘 중으로", "정답: '오늘 중으로 제출 부탁드립니다'라고 명시했습니다."),
                                opt("내일까지", "🔵 파랑 - '오늘'을 '내일'로 바꾼 오답입니다."),
                                opt("다음 주까지", "🔵 파랑 - 기한을 늘린 오답입니다."),
                                opt("이미 제출 기한이 지났으니 필요 없다.", "🔵 파랑 - 여전히 제출을 요청하고 있는 내용과 반대됩니다.")
                        ), 0, "🔵 기한을 바꾸거나 제출이 더 이상 필요 없다고 왜곡합니다.",
                                "[이메일 마인드맵] 상황(미제출) → 요청(오늘 중 제출). 기한 표현에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "회의록 요약",
                        "[회의록 요약] 안건: 사무실 리모델링. 결론: 예산 문제로 리모델링을 내년으로 연기하기로 했다.",
                        q("이 회의록의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("리모델링이 내년으로 연기됐다.", "정답: 회의록에 그대로 명시되어 있습니다."),
                                opt("리모델링이 이번 달에 진행된다.", "🔵 파랑 - 연기 사실과 반대되는 오답입니다."),
                                opt("리모델링이 완전히 취소됐다.", "🔵 파랑 - '연기'를 '취소'로 착각하게 하는 오답입니다."),
                                opt("예산이 충분해서 진행한다.", "🔵 파랑 - '예산 문제로'라는 이유와 반대되는 오답입니다.")
                        ), 0, "🔵 연기/취소를 혼동하거나 연기 이유를 반대로 착각하게 합니다.",
                                "[회의록 마인드맵] 원인(예산 문제) → 결과(내년 연기). 원인과 결과를 화살표로 연결하세요.")),
                onePassage(PassageCategory.READING, "팀 프로젝트 안내",
                        "[팀 프로젝트 안내] 발표 순서는 제비뽑기로 정하며, 발표 시간은 팀당 10분으로 제한됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("발표 시간은 팀당 10분이다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("발표 시간에 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("발표 순서는 담당 교수가 정한다.", "🔵 파랑 - '제비뽑기'를 '교수가 정함'으로 바꾼 오답입니다."),
                                opt("발표 시간은 20분이다.", "🔵 파랑 - 시간 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 발표 시간 숫자나 순서 결정 방법을 바꿔 오답을 만듭니다.",
                                "[프로젝트 마인드맵] 순서(제비뽑기) + 시간(10분). 두 조건을 색으로 구분하세요."))
        );

        List<PassageSeed> listening7th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "계절 축제",
                        "여자: 이번 주말에 벚꽃 축제 하러 가요.\n남자: 좋아요, 몇 시에 만날까요?",
                        q("두 사람이 가려고 하는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("벚꽃 축제", "정답: '벚꽃 축제 하러 가요'라고 말했습니다."),
                                opt("불꽃 축제", "🔵 파랑 - '벚꽃'을 '불꽃'으로 바꾼 오답입니다."),
                                opt("눈꽃 축제", "🔵 파랑 - '벚꽃'을 '눈꽃'으로 바꾼 오답입니다."),
                                opt("영화제", "🔵 파랑 - 언급되지 않은 행사입니다.")
                        ), 0, "🔵 발음이 비슷한 다른 축제명으로 오답을 만듭니다.",
                                "[축제 마인드맵] 중심 = 주말, 가지 = 벚꽃 축제. 축제명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "명절 인사",
                        "남자: 새해 복 많이 받으세요!\n여자: 네, 새해에도 건강하시고 좋은 일만 가득하시길 바라요.",
                        q("두 사람이 나누는 인사로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("새해 인사", "정답: '새해 복 많이 받으세요'는 새해 인사입니다."),
                                opt("생일 축하 인사", "🟢 초록 - 언급되지 않은 상황입니다."),
                                opt("작별 인사", "🟢 초록 - 대화 내용과 맞지 않습니다."),
                                opt("사과 인사", "🟢 초록 - 대화 분위기와 맞지 않습니다.")
                        ), 0, "🟢 명절 인사를 무관한 다른 상황의 인사로 착각하게 합니다.",
                                "[인사 마인드맵] 표현(새해 복 많이 받으세요) = 새해 인사. 고정 표현을 기억하세요.")),
                onePassage(PassageCategory.LISTENING, "기념일 축하",
                        "여자: 오늘 결혼기념일이라면서요? 축하해요!\n남자: 감사해요, 저녁에 아내랑 근사한 식당에 갈 거예요.",
                        q("남자가 오늘 저녁에 하려는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("아내와 식당에 가기", "정답: '아내랑 근사한 식당에 갈 것'이라고 답했습니다."),
                                opt("아내와 여행 가기", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("혼자 식당에 가기", "🟢 초록 - '아내랑'이라는 내용을 놓치게 하는 오답입니다."),
                                opt("집에서 요리하기", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 함께하는 대상(아내)을 빼거나 무관한 다른 계획으로 오답을 만듭니다.",
                                "[축하 마인드맵] 기념일(결혼) + 계획(아내와 식당). 대상과 장소를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "동호회 활동",
                        "남자: 요즘 사진 동호회에 가입했다면서요?\n여자: 네, 매주 토요일마다 같이 출사를 나가요.",
                        q("동호회 활동이 진행되는 요일로 알맞은 것을 고르십시오.", List.of(
                                opt("토요일", "정답: '매주 토요일마다 출사를 나간다'고 답했습니다."),
                                opt("일요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("금요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("매일", "🔴 빨강 - '매주 토요일'을 '매일'로 과장한 오답입니다.")
                        ), 0, "🔴 요일을 바꾸거나 빈도를 과장해 오답을 만듭니다.",
                                "[동호회 마인드맵] 활동(출사) + 요일(토요일). 요일 단어를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "봉사활동",
                        "여자: 이번 주말에 유기견 보호소에서 봉사하기로 했어요.\n남자: 좋은 일 하시네요.",
                        q("여자가 봉사하려는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("유기견 보호소", "정답: '유기견 보호소에서 봉사하기로 했다'고 답했습니다."),
                                opt("양로원", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("고아원", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("도서관", "🔵 파랑 - 언급되지 않은 장소입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 봉사 장소로 오답을 만듭니다.",
                                "[봉사 마인드맵] 시점(이번 주말) + 장소(유기견 보호소). 장소명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "계절 축제",
                        "남자: 가을 단풍 축제는 언제부터예요?\n여자: 다음 주 금요일부터 2주 동안 열려요.",
                        q("단풍 축제가 열리는 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2주", "정답: '다음 주 금요일부터 2주 동안'이라고 답했습니다."),
                                opt("1주", "🔴 빨강 - 기간 숫자를 바꾼 오답입니다."),
                                opt("한 달", "🔴 빨강 - 기간 숫자를 바꾼 오답입니다."),
                                opt("하루", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[축제 마인드맵] 시작(다음 주 금요일) + 기간(2주). 시작일과 기간을 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "명절 인사",
                        "여자: 추석 잘 보내세요! 가족들과 좋은 시간 되세요.\n남자: 네, 그쪽도 명절 잘 보내세요.",
                        q("두 사람이 나누는 인사로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("추석 인사", "정답: '추석 잘 보내세요'는 추석 인사입니다."),
                                opt("환영 인사", "🟢 초록 - 언급되지 않은 상황입니다."),
                                opt("퇴근 인사", "🟢 초록 - 대화 내용과 맞지 않습니다."),
                                opt("건배 인사", "🟢 초록 - 대화 내용과 맞지 않습니다.")
                        ), 0, "🟢 명절 인사를 무관한 다른 상황의 인사로 착각하게 합니다.",
                                "[인사 마인드맵] 표현(추석 잘 보내세요) = 추석 인사. 명절 이름을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "기념일 축하",
                        "남자: 회사 창립 10주년 기념 행사가 있대요.\n여자: 우와, 벌써 10년이나 됐군요.",
                        q("이 회사의 창립 몇 주년 행사인지 알맞은 것을 고르십시오.", List.of(
                                opt("10주년", "정답: '창립 10주년 기념 행사'라고 명시했습니다."),
                                opt("5주년", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("20주년", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("1주년", "🔴 빨강 - 대화에 없는 숫자입니다.")
                        ), 0, "🔴 연수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[축하 마인드맵] 행사(창립 기념) + 연수(10주년). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "동호회 활동",
                        "여자: 독서 동호회는 어떤 책을 읽고 있어요?\n남자: 이번 달엔 역사 소설을 읽고 있어요.",
                        q("동호회가 이번 달에 읽고 있는 책의 종류로 알맞은 것을 고르십시오.", List.of(
                                opt("역사 소설", "정답: '이번 달엔 역사 소설을 읽고 있다'고 답했습니다."),
                                opt("추리 소설", "🔵 파랑 - 언급되지 않은 종류입니다."),
                                opt("에세이", "🔵 파랑 - 언급되지 않은 종류입니다."),
                                opt("시집", "🔵 파랑 - 언급되지 않은 종류입니다.")
                        ), 0, "🔵 실제 언급된 책 종류와 무관한 다른 장르로 오답을 만듭니다.",
                                "[동호회 마인드맵] 시점(이번 달) + 장르(역사 소설). 장르명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "봉사활동",
                        "남자: 봉사 시간은 어떻게 인정받아요?\n여자: 봉사 후에 확인서를 받아서 제출하면 돼요.",
                        q("봉사 시간을 인정받는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("확인서를 받아 제출하기", "정답: '확인서를 받아서 제출하면 된다'고 답했습니다."),
                                opt("사진을 찍어 제출하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("담당자에게 전화하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("따로 신청할 필요가 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 방법(확인서 제출)과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[봉사 마인드맵] 절차(봉사 → 확인서 → 제출). 순서대로 색칠하세요."))
        );

        List<PassageSeed> listening7th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "계절 축제",
                        "여자: 겨울 눈꽃 축제에 가 본 적 있어요?\n남자: 아니요, 이번 겨울에 처음 가 보려고요.",
                        q("남자에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("눈꽃 축제에 처음 가 볼 예정이다.", "정답: '이번 겨울에 처음 가 보려고요'라고 답했습니다."),
                                opt("눈꽃 축제에 여러 번 가 봤다.", "🔴 빨강 - '처음'이라는 표현과 반대되는 오답입니다."),
                                opt("눈꽃 축제에 갈 계획이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("작년에 눈꽃 축제에 갔다.", "🔴 빨강 - '처음'이라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔴 '처음'이라는 경험 표현을 반대로 착각하게 합니다.",
                                "[축제 마인드맵] 경험(없음) → 계획(이번 겨울 처음). '처음'이라는 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "명절 인사",
                        "남자: 새해에는 하시는 일 다 잘되시길 바라요.\n여자: 감사합니다. 새해 복 많이 받으세요.",
                        q("두 사람이 나누는 인사의 시기로 알맞은 것을 고르십시오.", List.of(
                                opt("새해", "정답: '새해 복 많이 받으세요'라는 표현으로 알 수 있습니다."),
                                opt("추석", "🔵 파랑 - 다른 명절로 바꾼 오답입니다."),
                                opt("생일", "🔵 파랑 - 언급되지 않은 상황입니다."),
                                opt("졸업식", "🔵 파랑 - 언급되지 않은 상황입니다.")
                        ), 0, "🔵 명절 종류를 바꿔 오답을 만듭니다.",
                                "[인사 마인드맵] 표현(새해 복) = 시기(새해). 고정 인사말에서 시기를 유추하세요.")),
                onePassage(PassageCategory.LISTENING, "기념일 축하",
                        "여자: 오늘 졸업식이라면서요? 정말 축하해요.\n남자: 감사합니다. 드디어 4년의 대학 생활이 끝났네요.",
                        q("남자가 오늘 참석한 행사로 알맞은 것을 고르십시오.", List.of(
                                opt("졸업식", "정답: '오늘 졸업식'이라고 명시했습니다."),
                                opt("입학식", "🔵 파랑 - '졸업식'을 '입학식'으로 바꾼 오답입니다."),
                                opt("결혼식", "🔵 파랑 - 언급되지 않은 행사입니다."),
                                opt("생일 파티", "🔵 파랑 - 언급되지 않은 행사입니다.")
                        ), 0, "🔵 비슷한 인생 행사(입학식, 결혼식)로 오답을 만듭니다.",
                                "[축하 마인드맵] 행사(졸업식) + 기간(4년). 행사명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "동호회 활동",
                        "남자: 축구 동호회에 새로 가입한 사람이 몇 명이에요?\n여자: 이번 달에 다섯 명이 새로 들어왔어요.",
                        q("이번 달에 새로 가입한 인원수로 알맞은 것을 고르십시오.", List.of(
                                opt("다섯 명", "정답: '이번 달에 다섯 명이 새로 들어왔다'고 답했습니다."),
                                opt("세 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("열 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("한 명도 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 인원수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[동호회 마인드맵] 시점(이번 달) + 인원(다섯 명). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "봉사활동",
                        "여자: 봉사활동 신청은 어디서 해요?\n남자: 구청 홈페이지에서 신청할 수 있어요.",
                        q("봉사활동 신청 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("구청 홈페이지에서 신청하기", "정답: '구청 홈페이지에서 신청할 수 있다'고 답했습니다."),
                                opt("구청에 직접 방문하기", "🔵 파랑 - '홈페이지'를 '방문'으로 바꾼 오답입니다."),
                                opt("전화로 신청하기", "🔵 파랑 - 언급되지 않은 방법입니다."),
                                opt("신청이 필요 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 방법(온라인/방문/전화)을 바꿔 오답을 만듭니다.",
                                "[봉사 마인드맵] 방법 = 구청 홈페이지. 실제 언급된 수단만 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "계절 축제",
                        "남자: 여름 물총 축제 표는 미리 사야 하나요?\n여자: 네, 현장에서는 안 팔고 온라인으로만 예매 가능해요.",
                        q("물총 축제 표를 구매하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("온라인으로만 예매", "정답: '온라인으로만 예매 가능하다'고 답했습니다."),
                                opt("현장에서 구매", "🔴 빨강 - '현장에서는 안 판다'와 반대되는 오답입니다."),
                                opt("전화로 예매", "🔴 빨강 - 언급되지 않은 방법입니다."),
                                opt("표가 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 온라인/현장 구매 방법을 뒤바꿔 오답을 만듭니다.",
                                "[축제 마인드맵] 온라인(가능) ↔ 현장(불가능). 두 방법을 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "명절 인사",
                        "여자: 설날에 세배는 하셨어요?\n남자: 네, 할머니 할아버지께 세배드리고 세뱃돈도 받았어요.",
                        q("남자가 설날에 한 일로 알맞은 것을 고르십시오.", List.of(
                                opt("세배를 하고 세뱃돈을 받았다.", "정답: 대화에 그대로 나와 있습니다."),
                                opt("세배만 하고 세뱃돈은 못 받았다.", "🟢 초록 - '세뱃돈도 받았다'와 반대되는 오답입니다."),
                                opt("세배를 하지 않았다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("친구들과 여행을 갔다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 명절 풍습(세배, 세뱃돈) 언급을 빠뜨리거나 반대로 착각하게 합니다.",
                                "[인사 마인드맵] 풍습(세배) + 결과(세뱃돈). 두 단어를 각각 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "기념일 축하",
                        "남자: 오늘 입사 1주년이시라면서요?\n여자: 네, 시간이 정말 빠르네요.",
                        q("여자의 입사 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("1년", "정답: '입사 1주년'이라고 명시했습니다."),
                                opt("2년", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("6개월", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("10년", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 연차 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[축하 마인드맵] 행사(입사 기념) + 연차(1주년). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "동호회 활동",
                        "여자: 요리 동호회 모임은 어디서 해요?\n남자: 문화센터 조리실을 빌려서 해요.",
                        q("요리 동호회 모임 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("문화센터 조리실", "정답: '문화센터 조리실을 빌려서 한다'고 답했습니다."),
                                opt("회원 집", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("식당", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("공원", "🔵 파랑 - 언급되지 않은 장소입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 장소로 오답을 만듭니다.",
                                "[동호회 마인드맵] 활동(요리) + 장소(문화센터 조리실). 장소명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "봉사활동",
                        "남자: 이번 봉사활동은 몇 시간짜리예요?\n여자: 오전 9시부터 오후 1시까지, 총 4시간이에요.",
                        q("이번 봉사활동의 총 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("4시간", "정답: '총 4시간'이라고 명시했습니다."),
                                opt("2시간", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("6시간", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("하루 종일", "🔴 빨강 - 대화 내용을 과장한 오답입니다.")
                        ), 0, "🔴 시간 숫자를 바꾸거나 과장해 오답을 만듭니다.",
                                "[봉사 마인드맵] 시작(9시) + 종료(1시) = 총 4시간. 계산 결과를 확인하세요."))
        );

        List<PassageSeed> reading7th21to30 = List.of(
                onePassage(PassageCategory.READING, "축제 안내 포스터",
                        "[축제 안내] 제10회 벚꽃 축제. 기간: 4월 5일~4월 14일. 장소: 시민공원. 입장료 무료.",
                        q("이 포스터의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("입장료는 무료다.", "정답: '입장료 무료'라고 명시되어 있습니다."),
                                opt("입장료가 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("장소는 시청 앞이다.", "🔵 파랑 - '시민공원'을 '시청 앞'으로 바꾼 오답입니다."),
                                opt("올해 처음 열리는 축제다.", "🔵 파랑 - '제10회'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 입장료 유무, 장소, 회차 정보를 바꿔 오답을 만듭니다.",
                                "[포스터 마인드맵] 기간(4/5~4/14) + 장소(시민공원) + 요금(무료). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "명절 인사 카드",
                        "[인사 카드] 새해 복 많이 받으세요. 올 한 해도 가족 모두 건강하고 행복하시길 바랍니다.",
                        q("이 카드의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("새해 인사를 전하려고", "정답: '새해 복 많이 받으세요'가 새해 인사입니다."),
                                opt("생일을 축하하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("초대하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("사과하려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 명절 인사를 무관한 다른 목적으로 착각하게 합니다.",
                                "[카드 마인드맵] 표현(새해 복) = 새해 인사. 고정 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "동호회 모집글",
                        "[동호회 모집] 등산 동호회 회원을 모집합니다. 매월 둘째 주 일요일에 산행을 하며, 초보자도 환영합니다.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초보자도 가입할 수 있다.", "정답: '초보자도 환영합니다'라고 명시했습니다."),
                                opt("경력자만 가입할 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("매주 산행을 한다.", "🔵 파랑 - '매월 둘째 주'를 '매주'로 바꾼 오답입니다."),
                                opt("토요일에 산행을 한다.", "🔵 파랑 - '일요일'을 '토요일'로 바꾼 오답입니다.")
                        ), 0, "🔵 대상 조건이나 산행 빈도, 요일을 바꿔 오답을 만듭니다.",
                                "[모집글 마인드맵] 빈도(매월 둘째 주 일요일) + 대상(초보자 환영). 색칠하세요.")),
                onePassage(PassageCategory.READING, "봉사활동 신청서",
                        "[봉사활동 신청서] 신청 대상: 만 14세 이상. 활동 장소: 지역아동센터. 준비물: 앞치마.",
                        q("이 신청서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("준비물은 앞치마다.", "정답: 신청서에 그대로 명시되어 있습니다."),
                                opt("준비물이 필요 없다.", "🔵 파랑 - 명시된 준비물과 반대되는 오답입니다."),
                                opt("신청 대상은 만 19세 이상이다.", "🔵 파랑 - 나이 조건을 바꾼 오답입니다."),
                                opt("활동 장소는 도서관이다.", "🔵 파랑 - '지역아동센터'를 '도서관'으로 바꾼 오답입니다.")
                        ), 0, "🔵 준비물 유무나 나이 조건, 장소를 바꿔 오답을 만듭니다.",
                                "[신청서 마인드맵] 대상(만 14세 이상) + 장소(지역아동센터) + 준비물(앞치마). 색칠하세요.")),
                onePassage(PassageCategory.READING, "지역 소식지",
                        "[지역 소식지] 이번 달 주민센터에서 무료 법률 상담을 진행합니다. 매주 수요일 오후 2시부터 접수 없이 참여 가능합니다.",
                        q("이 소식지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("접수 없이 참여할 수 있다.", "정답: '접수 없이 참여 가능하다'고 명시했습니다."),
                                opt("사전 접수가 필요하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("상담은 유료다.", "🔵 파랑 - '무료 법률 상담'과 반대되는 오답입니다."),
                                opt("매주 월요일에 진행된다.", "🔵 파랑 - '수요일'을 '월요일'로 바꾼 오답입니다.")
                        ), 0, "🔵 접수 필요 여부, 무료/유료, 요일을 바꿔 오답을 만듭니다.",
                                "[소식지 마인드맵] 요일(수요일) + 접수(불필요) + 비용(무료). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "축제 안내 포스터",
                        "[축제 안내] 겨울 빛 축제. 매일 저녁 6시부터 10시까지 점등됩니다. 주차 공간이 부족하니 대중교통을 이용해 주세요.",
                        q("이 포스터의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("대중교통 이용을 권장한다.", "정답: '대중교통을 이용해 주세요'라고 명시했습니다."),
                                opt("주차 공간이 충분하다.", "🔵 파랑 - '주차 공간이 부족하다'와 반대되는 오답입니다."),
                                opt("점등은 오전에 시작한다.", "🔵 파랑 - '저녁 6시'를 '오전'으로 바꾼 오답입니다."),
                                opt("주말에만 점등된다.", "🔵 파랑 - '매일'을 '주말에만'으로 바꾼 오답입니다.")
                        ), 0, "🔵 주차 상황이나 점등 시간, 요일 제한을 바꿔 오답을 만듭니다.",
                                "[포스터 마인드맵] 시간(6시~10시) + 권장(대중교통). 시간과 권장사항을 색칠하세요.")),
                onePassage(PassageCategory.READING, "명절 인사 카드",
                        "[인사 카드] 즐거운 추석 보내세요. 풍성한 한가위 되시고 소중한 분들과 좋은 시간 보내세요.",
                        q("이 카드가 전하는 명절로 알맞은 것을 고르십시오.", List.of(
                                opt("추석", "정답: '즐거운 추석 보내세요'라고 명시했습니다."),
                                opt("설날", "🔵 파랑 - 다른 명절로 바꾼 오답입니다."),
                                opt("어린이날", "🔵 파랑 - 언급되지 않은 명절입니다."),
                                opt("크리스마스", "🔵 파랑 - 언급되지 않은 명절입니다.")
                        ), 0, "🔵 명절 종류를 바꿔 오답을 만듭니다.",
                                "[카드 마인드맵] 표현(추석, 한가위) = 추석 인사. 명절 이름 단어를 확인하세요.")),
                onePassage(PassageCategory.READING, "동호회 모집글",
                        "[동호회 모집] 사진 동호회원 모집. 카메라가 없어도 참여 가능하며, 스마트폰 촬영도 환영합니다.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("카메라가 없어도 참여할 수 있다.", "정답: '카메라가 없어도 참여 가능하다'고 명시했습니다."),
                                opt("전문 카메라가 반드시 필요하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("스마트폰 촬영은 안 된다.", "🔵 파랑 - '스마트폰 촬영도 환영'과 반대되는 오답입니다."),
                                opt("사진 경력자만 가입 가능하다.", "🔵 파랑 - 언급되지 않은 조건입니다.")
                        ), 0, "🔵 필수 장비 유무나 참여 조건을 반대로 착각하게 합니다.",
                                "[모집글 마인드맵] 조건(카메라 불필요) + 환영(스마트폰 촬영). 두 조건을 색칠하세요.")),
                onePassage(PassageCategory.READING, "봉사활동 신청서",
                        "[봉사활동 신청서] 신청 기간: 매달 25일까지. 선정 결과는 다음 달 1일에 개별 통보됩니다.",
                        q("이 신청서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("선정 결과는 다음 달 1일에 통보된다.", "정답: 신청서에 그대로 명시되어 있습니다."),
                                opt("선정 결과는 신청 즉시 통보된다.", "🔵 파랑 - 명시된 통보 시점과 반대되는 오답입니다."),
                                opt("신청 기간에 제한이 없다.", "🔵 파랑 - '매달 25일까지'라는 기한과 반대됩니다."),
                                opt("결과는 단체로 공지된다.", "🔵 파랑 - '개별 통보'와 반대되는 오답입니다.")
                        ), 0, "🔵 통보 시점이나 방식(개별/단체)을 바꿔 오답을 만듭니다.",
                                "[신청서 마인드맵] 신청(25일까지) → 통보(다음 달 1일, 개별). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "지역 소식지",
                        "[지역 소식지] 이번 달 도서관에서 어린이 독서 프로그램을 진행합니다. 참가 신청은 도서관 안내데스크에서 받습니다.",
                        q("이 소식지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신청은 안내데스크에서 받는다.", "정답: 소식지에 그대로 명시되어 있습니다."),
                                opt("신청은 온라인으로만 받는다.", "🔵 파랑 - '안내데스크'를 '온라인'으로 바꾼 오답입니다."),
                                opt("성인 대상 프로그램이다.", "🔵 파랑 - '어린이'를 '성인'으로 바꾼 오답입니다."),
                                opt("프로그램은 다음 달에 시작한다.", "🔵 파랑 - '이번 달'을 '다음 달'로 바꾼 오답입니다.")
                        ), 0, "🔵 신청 장소, 대상, 시점을 바꿔 오답을 만듭니다.",
                                "[소식지 마인드맵] 대상(어린이) + 신청 장소(안내데스크). 두 정보를 색칠하세요."))
        );

        List<PassageSeed> reading7th31to40 = List.of(
                onePassage(PassageCategory.READING, "축제 안내 포스터",
                        "[축제 안내] 여름 물놀이 축제. 만 12세 이하 어린이는 보호자 동반 시에만 입장 가능합니다.",
                        q("이 포스터의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("어린이는 보호자와 함께 입장해야 한다.", "정답: '보호자 동반 시에만 입장 가능'이라고 명시했습니다."),
                                opt("어린이는 혼자서도 입장할 수 있다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("모든 연령이 자유롭게 입장 가능하다.", "🔵 파랑 - 나이 조건을 빼고 읽게 하는 오답입니다."),
                                opt("어린이는 아예 입장할 수 없다.", "🔵 파랑 - '보호자 동반 시 가능'과 반대되는 과장된 오답입니다.")
                        ), 0, "🔵 보호자 동반 조건을 빼거나 완전 금지로 과장해 오답을 만듭니다.",
                                "[포스터 마인드맵] 조건(만 12세 이하) + 필수(보호자 동반). 조건 문장에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "명절 인사 카드",
                        "[인사 카드] 어버이날을 맞아 그동안 키워주셔서 감사하다는 말씀을 전하고 싶습니다.",
                        q("이 카드를 보내는 대상으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부모님", "정답: '어버이날'과 '키워주셔서 감사하다'는 부모님께 보내는 내용입니다."),
                                opt("선생님", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("친구", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("직장 상사", "🔵 파랑 - 언급된 관계와 다른 대상입니다.")
                        ), 0, "🔵 '어버이날'이라는 명절과 무관한 다른 대상으로 오답을 만듭니다.",
                                "[카드 마인드맵] 명절(어버이날) → 대상(부모님). 명절 이름이 대상을 알려줍니다.")),
                onePassage(PassageCategory.READING, "동호회 모집글",
                        "[동호회 모집] 배드민턴 동호회 신입 회원 모집. 라켓은 동호회에서 대여해 드립니다.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("라켓을 동호회에서 빌릴 수 있다.", "정답: '라켓은 동호회에서 대여해 드립니다'라고 명시했습니다."),
                                opt("라켓을 반드시 구매해야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("경력자만 가입할 수 있다.", "🔵 파랑 - 언급되지 않은 조건입니다."),
                                opt("라켓 대여는 유료다.", "🔵 파랑 - 무료/유료 언급이 없는데 유료로 단정한 오답입니다.")
                        ), 0, "🔵 구매 필수로 과장하거나 언급되지 않은 조건을 추가해 오답을 만듭니다.",
                                "[모집글 마인드맵] 혜택(라켓 대여). 실제 언급된 혜택 내용만 정답입니다.")),
                onePassage(PassageCategory.READING, "봉사활동 신청서",
                        "[봉사활동 신청서] 1인당 신청 가능한 활동은 월 최대 2회이며, 초과 신청 시 자동 취소됩니다.",
                        q("이 신청서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("월 최대 2회까지 신청할 수 있다.", "정답: 신청서에 그대로 명시되어 있습니다."),
                                opt("신청 횟수에 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("초과 신청해도 모두 인정된다.", "🔵 파랑 - '자동 취소'와 반대되는 오답입니다."),
                                opt("월 최대 5회까지 가능하다.", "🔵 파랑 - 횟수 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 신청 횟수 제한을 빼거나 숫자를 바꿔 오답을 만듭니다.",
                                "[신청서 마인드맵] 제한(월 2회) + 초과 시(자동 취소). 숫자와 결과를 색칠하세요.")),
                onePassage(PassageCategory.READING, "지역 소식지",
                        "[지역 소식지] 다음 달부터 주민센터 운영 시간이 오전 9시에서 오후 6시로 연장됩니다.",
                        q("이 소식지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("운영 시간이 연장된다.", "정답: '운영 시간이 연장됩니다'라고 명시했습니다."),
                                opt("운영 시간이 단축된다.", "🔵 파랑 - '연장'을 '단축'으로 착각하게 하는 오답입니다."),
                                opt("이번 달부터 바로 적용된다.", "🔵 파랑 - '다음 달부터'라는 시점을 바꾼 오답입니다."),
                                opt("주민센터가 문을 닫는다.", "🔵 파랑 - 소식지 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 연장/단축을 뒤바꾸거나 시행 시점을 바꿔 오답을 만듭니다.",
                                "[소식지 마인드맵] 변경 전(9시~) → 변경 후(연장, 6시까지). 화살표 방향에 주목하세요.")),
                onePassage(PassageCategory.READING, "축제 안내 포스터",
                        "[축제 안내] 음식 축제 참가 업체 모집. 참가비는 무료이며, 신청 마감은 이번 달 말입니다.",
                        q("이 포스터의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("참가비는 무료다.", "정답: '참가비는 무료'라고 명시했습니다."),
                                opt("참가비가 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("신청 마감은 다음 달 말이다.", "🔵 파랑 - '이번 달 말'을 '다음 달 말'로 바꾼 오답입니다."),
                                opt("신청 기한에 제한이 없다.", "🔵 파랑 - 명시된 마감일과 반대되는 오답입니다.")
                        ), 0, "🔵 참가비 유무나 마감 시점을 바꿔 오답을 만듭니다.",
                                "[포스터 마인드맵] 비용(무료) + 마감(이번 달 말). 두 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "명절 인사 카드",
                        "[인사 카드] 스승의 날을 맞아 선생님의 가르침에 깊이 감사드립니다.",
                        q("이 카드를 보내는 대상으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("선생님", "정답: '스승의 날'과 '선생님의 가르침'이 대상을 알려줍니다."),
                                opt("부모님", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("동료", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("이웃", "🔵 파랑 - 언급된 관계와 다른 대상입니다.")
                        ), 0, "🔵 '스승의 날'이라는 명절과 무관한 다른 대상으로 오답을 만듭니다.",
                                "[카드 마인드맵] 명절(스승의 날) → 대상(선생님). 명절 이름이 대상을 알려줍니다.")),
                onePassage(PassageCategory.READING, "동호회 모집글",
                        "[동호회 모집] 영화 동호회원 모집. 매달 마지막 주 금요일 저녁에 함께 영화를 보고 이야기를 나눕니다.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("매달 마지막 주 금요일에 모인다.", "정답: 모집글에 그대로 명시되어 있습니다."),
                                opt("매주 금요일에 모인다.", "🔵 파랑 - '매달 마지막 주'를 '매주'로 바꾼 오답입니다."),
                                opt("토요일 오전에 모인다.", "🔵 파랑 - 요일과 시간대를 모두 바꾼 오답입니다."),
                                opt("영화만 보고 대화는 안 한다.", "🔵 파랑 - '이야기를 나눈다'와 반대되는 오답입니다.")
                        ), 0, "🔵 모임 빈도나 요일, 활동 내용을 바꿔 오답을 만듭니다.",
                                "[모집글 마인드맵] 빈도(매달 마지막 금요일) + 활동(영화+대화). 색칠하세요.")),
                onePassage(PassageCategory.READING, "봉사활동 신청서",
                        "[봉사활동 신청서] 단체 신청 시 최소 5명 이상 구성해야 하며, 대표자 1인이 일괄 접수합니다.",
                        q("이 신청서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("단체 신청은 5명 이상이어야 한다.", "정답: 신청서에 그대로 명시되어 있습니다."),
                                opt("단체 신청은 인원 제한이 없다.", "🔵 파랑 - 명시된 최소 인원과 반대되는 오답입니다."),
                                opt("각자 개별로 접수해야 한다.", "🔵 파랑 - '대표자 1인이 일괄 접수'와 반대되는 오답입니다."),
                                opt("최소 인원은 10명이다.", "🔵 파랑 - 인원 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 최소 인원 숫자나 접수 방식(개별/일괄)을 바꿔 오답을 만듭니다.",
                                "[신청서 마인드맵] 조건(5명 이상) + 접수(대표자 일괄). 두 조건을 색칠하세요.")),
                onePassage(PassageCategory.READING, "지역 소식지",
                        "[지역 소식지] 이번 여름 지역 축제 자원봉사자를 모집합니다. 활동 인원에게는 소정의 활동비가 지급됩니다.",
                        q("이 소식지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("자원봉사자에게 활동비가 지급된다.", "정답: '소정의 활동비가 지급된다'고 명시했습니다."),
                                opt("자원봉사는 무보수로 진행된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("겨울 축제 봉사자를 모집한다.", "🔵 파랑 - '여름'을 '겨울'로 바꾼 오답입니다."),
                                opt("활동비는 지급되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 활동비 지급 여부나 계절을 바꿔 오답을 만듭니다.",
                                "[소식지 마인드맵] 시기(여름) + 혜택(활동비 지급). 두 정보를 색칠하세요."))
        );

        return new WeekSeed("1~2급 컬러맵 심화 (일상 확장)",
                "일상 속 감정·의견 표현과 실용문 독해력을 색깔 코딩과 마인드맵으로 확장한다.",
                WEEK2_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(감정 표현, 의견 제시, 비교/선택, 부탁/거절, 위로/격려) + 읽기 20(온라인 게시글, 뉴스 단신, 광고 문구, 그래프/도표 설명, 신청서 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening1to10, listening11to20, reading21to30, reading31to40)),
                        day("2차(40문항) - 듣기 20(계획 세우기, 후회/아쉬움, 칭찬/감사, 놀람/걱정, 추천/조언) + 읽기 20(레시피, 사용후기, 모집 공고, 안내 방송문, 인터뷰 기사). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening2nd1to10, listening2nd11to20, reading2nd21to30, reading2nd31to40)),
                        day("3차(40문항) - 듣기 20(예약 변경/취소, 배달 주문, 고장/수리 신고, 분실물 신고, 환불/교환) + 읽기 20(영수증/명세서, 계약서 요약, 초대 메시지, 공지 이메일, 지도/약도 설명). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening3rd1to10, listening3rd11to20, reading3rd21to30, reading3rd31to40)),
                        day("4차(40문항) - 듣기 20(여행 계획, 숙소 예약, 항공/기차 안내, 관광지 설명, 환전/환율) + 읽기 20(여행 후기, 숙박 안내문, 교통 시간표, 관광 안내 책자, 환전소 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening4th1to10, listening4th11to20, reading4th21to30, reading4th31to40)),
                        day("5차(40문항) - 듣기 20(건강 검진, 다이어트/운동, 스트레스 관리, 수면 습관, 응급 상황) + 읽기 20(건강 정보 기사, 운동 프로그램 안내, 병원 예약 확인서, 약 복용법 설명서, 응급처치 안내문). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening5th1to10, listening5th11to20, reading5th21to30, reading5th31to40)),
                        day("6차(40문항) - 듣기 20(진로/직업, 이직/취업, 회의 결과 보고, 프로젝트 진행, 협업/팀워크) + 읽기 20(채용 정보, 사내 공지, 업무 이메일, 회의록 요약, 팀 프로젝트 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening6th1to10, listening6th11to20, reading6th21to30, reading6th31to40)),
                        day("7차(40문항) - 듣기 20(계절 축제, 명절 인사, 기념일 축하, 동호회 활동, 봉사활동) + 읽기 20(축제 안내 포스터, 명절 인사 카드, 동호회 모집글, 봉사활동 신청서, 지역 소식지). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening7th1to10, listening7th11to20, reading7th21to30, reading7th31to40))
                ));
    }

    // ===================== WEEK 3: 1~2급 컬러맵 실전 감각 =====================

    private static final String WEEK3_ANSWER_NOTE_TEMPLATE = """
            [🎨 오답 노트 템플릿 - WEEK3용]
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

    private WeekSeed week3() {
        List<PassageSeed> listening1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "인터넷 쇼핑",
                        "여자: 이 신발 온라인으로 주문했는데 언제 와요?\n남자: 주문서 보니까 모레 도착 예정이에요.",
                        q("신발이 도착하는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("모레", "정답: '모레 도착 예정'이라고 답했습니다."),
                                opt("내일", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("오늘", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("다음 주", "🔴 빨강 - 시점을 바꾼 오답입니다.")
                        ), 0, "🔴 시점 표현(오늘/내일/모레)을 슬쩍 바꿔 오답을 만듭니다.",
                                "[쇼핑 마인드맵] 주문 → 배송 예정(모레). 시점 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "배달앱 이용",
                        "남자: 배달앱에서 쿠폰 어떻게 써요?\n여자: 결제할 때 쿠폰함에서 선택하면 자동으로 할인돼요.",
                        q("여자가 설명한 쿠폰 사용 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("결제할 때 쿠폰함에서 선택하기", "정답: '결제할 때 쿠폰함에서 선택한다'고 답했습니다."),
                                opt("주문 전에 미리 등록하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("배달원에게 직접 말하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("고객센터에 전화하기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[배달앱 마인드맵] 시점(결제 시) + 방법(쿠폰함 선택). 순서와 방법을 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "SNS 소통",
                        "여자: 어제 올린 사진에 댓글이 정말 많이 달렸어요.\n남자: 진짜요? 저도 좋아요 눌렀어요.",
                        q("남자가 한 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("사진에 좋아요를 눌렀다.", "정답: '저도 좋아요 눌렀어요'라고 답했습니다."),
                                opt("사진에 댓글을 달았다.", "🟢 초록 - '좋아요'를 '댓글'로 바꾼 오답입니다."),
                                opt("사진을 삭제했다.", "🟢 초록 - 언급되지 않은 행동입니다."),
                                opt("사진을 공유했다.", "🟢 초록 - 언급되지 않은 행동입니다.")
                        ), 0, "🟢 '좋아요'와 '댓글'이라는 비슷한 SNS 반응을 혼동하게 합니다.",
                                "[SNS 마인드맵] 반응(좋아요) vs 반응(댓글). 실제 언급된 반응만 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "온라인 강의",
                        "남자: 이 강의는 언제까지 들을 수 있어요?\n여자: 결제 후 60일 동안 무제한으로 들을 수 있어요.",
                        q("온라인 강의를 들을 수 있는 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("60일", "정답: '결제 후 60일 동안'이라고 답했습니다."),
                                opt("30일", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("90일", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("기간 제한이 없다.", "🔴 빨강 - 명시된 기간과 반대되는 오답입니다.")
                        ), 0, "🔴 기간 숫자를 바꾸거나 무제한 기간으로 착각하게 합니다.",
                                "[강의 마인드맵] 조건(결제 후) + 기간(60일). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화상 통화",
                        "여자: 화면이 자꾸 끊겨요.\n남자: 인터넷 연결 상태를 한번 확인해 보세요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("인터넷 연결 상태 확인하기", "정답: '인터넷 연결 상태를 확인해 보라'고 제안했습니다."),
                                opt("화상 통화 앱 재설치하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("전화로 다시 걸기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("컴퓨터를 재부팅하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(연결 상태 확인)과 무관한 다른 해결책으로 오답을 만듭니다.",
                                "[화상통화 마인드맵] 문제(화면 끊김) → 제안(연결 확인). 제안 문장의 목적어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "인터넷 쇼핑",
                        "남자: 이 옷 반품하려면 어떻게 해야 해요?\n여자: 마이페이지에서 반품 신청하시면 택배기사님이 수거하러 와요.",
                        q("반품 절차로 알맞은 것을 고르십시오.", List.of(
                                opt("마이페이지 신청 → 택배기사 수거", "정답: 여자의 설명 순서 그대로입니다."),
                                opt("택배기사 수거 → 마이페이지 신청", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("고객센터 전화만 하면 된다.", "🔴 빨강 - 언급되지 않은 방법입니다."),
                                opt("매장에 직접 방문해야 한다.", "🔴 빨강 - 언급되지 않은 방법입니다.")
                        ), 0, "🔴 절차 순서를 뒤바꾸거나 무관한 다른 방법으로 오답을 만듭니다.",
                                "[반품 마인드맵] 1(마이페이지 신청) → 2(수거). 순서 화살표를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "배달앱 이용",
                        "여자: 배달비가 왜 이렇게 비싸요?\n남자: 거리가 멀어서 그런 것 같아요. 가까운 가게를 찾아보세요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("가까운 가게 찾아보기", "정답: '가까운 가게를 찾아보라'고 제안했습니다."),
                                opt("배달앱 바꾸기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("직접 가서 사 오기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("배달비 할인 쿠폰 찾기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(가까운 가게)과 무관한 다른 해결책으로 오답을 만듭니다.",
                                "[배달앱 마인드맵] 원인(거리 멂) → 제안(가까운 가게). 원인과 제안을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "SNS 소통",
                        "남자: 요즘 SNS에 사진을 잘 안 올리시네요.\n여자: 네, 요즘엔 그냥 보는 것만 하고 있어요.",
                        q("여자의 요즘 SNS 사용 방식으로 알맞은 것을 고르십시오.", List.of(
                                opt("게시물을 보기만 한다.", "정답: '보는 것만 하고 있다'고 답했습니다."),
                                opt("게시물을 자주 올린다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("SNS를 아예 안 한다.", "🟢 초록 - '보는 것만 한다'와 다른 오답입니다."),
                                opt("댓글만 단다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 '보기만 함'을 '전혀 안 함'이나 다른 활동으로 착각하게 합니다.",
                                "[SNS 마인드맵] 예전(게시) → 지금(보기만). 변화 방향을 화살표로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "온라인 강의",
                        "여자: 강의 자막이 있나요?\n남자: 네, 한국어와 영어 자막을 모두 제공해요.",
                        q("이 강의가 제공하는 자막 언어로 알맞은 것을 고르십시오.", List.of(
                                opt("한국어와 영어", "정답: '한국어와 영어 자막을 모두 제공한다'고 답했습니다."),
                                opt("한국어만", "🔵 파랑 - '영어'라는 정보를 빠뜨린 오답입니다."),
                                opt("영어만", "🔵 파랑 - '한국어'라는 정보를 빠뜨린 오답입니다."),
                                opt("자막이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 두 언어 중 하나만 언급하거나 아예 없다고 왜곡합니다.",
                                "[강의 마인드맵] 자막(한국어+영어). 언급된 언어 두 개를 모두 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화상 통화",
                        "남자: 화상 회의 몇 시에 시작해요?\n여자: 원래 2시였는데 30분 늦춰져서 2시 반에 시작해요.",
                        q("화상 회의가 시작하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2시 반", "정답: '30분 늦춰져서 2시 반에 시작한다'고 답했습니다."),
                                opt("2시", "🔴 빨강 - 원래 시간(변경 전)에 꽂힌 오답입니다."),
                                opt("3시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("1시 반", "🔴 빨강 - 대화에 없는 시간입니다.")
                        ), 0, "🔴 원래 시간과 변경된 시간을 혼동하게 합니다.",
                                "[화상통화 마인드맵] 2시(변경 전) → 화살표 → 2시 반(변경 후). 화살표 뒤가 정답입니다."))
        );

        List<PassageSeed> listening11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "인터넷 쇼핑",
                        "여자: 이 사이트는 회원 가입하면 첫 구매 할인이 있대요.\n남자: 얼마나 할인해 줘요?\n여자: 만 원 할인이요.",
                        q("첫 구매 시 받을 수 있는 할인 금액으로 알맞은 것을 고르십시오.", List.of(
                                opt("만 원", "정답: '만 원 할인'이라고 답했습니다."),
                                opt("오천 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("이만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("할인이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 금액 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[쇼핑 마인드맵] 조건(회원 가입+첫 구매) + 할인(만 원). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "배달앱 이용",
                        "남자: 리뷰 쓰면 포인트 준다면서요?\n여자: 네, 사진 포함 리뷰는 500포인트예요.",
                        q("사진 포함 리뷰 작성 시 받는 포인트로 알맞은 것을 고르십시오.", List.of(
                                opt("500포인트", "정답: '사진 포함 리뷰는 500포인트'라고 답했습니다."),
                                opt("100포인트", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("1000포인트", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("포인트가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 포인트 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[배달앱 마인드맵] 조건(사진 포함) + 포인트(500). 조건과 숫자를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "SNS 소통",
                        "여자: 이 계정 왜 이렇게 팔로워가 많아요?\n남자: 매일 꾸준히 유용한 정보를 올려서 그런 것 같아요.",
                        q("남자가 말한 팔로워가 많은 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("매일 꾸준히 유용한 정보를 올려서", "정답: 대화에 그대로 나와 있습니다."),
                                opt("광고를 많이 해서", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("유명한 사람이라서", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("오래된 계정이라서", "🟢 초록 - 언급되지 않은 이유입니다.")
                        ), 0, "🟢 실제 언급된 이유와 무관한 다른 이유로 오답을 만듭니다.",
                                "[SNS 마인드맵] 결과(팔로워 많음) ← 원인(꾸준한 정보 게시). 원인 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "온라인 강의",
                        "남자: 이 강의 몇 강까지 있어요?\n여자: 총 20강으로 구성돼 있어요.",
                        q("이 강의의 총 회차 수로 알맞은 것을 고르십시오.", List.of(
                                opt("20강", "정답: '총 20강으로 구성돼 있다'고 답했습니다."),
                                opt("10강", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("30강", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 회차 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[강의 마인드맵] 질문(총 회차) → 답(20강). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화상 통화",
                        "여자: 화상 면접 볼 때 배경은 어떻게 하는 게 좋아요?\n남자: 깔끔한 흰 벽이나 서재 배경이 좋아요.",
                        q("남자가 추천한 화상 면접 배경으로 알맞은 것을 고르십시오.", List.of(
                                opt("깔끔한 흰 벽이나 서재", "정답: 대화에 그대로 나와 있습니다."),
                                opt("화려한 배경화면", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("야외 풍경", "🟢 초록 - 언급되지 않은 배경입니다."),
                                opt("배경은 상관없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 추천 배경을 무관하거나 반대되는 배경으로 착각하게 합니다.",
                                "[화상통화 마인드맵] 추천(흰 벽, 서재). 실제 언급된 배경 두 가지를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "인터넷 쇼핑",
                        "남자: 이 사이트 배송비는 얼마예요?\n여자: 3만 원 이상 구매하면 무료고, 그 이하는 3천 원이에요.",
                        q("배송비가 무료가 되는 구매 금액 기준으로 알맞은 것을 고르십시오.", List.of(
                                opt("3만 원 이상", "정답: '3만 원 이상 구매하면 무료'라고 답했습니다."),
                                opt("3천 원 이상", "🔴 빨강 - 배송비 금액과 기준 금액을 혼동하게 합니다."),
                                opt("5만 원 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("항상 무료다.", "🔴 빨강 - 조건을 빼고 읽게 하는 오답입니다.")
                        ), 0, "🔴 기준 금액과 배송비 금액을 혼동하게 하거나 조건을 빼고 읽게 합니다.",
                                "[쇼핑 마인드맵] 기준(3만 원↑) → 무료. 기준(3만 원↓) → 3천 원. 조건별로 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "배달앱 이용",
                        "여자: 주문한 지 한참 됐는데 아직도 안 와요.\n남자: 배달앱에서 실시간 위치 확인해 보세요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("실시간 위치 확인하기", "정답: '실시간 위치 확인해 보라'고 제안했습니다."),
                                opt("가게에 직접 전화하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("주문 취소하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("환불 요청하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(위치 확인)과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[배달앱 마인드맵] 문제(배달 지연) → 제안(위치 확인). 제안 동사를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "SNS 소통",
                        "남자: 이 게시물 왜 삭제했어요?\n여자: 사람들이 오해할까 봐 걱정돼서 지웠어요.",
                        q("여자가 게시물을 삭제한 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("사람들이 오해할까 봐", "정답: 대화에 그대로 나와 있습니다."),
                                opt("사진이 마음에 안 들어서", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("실수로 잘못 올려서", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("친구가 지우라고 해서", "🟢 초록 - 언급되지 않은 이유입니다.")
                        ), 0, "🟢 실제 언급된 이유와 무관한 다른 이유로 오답을 만듭니다.",
                                "[SNS 마인드맵] 결과(삭제) ← 이유(오해 우려). 이유 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "온라인 강의",
                        "여자: 이 강의 환불이 되나요?\n남자: 수강 시작 후 7일 이내에는 전액 환불 가능해요.",
                        q("전액 환불이 가능한 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("수강 시작 후 7일 이내", "정답: 대화에 그대로 나와 있습니다."),
                                opt("수강 시작 후 14일 이내", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("환불이 전혀 안 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("언제든지 환불 가능하다.", "🔴 빨강 - 기간 제한을 빼고 읽게 하는 오답입니다.")
                        ), 0, "🔴 기간 숫자를 바꾸거나 제한을 빼고 읽게 합니다.",
                                "[강의 마인드맵] 조건(7일 이내) + 결과(전액 환불). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.LISTENING, "화상 통화",
                        "남자: 화상 수업 중에 마이크는 언제 켜야 해요?\n여자: 질문할 때만 켜고 평소엔 꺼 두세요.",
                        q("여자가 안내한 마이크 사용법으로 알맞은 것을 고르십시오.", List.of(
                                opt("질문할 때만 켜기", "정답: '질문할 때만 켜라'고 안내했습니다."),
                                opt("항상 켜 두기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("항상 꺼 두기", "🟢 초록 - '질문할 때만'이라는 예외를 놓치게 합니다."),
                                opt("수업 끝날 때만 켜기", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 예외 조건(질문할 때)을 놓치고 항상 켜거나 끄는 것으로 착각하게 합니다.",
                                "[화상통화 마인드맵] 기본(끄기) + 예외(질문 시 켜기). 조건 표현을 색칠하세요."))
        );

        List<PassageSeed> reading21to30 = List.of(
                onePassage(PassageCategory.READING, "앱 사용 후기",
                        "[앱 리뷰] ★★★★☆ 사용법이 간단해서 좋아요. 다만 가끔 로딩이 느려질 때가 있어요.",
                        q("이 리뷰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("가끔 로딩이 느리다.", "정답: '가끔 로딩이 느려질 때가 있다'고 명시했습니다."),
                                opt("로딩이 항상 빠르다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("사용법이 복잡하다.", "🔵 파랑 - '사용법이 간단하다'와 반대되는 오답입니다."),
                                opt("만족도가 매우 낮다.", "🔵 파랑 - 높은 별점(★★★★☆)과 반대되는 오답입니다.")
                        ), 0, "🔵 장단점을 뒤바꾸거나 별점과 반대되는 평가로 착각하게 합니다.",
                                "[리뷰 마인드맵] 장점(간단함) + 단점(가끔 느림). 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "SNS 게시물",
                        "[SNS 게시물] 오늘 드디어 새 카페를 오픈했어요! 첫 방문 고객님께는 음료 한 잔을 무료로 드립니다.",
                        q("이 게시물의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("첫 방문 고객은 음료를 무료로 받는다.", "정답: 게시물에 그대로 명시되어 있습니다."),
                                opt("모든 고객이 무료로 받는다.", "🔵 파랑 - '첫 방문 고객'이라는 제한을 빼고 읽게 합니다."),
                                opt("카페는 예전부터 있었다.", "🔵 파랑 - '오늘 드디어 오픈'과 반대되는 오답입니다."),
                                opt("음료 할인 이벤트다.", "🔵 파랑 - '무료'를 '할인'으로 바꾼 오답입니다.")
                        ), 0, "🔵 대상 제한을 빼거나 무료/할인을 혼동하게 합니다.",
                                "[게시물 마인드맵] 대상(첫 방문) + 혜택(무료 음료). 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "온라인 강의 안내",
                        "[강의 안내] 이 강의는 초급자를 위한 과정으로, 사전 지식이 없어도 수강할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("사전 지식이 없어도 들을 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("사전 지식이 반드시 필요하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("이 강의는 고급자 대상이다.", "🔵 파랑 - '초급자'를 '고급자'로 바꾼 오답입니다."),
                                opt("수강 조건이 매우 까다롭다.", "🔵 파랑 - 조건이 없다는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 필요 조건 유무나 난이도를 반대로 착각하게 합니다.",
                                "[강의 마인드맵] 대상(초급자) + 조건(사전 지식 불필요). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "화상회의 초대장",
                        "[초대장] 화상회의 초대: 3월 20일 오후 3시. 링크는 회의 10분 전에 활성화됩니다.",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("링크는 회의 10분 전에 활성화된다.", "정답: 초대장에 그대로 명시되어 있습니다."),
                                opt("링크는 언제든지 사용할 수 있다.", "🔵 파랑 - 명시된 활성화 시점과 반대되는 오답입니다."),
                                opt("회의는 오전에 진행된다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다."),
                                opt("회의는 3월 30일이다.", "🔵 파랑 - 날짜를 바꾼 오답입니다.")
                        ), 0, "🔵 링크 활성화 시점이나 날짜, 시간대를 바꿔 오답을 만듭니다.",
                                "[초대장 마인드맵] 일시(3/20 오후 3시) + 링크 활성화(10분 전). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "쇼핑몰 이벤트",
                        "[이벤트 안내] 신규 회원 가입 시 5천 원 쿠폰을 드립니다. 쿠폰은 가입 후 7일간 사용 가능합니다.",
                        q("이 이벤트의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("쿠폰은 가입 후 7일간 사용 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("쿠폰 사용 기한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("기존 회원도 쿠폰을 받는다.", "🔵 파랑 - '신규 회원'이라는 조건과 반대됩니다."),
                                opt("쿠폰 금액은 만 원이다.", "🔵 파랑 - 금액 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 사용 기한이나 대상, 금액을 바꿔 오답을 만듭니다.",
                                "[이벤트 마인드맵] 대상(신규 회원) + 혜택(5천 원) + 기한(7일). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "앱 사용 후기",
                        "[앱 리뷰] ★★★★★ 알림 기능이 정말 유용해요. 광고도 거의 없어서 쾌적하게 사용하고 있어요.",
                        q("이 리뷰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("광고가 거의 없다.", "정답: '광고도 거의 없다'고 명시했습니다."),
                                opt("광고가 매우 많다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("알림 기능이 불편하다.", "🔵 파랑 - '정말 유용하다'와 반대되는 오답입니다."),
                                opt("평가가 매우 낮다.", "🔵 파랑 - 높은 별점(★★★★★)과 반대되는 오답입니다.")
                        ), 0, "🔵 장점을 반대로 착각하게 하거나 별점과 반대되는 평가로 왜곡합니다.",
                                "[리뷰 마인드맵] 장점(알림 기능, 적은 광고). 긍정 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "SNS 게시물",
                        "[SNS 게시물] 오늘 마라톤 완주했습니다! 처음엔 힘들었지만 끝까지 포기하지 않길 잘한 것 같아요.",
                        q("이 게시물을 쓴 사람의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("뿌듯하다", "정답: 완주 후 포기하지 않은 것을 잘했다고 느끼는 뿌듯함이 드러납니다."),
                                opt("실망스럽다", "🔵 파랑 - 게시물 내용과 반대되는 오답입니다."),
                                opt("지루하다", "🔵 파랑 - 언급되지 않은 감정입니다."),
                                opt("무섭다", "🔵 파랑 - 언급되지 않은 감정입니다.")
                        ), 0, "🔵 성취감을 무관한 부정적 감정으로 착각하게 합니다.",
                                "[게시물 마인드맵] 행동(완주) → 감정(뿌듯함). 마지막 문장의 평가 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "온라인 강의 안내",
                        "[강의 안내] 실시간 강의는 녹화되어 다시 볼 수 있으며, 다시보기는 강의 종료 후 24시간 이내에 업로드됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("다시보기는 24시간 이내에 올라온다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("다시보기는 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("다시보기는 일주일 후에 올라온다.", "🔵 파랑 - 시간 숫자를 바꾼 오답입니다."),
                                opt("실시간 강의만 볼 수 있다.", "🔵 파랑 - '녹화되어 다시 볼 수 있다'와 반대되는 오답입니다.")
                        ), 0, "🔵 다시보기 제공 여부나 업로드 시점을 바꿔 오답을 만듭니다.",
                                "[강의 마인드맵] 실시간(원본) → 녹화(다시보기, 24시간 이내). 시점을 색칠하세요.")),
                onePassage(PassageCategory.READING, "화상회의 초대장",
                        "[초대장] 회의 참석자는 사전에 자료를 검토해 주시기 바랍니다. 자료는 이메일로 첨부되어 있습니다.",
                        q("이 초대장의 요청 사항으로 알맞은 것을 고르십시오.", List.of(
                                opt("사전에 자료를 검토하기", "정답: '사전에 자료를 검토해 주시기 바랍니다'라고 명시했습니다."),
                                opt("회의 중에 자료를 요청하기", "🔵 파랑 - '사전에'라는 시점과 반대되는 오답입니다."),
                                opt("자료를 인쇄해 오기", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("자료 검토는 필요 없다.", "🔵 파랑 - 요청 사항과 반대되는 오답입니다.")
                        ), 0, "🔵 요청 시점을 바꾸거나 요청 자체를 반대로 착각하게 합니다.",
                                "[초대장 마인드맵] 요청(사전 검토) + 방법(이메일 첨부). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "쇼핑몰 이벤트",
                        "[이벤트 안내] 리뷰 작성 시 추첨을 통해 10명에게 상품권을 드립니다. 당첨자는 이벤트 종료 후 개별 안내됩니다.",
                        q("이 이벤트의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("당첨자는 개별로 안내된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("당첨자는 전체 공지로 발표된다.", "🔵 파랑 - '개별 안내'와 반대되는 오답입니다."),
                                opt("리뷰를 쓴 모든 사람이 당첨된다.", "🔵 파랑 - '추첨을 통해 10명'이라는 조건을 빼고 읽게 합니다."),
                                opt("당첨자 수는 정해지지 않았다.", "🔵 파랑 - '10명'이라는 숫자와 반대되는 오답입니다.")
                        ), 0, "🔵 발표 방식이나 당첨 인원, 조건을 바꿔 오답을 만듭니다.",
                                "[이벤트 마인드맵] 참여(리뷰 작성) + 당첨(추첨 10명, 개별 안내). 색칠하세요."))
        );

        List<PassageSeed> reading31to40 = List.of(
                onePassage(PassageCategory.READING, "앱 사용 후기",
                        "[앱 리뷰] ★★☆☆☆ 업데이트 후 자꾸 앱이 꺼져요. 빠른 수정 부탁드려요.",
                        q("이 리뷰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("업데이트 후 앱이 자주 꺼진다.", "정답: '업데이트 후 자꾸 앱이 꺼진다'고 명시했습니다."),
                                opt("업데이트 전에 문제가 있었다.", "🔵 파랑 - '업데이트 후'라는 시점과 반대되는 오답입니다."),
                                opt("앱이 잘 작동한다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("만족도가 매우 높다.", "🔵 파랑 - 낮은 별점(★★☆☆☆)과 반대되는 오답입니다.")
                        ), 0, "🔵 시점(업데이트 전/후)을 바꾸거나 평가를 반대로 착각하게 합니다.",
                                "[리뷰 마인드맵] 원인(업데이트) → 문제(앱 꺼짐). 별점이 낮으면 문제점에 주목하세요.")),
                onePassage(PassageCategory.READING, "SNS 게시물",
                        "[SNS 게시물] 새로 산 카메라로 찍은 사진들이에요. 다음 주에는 부산으로 출사를 갈 예정이에요.",
                        q("이 사람이 다음 주에 하려는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("부산으로 출사를 간다.", "정답: '다음 주에는 부산으로 출사를 갈 예정'이라고 명시했습니다."),
                                opt("카메라를 새로 산다.", "🔵 파랑 - 이미 완료된 일을 미래로 착각하게 합니다."),
                                opt("서울에서 촬영한다.", "🔵 파랑 - '부산'을 '서울'로 바꾼 오답입니다."),
                                opt("사진전에 참가한다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 이미 완료된 일과 미래 계획을 혼동하게 하거나 지역을 바꿔 오답을 만듭니다.",
                                "[게시물 마인드맵] 과거(카메라 구매) → 미래(부산 출사). 시제를 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "온라인 강의 안내",
                        "[강의 안내] 질문 게시판을 통해 강사에게 직접 질문할 수 있으며, 답변은 보통 24시간 이내에 등록됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("답변은 보통 24시간 이내에 등록된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("질문은 받지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("답변은 일주일 걸린다.", "🔵 파랑 - 시간 숫자를 바꾼 오답입니다."),
                                opt("질문은 이메일로만 가능하다.", "🔵 파랑 - '질문 게시판'을 '이메일'로 바꾼 오답입니다.")
                        ), 0, "🔵 답변 소요 시간이나 질문 방법을 바꿔 오답을 만듭니다.",
                                "[강의 마인드맵] 방법(질문 게시판) + 소요 시간(24시간 이내). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "화상회의 초대장",
                        "[초대장] 회의는 최대 1시간 진행되며, 참석이 어려운 경우 사전에 회신 부탁드립니다.",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("불참 시 사전에 회신해야 한다.", "정답: '참석이 어려운 경우 사전에 회신 부탁드립니다'라고 명시했습니다."),
                                opt("불참해도 회신할 필요 없다.", "🔵 파랑 - 명시된 요청과 반대되는 오답입니다."),
                                opt("회의는 최대 3시간 진행된다.", "🔵 파랑 - '1시간'을 '3시간'으로 바꾼 오답입니다."),
                                opt("모든 참석자가 필수로 발언해야 한다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 회신 필요 여부나 회의 시간을 바꿔 오답을 만듭니다.",
                                "[초대장 마인드맵] 시간(최대 1시간) + 요청(불참 시 회신). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "쇼핑몰 이벤트",
                        "[이벤트 안내] 특정 브랜드 상품 구매 시 사은품을 증정합니다. 사은품은 선착순 100명 한정입니다.",
                        q("이 이벤트의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("사은품은 선착순 100명까지만 받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("사은품은 인원 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("모든 상품 구매 시 사은품을 준다.", "🔵 파랑 - '특정 브랜드'라는 조건을 빼고 읽게 합니다."),
                                opt("사은품은 200명에게 준다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 인원 제한이나 대상 조건, 숫자를 바꿔 오답을 만듭니다.",
                                "[이벤트 마인드맵] 조건(특정 브랜드) + 한정(선착순 100명). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "앱 사용 후기",
                        "[앱 리뷰] ★★★★☆ 인터페이스가 직관적이라 처음 써도 헷갈리지 않았어요.",
                        q("이 리뷰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("인터페이스가 이해하기 쉽다.", "정답: '직관적이라 헷갈리지 않았다'는 내용입니다."),
                                opt("인터페이스가 복잡하다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("처음 쓰는 사람은 사용하기 어렵다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("여러 번 써야 익숙해진다.", "🔵 파랑 - '처음 써도'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 사용 편의성 평가를 반대로 착각하게 합니다.",
                                "[리뷰 마인드맵] 특징(직관적) → 결과(안 헷갈림). 인과관계를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "SNS 게시물",
                        "[SNS 게시물] 오랜만에 고향 친구들을 만났어요. 다들 그대로인 것 같아서 반가웠습니다.",
                        q("이 사람의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("반갑다", "정답: '반가웠습니다'라고 직접 표현했습니다."),
                                opt("서운하다", "🔵 파랑 - 언급되지 않은 감정입니다."),
                                opt("어색하다", "🔵 파랑 - 언급되지 않은 감정입니다."),
                                opt("피곤하다", "🔵 파랑 - 언급되지 않은 감정입니다.")
                        ), 0, "🔵 명확한 감정 표현을 무관한 다른 감정으로 착각하게 합니다.",
                                "[게시물 마인드맵] 상황(오랜만의 재회) → 감정(반가움). 감정 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "온라인 강의 안내",
                        "[강의 안내] 이 강의는 모바일 앱에서도 수강할 수 있으며, PC와 진도가 자동으로 동기화됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("모바일과 PC 진도가 자동으로 동기화된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모바일에서는 수강할 수 없다.", "🔵 파랑 - '모바일 앱에서도 수강 가능'과 반대되는 오답입니다."),
                                opt("진도는 수동으로 입력해야 한다.", "🔵 파랑 - '자동으로 동기화'와 반대되는 오답입니다."),
                                opt("PC에서만 수강할 수 있다.", "🔵 파랑 - '모바일에서도'라는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 수강 가능 기기나 동기화 방식(자동/수동)을 반대로 착각하게 합니다.",
                                "[강의 마인드맵] 기기(모바일+PC) + 진도(자동 동기화). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "화상회의 초대장",
                        "[초대장] 이번 회의는 녹화되지 않으니, 필요한 내용은 직접 메모해 주시기 바랍니다.",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이 회의는 녹화되지 않는다.", "정답: '녹화되지 않는다'고 명시했습니다."),
                                opt("이 회의는 녹화된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("메모는 필요 없다.", "🔵 파랑 - '직접 메모해 주세요'와 반대되는 오답입니다."),
                                opt("회의 후 녹화본을 보내준다.", "🔵 파랑 - '녹화되지 않는다'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 녹화 여부를 반대로 착각하게 하거나 메모 필요성을 반대로 왜곡합니다.",
                                "[초대장 마인드맵] 녹화(안 됨) → 요청(직접 메모). 부정 표현에 주목하세요.")),
                onePassage(PassageCategory.READING, "쇼핑몰 이벤트",
                        "[이벤트 안내] 앱 설치 후 첫 로그인 시 무료 배송 쿠폰이 자동으로 지급됩니다.",
                        q("이 이벤트의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("첫 로그인 시 쿠폰이 자동 지급된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("쿠폰을 직접 신청해야 한다.", "🔵 파랑 - '자동으로 지급'과 반대되는 오답입니다."),
                                opt("두 번째 로그인부터 쿠폰을 준다.", "🔵 파랑 - '첫 로그인'이라는 조건과 반대되는 오답입니다."),
                                opt("쿠폰은 할인 쿠폰이다.", "🔵 파랑 - '무료 배송 쿠폰'을 '할인 쿠폰'으로 바꾼 오답입니다.")
                        ), 0, "🔵 자동/수동 지급 여부나 조건, 쿠폰 종류를 바꿔 오답을 만듭니다.",
                                "[이벤트 마인드맵] 조건(첫 로그인) + 혜택(무료배송 쿠폰, 자동). 색칠하세요."))
        );

        List<PassageSeed> listening2nd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "계절 옷차림",
                        "여자: 내일 날씨가 갑자기 추워진대요.\n남자: 그럼 두꺼운 외투를 입어야겠네요.",
                        q("남자가 입으려는 옷으로 알맞은 것을 고르십시오.", List.of(
                                opt("두꺼운 외투", "정답: '두꺼운 외투를 입어야겠다'고 답했습니다."),
                                opt("얇은 셔츠", "🔴 빨강 - 날씨 정보와 반대되는 오답입니다."),
                                opt("반팔 티셔츠", "🔴 빨강 - 날씨 정보와 반대되는 오답입니다."),
                                opt("수영복", "🔴 빨강 - 상황과 전혀 맞지 않는 오답입니다.")
                        ), 0, "🔴 날씨(추움)와 반대되는 옷차림으로 오답을 만듭니다.",
                                "[옷차림 마인드맵] 날씨(추움) → 옷(두꺼운 외투). 원인과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "미세먼지 대응",
                        "남자: 오늘 미세먼지가 심하대요.\n여자: 그럼 마스크 꼭 쓰고 나가야겠어요.",
                        q("여자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("마스크를 쓰고 나간다.", "정답: '마스크 꼭 쓰고 나가야겠다'고 답했습니다."),
                                opt("마스크 없이 나간다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("외출을 하지 않는다.", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("창문을 연다.", "🟢 초록 - 상황과 반대되는 행동입니다.")
                        ), 0, "🟢 실제 행동(마스크 착용)과 반대되거나 무관한 행동으로 오답을 만듭니다.",
                                "[미세먼지 마인드맵] 상황(심함) → 대응(마스크 착용). 원인과 대응을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "실내 활동",
                        "여자: 비 오는 날엔 뭐 하고 놀아요?\n남자: 저는 주로 집에서 보드게임을 해요.",
                        q("남자가 비 오는 날에 하는 활동으로 알맞은 것을 고르십시오.", List.of(
                                opt("집에서 보드게임 하기", "정답: '집에서 보드게임을 한다'고 답했습니다."),
                                opt("공원에서 산책하기", "🟢 초록 - 실외 활동으로 바꾼 오답입니다."),
                                opt("영화관 가기", "🟢 초록 - 언급되지 않은 활동입니다."),
                                opt("등산하기", "🟢 초록 - 실외 활동으로 바꾼 오답입니다.")
                        ), 0, "🟢 실내 활동을 실외 활동으로 바꾸거나 무관한 활동으로 오답을 만듭니다.",
                                "[실내활동 마인드맵] 날씨(비) → 활동(보드게임, 실내). 날씨와 장소를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "야외 활동",
                        "남자: 이번 주말에 날씨 좋으면 캠핑 갈까요?\n여자: 좋아요! 텐트도 새로 샀는데 써 봐야겠어요.",
                        q("두 사람이 하려는 활동으로 알맞은 것을 고르십시오.", List.of(
                                opt("캠핑 가기", "정답: '캠핑 갈까요'라고 제안하고 동의했습니다."),
                                opt("등산 가기", "🔵 파랑 - 언급되지 않은 활동입니다."),
                                opt("낚시 가기", "🔵 파랑 - 언급되지 않은 활동입니다."),
                                opt("집에서 쉬기", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 활동과 무관한 다른 야외 활동으로 오답을 만듭니다.",
                                "[야외활동 마인드맵] 조건(날씨 좋음) + 활동(캠핑). 조건과 활동을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "날씨별 준비물",
                        "여자: 오늘 오후에 비 온다는데 우산 챙겼어요?\n남자: 네, 가방에 접이식 우산 넣어 왔어요.",
                        q("남자가 챙긴 물건으로 알맞은 것을 고르십시오.", List.of(
                                opt("접이식 우산", "정답: '접이식 우산 넣어 왔다'고 답했습니다."),
                                opt("장우산", "🔵 파랑 - '접이식'을 '장우산'으로 바꾼 오답입니다."),
                                opt("우비", "🔵 파랑 - '우산'을 '우비'로 바꾼 오답입니다."),
                                opt("아무것도 안 챙겼다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 물건의 종류를 슬쩍 바꾸거나 준비 여부를 반대로 착각하게 합니다.",
                                "[준비물 마인드맵] 날씨(비) + 준비물(접이식 우산). 물건의 종류를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "계절 옷차림",
                        "남자: 여름 휴가 때 뭘 입고 갈까요?\n여자: 시원한 반팔이랑 반바지가 좋을 것 같아요.",
                        q("여자가 추천한 옷차림으로 알맞은 것을 고르십시오.", List.of(
                                opt("반팔과 반바지", "정답: '시원한 반팔이랑 반바지'라고 추천했습니다."),
                                opt("긴팔과 긴바지", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("두꺼운 코트", "🔴 빨강 - 계절과 맞지 않는 오답입니다."),
                                opt("정장", "🔴 빨강 - 언급되지 않은 옷차림입니다.")
                        ), 0, "🔴 계절(여름)에 맞지 않는 옷차림으로 오답을 만듭니다.",
                                "[옷차림 마인드맵] 계절(여름) → 옷(반팔, 반바지). 계절과 옷의 종류를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "미세먼지 대응",
                        "여자: 미세먼지 앱으로 오늘 수치 확인했어요?\n남자: 네, 오늘은 '나쁨' 단계라 실내 운동으로 바꿨어요.",
                        q("남자가 오늘 하려는 운동으로 알맞은 것을 고르십시오.", List.of(
                                opt("실내 운동", "정답: '실내 운동으로 바꿨다'고 답했습니다."),
                                opt("야외 운동", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("운동을 안 한다.", "🟢 초록 - '실내 운동으로 바꿨다'와 다른 오답입니다."),
                                opt("수영", "🟢 초록 - 언급되지 않은 운동입니다.")
                        ), 0, "🟢 실내/야외 운동을 뒤바꾸거나 무관한 운동으로 오답을 만듭니다.",
                                "[미세먼지 마인드맵] 수치(나쁨) → 대응(실내 운동으로 변경). 원인과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "실내 활동",
                        "남자: 요즘 집에서 뭐 하면서 시간 보내요?\n여자: 저는 요즘 홈베이킹에 빠져 있어요.",
                        q("여자가 요즘 집에서 하는 활동으로 알맞은 것을 고르십시오.", List.of(
                                opt("홈베이킹", "정답: '홈베이킹에 빠져 있다'고 답했습니다."),
                                opt("독서", "🟢 초록 - 언급되지 않은 활동입니다."),
                                opt("운동", "🟢 초록 - 언급되지 않은 활동입니다."),
                                opt("게임", "🟢 초록 - 언급되지 않은 활동입니다.")
                        ), 0, "🟢 실제 언급된 활동과 무관한 다른 실내 활동으로 오답을 만듭니다.",
                                "[실내활동 마인드맵] 질문(시간 보내는 법) → 답(홈베이킹). 핵심 명사를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "야외 활동",
                        "여자: 자전거 타기 좋은 계절이 됐네요.\n남자: 맞아요, 저도 이번 주말에 한강에 가서 타려고요.",
                        q("남자가 자전거를 타려는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("한강", "정답: '한강에 가서 타려고요'라고 답했습니다."),
                                opt("공원", "🔵 파랑 - 대화에 없는 장소입니다."),
                                opt("운동장", "🔵 파랑 - 대화에 없는 장소입니다."),
                                opt("산", "🔵 파랑 - 대화에 없는 장소입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 장소로 오답을 만듭니다.",
                                "[야외활동 마인드맵] 활동(자전거) + 장소(한강). 장소명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "날씨별 준비물",
                        "남자: 오늘 자외선이 강하다는데 선크림 발랐어요?\n여자: 네, 아침에 바르고 나왔어요.",
                        q("여자가 아침에 한 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("선크림을 발랐다.", "정답: '아침에 바르고 나왔다'고 답했습니다."),
                                opt("선크림을 안 발랐다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("모자를 썼다.", "🔵 파랑 - 언급되지 않은 행동입니다."),
                                opt("선글라스를 썼다.", "🔵 파랑 - 언급되지 않은 행동입니다.")
                        ), 0, "🔵 실제 언급된 행동과 무관한 다른 준비물로 오답을 만듭니다.",
                                "[준비물 마인드맵] 날씨(자외선 강함) + 준비물(선크림). 실제 언급된 물건만 정답입니다."))
        );

        List<PassageSeed> listening2nd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "계절 옷차림",
                        "여자: 환절기라 아침저녁으로 쌀쌀하네요.\n남자: 맞아요, 그래서 저는 얇은 겉옷을 하나 더 챙겨요.",
                        q("남자가 환절기에 챙기는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("얇은 겉옷", "정답: '얇은 겉옷을 하나 더 챙긴다'고 답했습니다."),
                                opt("두꺼운 패딩", "🔴 빨강 - '얇은'을 '두꺼운'으로 바꾼 오답입니다."),
                                opt("우산", "🔴 빨강 - 언급되지 않은 물건입니다."),
                                opt("장갑", "🔴 빨강 - 언급되지 않은 물건입니다.")
                        ), 0, "🔴 옷의 두께나 종류를 바꿔 오답을 만듭니다.",
                                "[옷차림 마인드맵] 계절(환절기) + 준비물(얇은 겉옷). 두께 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "미세먼지 대응",
                        "여자: 미세먼지 심한 날엔 창문을 어떻게 해야 해요?\n남자: 환기는 미세먼지가 나쁜 날엔 피하고, 공기청정기를 트는 게 좋아요.",
                        q("남자가 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("공기청정기 틀기", "정답: '공기청정기를 트는 게 좋다'고 제안했습니다."),
                                opt("창문을 자주 열기", "🟢 초록 - '환기는 피하라'는 내용과 반대되는 오답입니다."),
                                opt("가습기 틀기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("외출하기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(공기청정기)과 반대되거나 무관한 다른 행동으로 오답을 만듭니다.",
                                "[미세먼지 마인드맵] 금지(환기) + 권장(공기청정기). 두 표현을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "실내 활동",
                        "남자: 이 실내 클라이밍장 처음 와 봐요.\n여자: 저도 처음인데 생각보다 재미있네요.",
                        q("두 사람에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("두 사람 모두 처음 온다.", "정답: 둘 다 '처음'이라고 답했습니다."),
                                opt("남자만 처음 온다.", "🟢 초록 - 여자도 처음이라는 내용을 놓치게 합니다."),
                                opt("여자만 처음 온다.", "🟢 초록 - 남자도 처음이라는 내용을 놓치게 합니다."),
                                opt("둘 다 예전에 와 봤다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 한쪽만 처음이라고 착각하게 하거나 완전히 반대로 왜곡합니다.",
                                "[실내활동 마인드맵] 남자(처음) + 여자(처음). 두 사람 모두 같은 경험임을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "야외 활동",
                        "여자: 이번 등산은 얼마나 걸려요?\n남자: 왕복 3시간 정도 걸릴 거예요.",
                        q("등산에 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("왕복 3시간", "정답: '왕복 3시간 정도 걸릴 것'이라고 답했습니다."),
                                opt("편도 3시간", "🔴 빨강 - '왕복'을 '편도'로 바꾼 오답입니다."),
                                opt("왕복 1시간", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("왕복 5시간", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 왕복/편도를 혼동하게 하거나 시간 숫자를 바꿔 오답을 만듭니다.",
                                "[야외활동 마인드맵] 구간(왕복) + 시간(3시간). 두 정보를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "날씨별 준비물",
                        "남자: 겨울 산행할 때 꼭 필요한 게 뭐예요?\n여자: 아이젠이랑 방한 장갑은 꼭 챙기세요.",
                        q("여자가 추천한 준비물로 알맞은 것을 고르십시오.", List.of(
                                opt("아이젠과 방한 장갑", "정답: '아이젠이랑 방한 장갑은 꼭 챙기라'고 답했습니다."),
                                opt("우산과 우비", "🔵 파랑 - 계절과 맞지 않는 오답입니다."),
                                opt("선크림과 모자", "🔵 파랑 - 여름철 준비물로 착각하게 하는 오답입니다."),
                                opt("아무것도 필요 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 계절에 맞지 않는 다른 준비물로 오답을 만듭니다.",
                                "[준비물 마인드맵] 계절(겨울 산행) + 준비물(아이젠, 방한 장갑). 두 물건을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "계절 옷차림",
                        "여자: 장마철엔 신발을 뭘 신어야 해요?\n남자: 방수 신발이나 장화를 추천해요.",
                        q("남자가 추천한 신발로 알맞은 것을 고르십시오.", List.of(
                                opt("방수 신발이나 장화", "정답: 대화에 그대로 나와 있습니다."),
                                opt("샌들", "🔴 빨강 - 장마철과 맞지 않는 오답입니다."),
                                opt("운동화", "🔴 빨강 - 방수 기능이 없는 오답입니다."),
                                opt("구두", "🔴 빨강 - 언급되지 않은 신발입니다.")
                        ), 0, "🔴 계절(장마철)에 맞지 않는 신발로 오답을 만듭니다.",
                                "[옷차림 마인드맵] 계절(장마철) + 신발(방수, 장화). 기능성 단어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "미세먼지 대응",
                        "남자: 미세먼지 마스크는 아무거나 써도 되나요?\n여자: 아니요, KF80 이상 등급을 써야 효과가 있어요.",
                        q("여자가 추천한 마스크 등급으로 알맞은 것을 고르십시오.", List.of(
                                opt("KF80 이상", "정답: 'KF80 이상 등급을 써야 한다'고 답했습니다."),
                                opt("KF80 이하", "🔴 빨강 - '이상'을 '이하'로 바꾼 오답입니다."),
                                opt("등급은 상관없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("일반 마스크면 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 이상/이하를 뒤바꾸거나 등급이 상관없다고 왜곡합니다.",
                                "[미세먼지 마인드맵] 조건(KF80 이상) → 효과. '이상'이라는 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "실내 활동",
                        "여자: 실내 수영장 이용 시간이 어떻게 되나요?\n남자: 오전 6시부터 오후 10시까지 이용 가능해요.",
                        q("실내 수영장 이용 가능 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("오전 6시부터 오후 10시까지", "정답: 대화에 그대로 나와 있습니다."),
                                opt("오전 9시부터 오후 6시까지", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("24시간 운영", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("오후에만 운영", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 운영 시간 숫자를 바꾸거나 24시간 운영으로 과장합니다.",
                                "[실내활동 마인드맵] 시작(오전 6시) + 종료(오후 10시). 두 시간을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "야외 활동",
                        "남자: 이 공원에 피크닉 존이 있어요?\n여자: 네, 잔디밭 구역에서 돗자리 깔고 쉴 수 있어요.",
                        q("피크닉을 즐길 수 있는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("잔디밭 구역", "정답: '잔디밭 구역에서 쉴 수 있다'고 답했습니다."),
                                opt("주차장", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("놀이터", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("공원 입구", "🔵 파랑 - 언급되지 않은 장소입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 구역으로 오답을 만듭니다.",
                                "[야외활동 마인드맵] 활동(피크닉) + 장소(잔디밭 구역). 장소명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "날씨별 준비물",
                        "여자: 폭염 특보인데 물 충분히 챙기셨어요?\n남자: 네, 텀블러에 물 가득 채워 왔어요.",
                        q("남자가 챙긴 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("물을 채운 텀블러", "정답: '텀블러에 물 가득 채워 왔다'고 답했습니다."),
                                opt("물을 안 챙겼다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("음료수", "🔵 파랑 - '물'을 '음료수'로 바꾼 오답입니다."),
                                opt("얼음물이 아닌 뜨거운 물", "🔵 파랑 - 언급되지 않은 세부 내용입니다.")
                        ), 0, "🔵 실제 언급된 물건과 무관한 다른 음료로 오답을 만듭니다.",
                                "[준비물 마인드맵] 날씨(폭염) + 준비물(물, 텀블러). 물건명을 정확히 확인하세요."))
        );

        List<PassageSeed> reading2nd21to30 = List.of(
                onePassage(PassageCategory.READING, "날씨 예보",
                        "[날씨 예보] 내일은 전국적으로 맑겠으며, 낮 기온은 25도까지 오르겠습니다.",
                        q("이 예보의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("내일 낮 최고 기온은 25도다.", "정답: 예보에 그대로 명시되어 있습니다."),
                                opt("내일 비가 온다.", "🔵 파랑 - '맑겠다'와 반대되는 오답입니다."),
                                opt("내일 기온이 15도까지 오른다.", "🔵 파랑 - 기온 숫자를 바꾼 오답입니다."),
                                opt("일부 지역만 맑다.", "🔵 파랑 - '전국적으로'라는 범위와 반대되는 오답입니다.")
                        ), 0, "🔵 날씨나 기온 숫자, 범위(전국/일부)를 바꿔 오답을 만듭니다.",
                                "[예보 마인드맵] 날씨(맑음) + 기온(25도) + 범위(전국). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "계절별 건강수칙",
                        "[건강수칙] 여름철에는 충분한 수분 섭취가 중요하며, 갈증을 느끼기 전에 물을 자주 마시는 것이 좋습니다.",
                        q("이 건강수칙의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("갈증을 느끼기 전에 물을 마셔야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("갈증을 느낀 후에 마셔도 충분하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("물을 적게 마시는 것이 좋다.", "🔵 파랑 - '충분한 수분 섭취'와 반대되는 오답입니다."),
                                opt("겨울철 건강수칙이다.", "🔵 파랑 - '여름철'을 '겨울철'로 바꾼 오답입니다.")
                        ), 0, "🔵 수분 섭취 시점이나 계절을 바꿔 오답을 만듭니다.",
                                "[건강수칙 마인드맵] 계절(여름) + 습관(갈증 전 물 마시기). 시점 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "실내활동 안내",
                        "[실내활동 안내] 실내 볼링장은 6개월 이상 이용하지 않으면 회원 등급이 초기화됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("6개월 이상 미이용 시 등급이 초기화된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("등급은 절대 초기화되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("3개월만 이용하지 않아도 초기화된다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다."),
                                opt("매달 등급이 초기화된다.", "🔵 파랑 - 조건을 무시한 과장된 오답입니다.")
                        ), 0, "🔵 초기화 조건 기간을 바꾸거나 조건 자체를 왜곡합니다.",
                                "[안내문 마인드맵] 조건(6개월 미이용) → 결과(등급 초기화). 기간 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "야외활동 주의사항",
                        "[야외활동 주의사항] 등산 시에는 정해진 등산로를 벗어나지 말고, 해 지기 전에 하산을 완료하세요.",
                        q("이 주의사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("해 지기 전에 하산해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("밤에 등산해도 안전하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("등산로를 벗어나도 괜찮다.", "🔵 파랑 - '벗어나지 말라'는 내용과 반대되는 오답입니다."),
                                opt("하산 시간에 제한이 없다.", "🔵 파랑 - '해 지기 전'이라는 제한과 반대되는 오답입니다.")
                        ), 0, "🔵 등산로 준수 여부나 하산 시간 제한을 반대로 착각하게 합니다.",
                                "[주의사항 마인드맵] 금지(등산로 이탈) + 시간(해 지기 전 하산). 두 규칙을 색칠하세요.")),
                onePassage(PassageCategory.READING, "옷차림 가이드",
                        "[옷차림 가이드] 환절기에는 얇은 옷을 여러 겹 겹쳐 입어 체온 조절을 쉽게 하는 것이 좋습니다.",
                        q("이 가이드의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("얇은 옷을 여러 겹 입는 것이 좋다.", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("두꺼운 옷 한 벌만 입는 것이 좋다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("겨울철 옷차림 가이드다.", "🔵 파랑 - '환절기'를 '겨울철'로 바꾼 오답입니다."),
                                opt("옷의 두께는 상관없다.", "🔵 파랑 - '얇은 옷'이라는 조건과 반대되는 오답입니다.")
                        ), 0, "🔵 옷 겹쳐 입기 방식이나 계절, 두께 조건을 바꿔 오답을 만듭니다.",
                                "[옷차림 마인드맵] 계절(환절기) + 방법(얇은 옷 겹쳐 입기). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "날씨 예보",
                        "[날씨 예보] 이번 주말 제주도에는 태풍의 영향으로 강한 비바람이 예상됩니다.",
                        q("이 예보의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("제주도에 강한 비바람이 예상된다.", "정답: 예보에 그대로 명시되어 있습니다."),
                                opt("제주도 날씨는 맑을 것이다.", "🔵 파랑 - 예보 내용과 반대되는 오답입니다."),
                                opt("서울에 태풍이 온다.", "🔵 파랑 - '제주도'를 '서울'로 바꾼 오답입니다."),
                                opt("이번 주 초에 태풍이 온다.", "🔵 파랑 - '주말'을 '주 초'로 바꾼 오답입니다.")
                        ), 0, "🔵 날씨 상태나 지역, 시점을 바꿔 오답을 만듭니다.",
                                "[예보 마인드맵] 시점(주말) + 지역(제주도) + 날씨(태풍). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "계절별 건강수칙",
                        "[건강수칙] 겨울철에는 실내외 온도 차가 크므로 외출 전 가벼운 스트레칭으로 몸을 풀어 주는 것이 좋습니다.",
                        q("이 건강수칙의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("외출 전 스트레칭을 하는 것이 좋다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("스트레칭은 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("여름철 건강수칙이다.", "🔵 파랑 - '겨울철'을 '여름철'로 바꾼 오답입니다."),
                                opt("실내외 온도 차가 적다.", "🔵 파랑 - '온도 차가 크다'와 반대되는 오답입니다.")
                        ), 0, "🔵 계절이나 온도 차 정도, 스트레칭 필요성을 반대로 착각하게 합니다.",
                                "[건강수칙 마인드맵] 계절(겨울) + 이유(온도차) + 방법(스트레칭). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "실내활동 안내",
                        "[실내활동 안내] 방탈출 카페는 사전 예약제로만 운영되며, 최소 2인부터 입장 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("최소 2인부터 입장할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("1인도 입장할 수 있다.", "🔵 파랑 - '최소 2인'이라는 조건과 반대되는 오답입니다."),
                                opt("예약 없이 방문 가능하다.", "🔵 파랑 - '사전 예약제'와 반대되는 오답입니다."),
                                opt("최대 인원 제한이 있다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 최소 인원 조건이나 예약 필요 여부를 반대로 착각하게 합니다.",
                                "[안내문 마인드맵] 방식(사전 예약) + 인원(최소 2인). 두 조건을 색칠하세요.")),
                onePassage(PassageCategory.READING, "야외활동 주의사항",
                        "[야외활동 주의사항] 물놀이 시에는 반드시 구명조끼를 착용하고, 보호자 없이 아이 혼자 물에 들어가지 않도록 하세요.",
                        q("이 주의사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("아이는 보호자와 함께 물에 들어가야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("아이 혼자 물에 들어가도 안전하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("구명조끼는 선택 사항이다.", "🔵 파랑 - '반드시 착용'과 반대되는 오답입니다."),
                                opt("성인은 구명조끼가 필요 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 보호자 동반 필요성이나 구명조끼 착용 여부를 반대로 착각하게 합니다.",
                                "[주의사항 마인드맵] 필수(구명조끼) + 필수(보호자 동반). 두 규칙을 색칠하세요.")),
                onePassage(PassageCategory.READING, "옷차림 가이드",
                        "[옷차림 가이드] 면접 복장은 단정한 정장 스타일을 추천하며, 지나치게 화려한 색상은 피하는 것이 좋습니다.",
                        q("이 가이드의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("화려한 색상은 피하는 것이 좋다.", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("화려한 색상을 추천한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("캐주얼한 복장을 추천한다.", "🔵 파랑 - '정장 스타일'과 반대되는 오답입니다."),
                                opt("복장에 제한이 없다.", "🔵 파랑 - 명시된 권장사항과 반대되는 오답입니다.")
                        ), 0, "🔵 색상 권장 여부나 복장 스타일을 반대로 착각하게 합니다.",
                                "[옷차림 마인드맵] 스타일(정장) + 색상(화려함 피하기). 두 조건을 색칠하세요."))
        );

        List<PassageSeed> reading2nd31to40 = List.of(
                onePassage(PassageCategory.READING, "날씨 예보",
                        "[날씨 예보] 오늘 밤부터 기온이 큰 폭으로 떨어지니 건강 관리에 유의하시기 바랍니다.",
                        q("이 예보의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("오늘 밤부터 기온이 크게 떨어진다.", "정답: 예보에 그대로 명시되어 있습니다."),
                                opt("오늘 밤부터 기온이 크게 오른다.", "🔵 파랑 - '떨어진다'를 '오른다'로 바꾼 오답입니다."),
                                opt("내일 낮부터 기온이 떨어진다.", "🔵 파랑 - 시점을 바꾼 오답입니다."),
                                opt("기온 변화가 거의 없다.", "🔵 파랑 - '큰 폭으로'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 상승/하락을 뒤바꾸거나 시점, 변화 폭을 왜곡합니다.",
                                "[예보 마인드맵] 시점(오늘 밤부터) + 변화(큰 폭 하락). 방향과 시점을 색칠하세요.")),
                onePassage(PassageCategory.READING, "계절별 건강수칙",
                        "[건강수칙] 환절기에는 일교차가 커서 감기에 걸리기 쉬우니 실내 습도를 40~60%로 유지하는 것이 좋습니다.",
                        q("이 건강수칙에서 권장하는 실내 습도로 알맞은 것을 고르십시오.", List.of(
                                opt("40~60%", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("10~20%", "🔵 파랑 - 습도 숫자를 바꾼 오답입니다."),
                                opt("80~100%", "🔵 파랑 - 습도 숫자를 바꾼 오답입니다."),
                                opt("습도는 상관없다.", "🔵 파랑 - 명시된 권장사항과 반대되는 오답입니다.")
                        ), 0, "🔵 습도 범위 숫자를 바꾸거나 상관없다고 왜곡합니다.",
                                "[건강수칙 마인드맵] 원인(일교차) + 대응(습도 40~60%). 숫자 범위를 정확히 확인하세요.")),
                onePassage(PassageCategory.READING, "실내활동 안내",
                        "[실내활동 안내] 실내 암벽등반장은 초보자를 위한 강습이 매주 화요일 저녁에 운영됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초보자 강습은 화요일 저녁에 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("초보자 강습은 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("강습은 매일 진행된다.", "🔵 파랑 - '매주 화요일'을 '매일'로 바꾼 오답입니다."),
                                opt("강습은 오전에 진행된다.", "🔵 파랑 - '저녁'을 '오전'으로 바꾼 오답입니다.")
                        ), 0, "🔵 강습 유무나 요일, 시간대를 바꿔 오답을 만듭니다.",
                                "[안내문 마인드맵] 대상(초보자) + 시간(화요일 저녁). 두 정보를 색칠하세요.")),
                onePassage(PassageCategory.READING, "야외활동 주의사항",
                        "[야외활동 주의사항] 캠핑 시 화기 사용 후에는 반드시 완전히 꺼졌는지 확인하고 자리를 떠나야 합니다.",
                        q("이 주의사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("화기가 꺼졌는지 확인해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("화기는 확인 없이 그냥 두어도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("화기 사용이 완전히 금지되어 있다.", "🔵 파랑 - '사용 후 확인'과 다른 과장된 오답입니다."),
                                opt("화기는 캠핑장 직원이 관리한다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 확인 필요 여부를 반대로 착각하게 하거나 사용 금지로 과장합니다.",
                                "[주의사항 마인드맵] 사용 후(화기 확인) → 자리 이동. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "옷차림 가이드",
                        "[옷차림 가이드] 등산복은 땀 흡수가 잘 되고 빨리 마르는 기능성 소재를 선택하는 것이 좋습니다.",
                        q("이 가이드에서 추천하는 등산복 소재의 특징으로 알맞은 것을 고르십시오.", List.of(
                                opt("땀 흡수가 잘 되고 빨리 마름", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("땀 흡수가 안 되고 오래 젖어 있음", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("두껍고 무거움", "🔵 파랑 - 언급되지 않은 특징입니다."),
                                opt("면 소재를 추천한다.", "🔵 파랑 - '기능성 소재'와 다른 오답입니다.")
                        ), 0, "🔵 소재 특징을 반대로 착각하게 하거나 언급되지 않은 소재로 오답을 만듭니다.",
                                "[옷차림 마인드맵] 활동(등산) + 소재 특징(흡수+속건). 두 특징을 색칠하세요.")),
                onePassage(PassageCategory.READING, "날씨 예보",
                        "[날씨 예보] 오전에는 안개가 짙게 끼겠으니 운전 시 주의하시기 바랍니다.",
                        q("이 예보의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("오전에 안개가 짙게 낀다.", "정답: 예보에 그대로 명시되어 있습니다."),
                                opt("오후에 안개가 낀다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("안개는 옅게 낀다.", "🔵 파랑 - '짙게'를 '옅게'로 바꾼 오답입니다."),
                                opt("운전에 아무 문제가 없다.", "🔵 파랑 - '운전 시 주의'와 반대되는 오답입니다.")
                        ), 0, "🔵 시점이나 안개의 정도, 주의사항 여부를 바꿔 오답을 만듭니다.",
                                "[예보 마인드맵] 시점(오전) + 상태(짙은 안개) + 주의(운전). 색칠하세요.")),
                onePassage(PassageCategory.READING, "계절별 건강수칙",
                        "[건강수칙] 봄철에는 꽃가루 알레르기가 심해질 수 있으니 외출 후 손발을 깨끗이 씻는 것이 좋습니다.",
                        q("이 건강수칙의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("외출 후 손발을 씻는 것이 좋다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("손발을 씻을 필요가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("가을철 건강수칙이다.", "🔵 파랑 - '봄철'을 '가을철'로 바꾼 오답입니다."),
                                opt("꽃가루와 무관한 수칙이다.", "🔵 파랑 - 명시된 원인과 반대되는 오답입니다.")
                        ), 0, "🔵 계절이나 씻는 행위의 필요성을 반대로 착각하게 합니다.",
                                "[건강수칙 마인드맵] 계절(봄) + 원인(꽃가루) + 대응(손발 씻기). 색칠하세요.")),
                onePassage(PassageCategory.READING, "실내활동 안내",
                        "[실내활동 안내] 실내 스크린골프장은 회원 등록 시 락커룸을 무료로 이용할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("회원은 락커룸을 무료로 이용한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("락커룸 이용은 유료다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("비회원도 락커룸을 무료로 쓴다.", "🔵 파랑 - '회원 등록 시'라는 조건을 빼고 읽게 합니다."),
                                opt("락커룸은 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 무료/유료 여부나 회원 조건을 빼고 읽게 합니다.",
                                "[안내문 마인드맵] 조건(회원 등록) + 혜택(락커룸 무료). 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "야외활동 주의사항",
                        "[야외활동 주의사항] 낚시 시에는 구명조끼를 착용하고, 미끄러운 바위 위는 피해서 이동하세요.",
                        q("이 주의사항의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("미끄러운 바위는 피해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("미끄러운 바위 위로 다녀도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("구명조끼는 필요 없다.", "🔵 파랑 - '구명조끼를 착용하라'와 반대되는 오답입니다."),
                                opt("낚시는 위험하지 않다.", "🔵 파랑 - 주의사항이 있다는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 이동 경로나 구명조끼 착용 필요성을 반대로 착각하게 합니다.",
                                "[주의사항 마인드맵] 착용(구명조끼) + 회피(미끄러운 바위). 두 규칙을 색칠하세요.")),
                onePassage(PassageCategory.READING, "옷차림 가이드",
                        "[옷차림 가이드] 결혼식 하객 복장으로 흰색 계열의 옷은 신부를 배려해 피하는 것이 예의입니다.",
                        q("이 가이드의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("흰색 계열 옷은 피하는 것이 예의다.", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("흰색 옷을 입는 것이 예의다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("검은색 옷은 절대 안 된다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("복장에 특별한 예의가 없다.", "🔵 파랑 - 명시된 예의와 반대되는 오답입니다.")
                        ), 0, "🔵 권장/비권장 색상을 반대로 착각하게 하거나 언급되지 않은 색상 규칙을 추가합니다.",
                                "[옷차림 마인드맵] 상황(결혼식) + 금기(흰색). 색상 단어에 형광펜을 칠하세요."))
        );

        List<PassageSeed> listening3rd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "세금 신고",
                        "여자: 종합소득세 신고 기한이 언제까지예요?\n남자: 이번 달 31일까지예요.",
                        q("종합소득세 신고 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("이번 달 31일", "정답: '이번 달 31일까지'라고 답했습니다."),
                                opt("이번 달 15일", "🔴 빨강 - 날짜를 바꾼 오답입니다."),
                                opt("다음 달 31일", "🔴 빨강 - '이번 달'을 '다음 달'로 바꾼 오답입니다."),
                                opt("기한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 날짜나 월을 슬쩍 바꿔 오답을 만듭니다.",
                                "[세금 마인드맵] 신고 종류(종합소득세) + 기한(이번 달 31일). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "보험 가입",
                        "남자: 실손보험 가입하려고 하는데 어떻게 해요?\n여자: 보험사 앱에서 간단하게 가입할 수 있어요.",
                        q("여자가 안내한 가입 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("보험사 앱으로 가입하기", "정답: '보험사 앱에서 가입할 수 있다'고 답했습니다."),
                                opt("은행 창구 방문하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("보험 설계사와 통화하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("우편으로 신청하기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[보험 마인드맵] 상품(실손보험) + 방법(앱 가입). 방법을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "연금 안내",
                        "여자: 국민연금은 몇 살부터 받을 수 있어요?\n남자: 보통 만 65세부터 받을 수 있어요.",
                        q("국민연금을 받을 수 있는 나이로 알맞은 것을 고르십시오.", List.of(
                                opt("만 65세", "정답: '만 65세부터 받을 수 있다'고 답했습니다."),
                                opt("만 60세", "🔴 빨강 - 나이를 바꾼 오답입니다."),
                                opt("만 70세", "🔴 빨강 - 나이를 바꾼 오답입니다."),
                                opt("나이 제한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 나이 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[연금 마인드맵] 질문(수령 나이) → 답(만 65세). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "저축 상담",
                        "남자: 목돈을 모으고 싶은데 어떤 상품이 좋을까요?\n여자: 적금 상품을 추천해요. 매달 일정 금액을 넣으면 돼요.",
                        q("여자가 추천한 저축 상품으로 알맞은 것을 고르십시오.", List.of(
                                opt("적금", "정답: '적금 상품을 추천한다'고 답했습니다."),
                                opt("예금", "🔵 파랑 - '적금'을 '예금'으로 바꾼 오답입니다."),
                                opt("펀드", "🔵 파랑 - 언급되지 않은 상품입니다."),
                                opt("주식", "🔵 파랑 - 언급되지 않은 상품입니다.")
                        ), 0, "🔵 비슷한 금융 상품(예금/적금)을 혼동하게 합니다.",
                                "[저축 마인드맵] 목표(목돈) + 추천(적금). 상품명을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "카드 발급",
                        "여자: 신용카드 발급받으려면 얼마나 걸려요?\n남자: 신청 후 보통 일주일 정도 걸려요.",
                        q("신용카드 발급에 걸리는 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("일주일 정도", "정답: '신청 후 보통 일주일 정도 걸린다'고 답했습니다."),
                                opt("하루", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("한 달", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("즉시 발급된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 대화에 없는 임의의 기간으로 오답을 만듭니다.",
                                "[카드 마인드맵] 질문(발급 기간) → 답(일주일). 기간 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "세금 신고",
                        "남자: 연말정산 서류는 어디서 받아요?\n여자: 국세청 홈택스에서 다운로드할 수 있어요.",
                        q("연말정산 서류를 받는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("국세청 홈택스에서 다운로드", "정답: '홈택스에서 다운로드할 수 있다'고 답했습니다."),
                                opt("회사에서 직접 발급", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("은행에서 발급", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("우편으로 받기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 발급처로 오답을 만듭니다.",
                                "[세금 마인드맵] 서류(연말정산) + 방법(홈택스 다운로드). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "보험 가입",
                        "여자: 보험료는 매달 얼마나 내야 해요?\n남자: 이 상품은 월 3만 원이에요.",
                        q("이 보험 상품의 월 보험료로 알맞은 것을 고르십시오.", List.of(
                                opt("3만 원", "정답: '월 3만 원'이라고 답했습니다."),
                                opt("1만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("5만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("무료다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 보험료 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[보험 마인드맵] 주기(매달) + 금액(3만 원). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "연금 안내",
                        "남자: 개인연금도 따로 가입해야 하나요?\n여자: 국민연금과 별도로 개인연금에 가입하면 노후 대비에 도움이 돼요.",
                        q("여자의 조언으로 알맞은 것을 고르십시오.", List.of(
                                opt("개인연금에 별도로 가입하기", "정답: '개인연금에 가입하면 도움이 된다'고 조언했습니다."),
                                opt("국민연금만 믿기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("연금을 해지하기", "🟢 초록 - 언급되지 않은 조언입니다."),
                                opt("저축을 그만두기", "🟢 초록 - 언급되지 않은 조언입니다.")
                        ), 0, "🟢 실제 조언(개인연금 가입)과 반대되거나 무관한 내용으로 오답을 만듭니다.",
                                "[연금 마인드맵] 국민연금 + 개인연금(추가 가입 권장). 두 종류를 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "저축 상담",
                        "여자: 비상금은 얼마나 모아두는 게 좋아요?\n남자: 보통 생활비의 3~6개월 치를 추천해요.",
                        q("남자가 추천한 비상금 규모로 알맞은 것을 고르십시오.", List.of(
                                opt("생활비의 3~6개월 치", "정답: 대화에 그대로 나와 있습니다."),
                                opt("생활비의 1개월 치", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("생활비의 1년 치", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("비상금은 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 개월 수 숫자를 바꿔 오답을 만듭니다.",
                                "[저축 마인드맵] 기준(생활비) + 규모(3~6개월). 숫자 범위를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "카드 발급",
                        "남자: 체크카드랑 신용카드 중에 뭐가 더 나을까요?\n여자: 아직 소득이 없으면 체크카드부터 쓰는 게 좋아요.",
                        q("여자가 추천한 카드로 알맞은 것을 고르십시오.", List.of(
                                opt("체크카드", "정답: '체크카드부터 쓰는 게 좋다'고 답했습니다."),
                                opt("신용카드", "🔴 빨강 - 비교 대상을 뒤바꾼 오답입니다."),
                                opt("두 카드 다 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("현금만 쓰기", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 비교 대상(체크카드/신용카드)을 뒤바꿔 오답을 만듭니다.",
                                "[카드 마인드맵] 조건(소득 없음) → 추천(체크카드). 조건과 추천을 색으로 연결하세요."))
        );

        List<PassageSeed> listening3rd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "세금 신고",
                        "여자: 신고를 늦게 하면 어떻게 돼요?\n남자: 가산세가 붙어서 더 많이 내야 해요.",
                        q("신고를 늦게 했을 때의 결과로 알맞은 것을 고르십시오.", List.of(
                                opt("가산세가 붙어 더 많이 낸다.", "정답: '가산세가 붙어서 더 많이 내야 한다'고 답했습니다."),
                                opt("세금이 면제된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("세금이 줄어든다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("아무 문제가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 불이익 내용을 반대로 착각하게 합니다.",
                                "[세금 마인드맵] 늦은 신고 → 결과(가산세). 원인과 결과를 화살표로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "보험 가입",
                        "남자: 보험 가입 전에 건강검진을 받아야 하나요?\n여자: 일부 상품은 필요하지만, 이 상품은 필요 없어요.",
                        q("이 보험 상품의 가입 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("건강검진이 필요 없다.", "정답: '이 상품은 필요 없다'고 답했습니다."),
                                opt("건강검진이 반드시 필요하다.", "🔴 빨강 - 이 상품에 대한 답과 반대되는 오답입니다."),
                                opt("나이 제한이 있다.", "🔴 빨강 - 언급되지 않은 조건입니다."),
                                opt("소득 증명이 필요하다.", "🔴 빨강 - 언급되지 않은 조건입니다.")
                        ), 0, "🔴 '일부 상품'과 '이 상품'의 조건을 혼동하게 합니다.",
                                "[보험 마인드맵] 일부 상품(필요) ↔ 이 상품(불필요). 대상을 정확히 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "연금 안내",
                        "여자: 연금을 일찍 받으면 금액이 줄어드나요?\n남자: 네, 조기 수령하면 매달 받는 금액이 줄어들어요.",
                        q("연금을 조기 수령할 때의 결과로 알맞은 것을 고르십시오.", List.of(
                                opt("매달 받는 금액이 줄어든다.", "정답: '조기 수령하면 금액이 줄어든다'고 답했습니다."),
                                opt("매달 받는 금액이 늘어난다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("금액은 변하지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("한 번에 전액을 받는다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 증가/감소를 뒤바꾸거나 변화 없음으로 왜곡합니다.",
                                "[연금 마인드맵] 조기 수령 → 결과(금액 감소). 원인과 결과를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "저축 상담",
                        "남자: 적금 중도 해지하면 손해예요?\n여자: 네, 약속된 기간을 채우지 않으면 이자가 크게 줄어요.",
                        q("적금을 중도 해지했을 때의 결과로 알맞은 것을 고르십시오.", List.of(
                                opt("이자가 크게 줄어든다.", "정답: '이자가 크게 줄어든다'고 답했습니다."),
                                opt("이자가 늘어난다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("원금도 못 받는다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("아무 손해가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 이자 증감을 뒤바꾸거나 손해가 없다고 왜곡합니다.",
                                "[저축 마인드맵] 중도 해지 → 결과(이자 감소). 조건문에 밑줄을 그으세요.")),
                onePassage(PassageCategory.LISTENING, "카드 발급",
                        "여자: 카드 분실하면 어떻게 해야 해요?\n남자: 바로 카드사에 전화해서 분실 신고부터 하세요.",
                        q("카드를 분실했을 때 가장 먼저 해야 할 일로 알맞은 것을 고르십시오.", List.of(
                                opt("카드사에 분실 신고하기", "정답: '바로 카드사에 전화해서 분실 신고부터 하라'고 답했습니다."),
                                opt("경찰서에 신고하기", "🔴 빨강 - 언급되지 않은 순서입니다."),
                                opt("새 카드를 신청하기", "🔴 빨강 - 언급되지 않은 순서입니다."),
                                opt("아무것도 하지 않아도 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 '먼저/바로'라는 순서 표현을 놓치고 다른 행동을 정답처럼 만듭니다.",
                                "[카드 마인드맵] 상황(분실) → 첫 조치(분실 신고). '바로'라는 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "세금 신고",
                        "남자: 환급금은 언제 들어와요?\n여자: 신고 후 보통 한 달 이내에 계좌로 입금돼요.",
                        q("환급금이 입금되는 데 걸리는 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("한 달 이내", "정답: '한 달 이내에 계좌로 입금된다'고 답했습니다."),
                                opt("일주일 이내", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("1년 이내", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("환급이 안 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기간 숫자를 바꾸거나 환급 자체를 부정하는 오답을 만듭니다.",
                                "[세금 마인드맵] 신고 후 → 입금(한 달 이내). 기간 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "보험 가입",
                        "여자: 보험금 청구는 어떻게 해요?\n남자: 진단서랑 영수증을 준비해서 앱으로 제출하면 돼요.",
                        q("보험금 청구에 필요한 서류로 알맞은 것을 고르십시오.", List.of(
                                opt("진단서와 영수증", "정답: '진단서랑 영수증을 준비하라'고 답했습니다."),
                                opt("주민등록등본", "🔵 파랑 - 언급되지 않은 서류입니다."),
                                opt("가족관계증명서", "🔵 파랑 - 언급되지 않은 서류입니다."),
                                opt("서류가 필요 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 서류와 무관한 다른 서류로 오답을 만듭니다.",
                                "[보험 마인드맵] 서류(진단서+영수증) + 방법(앱 제출). 서류명을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "연금 안내",
                        "남자: 연금 예상 수령액은 어디서 확인해요?\n여자: 국민연금공단 홈페이지에서 조회할 수 있어요.",
                        q("연금 예상 수령액을 확인하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("국민연금공단 홈페이지에서 조회", "정답: '국민연금공단 홈페이지에서 조회할 수 있다'고 답했습니다."),
                                opt("은행에 방문", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("세무서에 문의", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("직접 계산해야 한다.", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[연금 마인드맵] 확인 방법 = 국민연금공단 홈페이지. 기관명을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "저축 상담",
                        "여자: 목돈이 생기면 예금이 나을까요, 적금이 나을까요?\n남자: 목돈은 한 번에 넣는 예금이 더 적합해요.",
                        q("남자가 목돈에 추천한 상품으로 알맞은 것을 고르십시오.", List.of(
                                opt("예금", "정답: '예금이 더 적합하다'고 답했습니다."),
                                opt("적금", "🔴 빨강 - 비교 대상을 뒤바꾼 오답입니다."),
                                opt("펀드", "🔴 빨강 - 언급되지 않은 상품입니다."),
                                opt("둘 다 추천하지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 예금/적금 비교 대상을 뒤바꿔 오답을 만듭니다.",
                                "[저축 마인드맵] 목돈(한 번에) → 추천(예금). 상황과 추천을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "카드 발급",
                        "남자: 카드 연회비가 있나요?\n여자: 이 카드는 연회비가 없는 무료 카드예요.",
                        q("이 카드의 연회비로 알맞은 것을 고르십시오.", List.of(
                                opt("연회비가 없다.", "정답: '연회비가 없는 무료 카드'라고 답했습니다."),
                                opt("연회비가 있다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("연회비는 5만 원이다.", "🔴 빨강 - 대화에 없는 숫자입니다."),
                                opt("연회비는 매달 낸다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 유무를 반대로 착각하게 하거나 대화에 없는 숫자로 오답을 만듭니다.",
                                "[카드 마인드맵] 질문(연회비 유무) → 답(없음, 무료). 핵심 단어를 확인하세요."))
        );

        List<PassageSeed> reading3rd21to30 = List.of(
                onePassage(PassageCategory.READING, "세금 고지서",
                        "[세금 고지서] 재산세 납부 기한: 7월 31일. 납부하지 않을 경우 가산세가 부과됩니다.",
                        q("이 고지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("납부하지 않으면 가산세가 붙는다.", "정답: 고지서에 그대로 명시되어 있습니다."),
                                opt("늦게 내도 불이익이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("납부 기한은 8월 31일이다.", "🔵 파랑 - 날짜를 바꾼 오답입니다."),
                                opt("이 세금은 자동차세다.", "🔵 파랑 - '재산세'를 '자동차세'로 바꾼 오답입니다.")
                        ), 0, "🔵 불이익 여부나 날짜, 세금 종류를 바꿔 오답을 만듭니다.",
                                "[고지서 마인드맵] 종류(재산세) + 기한(7/31) + 불이행 시(가산세). 색칠하세요.")),
                onePassage(PassageCategory.READING, "보험 약관 요약",
                        "[보험 약관 요약] 이 상품은 가입 후 90일이 지나야 보장이 시작되는 면책기간이 있습니다.",
                        q("이 약관의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("가입 후 90일이 지나야 보장된다.", "정답: 약관에 그대로 명시되어 있습니다."),
                                opt("가입 즉시 보장이 시작된다.", "🔵 파랑 - 명시된 면책기간과 반대되는 오답입니다."),
                                opt("면책기간은 30일이다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다."),
                                opt("이 상품은 면책기간이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 면책기간 유무나 기간 숫자를 바꿔 오답을 만듭니다.",
                                "[약관 마인드맵] 가입 → 면책기간(90일) → 보장 시작. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "연금 안내서",
                        "[연금 안내서] 국민연금 보험료는 소득의 9%이며, 이 중 절반은 회사가 부담합니다.",
                        q("이 안내서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("보험료의 절반은 회사가 부담한다.", "정답: 안내서에 그대로 명시되어 있습니다."),
                                opt("보험료 전액을 개인이 부담한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("보험료는 소득의 20%다.", "🔵 파랑 - 비율 숫자를 바꾼 오답입니다."),
                                opt("회사는 보험료를 부담하지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 부담 주체나 비율 숫자를 바꿔 오답을 만듭니다.",
                                "[연금 마인드맵] 전체(9%) = 개인(절반) + 회사(절반). 비율을 색으로 나누세요.")),
                onePassage(PassageCategory.READING, "저축 상품 안내",
                        "[저축 상품 안내] 이 적금은 1년 만기 상품이며, 매달 최대 50만 원까지 납입할 수 있습니다.",
                        q("이 상품의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("매달 최대 50만 원까지 넣을 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("납입 한도가 없다.", "🔵 파랑 - 명시된 한도와 반대되는 오답입니다."),
                                opt("만기는 3년이다.", "🔵 파랑 - '1년'을 '3년'으로 바꾼 오답입니다."),
                                opt("매달 최대 100만 원까지 가능하다.", "🔵 파랑 - 한도 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 납입 한도 숫자나 만기 기간을 바꿔 오답을 만듭니다.",
                                "[저축 마인드맵] 만기(1년) + 한도(월 50만 원). 두 숫자를 색칠하세요.")),
                onePassage(PassageCategory.READING, "신용카드 안내문",
                        "[신용카드 안내문] 이 카드는 전월 실적 30만 원 이상 시 할인 혜택이 적용됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("전월 실적 30만 원 이상이어야 할인된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("실적과 상관없이 할인된다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("전월 실적 10만 원이면 충분하다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("이번 달 실적을 기준으로 한다.", "🔵 파랑 - '전월'을 '이번 달'로 바꾼 오답입니다.")
                        ), 0, "🔵 실적 조건 숫자나 기준 시점(전월/이번 달)을 바꿔 오답을 만듭니다.",
                                "[카드 마인드맵] 조건(전월 실적 30만 원) → 혜택(할인). 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "세금 고지서",
                        "[세금 고지서] 자동차세는 1월과 7월, 두 차례에 걸쳐 나누어 납부할 수 있습니다.",
                        q("이 고지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("자동차세는 두 차례 나눠 낼 수 있다.", "정답: 고지서에 그대로 명시되어 있습니다."),
                                opt("자동차세는 한 번에만 낼 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("납부 시기는 3월과 9월이다.", "🔵 파랑 - 월을 바꾼 오답입니다."),
                                opt("자동차세는 재산세다.", "🔵 파랑 - 세금 종류를 혼동하게 하는 오답입니다.")
                        ), 0, "🔵 분할 납부 가능 여부나 시기, 세금 종류를 바꿔 오답을 만듭니다.",
                                "[고지서 마인드맵] 종류(자동차세) + 시기(1월, 7월). 두 시점을 색칠하세요.")),
                onePassage(PassageCategory.READING, "보험 약관 요약",
                        "[보험 약관 요약] 만기 환급형 상품으로, 만기까지 유지 시 납입한 보험료의 100%를 돌려받습니다.",
                        q("이 약관의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("만기까지 유지하면 보험료 100%를 돌려받는다.", "정답: 약관에 그대로 명시되어 있습니다."),
                                opt("만기 환급은 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("50%만 돌려받는다.", "🔵 파랑 - 비율 숫자를 바꾼 오답입니다."),
                                opt("중도 해지해도 100% 환급된다.", "🔵 파랑 - '만기까지 유지'라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 환급 비율이나 조건(만기 유지)을 바꿔 오답을 만듭니다.",
                                "[약관 마인드맵] 조건(만기 유지) → 결과(100% 환급). 조건 문장에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "연금 안내서",
                        "[연금 안내서] 연금 수령 방식은 일시금과 연금형 중 선택할 수 있으며, 한 번 선택하면 변경이 어렵습니다.",
                        q("이 안내서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("한 번 선택하면 변경이 어렵다.", "정답: 안내서에 그대로 명시되어 있습니다."),
                                opt("언제든 자유롭게 변경할 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("수령 방식은 한 가지뿐이다.", "🔵 파랑 - '일시금과 연금형' 두 가지와 반대됩니다."),
                                opt("수령 방식을 선택할 필요가 없다.", "🔵 파랑 - '선택할 수 있다'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 변경 가능 여부나 선택지 개수를 바꿔 오답을 만듭니다.",
                                "[연금 마인드맵] 선택지(일시금, 연금형) + 주의(변경 어려움). 색칠하세요.")),
                onePassage(PassageCategory.READING, "저축 상품 안내",
                        "[저축 상품 안내] 이 예금은 우대금리 조건으로 급여 이체 시 추가 금리 0.3%가 제공됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("급여 이체 시 추가 금리를 받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("조건 없이 추가 금리를 받는다.", "🔵 파랑 - '급여 이체 시'라는 조건을 빼고 읽게 합니다."),
                                opt("추가 금리는 1%다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("우대금리 조건이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건을 빼고 읽거나 우대금리 숫자를 바꿔 오답을 만듭니다.",
                                "[저축 마인드맵] 조건(급여 이체) → 혜택(+0.3%). 조건과 숫자를 색칠하세요.")),
                onePassage(PassageCategory.READING, "신용카드 안내문",
                        "[신용카드 안내문] 해외 결제 시 수수료가 면제되는 카드로, 여행객에게 특히 유용합니다.",
                        q("이 카드의 특징으로 알맞은 것을 고르십시오.", List.of(
                                opt("해외 결제 수수료가 면제된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("해외 결제 수수료가 더 비싸다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("국내 결제만 가능하다.", "🔵 파랑 - '해외 결제'라는 특징과 반대되는 오답입니다."),
                                opt("여행객에게 불리한 카드다.", "🔵 파랑 - '특히 유용하다'와 반대되는 오답입니다.")
                        ), 0, "🔵 수수료 면제 여부나 유용성 평가를 반대로 착각하게 합니다.",
                                "[카드 마인드맵] 특징(해외 수수료 면제) + 대상(여행객). 색으로 구분하세요."))
        );

        List<PassageSeed> reading3rd31to40 = List.of(
                onePassage(PassageCategory.READING, "세금 고지서",
                        "[세금 고지서] 지방세 납부는 위택스 홈페이지 또는 은행 창구에서 가능합니다.",
                        q("이 고지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("위택스나 은행에서 납부할 수 있다.", "정답: 고지서에 그대로 명시되어 있습니다."),
                                opt("납부는 위택스에서만 가능하다.", "🔵 파랑 - '은행 창구'라는 방법을 빼고 읽게 합니다."),
                                opt("납부는 은행에서만 가능하다.", "🔵 파랑 - '위택스'라는 방법을 빼고 읽게 합니다."),
                                opt("납부 방법이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 두 가지 납부 방법 중 하나만 인정하는 오답을 만듭니다.",
                                "[고지서 마인드맵] 방법1(위택스) + 방법2(은행). 두 방법 모두 정답에 포함됩니다.")),
                onePassage(PassageCategory.READING, "보험 약관 요약",
                        "[보험 약관 요약] 갱신형 상품으로, 5년마다 보험료가 재산정되어 변경될 수 있습니다.",
                        q("이 약관의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("5년마다 보험료가 변경될 수 있다.", "정답: 약관에 그대로 명시되어 있습니다."),
                                opt("보험료는 평생 고정된다.", "🔵 파랑 - '갱신형'과 반대되는 오답입니다."),
                                opt("1년마다 재산정된다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다."),
                                opt("보험료는 절대 오르지 않는다.", "🔵 파랑 - 재산정 가능성과 반대되는 오답입니다.")
                        ), 0, "🔵 갱신 주기 숫자를 바꾸거나 고정형으로 착각하게 합니다.",
                                "[약관 마인드맵] 유형(갱신형) + 주기(5년). 재산정 가능성에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "연금 안내서",
                        "[연금 안내서] 퇴직연금은 회사가 매년 근로자의 연간 임금 총액의 일부를 적립하는 방식입니다.",
                        q("이 안내서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("회사가 매년 적립한다.", "정답: 안내서에 그대로 명시되어 있습니다."),
                                opt("근로자가 직접 적립해야 한다.", "🔵 파랑 - '회사가 적립'과 반대되는 오답입니다."),
                                opt("적립은 한 번만 이루어진다.", "🔵 파랑 - '매년'이라는 빈도와 반대되는 오답입니다."),
                                opt("퇴직연금은 국가가 지급한다.", "🔵 파랑 - 명시된 주체(회사)와 다른 오답입니다.")
                        ), 0, "🔵 적립 주체나 빈도를 반대로 착각하게 합니다.",
                                "[연금 마인드맵] 주체(회사) + 빈도(매년) + 대상(퇴직연금). 색칠하세요.")),
                onePassage(PassageCategory.READING, "저축 상품 안내",
                        "[저축 상품 안내] 자유적립식 적금으로, 매달 금액을 다르게 넣어도 상관없습니다.",
                        q("이 상품의 특징으로 알맞은 것을 고르십시오.", List.of(
                                opt("매달 다른 금액을 넣을 수 있다.", "정답: '금액을 다르게 넣어도 상관없다'고 명시했습니다."),
                                opt("매달 같은 금액만 넣어야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("한 번만 납입 가능하다.", "🔵 파랑 - '자유적립식'과 반대되는 오답입니다."),
                                opt("납입이 의무 사항이 아니다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 정액/자유 적립 방식을 반대로 착각하게 합니다.",
                                "[저축 마인드맵] 유형(자유적립식) + 특징(금액 자유). 핵심 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "신용카드 안내문",
                        "[신용카드 안내문] 카드 이용 한도는 심사를 거쳐 개인별로 다르게 부여됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이용 한도는 개인마다 다르다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("이용 한도는 모두 동일하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("심사 없이 한도가 정해진다.", "🔵 파랑 - '심사를 거쳐'라는 내용과 반대되는 오답입니다."),
                                opt("한도는 무제한이다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 개인별 차등 여부나 심사 필요성을 반대로 착각하게 합니다.",
                                "[카드 마인드맵] 심사 → 한도(개인별 상이). 순서와 결과를 색칠하세요.")),
                onePassage(PassageCategory.READING, "세금 고지서",
                        "[세금 고지서] 이의 신청은 고지서를 받은 날로부터 90일 이내에 해야 합니다.",
                        q("이 고지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이의 신청은 90일 이내에 해야 한다.", "정답: 고지서에 그대로 명시되어 있습니다."),
                                opt("이의 신청 기한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("이의 신청은 30일 이내다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("이의 신청은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 기한 숫자를 바꾸거나 신청 가능 여부를 반대로 착각하게 합니다.",
                                "[고지서 마인드맵] 시작(고지서 수령) + 기한(90일 이내). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "보험 약관 요약",
                        "[보험 약관 요약] 계약 후 15일 이내에는 청약을 철회하고 전액 환불받을 수 있습니다.",
                        q("이 약관의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("15일 이내에는 전액 환불받을 수 있다.", "정답: 약관에 그대로 명시되어 있습니다."),
                                opt("환불은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("30일 이내에 환불받을 수 있다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다."),
                                opt("일부만 환불받을 수 있다.", "🔵 파랑 - '전액'과 반대되는 오답입니다.")
                        ), 0, "🔵 철회 가능 기간이나 환불 비율을 바꿔 오답을 만듭니다.",
                                "[약관 마인드맵] 기한(15일 이내) + 환불(전액). 숫자와 비율을 색칠하세요.")),
                onePassage(PassageCategory.READING, "연금 안내서",
                        "[연금 안내서] 유족연금은 가입자가 사망했을 때 남은 가족에게 지급되는 연금입니다.",
                        q("유족연금이 지급되는 경우로 알맞은 것을 고르십시오.", List.of(
                                opt("가입자가 사망했을 때", "정답: 안내서에 그대로 명시되어 있습니다."),
                                opt("가입자가 은퇴했을 때", "🔵 파랑 - '노령연금'과 혼동하게 하는 오답입니다."),
                                opt("가입자가 이직했을 때", "🔵 파랑 - 언급되지 않은 경우입니다."),
                                opt("가입자가 결혼했을 때", "🔵 파랑 - 언급되지 않은 경우입니다.")
                        ), 0, "🔵 연금 종류별 지급 사유를 혼동하게 합니다.",
                                "[연금 마인드맵] 사유(사망) → 지급 대상(유족). 사유 단어를 확인하세요.")),
                onePassage(PassageCategory.READING, "저축 상품 안내",
                        "[저축 상품 안내] 청년 우대형 적금으로, 만 19세에서 34세까지 가입할 수 있습니다.",
                        q("이 상품의 가입 가능 연령으로 알맞은 것을 고르십시오.", List.of(
                                opt("만 19세에서 34세까지", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("만 14세에서 19세까지", "🔵 파랑 - 연령대를 바꾼 오답입니다."),
                                opt("연령 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("만 35세 이상만 가입 가능하다.", "🔵 파랑 - 연령 조건을 반대로 착각하게 합니다.")
                        ), 0, "🔵 연령 범위를 바꾸거나 제한이 없다고 왜곡합니다.",
                                "[저축 마인드맵] 대상(청년) + 연령(19~34세). 숫자 범위를 색칠하세요.")),
                onePassage(PassageCategory.READING, "신용카드 안내문",
                        "[신용카드 안내문] 이 카드는 분실 시 즉시 신고하면 부정 사용 피해액을 전액 보상해 드립니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("즉시 신고하면 피해액을 전액 보상받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("신고해도 보상받지 못한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("보상은 50%만 이루어진다.", "🔵 파랑 - '전액'을 '50%'로 바꾼 오답입니다."),
                                opt("신고하지 않아도 보상받는다.", "🔵 파랑 - '즉시 신고'라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 보상 비율이나 신고 조건을 바꿔 오답을 만듭니다.",
                                "[카드 마인드맵] 조건(즉시 신고) → 보상(전액). 조건 문장에 밑줄을 그으세요."))
        );

        List<PassageSeed> listening4th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "반려식물 키우기",
                        "여자: 이 화분 물은 얼마나 자주 줘야 해요?\n남자: 일주일에 한 번 정도면 충분해요.",
                        q("화분에 물을 주는 주기로 알맞은 것을 고르십시오.", List.of(
                                opt("일주일에 한 번", "정답: '일주일에 한 번 정도면 충분하다'고 답했습니다."),
                                opt("하루에 한 번", "🔴 빨강 - 빈도를 바꾼 오답입니다."),
                                opt("한 달에 한 번", "🔴 빨강 - 빈도를 바꾼 오답입니다."),
                                opt("물을 주지 않아도 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 빈도 숫자나 단위를 바꿔 오답을 만듭니다.",
                                "[반려식물 마인드맵] 질문(물 주기) → 답(일주일에 한 번). 빈도 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "인테리어",
                        "남자: 거실 벽지를 바꾸고 싶은데 어떤 색이 좋을까요?\n여자: 밝은 아이보리색이 공간을 넓어 보이게 해요.",
                        q("여자가 추천한 벽지 색으로 알맞은 것을 고르십시오.", List.of(
                                opt("밝은 아이보리색", "정답: '밝은 아이보리색이 좋다'고 추천했습니다."),
                                opt("어두운 회색", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("진한 갈색", "🟢 초록 - 언급되지 않은 색입니다."),
                                opt("검정색", "🟢 초록 - 언급되지 않은 색입니다.")
                        ), 0, "🟢 실제 추천된 색과 반대되거나 무관한 색으로 오답을 만듭니다.",
                                "[인테리어 마인드맵] 목적(넓어 보임) + 색(아이보리). 색상명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "가전제품 구매",
                        "여자: 냉장고 용량은 얼마나 되는 걸로 사야 할까요?\n남자: 4인 가족이면 800리터 정도가 적당해요.",
                        q("4인 가족에게 적당한 냉장고 용량으로 알맞은 것을 고르십시오.", List.of(
                                opt("800리터", "정답: '4인 가족이면 800리터 정도가 적당하다'고 답했습니다."),
                                opt("400리터", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("1500리터", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("용량은 상관없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 용량 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[가전 마인드맵] 조건(4인 가족) + 용량(800리터). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중고거래",
                        "남자: 이 자전거 얼마에 파실 거예요?\n여자: 원래 20만 원인데 상태 봐서 15만 원에 드릴게요.",
                        q("자전거의 판매 가격으로 알맞은 것을 고르십시오.", List.of(
                                opt("15만 원", "정답: '15만 원에 드릴게요'라고 답했습니다."),
                                opt("20만 원", "🔴 빨강 - 원래 가격과 헷갈리게 하는 오답입니다."),
                                opt("10만 원", "🔴 빨강 - 대화에 없는 금액입니다."),
                                opt("무료로 준다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 원래 가격과 실제 판매 가격을 혼동하게 합니다.",
                                "[중고거래 마인드맵] 원래(20만) → 화살표 → 판매가(15만). 화살표 뒤가 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "재활용",
                        "여자: 이 페트병은 어떻게 버려야 해요?\n남자: 라벨 떼고 물로 헹군 다음 압축해서 버리면 돼요.",
                        q("남자가 안내한 페트병 배출 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("라벨 제거 → 헹구기 → 압축", "정답: 남자의 설명 순서 그대로입니다."),
                                opt("압축 → 라벨 제거 → 헹구기", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("헹구지 않고 바로 버린다.", "🔴 빨강 - '헹군다'는 과정을 빠뜨린 오답입니다."),
                                opt("라벨은 떼지 않아도 된다.", "🔴 빨강 - '라벨 떼고'라는 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 순서를 뒤바꾸거나 과정 하나를 빠뜨려 오답을 만듭니다.",
                                "[재활용 마인드맵] 1(라벨 제거) → 2(헹굼) → 3(압축). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "반려식물 키우기",
                        "남자: 이 식물은 햇빛이 얼마나 필요해요?\n여자: 직사광선보다는 밝은 그늘이 좋아요.",
                        q("여자가 추천한 햇빛 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("밝은 그늘", "정답: '밝은 그늘이 좋다'고 답했습니다."),
                                opt("직사광선", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("완전히 어두운 곳", "🔴 빨강 - 언급되지 않은 조건입니다."),
                                opt("햇빛이 전혀 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 추천 조건과 반대되는 다른 조건으로 오답을 만듭니다.",
                                "[반려식물 마인드맵] 비교(직사광선 vs 밝은 그늘). 추천된 조건에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "인테리어",
                        "여자: 이 조명 설치하는 데 얼마나 걸려요?\n남자: 전문가가 하면 30분 정도면 끝나요.",
                        q("조명 설치에 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("30분", "정답: '30분 정도면 끝난다'고 답했습니다."),
                                opt("10분", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("3시간", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("하루 종일", "🔴 빨강 - 대화 내용과 반대되는 과장된 오답입니다.")
                        ), 0, "🔴 시간 숫자를 슬쩍 바꾸거나 과장해 오답을 만듭니다.",
                                "[인테리어 마인드맵] 조건(전문가) + 시간(30분). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "가전제품 구매",
                        "남자: 이 세탁기 에너지 등급이 어떻게 돼요?\n여자: 1등급이라 전기세가 절약돼요.",
                        q("이 세탁기의 에너지 등급으로 알맞은 것을 고르십시오.", List.of(
                                opt("1등급", "정답: '1등급이라 전기세가 절약된다'고 답했습니다."),
                                opt("3등급", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("5등급", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("등급이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 등급 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[가전 마인드맵] 등급(1등급) → 효과(전기세 절약). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중고거래",
                        "여자: 이 책상 직거래만 가능해요?\n남자: 아니요, 택배도 가능해요. 다만 배송비는 구매자 부담이에요.",
                        q("택배 배송비 부담에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("구매자가 배송비를 부담한다.", "정답: '배송비는 구매자 부담'이라고 명시했습니다."),
                                opt("판매자가 배송비를 부담한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("배송비는 무료다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("택배 거래는 불가능하다.", "🔵 파랑 - '택배도 가능하다'와 반대되는 오답입니다.")
                        ), 0, "🔵 부담 주체를 뒤바꾸거나 택배 거래 가능 여부를 반대로 착각하게 합니다.",
                                "[중고거래 마인드맵] 방법(직거래+택배) + 배송비(구매자 부담). 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "재활용",
                        "남자: 헌 옷은 어디에 버려요?\n여자: 아파트 단지 안에 있는 의류 수거함에 넣으면 돼요.",
                        q("헌 옷을 버리는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("의류 수거함", "정답: '의류 수거함에 넣으면 된다'고 답했습니다."),
                                opt("일반 쓰레기통", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("재활용 센터", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("아무 데나 버려도 된다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 장소로 오답을 만듭니다.",
                                "[재활용 마인드맵] 대상(헌 옷) + 장소(의류 수거함). 장소명에 형광펜을 칠하세요."))
        );

        List<PassageSeed> listening4th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "반려식물 키우기",
                        "여자: 화분 분갈이는 언제 해야 해요?\n남자: 보통 2년에 한 번 정도 해 주는 게 좋아요.",
                        q("화분 분갈이 주기로 알맞은 것을 고르십시오.", List.of(
                                opt("2년에 한 번", "정답: '2년에 한 번 정도 해 주는 게 좋다'고 답했습니다."),
                                opt("한 달에 한 번", "🔴 빨강 - 주기를 바꾼 오답입니다."),
                                opt("매년", "🔴 빨강 - 주기를 바꾼 오답입니다."),
                                opt("분갈이는 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 주기 숫자나 단위를 바꿔 오답을 만듭니다.",
                                "[반려식물 마인드맵] 작업(분갈이) + 주기(2년). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "인테리어",
                        "남자: 커튼이랑 블라인드 중에 뭐가 나을까요?\n여자: 채광 조절이 편한 블라인드를 추천해요.",
                        q("여자가 추천한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("블라인드", "정답: '블라인드를 추천한다'고 답했습니다."),
                                opt("커튼", "🔴 빨강 - 비교 대상을 뒤바꾼 오답입니다."),
                                opt("롤스크린", "🔴 빨강 - 언급되지 않은 제품입니다."),
                                opt("둘 다 추천하지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 비교 대상(커튼/블라인드)을 뒤바꿔 오답을 만듭니다.",
                                "[인테리어 마인드맵] 커튼 ↔ 블라인드(정답). 이유 뒤에 나온 선택이 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "가전제품 구매",
                        "여자: 이 청소기 무선인가요?\n남자: 네, 무선이라 이동이 편해요. 배터리는 한 번 충전에 40분 사용 가능해요.",
                        q("이 청소기의 배터리 사용 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("40분", "정답: '한 번 충전에 40분 사용 가능하다'고 답했습니다."),
                                opt("10분", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("2시간", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("충전이 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 사용 시간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[가전 마인드맵] 유형(무선) + 사용 시간(40분). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중고거래",
                        "남자: 이 물건 아직 판매 중이에요?\n여자: 아니요, 어제 다른 분한테 팔렸어요.",
                        q("이 물건의 판매 상태로 알맞은 것을 고르십시오.", List.of(
                                opt("이미 판매됐다.", "정답: '어제 다른 분한테 팔렸다'고 답했습니다."),
                                opt("아직 판매 중이다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("판매를 취소했다.", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("가격을 내렸다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 이미 완료된 거래를 진행 중으로 착각하게 합니다.",
                                "[중고거래 마인드맵] 질문(판매 여부) → 답(이미 팔림). '어제'라는 시점 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "재활용",
                        "여자: 유리병도 재활용되나요?\n남자: 네, 뚜껑만 분리해서 유리 수거함에 넣으면 돼요.",
                        q("유리병을 재활용하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("뚜껑 분리 후 유리 수거함에 넣기", "정답: 대화에 그대로 나와 있습니다."),
                                opt("뚜껑째로 그냥 버리기", "🔴 빨강 - '뚜껑만 분리'라는 내용과 반대되는 오답입니다."),
                                opt("일반 쓰레기로 버리기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("재활용이 안 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 분리 과정을 빠뜨리거나 일반 쓰레기로 착각하게 합니다.",
                                "[재활용 마인드맵] 과정(뚜껑 분리) → 배출(유리 수거함). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "반려식물 키우기",
                        "남자: 잎이 노랗게 변했는데 왜 그럴까요?\n여자: 물을 너무 많이 줘서 그런 것 같아요.",
                        q("여자가 말한 잎이 노랗게 변한 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("물을 너무 많이 줘서", "정답: '물을 너무 많이 줘서 그런 것 같다'고 답했습니다."),
                                opt("물을 너무 적게 줘서", "🔴 빨강 - '많이'를 '적게'로 바꾼 오답입니다."),
                                opt("햇빛이 부족해서", "🔴 빨강 - 언급되지 않은 이유입니다."),
                                opt("온도가 낮아서", "🔴 빨강 - 언급되지 않은 이유입니다.")
                        ), 0, "🔴 많음/적음을 뒤바꾸거나 무관한 다른 이유로 오답을 만듭니다.",
                                "[반려식물 마인드맵] 증상(잎 노랗게 변함) ← 원인(과다 급수). 원인 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "인테리어",
                        "여자: 원룸이 좁아 보이는데 어떻게 하면 넓어 보일까요?\n남자: 큰 가구보다는 작은 가구를 여러 개 배치하는 게 좋아요.",
                        q("남자가 추천한 가구 배치 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("작은 가구를 여러 개 배치하기", "정답: 대화에 그대로 나와 있습니다."),
                                opt("큰 가구 하나만 놓기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("가구를 아예 두지 않기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("가구 색을 어둡게 하기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 반대되거나 무관한 방법으로 오답을 만듭니다.",
                                "[인테리어 마인드맵] 목표(넓어 보임) → 방법(작은 가구 여러 개). 방법을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "가전제품 구매",
                        "남자: 이 에어컨 설치비는 따로 내야 하나요?\n여자: 네, 기본 설치비는 5만 원이고 추가 자재비는 별도예요.",
                        q("에어컨의 기본 설치비로 알맞은 것을 고르십시오.", List.of(
                                opt("5만 원", "정답: '기본 설치비는 5만 원'이라고 답했습니다."),
                                opt("무료다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("10만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("추가 자재비만 낸다.", "🔴 빨강 - 기본 설치비를 빼고 읽게 하는 오답입니다.")
                        ), 0, "🔴 설치비 숫자를 바꾸거나 무료로 착각하게 합니다.",
                                "[가전 마인드맵] 기본(5만 원) + 추가 자재비(별도). 두 항목을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "중고거래",
                        "여자: 이 가격에서 더 깎아 주실 수 있어요?\n남자: 죄송한데 이미 최저가라 더는 어려워요.",
                        q("남자의 대답으로 알맞은 것을 고르십시오.", List.of(
                                opt("가격을 더 깎아 줄 수 없다.", "정답: '더는 어려워요'라고 거절했습니다."),
                                opt("가격을 더 깎아 준다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("무료로 준다.", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("가격을 올린다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 완곡한 거절 표현을 수락으로 착각하게 합니다.",
                                "[중고거래 마인드맵] 요청(할인) → 거절(최저가). '이미 최저가'라는 이유를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "재활용",
                        "남자: 폐건전지는 어떻게 버려요?\n여자: 일반 쓰레기랑 섞지 말고 전용 수거함에 따로 버려야 해요.",
                        q("폐건전지를 버리는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("전용 수거함에 따로 버리기", "정답: '전용 수거함에 따로 버려야 한다'고 답했습니다."),
                                opt("일반 쓰레기와 함께 버리기", "🔴 빨강 - '섞지 말라'는 내용과 반대되는 오답입니다."),
                                opt("재활용 수거함에 버리기", "🔴 빨강 - '전용 수거함'과 다른 오답입니다."),
                                opt("아무 곳에나 버려도 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 일반 쓰레기와 혼동하게 하거나 배출 장소를 바꿔 오답을 만듭니다.",
                                "[재활용 마인드맵] 대상(폐건전지) + 장소(전용 수거함). 장소명을 정확히 확인하세요."))
        );

        List<PassageSeed> reading4th21to30 = List.of(
                onePassage(PassageCategory.READING, "반려식물 관리법",
                        "[반려식물 관리법] 다육식물은 물을 자주 주면 뿌리가 썩을 수 있으니 흙이 완전히 마른 후에 물을 주세요.",
                        q("이 관리법의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("흙이 마른 후에 물을 줘야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("물을 자주 줘야 잘 자란다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("흙이 젖어 있을 때 물을 준다.", "🔵 파랑 - '완전히 마른 후'와 반대되는 오답입니다."),
                                opt("물을 아예 주지 않아도 된다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 급수 빈도나 시점(마른 후/젖었을 때)을 반대로 착각하게 합니다.",
                                "[관리법 마인드맵] 식물(다육) + 조건(흙 마른 후 급수). 조건 문장에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "인테리어 후기",
                        "[인테리어 후기] 셀프로 벽지를 시공했는데 생각보다 어렵지 않았어요. 다만 접착제가 마르는 데 하루 정도 걸렸어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("접착제가 마르는 데 하루 정도 걸렸다.", "정답: 후기에 그대로 명시되어 있습니다."),
                                opt("접착제가 즉시 말랐다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("셀프 시공이 매우 어려웠다.", "🔵 파랑 - '생각보다 어렵지 않았다'와 반대되는 오답입니다."),
                                opt("전문가에게 맡겼다.", "🔵 파랑 - '셀프로 시공했다'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 건조 시간이나 난이도 평가를 반대로 착각하게 합니다.",
                                "[후기 마인드맵] 방법(셀프) + 소요 시간(하루). 시간 정보에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "가전제품 사용설명서",
                        "[사용설명서] 이 제품은 사용 전 반드시 필터를 물로 헹군 후 완전히 말려서 장착해야 합니다.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("필터는 헹군 후 말려서 장착해야 한다.", "정답: 설명서에 그대로 명시되어 있습니다."),
                                opt("필터는 젖은 상태로 장착해도 된다.", "🔵 파랑 - '완전히 말려서'와 반대되는 오답입니다."),
                                opt("필터를 헹굴 필요가 없다.", "🔵 파랑 - 명시된 절차와 반대되는 오답입니다."),
                                opt("필터 없이 사용해도 된다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 건조 필요 여부나 필터 세척 절차를 반대로 착각하게 합니다.",
                                "[설명서 마인드맵] 절차(헹구기 → 건조 → 장착). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "중고거래 게시글",
                        "[중고거래 게시글] 제목: 책상 팝니다\n내용: 상태 좋은 편이며, 직거래는 강남역에서 가능합니다. 네고 불가입니다.",
                        q("이 게시글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("가격 협상이 불가능하다.", "정답: '네고 불가'라는 표현이 가격 협상 불가를 뜻합니다."),
                                opt("가격 협상이 가능하다.", "🔵 파랑 - '네고 불가'와 반대되는 오답입니다."),
                                opt("직거래 장소는 홍대입니다.", "🔵 파랑 - '강남역'을 '홍대'로 바꾼 오답입니다."),
                                opt("택배 거래만 가능하다.", "🔵 파랑 - '직거래'라는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 '네고 불가'라는 표현의 의미를 반대로 착각하게 합니다.",
                                "[게시글 마인드맵] 조건(네고 불가) + 장소(강남역). '네고'가 협상을 뜻함을 기억하세요.")),
                onePassage(PassageCategory.READING, "분리수거 안내문",
                        "[분리수거 안내문] 스티로폼은 테이프와 스티커를 모두 제거한 후 배출해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("테이프와 스티커를 제거해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("테이프는 붙어 있어도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("스티커만 제거하면 된다.", "🔵 파랑 - '테이프와 스티커 모두'라는 내용을 놓치게 합니다."),
                                opt("스티로폼은 일반 쓰레기다.", "🔵 파랑 - 재활용 대상이라는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 제거 대상을 일부만 인정하거나 재활용 여부를 반대로 착각하게 합니다.",
                                "[분리수거 마인드맵] 대상(스티로폼) + 조건(테이프+스티커 제거). 색칠하세요.")),
                onePassage(PassageCategory.READING, "반려식물 관리법",
                        "[반려식물 관리법] 실내 식물은 잎에 먼지가 쌓이면 광합성이 어려우니 젖은 천으로 주기적으로 닦아 주세요.",
                        q("이 관리법의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("젖은 천으로 잎을 닦아야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("잎은 닦을 필요가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("마른 천으로 닦아야 한다.", "🔵 파랑 - '젖은 천'을 '마른 천'으로 바꾼 오답입니다."),
                                opt("먼지는 광합성과 무관하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 천의 상태(젖음/마름)를 바꾸거나 먼지와 광합성의 관계를 반대로 착각하게 합니다.",
                                "[관리법 마인드맵] 문제(먼지) → 방법(젖은 천 닦기). 도구 상태를 확인하세요.")),
                onePassage(PassageCategory.READING, "인테리어 후기",
                        "[인테리어 후기] 조명을 웜톤으로 바꿨더니 방 분위기가 훨씬 따뜻해졌어요. 강력 추천합니다.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("웜톤 조명으로 바꿔서 만족했다.", "정답: '방 분위기가 따뜻해져서 강력 추천한다'는 긍정 평가입니다."),
                                opt("웜톤 조명이 마음에 들지 않았다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("쿨톤 조명으로 바꿨다.", "🔵 파랑 - '웜톤'을 '쿨톤'으로 바꾼 오답입니다."),
                                opt("조명을 바꾸지 않기로 했다.", "🔵 파랑 - 이미 바꿨다는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 만족도를 반대로 착각하게 하거나 조명 톤 종류를 바꿔 오답을 만듭니다.",
                                "[후기 마인드맵] 변경(웜톤) → 결과(따뜻함, 만족). 인과관계를 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "가전제품 사용설명서",
                        "[사용설명서] 전자레인지 사용 시 금속 용기는 절대 사용하지 마시기 바랍니다.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("금속 용기는 사용하면 안 된다.", "정답: '절대 사용하지 마시기 바랍니다'라고 명시했습니다."),
                                opt("금속 용기를 사용해도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("플라스틱 용기만 금지된다.", "🔵 파랑 - '금속 용기'를 '플라스틱 용기'로 바꾼 오답입니다."),
                                opt("모든 용기 사용이 금지된다.", "🔵 파랑 - '금속 용기'라는 특정 대상을 과장한 오답입니다.")
                        ), 0, "🔵 금지 대상(금속/플라스틱)을 바꾸거나 전체 금지로 과장합니다.",
                                "[설명서 마인드맵] 금지 대상 = 금속 용기. 특정 대상만 금지됨을 확인하세요.")),
                onePassage(PassageCategory.READING, "중고거래 게시글",
                        "[중고거래 게시글] 제목: 노트북 팝니다\n내용: 사용감 있으나 정상 작동합니다. 박스와 충전기 포함입니다.",
                        q("이 게시글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("박스와 충전기가 포함되어 있다.", "정답: '박스와 충전기 포함'이라고 명시했습니다."),
                                opt("충전기는 별도 구매해야 한다.", "🔵 파랑 - '포함'이라는 내용과 반대되는 오답입니다."),
                                opt("노트북이 작동하지 않는다.", "🔵 파랑 - '정상 작동합니다'와 반대되는 오답입니다."),
                                opt("새 제품 상태다.", "🔵 파랑 - '사용감 있으나'와 반대되는 오답입니다.")
                        ), 0, "🔵 포함 여부나 작동 상태, 제품 상태(새것/중고)를 반대로 착각하게 합니다.",
                                "[게시글 마인드맵] 상태(사용감+정상작동) + 구성품(박스+충전기). 색칠하세요.")),
                onePassage(PassageCategory.READING, "분리수거 안내문",
                        "[분리수거 안내문] 종이팩과 일반 폐지는 함께 배출하지 말고 별도로 분리해서 버려 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("종이팩과 폐지는 따로 버려야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("종이팩과 폐지는 함께 버려도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("종이팩은 재활용되지 않는다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("폐지만 분리하면 된다.", "🔵 파랑 - '함께 배출하지 말라'는 내용을 왜곡한 오답입니다.")
                        ), 0, "🔵 분리 배출 여부를 반대로 착각하게 하거나 언급되지 않은 내용을 추가합니다.",
                                "[분리수거 마인드맵] 종이팩 ≠ 폐지(별도 분리). '함께 배출 금지'에 밑줄을 그으세요."))
        );

        List<PassageSeed> reading4th31to40 = List.of(
                onePassage(PassageCategory.READING, "반려식물 관리법",
                        "[반려식물 관리법] 겨울철에는 성장이 느려지므로 물 주는 횟수를 평소보다 줄이는 것이 좋습니다.",
                        q("이 관리법의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("겨울철에는 물 주는 횟수를 줄여야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("겨울철에는 물을 더 자주 줘야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("여름철 관리법이다.", "🔵 파랑 - '겨울철'을 '여름철'로 바꾼 오답입니다."),
                                opt("계절과 상관없이 똑같이 준다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 급수 횟수 증감이나 계절을 바꿔 오답을 만듭니다.",
                                "[관리법 마인드맵] 계절(겨울) + 성장(느려짐) → 급수(줄이기). 색칠하세요.")),
                onePassage(PassageCategory.READING, "인테리어 후기",
                        "[인테리어 후기] 붙박이장을 설치했더니 수납공간이 훨씬 늘어서 방이 깔끔해졌어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("수납공간이 늘어서 방이 깔끔해졌다.", "정답: 후기에 그대로 명시되어 있습니다."),
                                opt("수납공간이 줄어들었다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("방이 더 지저분해졌다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("붙박이장을 철거했다.", "🔵 파랑 - '설치했다'와 반대되는 오답입니다.")
                        ), 0, "🔵 수납공간의 증감이나 방의 상태 변화를 반대로 착각하게 합니다.",
                                "[후기 마인드맵] 설치(붙박이장) → 결과(수납 증가, 깔끔함). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "가전제품 사용설명서",
                        "[사용설명서] 제품 이상 발생 시 임의로 분해하지 마시고 반드시 서비스센터에 문의하시기 바랍니다.",
                        q("이 설명서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이상 발생 시 서비스센터에 문의해야 한다.", "정답: 설명서에 그대로 명시되어 있습니다."),
                                opt("이상 발생 시 직접 분해해도 된다.", "🔵 파랑 - '임의로 분해하지 마시고'와 반대되는 오답입니다."),
                                opt("서비스센터 문의는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("이상이 있어도 그냥 사용하면 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 직접 분해 가능 여부나 문의 필요성을 반대로 착각하게 합니다.",
                                "[설명서 마인드맵] 금지(임의 분해) + 권장(서비스센터 문의). 색칠하세요.")),
                onePassage(PassageCategory.READING, "중고거래 게시글",
                        "[중고거래 게시글] 제목: 의자 나눔합니다\n내용: 상태 좋은 편이며 무료로 드립니다. 직접 가져가실 분만 연락 주세요.",
                        q("이 게시글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("의자는 무료로 준다.", "정답: '무료로 드립니다'라고 명시했습니다."),
                                opt("의자는 유료로 판매한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("택배로 보내준다.", "🔵 파랑 - '직접 가져가실 분만'과 반대되는 오답입니다."),
                                opt("상태가 매우 나쁘다.", "🔵 파랑 - '상태 좋은 편'과 반대되는 오답입니다.")
                        ), 0, "🔵 유료/무료를 뒤바꾸거나 수령 방법, 상태 평가를 반대로 착각하게 합니다.",
                                "[게시글 마인드맵] 가격(무료) + 수령(직접 방문). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "분리수거 안내문",
                        "[분리수거 안내문] 대형 가전제품은 무상 방문 수거를 신청하면 별도 비용 없이 처리됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("대형 가전은 무상으로 수거된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("대형 가전 수거는 유료다.", "🔵 파랑 - '무상'과 반대되는 오답입니다."),
                                opt("직접 배출 장소로 가져가야 한다.", "🔵 파랑 - '방문 수거'라는 내용과 반대되는 오답입니다."),
                                opt("소형 가전만 무상 수거된다.", "🔵 파랑 - '대형 가전제품'을 '소형 가전'으로 바꾼 오답입니다.")
                        ), 0, "🔵 유료/무료를 뒤바꾸거나 수거 방식, 대상 크기를 바꿔 오답을 만듭니다.",
                                "[분리수거 마인드맵] 대상(대형 가전) + 방식(방문 수거, 무상). 색칠하세요.")),
                onePassage(PassageCategory.READING, "반려식물 관리법",
                        "[반려식물 관리법] 새로 데려온 식물은 2주 정도 격리해 병충해가 없는지 확인한 후 다른 식물과 함께 두세요.",
                        q("이 관리법의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("새 식물은 2주간 격리해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("새 식물은 바로 함께 둬도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("격리 기간은 1개월이다.", "🔵 파랑 - '2주'를 '1개월'로 바꾼 오답입니다."),
                                opt("병충해 확인은 필요 없다.", "🔵 파랑 - 명시된 목적과 반대되는 오답입니다.")
                        ), 0, "🔵 격리 필요성이나 기간 숫자를 바꿔 오답을 만듭니다.",
                                "[관리법 마인드맵] 새 식물 → 격리(2주) → 병충해 확인 → 합류. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "인테리어 후기",
                        "[인테리어 후기] 방음 커튼을 달았는데 밖의 소음이 확실히 줄어든 게 느껴져요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("방음 커튼으로 소음이 줄었다.", "정답: '소음이 확실히 줄었다'고 명시했습니다."),
                                opt("방음 커튼이 효과가 없었다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("소음이 더 심해졌다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("일반 커튼을 구매했다.", "🔵 파랑 - '방음 커튼'을 '일반 커튼'으로 바꾼 오답입니다.")
                        ), 0, "🔵 효과 유무를 반대로 착각하게 하거나 제품 종류를 바꿔 오답을 만듭니다.",
                                "[후기 마인드맵] 제품(방음 커튼) → 효과(소음 감소). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "가전제품 사용설명서",
                        "[사용설명서] 필터 교체 주기는 6개월이며, 교체하지 않을 경우 공기 정화 성능이 떨어질 수 있습니다.",
                        q("이 설명서에서 안내하는 필터 교체 주기로 알맞은 것을 고르십시오.", List.of(
                                opt("6개월", "정답: '필터 교체 주기는 6개월'이라고 명시했습니다."),
                                opt("1개월", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("2년", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("교체할 필요가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 교체 주기 숫자를 바꾸거나 교체 불필요로 왜곡합니다.",
                                "[설명서 마인드맵] 주기(6개월) → 미교체 시(성능 저하). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "중고거래 게시글",
                        "[중고거래 게시글] 제목: 자전거 급처합니다\n내용: 이사 때문에 급하게 팔아요. 가격 협상 가능합니다.",
                        q("이 게시글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("가격 협상이 가능하다.", "정답: '가격 협상 가능합니다'라고 명시했습니다."),
                                opt("가격 협상이 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("취미로 자전거를 판매한다.", "🔵 파랑 - '이사 때문에 급하게'라는 이유와 다른 오답입니다."),
                                opt("천천히 팔아도 상관없다.", "🔵 파랑 - '급처'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 협상 가능 여부나 판매 이유, 시급성을 반대로 착각하게 합니다.",
                                "[게시글 마인드맵] 이유(이사) + 조건(협상 가능). 핵심 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "분리수거 안내문",
                        "[분리수거 안내문] 우유팩은 일반 종이류와 구분하여 별도의 종이팩 전용 수거함에 배출해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("우유팩은 전용 수거함에 배출해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("우유팩은 일반 종이류와 함께 버려도 된다.", "🔵 파랑 - '구분하여'라는 내용과 반대되는 오답입니다."),
                                opt("우유팩은 재활용 대상이 아니다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("종이팩 수거함이 따로 없다.", "🔵 파랑 - '전용 수거함'과 반대되는 오답입니다.")
                        ), 0, "🔵 일반 종이류와의 구분 여부나 재활용 대상 여부를 반대로 착각하게 합니다.",
                                "[분리수거 마인드맵] 우유팩 ≠ 일반 종이류(전용 수거함). 구분 표현에 밑줄을 그으세요."))
        );

        List<PassageSeed> listening5th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "스포츠 경기 관람",
                        "여자: 어제 야구 경기 결과 어떻게 됐어요?\n남자: 우리 팀이 5대 2로 이겼어요.",
                        q("경기 결과로 알맞은 것을 고르십시오.", List.of(
                                opt("5대 2로 이겼다.", "정답: '5대 2로 이겼다'고 답했습니다."),
                                opt("2대 5로 졌다.", "🔴 빨강 - 점수를 뒤바꾼 오답입니다."),
                                opt("무승부였다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("경기가 취소됐다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 승패나 점수 순서를 뒤바꿔 오답을 만듭니다.",
                                "[경기 마인드맵] 우리 팀(5점) vs 상대팀(2점) = 승리. 숫자 순서를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "콘서트 관람",
                        "남자: 이번 콘서트 좌석 어디예요?\n여자: 2층 B구역이에요. 무대가 잘 보일 것 같아요.",
                        q("여자의 좌석 위치로 알맞은 것을 고르십시오.", List.of(
                                opt("2층 B구역", "정답: '2층 B구역'이라고 답했습니다."),
                                opt("1층 A구역", "🔵 파랑 - 층과 구역을 모두 바꾼 오답입니다."),
                                opt("3층 C구역", "🔵 파랑 - 층과 구역을 모두 바꾼 오답입니다."),
                                opt("스탠딩석", "🔵 파랑 - 언급되지 않은 좌석입니다.")
                        ), 0, "🔵 층수나 구역명을 슬쩍 바꿔 오답을 만듭니다.",
                                "[콘서트 마인드맵] 층(2층) + 구역(B). 두 정보를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "전시회 관람",
                        "여자: 이 전시회 몇 시까지 해요?\n남자: 오후 6시까지인데 마지막 입장은 5시 반이에요.",
                        q("전시회의 마지막 입장 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("오후 5시 반", "정답: '마지막 입장은 5시 반'이라고 답했습니다."),
                                opt("오후 6시", "🔴 빨강 - 종료 시간과 마지막 입장 시간을 혼동하게 합니다."),
                                opt("오후 4시", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("입장 제한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 종료 시간과 마지막 입장 시간을 혼동하게 합니다.",
                                "[전시회 마인드맵] 종료(6시) vs 마지막 입장(5시 반). 두 시간을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "영화 관람",
                        "남자: 이 영화 몇 분짜리예요?\n여자: 상영시간이 2시간 20분이에요.",
                        q("영화의 상영시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2시간 20분", "정답: '상영시간이 2시간 20분'이라고 답했습니다."),
                                opt("1시간 20분", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("2시간", "🔴 빨강 - '20분'을 빠뜨린 오답입니다."),
                                opt("3시간", "🔴 빨강 - 시간을 바꾼 오답입니다.")
                        ), 0, "🔴 시간 숫자를 슬쩍 바꾸거나 일부를 빠뜨려 오답을 만듭니다.",
                                "[영화 마인드맵] 상영시간(2시간 20분). 시간과 분을 모두 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "공연 예매",
                        "여자: 공연 티켓 예매하려는데 벌써 매진이래요.\n남자: 그럼 취소표라도 확인해 봐요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("취소표 확인하기", "정답: '취소표라도 확인해 보자'고 제안했습니다."),
                                opt("다른 공연 예매하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("암표 구매하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("예매를 포기하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 제안(취소표 확인)과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[예매 마인드맵] 문제(매진) → 제안(취소표 확인). 제안 문장의 목적어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "스포츠 경기 관람",
                        "남자: 축구 경기 몇 시에 시작해요?\n여자: 원래 7시였는데 우천으로 8시로 미뤄졌어요.",
                        q("축구 경기가 시작하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("8시", "정답: '우천으로 8시로 미뤄졌다'고 답했습니다."),
                                opt("7시", "🔴 빨강 - 원래 시간(변경 전)에 꽂힌 오답입니다."),
                                opt("9시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("경기가 취소됐다.", "🔴 빨강 - '미뤄졌다'와 다른 오답입니다.")
                        ), 0, "🔴 원래 시간과 변경된 시간을 혼동하게 합니다.",
                                "[경기 마인드맵] 7시(변경 전) → 화살표 → 8시(변경 후). 화살표 뒤가 정답입니다.")),
                onePassage(PassageCategory.LISTENING, "콘서트 관람",
                        "여자: 콘서트에서 응원봉 가져가도 되나요?\n남자: 네, 공식 응원봉만 반입 가능해요.",
                        q("콘서트에 반입 가능한 응원봉으로 알맞은 것을 고르십시오.", List.of(
                                opt("공식 응원봉", "정답: '공식 응원봉만 반입 가능하다'고 답했습니다."),
                                opt("아무 응원봉", "🔵 파랑 - '공식'이라는 조건을 빼고 읽게 하는 오답입니다."),
                                opt("응원봉은 전혀 반입할 수 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("응원봉 대신 손수건만 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 '공식'이라는 조건을 빼고 읽거나 반입 자체를 부정하는 오답을 만듭니다.",
                                "[콘서트 마인드맵] 조건(공식) + 물건(응원봉). 조건 표현에 밑줄을 그으세요.")),
                onePassage(PassageCategory.LISTENING, "전시회 관람",
                        "남자: 이 전시회 사진 촬영 되나요?\n여자: 네, 플래시만 안 쓰면 촬영 가능해요.",
                        q("전시회에서 사진 촬영 시 지켜야 할 규칙으로 알맞은 것을 고르십시오.", List.of(
                                opt("플래시를 사용하지 않기", "정답: '플래시만 안 쓰면 촬영 가능하다'고 답했습니다."),
                                opt("촬영이 전면 금지다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("플래시를 켜고 찍어야 한다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("동영상만 촬영 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 촬영 금지 조건(플래시)을 반대로 착각하게 하거나 전면 금지로 과장합니다.",
                                "[전시회 마인드맵] 허용(사진) + 금지(플래시). 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "영화 관람",
                        "여자: 이 영화 몇 세 이상 관람 가능해요?\n남자: 15세 이상 관람가예요.",
                        q("이 영화의 관람 가능 연령으로 알맞은 것을 고르십시오.", List.of(
                                opt("15세 이상", "정답: '15세 이상 관람가'라고 답했습니다."),
                                opt("전체 관람가", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("19세 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("12세 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 관람 등급 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[영화 마인드맵] 등급(15세 이상). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "공연 예매",
                        "남자: 예매한 티켓 취소하면 수수료 있나요?\n여자: 공연 3일 전까지는 수수료 없이 취소 가능해요.",
                        q("수수료 없이 취소 가능한 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("공연 3일 전까지", "정답: '3일 전까지는 수수료 없이 취소 가능하다'고 답했습니다."),
                                opt("공연 당일까지", "🔴 빨강 - 기한을 늘린 오답입니다."),
                                opt("공연 1일 전까지", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("취소가 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기한 숫자를 바꾸거나 취소 자체가 불가능하다고 왜곡합니다.",
                                "[예매 마인드맵] 조건(3일 전) → 결과(수수료 없음). 기한 숫자를 확인하세요."))
        );

        List<PassageSeed> listening5th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "스포츠 경기 관람",
                        "여자: 농구 경기 어디서 봐요?\n남자: 체육관 2층 관중석에서 볼 수 있어요.",
                        q("농구 경기를 관람할 수 있는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("체육관 2층 관중석", "정답: '체육관 2층 관중석에서 볼 수 있다'고 답했습니다."),
                                opt("체육관 1층 관중석", "🔵 파랑 - 층수를 바꾼 오답입니다."),
                                opt("경기장 밖", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("중계 화면으로만 볼 수 있다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 층수를 바꾸거나 직접 관람이 불가능하다고 왜곡합니다.",
                                "[경기 마인드맵] 장소(체육관) + 위치(2층 관중석). 두 정보를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "콘서트 관람",
                        "남자: 콘서트 앙코르는 몇 곡이나 했어요?\n여자: 세 곡이나 해 줬어요. 정말 감동이었어요.",
                        q("콘서트에서 앙코르로 부른 곡의 수로 알맞은 것을 고르십시오.", List.of(
                                opt("세 곡", "정답: '세 곡이나 해 줬다'고 답했습니다."),
                                opt("한 곡", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("다섯 곡", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("앙코르가 없었다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 곡의 수를 바꾸거나 앙코르 자체가 없었다고 왜곡합니다.",
                                "[콘서트 마인드맵] 질문(앙코르 곡 수) → 답(세 곡). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "전시회 관람",
                        "여자: 이 전시회 오디오 가이드가 있나요?\n남자: 네, 입구에서 대여료 3천 원 내면 빌릴 수 있어요.",
                        q("오디오 가이드 대여료로 알맞은 것을 고르십시오.", List.of(
                                opt("3천 원", "정답: '대여료 3천 원'이라고 답했습니다."),
                                opt("무료다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("5천 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 대여료 숫자를 슬쩍 바꾸거나 무료로 착각하게 합니다.",
                                "[전시회 마인드맵] 물건(오디오 가이드) + 대여료(3천 원). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "영화 관람",
                        "남자: 조조할인 몇 시까지 적용돼요?\n여자: 오전 9시 이전 상영이면 조조할인 받을 수 있어요.",
                        q("조조할인이 적용되는 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("오전 9시 이전 상영", "정답: '오전 9시 이전 상영이면 조조할인'이라고 답했습니다."),
                                opt("오후 9시 이전 상영", "🔴 빨강 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("오전 11시 이전 상영", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("시간과 상관없이 적용된다.", "🔴 빨강 - 조건을 빼고 읽게 하는 오답입니다.")
                        ), 0, "🔴 오전/오후를 바꾸거나 시간 조건을 빼고 읽게 합니다.",
                                "[영화 마인드맵] 조건(오전 9시 이전) → 혜택(조조할인). 시간대를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "공연 예매",
                        "여자: 이 공연 좌석 등급이 어떻게 나뉘어요?\n남자: VIP석, R석, S석으로 나뉘는데 저는 R석을 예매했어요.",
                        q("남자가 예매한 좌석 등급으로 알맞은 것을 고르십시오.", List.of(
                                opt("R석", "정답: '저는 R석을 예매했다'고 답했습니다."),
                                opt("VIP석", "🔵 파랑 - 언급된 다른 등급으로 헷갈리게 하는 오답입니다."),
                                opt("S석", "🔵 파랑 - 언급된 다른 등급으로 헷갈리게 하는 오답입니다."),
                                opt("등급을 정하지 않았다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 여러 등급이 나열될 때 실제 선택한 등급을 놓치게 합니다.",
                                "[예매 마인드맵] 등급(VIP, R, S) 중 → 선택(R석). '저는'이라는 화자 표현 뒤를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "스포츠 경기 관람",
                        "남자: 이번 마라톤 대회 참가자가 몇 명이래요?\n여자: 올해는 만 명이 넘게 참가했대요.",
                        q("이번 마라톤 대회 참가자 수로 알맞은 것을 고르십시오.", List.of(
                                opt("만 명 이상", "정답: '만 명이 넘게 참가했다'고 답했습니다."),
                                opt("천 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("오만 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("참가자가 없었다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 참가자 수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[경기 마인드맵] 질문(참가자 수) → 답(만 명 이상). 숫자 단위를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "콘서트 관람",
                        "여자: 콘서트장 주차는 어떻게 해요?\n남자: 주차 공간이 부족해서 대중교통 이용을 추천해요.",
                        q("남자가 추천한 이동 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("대중교통 이용", "정답: '대중교통 이용을 추천한다'고 답했습니다."),
                                opt("자가용 이용", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("도보 이용", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("택시 이용", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 추천된 방법과 무관하거나 반대되는 방법으로 오답을 만듭니다.",
                                "[콘서트 마인드맵] 문제(주차 부족) → 추천(대중교통). 원인과 추천을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "전시회 관람",
                        "남자: 이 전시회는 매주 월요일 휴관인가요?\n여자: 아니요, 이 전시회는 화요일이 휴관일이에요.",
                        q("이 전시회의 휴관일로 알맞은 것을 고르십시오.", List.of(
                                opt("화요일", "정답: '화요일이 휴관일'이라고 답했습니다."),
                                opt("월요일", "🔴 빨강 - 남자가 물어본 요일과 헷갈리게 하는 오답입니다."),
                                opt("수요일", "🔴 빨강 - 대화에 없는 요일입니다."),
                                opt("휴관일이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 질문에서 언급된 요일과 실제 답을 혼동하게 합니다.",
                                "[전시회 마인드맵] 질문(월요일?) → 답(아니요, 화요일). 부정 답변 뒤를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "영화 관람",
                        "여자: 이 영화관은 팝콘 세트가 얼마예요?\n남자: 라지 사이즈 세트가 만 이천 원이에요.",
                        q("라지 사이즈 팝콘 세트의 가격으로 알맞은 것을 고르십시오.", List.of(
                                opt("만 이천 원", "정답: '라지 사이즈 세트가 만 이천 원'이라고 답했습니다."),
                                opt("만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("이만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("무료다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 가격 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[영화 마인드맵] 사이즈(라지) + 가격(만 이천 원). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "공연 예매",
                        "남자: 예매 시 할인 쿠폰 쓸 수 있어요?\n여자: 네, 결제 단계에서 쿠폰 코드를 입력하면 돼요.",
                        q("할인 쿠폰을 사용하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("결제 단계에서 쿠폰 코드 입력하기", "정답: '결제 단계에서 쿠폰 코드를 입력하면 된다'고 답했습니다."),
                                opt("예매 전에 미리 등록하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("고객센터에 전화하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("현장에서 직원에게 말하기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[예매 마인드맵] 시점(결제 단계) + 방법(쿠폰 코드 입력). 순서와 방법을 색칠하세요."))
        );

        List<PassageSeed> reading5th21to30 = List.of(
                onePassage(PassageCategory.READING, "경기 결과 기사",
                        "[경기 결과] 어제 열린 결승전에서 청팀이 백팀을 3대 1로 꺾고 우승을 차지했다.",
                        q("이 기사의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("청팀이 우승했다.", "정답: '청팀이 백팀을 꺾고 우승을 차지했다'고 명시했습니다."),
                                opt("백팀이 우승했다.", "🔵 파랑 - 승패를 뒤바꾼 오답입니다."),
                                opt("점수는 1대 3이었다.", "🔵 파랑 - 점수 순서를 뒤바꾼 오답입니다."),
                                opt("경기는 무승부로 끝났다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 승패나 점수 순서를 뒤바꿔 오답을 만듭니다.",
                                "[경기 마인드맵] 청팀(3점, 승) vs 백팀(1점, 패). 숫자 순서를 정확히 확인하세요.")),
                onePassage(PassageCategory.READING, "콘서트 후기",
                        "[콘서트 후기] 음향 시설이 정말 좋아서 몰입도가 높았어요. 다만 대기 줄이 너무 길어서 힘들었어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("대기 줄이 길어서 힘들었다.", "정답: '대기 줄이 너무 길어서 힘들었다'고 명시했습니다."),
                                opt("대기 줄이 짧았다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("음향 시설이 나빴다.", "🔵 파랑 - '음향 시설이 좋았다'와 반대되는 오답입니다."),
                                opt("전반적으로 불만족스러웠다.", "🔵 파랑 - 긍정 평가(몰입도 높음)와 반대되는 오답입니다.")
                        ), 0, "🔵 장단점을 뒤바꾸거나 전체 평가를 반대로 착각하게 합니다.",
                                "[후기 마인드맵] 장점(음향) + 단점(긴 대기줄). 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "전시회 안내",
                        "[전시회 안내] 이 전시는 예약제로 운영되며, 현장 구매는 잔여 좌석이 있을 때만 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("현장 구매는 잔여석이 있을 때만 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("현장 구매가 항상 가능하다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("예약 없이는 절대 입장할 수 없다.", "🔵 파랑 - '잔여 좌석이 있을 때 가능'과 반대되는 과장된 오답입니다."),
                                opt("예약제가 아니다.", "🔵 파랑 - '예약제로 운영'과 반대되는 오답입니다.")
                        ), 0, "🔵 현장 구매 가능 조건을 반대로 착각하게 하거나 과장합니다.",
                                "[전시회 마인드맵] 기본(예약제) + 예외(잔여석 시 현장 구매). 색칠하세요.")),
                onePassage(PassageCategory.READING, "영화 리뷰",
                        "[영화 리뷰] ★★★★☆ 스토리는 예측 가능했지만 배우들의 연기가 훌륭해서 몰입할 수 있었다.",
                        q("이 리뷰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("배우들의 연기가 훌륭했다.", "정답: '배우들의 연기가 훌륭했다'고 명시했습니다."),
                                opt("배우들의 연기가 아쉬웠다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("스토리가 매우 독창적이었다.", "🔵 파랑 - '예측 가능했다'와 반대되는 오답입니다."),
                                opt("전체적으로 실망스러웠다.", "🔵 파랑 - 높은 별점(★★★★☆)과 반대되는 오답입니다.")
                        ), 0, "🔵 장단점을 뒤바꾸거나 전체 평가를 반대로 착각하게 합니다.",
                                "[리뷰 마인드맵] 단점(예측 가능한 스토리) + 장점(연기). 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "공연 예매 안내",
                        "[공연 예매 안내] 1인당 최대 4매까지 예매 가능하며, 초과 구매 시 예매가 자동 취소될 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("1인당 최대 4매까지 예매할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("매수 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("최대 10매까지 가능하다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("초과 구매해도 문제없다.", "🔵 파랑 - '자동 취소될 수 있다'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 매수 제한 숫자를 바꾸거나 초과 구매 시 결과를 반대로 착각하게 합니다.",
                                "[예매 마인드맵] 제한(4매) + 초과 시(자동 취소). 숫자와 결과를 색칠하세요.")),
                onePassage(PassageCategory.READING, "경기 결과 기사",
                        "[경기 결과] 이번 시즌 마지막 경기에서 선수가 시즌 최다 득점을 기록하며 화제를 모았다.",
                        q("이 기사의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("선수가 시즌 최다 득점을 기록했다.", "정답: 기사에 그대로 명시되어 있습니다."),
                                opt("선수가 이번 시즌 최소 득점을 기록했다.", "🔵 파랑 - '최다'를 '최소'로 바꾼 오답입니다."),
                                opt("이 경기는 시즌 첫 경기였다.", "🔵 파랑 - '마지막 경기'와 반대되는 오답입니다."),
                                opt("선수는 부상으로 결장했다.", "🔵 파랑 - 기사 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 최다/최소를 뒤바꾸거나 시즌 시점, 출전 여부를 왜곡합니다.",
                                "[경기 마인드맵] 시점(마지막 경기) + 기록(최다 득점). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "콘서트 후기",
                        "[콘서트 후기] 좌석에서 무대가 잘 안 보였어요. 다음엔 좀 더 좋은 자리로 예매해야겠어요.",
                        q("이 후기를 쓴 사람의 다음 계획으로 알맞은 것을 고르십시오.", List.of(
                                opt("다음엔 더 좋은 자리를 예매한다.", "정답: '다음엔 좀 더 좋은 자리로 예매해야겠다'고 명시했습니다."),
                                opt("다시는 콘서트에 가지 않는다.", "🔵 파랑 - 언급되지 않은 계획입니다."),
                                opt("같은 자리로 다시 예매한다.", "🔵 파랑 - 명시된 계획과 반대되는 오답입니다."),
                                opt("무대가 잘 보였다고 만족했다.", "🔵 파랑 - '잘 안 보였다'와 반대되는 오답입니다.")
                        ), 0, "🔵 불만을 반대로 착각하게 하거나 언급되지 않은 계획으로 오답을 만듭니다.",
                                "[후기 마인드맵] 불만(안 보임) → 계획(좋은 자리 예매). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "전시회 안내",
                        "[전시회 안내] 만 7세 미만 어린이는 보호자 동반 시 무료로 입장할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("만 7세 미만은 보호자와 동반 시 무료다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("만 7세 미만은 항상 무료다.", "🔵 파랑 - '보호자 동반 시'라는 조건을 빼고 읽게 합니다."),
                                opt("만 7세 이상만 무료다.", "🔵 파랑 - 나이 조건을 반대로 착각하게 합니다."),
                                opt("어린이는 입장할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(보호자 동반)을 빼거나 나이 조건을 반대로 착각하게 합니다.",
                                "[전시회 마인드맵] 대상(7세 미만) + 조건(보호자 동반) + 혜택(무료). 색칠하세요.")),
                onePassage(PassageCategory.READING, "영화 리뷰",
                        "[영화 리뷰] ★★☆☆☆ 초반 전개가 너무 느려서 지루했다. 후반부는 그나마 나았다.",
                        q("이 리뷰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초반 전개가 느려서 지루했다.", "정답: 리뷰에 그대로 명시되어 있습니다."),
                                opt("초반부터 매우 흥미진진했다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("후반부가 초반보다 별로였다.", "🔵 파랑 - '후반부는 그나마 나았다'와 반대되는 오답입니다."),
                                opt("전체적으로 매우 만족스러웠다.", "🔵 파랑 - 낮은 별점(★★☆☆☆)과 반대되는 오답입니다.")
                        ), 0, "🔵 초반/후반 평가를 뒤바꾸거나 전체 만족도를 반대로 착각하게 합니다.",
                                "[리뷰 마인드맵] 초반(지루함) vs 후반(나음). 두 구간을 색으로 비교하세요.")),
                onePassage(PassageCategory.READING, "공연 예매 안내",
                        "[공연 예매 안내] 예매 오픈은 다음 주 월요일 오후 2시이며, 선착순으로 진행됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("예매는 선착순으로 진행된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("예매는 추첨으로 진행된다.", "🔵 파랑 - '선착순'을 '추첨'으로 바꾼 오답입니다."),
                                opt("예매 오픈은 이번 주다.", "🔵 파랑 - '다음 주'를 '이번 주'로 바꾼 오답입니다."),
                                opt("예매는 오전에 시작한다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다.")
                        ), 0, "🔵 진행 방식이나 시점, 시간대를 바꿔 오답을 만듭니다.",
                                "[예매 마인드맵] 시점(다음 주 월요일 2시) + 방식(선착순). 색으로 구분하세요."))
        );

        List<PassageSeed> reading5th31to40 = List.of(
                onePassage(PassageCategory.READING, "경기 결과 기사",
                        "[경기 결과] 연장전까지 이어진 접전 끝에 홈팀이 극적인 역전승을 거두었다.",
                        q("이 기사의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("홈팀이 역전승했다.", "정답: '홈팀이 극적인 역전승을 거두었다'고 명시했습니다."),
                                opt("원정팀이 역전승했다.", "🔵 파랑 - '홈팀'을 '원정팀'으로 바꾼 오답입니다."),
                                opt("경기는 정규시간에 끝났다.", "🔵 파랑 - '연장전까지 이어진'과 반대되는 오답입니다."),
                                opt("일방적인 경기였다.", "🔵 파랑 - '접전'이라는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 승리 팀을 뒤바꾸거나 경기 양상(접전/일방적)을 왜곡합니다.",
                                "[경기 마인드맵] 양상(접전, 연장전) + 결과(홈팀 역전승). 색칠하세요.")),
                onePassage(PassageCategory.READING, "콘서트 후기",
                        "[콘서트 후기] 떼창 문화가 정말 인상적이었어요. 다음에도 꼭 다시 가고 싶어요.",
                        q("이 후기를 쓴 사람의 심정으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("만족스럽다", "정답: '다시 가고 싶다'는 표현에서 만족감이 드러납니다."),
                                opt("실망스럽다", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("지루하다", "🔵 파랑 - 언급되지 않은 감정입니다."),
                                opt("무섭다", "🔵 파랑 - 언급되지 않은 감정입니다.")
                        ), 0, "🔵 긍정적 후기를 무관한 부정적 감정으로 착각하게 합니다.",
                                "[후기 마인드맵] 경험(떼창) → 감정(만족, 재방문 의사). 마지막 문장을 확인하세요.")),
                onePassage(PassageCategory.READING, "전시회 안내",
                        "[전시회 안내] 이 전시는 순회 전시로, 서울 전시가 끝난 후 부산으로 이동할 예정입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("서울 전시 후 부산으로 이동한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("부산 전시 후 서울로 이동한다.", "🔵 파랑 - 순서를 뒤바꾼 오답입니다."),
                                opt("서울에서만 전시된다.", "🔵 파랑 - '순회 전시'라는 내용과 반대되는 오답입니다."),
                                opt("전시 일정이 정해지지 않았다.", "🔵 파랑 - 명시된 계획과 반대되는 오답입니다.")
                        ), 0, "🔵 도시 이동 순서를 뒤바꾸거나 순회 여부를 반대로 착각하게 합니다.",
                                "[전시회 마인드맵] 서울(1차) → 화살표 → 부산(2차). 순서를 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "영화 리뷰",
                        "[영화 리뷰] ★★★★★ OST가 너무 좋아서 영화 보고 나서 바로 음악을 찾아 들었어요.",
                        q("이 리뷰를 쓴 사람이 영화를 본 후 한 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("영화 음악을 찾아 들었다.", "정답: '바로 음악을 찾아 들었다'고 명시했습니다."),
                                opt("영화관에 다시 갔다.", "🔵 파랑 - 언급되지 않은 행동입니다."),
                                opt("영화 원작 소설을 읽었다.", "🔵 파랑 - 언급되지 않은 행동입니다."),
                                opt("아무 행동도 하지 않았다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 행동과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[리뷰 마인드맵] 감상(OST 좋음) → 행동(음악 찾아 듣기). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "공연 예매 안내",
                        "[공연 예매 안내] 학생 할인은 신분증 확인 후 현장에서 발권 시에만 적용됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("학생 할인은 현장 발권 시에만 적용된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("학생 할인은 온라인 예매 시에도 적용된다.", "🔵 파랑 - '현장에서 발권 시에만'과 반대되는 오답입니다."),
                                opt("신분증 확인 없이 할인받는다.", "🔵 파랑 - '신분증 확인 후'라는 조건을 빼고 읽게 합니다."),
                                opt("학생 할인은 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 적용 방법(온라인/현장)이나 확인 절차를 반대로 착각하게 합니다.",
                                "[예매 마인드맵] 조건(신분증 확인) + 방법(현장 발권). 색칠하세요.")),
                onePassage(PassageCategory.READING, "경기 결과 기사",
                        "[경기 결과] 부상으로 결장했던 주전 선수가 이번 경기에 복귀하며 팀 승리에 크게 기여했다.",
                        q("이 기사의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("주전 선수가 이번 경기에 복귀했다.", "정답: 기사에 그대로 명시되어 있습니다."),
                                opt("주전 선수가 이번 경기에도 결장했다.", "🔵 파랑 - '복귀했다'와 반대되는 오답입니다."),
                                opt("팀이 이번 경기에서 패배했다.", "🔵 파랑 - '승리에 기여했다'와 반대되는 오답입니다."),
                                opt("선수는 부상당한 적이 없다.", "🔵 파랑 - '부상으로 결장했던'과 반대되는 오답입니다.")
                        ), 0, "🔵 결장/복귀 여부나 경기 결과를 반대로 착각하게 합니다.",
                                "[경기 마인드맵] 부상(결장) → 화살표 → 복귀(승리 기여). 순서를 색으로 표시하세요.")),
                onePassage(PassageCategory.READING, "콘서트 후기",
                        "[콘서트 후기] 시작 시간이 예정보다 30분 늦어져서 아쉬웠지만, 공연 자체는 완벽했어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("시작 시간이 예정보다 늦어졌다.", "정답: '예정보다 30분 늦어졌다'고 명시했습니다."),
                                opt("시작 시간이 예정보다 빨랐다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("공연 내용이 별로였다.", "🔵 파랑 - '공연 자체는 완벽했다'와 반대되는 오답입니다."),
                                opt("정시에 시작했다.", "🔵 파랑 - '30분 늦어졌다'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 지연 여부나 공연 만족도를 반대로 착각하게 합니다.",
                                "[후기 마인드맵] 단점(지연) + 장점(완벽한 공연). 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "전시회 안내",
                        "[전시회 안내] 도슨트 설명은 매일 오전 11시와 오후 3시, 하루 두 차례 진행됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("도슨트 설명은 하루 두 차례 진행된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("도슨트 설명은 하루 한 차례다.", "🔵 파랑 - 횟수를 바꾼 오답입니다."),
                                opt("도슨트 설명은 저녁에만 있다.", "🔵 파랑 - 실제 시간대와 다른 오답입니다."),
                                opt("도슨트 설명은 주말에만 진행된다.", "🔵 파랑 - '매일'과 반대되는 오답입니다.")
                        ), 0, "🔵 진행 횟수나 시간대, 요일을 바꿔 오답을 만듭니다.",
                                "[전시회 마인드맵] 횟수(하루 2회) + 시간(11시, 3시). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "영화 리뷰",
                        "[영화 리뷰] ★★★☆☆ 특수효과는 화려했지만 이야기 전개가 다소 억지스러웠다.",
                        q("이 리뷰의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("특수효과는 화려했다.", "정답: 리뷰에 그대로 명시되어 있습니다."),
                                opt("특수효과가 부족했다.", "🔵 파랑 - 리뷰 내용과 반대되는 오답입니다."),
                                opt("이야기 전개가 자연스러웠다.", "🔵 파랑 - '억지스러웠다'와 반대되는 오답입니다."),
                                opt("전체적으로 완벽한 영화였다.", "🔵 파랑 - 중간 별점(★★★☆☆)과 반대되는 과장된 오답입니다.")
                        ), 0, "🔵 장단점을 뒤바꾸거나 전체 평가를 과장해 오답을 만듭니다.",
                                "[리뷰 마인드맵] 장점(특수효과) + 단점(억지 전개). 다른 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "공연 예매 안내",
                        "[공연 예매 안내] 공연 당일 좌석 변경은 불가능하니 예매 시 신중하게 선택해 주세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("공연 당일 좌석 변경이 불가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("공연 당일에도 좌석을 바꿀 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("예매 전에는 좌석 선택이 필요 없다.", "🔵 파랑 - '신중하게 선택하라'는 내용과 반대되는 오답입니다."),
                                opt("좌석은 랜덤으로 배정된다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 좌석 변경 가능 여부를 반대로 착각하게 하거나 언급되지 않은 배정 방식을 추가합니다.",
                                "[예매 마인드맵] 당일(변경 불가) → 예매 시(신중한 선택 필요). 색칠하세요."))
        );

        List<PassageSeed> listening6th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "어학 공부",
                        "여자: 요즘 영어 공부 어떻게 하고 있어요?\n남자: 매일 아침 30분씩 영어 뉴스를 듣고 있어요.",
                        q("남자의 영어 공부 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("매일 아침 영어 뉴스 듣기", "정답: '매일 아침 30분씩 영어 뉴스를 듣는다'고 답했습니다."),
                                opt("매일 저녁 단어 암기하기", "🔴 빨강 - 시간대와 방법을 모두 바꾼 오답입니다."),
                                opt("일주일에 한 번 회화 수업 듣기", "🔴 빨강 - 언급되지 않은 방법입니다."),
                                opt("영어 공부를 하지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시간대나 공부 방법을 슬쩍 바꿔 오답을 만듭니다.",
                                "[어학 마인드맵] 빈도(매일 아침) + 방법(뉴스 듣기). 두 정보를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "자격증 시험",
                        "남자: 자격증 시험이 언제예요?\n여자: 다음 달 둘째 주 토요일이에요.",
                        q("자격증 시험 날짜로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 달 둘째 주 토요일", "정답: '다음 달 둘째 주 토요일'이라고 답했습니다."),
                                opt("이번 달 둘째 주 토요일", "🔴 빨강 - '다음 달'을 '이번 달'로 바꾼 오답입니다."),
                                opt("다음 달 첫째 주 토요일", "🔴 빨강 - 주차를 바꾼 오답입니다."),
                                opt("다음 달 둘째 주 일요일", "🔴 빨강 - 요일을 바꾼 오답입니다.")
                        ), 0, "🔴 월, 주차, 요일 중 하나를 슬쩍 바꿔 오답을 만듭니다.",
                                "[자격증 마인드맵] 월(다음 달) + 주차(둘째) + 요일(토요일). 세 정보를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "취미 학원",
                        "여자: 요즘 뭐 배우고 있어요?\n남자: 저는 요즘 기타 학원에 다니고 있어요.",
                        q("남자가 다니고 있는 학원으로 알맞은 것을 고르십시오.", List.of(
                                opt("기타 학원", "정답: '기타 학원에 다니고 있다'고 답했습니다."),
                                opt("피아노 학원", "🔵 파랑 - '기타'를 '피아노'로 바꾼 오답입니다."),
                                opt("미술 학원", "🔵 파랑 - 언급되지 않은 학원입니다."),
                                opt("요리 학원", "🔵 파랑 - 언급되지 않은 학원입니다.")
                        ), 0, "🔵 실제 언급된 학원과 무관한 다른 학원으로 오답을 만듭니다.",
                                "[취미 마인드맵] 질문(뭐 배워요) → 답(기타 학원). 학원명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "온라인 튜터링",
                        "남자: 온라인 튜터링 수업은 몇 명이서 들어요?\n여자: 1대1로 진행돼서 저만 듣는 거예요.",
                        q("온라인 튜터링 수업의 형태로 알맞은 것을 고르십시오.", List.of(
                                opt("1대1 개인 수업", "정답: '1대1로 진행된다'고 답했습니다."),
                                opt("그룹 수업", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("대형 강의", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("녹화 강의만 본다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 1대1과 그룹 수업을 혼동하게 합니다.",
                                "[튜터링 마인드맵] 형태(1대1). 인원 관련 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "스터디 모임",
                        "여자: 스터디는 몇 명이서 해요?\n남자: 저희는 다섯 명이서 하고 있어요.",
                        q("스터디 모임의 인원수로 알맞은 것을 고르십시오.", List.of(
                                opt("다섯 명", "정답: '다섯 명이서 하고 있다'고 답했습니다."),
                                opt("세 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("열 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("혼자 한다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 인원수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[스터디 마인드맵] 질문(인원수) → 답(다섯 명). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "어학 공부",
                        "남자: 일본어는 어떻게 시작하는 게 좋을까요?\n여자: 먼저 히라가나부터 외우는 게 기본이에요.",
                        q("여자가 추천한 학습 순서로 알맞은 것을 고르십시오.", List.of(
                                opt("히라가나부터 외우기", "정답: '히라가나부터 외우는 게 기본'이라고 답했습니다."),
                                opt("한자부터 외우기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("회화부터 시작하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("문법책부터 사기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 추천된 방법과 무관한 다른 학습 순서로 오답을 만듭니다.",
                                "[어학 마인드맵] 시작(히라가나). '먼저'라는 순서 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "자격증 시험",
                        "여자: 이번 시험 합격률이 어떻게 돼요?\n남자: 작년 기준으로 30% 정도예요.",
                        q("이 시험의 합격률로 알맞은 것을 고르십시오.", List.of(
                                opt("30% 정도", "정답: '30% 정도'라고 답했습니다."),
                                opt("50% 정도", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("10% 정도", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("합격률이 매우 높다.", "🔴 빨강 - 낮은 합격률과 반대되는 오답입니다.")
                        ), 0, "🔴 합격률 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[자격증 마인드맵] 기준(작년) + 합격률(30%). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "취미 학원",
                        "남자: 요가 학원 등록하려는데 몇 개월 끊는 게 좋을까요?\n여자: 3개월 이상 등록하면 할인이 있어요.",
                        q("할인을 받을 수 있는 등록 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("3개월 이상", "정답: '3개월 이상 등록하면 할인이 있다'고 답했습니다."),
                                opt("1개월 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("6개월 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("할인이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 할인 조건의 기간 숫자를 바꿔 오답을 만듭니다.",
                                "[취미 마인드맵] 조건(3개월 이상) → 혜택(할인). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "온라인 튜터링",
                        "여자: 튜터 선생님은 어떻게 고를 수 있어요?\n남자: 프로필과 후기를 보고 직접 선택할 수 있어요.",
                        q("튜터를 선택하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("프로필과 후기를 보고 선택", "정답: '프로필과 후기를 보고 직접 선택할 수 있다'고 답했습니다."),
                                opt("자동으로 배정된다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("전화로 상담 후 배정된다.", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("나이순으로 배정된다.", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법(직접 선택)과 반대되거나 무관한 방법으로 오답을 만듭니다.",
                                "[튜터링 마인드맵] 방법(프로필+후기 확인 → 직접 선택). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "스터디 모임",
                        "남자: 스터디는 얼마나 자주 모여요?\n여자: 매주 수요일 저녁에 모여요.",
                        q("스터디 모임이 열리는 요일로 알맞은 것을 고르십시오.", List.of(
                                opt("수요일", "정답: '매주 수요일 저녁에 모인다'고 답했습니다."),
                                opt("월요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("금요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("매일 모인다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 요일을 바꾸거나 빈도를 과장해 오답을 만듭니다.",
                                "[스터디 마인드맵] 빈도(매주) + 요일(수요일) + 시간대(저녁). 색칠하세요."))
        );

        List<PassageSeed> listening6th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "어학 공부",
                        "여자: 중국어 발음이 너무 어려워요.\n남자: 성조 연습을 매일 꾸준히 하면 늘어요.",
                        q("남자가 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("성조 연습을 꾸준히 하기", "정답: '성조 연습을 매일 꾸준히 하면 는다'고 제안했습니다."),
                                opt("발음 교정 학원 다니기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("중국어를 포기하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("녹음해서 들어보기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(성조 연습)과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[어학 마인드맵] 문제(발음 어려움) → 제안(성조 연습). 원인과 해결책을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "자격증 시험",
                        "남자: 시험 접수는 언제까지예요?\n여자: 시험 2주 전까지 접수해야 해요.",
                        q("시험 접수 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("시험 2주 전까지", "정답: '시험 2주 전까지 접수해야 한다'고 답했습니다."),
                                opt("시험 1주 전까지", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("시험 당일까지", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("접수 기한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기간 숫자를 바꾸거나 기한이 없다고 왜곡합니다.",
                                "[자격증 마인드맵] 접수(2주 전까지). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "취미 학원",
                        "여자: 도자기 공방 체험은 얼마나 걸려요?\n남자: 보통 2시간 정도 걸려요.",
                        q("도자기 공방 체험에 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2시간", "정답: '보통 2시간 정도 걸린다'고 답했습니다."),
                                opt("30분", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("5시간", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("하루 종일 걸린다.", "🔴 빨강 - 대화 내용과 반대되는 과장된 오답입니다.")
                        ), 0, "🔴 소요 시간 숫자를 바꾸거나 과장해 오답을 만듭니다.",
                                "[취미 마인드맵] 체험(도자기) + 시간(2시간). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "온라인 튜터링",
                        "남자: 튜터링 수업은 녹화가 되나요?\n여자: 네, 복습할 수 있게 자동으로 녹화돼요.",
                        q("온라인 튜터링 수업의 특징으로 알맞은 것을 고르십시오.", List.of(
                                opt("수업이 자동으로 녹화된다.", "정답: '자동으로 녹화된다'고 답했습니다."),
                                opt("수업은 녹화되지 않는다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("녹화는 유료 서비스다.", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("직접 녹화 버튼을 눌러야 한다.", "🟢 초록 - '자동으로'라는 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 자동/수동 녹화 여부를 반대로 착각하게 하거나 녹화 여부 자체를 부정합니다.",
                                "[튜터링 마인드맵] 특징(자동 녹화) → 목적(복습). 핵심 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "스터디 모임",
                        "여자: 스터디 카페 자리 예약해야 하나요?\n남자: 네, 주말엔 사람이 많아서 미리 예약하는 게 좋아요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("미리 예약하기", "정답: '미리 예약하는 게 좋다'고 제안했습니다."),
                                opt("예약 없이 가기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("평일에만 가기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("다른 카페로 바꾸기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(예약)과 반대되거나 무관한 다른 행동으로 오답을 만듭니다.",
                                "[스터디 마인드맵] 상황(주말 혼잡) → 제안(미리 예약). 원인과 제안을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "어학 공부",
                        "남자: 스페인어 어휘는 어떻게 외우세요?\n여자: 저는 예문과 함께 통째로 외워요.",
                        q("여자의 단어 암기 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("예문과 함께 통째로 외우기", "정답: '예문과 함께 통째로 외운다'고 답했습니다."),
                                opt("단어만 따로 외우기", "🔴 빨강 - '예문과 함께'라는 내용과 반대되는 오답입니다."),
                                opt("단어장 앱을 사용한다.", "🔴 빨강 - 언급되지 않은 방법입니다."),
                                opt("외우지 않고 감으로 안다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 방법과 반대되거나 무관한 다른 암기법으로 오답을 만듭니다.",
                                "[어학 마인드맵] 방법(예문+단어 함께). '통째로'라는 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "자격증 시험",
                        "여자: 이 자격증은 실기 시험도 있나요?\n남자: 네, 필기 합격 후에 실기 시험을 봐요.",
                        q("실기 시험을 보는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("필기 합격 후", "정답: '필기 합격 후에 실기 시험을 본다'고 답했습니다."),
                                opt("필기 시험 전", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("필기와 실기를 동시에 본다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("실기 시험이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 필기와 실기의 순서를 뒤바꾸거나 동시 진행으로 착각하게 합니다.",
                                "[자격증 마인드맵] 1(필기) → 2(실기). 순서 화살표를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "취미 학원",
                        "남자: 이 학원은 몇 명씩 수업해요?\n여자: 소규모라서 최대 6명까지만 받아요.",
                        q("이 학원 수업의 최대 정원으로 알맞은 것을 고르십시오.", List.of(
                                opt("6명", "정답: '최대 6명까지만 받는다'고 답했습니다."),
                                opt("3명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("20명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("인원 제한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 정원 숫자를 슬쩍 바꾸거나 제한이 없다고 왜곡합니다.",
                                "[취미 마인드맵] 규모(소규모) + 정원(최대 6명). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "온라인 튜터링",
                        "여자: 튜터링 수업 시간은 조정할 수 있어요?\n남자: 네, 튜터와 협의해서 자유롭게 조정 가능해요.",
                        q("튜터링 수업 시간 조정에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("튜터와 협의해서 조정할 수 있다.", "정답: '튜터와 협의해서 조정 가능하다'고 답했습니다."),
                                opt("시간 조정이 불가능하다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("정해진 시간에만 들어야 한다.", "🔵 파랑 - '자유롭게 조정 가능'과 반대되는 오답입니다."),
                                opt("추가 요금을 내야 조정된다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 조정 가능 여부를 반대로 착각하게 하거나 언급되지 않은 조건을 추가합니다.",
                                "[튜터링 마인드맵] 방법(튜터와 협의) → 결과(자유 조정). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "스터디 모임",
                        "남자: 스터디 벌금 제도가 있어요?\n여자: 네, 지각하면 천 원씩 벌금을 내요.",
                        q("스터디 지각 시 내야 하는 벌금으로 알맞은 것을 고르십시오.", List.of(
                                opt("천 원", "정답: '지각하면 천 원씩 벌금을 낸다'고 답했습니다."),
                                opt("오천 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("벌금 제도가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 벌금 숫자를 슬쩍 바꾸거나 제도가 없다고 왜곡합니다.",
                                "[스터디 마인드맵] 규칙(지각 시) → 벌금(천 원). 조건과 숫자를 색으로 연결하세요."))
        );

        List<PassageSeed> reading6th21to30 = List.of(
                onePassage(PassageCategory.READING, "어학원 안내",
                        "[어학원 안내] 레벨 테스트 후 본인 수준에 맞는 반에 배정됩니다. 테스트는 매주 월요일에 진행됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("레벨 테스트는 매주 월요일에 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("레벨 테스트 없이 반이 정해진다.", "🔵 파랑 - 명시된 절차와 반대되는 오답입니다."),
                                opt("테스트는 매일 진행된다.", "🔵 파랑 - '매주 월요일'을 '매일'로 바꾼 오답입니다."),
                                opt("반 배정은 나이순으로 한다.", "🔵 파랑 - '수준에 맞는 반'과 반대되는 오답입니다.")
                        ), 0, "🔵 테스트 필요 여부나 요일, 배정 기준을 바꿔 오답을 만듭니다.",
                                "[어학원 마인드맵] 절차(테스트 → 배정) + 시점(매주 월요일). 색칠하세요.")),
                onePassage(PassageCategory.READING, "자격증 시험 공고",
                        "[시험 공고] 이번 회차부터 응시료가 5천 원 인상되어 3만 원이 됩니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("응시료가 3만 원으로 인상됐다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("응시료가 인하됐다.", "🔵 파랑 - '인상'과 반대되는 오답입니다."),
                                opt("응시료는 변동이 없다.", "🔵 파랑 - 명시된 변경과 반대되는 오답입니다."),
                                opt("응시료는 2만 원이다.", "🔵 파랑 - 최종 금액 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 인상/인하를 뒤바꾸거나 최종 금액 숫자를 바꿔 오답을 만듭니다.",
                                "[시험 공고 마인드맵] 변화(+5천 원) → 최종(3만 원). 변화 방향과 결과를 색칠하세요.")),
                onePassage(PassageCategory.READING, "학원 수강 안내",
                        "[수강 안내] 이 강좌는 4주 과정이며, 결석 시 보강 수업을 별도로 신청할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("결석 시 보강 수업 신청이 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("결석하면 보강이 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("강좌는 8주 과정이다.", "🔵 파랑 - '4주'를 '8주'로 바꾼 오답입니다."),
                                opt("보강 수업은 자동으로 배정된다.", "🔵 파랑 - '별도로 신청'과 반대되는 오답입니다.")
                        ), 0, "🔵 보강 가능 여부나 과정 기간, 신청 방식을 바꿔 오답을 만듭니다.",
                                "[수강 안내 마인드맵] 기간(4주) + 결석 시(보강 신청). 색칠하세요.")),
                onePassage(PassageCategory.READING, "튜터링 후기",
                        "[튜터링 후기] 선생님이 제 수준에 맞춰 진도를 조절해 주셔서 부담 없이 배울 수 있었어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("선생님이 진도를 학생 수준에 맞췄다.", "정답: '수준에 맞춰 진도를 조절해 주셨다'고 명시했습니다."),
                                opt("진도가 너무 빨라서 힘들었다.", "🔵 파랑 - '부담 없이 배웠다'와 반대되는 오답입니다."),
                                opt("선생님이 진도 조절을 해 주지 않았다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("정해진 진도표대로만 진행됐다.", "🔵 파랑 - '수준에 맞춰 조절'과 반대되는 오답입니다.")
                        ), 0, "🔵 진도 조절 여부나 학습 부담 정도를 반대로 착각하게 합니다.",
                                "[튜터링 마인드맵] 특징(수준별 조절) → 결과(부담 없음). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "스터디 모집글",
                        "[스터디 모집글] 토익 스터디원을 모집합니다. 목표 점수 900점 이상인 분들만 지원해 주세요.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("목표 점수 900점 이상만 지원할 수 있다.", "정답: 모집글에 그대로 명시되어 있습니다."),
                                opt("점수 제한 없이 누구나 지원 가능하다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("목표 점수는 700점이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("이것은 토플 스터디다.", "🔵 파랑 - '토익'을 '토플'로 바꾼 오답입니다.")
                        ), 0, "🔵 점수 조건이나 시험 종류를 바꿔 오답을 만듭니다.",
                                "[스터디 마인드맵] 시험(토익) + 조건(900점 이상). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "어학원 안내",
                        "[어학원 안내] 결석 3회 이상 시 수료증이 발급되지 않으니 유의하시기 바랍니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("결석 3회 이상이면 수료증을 못 받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("결석해도 수료증은 항상 발급된다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("결석 5회부터 문제가 된다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("수료증은 발급되지 않는다.", "🔵 파랑 - 조건 없이 발급 안 된다고 과장한 오답입니다.")
                        ), 0, "🔵 결석 횟수 기준을 바꾸거나 조건을 빼고 과장합니다.",
                                "[어학원 마인드맵] 조건(결석 3회 이상) → 결과(수료증 미발급). 색칠하세요.")),
                onePassage(PassageCategory.READING, "자격증 시험 공고",
                        "[시험 공고] 합격자 발표는 시험일로부터 2주 후 홈페이지를 통해 공지됩니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("합격자 발표는 시험 2주 후다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("합격자 발표는 시험 당일이다.", "🔵 파랑 - 명시된 시점과 반대되는 오답입니다."),
                                opt("발표는 전화로 개별 안내된다.", "🔵 파랑 - '홈페이지를 통해'와 반대되는 오답입니다."),
                                opt("발표는 1개월 후다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 발표 시점이나 방법, 기간 숫자를 바꿔 오답을 만듭니다.",
                                "[시험 공고 마인드맵] 시점(시험 후 2주) + 방법(홈페이지). 색칠하세요.")),
                onePassage(PassageCategory.READING, "학원 수강 안내",
                        "[수강 안내] 재수강 시 수강료의 20%를 할인해 드립니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("재수강 시 20% 할인받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("재수강은 할인이 안 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("할인율은 50%다.", "🔵 파랑 - 비율 숫자를 바꾼 오답입니다."),
                                opt("신규 수강생만 할인받는다.", "🔵 파랑 - '재수강 시'라는 조건과 반대되는 오답입니다.")
                        ), 0, "🔵 할인율 숫자나 할인 대상(재수강/신규)을 바꿔 오답을 만듭니다.",
                                "[수강 안내 마인드맵] 조건(재수강) → 혜택(20% 할인). 조건과 숫자를 색칠하세요.")),
                onePassage(PassageCategory.READING, "튜터링 후기",
                        "[튜터링 후기] 예약 취소가 자유로워서 바쁜 직장인인 저에게 딱 맞았어요.",
                        q("이 후기를 쓴 사람이 만족한 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("예약 취소가 자유로워서", "정답: '예약 취소가 자유로워서'라고 명시했습니다."),
                                opt("가격이 저렴해서", "🔵 파랑 - 언급되지 않은 이유입니다."),
                                opt("선생님이 유명해서", "🔵 파랑 - 언급되지 않은 이유입니다."),
                                opt("교재가 좋아서", "🔵 파랑 - 언급되지 않은 이유입니다.")
                        ), 0, "🔵 실제 언급된 이유와 무관한 다른 이유로 오답을 만듭니다.",
                                "[튜터링 마인드맵] 특징(자유로운 취소) → 대상(바쁜 직장인). 이유를 확인하세요.")),
                onePassage(PassageCategory.READING, "스터디 모집글",
                        "[스터디 모집글] 매주 토요일 오전 10시, 스터디 카페에서 오프라인으로 진행합니다.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("스터디는 오프라인으로 진행된다.", "정답: '스터디 카페에서 오프라인으로 진행한다'고 명시했습니다."),
                                opt("스터디는 온라인으로 진행된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("모임 시간은 오후 10시다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다."),
                                opt("모임은 매일 진행된다.", "🔵 파랑 - '매주 토요일'을 '매일'로 바꾼 오답입니다.")
                        ), 0, "🔵 온/오프라인 여부나 시간대, 빈도를 바꿔 오답을 만듭니다.",
                                "[스터디 마인드맵] 방식(오프라인) + 시간(토요일 오전 10시). 색칠하세요."))
        );

        List<PassageSeed> reading6th31to40 = List.of(
                onePassage(PassageCategory.READING, "어학원 안내",
                        "[어학원 안내] 조기 등록 시 교재비를 무료로 제공하며, 등록 마감은 개강 3일 전입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("조기 등록 시 교재비가 무료다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("교재비는 항상 유료다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("등록 마감은 개강 당일이다.", "🔵 파랑 - '개강 3일 전'과 반대되는 오답입니다."),
                                opt("늦게 등록해도 교재비가 무료다.", "🔵 파랑 - '조기 등록 시'라는 조건과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(조기 등록)을 빼고 읽거나 마감 시점을 바꿔 오답을 만듭니다.",
                                "[어학원 마인드맵] 조건(조기 등록) → 혜택(교재비 무료) + 마감(개강 3일 전). 색칠하세요.")),
                onePassage(PassageCategory.READING, "자격증 시험 공고",
                        "[시험 공고] 시험 당일 신분증 미지참 시 응시가 제한되니 반드시 지참하시기 바랍니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신분증이 없으면 응시가 제한된다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("신분증이 없어도 응시할 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("신분증은 시험 후에 확인한다.", "🔵 파랑 - '시험 당일'이라는 시점과 다른 오답입니다."),
                                opt("신분증 없이도 재응시가 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 신분증 필요 여부를 반대로 착각하게 하거나 언급되지 않은 내용을 추가합니다.",
                                "[시험 공고 마인드맵] 조건(신분증 지참) → 결과(응시 가능). 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "학원 수강 안내",
                        "[수강 안내] 온라인과 오프라인 수업을 병행할 수 있으며, 추가 비용은 없습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("온오프라인 병행 시 추가 비용이 없다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("온오프라인 병행은 불가능하다.", "🔵 파랑 - '병행할 수 있다'와 반대되는 오답입니다."),
                                opt("병행하려면 추가 비용을 내야 한다.", "🔵 파랑 - '추가 비용은 없다'와 반대되는 오답입니다."),
                                opt("온라인 수업만 가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 병행 가능 여부나 추가 비용 유무를 반대로 착각하게 합니다.",
                                "[수강 안내 마인드맵] 방식(온+오프 병행) + 비용(추가 없음). 색칠하세요.")),
                onePassage(PassageCategory.READING, "튜터링 후기",
                        "[튜터링 후기] 처음엔 화상 수업이 어색했는데 몇 번 하다 보니 대면 수업 못지않게 좋았어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("처음엔 어색했지만 점점 좋아졌다.", "정답: '처음엔 어색했는데 좋았다'는 변화가 나타납니다."),
                                opt("처음부터 매우 만족스러웠다.", "🔵 파랑 - '처음엔 어색했다'는 내용을 빼고 읽게 합니다."),
                                opt("끝까지 어색하고 불편했다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("대면 수업이 훨씬 나았다.", "🔵 파랑 - '못지않게 좋았다'와 다른 오답입니다.")
                        ), 0, "🔵 초반 감정을 빼고 읽거나 최종 평가를 반대로 착각하게 합니다.",
                                "[튜터링 마인드맵] 처음(어색함) → 화살표 → 나중(만족). 변화 방향을 색칠하세요.")),
                onePassage(PassageCategory.READING, "스터디 모집글",
                        "[스터디 모집글] 코딩 스터디원을 모집합니다. 초보자도 환영하며, 매주 과제를 함께 리뷰합니다.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초보자도 참여할 수 있다.", "정답: '초보자도 환영한다'고 명시했습니다."),
                                opt("경력자만 참여할 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("과제 없이 진행된다.", "🔵 파랑 - '매주 과제를 리뷰한다'와 반대되는 오답입니다."),
                                opt("한 달에 한 번 모인다.", "🔵 파랑 - '매주'를 '한 달에 한 번'으로 바꾼 오답입니다.")
                        ), 0, "🔵 참여 대상이나 과제 유무, 빈도를 반대로 착각하게 합니다.",
                                "[스터디 마인드맵] 대상(초보자 환영) + 활동(매주 과제 리뷰). 색칠하세요.")),
                onePassage(PassageCategory.READING, "어학원 안내",
                        "[어학원 안내] 원어민 선생님과 한국인 선생님의 수업을 각각 선택할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("원어민과 한국인 선생님 중 선택할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("원어민 선생님만 있다.", "🔵 파랑 - '한국인 선생님'이라는 선택지를 빼고 읽게 합니다."),
                                opt("선생님을 선택할 수 없다.", "🔵 파랑 - '각각 선택할 수 있다'와 반대되는 오답입니다."),
                                opt("한국인 선생님만 있다.", "🔵 파랑 - '원어민 선생님'이라는 선택지를 빼고 읽게 합니다.")
                        ), 0, "🔵 두 선택지 중 하나만 인정하거나 선택 가능 여부를 반대로 착각하게 합니다.",
                                "[어학원 마인드맵] 선택지1(원어민) + 선택지2(한국인). 둘 다 정답에 포함됩니다.")),
                onePassage(PassageCategory.READING, "자격증 시험 공고",
                        "[시험 공고] 이번 시험부터 컴퓨터 기반 시험(CBT)으로 전환되어 시험 결과를 즉시 확인할 수 있습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("시험 결과를 즉시 확인할 수 있다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("결과는 한 달 후에 나온다.", "🔵 파랑 - '즉시'와 반대되는 오답입니다."),
                                opt("여전히 종이 시험으로 진행된다.", "🔵 파랑 - 'CBT로 전환'과 반대되는 오답입니다."),
                                opt("결과 확인이 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 결과 확인 시점이나 시험 방식(종이/컴퓨터)을 바꿔 오답을 만듭니다.",
                                "[시험 공고 마인드맵] 방식(CBT) → 결과(즉시 확인). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "학원 수강 안내",
                        "[수강 안내] 수강 신청 취소는 개강 후 7일 이내에만 가능하며, 이후에는 환불되지 않습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("개강 후 7일 이내에만 취소할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("언제든지 취소하고 환불받을 수 있다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("개강 후 30일 이내에 취소 가능하다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다."),
                                opt("취소는 아예 불가능하다.", "🔵 파랑 - '7일 이내에는 가능하다'와 반대되는 과장된 오답입니다.")
                        ), 0, "🔵 취소 가능 기한을 바꾸거나 아예 불가능하다고 과장합니다.",
                                "[수강 안내 마인드맵] 기한(7일 이내) + 이후(환불 불가). 색칠하세요.")),
                onePassage(PassageCategory.READING, "튜터링 후기",
                        "[튜터링 후기] 숙제 피드백이 꼼꼼해서 제가 어떤 부분이 부족한지 정확히 알 수 있었어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("숙제 피드백이 꼼꼼했다.", "정답: '피드백이 꼼꼼했다'고 명시했습니다."),
                                opt("숙제 피드백이 부실했다.", "🔵 파랑 - 후기 내용과 반대되는 오답입니다."),
                                opt("숙제가 아예 없었다.", "🔵 파랑 - '숙제 피드백'이 있었다는 내용과 반대됩니다."),
                                opt("부족한 부분을 알 수 없었다.", "🔵 파랑 - '정확히 알 수 있었다'와 반대되는 오답입니다.")
                        ), 0, "🔵 피드백 품질을 반대로 착각하게 하거나 숙제 유무를 반대로 왜곡합니다.",
                                "[튜터링 마인드맵] 특징(꼼꼼한 피드백) → 결과(약점 파악). 색칠하세요.")),
                onePassage(PassageCategory.READING, "스터디 모집글",
                        "[스터디 모집글] 독서 스터디원을 모집합니다. 한 달에 책 두 권을 읽고 토론합니다.",
                        q("이 모집글의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("한 달에 책 두 권을 읽는다.", "정답: 모집글에 그대로 명시되어 있습니다."),
                                opt("한 달에 책 한 권을 읽는다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("일주일에 책 두 권을 읽는다.", "🔵 파랑 - 기간 단위를 바꾼 오답입니다."),
                                opt("책을 읽지 않고 토론만 한다.", "🔵 파랑 - '책을 읽고 토론한다'와 반대되는 오답입니다.")
                        ), 0, "🔵 권수나 기간 단위를 바꾸거나 독서 활동 자체를 빼고 읽게 합니다.",
                                "[스터디 마인드맵] 기간(한 달) + 권수(두 권). 숫자와 단위를 색칠하세요."))
        );

        List<PassageSeed> listening7th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "명절 음식 준비",
                        "여자: 이번 추석에 전 부치는 거 도와줄래요?\n남자: 네, 몇 시부터 시작해요?",
                        q("남자가 도와주려는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("전 부치기", "정답: '전 부치는 거 도와줄래요'라는 요청에 답했습니다."),
                                opt("송편 빚기", "🔵 파랑 - 언급되지 않은 음식입니다."),
                                opt("나물 무치기", "🔵 파랑 - 언급되지 않은 음식입니다."),
                                opt("설거지하기", "🔵 파랑 - 언급되지 않은 일입니다.")
                        ), 0, "🔵 실제 언급된 음식과 무관한 다른 명절 음식으로 오답을 만듭니다.",
                                "[명절 마인드맵] 요청(전 부치기) + 응답(시간 확인). 음식명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "부모님 생신 준비",
                        "남자: 어머니 생신 선물 뭘로 할까요?\n여자: 예전부터 갖고 싶어 하시던 목도리 어때요?",
                        q("여자가 제안한 선물로 알맞은 것을 고르십시오.", List.of(
                                opt("목도리", "정답: '목도리 어때요'라고 제안했습니다."),
                                opt("가방", "🟢 초록 - 언급되지 않은 선물입니다."),
                                opt("꽃다발", "🟢 초록 - 언급되지 않은 선물입니다."),
                                opt("케이크", "🟢 초록 - 언급되지 않은 선물입니다.")
                        ), 0, "🟢 실제 제안된 선물과 무관한 다른 물건으로 오답을 만듭니다.",
                                "[생신 마인드맵] 대상(어머니) + 선물(목도리). 선물명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "집들이 준비",
                        "여자: 집들이 음식 몇 명 분으로 준비해요?\n남자: 여덟 명 온다고 했으니까 넉넉하게 준비해요.",
                        q("집들이에 초대된 인원수로 알맞은 것을 고르십시오.", List.of(
                                opt("여덟 명", "정답: '여덟 명 온다고 했다'고 답했습니다."),
                                opt("네 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("열두 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("인원이 정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 인원수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[집들이 마인드맵] 질문(인원수) → 답(여덟 명). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "파티 준비",
                        "남자: 생일 파티 풍선 장식은 제가 할게요.\n여자: 좋아요, 저는 케이크를 준비할게요.",
                        q("여자가 준비하기로 한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("케이크", "정답: '저는 케이크를 준비할게요'라고 답했습니다."),
                                opt("풍선 장식", "🟢 초록 - 남자가 맡은 일과 헷갈리게 하는 오답입니다."),
                                opt("음료수", "🟢 초록 - 언급되지 않은 준비물입니다."),
                                opt("선물", "🟢 초록 - 언급되지 않은 준비물입니다.")
                        ), 0, "🟢 남자와 여자가 맡은 역할을 혼동하게 합니다.",
                                "[파티 마인드맵] 남자(풍선) ↔ 여자(케이크). 화자별로 역할을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "손님 초대",
                        "여자: 손님들 오시면 슬리퍼부터 챙겨 드려야겠어요.\n남자: 맞아요, 현관에 미리 꺼내 놓을게요.",
                        q("남자가 하려는 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("현관에 슬리퍼 꺼내 놓기", "정답: '현관에 미리 꺼내 놓을게요'라고 답했습니다."),
                                opt("손님에게 전화하기", "🟢 초록 - 언급되지 않은 행동입니다."),
                                opt("음식을 데우기", "🟢 초록 - 언급되지 않은 행동입니다."),
                                opt("청소하기", "🟢 초록 - 언급되지 않은 행동입니다.")
                        ), 0, "🟢 실제 언급된 행동과 무관한 다른 준비 행동으로 오답을 만듭니다.",
                                "[손님 초대 마인드맵] 목적(슬리퍼 준비) → 장소(현관). 장소와 행동을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "명절 음식 준비",
                        "남자: 명절 음식 재료는 다 샀어요?\n여자: 아니요, 나물이랑 고기가 부족해서 더 사야 해요.",
                        q("더 사야 하는 재료로 알맞은 것을 고르십시오.", List.of(
                                opt("나물과 고기", "정답: '나물이랑 고기가 부족하다'고 답했습니다."),
                                opt("과일과 떡", "🔵 파랑 - 언급되지 않은 재료입니다."),
                                opt("생선과 계란", "🔵 파랑 - 언급되지 않은 재료입니다."),
                                opt("재료를 다 샀다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 재료와 무관한 다른 재료로 오답을 만듭니다.",
                                "[명절 마인드맵] 부족한 재료(나물+고기). 재료명 두 개를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "부모님 생신 준비",
                        "여자: 아버지 생신 케이크는 어떤 걸로 주문했어요?\n남자: 아버지가 좋아하시는 초코 케이크로 했어요.",
                        q("주문한 케이크의 맛으로 알맞은 것을 고르십시오.", List.of(
                                opt("초코", "정답: '초코 케이크로 했다'고 답했습니다."),
                                opt("딸기", "🔵 파랑 - '초코'를 '딸기'로 바꾼 오답입니다."),
                                opt("치즈", "🔵 파랑 - 언급되지 않은 맛입니다."),
                                opt("케이크를 주문하지 않았다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 맛과 무관한 다른 맛으로 오답을 만듭니다.",
                                "[생신 마인드맵] 대상(아버지) + 선호(초코). 맛 이름에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "집들이 준비",
                        "남자: 집들이 선물로 뭘 사 가면 좋을까요?\n여자: 세제나 휴지 같은 생활용품이 무난해요.",
                        q("여자가 추천한 집들이 선물로 알맞은 것을 고르십시오.", List.of(
                                opt("세제나 휴지 같은 생활용품", "정답: 대화에 그대로 나와 있습니다."),
                                opt("꽃다발", "🟢 초록 - 언급되지 않은 선물입니다."),
                                opt("술", "🟢 초록 - 언급되지 않은 선물입니다."),
                                opt("현금", "🟢 초록 - 언급되지 않은 선물입니다.")
                        ), 0, "🟢 실제 추천된 선물과 무관한 다른 물건으로 오답을 만듭니다.",
                                "[집들이 마인드맵] 추천(생활용품: 세제, 휴지). 두 가지 예시를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "파티 준비",
                        "여자: 파티 음악은 누가 준비해요?\n남자: 제가 미리 플레이리스트 만들어 놨어요.",
                        q("남자가 이미 해 놓은 일로 알맞은 것을 고르십시오.", List.of(
                                opt("플레이리스트 만들기", "정답: '미리 플레이리스트 만들어 놨다'고 답했습니다."),
                                opt("스피커 대여하기", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("음악을 아직 준비 안 했다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("DJ를 섭외했다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 '미리'라는 완료 표현을 놓치고 아직 안 한 일로 착각하게 합니다.",
                                "[파티 마인드맵] 완료(플레이리스트 제작). '미리 만들어 놨다'는 과거완료 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "손님 초대",
                        "남자: 손님들 몇 시에 오신다고 했어요?\n여자: 6시에 오신다고 했으니까 5시 반까지는 준비를 끝내야 해요.",
                        q("손님이 도착하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("6시", "정답: '6시에 오신다고 했다'고 답했습니다."),
                                opt("5시 반", "🔴 빨강 - 준비 완료 목표 시간과 헷갈리게 하는 오답입니다."),
                                opt("7시", "🔴 빨강 - 대화에 없는 시간입니다."),
                                opt("시간이 정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 손님 도착 시간과 준비 완료 목표 시간을 혼동하게 합니다.",
                                "[손님 초대 마인드맵] 도착(6시) vs 준비 완료 목표(5시 반). 두 시간을 색으로 구분하세요."))
        );

        List<PassageSeed> listening7th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "명절 음식 준비",
                        "여자: 이번엔 만두를 좀 많이 빚어야겠어요.\n남자: 그래요, 몇 개 정도 빚을까요?\n여자: 백 개 정도는 빚어야 할 것 같아요.",
                        q("빚으려는 만두의 개수로 알맞은 것을 고르십시오.", List.of(
                                opt("백 개 정도", "정답: '백 개 정도는 빚어야 할 것 같다'고 답했습니다."),
                                opt("오십 개 정도", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("이백 개 정도", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("만두를 빚지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 개수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[명절 마인드맵] 음식(만두) + 개수(백 개). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "부모님 생신 준비",
                        "남자: 부모님 생신 자리는 식당으로 예약했어요?\n여자: 네, 룸으로 예약해서 편하게 식사하실 수 있을 거예요.",
                        q("여자가 예약한 자리 형태로 알맞은 것을 고르십시오.", List.of(
                                opt("룸", "정답: '룸으로 예약했다'고 답했습니다."),
                                opt("홀 좌석", "🔴 빨강 - '룸'과 반대되는 오답입니다."),
                                opt("야외 자리", "🔴 빨강 - 언급되지 않은 자리입니다."),
                                opt("예약하지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 예약한 자리 형태와 반대되거나 무관한 형태로 오답을 만듭니다.",
                                "[생신 마인드맵] 예약(식당) + 형태(룸). 자리 종류에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "집들이 준비",
                        "여자: 집들이 때 음악은 어떤 걸 틀까요?\n남자: 잔잔한 재즈 음악이 분위기에 좋을 것 같아요.",
                        q("남자가 추천한 음악 장르로 알맞은 것을 고르십시오.", List.of(
                                opt("재즈", "정답: '잔잔한 재즈 음악이 좋을 것 같다'고 답했습니다."),
                                opt("록", "🔵 파랑 - 언급되지 않은 장르입니다."),
                                opt("클래식", "🔵 파랑 - 언급되지 않은 장르입니다."),
                                opt("음악을 틀지 않는다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 장르와 무관한 다른 장르로 오답을 만듭니다.",
                                "[집들이 마인드맵] 추천(재즈) + 분위기(잔잔함). 장르명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "파티 준비",
                        "남자: 파티 초대장은 다 보냈어요?\n여자: 아직 두 명한테는 못 보냈어요. 오늘 중으로 보낼게요.",
                        q("여자가 아직 초대장을 못 보낸 인원으로 알맞은 것을 고르십시오.", List.of(
                                opt("두 명", "정답: '두 명한테는 못 보냈다'고 답했습니다."),
                                opt("한 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("다섯 명", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("모두 다 보냈다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 아직 완료되지 않은 인원수를 바꾸거나 전부 완료로 왜곡합니다.",
                                "[파티 마인드맵] 완료(대부분) + 미완료(두 명). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "손님 초대",
                        "여자: 손님들이 매운 음식을 잘 못 드신대요.\n남자: 그럼 맵지 않은 메뉴로 다시 준비해야겠네요.",
                        q("남자가 하려는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("맵지 않은 메뉴로 다시 준비하기", "정답: '맵지 않은 메뉴로 다시 준비해야겠다'고 답했습니다."),
                                opt("매운 음식을 그대로 준비하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("손님들에게 양해를 구하기", "🟢 초록 - 언급되지 않은 행동입니다."),
                                opt("음식을 아예 준비하지 않기", "🟢 초록 - 언급되지 않은 행동입니다.")
                        ), 0, "🟢 실제 반응(메뉴 변경)과 반대되거나 무관한 행동으로 오답을 만듭니다.",
                                "[손님 초대 마인드맵] 정보(매운 음식 못 먹음) → 대응(메뉴 변경). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "명절 음식 준비",
                        "남자: 이번 명절엔 갈비찜도 해요?\n여자: 네, 이번엔 갈비찜을 새로 추가했어요.",
                        q("이번 명절에 새로 추가된 음식으로 알맞은 것을 고르십시오.", List.of(
                                opt("갈비찜", "정답: '갈비찜을 새로 추가했다'고 답했습니다."),
                                opt("잡채", "🔵 파랑 - 언급되지 않은 음식입니다."),
                                opt("식혜", "🔵 파랑 - 언급되지 않은 음식입니다."),
                                opt("추가된 음식이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 음식과 무관한 다른 음식으로 오답을 만듭니다.",
                                "[명절 마인드맵] 새로 추가(갈비찜). 음식명에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "부모님 생신 준비",
                        "여자: 부모님께 편지도 같이 드릴까요?\n남자: 좋은 생각이에요. 저도 편지 써 볼게요.",
                        q("남자가 하려는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("편지 쓰기", "정답: '저도 편지 써 볼게요'라고 답했습니다."),
                                opt("사진 인화하기", "🟢 초록 - 언급되지 않은 행동입니다."),
                                opt("동영상 만들기", "🟢 초록 - 언급되지 않은 행동입니다."),
                                opt("편지는 안 쓰기로 했다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 행동과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[생신 마인드맵] 제안(편지) → 동의(남자도 씀). 제안과 동의를 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "집들이 준비",
                        "남자: 집들이 시간은 몇 시부터예요?\n여자: 오후 5시부터인데 편한 시간에 오시면 돼요.",
                        q("집들이가 시작하는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("오후 5시", "정답: '오후 5시부터'라고 답했습니다."),
                                opt("오전 5시", "🔴 빨강 - '오후'를 '오전'으로 바꾼 오답입니다."),
                                opt("오후 7시", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("시간이 정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 오전/오후를 바꾸거나 시간 숫자를 바꿔 오답을 만듭니다.",
                                "[집들이 마인드맵] 시작 시간(오후 5시) + 조건(편한 시간에). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "파티 준비",
                        "여자: 파티 장소는 집이 아니라 다른 곳으로 바꿀까요?\n남자: 아니요, 그냥 집에서 하는 게 편할 것 같아요.",
                        q("파티가 열리는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("집", "정답: '집에서 하는 게 편할 것 같다'고 답했습니다."),
                                opt("식당", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("공원", "🔴 빨강 - 언급되지 않은 장소입니다."),
                                opt("파티 장소를 바꾸기로 했다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 장소 변경 여부를 반대로 착각하게 합니다.",
                                "[파티 마인드맵] 제안(장소 변경) → 거절(그대로 집). '아니요'라는 응답을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "손님 초대",
                        "남자: 손님들 신발장이 좁아서 다 못 넣을 것 같아요.\n여자: 그럼 현관 앞에 신발 정리대를 하나 더 놔요.",
                        q("여자가 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("신발 정리대 추가하기", "정답: '신발 정리대를 하나 더 놓자'고 제안했습니다."),
                                opt("신발장을 새로 사기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("손님들에게 양해 구하기", "🟢 초록 - 언급되지 않은 제안입니다."),
                                opt("신발을 밖에 두기", "🟢 초록 - 언급되지 않은 제안입니다.")
                        ), 0, "🟢 실제 제안(정리대 추가)과 무관한 다른 해결책으로 오답을 만듭니다.",
                                "[손님 초대 마인드맵] 문제(신발장 좁음) → 해결책(정리대 추가). 색으로 연결하세요."))
        );

        List<PassageSeed> reading7th21to30 = List.of(
                onePassage(PassageCategory.READING, "명절 음식 레시피",
                        "[명절 음식 레시피 - 잡채] 1. 당면을 삶아 식힌다. 2. 채소와 고기를 각각 볶는다. 3. 모든 재료를 양념과 함께 버무린다.",
                        q("이 레시피의 조리 순서로 맞는 것을 고르십시오.", List.of(
                                opt("당면 삶기 → 재료 볶기 → 버무리기", "정답: 순서 1, 2, 3번 그대로입니다."),
                                opt("재료 볶기 → 당면 삶기 → 버무리기", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("버무리기 → 당면 삶기 → 재료 볶기", "🔴 빨강 - 마지막 단계를 처음으로 착각하게 합니다."),
                                opt("당면 없이 만든다.", "🔴 빨강 - 재료(당면)를 빠뜨린 오답입니다.")
                        ), 0, "🔴 조리 순서를 뒤바꾸거나 재료를 빠뜨려 오답을 만듭니다.",
                                "[레시피 마인드맵] 1(당면)→2(볶기)→3(버무리기). 숫자 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "생신 축하 카드",
                        "[생신 축하 카드] 아버지, 생신 축하드려요. 항상 건강하시고 저희와 오래오래 함께해 주세요.",
                        q("이 카드의 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("생신을 축하하려고", "정답: '생신 축하드려요'가 카드의 핵심 목적입니다."),
                                opt("여행을 제안하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("사과하려고", "🔵 파랑 - 언급되지 않은 목적입니다."),
                                opt("건강검진을 권유하려고", "🔵 파랑 - 언급되지 않은 목적입니다.")
                        ), 0, "🔵 축하 목적을 무관한 다른 목적으로 착각하게 합니다.",
                                "[카드 마인드맵] 대상(아버지) + 목적(생신 축하). 핵심 문장에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "집들이 초대장",
                        "[집들이 초대장] 이번 주 토요일 오후 5시, 저희 새집으로 초대합니다. 편하게 와 주세요.",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이번 주 토요일 오후 5시에 초대한다.", "정답: 초대장에 그대로 명시되어 있습니다."),
                                opt("다음 주 토요일에 초대한다.", "🔵 파랑 - '이번 주'를 '다음 주'로 바꾼 오답입니다."),
                                opt("오전 5시에 초대한다.", "🔵 파랑 - '오후'를 '오전'으로 바꾼 오답입니다."),
                                opt("정장을 입고 오라고 요청한다.", "🔵 파랑 - '편하게 와 달라'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 날짜나 시간대, 복장 안내를 바꿔 오답을 만듭니다.",
                                "[초대장 마인드맵] 시점(토요일 오후 5시) + 분위기(편하게). 색칠하세요.")),
                onePassage(PassageCategory.READING, "파티 준비물 목록",
                        "[파티 준비물 목록] 풍선, 케이크, 초, 폭죽, 카메라. 폭죽은 실내 사용 가능한 것으로 준비하세요.",
                        q("이 목록의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("폭죽은 실내용으로 준비해야 한다.", "정답: '실내 사용 가능한 것으로 준비하라'고 명시했습니다."),
                                opt("폭죽은 야외용만 가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("케이크는 준비물에 없다.", "🔵 파랑 - 목록에 있는 항목을 없다고 착각하게 합니다."),
                                opt("준비물은 세 가지뿐이다.", "🔵 파랑 - 실제로는 다섯 가지 항목입니다.")
                        ), 0, "🔵 실내/야외 조건을 반대로 착각하게 하거나 항목 개수를 착각하게 합니다.",
                                "[준비물 마인드맵] 목록(5가지) + 조건(폭죽은 실내용). 항목을 하나씩 색칠하세요.")),
                onePassage(PassageCategory.READING, "손님맞이 매너 안내",
                        "[손님맞이 매너 안내] 손님이 도착하면 먼저 따뜻한 차나 음료를 권하는 것이 기본 예의입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("손님에게 먼저 차나 음료를 권해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("음료는 손님이 요청할 때만 준다.", "🔵 파랑 - '먼저 권하는 것이 예의'와 반대되는 오답입니다."),
                                opt("차가운 음료만 제공해야 한다.", "🔵 파랑 - '따뜻한 차나 음료'와 다른 오답입니다."),
                                opt("음료 제공은 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 먼저 권하는 예의를 반대로 착각하게 하거나 음료 종류를 바꿉니다.",
                                "[매너 마인드맵] 순서(도착 → 차/음료 권하기). 순서 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "명절 음식 레시피",
                        "[명절 음식 레시피 - 식혜] 엿기름물을 우린 후 밥과 섞어 6시간 정도 삭힌 다음 끓입니다.",
                        q("이 레시피의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("6시간 정도 삭힌 후 끓인다.", "정답: 레시피에 그대로 명시되어 있습니다."),
                                opt("삭히지 않고 바로 끓인다.", "🔵 파랑 - '삭힌 다음 끓인다'는 순서와 반대되는 오답입니다."),
                                opt("삭히는 시간은 1시간이다.", "🔵 파랑 - 시간 숫자를 바꾼 오답입니다."),
                                opt("엿기름물 없이 만든다.", "🔵 파랑 - 재료를 빠뜨린 오답입니다.")
                        ), 0, "🔵 삭히는 시간 숫자를 바꾸거나 재료·순서를 빠뜨려 오답을 만듭니다.",
                                "[레시피 마인드맵] 재료(엿기름물+밥) → 삭힘(6시간) → 끓이기. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "생신 축하 카드",
                        "[생신 축하 카드] 어머니, 낳아주시고 키워주셔서 감사합니다. 작은 선물이지만 마음을 담았어요.",
                        q("이 카드를 보내는 대상으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("어머니", "정답: '어머니'라는 호칭이 대상을 알려줍니다."),
                                opt("친구", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("선생님", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("직장 동료", "🔵 파랑 - 언급된 관계와 다른 대상입니다.")
                        ), 0, "🔵 호칭을 무시하고 무관한 다른 대상으로 오답을 만듭니다.",
                                "[카드 마인드맵] 호칭(어머니) → 대상 확정. 첫 단어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "집들이 초대장",
                        "[집들이 초대장] 주차 공간이 협소하니 가급적 대중교통을 이용해 주시면 감사하겠습니다.",
                        q("이 초대장의 요청 사항으로 알맞은 것을 고르십시오.", List.of(
                                opt("대중교통 이용하기", "정답: '대중교통을 이용해 달라'고 요청했습니다."),
                                opt("자가용 이용하기", "🔵 파랑 - 명시된 요청과 반대되는 오답입니다."),
                                opt("일찍 도착하기", "🔵 파랑 - 언급되지 않은 요청입니다."),
                                opt("주차장을 예약하기", "🔵 파랑 - 언급되지 않은 요청입니다.")
                        ), 0, "🔵 실제 요청과 반대되거나 무관한 다른 요청으로 오답을 만듭니다.",
                                "[초대장 마인드맵] 이유(주차 협소) → 요청(대중교통). 색으로 연결하세요.")),
                onePassage(PassageCategory.READING, "파티 준비물 목록",
                        "[파티 준비물 목록] 냅킨, 종이컵, 접시, 포크. 인원수에 맞춰 여유 있게 준비하세요.",
                        q("이 목록의 안내로 알맞은 것을 고르십시오.", List.of(
                                opt("인원수보다 여유 있게 준비해야 한다.", "정답: '여유 있게 준비하라'고 명시했습니다."),
                                opt("인원수보다 적게 준비해야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("정확히 인원수만큼만 준비한다.", "🔵 파랑 - '여유 있게'라는 표현과 다른 오답입니다."),
                                opt("준비물 수량은 상관없다.", "🔵 파랑 - 명시된 안내와 반대되는 오답입니다.")
                        ), 0, "🔵 여유분 준비 여부를 반대로 착각하게 합니다.",
                                "[준비물 마인드맵] 기준(인원수) + 방침(여유 있게). 핵심 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "손님맞이 매너 안내",
                        "[손님맞이 매너 안내] 손님이 선물을 주면 그 자리에서 바로 열어보기보다는 나중에 열어보는 것이 예의일 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("선물은 나중에 열어보는 것이 예의일 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("선물은 반드시 그 자리에서 열어야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("선물은 절대 받으면 안 된다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("선물을 준 사람 앞에서 감상평을 말해야 한다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 선물을 여는 시점을 반대로 착각하게 하거나 언급되지 않은 예절을 추가합니다.",
                                "[매너 마인드맵] 선물 받음 → 나중에 개봉(예의). 시점 표현에 밑줄을 그으세요."))
        );

        List<PassageSeed> reading7th31to40 = List.of(
                onePassage(PassageCategory.READING, "명절 음식 레시피",
                        "[명절 음식 레시피 - 갈비찜] 갈비는 찬물에 담가 핏물을 빼는 과정이 냄새를 없애는 데 중요합니다.",
                        q("이 레시피의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("찬물에 담가 핏물을 빼야 한다.", "정답: 레시피에 그대로 명시되어 있습니다."),
                                opt("뜨거운 물에 담가야 한다.", "🔵 파랑 - '찬물'을 '뜨거운 물'로 바꾼 오답입니다."),
                                opt("핏물을 뺄 필요가 없다.", "🔵 파랑 - '중요하다'는 내용과 반대되는 오답입니다."),
                                opt("이 과정은 맛과 무관하다.", "🔵 파랑 - '냄새를 없애는 데 중요하다'와 반대되는 오답입니다.")
                        ), 0, "🔵 물의 온도를 바꾸거나 과정의 필요성·목적을 반대로 착각하게 합니다.",
                                "[레시피 마인드맵] 과정(찬물에 핏물 빼기) → 목적(냄새 제거). 색칠하세요.")),
                onePassage(PassageCategory.READING, "생신 축하 카드",
                        "[생신 축하 카드] 할머니, 백 세까지 건강하게 저희 곁에 계셔 주세요. 사랑합니다.",
                        q("이 카드를 보내는 대상으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("할머니", "정답: '할머니'라는 호칭이 대상을 알려줍니다."),
                                opt("이모", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("사촌", "🔵 파랑 - 언급된 관계와 다른 대상입니다."),
                                opt("옆집 어른", "🔵 파랑 - 언급된 관계와 다른 대상입니다.")
                        ), 0, "🔵 호칭을 무시하고 무관한 다른 대상으로 오답을 만듭니다.",
                                "[카드 마인드맵] 호칭(할머니) → 대상 확정. 첫 단어를 확인하세요.")),
                onePassage(PassageCategory.READING, "집들이 초대장",
                        "[집들이 초대장] 아이 동반 가능하며, 놀이방을 따로 마련해 두었습니다.",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("아이를 데려와도 된다.", "정답: '아이 동반 가능하다'고 명시했습니다."),
                                opt("아이는 동반할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("놀이방이 따로 없다.", "🔵 파랑 - '놀이방을 마련했다'와 반대되는 오답입니다."),
                                opt("어른만 참석 가능하다.", "🔵 파랑 - '아이 동반 가능'과 반대되는 오답입니다.")
                        ), 0, "🔵 아이 동반 가능 여부나 놀이방 유무를 반대로 착각하게 합니다.",
                                "[초대장 마인드맵] 허용(아이 동반) + 준비(놀이방). 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "파티 준비물 목록",
                        "[파티 준비물 목록] 생일 모자, 폭죽, 카메라. 촛불은 안전을 위해 성인이 관리해 주세요.",
                        q("이 목록의 안내로 알맞은 것을 고르십시오.", List.of(
                                opt("촛불은 성인이 관리해야 한다.", "정답: '성인이 관리해 달라'고 명시했습니다."),
                                opt("촛불은 아이가 관리해도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("촛불은 준비물에 없다.", "🔵 파랑 - 언급된 항목(촛불)을 없다고 착각하게 합니다."),
                                opt("안전 수칙이 필요 없다.", "🔵 파랑 - 명시된 안내와 반대되는 오답입니다.")
                        ), 0, "🔵 관리 주체(성인/아이)를 바꾸거나 안전 수칙 필요성을 반대로 착각하게 합니다.",
                                "[준비물 마인드맵] 물건(촛불) + 관리 주체(성인). 안전 수칙에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "손님맞이 매너 안내",
                        "[손님맞이 매너 안내] 손님이 돌아갈 때는 문 앞까지 배웅하며 인사하는 것이 좋습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("문 앞까지 배웅하는 것이 좋다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("배웅은 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("현관 안에서만 인사하면 된다.", "🔵 파랑 - '문 앞까지'라는 내용과 반대되는 오답입니다."),
                                opt("손님이 스스로 나가야 한다.", "🔵 파랑 - 배웅을 하라는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 배웅 필요성이나 배웅 범위를 반대로 착각하게 합니다.",
                                "[매너 마인드맵] 손님 귀가 → 배웅(문 앞까지). 범위 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "명절 음식 레시피",
                        "[명절 음식 레시피 - 나물] 나물은 데친 후 찬물에 바로 헹궈야 색이 예쁘게 유지됩니다.",
                        q("이 레시피의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("데친 후 바로 찬물에 헹궈야 한다.", "정답: 레시피에 그대로 명시되어 있습니다."),
                                opt("데친 후 그대로 두면 된다.", "🔵 파랑 - '바로 찬물에 헹궈야'라는 내용과 반대되는 오답입니다."),
                                opt("뜨거운 물에 헹궈야 한다.", "🔵 파랑 - '찬물'을 '뜨거운 물'로 바꾼 오답입니다."),
                                opt("헹구는 과정은 색과 무관하다.", "🔵 파랑 - '색이 예쁘게 유지된다'는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 헹구는 물의 온도나 과정의 목적을 반대로 착각하게 합니다.",
                                "[레시피 마인드맵] 과정(데치기 → 찬물 헹굼) → 목적(색 유지). 색칠하세요.")),
                onePassage(PassageCategory.READING, "생신 축하 카드",
                        "[생신 축하 카드] 여보, 결혼기념일 축하해요. 앞으로도 함께 행복하게 지내요.",
                        q("이 카드가 축하하는 날로 알맞은 것을 고르십시오.", List.of(
                                opt("결혼기념일", "정답: '결혼기념일 축하해요'라고 명시했습니다."),
                                opt("생일", "🔵 파랑 - '결혼기념일'을 '생일'로 바꾼 오답입니다."),
                                opt("졸업식", "🔵 파랑 - 언급되지 않은 날입니다."),
                                opt("입사 기념일", "🔵 파랑 - 언급되지 않은 날입니다.")
                        ), 0, "🔵 축하하는 날의 종류를 바꿔 오답을 만듭니다.",
                                "[카드 마인드맵] 대상(여보) + 날(결혼기념일). 핵심 단어를 확인하세요.")),
                onePassage(PassageCategory.READING, "집들이 초대장",
                        "[집들이 초대장] 알레르기가 있으신 분은 미리 알려주시면 음식을 따로 준비하겠습니다.",
                        q("이 초대장의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("알레르기가 있으면 미리 알려야 한다.", "정답: 초대장에 그대로 명시되어 있습니다."),
                                opt("알레르기 관련 안내는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("모든 음식은 미리 정해져 있다.", "🔵 파랑 - '따로 준비하겠다'는 유연성과 반대되는 오답입니다."),
                                opt("알레르기가 있으면 참석할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 과장된 오답입니다.")
                        ), 0, "🔵 사전 안내 필요성을 반대로 착각하게 하거나 참석 제한으로 과장합니다.",
                                "[초대장 마인드맵] 조건(알레르기 사전 안내) → 배려(별도 준비). 색칠하세요.")),
                onePassage(PassageCategory.READING, "파티 준비물 목록",
                        "[파티 준비물 목록] 테이블보, 장식 리본, 이름표. 이름표는 참석자 명단을 확인 후 미리 작성해 두세요.",
                        q("이 목록의 안내로 알맞은 것을 고르십시오.", List.of(
                                opt("이름표는 명단 확인 후 미리 작성해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("이름표는 당일 현장에서 작성한다.", "🔵 파랑 - '미리 작성해 두라'는 내용과 반대되는 오답입니다."),
                                opt("이름표는 준비할 필요가 없다.", "🔵 파랑 - 목록에 있는 항목과 반대되는 오답입니다."),
                                opt("테이블보는 준비물에 없다.", "🔵 파랑 - 목록에 있는 항목을 없다고 착각하게 합니다.")
                        ), 0, "🔵 작성 시점(사전/당일)을 바꾸거나 목록 항목 유무를 반대로 착각하게 합니다.",
                                "[준비물 마인드맵] 순서(명단 확인 → 이름표 작성). 순서 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "손님맞이 매너 안내",
                        "[손님맞이 매너 안내] 대화 중 상대방의 말을 끊지 않고 끝까지 듣는 태도가 중요합니다.",
                        q("이 안내문에서 강조하는 태도로 알맞은 것을 고르십시오.", List.of(
                                opt("상대방의 말을 끝까지 듣기", "정답: '끝까지 듣는 태도가 중요하다'고 명시했습니다."),
                                opt("빨리 말을 끝내기", "🔵 파랑 - 언급되지 않은 태도입니다."),
                                opt("상대방의 말을 자주 끊기", "🔵 파랑 - '끊지 않고'와 반대되는 오답입니다."),
                                opt("대화를 하지 않기", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 끊지 않고 듣는 태도를 반대로 착각하게 합니다.",
                                "[매너 마인드맵] 태도(끝까지 경청). '끊지 않고'라는 표현에 형광펜을 칠하세요."))
        );

        return new WeekSeed("1~2급 컬러맵 실전 감각",
                "디지털 일상과 실전 감각을 색깔 코딩과 마인드맵으로 익힌다.",
                WEEK3_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(인터넷 쇼핑, 배달앱 이용, SNS 소통, 온라인 강의, 화상 통화) + 읽기 20(앱 사용 후기, SNS 게시물, 온라인 강의 안내, 화상회의 초대장, 쇼핑몰 이벤트). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening1to10, listening11to20, reading21to30, reading31to40)),
                        day("2차(40문항) - 듣기 20(계절 옷차림, 미세먼지 대응, 실내 활동, 야외 활동, 날씨별 준비물) + 읽기 20(날씨 예보, 계절별 건강수칙, 실내활동 안내, 야외활동 주의사항, 옷차림 가이드). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening2nd1to10, listening2nd11to20, reading2nd21to30, reading2nd31to40)),
                        day("3차(40문항) - 듣기 20(세금 신고, 보험 가입, 연금 안내, 저축 상담, 카드 발급) + 읽기 20(세금 고지서, 보험 약관 요약, 연금 안내서, 저축 상품 안내, 신용카드 안내문). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening3rd1to10, listening3rd11to20, reading3rd21to30, reading3rd31to40)),
                        day("4차(40문항) - 듣기 20(반려식물 키우기, 인테리어, 가전제품 구매, 중고거래, 재활용) + 읽기 20(반려식물 관리법, 인테리어 후기, 가전제품 사용설명서, 중고거래 게시글, 분리수거 안내문). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening4th1to10, listening4th11to20, reading4th21to30, reading4th31to40)),
                        day("5차(40문항) - 듣기 20(스포츠 경기 관람, 콘서트 관람, 전시회 관람, 영화 관람, 공연 예매) + 읽기 20(경기 결과 기사, 콘서트 후기, 전시회 안내, 영화 리뷰, 공연 예매 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening5th1to10, listening5th11to20, reading5th21to30, reading5th31to40)),
                        day("6차(40문항) - 듣기 20(어학 공부, 자격증 시험, 취미 학원, 온라인 튜터링, 스터디 모임) + 읽기 20(어학원 안내, 자격증 시험 공고, 학원 수강 안내, 튜터링 후기, 스터디 모집글). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening6th1to10, listening6th11to20, reading6th21to30, reading6th31to40)),
                        day("7차(40문항) - 듣기 20(명절 음식 준비, 부모님 생신 준비, 집들이 준비, 파티 준비, 손님 초대) + 읽기 20(명절 음식 레시피, 생신 축하 카드, 집들이 초대장, 파티 준비물 목록, 손님맞이 매너 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening7th1to10, listening7th11to20, reading7th21to30, reading7th31to40))
                ));
    }

    // ===================== WEEK 4: 1~2급 컬러맵 마무리 =====================

    private static final String WEEK4_ANSWER_NOTE_TEMPLATE = """
            [🎨 오답 노트 템플릿 - WEEK4용]
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

    private WeekSeed week4() {
        List<PassageSeed> listening1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "자동차 정비",
                        "여자: 자동차 엔진오일 언제 갈았어요?\n남자: 지난달에 갈아서 아직 괜찮아요.",
                        q("남자가 엔진오일을 교체한 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("지난달", "정답: '지난달에 갈았다'고 답했습니다."),
                                opt("어제", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("작년", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("아직 안 갈았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시점 표현을 슬쩍 바꿔 오답을 만듭니다.",
                                "[정비 마인드맵] 작업(엔진오일 교체) + 시점(지난달). 시점 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "운전면허 시험",
                        "남자: 운전면허 필기시험은 몇 점 넘어야 합격이에요?\n여자: 70점 이상 받으면 합격이에요.",
                        q("운전면허 필기시험 합격 기준 점수로 알맞은 것을 고르십시오.", List.of(
                                opt("70점 이상", "정답: '70점 이상 받으면 합격'이라고 답했습니다."),
                                opt("60점 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("90점 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("점수 기준이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 합격 기준 점수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[면허 마인드맵] 시험(필기) + 기준(70점 이상). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "주차 문제",
                        "여자: 이 근처에 주차할 곳이 없어요.\n남자: 저기 공영주차장이 있는데 거기로 가 봐요.",
                        q("남자가 제안한 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("공영주차장", "정답: '공영주차장이 있는데 거기로 가 보자'고 제안했습니다."),
                                opt("사설주차장", "🔵 파랑 - '공영'을 '사설'로 바꾼 오답입니다."),
                                opt("길거리 주차", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("주차할 필요가 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 주차장 종류를 슬쩍 바꿔 오답을 만듭니다.",
                                "[주차 마인드맵] 문제(주차 공간 없음) → 제안(공영주차장). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "교통 법규",
                        "남자: 이 길은 왜 이렇게 차가 안 다녀요?\n여자: 여기 일방통행이라서 이쪽으로는 못 들어와요.",
                        q("이 도로의 특징으로 알맞은 것을 고르십시오.", List.of(
                                opt("일방통행이다.", "정답: '일방통행이라서 못 들어온다'고 답했습니다."),
                                opt("양방향 통행이다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("차량 통행이 전면 금지다.", "🔵 파랑 - '이쪽으로는'이라는 방향 제한을 과장한 오답입니다."),
                                opt("공사 중이다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 통행 방향 제한을 반대로 착각하게 하거나 전면 금지로 과장합니다.",
                                "[교통법규 마인드맵] 도로(일방통행) → 제한(한쪽 방향만). 방향 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "대중교통 민원",
                        "여자: 버스가 배차 시간보다 너무 늦게 와요.\n남자: 그럼 시청 홈페이지에 민원을 접수해 보세요.",
                        q("남자가 여자에게 제안한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("시청 홈페이지에 민원 접수하기", "정답: '시청 홈페이지에 민원을 접수해 보라'고 제안했습니다."),
                                opt("버스 회사에 전화하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("경찰서에 신고하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("그냥 참고 기다리기", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 제안(민원 접수)과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[민원 마인드맵] 문제(배차 지연) → 제안(민원 접수). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "자동차 정비",
                        "남자: 타이어 마모가 심한 것 같은데 교체해야 할까요?\n여자: 네, 마모선까지 닳았으면 바로 교체하는 게 안전해요.",
                        q("여자가 타이어 교체를 권한 기준으로 알맞은 것을 고르십시오.", List.of(
                                opt("마모선까지 닳았을 때", "정답: '마모선까지 닳았으면 바로 교체하라'고 답했습니다."),
                                opt("1년에 한 번씩", "🔵 파랑 - 언급되지 않은 기준입니다."),
                                opt("주행거리 1만km마다", "🔵 파랑 - 언급되지 않은 기준입니다."),
                                opt("교체할 필요가 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 기준과 무관한 다른 기준으로 오답을 만듭니다.",
                                "[정비 마인드맵] 상태(마모선까지 닳음) → 조치(교체). 기준 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "운전면허 시험",
                        "여자: 실기시험은 몇 번까지 재응시할 수 있어요?\n남자: 횟수 제한 없이 계속 볼 수 있어요.",
                        q("실기시험 재응시 횟수로 알맞은 것을 고르십시오.", List.of(
                                opt("제한 없이 계속 응시 가능", "정답: '횟수 제한 없이 계속 볼 수 있다'고 답했습니다."),
                                opt("최대 2번까지 가능", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("한 번만 가능", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("재응시가 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 제한 없음을 특정 횟수 제한으로 착각하게 합니다.",
                                "[면허 마인드맵] 재응시(제한 없음). '횟수 제한 없이'라는 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "주차 문제",
                        "남자: 여기 장애인 주차구역인데 잠깐만 세워도 될까요?\n여자: 안 돼요, 잠깐이라도 과태료가 부과돼요.",
                        q("장애인 주차구역에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("잠깐이라도 주차하면 과태료가 부과된다.", "정답: '잠깐이라도 과태료가 부과된다'고 답했습니다."),
                                opt("짧은 시간은 괜찮다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("과태료가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("장애인만 아니면 자유롭게 주차 가능하다.", "🔵 파랑 - 실제로는 잠깐도 금지라는 내용과 다른 오답입니다.")
                        ), 0, "🔵 '잠깐이라도'라는 강조 표현을 놓치고 예외가 있다고 착각하게 합니다.",
                                "[주차 마인드맵] 금지(장애인 구역) + 강조(잠깐이라도 과태료). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "교통 법규",
                        "여자: 어린이보호구역에서는 속도 제한이 어떻게 돼요?\n남자: 시속 30킬로미터 이하로 다녀야 해요.",
                        q("어린이보호구역의 제한 속도로 알맞은 것을 고르십시오.", List.of(
                                opt("시속 30킬로미터 이하", "정답: '시속 30킬로미터 이하로 다녀야 한다'고 답했습니다."),
                                opt("시속 50킬로미터 이하", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("시속 60킬로미터 이하", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("제한 속도가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 제한 속도 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[교통법규 마인드맵] 구역(어린이보호구역) + 속도(30km/h 이하). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "대중교통 민원",
                        "남자: 지하철에서 물건을 잃어버렸는데 어디에 문의해요?\n여자: 역무실에 바로 문의하시면 돼요.",
                        q("여자가 안내한 문의 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("역무실", "정답: '역무실에 바로 문의하라'고 답했습니다."),
                                opt("고객센터 전화", "🔵 파랑 - 언급되지 않은 방법입니다."),
                                opt("경찰서", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("문의할 곳이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 곳으로 오답을 만듭니다.",
                                "[민원 마인드맵] 문제(분실) → 장소(역무실). 장소명에 형광펜을 칠하세요."))
        );

        List<PassageSeed> listening11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "자동차 정비",
                        "여자: 정비 맡기면 얼마나 걸려요?\n남자: 간단한 점검이라 두 시간이면 끝나요.",
                        q("정비에 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("두 시간", "정답: '두 시간이면 끝난다'고 답했습니다."),
                                opt("30분", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("하루", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("일주일", "🔴 빨강 - 시간을 바꾼 오답입니다.")
                        ), 0, "🔴 소요 시간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[정비 마인드맵] 종류(간단한 점검) + 시간(두 시간). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "운전면허 시험",
                        "남자: 도로주행 시험은 어디서 봐요?\n여자: 실제 도로에서 감독관과 함께 봐요.",
                        q("도로주행 시험을 보는 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("실제 도로", "정답: '실제 도로에서 본다'고 답했습니다."),
                                opt("면허시험장 안", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("시뮬레이터실", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("도로주행 시험이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 장소로 오답을 만듭니다.",
                                "[면허 마인드맵] 시험 장소(실제 도로) + 감독(감독관 동승). 장소를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "주차 문제",
                        "여자: 이 아파트는 주차 등록을 해야 하나요?\n남자: 네, 관리사무소에서 차량 등록을 먼저 해야 해요.",
                        q("아파트 주차를 위해 먼저 해야 할 일로 알맞은 것을 고르십시오.", List.of(
                                opt("관리사무소에서 차량 등록하기", "정답: '관리사무소에서 차량 등록을 먼저 해야 한다'고 답했습니다."),
                                opt("경비실에 신고하기", "🔴 빨강 - '관리사무소'를 '경비실'로 바꾼 오답입니다."),
                                opt("등록 없이 주차 가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("입주민에게만 문의하면 된다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 장소를 바꾸거나 등록 필요성을 반대로 착각하게 합니다.",
                                "[주차 마인드맵] 절차(관리사무소 등록) → 주차 가능. 순서를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "교통 법규",
                        "남자: 신호 위반하면 벌점이 얼마나 나와요?\n여자: 신호 위반은 벌점 15점이 부과돼요.",
                        q("신호 위반 시 부과되는 벌점으로 알맞은 것을 고르십시오.", List.of(
                                opt("15점", "정답: '벌점 15점이 부과된다'고 답했습니다."),
                                opt("5점", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("30점", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("벌점이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 벌점 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[교통법규 마인드맵] 위반(신호 위반) + 벌점(15점). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "대중교통 민원",
                        "여자: 버스 기사님이 불친절했다고 신고할 수 있나요?\n남자: 네, 버스 회사 홈페이지에 민원을 남기시면 돼요.",
                        q("민원을 접수하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("버스 회사 홈페이지에 민원 남기기", "정답: '버스 회사 홈페이지에 민원을 남기면 된다'고 답했습니다."),
                                opt("경찰서에 신고하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("버스 기사에게 직접 항의하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("신고할 수 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[민원 마인드맵] 방법 = 버스 회사 홈페이지. 정확한 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "자동차 정비",
                        "남자: 배터리가 방전됐는데 어떻게 해야 해요?\n여자: 보험사에 전화하면 긴급출동 서비스를 불러 줘요.",
                        q("여자가 안내한 해결 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("보험사에 전화해서 긴급출동 부르기", "정답: '보험사에 전화하면 긴급출동 서비스를 불러 준다'고 답했습니다."),
                                opt("직접 배터리를 교체하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("정비소까지 걸어가기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("그냥 기다리기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 행동으로 오답을 만듭니다.",
                                "[정비 마인드맵] 문제(배터리 방전) → 해결(보험사 긴급출동). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "운전면허 시험",
                        "여자: 면허증 갱신은 몇 년마다 해요?\n남자: 적성검사 포함해서 10년마다 갱신해야 해요.",
                        q("운전면허증 갱신 주기로 알맞은 것을 고르십시오.", List.of(
                                opt("10년마다", "정답: '10년마다 갱신해야 한다'고 답했습니다."),
                                opt("5년마다", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("20년마다", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("갱신할 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 갱신 주기 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[면허 마인드맵] 절차(적성검사 포함) + 주기(10년). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "주차 문제",
                        "남자: 이 건물 지하주차장은 무료예요?\n여자: 첫 1시간은 무료고 이후엔 10분당 500원이에요.",
                        q("주차 요금에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("첫 1시간은 무료다.", "정답: '첫 1시간은 무료'라고 답했습니다."),
                                opt("전체 요금이 무료다.", "🔵 파랑 - '첫 1시간만'이라는 조건을 빼고 읽게 합니다."),
                                opt("처음부터 요금이 부과된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("10분당 1000원이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 무료 시간 조건을 빼고 읽거나 요금 숫자를 바꿔 오답을 만듭니다.",
                                "[주차 마인드맵] 무료(첫 1시간) + 유료(이후 10분당 500원). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "교통 법규",
                        "여자: 안전벨트 안 매면 벌금이 있나요?\n남자: 네, 안전벨트 미착용 시 3만 원 범칙금이 부과돼요.",
                        q("안전벨트 미착용 시 부과되는 범칙금으로 알맞은 것을 고르십시오.", List.of(
                                opt("3만 원", "정답: '3만 원 범칙금이 부과된다'고 답했습니다."),
                                opt("1만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("10만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("범칙금이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 범칙금 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[교통법규 마인드맵] 위반(안전벨트 미착용) + 범칙금(3만 원). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "대중교통 민원",
                        "남자: 지하철 냉방이 너무 세다고 민원 넣을 수 있어요?\n여자: 네, 앱으로 실시간 민원 접수가 가능해요.",
                        q("민원을 접수하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("앱으로 실시간 접수하기", "정답: '앱으로 실시간 민원 접수가 가능하다'고 답했습니다."),
                                opt("역무실 방문하기", "🔵 파랑 - 언급되지 않은 방법입니다."),
                                opt("우편으로 접수하기", "🔵 파랑 - 언급되지 않은 방법입니다."),
                                opt("민원 접수가 불가능하다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[민원 마인드맵] 방법(앱) + 특징(실시간). 수단 표현을 확인하세요."))
        );

        List<PassageSeed> reading21to30 = List.of(
                onePassage(PassageCategory.READING, "자동차 정비소 안내문",
                        "[정비소 안내문] 엔진오일 교체는 예약 없이도 가능하나, 대기 시간이 발생할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("예약 없이도 엔진오일 교체가 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("예약이 반드시 필요하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("대기 시간이 전혀 없다.", "🔵 파랑 - '대기 시간이 발생할 수 있다'와 반대되는 오답입니다."),
                                opt("엔진오일 교체 서비스가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 예약 필요 여부나 대기 시간 유무를 반대로 착각하게 합니다.",
                                "[정비소 마인드맵] 조건(예약 불필요) + 유의(대기 가능). 색칠하세요.")),
                onePassage(PassageCategory.READING, "운전면허 시험 안내",
                        "[시험 안내] 필기시험 합격 후 1년 이내에 실기시험에 합격해야 면허가 발급됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("필기 합격 후 1년 이내에 실기에 합격해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("필기 합격 후 기한 제한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("실기시험이 먼저다.", "🔵 파랑 - 순서를 뒤바꾼 오답입니다."),
                                opt("기한은 2년이다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 기한 유무나 순서, 숫자를 바꿔 오답을 만듭니다.",
                                "[면허 마인드맵] 필기(합격) → 기한(1년 이내) → 실기. 순서와 숫자를 색칠하세요.")),
                onePassage(PassageCategory.READING, "주차장 이용 안내문",
                        "[주차장 이용 안내] 경차와 친환경차는 주차 요금을 50% 할인해 드립니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("경차와 친환경차는 요금이 할인된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 차량이 할인받는다.", "🔵 파랑 - '경차와 친환경차'라는 조건을 빼고 읽게 합니다."),
                                opt("할인율은 100%다.", "🔵 파랑 - '50%'를 '100%'로 바꾼 오답입니다."),
                                opt("경차와 친환경차는 할인이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 할인 대상이나 할인율 숫자를 바꿔 오답을 만듭니다.",
                                "[주차장 마인드맵] 대상(경차, 친환경차) + 할인율(50%). 색칠하세요.")),
                onePassage(PassageCategory.READING, "교통 법규 위반 통지서",
                        "[위반 통지서] 과속으로 단속되었으며, 범칙금은 지정된 기한 내에 납부하지 않으면 가산금이 부과됩니다.",
                        q("이 통지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("기한 내 미납부 시 가산금이 붙는다.", "정답: 통지서에 그대로 명시되어 있습니다."),
                                opt("늦게 내도 불이익이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("이 위반은 신호 위반이다.", "🔵 파랑 - '과속'을 '신호 위반'으로 바꾼 오답입니다."),
                                opt("범칙금은 면제된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 불이익 여부나 위반 종류를 바꿔 오답을 만듭니다.",
                                "[통지서 마인드맵] 위반(과속) + 미납 시(가산금). 색칠하세요.")),
                onePassage(PassageCategory.READING, "대중교통 민원 게시판",
                        "[민원 게시판] 접수된 민원은 7일 이내에 답변드리는 것을 원칙으로 합니다.",
                        q("이 게시판의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("민원 답변은 7일 이내가 원칙이다.", "정답: 게시판에 그대로 명시되어 있습니다."),
                                opt("답변 기한이 정해져 있지 않다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("답변은 즉시 이루어진다.", "🔵 파랑 - '7일 이내'라는 기한과 다른 오답입니다."),
                                opt("민원은 접수되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 답변 기한 유무나 숫자를 바꿔 오답을 만듭니다.",
                                "[민원 마인드맵] 접수 → 답변(7일 이내). 기한 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "자동차 정비소 안내문",
                        "[정비소 안내문] 순정 부품 사용 시 6개월간 무상 A/S가 제공됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("순정 부품 사용 시 6개월간 무상 A/S를 받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 부품이 무상 A/S 대상이다.", "🔵 파랑 - '순정 부품'이라는 조건을 빼고 읽게 합니다."),
                                opt("무상 A/S 기간은 1년이다.", "🔵 파랑 - '6개월'을 '1년'으로 바꾼 오답입니다."),
                                opt("A/S는 유료로만 제공된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(순정 부품)을 빼거나 기간 숫자를 바꿔 오답을 만듭니다.",
                                "[정비소 마인드맵] 조건(순정 부품) + 혜택(6개월 무상 A/S). 색칠하세요.")),
                onePassage(PassageCategory.READING, "운전면허 시험 안내",
                        "[시험 안내] 만 18세 이상부터 응시 가능하며, 신체검사에 합격해야 필기시험을 볼 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신체검사 합격 후 필기시험을 볼 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("필기시험 후 신체검사를 본다.", "🔵 파랑 - 순서를 뒤바꾼 오답입니다."),
                                opt("만 15세부터 응시 가능하다.", "🔵 파랑 - 나이 숫자를 바꾼 오답입니다."),
                                opt("신체검사는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 순서를 뒤바꾸거나 나이 조건, 신체검사 필요성을 바꿔 오답을 만듭니다.",
                                "[면허 마인드맵] 나이(18세 이상) + 순서(신체검사 → 필기). 색칠하세요.")),
                onePassage(PassageCategory.READING, "주차장 이용 안내문",
                        "[주차장 이용 안내] 정기권 이용자는 출입구 차단기가 자동으로 열립니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("정기권 이용자는 차단기가 자동으로 열린다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("정기권 이용자도 매번 결제해야 한다.", "🔵 파랑 - '자동으로 열린다'와 반대되는 오답입니다."),
                                opt("일반 이용자만 자동으로 열린다.", "🔵 파랑 - 대상을 뒤바꾼 오답입니다."),
                                opt("정기권은 판매하지 않는다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 대상을 뒤바꾸거나 자동 개방 여부를 반대로 착각하게 합니다.",
                                "[주차장 마인드맵] 대상(정기권 이용자) + 혜택(자동 개방). 색칠하세요.")),
                onePassage(PassageCategory.READING, "교통 법규 위반 통지서",
                        "[위반 통지서] 이의가 있으신 경우 통지서 수령일로부터 60일 이내에 이의 신청이 가능합니다.",
                        q("이 통지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이의 신청은 60일 이내에 해야 한다.", "정답: 통지서에 그대로 명시되어 있습니다."),
                                opt("이의 신청 기한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("이의 신청은 30일 이내다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("이의 신청이 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 기한 숫자를 바꾸거나 신청 가능 여부를 반대로 착각하게 합니다.",
                                "[통지서 마인드맵] 기한(수령일로부터 60일). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "대중교통 민원 게시판",
                        "[민원 게시판] 동일한 민원을 중복 접수하시면 처리가 지연될 수 있으니 유의해 주세요.",
                        q("이 게시판의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("중복 접수 시 처리가 지연될 수 있다.", "정답: 게시판에 그대로 명시되어 있습니다."),
                                opt("중복 접수하면 더 빨리 처리된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("중복 접수는 금지되어 있다.", "🔵 파랑 - '지연될 수 있다'는 경고와 다른 과장된 오답입니다."),
                                opt("민원은 한 번만 접수할 수 있다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 중복 접수의 결과를 반대로 착각하게 하거나 완전 금지로 과장합니다.",
                                "[민원 마인드맵] 중복 접수 → 결과(지연 가능). 인과관계를 색칠하세요."))
        );

        List<PassageSeed> reading31to40 = List.of(
                onePassage(PassageCategory.READING, "자동차 정비소 안내문",
                        "[정비소 안내문] 타이어 4개를 동시에 교체하시면 얼라인먼트 점검을 무료로 해 드립니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("타이어 4개 교체 시 얼라인먼트 점검이 무료다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("타이어 2개만 바꿔도 무료 점검이 된다.", "🔵 파랑 - '4개를 동시에'라는 조건과 반대되는 오답입니다."),
                                opt("얼라인먼트 점검은 항상 유료다.", "🔵 파랑 - 명시된 조건부 무료와 반대되는 오답입니다."),
                                opt("타이어 교체 서비스가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(4개 동시 교체)을 빼거나 무료/유료를 반대로 착각하게 합니다.",
                                "[정비소 마인드맵] 조건(타이어 4개) → 혜택(얼라인먼트 무료). 색칠하세요.")),
                onePassage(PassageCategory.READING, "운전면허 시험 안내",
                        "[시험 안내] 초보운전 표지는 법적 의무는 아니지만 부착을 권장합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("초보운전 표지 부착은 의무가 아니다.", "정답: '법적 의무는 아니다'라고 명시했습니다."),
                                opt("초보운전 표지 부착은 법적 의무다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("표지 부착은 금지되어 있다.", "🔵 파랑 - '부착을 권장한다'와 반대되는 오답입니다."),
                                opt("표지가 없으면 벌금을 낸다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 의무 여부를 반대로 착각하게 하거나 벌금 관련 내용을 추가합니다.",
                                "[면허 마인드맵] 의무 아님 + 권장. 두 표현을 색으로 구분하세요.")),
                onePassage(PassageCategory.READING, "주차장 이용 안내문",
                        "[주차장 이용 안내] 전기차 충전 구역은 충전 완료 후 30분 이내에 차량을 이동해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("충전 완료 후 30분 이내에 이동해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("충전 완료 후 계속 주차해도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("이동 기한은 1시간이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("전기차는 충전 구역에 주차할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 이동 필요성이나 기한 숫자를 바꿔 오답을 만듭니다.",
                                "[주차장 마인드맵] 완료 → 이동(30분 이내). 시간 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "교통 법규 위반 통지서",
                        "[위반 통지서] 자진납부 시 범칙금의 20%가 감경되니 기한 내 납부를 권장합니다.",
                        q("이 통지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("자진납부 시 범칙금이 감경된다.", "정답: 통지서에 그대로 명시되어 있습니다."),
                                opt("자진납부해도 감경되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("감경율은 50%다.", "🔵 파랑 - 비율 숫자를 바꾼 오답입니다."),
                                opt("자진납부는 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 감경 여부나 감경율 숫자를 바꿔 오답을 만듭니다.",
                                "[통지서 마인드맵] 조건(자진납부) → 혜택(20% 감경). 색칠하세요.")),
                onePassage(PassageCategory.READING, "대중교통 민원 게시판",
                        "[민원 게시판] 익명으로 접수된 민원은 처리 결과를 개별 안내해 드릴 수 없습니다.",
                        q("이 게시판의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("익명 민원은 개별 안내가 불가능하다.", "정답: 게시판에 그대로 명시되어 있습니다."),
                                opt("익명 민원도 개별 안내를 받는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("익명으로는 민원을 접수할 수 없다.", "🔵 파랑 - '접수된 민원'이라는 표현과 반대되는 오답입니다."),
                                opt("실명 민원도 안내가 불가능하다.", "🔵 파랑 - '익명'이라는 조건과 다른 오답입니다.")
                        ), 0, "🔵 익명/실명 조건을 바꾸거나 접수 가능 여부를 반대로 착각하게 합니다.",
                                "[민원 마인드맵] 조건(익명) → 결과(개별 안내 불가). 색칠하세요.")),
                onePassage(PassageCategory.READING, "자동차 정비소 안내문",
                        "[정비소 안내문] 픽업 서비스는 반경 5km 이내 고객에 한해 무료로 제공됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("5km 이내 고객은 픽업이 무료다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 고객이 무료로 픽업받는다.", "🔵 파랑 - '5km 이내'라는 조건을 빼고 읽게 합니다."),
                                opt("픽업 서비스는 유료다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("반경 제한은 10km다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 거리 조건을 빼거나 숫자를 바꿔 오답을 만듭니다.",
                                "[정비소 마인드맵] 조건(5km 이내) + 혜택(무료 픽업). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "운전면허 시험 안내",
                        "[시험 안내] 시험 당일 취소는 응시료 환불이 불가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("시험 당일 취소는 환불이 안 된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("언제 취소해도 전액 환불된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("당일 취소해도 응시료가 환불된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("사전 취소도 환불이 안 된다.", "🔵 파랑 - '당일'이라는 조건과 다른 오답입니다.")
                        ), 0, "🔵 환불 가능 시점을 반대로 착각하게 합니다.",
                                "[면허 마인드맵] 당일 취소 → 환불 불가. 시점 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "주차장 이용 안내문",
                        "[주차장 이용 안내] 야간(오후 10시~오전 7시)에는 정기권 없이도 무료로 이용할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("야간에는 정기권 없이 무료다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("야간에도 요금을 내야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("주간에 무료로 이용할 수 있다.", "🔵 파랑 - '야간'을 '주간'으로 바꾼 오답입니다."),
                                opt("야간에도 정기권이 필요하다.", "🔵 파랑 - '정기권 없이도'와 반대되는 오답입니다.")
                        ), 0, "🔵 시간대(야간/주간)나 정기권 필요 여부를 반대로 착각하게 합니다.",
                                "[주차장 마인드맵] 시간(오후 10시~오전 7시) + 혜택(무료). 색칠하세요.")),
                onePassage(PassageCategory.READING, "교통 법규 위반 통지서",
                        "[위반 통지서] 불법 주정차로 단속되었으며, 재발 시 범칙금이 가중될 수 있습니다.",
                        q("이 통지서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("재발 시 범칙금이 늘어날 수 있다.", "정답: '재발 시 범칙금이 가중될 수 있다'고 명시했습니다."),
                                opt("재발해도 범칙금은 동일하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("이 위반은 과속이다.", "🔵 파랑 - '불법 주정차'를 '과속'으로 바꾼 오답입니다."),
                                opt("재발 시 범칙금이 줄어든다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 재발 시 결과(가중/동일/감소)를 바꾸거나 위반 종류를 바꿔 오답을 만듭니다.",
                                "[통지서 마인드맵] 위반(불법 주정차) + 재발 시(가중). 색칠하세요.")),
                onePassage(PassageCategory.READING, "대중교통 민원 게시판",
                        "[민원 게시판] 우수 제안으로 채택되면 소정의 포상금이 지급됩니다.",
                        q("이 게시판의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("우수 제안은 포상금을 받는다.", "정답: 게시판에 그대로 명시되어 있습니다."),
                                opt("모든 민원에 포상금이 지급된다.", "🔵 파랑 - '우수 제안으로 채택되면'이라는 조건을 빼고 읽게 합니다."),
                                opt("포상금 제도는 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("포상금은 모든 참여자에게 지급된다.", "🔵 파랑 - 조건(채택)을 빼고 읽게 하는 오답입니다.")
                        ), 0, "🔵 포상 조건(채택)을 빼고 읽거나 제도 유무를 반대로 착각하게 합니다.",
                                "[민원 마인드맵] 조건(우수 제안 채택) → 혜택(포상금). 조건에 밑줄을 그으세요."))
        );

        List<PassageSeed> listening2nd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "재난 대비",
                        "여자: 비상용품은 뭘 준비해 놓는 게 좋아요?\n남자: 물, 손전등, 비상식량을 미리 챙겨 두세요.",
                        q("남자가 추천한 비상용품으로 알맞은 것을 고르십시오.", List.of(
                                opt("물, 손전등, 비상식량", "정답: 대화에 그대로 나와 있습니다."),
                                opt("담요와 라디오만", "🔵 파랑 - 언급되지 않은 물품입니다."),
                                opt("휴대폰 충전기만", "🔵 파랑 - 언급되지 않은 물품입니다."),
                                opt("비상용품이 필요 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 물품과 무관한 다른 물품으로 오답을 만듭니다.",
                                "[재난 마인드맵] 준비물(물+손전등+비상식량). 세 가지 물품을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화재 대피",
                        "남자: 불이 났을 때 엘리베이터를 타도 되나요?\n여자: 아니요, 절대 안 돼요. 계단으로 대피해야 해요.",
                        q("화재 시 대피 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("계단으로 대피하기", "정답: '계단으로 대피해야 한다'고 답했습니다."),
                                opt("엘리베이터 이용하기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("창문에서 뛰어내리기", "🔴 빨강 - 언급되지 않은 위험한 행동입니다."),
                                opt("그냥 방에서 대기하기", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 금지된 행동(엘리베이터)을 정답처럼 착각하게 합니다.",
                                "[화재 마인드맵] 금지(엘리베이터) ↔ 권장(계단). 반대되는 두 방법을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "지진 대응",
                        "여자: 지진이 나면 제일 먼저 뭘 해야 해요?\n남자: 튼튼한 탁자 밑으로 들어가서 몸을 보호해야 해요.",
                        q("지진 발생 시 가장 먼저 해야 할 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("탁자 밑으로 들어가기", "정답: '탁자 밑으로 들어가서 몸을 보호해야 한다'고 답했습니다."),
                                opt("밖으로 뛰쳐나가기", "🔴 빨강 - 대화 내용과 반대되는 위험한 오답입니다."),
                                opt("창문을 열기", "🔴 빨강 - 언급되지 않은 행동입니다."),
                                opt("가만히 서 있기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 행동 요령과 반대되는 위험한 행동으로 오답을 만듭니다.",
                                "[지진 마인드맵] 상황(지진) → 행동(탁자 밑 대피). 핵심 행동에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 신고",
                        "남자: 응급상황일 때 몇 번으로 전화해요?\n여자: 119로 전화하면 돼요.",
                        q("응급상황 시 신고하는 번호로 알맞은 것을 고르십시오.", List.of(
                                opt("119", "정답: '119로 전화하면 된다'고 답했습니다."),
                                opt("112", "🔴 빨강 - 다른 신고 번호와 혼동하게 하는 오답입니다."),
                                opt("110", "🔴 빨강 - 대화에 없는 번호입니다."),
                                opt("신고할 번호가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 비슷한 신고 번호(119/112)를 혼동하게 합니다.",
                                "[응급신고 마인드맵] 응급상황 → 119. 번호를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "안전 교육",
                        "여자: 이번 안전 교육은 몇 시간이에요?\n남자: 두 시간짜리 교육이에요.",
                        q("안전 교육의 소요 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("두 시간", "정답: '두 시간짜리 교육'이라고 답했습니다."),
                                opt("한 시간", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("네 시간", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("하루 종일이다.", "🔴 빨강 - 대화 내용과 반대되는 과장된 오답입니다.")
                        ), 0, "🔴 소요 시간 숫자를 슬쩍 바꾸거나 과장해 오답을 만듭니다.",
                                "[안전교육 마인드맵] 질문(시간) → 답(두 시간). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "재난 대비",
                        "남자: 대피소 위치는 어떻게 확인해요?\n여자: 안전디딤돌 앱에서 가까운 대피소를 확인할 수 있어요.",
                        q("대피소 위치를 확인하는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("안전디딤돌 앱 확인", "정답: '안전디딤돌 앱에서 확인할 수 있다'고 답했습니다."),
                                opt("동사무소 방문", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("이웃에게 물어보기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("확인할 방법이 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[재난 마인드맵] 방법 = 안전디딤돌 앱. 앱 이름을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화재 대피",
                        "여자: 연기가 많을 때는 어떻게 이동해야 해요?\n남자: 자세를 낮추고 코와 입을 막고 이동해야 해요.",
                        q("연기가 많을 때의 대피 자세로 알맞은 것을 고르십시오.", List.of(
                                opt("자세를 낮추고 코와 입 막기", "정답: 대화에 그대로 나와 있습니다."),
                                opt("서서 빠르게 뛰기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("바닥에 엎드려 그대로 있기", "🔴 빨강 - '이동해야 한다'와 반대되는 오답입니다."),
                                opt("숨을 크게 쉬며 이동하기", "🔴 빨강 - '코와 입을 막고'와 반대되는 오답입니다.")
                        ), 0, "🔴 자세나 호흡 방법을 반대로 착각하게 합니다.",
                                "[화재 마인드맵] 자세(낮춤) + 호흡(코와 입 막기). 두 행동을 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "지진 대응",
                        "남자: 지진 후에 밖으로 나갈 때 조심할 게 있나요?\n여자: 네, 떨어지는 물건에 조심하면서 엘리베이터 대신 계단을 이용하세요.",
                        q("지진 후 대피 시 이용해야 할 이동 수단으로 알맞은 것을 고르십시오.", List.of(
                                opt("계단", "정답: '엘리베이터 대신 계단을 이용하라'고 답했습니다."),
                                opt("엘리베이터", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("에스컬레이터", "🔴 빨강 - 언급되지 않은 수단입니다."),
                                opt("아무거나 상관없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 엘리베이터와 계단을 혼동하게 합니다.",
                                "[지진 마인드맵] 금지(엘리베이터) ↔ 권장(계단). 반대 표현을 색으로 구분하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 신고",
                        "여자: 신고할 때 뭘 먼저 말해야 해요?\n남자: 정확한 위치부터 말씀하시는 게 중요해요.",
                        q("신고 시 가장 먼저 말해야 할 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("정확한 위치", "정답: '정확한 위치부터 말씀하시는 게 중요하다'고 답했습니다."),
                                opt("이름", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("전화번호", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("아무거나 상관없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 우선순위와 무관한 다른 정보로 오답을 만듭니다.",
                                "[응급신고 마인드맵] 우선순위 1(위치). '먼저'라는 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "안전 교육",
                        "남자: 소화기 사용법도 교육받나요?\n여자: 네, 실습 시간에 직접 소화기를 사용해 볼 수 있어요.",
                        q("안전 교육에서 실습하는 내용으로 알맞은 것을 고르십시오.", List.of(
                                opt("소화기 사용법", "정답: '직접 소화기를 사용해 볼 수 있다'고 답했습니다."),
                                opt("심폐소생술만", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("대피로 그리기", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("실습이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 실습 내용과 무관한 다른 내용으로 오답을 만듭니다.",
                                "[안전교육 마인드맵] 실습(소화기 사용). 실습 대상에 형광펜을 칠하세요."))
        );

        List<PassageSeed> listening2nd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "재난 대비",
                        "여자: 태풍 오기 전에 뭘 준비해야 해요?\n남자: 창문에 테이프를 붙이고 배수구를 미리 점검해야 해요.",
                        q("태풍 대비 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("창문 테이프 붙이기와 배수구 점검", "정답: 대화에 그대로 나와 있습니다."),
                                opt("창문을 열어 놓기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("실외 활동을 늘리기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("아무 준비도 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 준비 방법과 반대되는 행동으로 오답을 만듭니다.",
                                "[재난 마인드맵] 준비(테이프+배수구 점검). 두 가지 방법을 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "화재 대피",
                        "남자: 화재 대피 훈련은 얼마나 자주 해요?\n여자: 분기별로 한 번씩 진행돼요.",
                        q("화재 대피 훈련의 주기로 알맞은 것을 고르십시오.", List.of(
                                opt("분기별로 한 번", "정답: '분기별로 한 번씩 진행된다'고 답했습니다."),
                                opt("매달 한 번", "🔴 빨강 - 주기를 바꾼 오답입니다."),
                                opt("1년에 한 번", "🔴 빨강 - 주기를 바꾼 오답입니다."),
                                opt("훈련이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 주기 단위(분기/월/년)를 바꿔 오답을 만듭니다.",
                                "[화재 마인드맵] 훈련(분기별 1회). 주기 표현을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "지진 대응",
                        "여자: 지진 대피 후에는 바로 건물에 다시 들어가도 되나요?\n남자: 아니요, 안전이 확인될 때까지 기다려야 해요.",
                        q("지진 대피 후 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("안전 확인될 때까지 기다리기", "정답: '안전이 확인될 때까지 기다려야 한다'고 답했습니다."),
                                opt("바로 건물에 다시 들어가기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("짐을 가지러 즉시 들어가기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("아무 때나 들어가도 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 즉시 재입장 가능하다는 반대 내용으로 오답을 만듭니다.",
                                "[지진 마인드맵] 대피 → 대기(안전 확인까지). '아니요'라는 부정 답변을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 신고",
                        "남자: 오작동으로 잘못 신고했을 때는 어떻게 해요?\n여자: 바로 다시 전화해서 오작동이라고 말씀해 주세요.",
                        q("오작동 신고 시 해야 할 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("바로 다시 전화해서 알리기", "정답: '바로 다시 전화해서 오작동이라고 말하라'고 답했습니다."),
                                opt("그냥 두기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("문자로 알리기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("경찰서를 방문하기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 안내(재전화)와 무관한 다른 방법으로 오답을 만듭니다.",
                                "[응급신고 마인드맵] 오작동 → 재전화 안내. 방법을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "안전 교육",
                        "여자: 이 교육은 누가 꼭 들어야 해요?\n남자: 신입 직원은 입사 후 한 달 이내에 필수로 들어야 해요.",
                        q("신입 직원의 안전 교육 이수 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("입사 후 한 달 이내", "정답: '입사 후 한 달 이내에 필수로 들어야 한다'고 답했습니다."),
                                opt("입사 후 일주일 이내", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("입사 후 1년 이내", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("이수 기한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 이수 기한 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[안전교육 마인드맵] 대상(신입 직원) + 기한(한 달 이내). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "재난 대비",
                        "남자: 정전됐을 때 냉장고 문은 열어도 되나요?\n여자: 아니요, 가능한 한 열지 않는 게 음식 보존에 좋아요.",
                        q("정전 시 냉장고 관리 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("가능한 한 문을 열지 않기", "정답: '가능한 한 열지 않는 게 좋다'고 답했습니다."),
                                opt("자주 열어서 확인하기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("바로 다 버리기", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("문을 완전히 열어 두기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 권장 행동과 반대되는 행동으로 오답을 만듭니다.",
                                "[재난 마인드맵] 정전 → 냉장고 문 최소 개방. '아니요'라는 응답을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "화재 대피",
                        "여자: 옷에 불이 붙으면 어떻게 해야 해요?\n남자: 멈춰서 바닥에 엎드려 구르는 게 좋아요.",
                        q("옷에 불이 붙었을 때의 대처법으로 알맞은 것을 고르십시오.", List.of(
                                opt("멈춰서 바닥에 엎드려 구르기", "정답: 대화에 그대로 나와 있습니다."),
                                opt("빠르게 뛰어다니기", "🔴 빨강 - 대화 내용과 반대되는 위험한 오답입니다."),
                                opt("물로 씻어내기만 하기", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("가만히 서 있기", "🔴 빨강 - '엎드려 구르기'와 다른 오답입니다.")
                        ), 0, "🔴 실제 행동 요령과 반대되는 위험한 행동으로 오답을 만듭니다.",
                                "[화재 마인드맵] 상황(옷에 불) → 행동(멈춤+엎드림+구르기). 순서를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "지진 대응",
                        "남자: 지진 대비 가구 고정은 어떻게 해요?\n여자: 책장이나 장롱은 벽에 고정 장치로 고정해 두세요.",
                        q("여자가 안내한 가구 고정 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("벽에 고정 장치로 고정하기", "정답: '벽에 고정 장치로 고정해 두라'고 답했습니다."),
                                opt("가구를 낮게 배치하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("가구를 모두 치우기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("고정할 필요가 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[지진 마인드맵] 대상(책장, 장롱) + 방법(벽 고정). 방법을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "응급 신고",
                        "여자: 심정지 환자를 발견하면 무엇부터 해야 해요?\n남자: 먼저 119에 신고하고 심폐소생술을 시작해야 해요.",
                        q("심정지 환자 발견 시 첫 번째 행동으로 알맞은 것을 고르십시오.", List.of(
                                opt("119에 신고하기", "정답: '먼저 119에 신고하라'고 답했습니다."),
                                opt("심폐소생술 먼저 하기", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("환자를 옮기기", "🔴 빨강 - 언급되지 않은 행동입니다."),
                                opt("주변 사람에게만 알리기", "🔴 빨강 - 언급되지 않은 행동입니다.")
                        ), 0, "🔴 '먼저'라는 순서 표현을 놓치고 다른 행동을 첫 번째로 착각하게 합니다.",
                                "[응급신고 마인드맵] 1(신고) → 2(심폐소생술). 순서를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "안전 교육",
                        "남자: 안전 교육 수료증은 어떻게 받아요?\n여자: 교육 이수 후 온라인으로 자동 발급돼요.",
                        q("안전 교육 수료증 발급 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("교육 이수 후 온라인 자동 발급", "정답: 대화에 그대로 나와 있습니다."),
                                opt("직접 신청해야 발급된다.", "🟢 초록 - '자동으로'라는 내용과 반대되는 오답입니다."),
                                opt("수료증은 발급되지 않는다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("우편으로만 받을 수 있다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 자동/수동 발급 여부를 반대로 착각하게 하거나 무관한 방법으로 오답을 만듭니다.",
                                "[안전교육 마인드맵] 이수 → 자동 발급(온라인). 발급 방식을 확인하세요."))
        );

        List<PassageSeed> reading2nd21to30 = List.of(
                onePassage(PassageCategory.READING, "재난 대비 안내문",
                        "[재난 대비 안내문] 비상용 배낭은 3일치 식수와 식량을 기준으로 준비하는 것이 좋습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("3일치 식수와 식량을 기준으로 준비한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("1일치만 준비하면 충분하다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("식수만 준비하면 된다.", "🔵 파랑 - '식량'이라는 항목을 빼고 읽게 합니다."),
                                opt("비상용 배낭은 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 기준 일수 숫자를 바꾸거나 항목을 빼고 읽게 합니다.",
                                "[재난 마인드맵] 기준(3일치) + 항목(식수+식량). 숫자와 항목을 색칠하세요.")),
                onePassage(PassageCategory.READING, "화재 대피 요령",
                        "[화재 대피 요령] 대피 시 문을 만져 뜨거우면 다른 통로를 이용해야 합니다.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("문이 뜨거우면 다른 통로를 써야 한다.", "정답: 요령에 그대로 명시되어 있습니다."),
                                opt("문이 뜨거워도 그대로 열어야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("문을 만질 필요가 없다.", "🔵 파랑 - '문을 만져'라는 확인 절차와 반대되는 오답입니다."),
                                opt("항상 같은 통로만 이용해야 한다.", "🔵 파랑 - '다른 통로'라는 대안과 반대되는 오답입니다.")
                        ), 0, "🔵 확인 절차나 대안 통로 이용 여부를 반대로 착각하게 합니다.",
                                "[화재 마인드맵] 확인(문 온도) → 대안(다른 통로). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "지진 행동 요령",
                        "[지진 행동 요령] 실외에 있을 때는 건물이나 담장에서 떨어져 넓은 공터로 이동해야 합니다.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("건물에서 떨어져 넓은 공터로 이동해야 한다.", "정답: 요령에 그대로 명시되어 있습니다."),
                                opt("건물 벽에 붙어 있어야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("실외에서는 대피할 필요가 없다.", "🔵 파랑 - 명시된 행동 요령과 반대되는 오답입니다."),
                                opt("담장 옆이 가장 안전하다.", "🔵 파랑 - '담장에서 떨어져야 한다'와 반대되는 오답입니다.")
                        ), 0, "🔵 건물·담장과의 거리를 반대로 착각하게 합니다.",
                                "[지진 마인드맵] 실외 → 건물·담장에서 이탈 → 공터. 이동 방향을 색칠하세요.")),
                onePassage(PassageCategory.READING, "응급 신고 안내",
                        "[응급 신고 안내] 문자 신고는 음성 통화가 어려운 청각장애인을 위한 서비스입니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("문자 신고는 청각장애인을 위한 서비스다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("문자 신고는 아무나 이용할 수 없다.", "🔵 파랑 - 대상이 한정되어 있다는 뉘앙스로 왜곡한 오답입니다."),
                                opt("음성 통화만 가능하다.", "🔵 파랑 - '문자 신고'라는 대안과 반대되는 오답입니다."),
                                opt("문자 신고 서비스는 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 서비스 대상이나 존재 여부를 반대로 착각하게 합니다.",
                                "[응급신고 마인드맵] 대상(청각장애인) + 방법(문자). 색칠하세요.")),
                onePassage(PassageCategory.READING, "안전 교육 공지",
                        "[안전 교육 공지] 이번 교육은 온라인으로 대체되며, 수료 기준은 영상 시청 완료입니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("교육은 온라인으로 진행된다.", "정답: 공지에 그대로 명시되어 있습니다."),
                                opt("교육은 오프라인으로만 진행된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("수료 기준은 시험 합격이다.", "🔵 파랑 - '영상 시청 완료'와 다른 오답입니다."),
                                opt("이번 교육은 취소되었다.", "🔵 파랑 - '온라인으로 대체'와 반대되는 오답입니다.")
                        ), 0, "🔵 온/오프라인 여부나 수료 기준, 취소 여부를 바꿔 오답을 만듭니다.",
                                "[안전교육 마인드맵] 방식(온라인) + 수료 기준(영상 시청). 색칠하세요.")),
                onePassage(PassageCategory.READING, "재난 대비 안내문",
                        "[재난 대비 안내문] 가족과의 비상 연락 방법을 미리 정해 두고 만날 장소도 약속해 두세요.",
                        q("이 안내문에서 권장하는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("비상 연락 방법과 만날 장소를 미리 정하기", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("연락 방법은 상황에 따라 즉흥적으로 정한다.", "🔵 파랑 - '미리 정해 두라'는 내용과 반대되는 오답입니다."),
                                opt("만날 장소는 정할 필요가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("가족과의 연락은 중요하지 않다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 사전 준비의 필요성을 반대로 착각하게 합니다.",
                                "[재난 마인드맵] 준비(연락 방법+만날 장소, 사전 약속). 색칠하세요.")),
                onePassage(PassageCategory.READING, "화재 대피 요령",
                        "[화재 대피 요령] 대피 후에는 절대 화재 현장으로 다시 들어가지 마세요.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("대피 후 다시 들어가면 안 된다.", "정답: '절대 다시 들어가지 말라'고 명시했습니다."),
                                opt("귀중품을 가지러 다시 들어가도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("대피 후 즉시 재입장해야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("현장에 들어가는 것은 상관없다.", "🔵 파랑 - '절대'라는 강한 금지 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 재입장 금지 여부를 반대로 착각하게 합니다.",
                                "[화재 마인드맵] 대피 후 → 금지(재진입). '절대'라는 강조어에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.READING, "지진 행동 요령",
                        "[지진 행동 요령] 흔들림이 멈춘 후에도 여진에 대비해 안전한 곳에서 대기해야 합니다.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("흔들림이 멈춰도 여진에 대비해야 한다.", "정답: 요령에 그대로 명시되어 있습니다."),
                                opt("흔들림이 멈추면 바로 안심해도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("여진은 발생하지 않는다.", "🔵 파랑 - '여진에 대비'라는 내용과 반대되는 오답입니다."),
                                opt("대기할 필요가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 안심 가능 여부나 여진 발생 가능성을 반대로 착각하게 합니다.",
                                "[지진 마인드맵] 흔들림 멈춤 → 여진 대비(계속 경계). 색칠하세요.")),
                onePassage(PassageCategory.READING, "응급 신고 안내",
                        "[응급 신고 안내] 장난 신고는 공무집행방해로 처벌받을 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("장난 신고는 처벌받을 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("장난 신고는 아무 문제가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("장난 신고는 권장된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("처벌 규정이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 처벌 가능 여부를 반대로 착각하게 합니다.",
                                "[응급신고 마인드맵] 장난 신고 → 처벌(공무집행방해). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "안전 교육 공지",
                        "[안전 교육 공지] 미이수자는 다음 분기 정기 교육에 재응시해야 합니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("미이수자는 다음 분기에 재응시해야 한다.", "정답: 공지에 그대로 명시되어 있습니다."),
                                opt("미이수자는 아무 조치가 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("재응시는 다음 해에 가능하다.", "🔵 파랑 - '다음 분기'를 '다음 해'로 바꾼 오답입니다."),
                                opt("미이수는 불가능한 상황이다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 재응시 시점이나 필요성을 바꿔 오답을 만듭니다.",
                                "[안전교육 마인드맵] 미이수 → 재응시(다음 분기). 시점에 밑줄을 그으세요."))
        );

        List<PassageSeed> reading2nd31to40 = List.of(
                onePassage(PassageCategory.READING, "재난 대비 안내문",
                        "[재난 대비 안내문] 정전 대비를 위해 보조배터리는 항상 완충 상태로 보관하는 것이 좋습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("보조배터리는 완충 상태로 보관해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("보조배터리는 방전 상태로 둬도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("보조배터리는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("완충은 정전과 무관하다.", "🔵 파랑 - '정전 대비를 위해'라는 목적과 반대되는 오답입니다.")
                        ), 0, "🔵 보관 상태나 목적의 관련성을 반대로 착각하게 합니다.",
                                "[재난 마인드맵] 목적(정전 대비) → 방법(완충 보관). 색칠하세요.")),
                onePassage(PassageCategory.READING, "화재 대피 요령",
                        "[화재 대피 요령] 승강기가 자동으로 1층에 정지하며 운행이 멈추니 반드시 계단을 이용하세요.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("화재 시 승강기 운행이 자동으로 멈춘다.", "정답: 요령에 그대로 명시되어 있습니다."),
                                opt("화재 시에도 승강기는 정상 운행된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("승강기는 옥상으로 이동한다.", "🔵 파랑 - '1층'을 다른 층으로 바꾼 오답입니다."),
                                opt("계단 이용은 선택 사항이다.", "🔵 파랑 - '반드시'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 승강기 운행 여부나 계단 이용의 필수성을 반대로 착각하게 합니다.",
                                "[화재 마인드맵] 승강기(자동 정지) → 계단(필수 이용). 색칠하세요.")),
                onePassage(PassageCategory.READING, "지진 행동 요령",
                        "[지진 행동 요령] 운전 중 지진이 발생하면 서서히 속도를 줄여 도로 오른쪽에 정차해야 합니다.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("도로 오른쪽에 서서히 정차해야 한다.", "정답: 요령에 그대로 명시되어 있습니다."),
                                opt("급정거해야 한다.", "🔵 파랑 - '서서히'라는 표현과 반대되는 오답입니다."),
                                opt("도로 한가운데 멈춰야 한다.", "🔵 파랑 - '오른쪽'이라는 위치와 다른 오답입니다."),
                                opt("계속 운전해도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 정차 방법이나 위치를 반대로 착각하게 합니다.",
                                "[지진 마인드맵] 운전 중 → 서서히 감속 → 오른쪽 정차. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "응급 신고 안내",
                        "[응급 신고 안내] 목격자는 사고 현장의 사진을 찍어 신고 시 함께 제공하면 도움이 됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("사고 현장 사진을 함께 제공하면 도움이 된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("사진 촬영은 금지되어 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("사진은 신고와 무관하다.", "🔵 파랑 - '도움이 된다'는 내용과 반대되는 오답입니다."),
                                opt("목격자는 신고할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 사진 제공의 도움 여부나 금지 여부를 반대로 착각하게 합니다.",
                                "[응급신고 마인드맵] 목격자 → 사진 촬영 → 신고 시 제공. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "안전 교육 공지",
                        "[안전 교육 공지] 이번 교육에 참여하지 못한 직원은 인사팀에 별도로 사유서를 제출해야 합니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("불참자는 인사팀에 사유서를 제출해야 한다.", "정답: 공지에 그대로 명시되어 있습니다."),
                                opt("불참해도 아무 조치가 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("사유서는 총무팀에 제출한다.", "🔵 파랑 - '인사팀'을 '총무팀'으로 바꾼 오답입니다."),
                                opt("모든 직원이 사유서를 내야 한다.", "🔵 파랑 - '불참자'라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 제출 대상(불참자/전체)이나 제출처를 바꿔 오답을 만듭니다.",
                                "[안전교육 마인드맵] 대상(불참자) + 제출처(인사팀). 색칠하세요.")),
                onePassage(PassageCategory.READING, "재난 대비 안내문",
                        "[재난 대비 안내문] 반려동물과 함께 대피할 경우 이동장과 최소 3일치 사료를 준비하세요.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("반려동물 대피 시 이동장과 사료를 준비해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("반려동물은 대피소에 데려갈 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("사료는 1일치만 준비하면 된다.", "🔵 파랑 - '최소 3일치'라는 숫자를 바꾼 오답입니다."),
                                opt("이동장은 필요 없다.", "🔵 파랑 - 명시된 준비물과 반대되는 오답입니다.")
                        ), 0, "🔵 준비물 유무나 기준 일수를 바꿔 오답을 만듭니다.",
                                "[재난 마인드맵] 대상(반려동물) + 준비물(이동장+사료 3일치). 색칠하세요.")),
                onePassage(PassageCategory.READING, "화재 대피 요령",
                        "[화재 대피 요령] 대피 공간이 없는 저층에서는 창문을 통한 대피도 고려할 수 있습니다.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("저층에서는 창문 대피도 고려할 수 있다.", "정답: 요령에 그대로 명시되어 있습니다."),
                                opt("창문 대피는 절대 금지된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("고층에서 권장되는 방법이다.", "🔵 파랑 - '저층'을 '고층'으로 바꾼 오답입니다."),
                                opt("이 방법은 모든 상황에 적용된다.", "🔵 파랑 - '대피 공간이 없는 저층'이라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 층수 조건을 바꾸거나 절대 금지로 과장합니다.",
                                "[화재 마인드맵] 조건(저층, 대피 공간 없음) + 방법(창문 대피). 색칠하세요.")),
                onePassage(PassageCategory.READING, "지진 행동 요령",
                        "[지진 행동 요령] 대형 마트 등 다중이용시설에서는 진열대에서 떨어져 기둥 옆으로 이동하세요.",
                        q("이 요령의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("진열대에서 떨어져 기둥 옆으로 이동해야 한다.", "정답: 요령에 그대로 명시되어 있습니다."),
                                opt("진열대 옆이 가장 안전하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("기둥에서 멀리 떨어져야 한다.", "🔵 파랑 - '기둥 옆으로'와 반대되는 오답입니다."),
                                opt("이동할 필요 없이 제자리에 있으면 된다.", "🔵 파랑 - 명시된 행동 요령과 반대되는 오답입니다.")
                        ), 0, "🔵 안전한 위치(진열대/기둥)를 반대로 착각하게 합니다.",
                                "[지진 마인드맵] 회피(진열대) → 이동(기둥 옆). 방향을 색칠하세요.")),
                onePassage(PassageCategory.READING, "응급 신고 안내",
                        "[응급 신고 안내] 위치 정보 제공에 동의하면 정확한 출동이 더 빨라질 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("위치 정보 제공 시 출동이 빨라질 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("위치 정보 제공은 출동과 무관하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("위치 정보 제공은 의무 사항이다.", "🔵 파랑 - '동의하면'이라는 표현과 다른 오답입니다."),
                                opt("위치 정보 제공 시 오히려 늦어진다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 위치 정보 제공의 효과를 반대로 착각하게 합니다.",
                                "[응급신고 마인드맵] 동의(위치 정보) → 효과(빠른 출동). 색칠하세요.")),
                onePassage(PassageCategory.READING, "안전 교육 공지",
                        "[안전 교육 공지] 우수 이수자에게는 소정의 인센티브가 지급될 예정입니다.",
                        q("이 공지의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("우수 이수자는 인센티브를 받는다.", "정답: 공지에 그대로 명시되어 있습니다."),
                                opt("모든 이수자가 인센티브를 받는다.", "🔵 파랑 - '우수 이수자'라는 조건을 빼고 읽게 합니다."),
                                opt("인센티브 제도가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("인센티브는 벌금 형태다.", "🔵 파랑 - '인센티브'를 반대 의미로 왜곡한 오답입니다.")
                        ), 0, "🔵 조건(우수 이수자)을 빼거나 인센티브의 의미를 반대로 왜곡합니다.",
                                "[안전교육 마인드맵] 조건(우수 이수) → 혜택(인센티브). 색칠하세요."))
        );

        List<PassageSeed> listening3rd1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "부동산 계약",
                        "여자: 이 집 계약하려면 계약금은 얼마나 필요해요?\n남자: 보증금의 10%를 계약금으로 내시면 돼요.",
                        q("계약금으로 내야 하는 비율로 알맞은 것을 고르십시오.", List.of(
                                opt("보증금의 10%", "정답: '보증금의 10%를 계약금으로 낸다'고 답했습니다."),
                                opt("보증금의 50%", "🔴 빨강 - 비율을 바꾼 오답입니다."),
                                opt("보증금 전액", "🔴 빨강 - 비율을 바꾼 오답입니다."),
                                opt("계약금이 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 계약금 비율 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[부동산 마인드맵] 보증금 + 계약금(10%). 비율을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "전월세 계약",
                        "남자: 전세랑 월세 중에 뭐가 나을까요?\n여자: 목돈이 있으면 전세가 장기적으로 유리해요.",
                        q("여자가 목돈이 있을 때 추천한 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("전세", "정답: '목돈이 있으면 전세가 유리하다'고 답했습니다."),
                                opt("월세", "🔴 빨강 - 비교 대상을 뒤바꾼 오답입니다."),
                                opt("반전세", "🔴 빨강 - 언급되지 않은 형태입니다."),
                                opt("둘 다 추천하지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 비교 대상(전세/월세)을 뒤바꿔 오답을 만듭니다.",
                                "[전월세 마인드맵] 조건(목돈 있음) → 추천(전세). 조건과 추천을 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "이사 견적",
                        "여자: 이사 견적 받아보셨어요?\n남자: 네, 포장이사로 80만 원 견적 받았어요.",
                        q("남자가 받은 포장이사 견적 금액으로 알맞은 것을 고르십시오.", List.of(
                                opt("80만 원", "정답: '포장이사로 80만 원 견적 받았다'고 답했습니다."),
                                opt("50만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("150만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("견적을 안 받았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 견적 금액 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[이사 마인드맵] 종류(포장이사) + 금액(80만 원). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "등기 신청",
                        "남자: 등기 신청은 어디서 해요?\n여자: 인터넷 등기소에서 온라인으로 신청할 수 있어요.",
                        q("등기 신청 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("인터넷 등기소에서 온라인 신청", "정답: '인터넷 등기소에서 온라인으로 신청할 수 있다'고 답했습니다."),
                                opt("법원에 직접 방문", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("우편으로만 신청", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("신청할 필요가 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[등기 마인드맵] 방법 = 인터넷 등기소(온라인). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "관리비 정산",
                        "여자: 이번 달 관리비가 왜 이렇게 많이 나왔어요?\n남자: 겨울이라 난방비가 추가돼서 그런 것 같아요.",
                        q("관리비가 많이 나온 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("난방비가 추가돼서", "정답: '난방비가 추가돼서 그런 것 같다'고 답했습니다."),
                                opt("수도세가 올라서", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("주차비가 추가돼서", "🟢 초록 - 언급되지 않은 이유입니다."),
                                opt("이유를 알 수 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 이유와 무관한 다른 이유로 오답을 만듭니다.",
                                "[관리비 마인드맵] 계절(겨울) → 원인(난방비 추가). 원인 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "부동산 계약",
                        "남자: 이 집 전세 계약 기간은 몇 년이에요?\n여자: 기본 2년 계약이고 협의하면 연장할 수 있어요.",
                        q("전세 계약의 기본 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2년", "정답: '기본 2년 계약'이라고 답했습니다."),
                                opt("1년", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("5년", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("계약 기간이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 계약 기간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[부동산 마인드맵] 기본 기간(2년) + 연장(협의 가능). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "전월세 계약",
                        "여자: 월세 계약할 때 중개수수료는 누가 내요?\n남자: 임대인과 임차인이 각각 부담해요.",
                        q("중개수수료 부담 방식으로 알맞은 것을 고르십시오.", List.of(
                                opt("임대인과 임차인이 각각 부담", "정답: '각각 부담한다'고 답했습니다."),
                                opt("임대인만 전액 부담", "🔴 빨강 - 명시된 내용과 반대되는 오답입니다."),
                                opt("임차인만 전액 부담", "🔴 빨강 - 명시된 내용과 반대되는 오답입니다."),
                                opt("수수료가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 부담 주체를 한쪽으로 치우치게 착각하게 합니다.",
                                "[전월세 마인드맵] 임대인(부담) + 임차인(부담). 양쪽 모두 부담함을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이사 견적",
                        "남자: 이사 날짜는 언제로 잡으셨어요?\n여자: 다음 달 15일로 예약했어요.",
                        q("이사 예정일로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 달 15일", "정답: '다음 달 15일로 예약했다'고 답했습니다."),
                                opt("이번 달 15일", "🔴 빨강 - '다음 달'을 '이번 달'로 바꾼 오답입니다."),
                                opt("다음 달 25일", "🔴 빨강 - 날짜를 바꾼 오답입니다."),
                                opt("날짜가 정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 월이나 날짜 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[이사 마인드맵] 월(다음 달) + 날짜(15일). 두 숫자를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "등기 신청",
                        "여자: 등기부등본은 어떻게 발급받아요?\n남자: 인터넷등기소에서 열람하거나 발급받을 수 있어요.",
                        q("등기부등본을 발급받는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("인터넷등기소에서 발급", "정답: '인터넷등기소에서 발급받을 수 있다'고 답했습니다."),
                                opt("주민센터에서 발급", "🔵 파랑 - 언급되지 않은 기관입니다."),
                                opt("은행에서 발급", "🔵 파랑 - 언급되지 않은 기관입니다."),
                                opt("발급이 불가능하다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 기관과 무관한 다른 기관으로 오답을 만듭니다.",
                                "[등기 마인드맵] 서류(등기부등본) + 발급처(인터넷등기소). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "관리비 정산",
                        "남자: 이사 나갈 때 관리비 정산은 어떻게 해요?\n여자: 관리사무소에서 최종 정산서를 받으시면 돼요.",
                        q("이사 시 관리비 정산 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("관리사무소에서 정산서 받기", "정답: '관리사무소에서 최종 정산서를 받으면 된다'고 답했습니다."),
                                opt("은행에서 정산하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("정산 없이 이사 가능하다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("집주인에게 직접 문의하기", "🟢 초록 - 언급되지 않은 방법입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[관리비 마인드맵] 절차 = 관리사무소 정산서. 기관명을 확인하세요."))
        );

        List<PassageSeed> listening3rd11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "부동산 계약",
                        "여자: 이 오피스텔은 주차 공간이 세대당 몇 대예요?\n남자: 세대당 1대씩 배정돼요.",
                        q("세대당 배정되는 주차 공간 수로 알맞은 것을 고르십시오.", List.of(
                                opt("1대", "정답: '세대당 1대씩 배정된다'고 답했습니다."),
                                opt("2대", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("주차 공간이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("3대", "🔴 빨강 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔴 주차 대수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[부동산 마인드맵] 기준(세대당) + 배정(1대). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "전월세 계약",
                        "남자: 반전세는 뭐예요?\n여자: 보증금을 낮추는 대신 매달 월세를 조금씩 내는 방식이에요.",
                        q("반전세에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("보증금을 낮추고 월세를 내는 방식", "정답: 대화에 그대로 나와 있습니다."),
                                opt("보증금만 내고 월세는 없는 방식", "🔵 파랑 - '순수 전세'와 혼동하게 하는 오답입니다."),
                                opt("월세만 내고 보증금은 없는 방식", "🔵 파랑 - '순수 월세'와 혼동하게 하는 오답입니다."),
                                opt("보증금과 월세 둘 다 없는 방식", "🔵 파랑 - 언급되지 않은 방식입니다.")
                        ), 0, "🔵 전세/월세/반전세 개념을 혼동하게 합니다.",
                                "[전월세 마인드맵] 반전세 = 낮은 보증금 + 월세. 두 요소를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이사 견적",
                        "여자: 사다리차 비용은 견적에 포함돼 있나요?\n남자: 아니요, 사다리차는 별도 비용이에요.",
                        q("사다리차 비용에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("사다리차는 별도 비용이다.", "정답: '사다리차는 별도 비용'이라고 답했습니다."),
                                opt("사다리차 비용은 견적에 포함돼 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("사다리차는 무료로 제공된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("사다리차는 이용할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 포함/별도 여부를 반대로 착각하게 하거나 무료로 왜곡합니다.",
                                "[이사 마인드맵] 기본 견적 ≠ 사다리차(별도). '아니요'라는 부정 답변을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "등기 신청",
                        "남자: 소유권 이전 등기는 언제까지 해야 해요?\n여자: 잔금 지급일로부터 60일 이내에 해야 해요.",
                        q("소유권 이전 등기 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("잔금 지급일로부터 60일 이내", "정답: 대화에 그대로 나와 있습니다."),
                                opt("잔금 지급일로부터 10일 이내", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("계약일로부터 60일 이내", "🔴 빨강 - 기준일을 바꾼 오답입니다."),
                                opt("기한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기준일이나 기한 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[등기 마인드맵] 기준(잔금 지급일) + 기한(60일). 두 정보를 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "관리비 정산",
                        "여자: 관리비 자동이체 신청은 어떻게 해요?\n남자: 관리사무소에 통장 사본을 제출하시면 돼요.",
                        q("관리비 자동이체 신청 시 제출해야 하는 서류로 알맞은 것을 고르십시오.", List.of(
                                opt("통장 사본", "정답: '통장 사본을 제출하면 된다'고 답했습니다."),
                                opt("신분증 사본", "🔵 파랑 - 언급되지 않은 서류입니다."),
                                opt("계약서 사본", "🔵 파랑 - 언급되지 않은 서류입니다."),
                                opt("서류가 필요 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 서류와 무관한 다른 서류로 오답을 만듭니다.",
                                "[관리비 마인드맵] 신청 → 제출 서류(통장 사본). 서류명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "부동산 계약",
                        "남자: 이 집 융자가 껴 있다는데 계약해도 괜찮을까요?\n여자: 잔금 전에 융자를 상환한다는 특약을 넣으면 안전해요.",
                        q("여자가 제안한 안전 조치로 알맞은 것을 고르십시오.", List.of(
                                opt("잔금 전 융자 상환 특약 넣기", "정답: '특약을 넣으면 안전하다'고 답했습니다."),
                                opt("계약을 포기하기", "🟢 초록 - 언급되지 않은 조치입니다."),
                                opt("융자를 그대로 두기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("변호사를 선임하기", "🟢 초록 - 언급되지 않은 조치입니다.")
                        ), 0, "🟢 실제 언급된 조치와 무관한 다른 행동으로 오답을 만듭니다.",
                                "[부동산 마인드맵] 위험(융자) → 조치(상환 특약). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "전월세 계약",
                        "여자: 계약 만료 전에 이사 나가면 어떻게 되나요?\n남자: 새로운 세입자를 구할 때까지 월세를 내야 할 수도 있어요.",
                        q("계약 만료 전 이사 시 발생할 수 있는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("새 세입자 구할 때까지 월세를 낼 수 있다.", "정답: 대화에 그대로 나와 있습니다."),
                                opt("아무 문제 없이 나갈 수 있다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("보증금을 두 배로 돌려받는다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("계약이 자동으로 연장된다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 실제 언급된 결과와 반대되거나 무관한 내용으로 오답을 만듭니다.",
                                "[전월세 마인드맵] 조기 퇴거 → 결과(새 세입자까지 월세 부담). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "이사 견적",
                        "남자: 이사할 때 에어컨 이전 설치비도 포함돼요?\n여자: 아니요, 에어컨 설치는 전문 업체를 따로 불러야 해요.",
                        q("에어컨 이전 설치에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("전문 업체를 따로 불러야 한다.", "정답: '전문 업체를 따로 불러야 한다'고 답했습니다."),
                                opt("이사 견적에 포함돼 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("에어컨은 이전 설치할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("무료로 설치해 준다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 포함 여부를 반대로 착각하게 하거나 무료로 왜곡합니다.",
                                "[이사 마인드맵] 기본 견적 ≠ 에어컨 설치(별도 업체). '아니요'를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "등기 신청",
                        "여자: 등기 수수료는 얼마나 들어요?\n남자: 부동산 가격에 따라 다른데 보통 몇십만 원 정도 들어요.",
                        q("등기 수수료에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("부동산 가격에 따라 달라진다.", "정답: '부동산 가격에 따라 다르다'고 답했습니다."),
                                opt("수수료는 항상 동일하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("수수료가 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("수수료는 백만 원 이상이다.", "🔵 파랑 - '몇십만 원 정도'와 다른 오답입니다.")
                        ), 0, "🔵 수수료가 고정이라고 착각하게 하거나 금액대를 바꿔 오답을 만듭니다.",
                                "[등기 마인드맵] 기준(부동산 가격) → 수수료(변동). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "관리비 정산",
                        "남자: 관리비 미납하면 어떻게 되나요?\n여자: 연체료가 붙고 계속 미납되면 단전, 단수 조치가 될 수 있어요.",
                        q("관리비 장기 미납 시 발생할 수 있는 일로 알맞은 것을 고르십시오.", List.of(
                                opt("단전, 단수 조치", "정답: '단전, 단수 조치가 될 수 있다'고 답했습니다."),
                                opt("아무 조치도 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("관리비가 자동 면제된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("이사를 강제로 가야 한다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 실제 언급된 결과와 반대되거나 무관한 내용으로 오답을 만듭니다.",
                                "[관리비 마인드맵] 미납 → 연체료 → 단전·단수. 순서대로 색칠하세요."))
        );

        List<PassageSeed> reading3rd21to30 = List.of(
                onePassage(PassageCategory.READING, "부동산 매물 정보",
                        "[매물 정보] 이 아파트는 전용면적 84제곱미터이며, 남향으로 채광이 좋습니다.",
                        q("이 매물 정보로 맞는 것을 고르십시오.", List.of(
                                opt("이 아파트는 남향이다.", "정답: '남향으로 채광이 좋다'고 명시했습니다."),
                                opt("이 아파트는 북향이다.", "🔵 파랑 - '남향'을 '북향'으로 바꾼 오답입니다."),
                                opt("전용면적은 60제곱미터다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("채광이 좋지 않다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 방향이나 면적 숫자, 채광 평가를 바꿔 오답을 만듭니다.",
                                "[매물 마인드맵] 면적(84㎡) + 방향(남향) + 특징(채광 좋음). 색칠하세요.")),
                onePassage(PassageCategory.READING, "전월세 계약서 요약",
                        "[계약서 요약] 보증금은 5천만 원, 월세는 50만 원이며, 매달 25일에 납부합니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("월세 납부일은 매달 25일이다.", "정답: 계약서에 그대로 명시되어 있습니다."),
                                opt("월세 납부일은 매달 5일이다.", "🔵 파랑 - 날짜를 바꾼 오답입니다."),
                                opt("보증금은 5백만 원이다.", "🔵 파랑 - 자릿수를 바꾼 오답입니다."),
                                opt("월세는 100만 원이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 납부일이나 금액 숫자를 바꿔 오답을 만듭니다.",
                                "[계약서 마인드맵] 보증금(5천만) + 월세(50만) + 납부일(25일). 색칠하세요.")),
                onePassage(PassageCategory.READING, "이사 견적서",
                        "[이사 견적서] 포장이사 기본 견적에는 포장재 비용이 포함되어 있습니다.",
                        q("이 견적서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("포장재 비용이 기본 견적에 포함돼 있다.", "정답: 견적서에 그대로 명시되어 있습니다."),
                                opt("포장재는 별도로 구매해야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("이 견적은 반포장이사다.", "🔵 파랑 - '포장이사'를 '반포장이사'로 바꾼 오답입니다."),
                                opt("포장 서비스는 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 포함 여부나 이사 종류를 바꿔 오답을 만듭니다.",
                                "[견적서 마인드맵] 종류(포장이사) + 포함(포장재). 색칠하세요.")),
                onePassage(PassageCategory.READING, "등기소 안내문",
                        "[등기소 안내문] 등기 신청 시 등록면허세를 미리 납부한 영수증을 첨부해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("등록면허세 납부 영수증을 첨부해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("영수증 첨부는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("등록면허세는 등기 후에 납부한다.", "🔵 파랑 - '미리 납부'라는 순서와 반대되는 오답입니다."),
                                opt("등록면허세가 면제된다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 첨부 필요성이나 납부 시점(전/후)을 반대로 착각하게 합니다.",
                                "[등기소 마인드맵] 납부(사전) → 영수증 첨부(신청 시). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "관리비 명세서",
                        "[관리비 명세서] 공용관리비 8만 원, 전기료 3만 원, 수도료 2만 원, 합계 13만 원.",
                        q("이 명세서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("합계 금액은 13만 원이다.", "정답: '합계 13만 원'이라고 명시되어 있습니다."),
                                opt("공용관리비가 가장 저렴하다.", "🔵 파랑 - 실제로는 공용관리비가 가장 비쌉니다."),
                                opt("전기료가 수도료보다 저렴하다.", "🔵 파랑 - 3만 원과 2만 원의 크기를 뒤바꾼 오답입니다."),
                                opt("항목은 두 가지뿐이다.", "🔵 파랑 - 실제로는 세 항목입니다.")
                        ), 0, "🔵 항목별 금액 크기를 뒤바꾸거나 항목 개수를 착각하게 합니다.",
                                "[명세서 마인드맵] 공용관리비(8만)>전기료(3만)>수도료(2만). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "부동산 매물 정보",
                        "[매물 정보] 이 원룸은 즉시 입주 가능하며, 반려동물 동반 입주도 가능합니다.",
                        q("이 매물 정보로 맞는 것을 고르십시오.", List.of(
                                opt("반려동물과 함께 입주할 수 있다.", "정답: '반려동물 동반 입주도 가능하다'고 명시했습니다."),
                                opt("반려동물은 입주할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("입주까지 한 달이 걸린다.", "🔵 파랑 - '즉시 입주 가능'과 반대되는 오답입니다."),
                                opt("이 매물은 투룸이다.", "🔵 파랑 - '원룸'을 '투룸'으로 바꾼 오답입니다.")
                        ), 0, "🔵 반려동물 동반 가능 여부나 입주 시점, 방 개수를 바꿔 오답을 만듭니다.",
                                "[매물 마인드맵] 입주(즉시) + 반려동물(동반 가능). 색칠하세요.")),
                onePassage(PassageCategory.READING, "전월세 계약서 요약",
                        "[계약서 요약] 원상복구 의무는 임차인에게 있으며, 자연 마모는 예외로 합니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("자연 마모는 원상복구 대상이 아니다.", "정답: '자연 마모는 예외로 한다'고 명시했습니다."),
                                opt("자연 마모도 원상복구해야 한다.", "🔵 파랑 - 명시된 예외와 반대되는 오답입니다."),
                                opt("원상복구 의무는 임대인에게 있다.", "🔵 파랑 - '임차인'을 '임대인'으로 바꾼 오답입니다."),
                                opt("원상복구 의무가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 예외 대상을 반대로 착각하게 하거나 의무 주체를 바꿔 오답을 만듭니다.",
                                "[계약서 마인드맵] 원칙(임차인 원상복구) + 예외(자연 마모). 색칠하세요.")),
                onePassage(PassageCategory.READING, "이사 견적서",
                        "[이사 견적서] 야간이나 주말 이사는 기본 요금의 20%가 추가됩니다.",
                        q("이 견적서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("야간·주말 이사는 요금이 추가된다.", "정답: 견적서에 그대로 명시되어 있습니다."),
                                opt("야간·주말 이사는 할인된다.", "🔵 파랑 - '추가'를 '할인'으로 착각하게 하는 오답입니다."),
                                opt("추가 요금은 50%다.", "🔵 파랑 - 비율 숫자를 바꾼 오답입니다."),
                                opt("평일 이사만 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 추가/할인을 뒤바꾸거나 비율 숫자를 바꿔 오답을 만듭니다.",
                                "[견적서 마인드맵] 조건(야간·주말) → 추가 요금(20%). 색칠하세요.")),
                onePassage(PassageCategory.READING, "등기소 안내문",
                        "[등기소 안내문] 등기 완료까지 통상 3~5일이 소요되며, 진행 상황은 온라인으로 조회 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("진행 상황을 온라인으로 조회할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("진행 상황은 조회할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("등기는 당일 완료된다.", "🔵 파랑 - '3~5일'이라는 기간과 반대되는 오답입니다."),
                                opt("진행 상황은 전화로만 확인 가능하다.", "🔵 파랑 - '온라인'을 '전화'로 바꾼 오답입니다.")
                        ), 0, "🔵 조회 가능 여부나 소요 기간, 조회 방법을 바꿔 오답을 만듭니다.",
                                "[등기소 마인드맵] 기간(3~5일) + 조회(온라인). 색칠하세요.")),
                onePassage(PassageCategory.READING, "관리비 명세서",
                        "[관리비 명세서] 장기수선충당금은 추후 퇴거 시 임대인에게 반환을 요청할 수 있습니다.",
                        q("이 명세서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("퇴거 시 임대인에게 반환 요청할 수 있다.", "정답: 명세서에 그대로 명시되어 있습니다."),
                                opt("장기수선충당금은 반환되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("반환은 관리사무소에 요청한다.", "🔵 파랑 - '임대인'을 '관리사무소'로 바꾼 오답입니다."),
                                opt("입주 시 반환 요청해야 한다.", "🔵 파랑 - '퇴거 시'를 '입주 시'로 바꾼 오답입니다.")
                        ), 0, "🔵 반환 가능 여부나 요청 대상, 시점을 바꿔 오답을 만듭니다.",
                                "[명세서 마인드맵] 항목(장기수선충당금) + 반환 시점(퇴거 시) + 대상(임대인). 색칠하세요."))
        );

        List<PassageSeed> reading3rd31to40 = List.of(
                onePassage(PassageCategory.READING, "부동산 매물 정보",
                        "[매물 정보] 이 상가는 역세권에 위치해 있으며, 유동인구가 많은 곳입니다.",
                        q("이 매물 정보로 맞는 것을 고르십시오.", List.of(
                                opt("역세권에 위치해 있다.", "정답: '역세권에 위치해 있다'고 명시했습니다."),
                                opt("역에서 멀리 떨어져 있다.", "🔵 파랑 - '역세권'과 반대되는 오답입니다."),
                                opt("유동인구가 적은 곳이다.", "🔵 파랑 - '유동인구가 많다'와 반대되는 오답입니다."),
                                opt("이 매물은 주택이다.", "🔵 파랑 - '상가'를 '주택'으로 바꾼 오답입니다.")
                        ), 0, "🔵 위치나 유동인구, 매물 종류를 반대로 착각하게 합니다.",
                                "[매물 마인드맵] 위치(역세권) + 특징(유동인구 많음). 색칠하세요.")),
                onePassage(PassageCategory.READING, "전월세 계약서 요약",
                        "[계약서 요약] 특약사항으로 반려동물 사육을 금지한다는 조항이 포함되어 있습니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("반려동물 사육이 금지되어 있다.", "정답: 계약서에 그대로 명시되어 있습니다."),
                                opt("반려동물을 자유롭게 키울 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("특약사항이 없는 계약이다.", "🔵 파랑 - 명시된 특약과 반대되는 오답입니다."),
                                opt("반려동물은 허가 후 가능하다.", "🔵 파랑 - '금지'와 다른 오답입니다.")
                        ), 0, "🔵 사육 가능 여부나 특약 유무를 반대로 착각하게 합니다.",
                                "[계약서 마인드맵] 특약(반려동물 금지). 금지 표현에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "이사 견적서",
                        "[이사 견적서] 폐기물 처리 비용은 별도이며, 처리할 물품 목록을 미리 알려주셔야 합니다.",
                        q("이 견적서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("폐기물 목록을 미리 알려야 한다.", "정답: 견적서에 그대로 명시되어 있습니다."),
                                opt("폐기물 처리는 기본 견적에 포함된다.", "🔵 파랑 - '별도'라는 내용과 반대되는 오답입니다."),
                                opt("폐기물은 당일 알려도 된다.", "🔵 파랑 - '미리'라는 표현과 반대되는 오답입니다."),
                                opt("폐기물 처리 서비스는 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 포함 여부나 통보 시점을 반대로 착각하게 합니다.",
                                "[견적서 마인드맵] 비용(별도) + 통보(사전). 순서와 조건을 색칠하세요.")),
                onePassage(PassageCategory.READING, "등기소 안내문",
                        "[등기소 안내문] 등기 신청 서류에 오류가 있으면 보정 명령이 발송되며, 기한 내 보정하지 않으면 각하됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("기한 내 보정하지 않으면 각하된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("오류가 있어도 등기는 그대로 진행된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("보정 명령은 무기한이다.", "🔵 파랑 - '기한 내'라는 표현과 반대되는 오답입니다."),
                                opt("보정 명령은 발송되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 보정 기한이나 각하 여부를 반대로 착각하게 합니다.",
                                "[등기소 마인드맵] 오류 → 보정 명령 → 기한 내 미이행(각하). 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "관리비 명세서",
                        "[관리비 명세서] 관리비 이의 신청은 명세서 발급일로부터 30일 이내에 가능합니다.",
                        q("이 명세서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이의 신청은 30일 이내에 가능하다.", "정답: 명세서에 그대로 명시되어 있습니다."),
                                opt("이의 신청 기한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("이의 신청은 7일 이내다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("이의 신청은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 기한 숫자를 바꾸거나 신청 가능 여부를 반대로 착각하게 합니다.",
                                "[명세서 마인드맵] 기한(발급일로부터 30일). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "부동산 매물 정보",
                        "[매물 정보] 이 빌라는 리모델링이 완료되어 새 건물처럼 깨끗한 상태입니다.",
                        q("이 매물 정보로 맞는 것을 고르십시오.", List.of(
                                opt("리모델링이 완료된 상태다.", "정답: '리모델링이 완료되었다'고 명시했습니다."),
                                opt("리모델링이 필요한 상태다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("낡고 오래된 상태다.", "🔵 파랑 - '새 건물처럼 깨끗하다'와 반대되는 오답입니다."),
                                opt("리모델링 예정이다.", "🔵 파랑 - '완료되었다'는 과거 완료와 다른 오답입니다.")
                        ), 0, "🔵 리모델링 완료 여부나 상태 평가를 반대로 착각하게 합니다.",
                                "[매물 마인드맵] 상태(리모델링 완료, 깨끗함). 완료 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "전월세 계약서 요약",
                        "[계약서 요약] 계약 갱신 시 임대료는 직전 계약 대비 5% 이내로만 인상할 수 있습니다.",
                        q("이 계약서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("임대료 인상은 5% 이내로 제한된다.", "정답: 계약서에 그대로 명시되어 있습니다."),
                                opt("임대료 인상에 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("인상률은 10%까지 가능하다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("임대료는 인하만 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 인상 제한 여부나 비율 숫자를 바꿔 오답을 만듭니다.",
                                "[계약서 마인드맵] 갱신 시 인상 제한(5% 이내). 비율에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "이사 견적서",
                        "[이사 견적서] 사전 방문 견적은 무료이며, 방문 후 정확한 금액이 확정됩니다.",
                        q("이 견적서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("사전 방문 견적은 무료다.", "정답: 견적서에 그대로 명시되어 있습니다."),
                                opt("사전 방문 견적은 유료다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("방문 없이 금액이 확정된다.", "🔵 파랑 - '방문 후 확정'과 반대되는 오답입니다."),
                                opt("견적 서비스는 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 무료/유료 여부나 확정 시점을 반대로 착각하게 합니다.",
                                "[견적서 마인드맵] 방문(무료) → 금액 확정. 순서와 비용을 색칠하세요.")),
                onePassage(PassageCategory.READING, "등기소 안내문",
                        "[등기소 안내문] 등기필증을 분실한 경우 재발급이 불가능하니 별도의 확인서면 절차를 거쳐야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("등기필증은 재발급이 불가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("등기필증은 쉽게 재발급받을 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("분실해도 아무 절차가 필요 없다.", "🔵 파랑 - '별도의 확인서면 절차'와 반대되는 오답입니다."),
                                opt("등기필증은 분실될 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 재발급 가능 여부나 절차 필요성을 반대로 착각하게 합니다.",
                                "[등기소 마인드맵] 분실 → 재발급 불가 → 별도 절차 필요. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.READING, "관리비 명세서",
                        "[관리비 명세서] 세대별 검침값 차이로 인한 수도료 오차는 익월에 정산됩니다.",
                        q("이 명세서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("수도료 오차는 다음 달에 정산된다.", "정답: '익월에 정산된다'고 명시했습니다."),
                                opt("수도료 오차는 정산되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("오차는 당월에 바로 정산된다.", "🔵 파랑 - '익월'을 '당월'로 바꾼 오답입니다."),
                                opt("검침은 하지 않는다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 정산 시점(익월/당월)을 바꾸거나 정산 여부를 반대로 착각하게 합니다.",
                                "[명세서 마인드맵] 검침 차이 → 오차 → 익월 정산. 순서대로 색칠하세요."))
        );

        List<PassageSeed> listening4th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "도서 대출 연장",
                        "여자: 빌린 책 반납일이 다가오는데 더 읽고 싶어요.\n남자: 앱에서 대출 연장 신청하시면 일주일 더 빌릴 수 있어요.",
                        q("대출 연장 시 추가로 빌릴 수 있는 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("일주일", "정답: '일주일 더 빌릴 수 있다'고 답했습니다."),
                                opt("하루", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("한 달", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("연장이 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 연장 기간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[도서 마인드맵] 방법(앱 신청) + 연장 기간(일주일). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "문화센터 강좌",
                        "남자: 문화센터 요리 강좌 신청하셨어요?\n여자: 네, 다음 주 화요일부터 시작해요.",
                        q("요리 강좌가 시작하는 요일로 알맞은 것을 고르십시오.", List.of(
                                opt("화요일", "정답: '다음 주 화요일부터 시작한다'고 답했습니다."),
                                opt("월요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("목요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("시작일이 정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 요일을 슬쩍 바꿔 오답을 만듭니다.",
                                "[문화센터 마인드맵] 시점(다음 주) + 요일(화요일). 숫자와 요일을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "청소년 상담",
                        "여자: 청소년 상담은 무료인가요?\n남자: 네, 청소년센터에서 무료로 상담받을 수 있어요.",
                        q("청소년 상담 비용으로 알맞은 것을 고르십시오.", List.of(
                                opt("무료", "정답: '무료로 상담받을 수 있다'고 답했습니다."),
                                opt("유료", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("첫 회만 무료", "🔵 파랑 - 언급되지 않은 조건입니다."),
                                opt("상담이 불가능하다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 무료/유료 여부를 반대로 착각하게 하거나 조건을 추가합니다.",
                                "[상담 마인드맵] 대상(청소년) + 비용(무료). 핵심 단어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "노인 복지관",
                        "남자: 노인 복지관에서 무슨 프로그램을 하나요?\n여자: 서예, 게이트볼, 컴퓨터 교실 같은 프로그램이 있어요.",
                        q("노인 복지관에서 제공하는 프로그램으로 알맞은 것을 고르십시오.", List.of(
                                opt("서예, 게이트볼, 컴퓨터 교실", "정답: 대화에 그대로 나와 있습니다."),
                                opt("수영, 요가, 필라테스", "🔵 파랑 - 언급되지 않은 프로그램입니다."),
                                opt("영어 회화, 중국어", "🔵 파랑 - 언급되지 않은 프로그램입니다."),
                                opt("프로그램이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 프로그램과 무관한 다른 프로그램으로 오답을 만듭니다.",
                                "[복지관 마인드맵] 프로그램(서예+게이트볼+컴퓨터). 세 가지를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "다문화 지원 센터",
                        "여자: 한국어 교육은 어디서 받을 수 있어요?\n남자: 다문화가족지원센터에서 무료로 배울 수 있어요.",
                        q("한국어 교육을 받을 수 있는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("다문화가족지원센터", "정답: '다문화가족지원센터에서 배울 수 있다'고 답했습니다."),
                                opt("주민센터", "🟢 초록 - 언급되지 않은 장소입니다."),
                                opt("구청", "🟢 초록 - 언급되지 않은 장소입니다."),
                                opt("배울 수 있는 곳이 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 기관과 무관한 다른 기관으로 오답을 만듭니다.",
                                "[다문화 마인드맵] 교육(한국어) + 기관(다문화가족지원센터). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "도서 대출 연장",
                        "남자: 대출 연장은 몇 번까지 가능해요?\n여자: 최대 2번까지 연장할 수 있어요.",
                        q("대출 연장 가능 횟수로 알맞은 것을 고르십시오.", List.of(
                                opt("최대 2번", "정답: '최대 2번까지 연장할 수 있다'고 답했습니다."),
                                opt("최대 1번", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("최대 5번", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("횟수 제한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 연장 횟수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[도서 마인드맵] 질문(연장 횟수) → 답(2번). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "문화센터 강좌",
                        "여자: 이 강좌 수강료는 얼마예요?\n남자: 한 달에 5만 원이에요.",
                        q("강좌의 한 달 수강료로 알맞은 것을 고르십시오.", List.of(
                                opt("5만 원", "정답: '한 달에 5만 원'이라고 답했습니다."),
                                opt("3만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("10만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("무료다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 수강료 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[문화센터 마인드맵] 기간(한 달) + 요금(5만 원). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "청소년 상담",
                        "남자: 상담 예약은 어떻게 해요?\n여자: 홈페이지에서 원하는 시간을 선택해서 예약하면 돼요.",
                        q("상담 예약 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("홈페이지에서 시간 선택 후 예약", "정답: '홈페이지에서 원하는 시간을 선택해서 예약하면 된다'고 답했습니다."),
                                opt("전화로 예약", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("직접 방문해서 예약", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("예약이 필요 없다.", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 실제 언급된 방법과 무관한 다른 절차로 오답을 만듭니다.",
                                "[상담 마인드맵] 방법(홈페이지) + 절차(시간 선택). 수단을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "노인 복지관",
                        "여자: 이용하려면 몇 세 이상이어야 해요?\n남자: 만 60세 이상부터 이용 가능해요.",
                        q("노인 복지관 이용 가능 연령으로 알맞은 것을 고르십시오.", List.of(
                                opt("만 60세 이상", "정답: '만 60세 이상부터 이용 가능하다'고 답했습니다."),
                                opt("만 50세 이상", "🔴 빨강 - 나이 숫자를 바꾼 오답입니다."),
                                opt("만 70세 이상", "🔴 빨강 - 나이 숫자를 바꾼 오답입니다."),
                                opt("나이 제한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 연령 조건 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[복지관 마인드맵] 조건(만 60세 이상). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "다문화 지원 센터",
                        "남자: 통번역 서비스도 받을 수 있나요?\n여자: 네, 병원이나 관공서 방문 시 통역 지원을 받을 수 있어요.",
                        q("통역 지원을 받을 수 있는 상황으로 알맞은 것을 고르십시오.", List.of(
                                opt("병원이나 관공서 방문 시", "정답: 대화에 그대로 나와 있습니다."),
                                opt("쇼핑할 때만", "🔵 파랑 - 언급되지 않은 상황입니다."),
                                opt("여행 갈 때만", "🔵 파랑 - 언급되지 않은 상황입니다."),
                                opt("통역 지원이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 상황과 무관한 다른 상황으로 오답을 만듭니다.",
                                "[다문화 마인드맵] 상황(병원, 관공서) + 지원(통역). 두 장소를 확인하세요."))
        );

        List<PassageSeed> listening4th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "도서 대출 연장",
                        "여자: 다른 사람이 예약한 책도 연장할 수 있어요?\n남자: 아니요, 예약자가 있으면 연장이 안 돼요.",
                        q("대출 연장이 불가능한 경우로 알맞은 것을 고르십시오.", List.of(
                                opt("다른 사람이 예약했을 때", "정답: '예약자가 있으면 연장이 안 된다'고 답했습니다."),
                                opt("반납일이 지났을 때만", "🔴 빨강 - 언급되지 않은 조건입니다."),
                                opt("신간 도서일 때만", "🔴 빨강 - 언급되지 않은 조건입니다."),
                                opt("항상 연장 가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 조건(예약자 존재)과 무관한 다른 조건으로 오답을 만듭니다.",
                                "[도서 마인드맵] 예외(예약자 있음) → 연장 불가. '아니요'라는 응답을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "문화센터 강좌",
                        "남자: 강좌 정원이 다 찼는데 대기 신청 가능해요?\n여자: 네, 대기 신청하시면 자리 나는 대로 연락드려요.",
                        q("여자가 안내한 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("대기 신청하기", "정답: '대기 신청하시면 자리 나는 대로 연락드린다'고 답했습니다."),
                                opt("다음 기수를 기다리기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("다른 강좌를 신청하기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("신청이 아예 불가능하다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 안내된 방법과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[문화센터 마인드맵] 정원 마감 → 대기 신청. 방법을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "청소년 상담",
                        "여자: 부모님도 같이 상담받을 수 있나요?\n남자: 네, 가족 상담 프로그램을 신청하시면 함께 받을 수 있어요.",
                        q("부모님과 함께 상담받을 수 있는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("가족 상담 프로그램 신청", "정답: '가족 상담 프로그램을 신청하면 함께 받을 수 있다'고 답했습니다."),
                                opt("개인 상담만 가능", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("부모님은 참여 불가", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("추가 비용을 내야 한다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 실제 방법과 반대되거나 무관한 다른 조건으로 오답을 만듭니다.",
                                "[상담 마인드맵] 프로그램(가족 상담) → 함께 참여 가능. 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "노인 복지관",
                        "남자: 복지관 셔틀버스도 있나요?\n여자: 네, 오전 9시와 오후 2시에 셔틀버스가 운행돼요.",
                        q("셔틀버스가 운행되는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("오전 9시와 오후 2시", "정답: 대화에 그대로 나와 있습니다."),
                                opt("오전 7시와 오후 5시", "🔴 빨강 - 시간을 바꾼 오답입니다."),
                                opt("오전 9시만", "🔴 빨강 - 언급된 시간 중 하나를 빠뜨린 오답입니다."),
                                opt("셔틀버스가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시간을 바꾸거나 언급된 두 시간 중 하나를 빠뜨려 오답을 만듭니다.",
                                "[복지관 마인드맵] 운행(오전 9시+오후 2시). 두 시간을 모두 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "다문화 지원 센터",
                        "여자: 자녀 학습 지원 프로그램도 있어요?\n남자: 네, 다문화 가정 자녀를 위한 방과 후 학습 지원이 있어요.",
                        q("다문화 지원 센터의 자녀 지원 프로그램으로 알맞은 것을 고르십시오.", List.of(
                                opt("방과 후 학습 지원", "정답: '방과 후 학습 지원이 있다'고 답했습니다."),
                                opt("유학 지원", "🔵 파랑 - 언급되지 않은 프로그램입니다."),
                                opt("장학금 지원", "🔵 파랑 - 언급되지 않은 프로그램입니다."),
                                opt("자녀 지원 프로그램이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 프로그램과 무관한 다른 프로그램으로 오답을 만듭니다.",
                                "[다문화 마인드맵] 대상(자녀) + 지원(방과 후 학습). 프로그램명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "도서 대출 연장",
                        "남자: 전자책도 대출 연장이 되나요?\n여자: 아니요, 전자책은 대출 기간이 자동으로 종료돼서 연장이 안 돼요.",
                        q("전자책 대출에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("전자책은 연장이 안 된다.", "정답: '전자책은 연장이 안 된다'고 답했습니다."),
                                opt("전자책도 연장이 가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("전자책은 대출 기간이 없다.", "🔵 파랑 - '자동으로 종료된다'는 내용과 다른 오답입니다."),
                                opt("전자책은 대출할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 연장 가능 여부를 반대로 착각하게 합니다.",
                                "[도서 마인드맵] 종이책 ≠ 전자책(연장 불가). '아니요'를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "문화센터 강좌",
                        "여자: 강좌 준비물은 따로 있나요?\n남자: 네, 첫 시간에 안내해 드리니 그때 준비하시면 돼요.",
                        q("강좌 준비물을 준비하는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("첫 시간에 안내받은 후", "정답: '첫 시간에 안내해 드리니 그때 준비하면 된다'고 답했습니다."),
                                opt("신청 전에 미리", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("마지막 시간에", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("준비물이 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 준비 시점을 슬쩍 바꾸거나 준비물이 없다고 왜곡합니다.",
                                "[문화센터 마인드맵] 첫 시간(안내) → 준비. 시점 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "청소년 상담",
                        "남자: 상담 내용은 부모님께 알려지나요?\n여자: 아니요, 상담 내용은 철저히 비밀이 보장돼요.",
                        q("상담 내용의 비밀 보장에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("상담 내용은 비밀이 보장된다.", "정답: '철저히 비밀이 보장된다'고 답했습니다."),
                                opt("상담 내용은 부모님께 알려진다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("상담 내용은 학교에 공개된다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("비밀 보장이 안 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 비밀 보장 여부를 반대로 착각하게 합니다.",
                                "[상담 마인드맵] 원칙(비밀 보장). '아니요'라는 부정 답변을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "노인 복지관",
                        "여자: 컴퓨터 교실은 초보자도 들을 수 있어요?\n남자: 네, 초급반이 따로 있어서 컴퓨터를 처음 배우는 분도 가능해요.",
                        q("초급반 컴퓨터 교실의 대상으로 알맞은 것을 고르십시오.", List.of(
                                opt("컴퓨터를 처음 배우는 사람", "정답: '컴퓨터를 처음 배우는 분도 가능하다'고 답했습니다."),
                                opt("컴퓨터 전문가만", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("자격증 소지자만", "🔴 빨강 - 언급되지 않은 조건입니다."),
                                opt("초급반은 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 대상(초보자)과 반대되거나 무관한 조건으로 오답을 만듭니다.",
                                "[복지관 마인드맵] 초급반 → 대상(처음 배우는 사람). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "다문화 지원 센터",
                        "남자: 국적 취득 관련 상담도 받을 수 있나요?\n여자: 네, 법률 전문가와 함께하는 국적 상담을 매주 목요일에 진행해요.",
                        q("국적 상담이 진행되는 요일로 알맞은 것을 고르십시오.", List.of(
                                opt("목요일", "정답: '매주 목요일에 진행한다'고 답했습니다."),
                                opt("월요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("토요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("상담이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 요일 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[다문화 마인드맵] 상담(국적) + 요일(목요일). 요일을 확인하세요."))
        );

        List<PassageSeed> reading4th21to30 = List.of(
                onePassage(PassageCategory.READING, "도서관 대출 연장 안내",
                        "[도서관 안내] 대출 연장은 반납 예정일 3일 전부터 홈페이지에서 신청할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("반납 예정일 3일 전부터 연장 신청이 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("반납일 당일에만 연장할 수 있다.", "🔵 파랑 - '3일 전부터'라는 내용과 반대되는 오답입니다."),
                                opt("연장 신청은 전화로만 가능하다.", "🔵 파랑 - '홈페이지에서'와 다른 오답입니다."),
                                opt("연장 신청 기간에 제한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 가능 시점이나 방법을 바꿔 오답을 만듭니다.",
                                "[도서관 마인드맵] 시점(반납 3일 전부터) + 방법(홈페이지). 색칠하세요.")),
                onePassage(PassageCategory.READING, "문화센터 강좌 안내",
                        "[강좌 안내] 재수강생은 신규 수강생보다 우선 등록 기회가 주어집니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("재수강생은 우선 등록 기회를 받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("신규 수강생이 먼저 등록한다.", "🔵 파랑 - 명시된 순서와 반대되는 오답입니다."),
                                opt("등록 순서는 상관없다.", "🔵 파랑 - 명시된 우선순위와 반대되는 오답입니다."),
                                opt("재수강은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 등록 우선순위를 반대로 착각하게 합니다.",
                                "[강좌 마인드맵] 재수강생(우선) vs 신규 수강생. 우선순위를 색칠하세요.")),
                onePassage(PassageCategory.READING, "청소년 상담 안내문",
                        "[상담 안내문] 상담은 만 9세부터 만 24세까지의 청소년 및 청년을 대상으로 합니다.",
                        q("이 안내문의 상담 대상 연령으로 알맞은 것을 고르십시오.", List.of(
                                opt("만 9세부터 만 24세까지", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("만 13세부터 만 19세까지", "🔵 파랑 - 연령 범위를 바꾼 오답입니다."),
                                opt("연령 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("성인만 대상이다.", "🔵 파랑 - 명시된 대상과 반대되는 오답입니다.")
                        ), 0, "🔵 연령 범위를 바꾸거나 제한이 없다고 왜곡합니다.",
                                "[상담 마인드맵] 대상 연령(9세~24세). 숫자 범위를 색칠하세요.")),
                onePassage(PassageCategory.READING, "노인 복지관 프로그램",
                        "[복지관 프로그램] 건강 체조 프로그램은 매일 오전 10시에 무료로 운영됩니다.",
                        q("이 프로그램의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("건강 체조는 매일 오전 10시에 무료로 진행된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("건강 체조는 유료다.", "🔵 파랑 - '무료'와 반대되는 오답입니다."),
                                opt("주 1회만 진행된다.", "🔵 파랑 - '매일'을 '주 1회'로 바꾼 오답입니다."),
                                opt("오후에 진행된다.", "🔵 파랑 - '오전'을 '오후'로 바꾼 오답입니다.")
                        ), 0, "🔵 요금이나 빈도, 시간대를 바꿔 오답을 만듭니다.",
                                "[복지관 마인드맵] 빈도(매일) + 시간(오전 10시) + 비용(무료). 색칠하세요.")),
                onePassage(PassageCategory.READING, "다문화 지원 안내문",
                        "[다문화 지원 안내] 임신·출산 지원 서비스는 다문화가족지원센터를 통해 신청할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("임신·출산 지원은 다문화가족지원센터에서 신청한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("임신·출산 지원은 병원에서만 신청한다.", "🔵 파랑 - '다문화가족지원센터'를 '병원'으로 바꾼 오답입니다."),
                                opt("이 서비스는 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("신청 기관이 정해지지 않았다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 기관을 바꾸거나 서비스 존재 여부를 반대로 착각하게 합니다.",
                                "[다문화 마인드맵] 서비스(임신·출산 지원) + 기관(다문화가족지원센터). 색칠하세요.")),
                onePassage(PassageCategory.READING, "도서관 대출 연장 안내",
                        "[도서관 안내] 연체 중인 도서가 있으면 다른 도서의 대출 연장도 제한됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("연체 도서가 있으면 다른 대출 연장도 제한된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("연체와 상관없이 항상 연장할 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("연체 도서만 반납하면 아무 문제 없다.", "🔵 파랑 - '다른 도서의 연장도 제한'이라는 내용을 놓치게 합니다."),
                                opt("연체는 불가능한 상황이다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 연체가 다른 대출에 미치는 영향을 반대로 착각하게 합니다.",
                                "[도서관 마인드맵] 연체(있음) → 영향(전체 연장 제한). 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "문화센터 강좌 안내",
                        "[강좌 안내] 결석이 3회를 초과하면 수료증이 발급되지 않습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("결석 3회 초과 시 수료증이 발급되지 않는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("결석해도 항상 수료증을 받는다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("결석 5회부터 문제가 된다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("결석은 허용되지 않는다.", "🔵 파랑 - '3회'까지는 허용된다는 내용과 다른 오답입니다.")
                        ), 0, "🔵 결석 허용 횟수를 바꾸거나 완전 금지로 과장합니다.",
                                "[강좌 마인드맵] 조건(결석 3회 초과) → 결과(수료증 미발급). 색칠하세요.")),
                onePassage(PassageCategory.READING, "청소년 상담 안내문",
                        "[상담 안내문] 위기 상황의 경우 24시간 긴급 상담 전화를 이용할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("위기 상황 시 24시간 상담이 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("상담은 업무시간에만 가능하다.", "🔵 파랑 - '24시간'과 반대되는 오답입니다."),
                                opt("긴급 상담 전화는 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("위기 상황이 아니면 상담할 수 없다.", "🔵 파랑 - 언급되지 않은 제한입니다.")
                        ), 0, "🔵 상담 가능 시간이나 서비스 존재 여부를 반대로 착각하게 합니다.",
                                "[상담 마인드맵] 상황(위기) + 서비스(24시간 긴급 상담). 색칠하세요.")),
                onePassage(PassageCategory.READING, "노인 복지관 프로그램",
                        "[복지관 프로그램] 신규 프로그램 신청은 매월 1일부터 5일까지 접수합니다.",
                        q("이 프로그램의 신청 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("매월 1일부터 5일까지", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("매월 10일부터 15일까지", "🔵 파랑 - 날짜를 바꾼 오답입니다."),
                                opt("신청 기간에 제한이 없다.", "🔵 파랑 - 명시된 기간과 반대되는 오답입니다."),
                                opt("연중 상시 접수한다.", "🔵 파랑 - 명시된 기간 제한과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 기간 날짜를 바꾸거나 상시 접수로 왜곡합니다.",
                                "[복지관 마인드맵] 기간(매월 1일~5일). 날짜에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "다문화 지원 안내문",
                        "[다문화 지원 안내] 통번역 서비스 이용은 사전 예약제로 운영되며 당일 이용은 어려울 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("당일 이용은 어려울 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("언제든 당일 이용이 가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("예약 없이도 이용할 수 있다.", "🔵 파랑 - '사전 예약제'와 반대되는 오답입니다."),
                                opt("통번역 서비스는 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 당일 이용 가능 여부나 예약 필요성을 반대로 착각하게 합니다.",
                                "[다문화 마인드맵] 방식(사전 예약제) + 유의(당일 이용 어려움). 색칠하세요."))
        );

        List<PassageSeed> reading4th31to40 = List.of(
                onePassage(PassageCategory.READING, "도서관 대출 연장 안내",
                        "[도서관 안내] 예약 도서가 없는 경우에 한해 최대 2회까지 연장이 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("예약자가 없을 때 최대 2회 연장 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("예약자가 있어도 연장할 수 있다.", "🔵 파랑 - '예약 도서가 없는 경우에 한해'와 반대되는 오답입니다."),
                                opt("연장은 무제한으로 가능하다.", "🔵 파랑 - '최대 2회'라는 제한과 반대되는 오답입니다."),
                                opt("연장은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(예약자 없음)을 빼거나 횟수 제한을 반대로 착각하게 합니다.",
                                "[도서관 마인드맵] 조건(예약자 없음) + 한도(2회). 색칠하세요.")),
                onePassage(PassageCategory.READING, "문화센터 강좌 안내",
                        "[강좌 안내] 강좌 개강 3일 전까지는 전액 환불되며, 이후에는 환불이 불가합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("개강 3일 전까지는 전액 환불된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("개강 후에도 전액 환불된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("환불은 절대 불가능하다.", "🔵 파랑 - '3일 전까지는 가능'과 다른 과장된 오답입니다."),
                                opt("환불 기한은 개강 1주 전까지다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 환불 가능 시점을 바꾸거나 절대 불가로 과장합니다.",
                                "[강좌 마인드맵] 환불(3일 전까지, 전액) + 이후(불가). 색칠하세요.")),
                onePassage(PassageCategory.READING, "청소년 상담 안내문",
                        "[상담 안내문] 학교 부적응 관련 상담은 담임교사의 의뢰서 없이도 직접 신청 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("의뢰서 없이 직접 신청할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("담임교사의 의뢰서가 반드시 필요하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("학교를 통해서만 신청 가능하다.", "🔵 파랑 - '직접 신청 가능'과 반대되는 오답입니다."),
                                opt("이 상담은 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 의뢰서 필요 여부를 반대로 착각하게 합니다.",
                                "[상담 마인드맵] 의뢰서(불필요) + 직접 신청(가능). 색칠하세요.")),
                onePassage(PassageCategory.READING, "노인 복지관 프로그램",
                        "[복지관 프로그램] 프로그램 참가비는 저소득층에 한해 전액 면제됩니다.",
                        q("이 프로그램의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("저소득층은 참가비가 면제된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 참가자가 무료로 이용한다.", "🔵 파랑 - '저소득층에 한해'라는 조건을 빼고 읽게 합니다."),
                                opt("저소득층도 참가비를 내야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("참가비 면제 제도가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 면제 대상 조건을 빼거나 반대로 착각하게 합니다.",
                                "[복지관 마인드맵] 대상(저소득층) + 혜택(전액 면제). 색칠하세요.")),
                onePassage(PassageCategory.READING, "다문화 지원 안내문",
                        "[다문화 지원 안내] 배우자의 국적과 상관없이 결혼이민자는 누구나 상담받을 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("결혼이민자는 누구나 상담받을 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("특정 국적만 상담받을 수 있다.", "🔵 파랑 - '국적과 상관없이'라는 내용과 반대되는 오답입니다."),
                                opt("결혼이민자는 상담 대상이 아니다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("상담은 제한적으로만 제공된다.", "🔵 파랑 - '누구나'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 상담 대상 범위를 제한적으로 착각하게 합니다.",
                                "[다문화 마인드맵] 조건(국적 무관) + 대상(결혼이민자 전체). 색칠하세요.")),
                onePassage(PassageCategory.READING, "도서관 대출 연장 안내",
                        "[도서관 안내] 회원증 정지 상태에서는 대출 및 연장이 모두 제한됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("회원증 정지 시 대출과 연장이 모두 제한된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("회원증이 정지돼도 연장은 가능하다.", "🔵 파랑 - '모두 제한된다'와 반대되는 오답입니다."),
                                opt("회원증 정지는 대출과 무관하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("회원증은 정지되지 않는다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 정지 상태의 영향 범위를 반대로 착각하게 합니다.",
                                "[도서관 마인드맵] 정지 상태 → 대출·연장 모두 제한. 범위를 색칠하세요.")),
                onePassage(PassageCategory.READING, "문화센터 강좌 안내",
                        "[강좌 안내] 이 강좌는 미취학 아동을 동반한 수강이 불가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("미취학 아동 동반 수강이 불가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("미취학 아동도 함께 들을 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("초등학생은 동반할 수 있다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("아동 동반에 제한이 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다.")
                        ), 0, "🔵 동반 가능 여부를 반대로 착각하게 합니다.",
                                "[강좌 마인드맵] 대상 제한(미취학 아동 동반 불가). 부정 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "청소년 상담 안내문",
                        "[상담 안내문] 상담사는 청소년상담사 자격증을 소지한 전문가로 구성되어 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("상담사는 자격증을 소지한 전문가다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("상담사는 자원봉사자로 구성된다.", "🔵 파랑 - '전문가'와 반대되는 오답입니다."),
                                opt("자격증이 없어도 상담할 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("상담사에 대한 정보가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 상담사의 자격이나 전문성을 반대로 착각하게 합니다.",
                                "[상담 마인드맵] 상담사(자격증 소지 전문가). 핵심 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "노인 복지관 프로그램",
                        "[복지관 프로그램] 프로그램 신청은 1인당 최대 2개까지 중복 신청이 가능합니다.",
                        q("이 프로그램의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("1인당 최대 2개까지 신청할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("1인당 1개만 신청할 수 있다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("중복 신청은 금지되어 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("신청 개수에 제한이 없다.", "🔵 파랑 - '최대 2개'라는 제한과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 가능 개수를 바꾸거나 제한 여부를 반대로 착각하게 합니다.",
                                "[복지관 마인드맵] 제한(1인당 최대 2개). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "다문화 지원 안내문",
                        "[다문화 지원 안내] 상담 시 개인정보는 관련 법령에 따라 안전하게 보호됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("개인정보는 법령에 따라 보호된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("개인정보는 외부에 공개된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("개인정보 보호 규정이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("개인정보는 상담사 재량으로 처리된다.", "🔵 파랑 - '관련 법령에 따라'와 다른 오답입니다.")
                        ), 0, "🔵 개인정보 보호 여부나 근거(법령/재량)를 반대로 착각하게 합니다.",
                                "[다문화 마인드맵] 개인정보 → 법령 보호. 근거 표현을 확인하세요."))
        );

        List<PassageSeed> listening5th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "이력서 작성",
                        "여자: 이력서에 사진도 꼭 붙여야 하나요?\n남자: 요즘은 대부분 사진 없이도 지원 가능해요.",
                        q("남자의 대답으로 알맞은 것을 고르십시오.", List.of(
                                opt("사진 없이도 지원 가능하다.", "정답: '사진 없이도 지원 가능하다'고 답했습니다."),
                                opt("사진이 반드시 필요하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("사진을 두 장 붙여야 한다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("이력서를 낼 수 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 사진 필요 여부를 반대로 착각하게 합니다.",
                                "[이력서 마인드맵] 질문(사진 필요?) → 답(대부분 불필요). 핵심 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "면접 준비",
                        "남자: 면접 복장은 정장을 입어야 하나요?\n여자: 회사마다 다른데 이 회사는 비즈니스 캐주얼이 무난해요.",
                        q("여자가 추천한 이 회사의 면접 복장으로 알맞은 것을 고르십시오.", List.of(
                                opt("비즈니스 캐주얼", "정답: '비즈니스 캐주얼이 무난하다'고 답했습니다."),
                                opt("정장", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("운동복", "🔴 빨강 - 언급되지 않은 복장입니다."),
                                opt("복장 제한이 없다.", "🔴 빨강 - 대화 내용과 다른 오답입니다.")
                        ), 0, "🔴 실제 추천 복장과 반대되거나 무관한 다른 복장으로 오답을 만듭니다.",
                                "[면접 마인드맵] 이 회사(비즈니스 캐주얼). 회사별 차이에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "인턴십 지원",
                        "여자: 인턴십 지원 마감이 언제예요?\n남자: 이번 주 금요일까지예요.",
                        q("인턴십 지원 마감일로 알맞은 것을 고르십시오.", List.of(
                                opt("이번 주 금요일", "정답: '이번 주 금요일까지'라고 답했습니다."),
                                opt("다음 주 금요일", "🔴 빨강 - '이번 주'를 '다음 주'로 바꾼 오답입니다."),
                                opt("이번 주 월요일", "🔴 빨강 - 요일을 바꾼 오답입니다."),
                                opt("마감이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 주차나 요일을 슬쩍 바꿔 오답을 만듭니다.",
                                "[인턴십 마인드맵] 마감(이번 주 금요일). 시점 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "연봉 협상",
                        "남자: 연봉 협상할 때 뭘 준비하면 좋아요?\n여자: 본인의 성과를 구체적인 자료로 정리해 가면 좋아요.",
                        q("여자가 추천한 준비물로 알맞은 것을 고르십시오.", List.of(
                                opt("성과를 정리한 구체적 자료", "정답: '성과를 구체적인 자료로 정리해 가라'고 답했습니다."),
                                opt("동료의 추천서", "🟢 초록 - 언급되지 않은 준비물입니다."),
                                opt("경쟁사 연봉 자료만", "🟢 초록 - 언급되지 않은 준비물입니다."),
                                opt("아무것도 준비할 필요 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 준비물과 무관한 다른 자료로 오답을 만듭니다.",
                                "[연봉협상 마인드맵] 준비물(성과 자료). 핵심 명사에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "퇴사 절차",
                        "여자: 퇴사하려면 얼마나 전에 말씀드려야 해요?\n남자: 최소 한 달 전에는 알려주시는 게 좋아요.",
                        q("퇴사 통보 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("최소 한 달 전", "정답: '최소 한 달 전에는 알려주는 게 좋다'고 답했습니다."),
                                opt("최소 일주일 전", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("당일 통보", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("통보할 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[퇴사 마인드맵] 질문(통보 시점) → 답(한 달 전). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이력서 작성",
                        "남자: 자기소개서는 몇 자 정도 써야 해요?\n여자: 보통 항목당 500자 이내로 작성하면 돼요.",
                        q("자기소개서 항목당 권장 글자 수로 알맞은 것을 고르십시오.", List.of(
                                opt("500자 이내", "정답: '항목당 500자 이내로 작성하면 된다'고 답했습니다."),
                                opt("100자 이내", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("1000자 이내", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("글자 수 제한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 글자 수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[이력서 마인드맵] 기준(항목당) + 글자 수(500자 이내). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "면접 준비",
                        "여자: 면접 시간보다 얼마나 일찍 도착하는 게 좋아요?\n남자: 10분에서 15분 정도 일찍 도착하는 게 적당해요.",
                        q("면접에 도착하기 좋은 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("10분에서 15분 일찍", "정답: 대화에 그대로 나와 있습니다."),
                                opt("1시간 일찍", "🔴 빨강 - 시간을 과장한 오답입니다."),
                                opt("정시에 도착", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("늦게 도착해도 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시간 범위를 과장하거나 정시/지각으로 왜곡합니다.",
                                "[면접 마인드맵] 적정 도착(10~15분 전). 숫자 범위를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "인턴십 지원",
                        "남자: 인턴십은 유급인가요?\n여자: 네, 시급 만 원에 교통비도 별도로 지급돼요.",
                        q("인턴십의 시급으로 알맞은 것을 고르십시오.", List.of(
                                opt("만 원", "정답: '시급 만 원'이라고 답했습니다."),
                                opt("오천 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("이만 원", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("무급이다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시급 숫자를 슬쩍 바꾸거나 무급으로 왜곡합니다.",
                                "[인턴십 마인드맵] 시급(만 원) + 교통비(별도 지급). 두 정보를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "연봉 협상",
                        "여자: 연봉 협상은 언제 하는 게 좋아요?\n남자: 계약서에 사인하기 전에 미리 논의하는 게 좋아요.",
                        q("연봉 협상을 하기 좋은 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("계약서 사인 전", "정답: '계약서에 사인하기 전에 미리 논의하는 게 좋다'고 답했습니다."),
                                opt("계약서 사인 후", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("입사 후 1년 뒤", "🔴 빨강 - 언급되지 않은 시점입니다."),
                                opt("협상할 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 협상 시점(전/후)을 뒤바꿔 오답을 만듭니다.",
                                "[연봉협상 마인드맵] 협상(사인 전) → 계약. 순서를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "퇴사 절차",
                        "남자: 퇴사할 때 인수인계는 얼마나 자세히 해야 해요?\n여자: 후임자가 바로 업무를 이어받을 수 있도록 문서로 정리하는 게 좋아요.",
                        q("여자가 추천한 인수인계 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("문서로 정리하기", "정답: '문서로 정리하는 게 좋다'고 답했습니다."),
                                opt("구두로만 설명하기", "🟢 초록 - '문서로'와 반대되는 오답입니다."),
                                opt("인수인계를 생략하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("후임자에게 맡기지 않기", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 실제 추천 방법(문서화)과 반대되거나 무관한 방법으로 오답을 만듭니다.",
                                "[퇴사 마인드맵] 목적(원활한 인계) → 방법(문서화). 색으로 연결하세요."))
        );

        List<PassageSeed> listening5th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "이력서 작성",
                        "여자: 경력 사항은 최신순으로 써야 하나요?\n남자: 네, 최근 경력부터 역순으로 작성하는 게 일반적이에요.",
                        q("경력 사항 작성 순서로 알맞은 것을 고르십시오.", List.of(
                                opt("최근 경력부터 역순으로", "정답: '최근 경력부터 역순으로 작성하는 게 일반적'이라고 답했습니다."),
                                opt("오래된 경력부터 순서대로", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("경력 순서는 상관없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("경력 사항은 쓰지 않는다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 최신순/오래된순을 뒤바꾸거나 순서가 상관없다고 왜곡합니다.",
                                "[이력서 마인드맵] 순서(최신 → 과거, 역순). 방향을 화살표로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "면접 준비",
                        "남자: 면접에서 예상 질문 리스트도 준비하는 게 좋을까요?\n여자: 네, 예상 질문에 대한 답변을 미리 연습해 보는 게 도움이 돼요.",
                        q("여자가 추천한 면접 준비 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("예상 질문 답변 미리 연습하기", "정답: '예상 질문에 대한 답변을 미리 연습해 보라'고 답했습니다."),
                                opt("질문 없이 즉흥적으로 답하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("이력서만 다시 읽어 보기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("준비할 필요가 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 추천된 방법과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[면접 마인드맵] 준비(예상 질문 연습). 핵심 동사에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "인턴십 지원",
                        "여자: 인턴십 끝나고 정규직 전환도 가능한가요?\n남자: 네, 우수 인턴에게는 정규직 전환 기회가 주어져요.",
                        q("정규직 전환 대상으로 알맞은 것을 고르십시오.", List.of(
                                opt("우수 인턴", "정답: '우수 인턴에게 전환 기회가 주어진다'고 답했습니다."),
                                opt("모든 인턴", "🔵 파랑 - '우수 인턴'이라는 조건을 빼고 읽게 하는 오답입니다."),
                                opt("전환 제도가 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("신입사원만 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 대상 조건(우수 인턴)을 빼고 읽거나 제도 유무를 반대로 착각하게 합니다.",
                                "[인턴십 마인드맵] 조건(우수 인턴) → 전환 기회. 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.LISTENING, "연봉 협상",
                        "남자: 연봉 협상 실패하면 어떻게 해요?\n여자: 실패해도 다음 평가 때 다시 논의할 수 있어요.",
                        q("연봉 협상 실패 시 대응으로 알맞은 것을 고르십시오.", List.of(
                                opt("다음 평가 때 다시 논의 가능", "정답: '다음 평가 때 다시 논의할 수 있다'고 답했습니다."),
                                opt("다시는 논의할 수 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("바로 퇴사해야 한다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("협상은 한 번만 가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 재논의 가능 여부를 반대로 착각하게 합니다.",
                                "[연봉협상 마인드맵] 실패 → 다음 평가(재논의 가능). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "퇴사 절차",
                        "여자: 퇴직금은 언제 받을 수 있어요?\n남자: 퇴사일로부터 14일 이내에 지급돼요.",
                        q("퇴직금 지급 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("퇴사일로부터 14일 이내", "정답: 대화에 그대로 나와 있습니다."),
                                opt("퇴사일로부터 30일 이내", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("퇴사 당일 즉시", "🔴 빨강 - '14일 이내'라는 내용과 다른 오답입니다."),
                                opt("퇴직금은 지급되지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기한 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[퇴사 마인드맵] 기준(퇴사일) + 기한(14일 이내). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이력서 작성",
                        "남자: 이력서에 취미와 특기도 써야 하나요?\n여자: 필수는 아니지만 직무와 관련 있으면 쓰는 게 좋아요.",
                        q("취미와 특기 작성에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("필수는 아니지만 직무 관련성이 있으면 쓰는 게 좋다.", "정답: 대화에 그대로 나와 있습니다."),
                                opt("반드시 작성해야 한다.", "🔵 파랑 - '필수는 아니다'와 반대되는 오답입니다."),
                                opt("작성하면 안 된다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("직무와 무관해도 상관없다.", "🔵 파랑 - '직무와 관련 있으면'이라는 조건과 다른 오답입니다.")
                        ), 0, "🔵 필수 여부나 관련성 조건을 반대로 착각하게 합니다.",
                                "[이력서 마인드맵] 선택 사항(취미·특기) + 조건(직무 관련성). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "면접 준비",
                        "여자: 면접 후에 감사 메일을 보내는 게 좋을까요?\n남자: 네, 면접 당일이나 다음 날 보내면 좋은 인상을 줄 수 있어요.",
                        q("감사 메일을 보내기 좋은 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("면접 당일이나 다음 날", "정답: 대화에 그대로 나와 있습니다."),
                                opt("면접 일주일 후", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("면접 전에", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("보낼 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시점을 바꾸거나 순서를 뒤바꿔 오답을 만듭니다.",
                                "[면접 마인드맵] 면접 후 → 감사 메일(당일~다음 날). 순서를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "인턴십 지원",
                        "남자: 인턴십 서류 전형 결과는 어떻게 알려줘요?\n여자: 이메일로 개별 통보돼요.",
                        q("인턴십 서류 전형 결과를 알려주는 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("이메일로 개별 통보", "정답: '이메일로 개별 통보된다'고 답했습니다."),
                                opt("전화로 통보", "🔵 파랑 - 언급되지 않은 방법입니다."),
                                opt("홈페이지 공지로만 확인", "🔵 파랑 - '개별 통보'와 다른 오답입니다."),
                                opt("결과를 알려주지 않는다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 방법과 무관한 다른 통보 방식으로 오답을 만듭니다.",
                                "[인턴십 마인드맵] 결과 통보(이메일, 개별). 방법을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "연봉 협상",
                        "여자: 연봉 협상할 때 희망 연봉을 먼저 말하는 게 좋아요?\n남자: 회사에서 먼저 제시할 때까지 기다리는 것도 전략이에요.",
                        q("남자가 제안한 전략으로 알맞은 것을 고르십시오.", List.of(
                                opt("회사가 먼저 제시할 때까지 기다리기", "정답: '회사에서 먼저 제시할 때까지 기다리는 것도 전략'이라고 답했습니다."),
                                opt("무조건 먼저 말하기", "🟢 초록 - 대화 내용과 반대되는 오답입니다."),
                                opt("협상을 아예 하지 않기", "🟢 초록 - 언급되지 않은 내용입니다."),
                                opt("동료에게 물어보기", "🟢 초록 - 언급되지 않은 내용입니다.")
                        ), 0, "🟢 실제 제안된 전략과 반대되거나 무관한 행동으로 오답을 만듭니다.",
                                "[연봉협상 마인드맵] 전략(회사 제시 기다리기). 핵심 표현에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "퇴사 절차",
                        "남자: 퇴사 서류는 어디에 제출해요?\n여자: 인사팀에 사직서를 제출하시면 돼요.",
                        q("사직서를 제출하는 부서로 알맞은 것을 고르십시오.", List.of(
                                opt("인사팀", "정답: '인사팀에 사직서를 제출하면 된다'고 답했습니다."),
                                opt("총무팀", "🔵 파랑 - '인사팀'을 '총무팀'으로 바꾼 오답입니다."),
                                opt("경영지원팀", "🔵 파랑 - 언급되지 않은 부서입니다."),
                                opt("제출할 곳이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 부서와 무관한 다른 부서로 오답을 만듭니다.",
                                "[퇴사 마인드맵] 서류(사직서) + 제출처(인사팀). 부서명을 확인하세요."))
        );

        List<PassageSeed> reading5th21to30 = List.of(
                onePassage(PassageCategory.READING, "이력서 작성 가이드",
                        "[이력서 작성 가이드] 지원 동기는 구체적인 경험을 근거로 작성하면 설득력이 높아집니다.",
                        q("이 가이드의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("구체적 경험을 근거로 쓰면 설득력이 높아진다.", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("추상적으로 쓰는 것이 좋다.", "🔵 파랑 - '구체적인 경험'과 반대되는 오답입니다."),
                                opt("지원 동기는 쓰지 않아도 된다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("경험과 무관하게 작성해야 한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 구체적/추상적 작성 방식을 반대로 착각하게 합니다.",
                                "[이력서 마인드맵] 방법(구체적 경험 근거) → 효과(설득력). 색칠하세요.")),
                onePassage(PassageCategory.READING, "면접 안내문",
                        "[면접 안내문] 면접은 화상으로 진행되며, 사전에 통신 상태를 점검해 주시기 바랍니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("면접은 화상으로 진행된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("면접은 대면으로 진행된다.", "🔵 파랑 - '화상'과 반대되는 오답입니다."),
                                opt("통신 상태 점검은 필요 없다.", "🔵 파랑 - '사전에 점검해 달라'는 요청과 반대되는 오답입니다."),
                                opt("면접은 전화로 진행된다.", "🔵 파랑 - '화상'을 '전화'로 바꾼 오답입니다.")
                        ), 0, "🔵 면접 방식이나 사전 점검 필요성을 바꿔 오답을 만듭니다.",
                                "[면접 마인드맵] 방식(화상) + 요청(통신 점검). 색칠하세요.")),
                onePassage(PassageCategory.READING, "인턴십 모집 공고",
                        "[인턴십 모집 공고] 근무 기간은 3개월이며, 주 5일 풀타임으로 근무합니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("주 5일 풀타임으로 근무한다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("주 2일만 근무한다.", "🔵 파랑 - 명시된 근무일과 반대되는 오답입니다."),
                                opt("근무 기간은 6개월이다.", "🔵 파랑 - '3개월'을 '6개월'로 바꾼 오답입니다."),
                                opt("파트타임 근무다.", "🔵 파랑 - '풀타임'과 반대되는 오답입니다.")
                        ), 0, "🔵 근무일이나 기간, 근무 형태를 바꿔 오답을 만듭니다.",
                                "[인턴십 마인드맵] 기간(3개월) + 형태(주5일 풀타임). 색칠하세요.")),
                onePassage(PassageCategory.READING, "연봉 협상 후기",
                        "[연봉 협상 후기] 시장 조사 자료를 근거로 제시했더니 원하는 수준으로 협상이 잘 됐어요.",
                        q("이 후기의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("시장 조사 자료로 협상에 성공했다.", "정답: '자료를 근거로 제시했더니 협상이 잘 됐다'고 명시했습니다."),
                                opt("자료 없이 협상했다.", "🔵 파랑 - '시장 조사 자료를 근거로'와 반대되는 오답입니다."),
                                opt("협상에 실패했다.", "🔵 파랑 - '협상이 잘 됐다'와 반대되는 오답입니다."),
                                opt("연봉이 삭감됐다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 협상 성공 여부나 준비 자료 유무를 반대로 착각하게 합니다.",
                                "[연봉협상 마인드맵] 준비(시장 조사 자료) → 결과(성공). 색칠하세요.")),
                onePassage(PassageCategory.READING, "퇴사 절차 안내문",
                        "[퇴사 절차 안내문] 퇴사자는 사내 시스템 계정과 사원증을 마지막 근무일에 반납해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("마지막 근무일에 사원증을 반납해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("사원증은 반납하지 않아도 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("반납은 퇴사 후 일주일 이내다.", "🔵 파랑 - '마지막 근무일'이라는 시점과 다른 오답입니다."),
                                opt("계정만 반납하면 된다.", "🔵 파랑 - '계정과 사원증'이라는 두 항목 중 하나를 빼고 읽게 합니다.")
                        ), 0, "🔵 반납 시점이나 반납 대상 항목을 바꿔 오답을 만듭니다.",
                                "[퇴사 마인드맵] 대상(계정+사원증) + 시점(마지막 근무일). 색칠하세요.")),
                onePassage(PassageCategory.READING, "이력서 작성 가이드",
                        "[이력서 작성 가이드] 오타나 맞춤법 오류는 성의 없어 보일 수 있으니 제출 전 꼼꼼히 검토하세요.",
                        q("이 가이드의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("제출 전 꼼꼼히 검토해야 한다.", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("검토는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("오타는 큰 문제가 되지 않는다.", "🔵 파랑 - '성의 없어 보일 수 있다'와 반대되는 오답입니다."),
                                opt("맞춤법은 자동으로 수정된다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 검토 필요성이나 오타의 영향을 반대로 착각하게 합니다.",
                                "[이력서 마인드맵] 위험(오타·오류) → 대응(꼼꼼한 검토). 색칠하세요.")),
                onePassage(PassageCategory.READING, "면접 안내문",
                        "[면접 안내문] 1차 면접 합격자에 한해 2차 면접 일정이 개별 안내됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("1차 합격자만 2차 면접 안내를 받는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 지원자가 2차 면접을 본다.", "🔵 파랑 - '1차 합격자에 한해'라는 조건을 빼고 읽게 합니다."),
                                opt("2차 면접은 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("2차 면접은 전체 공지로 안내된다.", "🔵 파랑 - '개별 안내'와 반대되는 오답입니다.")
                        ), 0, "🔵 조건(1차 합격)을 빼거나 안내 방식을 반대로 착각하게 합니다.",
                                "[면접 마인드맵] 조건(1차 합격) → 2차 안내(개별). 색칠하세요.")),
                onePassage(PassageCategory.READING, "인턴십 모집 공고",
                        "[인턴십 모집 공고] 자기소개서 문항 중 하나는 지원 직무와 관련된 프로젝트 경험을 서술해야 합니다.",
                        q("이 공고의 자기소개서 문항 요구사항으로 알맞은 것을 고르십시오.", List.of(
                                opt("지원 직무 관련 프로젝트 경험 서술", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("취미 활동 서술", "🔵 파랑 - 언급되지 않은 요구사항입니다."),
                                opt("가족 소개 서술", "🔵 파랑 - 언급되지 않은 요구사항입니다."),
                                opt("문항이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 요구사항과 무관한 다른 내용으로 오답을 만듭니다.",
                                "[인턴십 마인드맵] 문항(직무 관련 프로젝트 경험). 핵심 조건을 확인하세요.")),
                onePassage(PassageCategory.READING, "연봉 협상 후기",
                        "[연봉 협상 후기] 무리한 요구는 오히려 역효과가 날 수 있다는 것을 이번에 배웠어요.",
                        q("이 후기를 쓴 사람이 얻은 교훈으로 알맞은 것을 고르십시오.", List.of(
                                opt("무리한 요구는 역효과가 날 수 있다.", "정답: 후기에 그대로 명시되어 있습니다."),
                                opt("무리한 요구가 항상 성공한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("협상은 무조건 피해야 한다.", "🔵 파랑 - 언급되지 않은 결론입니다."),
                                opt("연봉은 협상할 필요가 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 무리한 요구의 결과를 반대로 착각하게 합니다.",
                                "[연봉협상 마인드맵] 무리한 요구 → 역효과. 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "퇴사 절차 안내문",
                        "[퇴사 절차 안내문] 경력증명서 발급은 퇴사 후에도 인사팀에 요청하면 언제든지 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("퇴사 후에도 경력증명서를 요청할 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("경력증명서는 재직 중에만 발급된다.", "🔵 파랑 - '퇴사 후에도'라는 내용과 반대되는 오답입니다."),
                                opt("경력증명서 발급 기한이 정해져 있다.", "🔵 파랑 - '언제든지'라는 표현과 반대되는 오답입니다."),
                                opt("경력증명서는 발급되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 발급 가능 시점이나 기한 유무를 반대로 착각하게 합니다.",
                                "[퇴사 마인드맵] 퇴사 후에도 → 언제든지 요청 가능. 색칠하세요."))
        );

        List<PassageSeed> reading5th31to40 = List.of(
                onePassage(PassageCategory.READING, "이력서 작성 가이드",
                        "[이력서 작성 가이드] 공백 기간이 있다면 그 기간에 무엇을 했는지 간단히 언급하는 것이 좋습니다.",
                        q("이 가이드의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("공백 기간에 한 일을 간단히 언급하는 게 좋다.", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("공백 기간은 절대 언급하면 안 된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("공백 기간을 자세히 나열해야 한다.", "🔵 파랑 - '간단히'라는 표현과 반대되는 오답입니다."),
                                opt("공백 기간이 있으면 지원할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 언급 여부나 서술 분량(간단히/자세히)을 반대로 착각하게 합니다.",
                                "[이력서 마인드맵] 공백 기간 → 간단히 언급. 분량 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "면접 안내문",
                        "[면접 안내문] 면접 당일 신분증을 지참하지 않으면 면접에 응시할 수 없습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신분증이 없으면 면접에 응시할 수 없다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("신분증이 없어도 면접을 볼 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("신분증은 면접 후에 확인한다.", "🔵 파랑 - '당일'이라는 시점과 다른 오답입니다."),
                                opt("신분증 없이도 재응시가 가능하다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 신분증 필요 여부를 반대로 착각하게 합니다.",
                                "[면접 마인드맵] 조건(신분증 지참) → 응시 가능. 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "인턴십 모집 공고",
                        "[인턴십 모집 공고] 최종 합격자는 온라인 오리엔테이션 참석이 필수입니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("최종 합격자는 오리엔테이션에 참석해야 한다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("오리엔테이션은 선택 사항이다.", "🔵 파랑 - '필수'와 반대되는 오답입니다."),
                                opt("오리엔테이션은 오프라인으로 진행된다.", "🔵 파랑 - '온라인'을 '오프라인'으로 바꾼 오답입니다."),
                                opt("오리엔테이션은 진행되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 필수/선택 여부나 진행 방식을 바꿔 오답을 만듭니다.",
                                "[인턴십 마인드맵] 대상(최종 합격자) + 필수(오리엔테이션). 색칠하세요.")),
                onePassage(PassageCategory.READING, "연봉 협상 후기",
                        "[연봉 협상 후기] 협상 전에 회사의 연봉 테이블을 미리 파악해 둔 것이 도움이 됐어요.",
                        q("이 후기를 쓴 사람에게 도움이 된 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("회사의 연봉 테이블을 미리 파악한 것", "정답: 후기에 그대로 명시되어 있습니다."),
                                opt("동료와 연봉을 비교한 것", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("아무 준비도 하지 않은 것", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("협상을 포기한 것", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 실제 도움이 된 준비와 무관한 다른 내용으로 오답을 만듭니다.",
                                "[연봉협상 마인드맵] 준비(연봉 테이블 파악) → 도움. 핵심 명사를 확인하세요.")),
                onePassage(PassageCategory.READING, "퇴사 절차 안내문",
                        "[퇴사 절차 안내문] 퇴사 면담은 팀장과 인사팀 각각 한 번씩 진행됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("퇴사 면담은 팀장, 인사팀과 각각 진행된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("퇴사 면담은 팀장하고만 진행된다.", "🔵 파랑 - '인사팀'이라는 항목을 빼고 읽게 합니다."),
                                opt("퇴사 면담은 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("퇴사 면담은 세 번 진행된다.", "🔵 파랑 - 횟수를 바꾼 오답입니다.")
                        ), 0, "🔵 면담 대상 중 하나를 빼거나 횟수를 바꿔 오답을 만듭니다.",
                                "[퇴사 마인드맵] 대상(팀장+인사팀) + 횟수(각 1회). 색칠하세요.")),
                onePassage(PassageCategory.READING, "이력서 작성 가이드",
                        "[이력서 작성 가이드] 자격증은 최신 취득 순으로 나열하되 직무 관련성이 높은 것을 우선 배치하세요.",
                        q("이 가이드의 자격증 배치 순서로 알맞은 것을 고르십시오.", List.of(
                                opt("직무 관련성이 높은 것을 우선 배치", "정답: 가이드에 그대로 명시되어 있습니다."),
                                opt("무조건 오래된 순서로 배치", "🔵 파랑 - '최신 취득 순'과 반대되는 오답입니다."),
                                opt("자격증은 나열하지 않는다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("순서는 상관없다.", "🔵 파랑 - 명시된 기준과 반대되는 오답입니다.")
                        ), 0, "🔵 배치 기준을 반대로 착각하게 하거나 순서가 상관없다고 왜곡합니다.",
                                "[이력서 마인드맵] 기준(직무 관련성) → 우선 배치. 핵심 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "면접 안내문",
                        "[면접 안내문] 면접 질문에 대한 모범 답안은 따로 제공되지 않습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("모범 답안은 제공되지 않는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모범 답안이 사전에 제공된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("면접 질문이 미리 공개된다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("면접 질문은 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 모범 답안 제공 여부를 반대로 착각하게 합니다.",
                                "[면접 마인드맵] 모범 답안(미제공). 부정 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "인턴십 모집 공고",
                        "[인턴십 모집 공고] 외국인 유학생도 체류 자격 요건을 충족하면 지원할 수 있습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("외국인 유학생도 요건 충족 시 지원 가능하다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("외국인 유학생은 지원할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("체류 자격은 상관없다.", "🔵 파랑 - '요건을 충족하면'이라는 조건과 반대되는 오답입니다."),
                                opt("내국인만 지원 가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 지원 가능 대상이나 조건 필요성을 반대로 착각하게 합니다.",
                                "[인턴십 마인드맵] 대상(외국인 유학생) + 조건(체류 자격). 색칠하세요.")),
                onePassage(PassageCategory.READING, "연봉 협상 후기",
                        "[연봉 협상 후기] 회사의 재정 상황을 고려하지 않고 무작정 높은 금액을 요구했다가 결렬됐어요.",
                        q("이 후기에서 협상이 결렬된 이유로 알맞은 것을 고르십시오.", List.of(
                                opt("회사 재정을 고려하지 않고 무작정 요구해서", "정답: 후기에 그대로 명시되어 있습니다."),
                                opt("너무 낮은 금액을 요구해서", "🔵 파랑 - '높은 금액'과 반대되는 오답입니다."),
                                opt("협상 자체를 하지 않아서", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("회사가 먼저 결렬을 선언해서", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 실제 결렬 이유와 반대되거나 무관한 이유로 오답을 만듭니다.",
                                "[연봉협상 마인드맵] 원인(무작정 높은 요구) → 결과(결렬). 색칠하세요.")),
                onePassage(PassageCategory.READING, "퇴사 절차 안내문",
                        "[퇴사 절차 안내문] 미사용 연차수당은 퇴사 정산 급여에 포함되어 지급됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("미사용 연차수당은 정산 급여에 포함된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("미사용 연차수당은 지급되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("연차는 퇴사 전에 모두 사용해야만 한다.", "🔵 파랑 - '미사용' 수당 지급과 반대되는 오답입니다."),
                                opt("연차수당은 별도로 신청해야 받는다.", "🔵 파랑 - '정산 급여에 포함'과 다른 오답입니다.")
                        ), 0, "🔵 수당 지급 여부나 별도 신청 필요성을 반대로 착각하게 합니다.",
                                "[퇴사 마인드맵] 미사용 연차 → 수당(정산 급여 포함). 색칠하세요."))
        );

        List<PassageSeed> listening6th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "유학 준비",
                        "여자: 유학 준비할 때 제일 먼저 뭘 해야 해요?\n남자: 먼저 어학 성적부터 준비하는 게 좋아요.",
                        q("남자가 추천한 유학 준비 순서로 알맞은 것을 고르십시오.", List.of(
                                opt("어학 성적 준비부터", "정답: '먼저 어학 성적부터 준비하는 게 좋다'고 답했습니다."),
                                opt("비자부터 신청", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("항공권부터 예매", "🔴 빨강 - 언급되지 않은 순서입니다."),
                                opt("아무 준비도 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 '먼저'라는 순서 표현을 놓치고 다른 항목을 첫 단계로 착각하게 합니다.",
                                "[유학 마인드맵] 1(어학 성적) → 2(이후 절차). '먼저'에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "비자 신청",
                        "남자: 학생 비자 신청은 얼마나 걸려요?\n여자: 보통 2주에서 4주 정도 걸려요.",
                        q("학생 비자 신청에 걸리는 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("2주에서 4주", "정답: '2주에서 4주 정도 걸린다'고 답했습니다."),
                                opt("1주에서 2주", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("두 달 이상", "🔴 빨강 - 기간을 바꾼 오답입니다."),
                                opt("즉시 발급된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 기간 범위를 슬쩍 바꿔 오답을 만듭니다.",
                                "[비자 마인드맵] 종류(학생 비자) + 기간(2~4주). 숫자 범위를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "외국인 등록",
                        "여자: 외국인등록증은 어디서 만들어요?\n남자: 관할 출입국관리사무소에서 신청할 수 있어요.",
                        q("외국인등록증을 신청하는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("관할 출입국관리사무소", "정답: '관할 출입국관리사무소에서 신청할 수 있다'고 답했습니다."),
                                opt("주민센터", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("경찰서", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("신청할 곳이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 기관과 무관한 다른 기관으로 오답을 만듭니다.",
                                "[외국인등록 마인드맵] 서류(등록증) + 기관(출입국관리사무소). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "교환학생 프로그램",
                        "남자: 교환학생은 몇 학기 동안 가는 거예요?\n여자: 보통 한 학기나 두 학기 프로그램이 있어요.",
                        q("교환학생 프로그램의 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("한 학기나 두 학기", "정답: 대화에 그대로 나와 있습니다."),
                                opt("한 달", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("4년 전체", "🔴 빨강 - 대화에 없는 기간입니다."),
                                opt("기간이 정해지지 않았다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 언급된 기간과 무관한 다른 기간으로 오답을 만듭니다.",
                                "[교환학생 마인드맵] 기간(한 학기 또는 두 학기). 두 선택지를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "졸업 논문",
                        "여자: 논문 주제는 언제까지 정해야 해요?\n남자: 이번 학기 중간고사 전까지 정하면 돼요.",
                        q("논문 주제를 정해야 하는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("이번 학기 중간고사 전", "정답: '중간고사 전까지 정하면 된다'고 답했습니다."),
                                opt("이번 학기 기말고사 후", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("다음 학기 초", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("주제는 정할 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 시점 표현을 슬쩍 바꿔 오답을 만듭니다.",
                                "[논문 마인드맵] 기한(중간고사 전). 시점을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "유학 준비",
                        "남자: 유학 비용은 어느 정도 예상해야 해요?\n여자: 나라마다 다르지만 1년에 3천만 원 정도는 생각해야 해요.",
                        q("1년 유학 비용으로 예상되는 금액으로 알맞은 것을 고르십시오.", List.of(
                                opt("3천만 원 정도", "정답: '1년에 3천만 원 정도는 생각해야 한다'고 답했습니다."),
                                opt("천만 원 정도", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("1억 원 정도", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("비용이 들지 않는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 예상 비용 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[유학 마인드맵] 기간(1년) + 비용(3천만 원). 숫자를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "비자 신청",
                        "여자: 비자 연장은 만료 전에 해야 하나요?\n남자: 네, 만료 4개월 전부터 연장 신청이 가능해요.",
                        q("비자 연장 신청이 가능한 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("만료 4개월 전부터", "정답: '만료 4개월 전부터 연장 신청이 가능하다'고 답했습니다."),
                                opt("만료 1개월 전부터", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("만료 후에만", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("연장 신청이 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 신청 가능 시점 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[비자 마인드맵] 시점(만료 4개월 전). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "외국인 등록",
                        "남자: 등록증에 주소가 바뀌면 어떻게 해요?\n여자: 변경된 날로부터 14일 이내에 신고해야 해요.",
                        q("주소 변경 신고 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("변경일로부터 14일 이내", "정답: 대화에 그대로 나와 있습니다."),
                                opt("변경일로부터 30일 이내", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("신고할 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("변경 즉시 신고해야 한다.", "🔴 빨강 - '14일 이내'라는 유예 기간과 다른 오답입니다.")
                        ), 0, "🔴 기한 숫자를 슬쩍 바꾸거나 유예 기간을 없애 오답을 만듭니다.",
                                "[외국인등록 마인드맵] 변경 → 신고(14일 이내). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.LISTENING, "교환학생 프로그램",
                        "여자: 교환학생 지원할 때 학점 기준이 있나요?\n남자: 네, 평균 학점 3.0 이상이어야 지원할 수 있어요.",
                        q("교환학생 지원 학점 기준으로 알맞은 것을 고르십시오.", List.of(
                                opt("평균 학점 3.0 이상", "정답: '평균 학점 3.0 이상이어야 한다'고 답했습니다."),
                                opt("평균 학점 2.0 이상", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("학점 기준이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("만점이어야 한다.", "🔴 빨강 - 숫자를 과장한 오답입니다.")
                        ), 0, "🔴 학점 기준 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[교환학생 마인드맵] 조건(평균 학점 3.0 이상). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "졸업 논문",
                        "남자: 논문 심사는 몇 번 받아요?\n여자: 초심과 본심, 두 번 받아요.",
                        q("논문 심사 횟수로 알맞은 것을 고르십시오.", List.of(
                                opt("두 번", "정답: '초심과 본심, 두 번 받는다'고 답했습니다."),
                                opt("한 번", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("세 번", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("심사가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 심사 횟수 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[논문 마인드맵] 심사(초심+본심=2회). 숫자를 정확히 확인하세요."))
        );

        List<PassageSeed> listening6th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "유학 준비",
                        "여자: 기숙사는 미리 신청해야 하나요?\n남자: 네, 입학 허가서 받은 후 바로 신청하는 게 좋아요.",
                        q("기숙사를 신청하는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("입학 허가서를 받은 후 바로", "정답: '입학 허가서 받은 후 바로 신청하는 게 좋다'고 답했습니다."),
                                opt("입학 허가서를 받기 전", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("입학 후 한 달 뒤", "🔴 빨강 - 시점을 바꾼 오답입니다."),
                                opt("신청할 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 순서(허가서 전/후)를 뒤바꾸거나 시점을 왜곡합니다.",
                                "[유학 마인드맵] 입학 허가서 → 기숙사 신청. 순서를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "비자 신청",
                        "남자: 비자 인터뷰도 봐야 하나요?\n여자: 나라에 따라 다른데, 일부 국가는 인터뷰가 필수예요.",
                        q("비자 인터뷰에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("일부 국가는 인터뷰가 필수다.", "정답: 대화에 그대로 나와 있습니다."),
                                opt("모든 국가에서 인터뷰가 필수다.", "🔵 파랑 - '일부 국가'라는 제한을 빼고 읽게 합니다."),
                                opt("인터뷰는 어디서도 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("인터뷰는 선택 사항이다.", "🔵 파랑 - '필수'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 국가별 조건을 빼고 읽거나 필수/선택을 반대로 착각하게 합니다.",
                                "[비자 마인드맵] 조건(일부 국가) + 요건(인터뷰 필수). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "외국인 등록",
                        "여자: 등록증 유효기간이 얼마나 돼요?\n남자: 체류 기간과 동일하게 설정돼요.",
                        q("외국인등록증의 유효기간에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("체류 기간과 동일하다.", "정답: '체류 기간과 동일하게 설정된다'고 답했습니다."),
                                opt("항상 5년이다.", "🔵 파랑 - 대화에 없는 고정 기간입니다."),
                                opt("유효기간이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("체류 기간과 무관하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 유효기간을 고정 숫자로 착각하게 하거나 체류 기간과의 관계를 반대로 왜곡합니다.",
                                "[외국인등록 마인드맵] 등록증 유효기간 = 체류 기간. 관계를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "교환학생 프로그램",
                        "남자: 교환학생 기간 동안 등록금은 어떻게 내요?\n여자: 원래 학교에 등록금을 내고 상대 학교 등록금은 면제돼요.",
                        q("교환학생 등록금 납부 방식으로 알맞은 것을 고르십시오.", List.of(
                                opt("원래 학교에만 등록금을 낸다.", "정답: '원래 학교에 등록금을 내고 상대 학교는 면제된다'고 답했습니다."),
                                opt("양쪽 학교 모두에 낸다.", "🔴 빨강 - 명시된 내용과 반대되는 오답입니다."),
                                opt("상대 학교에만 낸다.", "🔴 빨강 - 명시된 내용과 반대되는 오답입니다."),
                                opt("등록금이 전혀 없다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 등록금 납부 대상을 뒤바꾸거나 완전 면제로 왜곡합니다.",
                                "[교환학생 마인드맵] 원래 학교(납부) + 상대 학교(면제). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "졸업 논문",
                        "여자: 논문 표절 검사도 받아야 해요?\n남자: 네, 제출 전에 표절 검사 결과지를 첨부해야 해요.",
                        q("논문 제출 시 첨부해야 하는 것으로 알맞은 것을 고르십시오.", List.of(
                                opt("표절 검사 결과지", "정답: '표절 검사 결과지를 첨부해야 한다'고 답했습니다."),
                                opt("추천서", "🔵 파랑 - 언급되지 않은 서류입니다."),
                                opt("성적표", "🔵 파랑 - 언급되지 않은 서류입니다."),
                                opt("첨부 서류가 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 서류와 무관한 다른 서류로 오답을 만듭니다.",
                                "[논문 마인드맵] 제출 전 → 표절 검사 결과지 첨부. 서류명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "유학 준비",
                        "남자: 유학 갈 때 보험도 가입해야 하나요?\n여자: 네, 현지 의료보험 가입이 의무인 나라가 많아요.",
                        q("유학 시 의료보험에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("현지 의료보험 가입이 의무인 나라가 많다.", "정답: 대화에 그대로 나와 있습니다."),
                                opt("의료보험은 어디서도 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("모든 나라에서 선택 사항이다.", "🔵 파랑 - '의무인 나라가 많다'와 반대되는 오답입니다."),
                                opt("한국 보험만 있으면 된다.", "🔵 파랑 - '현지 의료보험'과 다른 오답입니다.")
                        ), 0, "🔵 의무/선택 여부나 보험 종류(현지/한국)를 반대로 착각하게 합니다.",
                                "[유학 마인드맵] 현지 의료보험(의무인 나라 많음). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "비자 신청",
                        "여자: 비자 발급 거부되면 재신청할 수 있어요?\n남자: 네, 거부 사유를 보완해서 다시 신청할 수 있어요.",
                        q("비자 발급 거부 시 대응 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("거부 사유를 보완해서 재신청", "정답: '거부 사유를 보완해서 다시 신청할 수 있다'고 답했습니다."),
                                opt("재신청이 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("다른 나라로 가야 한다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("보완 없이 그대로 재신청", "🔴 빨강 - '사유를 보완해서'라는 조건과 다른 오답입니다.")
                        ), 0, "🔴 재신청 가능 여부나 보완 필요성을 반대로 착각하게 합니다.",
                                "[비자 마인드맵] 거부 → 보완 → 재신청. 순서대로 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "외국인 등록",
                        "남자: 등록증을 분실하면 어떻게 해요?\n여자: 가까운 출입국관리사무소에서 재발급 신청하시면 돼요.",
                        q("등록증 분실 시 재발급 신청 장소로 알맞은 것을 고르십시오.", List.of(
                                opt("가까운 출입국관리사무소", "정답: '가까운 출입국관리사무소에서 재발급 신청하면 된다'고 답했습니다."),
                                opt("경찰서", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("대사관", "🔵 파랑 - 언급되지 않은 장소입니다."),
                                opt("재발급이 불가능하다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 장소와 무관한 다른 기관으로 오답을 만듭니다.",
                                "[외국인등록 마인드맵] 분실 → 재발급(출입국관리사무소). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "교환학생 프로그램",
                        "여자: 교환학생 다녀오면 학점 인정이 되나요?\n남자: 네, 사전에 승인받은 과목은 학점으로 인정돼요.",
                        q("교환학생 학점 인정 조건으로 알맞은 것을 고르십시오.", List.of(
                                opt("사전에 승인받은 과목", "정답: '사전에 승인받은 과목은 학점으로 인정된다'고 답했습니다."),
                                opt("모든 과목이 자동 인정", "🔴 빨강 - '사전 승인'이라는 조건을 빼고 읽게 하는 오답입니다."),
                                opt("학점 인정이 전혀 안 된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("귀국 후 시험을 봐야 한다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 조건(사전 승인)을 빼고 읽거나 인정 여부를 반대로 착각하게 합니다.",
                                "[교환학생 마인드맵] 조건(사전 승인) → 학점 인정. 조건에 밑줄을 그으세요.")),
                onePassage(PassageCategory.LISTENING, "졸업 논문",
                        "남자: 논문 지도교수는 언제 정해요?\n여자: 3학년 2학기에 지도교수를 배정받아요.",
                        q("지도교수를 배정받는 시점으로 알맞은 것을 고르십시오.", List.of(
                                opt("3학년 2학기", "정답: '3학년 2학기에 지도교수를 배정받는다'고 답했습니다."),
                                opt("1학년 1학기", "🔴 빨강 - 학년을 바꾼 오답입니다."),
                                opt("4학년 2학기", "🔴 빨강 - 학년을 바꾼 오답입니다."),
                                opt("지도교수는 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 학년·학기 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[논문 마인드맵] 시점(3학년 2학기) + 배정(지도교수). 숫자를 확인하세요."))
        );

        List<PassageSeed> reading6th21to30 = List.of(
                onePassage(PassageCategory.READING, "유학 안내서",
                        "[유학 안내서] 어학연수 없이 바로 학위 과정에 진학하려면 공인 어학 성적이 필요합니다.",
                        q("이 안내서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("바로 학위 과정 진학 시 공인 어학 성적이 필요하다.", "정답: 안내서에 그대로 명시되어 있습니다."),
                                opt("어학 성적 없이도 바로 진학할 수 있다.", "🔵 파랑 - 명시된 조건과 반대되는 오답입니다."),
                                opt("어학연수는 필수 과정이다.", "🔵 파랑 - '어학연수 없이'와 반대되는 오답입니다."),
                                opt("학위 과정은 존재하지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 조건(어학 성적) 필요 여부를 반대로 착각하게 합니다.",
                                "[유학 마인드맵] 경로(바로 진학) + 조건(공인 어학 성적). 색칠하세요.")),
                onePassage(PassageCategory.READING, "비자 신청 안내문",
                        "[비자 신청 안내문] 재정 능력 입증 서류로 최근 3개월 은행 잔고증명서가 필요합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("최근 3개월 잔고증명서가 필요하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("잔고증명서는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("최근 1년 치 증명서가 필요하다.", "🔵 파랑 - 기간 숫자를 바꾼 오답입니다."),
                                opt("재정 증명은 선택 사항이다.", "🔵 파랑 - '필요합니다'라는 표현과 반대되는 오답입니다.")
                        ), 0, "🔵 필요 여부나 기간 숫자를 바꿔 오답을 만듭니다.",
                                "[비자 마인드맵] 서류(잔고증명서) + 기간(최근 3개월). 색칠하세요.")),
                onePassage(PassageCategory.READING, "외국인등록증 안내",
                        "[외국인등록증 안내] 등록증 신청 시 여권용 사진 1매와 여권 원본을 지참해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("여권용 사진과 여권 원본이 필요하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("사진 없이 신청할 수 있다.", "🔵 파랑 - '사진 1매'라는 요건과 반대되는 오답입니다."),
                                opt("여권 사본만 있으면 된다.", "🔵 파랑 - '여권 원본'과 반대되는 오답입니다."),
                                opt("사진은 2매가 필요하다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 필요 서류나 사진 매수, 원본/사본 여부를 바꿔 오답을 만듭니다.",
                                "[외국인등록 마인드맵] 서류(사진 1매 + 여권 원본). 색칠하세요.")),
                onePassage(PassageCategory.READING, "교환학생 모집 공고",
                        "[교환학생 모집 공고] 지원자는 어학 성적표와 자기소개서, 학업계획서를 함께 제출해야 합니다.",
                        q("이 공고에서 요구하는 제출 서류로 알맞은 것을 고르십시오.", List.of(
                                opt("어학 성적표, 자기소개서, 학업계획서", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("어학 성적표만", "🔵 파랑 - 다른 서류들을 빼고 읽게 하는 오답입니다."),
                                opt("추천서만", "🔵 파랑 - 언급되지 않은 서류입니다."),
                                opt("서류 제출이 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 세 가지 서류 중 일부만 인정하거나 무관한 서류로 오답을 만듭니다.",
                                "[교환학생 마인드맵] 서류 3종(성적표+자소서+계획서). 색칠하세요.")),
                onePassage(PassageCategory.READING, "논문 제출 안내",
                        "[논문 제출 안내] 논문은 지정된 양식에 맞춰 PDF 파일로 제출해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("논문은 PDF 파일로 제출해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("논문은 워드 파일로만 제출한다.", "🔵 파랑 - 'PDF'를 '워드'로 바꾼 오답입니다."),
                                opt("양식은 자유롭게 정해도 된다.", "🔵 파랑 - '지정된 양식'과 반대되는 오답입니다."),
                                opt("인쇄본만 제출하면 된다.", "🔵 파랑 - 'PDF 파일'과 반대되는 오답입니다.")
                        ), 0, "🔵 파일 형식이나 양식 자유도를 바꿔 오답을 만듭니다.",
                                "[논문 마인드맵] 형식(PDF) + 양식(지정). 색칠하세요.")),
                onePassage(PassageCategory.READING, "유학 안내서",
                        "[유학 안내서] 장학금은 성적 우수자에 한해 등록금의 최대 50%까지 지원됩니다.",
                        q("이 안내서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("성적 우수자는 최대 50% 장학금을 받는다.", "정답: 안내서에 그대로 명시되어 있습니다."),
                                opt("모든 학생이 장학금을 받는다.", "🔵 파랑 - '성적 우수자에 한해'라는 조건을 빼고 읽게 합니다."),
                                opt("장학금은 등록금 전액을 지원한다.", "🔵 파랑 - '최대 50%'라는 비율과 반대되는 오답입니다."),
                                opt("장학금 제도가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 대상 조건을 빼거나 지원 비율을 바꿔 오답을 만듭니다.",
                                "[유학 마인드맵] 조건(성적 우수자) + 비율(최대 50%). 색칠하세요.")),
                onePassage(PassageCategory.READING, "비자 신청 안내문",
                        "[비자 신청 안내문] 허위 서류 제출이 적발되면 향후 5년간 비자 발급이 제한될 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("허위 서류 적발 시 5년간 비자 발급이 제한될 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("허위 서류를 제출해도 문제없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("제한 기간은 1년이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("영구적으로 비자를 받을 수 없다.", "🔵 파랑 - '5년간'이라는 기한과 다른 과장된 오답입니다.")
                        ), 0, "🔵 제재 여부나 기간 숫자를 바꾸거나 영구 제한으로 과장합니다.",
                                "[비자 마인드맵] 위반(허위 서류) → 제재(5년 제한). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "외국인등록증 안내",
                        "[외국인등록증 안내] 등록 후 재외공관을 통해 발급받은 사증과 등록증 정보가 다르면 정정 신청이 필요합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("사증과 등록증 정보가 다르면 정정 신청이 필요하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("정보가 달라도 정정할 필요가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("사증과 등록증은 항상 같은 기관에서 발급한다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("정정 신청은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 정정 필요성이나 가능 여부를 반대로 착각하게 합니다.",
                                "[외국인등록 마인드맵] 정보 불일치 → 정정 신청 필요. 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "교환학생 모집 공고",
                        "[교환학생 모집 공고] 선발 결과에 이의가 있는 경우 결과 발표 후 7일 이내에 이의 신청할 수 있습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("이의 신청은 발표 후 7일 이내에 가능하다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("이의 신청 기한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("이의 신청은 발표 전에만 가능하다.", "🔵 파랑 - '발표 후'라는 시점과 반대되는 오답입니다."),
                                opt("이의 신청은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 신청 기한이나 시점(전/후)을 바꿔 오답을 만듭니다.",
                                "[교환학생 마인드맵] 발표 → 이의 신청(7일 이내). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "논문 제출 안내",
                        "[논문 제출 안내] 논문 제출 마감일을 지키지 못하면 다음 학기로 졸업이 연기됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("마감일을 못 지키면 졸업이 다음 학기로 연기된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("마감일을 못 지켜도 졸업에 영향이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("마감일이 지나도 언제든 제출할 수 있다.", "🔵 파랑 - '연기된다'는 결과와 반대되는 오답입니다."),
                                opt("논문 제출에 마감일이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 마감일 미준수의 결과를 반대로 착각하게 합니다.",
                                "[논문 마인드맵] 마감 미준수 → 졸업 연기(다음 학기). 색칠하세요."))
        );

        List<PassageSeed> reading6th31to40 = List.of(
                onePassage(PassageCategory.READING, "유학 안내서",
                        "[유학 안내서] 기숙사 신청은 선착순이므로 입학 허가 통보 즉시 신청하는 것이 유리합니다.",
                        q("이 안내서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("기숙사는 선착순으로 배정된다.", "정답: '선착순이므로'라고 명시했습니다."),
                                opt("기숙사는 성적순으로 배정된다.", "🔵 파랑 - '선착순'과 다른 오답입니다."),
                                opt("기숙사 신청은 서둘러도 소용없다.", "🔵 파랑 - '즉시 신청하는 것이 유리하다'와 반대되는 오답입니다."),
                                opt("기숙사는 신청할 필요가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 배정 기준(선착순/성적순)을 바꾸거나 신속 신청의 유리함을 반대로 착각하게 합니다.",
                                "[유학 마인드맵] 방식(선착순) → 전략(즉시 신청). 색칠하세요.")),
                onePassage(PassageCategory.READING, "비자 신청 안내문",
                        "[비자 신청 안내문] 미성년자의 경우 법정대리인의 동의서가 추가로 필요합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("미성년자는 법정대리인 동의서가 필요하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("미성년자는 비자를 신청할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("성인만 동의서가 필요하다.", "🔵 파랑 - '미성년자'라는 대상과 반대되는 오답입니다."),
                                opt("동의서는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 대상(미성년자/성인)이나 필요 서류 여부를 바꿔 오답을 만듭니다.",
                                "[비자 마인드맵] 대상(미성년자) + 추가 서류(동의서). 색칠하세요.")),
                onePassage(PassageCategory.READING, "외국인등록증 안내",
                        "[외국인등록증 안내] 등록증은 신분증으로 사용 가능하며, 은행 계좌 개설 시에도 활용됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("등록증은 은행 계좌 개설에도 활용된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("등록증은 신분증으로 사용할 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("은행 계좌 개설에는 여권만 필요하다.", "🔵 파랑 - '등록증도 활용된다'는 내용과 다른 오답입니다."),
                                opt("등록증은 용도가 제한적이다.", "🔵 파랑 - 다양하게 활용된다는 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 등록증의 활용 범위를 좁게 착각하게 합니다.",
                                "[외국인등록 마인드맵] 용도(신분증+계좌 개설). 두 용도를 확인하세요.")),
                onePassage(PassageCategory.READING, "교환학생 모집 공고",
                        "[교환학생 모집 공고] 파견 대학은 총 5개국 10개 대학 중에서 선택할 수 있습니다.",
                        q("이 공고에서 선택 가능한 대학 수로 알맞은 것을 고르십시오.", List.of(
                                opt("10개 대학", "정답: '5개국 10개 대학 중에서 선택'이라고 명시했습니다."),
                                opt("5개 대학", "🔵 파랑 - 국가 수(5개국)와 대학 수(10개)를 혼동하게 하는 오답입니다."),
                                opt("20개 대학", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("선택의 여지가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 국가 수와 대학 수를 혼동하게 하거나 숫자를 바꿔 오답을 만듭니다.",
                                "[교환학생 마인드맵] 국가(5개) + 대학(10개). 두 숫자를 구분하세요.")),
                onePassage(PassageCategory.READING, "논문 제출 안내",
                        "[논문 제출 안내] 공동 저자가 있는 경우 모든 저자의 동의서를 첨부해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("공동 저자 모두의 동의서가 필요하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("대표 저자 동의서만 있으면 된다.", "🔵 파랑 - '모든 저자'라는 내용과 반대되는 오답입니다."),
                                opt("공동 저자는 동의서가 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("공동 저자 논문은 제출할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 동의서 필요 범위(전체/대표)를 반대로 착각하게 합니다.",
                                "[논문 마인드맵] 공동 저자 → 모든 저자 동의서. 범위를 색칠하세요.")),
                onePassage(PassageCategory.READING, "유학 안내서",
                        "[유학 안내서] 현지 생활 적응을 위한 오리엔테이션이 도착 첫 주에 진행됩니다.",
                        q("이 안내서의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("오리엔테이션은 도착 첫 주에 진행된다.", "정답: 안내서에 그대로 명시되어 있습니다."),
                                opt("오리엔테이션은 출국 전에 진행된다.", "🔵 파랑 - '도착 첫 주'와 반대되는 오답입니다."),
                                opt("오리엔테이션은 학기 말에 진행된다.", "🔵 파랑 - 시점을 바꾼 오답입니다."),
                                opt("오리엔테이션은 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 진행 시점을 바꾸거나 존재 여부를 반대로 착각하게 합니다.",
                                "[유학 마인드맵] 시점(도착 첫 주) + 목적(현지 적응). 색칠하세요.")),
                onePassage(PassageCategory.READING, "비자 신청 안내문",
                        "[비자 신청 안내문] 신청 수수료는 비자 발급 여부와 관계없이 환불되지 않습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("발급 여부와 관계없이 수수료는 환불되지 않는다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("발급이 거부되면 수수료를 환불받는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("수수료는 항상 환불된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("수수료가 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 환불 가능 여부를 반대로 착각하게 합니다.",
                                "[비자 마인드맵] 수수료(환불 불가, 발급 여부 무관). 색칠하세요.")),
                onePassage(PassageCategory.READING, "외국인등록증 안내",
                        "[외국인등록증 안내] 등록증 갱신은 만료 2개월 전부터 온라인으로 예약 접수가 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("만료 2개월 전부터 온라인 예약이 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("만료 후에만 갱신할 수 있다.", "🔵 파랑 - 명시된 시점과 반대되는 오답입니다."),
                                opt("갱신 예약은 방문으로만 가능하다.", "🔵 파랑 - '온라인'과 반대되는 오답입니다."),
                                opt("갱신 기한은 1개월 전이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 예약 시점이나 방법, 숫자를 바꿔 오답을 만듭니다.",
                                "[외국인등록 마인드맵] 시점(만료 2개월 전) + 방법(온라인). 색칠하세요.")),
                onePassage(PassageCategory.READING, "교환학생 모집 공고",
                        "[교환학생 모집 공고] 파견 기간 중 발생하는 생활비는 본인이 부담해야 합니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("생활비는 본인이 부담해야 한다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("생활비는 학교에서 전액 지원한다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("생활비 부담이 전혀 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("생활비는 상대 학교가 부담한다.", "🔵 파랑 - '본인 부담'과 반대되는 오답입니다.")
                        ), 0, "🔵 생활비 부담 주체를 반대로 착각하게 합니다.",
                                "[교환학생 마인드맵] 생활비 부담 주체(본인). 핵심 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "논문 제출 안내",
                        "[논문 제출 안내] 최종 심사 통과 후에도 지도교수의 최종 확인 서명이 있어야 인쇄본 제출이 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("지도교수 서명이 있어야 인쇄본 제출이 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("서명 없이도 인쇄본을 제출할 수 있다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("심사 통과 전에 서명을 받아야 한다.", "🔵 파랑 - '통과 후에도'라는 순서와 반대되는 오답입니다."),
                                opt("인쇄본 제출은 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 서명 필요성이나 절차 순서를 반대로 착각하게 합니다.",
                                "[논문 마인드맵] 심사 통과 → 교수 서명 → 인쇄본 제출. 순서대로 색칠하세요."))
        );

        List<PassageSeed> listening7th1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "창업 준비",
                        "여자: 창업하려면 제일 먼저 뭘 준비해야 해요?\n남자: 사업 계획서부터 작성하는 게 좋아요.",
                        q("남자가 추천한 창업 준비 순서로 알맞은 것을 고르십시오.", List.of(
                                opt("사업 계획서 작성부터", "정답: '사업 계획서부터 작성하는 게 좋다'고 답했습니다."),
                                opt("사무실 계약부터", "🔴 빨강 - 순서를 뒤바꾼 오답입니다."),
                                opt("직원 채용부터", "🔴 빨강 - 언급되지 않은 순서입니다."),
                                opt("아무 준비도 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 '먼저'라는 순서 표현을 놓치고 다른 항목을 첫 단계로 착각하게 합니다.",
                                "[창업 마인드맵] 1(사업 계획서) → 2(이후 절차). '먼저'에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "사업자 등록",
                        "남자: 사업자 등록은 어디서 해요?\n여자: 관할 세무서에서 신청할 수 있어요.",
                        q("사업자 등록을 신청하는 곳으로 알맞은 것을 고르십시오.", List.of(
                                opt("관할 세무서", "정답: '관할 세무서에서 신청할 수 있다'고 답했습니다."),
                                opt("구청", "🟢 초록 - 언급되지 않은 기관입니다."),
                                opt("은행", "🟢 초록 - 언급되지 않은 기관입니다."),
                                opt("신청할 곳이 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 언급된 기관과 무관한 다른 기관으로 오답을 만듭니다.",
                                "[사업자등록 마인드맵] 신청처(관할 세무서). 기관명을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "프리랜서 세무",
                        "여자: 프리랜서도 세금 신고를 해야 하나요?\n남자: 네, 매년 5월에 종합소득세 신고를 해야 해요.",
                        q("프리랜서의 종합소득세 신고 시기로 알맞은 것을 고르십시오.", List.of(
                                opt("매년 5월", "정답: '매년 5월에 종합소득세 신고를 해야 한다'고 답했습니다."),
                                opt("매년 3월", "🔴 빨강 - 월을 바꾼 오답입니다."),
                                opt("매년 12월", "🔴 빨강 - 월을 바꾼 오답입니다."),
                                opt("신고할 필요가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 신고 월을 슬쩍 바꿔 오답을 만듭니다.",
                                "[세무 마인드맵] 대상(프리랜서) + 시기(5월). 월을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "소상공인 지원",
                        "남자: 소상공인 대출 지원도 있나요?\n여자: 네, 저금리로 자금을 대출해 주는 지원 제도가 있어요.",
                        q("소상공인 지원 제도로 알맞은 것을 고르십시오.", List.of(
                                opt("저금리 대출 지원", "정답: '저금리로 자금을 대출해 주는 지원 제도가 있다'고 답했습니다."),
                                opt("세금 전액 면제", "🔵 파랑 - 언급되지 않은 지원입니다."),
                                opt("무상 지원금 지급", "🔵 파랑 - '대출'을 '무상 지원'으로 바꾼 오답입니다."),
                                opt("지원 제도가 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 지원(저금리 대출)과 무관한 다른 지원으로 오답을 만듭니다.",
                                "[소상공인 마인드맵] 지원 종류(저금리 대출). 핵심 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "크라우드펀딩",
                        "여자: 크라우드펀딩은 목표 금액을 못 채우면 어떻게 돼요?\n남자: 목표 금액에 도달하지 못하면 후원금이 전액 환불돼요.",
                        q("목표 금액 미달성 시 결과로 알맞은 것을 고르십시오.", List.of(
                                opt("후원금이 전액 환불된다.", "정답: '목표 금액에 도달하지 못하면 후원금이 전액 환불된다'고 답했습니다."),
                                opt("모금한 금액만큼만 받는다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("프로젝트가 자동으로 연장된다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("환불이 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 결과(전액 환불)와 반대되거나 무관한 내용으로 오답을 만듭니다.",
                                "[크라우드펀딩 마인드맵] 목표 미달 → 결과(전액 환불). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "창업 준비",
                        "남자: 창업 자금은 어떻게 마련하는 게 좋아요?\n여자: 정부 창업지원금부터 알아보는 게 좋아요.",
                        q("여자가 추천한 창업 자금 마련 방법으로 알맞은 것을 고르십시오.", List.of(
                                opt("정부 창업지원금 알아보기", "정답: '정부 창업지원금부터 알아보는 게 좋다'고 답했습니다."),
                                opt("은행 대출만 받기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("가족에게 빌리기", "🟢 초록 - 언급되지 않은 방법입니다."),
                                opt("자금 마련이 필요 없다.", "🟢 초록 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🟢 실제 추천된 방법과 무관한 다른 방법으로 오답을 만듭니다.",
                                "[창업 마인드맵] 방법(정부 창업지원금). 핵심 명사에 형광펜을 칠하세요.")),
                onePassage(PassageCategory.LISTENING, "사업자 등록",
                        "여자: 사업자 등록증은 발급까지 얼마나 걸려요?\n남자: 신청 당일에 바로 발급돼요.",
                        q("사업자 등록증 발급까지 걸리는 시간으로 알맞은 것을 고르십시오.", List.of(
                                opt("신청 당일", "정답: '신청 당일에 바로 발급된다'고 답했습니다."),
                                opt("일주일", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("한 달", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("발급이 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 즉시 발급을 다른 기간으로 착각하게 합니다.",
                                "[사업자등록 마인드맵] 발급(신청 당일). '바로'라는 표현을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "프리랜서 세무",
                        "남자: 프리랜서 원천징수는 몇 퍼센트예요?\n여자: 보통 3.3%가 원천징수돼요.",
                        q("프리랜서 원천징수 비율로 알맞은 것을 고르십시오.", List.of(
                                opt("3.3%", "정답: '보통 3.3%가 원천징수된다'고 답했습니다."),
                                opt("10%", "🔴 빨강 - 비율을 바꾼 오답입니다."),
                                opt("1%", "🔴 빨강 - 비율을 바꾼 오답입니다."),
                                opt("원천징수가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 원천징수 비율 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[세무 마인드맵] 원천징수 비율(3.3%). 숫자를 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "소상공인 지원",
                        "여자: 소상공인 컨설팅도 받을 수 있어요?\n남자: 네, 경영 컨설팅을 무료로 받을 수 있는 프로그램이 있어요.",
                        q("소상공인 컨설팅 프로그램의 비용으로 알맞은 것을 고르십시오.", List.of(
                                opt("무료", "정답: '경영 컨설팅을 무료로 받을 수 있다'고 답했습니다."),
                                opt("유료", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("첫 회만 유료", "🔵 파랑 - 언급되지 않은 조건입니다."),
                                opt("컨설팅이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 무료/유료 여부를 반대로 착각하게 하거나 조건을 추가합니다.",
                                "[소상공인 마인드맵] 서비스(경영 컨설팅) + 비용(무료). 핵심 단어를 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "크라우드펀딩",
                        "남자: 펀딩 프로젝트는 최대 얼마나 진행할 수 있어요?\n여자: 보통 최대 60일까지 진행할 수 있어요.",
                        q("크라우드펀딩 프로젝트의 최대 진행 기간으로 알맞은 것을 고르십시오.", List.of(
                                opt("60일", "정답: '최대 60일까지 진행할 수 있다'고 답했습니다."),
                                opt("30일", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("100일", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("기간 제한이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 최대 기간 숫자를 슬쩍 바꿔 오답을 만듭니다.",
                                "[크라우드펀딩 마인드맵] 최대 기간(60일). 숫자를 정확히 확인하세요."))
        );

        List<PassageSeed> listening7th11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "창업 준비",
                        "여자: 창업 교육도 들어야 하나요?\n남자: 필수는 아니지만 들으면 지원 사업에서 가점을 받을 수 있어요.",
                        q("창업 교육 이수 시 얻을 수 있는 혜택으로 알맞은 것을 고르십시오.", List.of(
                                opt("지원 사업 가점", "정답: '들으면 지원 사업에서 가점을 받을 수 있다'고 답했습니다."),
                                opt("세금 면제", "🔵 파랑 - 언급되지 않은 혜택입니다."),
                                opt("무료 사무실 제공", "🔵 파랑 - 언급되지 않은 혜택입니다."),
                                opt("혜택이 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 혜택과 무관한 다른 혜택으로 오답을 만듭니다.",
                                "[창업 마인드맵] 교육(선택) → 혜택(가점). 조건과 혜택을 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "사업자 등록",
                        "남자: 사업자 등록 안 하고 장사하면 어떻게 돼요?\n여자: 무등록 영업으로 가산세가 부과될 수 있어요.",
                        q("사업자 등록 없이 영업할 때의 불이익으로 알맞은 것을 고르십시오.", List.of(
                                opt("가산세 부과", "정답: '가산세가 부과될 수 있다'고 답했습니다."),
                                opt("영업 자체가 불가능하다.", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("아무 불이익이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("세금이 면제된다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 불이익과 반대되거나 무관한 내용으로 오답을 만듭니다.",
                                "[사업자등록 마인드맵] 무등록 → 불이익(가산세). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "프리랜서 세무",
                        "여자: 경비 처리는 어떻게 해요?\n남자: 업무 관련 영수증을 잘 모아두면 경비로 인정받을 수 있어요.",
                        q("경비 인정을 위해 해야 할 일로 알맞은 것을 고르십시오.", List.of(
                                opt("업무 관련 영수증 모으기", "정답: '업무 관련 영수증을 잘 모아두면 경비로 인정받을 수 있다'고 답했습니다."),
                                opt("영수증을 버리기", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("세무서에 매일 방문하기", "🔴 빨강 - 언급되지 않은 내용입니다."),
                                opt("경비 처리는 불가능하다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 실제 방법과 반대되거나 무관한 다른 행동으로 오답을 만듭니다.",
                                "[세무 마인드맵] 방법(영수증 보관) → 결과(경비 인정). 색으로 연결하세요.")),
                onePassage(PassageCategory.LISTENING, "소상공인 지원",
                        "남자: 임대료 지원도 받을 수 있나요?\n여자: 네, 일정 조건을 충족하면 임대료 일부를 지원받을 수 있어요.",
                        q("임대료 지원에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("일정 조건 충족 시 임대료 일부를 지원받는다.", "정답: 대화에 그대로 나와 있습니다."),
                                opt("조건 없이 전액 지원받는다.", "🔵 파랑 - '일정 조건'과 '일부'라는 내용을 무시한 오답입니다."),
                                opt("임대료 지원은 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다."),
                                opt("모든 사업자가 지원받는다.", "🔵 파랑 - 조건을 빼고 읽게 하는 오답입니다.")
                        ), 0, "🔵 조건이나 지원 범위(전액/일부)를 반대로 착각하게 합니다.",
                                "[소상공인 마인드맵] 조건(충족 시) + 범위(일부 지원). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "크라우드펀딩",
                        "여자: 펀딩 수수료는 얼마나 돼요?\n남자: 모금액의 5% 정도가 플랫폼 수수료로 나가요.",
                        q("크라우드펀딩 플랫폼 수수료로 알맞은 것을 고르십시오.", List.of(
                                opt("모금액의 5%", "정답: '모금액의 5% 정도가 플랫폼 수수료로 나간다'고 답했습니다."),
                                opt("모금액의 20%", "🔴 빨강 - 숫자를 바꾼 오답입니다."),
                                opt("수수료가 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("고정 금액 10만 원", "🔴 빨강 - 대화에 없는 방식입니다.")
                        ), 0, "🔴 수수료 비율 숫자를 슬쩍 바꾸거나 방식을 왜곡합니다.",
                                "[크라우드펀딩 마인드맵] 수수료(모금액의 5%). 비율을 정확히 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "창업 준비",
                        "남자: 상권 분석은 꼭 해야 해요?\n여자: 네, 상권 분석 없이 시작하면 실패 위험이 커요.",
                        q("상권 분석을 하지 않았을 때의 결과로 알맞은 것을 고르십시오.", List.of(
                                opt("실패 위험이 커진다.", "정답: '상권 분석 없이 시작하면 실패 위험이 커진다'고 답했습니다."),
                                opt("성공 확률이 높아진다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("아무 영향이 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("비용이 줄어든다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 위험 증가를 반대로 착각하게 하거나 무관한 결과로 오답을 만듭니다.",
                                "[창업 마인드맵] 상권 분석 없음 → 실패 위험 증가. 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "사업자 등록",
                        "여자: 사업 종류를 바꾸면 다시 등록해야 해요?\n남자: 아니요, 업종 추가나 변경은 정정 신고만 하면 돼요.",
                        q("업종 변경 시 해야 할 절차로 알맞은 것을 고르십시오.", List.of(
                                opt("정정 신고", "정답: '업종 추가나 변경은 정정 신고만 하면 된다'고 답했습니다."),
                                opt("사업자 재등록", "🔴 빨강 - '다시 등록'과 반대되는 오답입니다."),
                                opt("폐업 신고", "🔴 빨강 - 언급되지 않은 절차입니다."),
                                opt("아무 절차도 필요 없다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔴 재등록과 정정 신고를 혼동하게 합니다.",
                                "[사업자등록 마인드맵] 업종 변경 → 정정 신고(재등록 아님). 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "프리랜서 세무",
                        "남자: 부가가치세도 신고해야 하나요?\n여자: 프리랜서는 대부분 면세 사업자라 부가세 신고 대상이 아니에요.",
                        q("프리랜서의 부가가치세 신고에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("대부분 면세 사업자라 신고 대상이 아니다.", "정답: 대화에 그대로 나와 있습니다."),
                                opt("모든 프리랜서가 신고해야 한다.", "🔵 파랑 - '대부분'이라는 표현과 반대되는 오답입니다."),
                                opt("부가가치세는 존재하지 않는다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("종합소득세와 같은 개념이다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 신고 대상 여부를 반대로 착각하게 하거나 세금 종류를 혼동하게 합니다.",
                                "[세무 마인드맵] 프리랜서(대부분 면세) → 부가세 신고 제외. 색칠하세요.")),
                onePassage(PassageCategory.LISTENING, "소상공인 지원",
                        "여자: 폐업할 때도 지원받을 수 있어요?\n남자: 네, 폐업 컨설팅과 재취업 지원 프로그램이 있어요.",
                        q("폐업 시 받을 수 있는 지원으로 알맞은 것을 고르십시오.", List.of(
                                opt("폐업 컨설팅과 재취업 지원", "정답: 대화에 그대로 나와 있습니다."),
                                opt("창업 자금 추가 지원", "🔵 파랑 - 언급되지 않은 지원입니다."),
                                opt("세금 완전 면제", "🔵 파랑 - 언급되지 않은 지원입니다."),
                                opt("지원이 전혀 없다.", "🔵 파랑 - 대화 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 실제 언급된 지원과 무관한 다른 지원으로 오답을 만듭니다.",
                                "[소상공인 마인드맵] 폐업 → 지원(컨설팅+재취업). 두 지원을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "크라우드펀딩",
                        "남자: 리워드형이랑 후원형이랑 뭐가 달라요?\n여자: 리워드형은 후원 대가로 상품을 받고, 후원형은 대가 없이 순수하게 후원하는 거예요.",
                        q("리워드형 크라우드펀딩에 대한 설명으로 맞는 것을 고르십시오.", List.of(
                                opt("후원 대가로 상품을 받는다.", "정답: '리워드형은 후원 대가로 상품을 받는다'고 답했습니다."),
                                opt("대가 없이 순수하게 후원한다.", "🔴 빨강 - 이는 후원형에 대한 설명입니다."),
                                opt("아무 방식도 아니다.", "🔴 빨강 - 대화 내용과 반대되는 오답입니다."),
                                opt("투자금을 돌려받는 방식이다.", "🔴 빨강 - 언급되지 않은 내용입니다.")
                        ), 0, "🔴 리워드형과 후원형의 설명을 뒤바꿔 오답을 만듭니다.",
                                "[크라우드펀딩 마인드맵] 리워드형(상품) ≠ 후원형(무대가). 두 개념을 색으로 구분하세요."))
        );

        List<PassageSeed> reading7th21to30 = List.of(
                onePassage(PassageCategory.READING, "창업 지원 안내문",
                        "[창업 지원 안내문] 예비 창업자 지원 사업은 만 39세 이하 청년을 우선 대상으로 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("만 39세 이하 청년이 우선 대상이다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("연령 제한이 전혀 없다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("40세 이상만 지원 가능하다.", "🔵 파랑 - 명시된 대상과 반대되는 오답입니다."),
                                opt("청년이 아니면 지원할 수 없다.", "🔵 파랑 - '우선 대상'이라는 표현을 '전용'으로 과장한 오답입니다.")
                        ), 0, "🔵 연령 조건을 반대로 착각하게 하거나 우선 대상을 전용 대상으로 과장합니다.",
                                "[창업 마인드맵] 대상(만 39세 이하, 우선). 숫자와 표현을 색칠하세요.")),
                onePassage(PassageCategory.READING, "사업자 등록 안내",
                        "[사업자 등록 안내] 사업 개시일로부터 20일 이내에 사업자 등록을 신청해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("개시일로부터 20일 이내에 등록해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("등록 기한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("개시일로부터 60일 이내다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("사업 개시 전에 등록해야 한다.", "🔵 파랑 - '개시일로부터'라는 기준과 다른 오답입니다.")
                        ), 0, "🔵 기한 숫자나 기준 시점을 바꿔 오답을 만듭니다.",
                                "[사업자등록 마인드맵] 기준(개시일) + 기한(20일 이내). 색칠하세요.")),
                onePassage(PassageCategory.READING, "세무 신고 안내문",
                        "[세무 신고 안내문] 홈택스를 이용하면 세무서 방문 없이 온라인으로 신고할 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("홈택스로 온라인 신고가 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("반드시 세무서를 방문해야 한다.", "🔵 파랑 - '방문 없이'와 반대되는 오답입니다."),
                                opt("온라인 신고는 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("홈택스는 유료 서비스다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 방문 필요성이나 온라인 신고 가능 여부를 반대로 착각하게 합니다.",
                                "[세무 마인드맵] 방법(홈택스) + 특징(방문 불필요). 색칠하세요.")),
                onePassage(PassageCategory.READING, "소상공인 지원 공고",
                        "[소상공인 지원 공고] 지원금은 사업자등록증 발급 후 6개월 이상 운영 중인 업체만 신청할 수 있습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("6개월 이상 운영한 업체만 신청 가능하다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("신규 등록 업체도 바로 신청할 수 있다.", "🔵 파랑 - '6개월 이상 운영'이라는 조건과 반대되는 오답입니다."),
                                opt("운영 기간과 상관없이 신청 가능하다.", "🔵 파랑 - 명시된 조건을 빼고 읽게 합니다."),
                                opt("1년 이상 운영해야 한다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 운영 기간 조건 숫자를 바꾸거나 조건을 빼고 읽게 합니다.",
                                "[소상공인 마인드맵] 조건(6개월 이상 운영). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "크라우드펀딩 안내",
                        "[크라우드펀딩 안내] 프로젝트 개설자는 후원자에게 진행 상황을 정기적으로 업데이트해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("진행 상황을 정기적으로 업데이트해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("업데이트는 선택 사항이다.", "🔵 파랑 - '해야 한다'는 의무와 반대되는 오답입니다."),
                                opt("업데이트는 프로젝트 종료 후에만 한다.", "🔵 파랑 - '정기적으로'라는 내용과 다른 오답입니다."),
                                opt("후원자와의 소통은 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 의무 여부나 업데이트 시점을 반대로 착각하게 합니다.",
                                "[크라우드펀딩 마인드맵] 의무(정기 업데이트). 빈도 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "창업 지원 안내문",
                        "[창업 지원 안내문] 선정된 팀에게는 최대 5천만 원의 사업화 자금이 지원됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("최대 5천만 원까지 지원받을 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("지원 금액에 상한이 없다.", "🔵 파랑 - '최대'라는 표현과 반대되는 오답입니다."),
                                opt("지원금은 1천만 원으로 고정이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("모든 지원자가 자동으로 받는다.", "🔵 파랑 - '선정된 팀'이라는 조건을 빼고 읽게 합니다.")
                        ), 0, "🔵 상한 금액을 바꾸거나 선정 조건을 빼고 읽게 합니다.",
                                "[창업 마인드맵] 조건(선정) + 한도(최대 5천만 원). 색칠하세요.")),
                onePassage(PassageCategory.READING, "사업자 등록 안내",
                        "[사업자 등록 안내] 공동사업자로 등록하려면 동업 계약서를 함께 제출해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("공동사업자는 동업 계약서를 제출해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("동업 계약서는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("공동사업자 등록은 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("단독사업자만 계약서가 필요하다.", "🔵 파랑 - '공동사업자'라는 대상과 반대되는 오답입니다.")
                        ), 0, "🔵 제출 서류 필요 여부나 대상을 반대로 착각하게 합니다.",
                                "[사업자등록 마인드맵] 대상(공동사업자) + 서류(동업 계약서). 색칠하세요.")),
                onePassage(PassageCategory.READING, "세무 신고 안내문",
                        "[세무 신고 안내문] 기한 후 신고 시에도 가산세가 붙지만 무신고보다는 부담이 적습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("기한 후 신고가 무신고보다 부담이 적다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("기한 후 신고는 가산세가 전혀 없다.", "🔵 파랑 - '가산세가 붙는다'와 반대되는 오답입니다."),
                                opt("무신고가 기한 후 신고보다 유리하다.", "🔵 파랑 - 명시된 비교와 반대되는 오답입니다."),
                                opt("기한 후 신고는 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 가산세 유무나 기한 후 신고와 무신고의 유불리를 반대로 착각하게 합니다.",
                                "[세무 마인드맵] 기한 후 신고(가산세 있음, 무신고보다 유리). 색칠하세요.")),
                onePassage(PassageCategory.READING, "소상공인 지원 공고",
                        "[소상공인 지원 공고] 신청은 온라인으로만 접수하며, 서면 접수는 받지 않습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신청은 온라인으로만 가능하다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("서면 접수도 가능하다.", "🔵 파랑 - '서면 접수는 받지 않는다'와 반대되는 오답입니다."),
                                opt("온라인 접수는 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("방문 접수만 가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 접수 방법(온라인/서면/방문)을 바꿔 오답을 만듭니다.",
                                "[소상공인 마인드맵] 방법(온라인만) + 제외(서면 불가). 색칠하세요.")),
                onePassage(PassageCategory.READING, "크라우드펀딩 안내",
                        "[크라우드펀딩 안내] 목표 금액을 초과 달성한 경우에도 추가 후원을 계속 받을 수 있습니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("목표 초과 달성 후에도 후원을 계속 받을 수 있다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("목표 달성 즉시 후원이 마감된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("초과 후원금은 받을 수 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("목표 금액은 변경할 수 없다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 목표 달성 후 후원 지속 가능 여부를 반대로 착각하게 합니다.",
                                "[크라우드펀딩 마인드맵] 목표 초과 → 추가 후원 계속 가능. 색칠하세요."))
        );

        List<PassageSeed> reading7th31to40 = List.of(
                onePassage(PassageCategory.READING, "창업 지원 안내문",
                        "[창업 지원 안내문] 사업화 자금은 용도가 증빙된 항목에 한해 정산됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("용도가 증빙된 항목만 정산된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("모든 지출이 증빙 없이 정산된다.", "🔵 파랑 - '증빙된 항목에 한해'라는 조건을 빼고 읽게 합니다."),
                                opt("정산 절차가 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("증빙은 선택 사항이다.", "🔵 파랑 - '증빙된 항목에 한해'와 반대되는 오답입니다.")
                        ), 0, "🔵 증빙 필요성을 반대로 착각하게 하거나 조건을 빼고 읽게 합니다.",
                                "[창업 마인드맵] 조건(증빙) → 정산. 조건 표현에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "사업자 등록 안내",
                        "[사업자 등록 안내] 간이과세자는 연 매출 8천만 원 미만인 경우에 해당합니다.",
                        q("이 안내문의 간이과세자 기준으로 알맞은 것을 고르십시오.", List.of(
                                opt("연 매출 8천만 원 미만", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("연 매출 1억 원 이상", "🔵 파랑 - 명시된 기준과 반대되는 오답입니다."),
                                opt("연 매출과 무관하다.", "🔵 파랑 - 명시된 기준과 반대되는 오답입니다."),
                                opt("연 매출 3천만 원 미만", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 매출 기준 숫자를 바꾸거나 기준 자체를 반대로 착각하게 합니다.",
                                "[사업자등록 마인드맵] 간이과세자 기준(8천만 원 미만). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "세무 신고 안내문",
                        "[세무 신고 안내문] 신고 도움 서비스는 매년 신고 기간에 한시적으로 운영됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("신고 도움 서비스는 신고 기간에만 운영된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("신고 도움 서비스는 연중 상시 운영된다.", "🔵 파랑 - '한시적으로'라는 표현과 반대되는 오답입니다."),
                                opt("이 서비스는 유료다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("서비스가 제공되지 않는다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 운영 기간(한시적/상시)을 반대로 착각하게 합니다.",
                                "[세무 마인드맵] 운영(신고 기간 한정). '한시적'이라는 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "소상공인 지원 공고",
                        "[소상공인 지원 공고] 동일 사업자가 동시에 두 개 이상의 지원 사업에 중복 신청할 수 없습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("두 개 이상의 지원 사업에 중복 신청할 수 없다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("여러 지원 사업에 동시 신청 가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("한 개의 지원 사업만 존재한다.", "🔵 파랑 - 언급되지 않은 내용입니다."),
                                opt("중복 신청은 권장된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 중복 신청 가능 여부를 반대로 착각하게 합니다.",
                                "[소상공인 마인드맵] 제한(중복 신청 불가). 부정 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "크라우드펀딩 안내",
                        "[크라우드펀딩 안내] 후원 취소는 결제 후 24시간 이내에만 가능합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("후원 취소는 결제 후 24시간 이내에 가능하다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("후원 취소는 언제든지 가능하다.", "🔵 파랑 - '24시간 이내'라는 제한과 반대되는 오답입니다."),
                                opt("후원 취소는 불가능하다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("취소 기한은 일주일이다.", "🔵 파랑 - 숫자를 바꾼 오답입니다.")
                        ), 0, "🔵 취소 가능 기한을 바꾸거나 취소 자체가 불가능하다고 왜곡합니다.",
                                "[크라우드펀딩 마인드맵] 기한(결제 후 24시간). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "창업 지원 안내문",
                        "[창업 지원 안내문] 지원 사업 참여 중 다른 기관의 유사 사업에 중복 참여는 제한됩니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("유사 사업 중복 참여가 제한된다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("여러 유사 사업에 자유롭게 참여할 수 있다.", "🔵 파랑 - 명시된 제한과 반대되는 오답입니다."),
                                opt("중복 참여는 권장된다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("다른 기관 사업은 존재하지 않는다.", "🔵 파랑 - 언급되지 않은 내용입니다.")
                        ), 0, "🔵 중복 참여 가능 여부를 반대로 착각하게 합니다.",
                                "[창업 마인드맵] 제한(유사 사업 중복 참여 금지). 부정 표현을 확인하세요.")),
                onePassage(PassageCategory.READING, "사업자 등록 안내",
                        "[사업자 등록 안내] 폐업 신고는 사업 종료일로부터 지체 없이 해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("폐업 신고는 지체 없이 해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("폐업 신고는 나중에 해도 상관없다.", "🔵 파랑 - '지체 없이'라는 표현과 반대되는 오답입니다."),
                                opt("폐업 신고는 필요 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("폐업 신고 기한은 1년이다.", "🔵 파랑 - 언급되지 않은 구체적 기한입니다.")
                        ), 0, "🔵 신고 시급성을 반대로 착각하게 하거나 언급되지 않은 기한을 추가합니다.",
                                "[사업자등록 마인드맵] 폐업 → 지체 없는 신고. '지체 없이'를 확인하세요.")),
                onePassage(PassageCategory.READING, "세무 신고 안내문",
                        "[세무 신고 안내문] 경정청구는 신고 기한 후 5년 이내에 세금을 돌려받기 위해 신청하는 제도입니다.",
                        q("이 안내문의 경정청구 신청 기한으로 알맞은 것을 고르십시오.", List.of(
                                opt("신고 기한 후 5년 이내", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("신고 기한 후 1년 이내", "🔵 파랑 - 숫자를 바꾼 오답입니다."),
                                opt("기한 제한이 없다.", "🔵 파랑 - 명시된 기한과 반대되는 오답입니다."),
                                opt("신고 기한 전에만 가능하다.", "🔵 파랑 - '기한 후'라는 시점과 반대되는 오답입니다.")
                        ), 0, "🔵 기한 숫자를 바꾸거나 시점(전/후)을 반대로 착각하게 합니다.",
                                "[세무 마인드맵] 경정청구 기한(신고 후 5년 이내). 숫자에 밑줄을 그으세요.")),
                onePassage(PassageCategory.READING, "소상공인 지원 공고",
                        "[소상공인 지원 공고] 지원금을 목적 외 용도로 사용하면 전액 환수될 수 있습니다.",
                        q("이 공고의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("목적 외 사용 시 전액 환수될 수 있다.", "정답: 공고에 그대로 명시되어 있습니다."),
                                opt("목적 외 사용해도 문제없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다."),
                                opt("일부만 환수된다.", "🔵 파랑 - '전액'과 반대되는 오답입니다."),
                                opt("용도 제한이 없다.", "🔵 파랑 - 명시된 내용과 반대되는 오답입니다.")
                        ), 0, "🔵 환수 여부나 환수 범위(전액/일부)를 반대로 착각하게 합니다.",
                                "[소상공인 마인드맵] 목적 외 사용 → 전액 환수. 인과관계를 색칠하세요.")),
                onePassage(PassageCategory.READING, "크라우드펀딩 안내",
                        "[크라우드펀딩 안내] 프로젝트 실패 시에도 이미 사용한 후원금에 대한 소명 자료를 제출해야 합니다.",
                        q("이 안내문의 내용으로 맞는 것을 고르십시오.", List.of(
                                opt("프로젝트 실패 시에도 소명 자료를 제출해야 한다.", "정답: 안내문에 그대로 명시되어 있습니다."),
                                opt("실패하면 아무 책임이 없다.", "🔵 파랑 - 명시된 의무와 반대되는 오답입니다."),
                                opt("성공한 프로젝트만 소명이 필요하다.", "🔵 파랑 - '실패 시에도'라는 내용과 반대되는 오답입니다."),
                                opt("소명 자료는 선택 사항이다.", "🔵 파랑 - '제출해야 한다'는 의무와 반대되는 오답입니다.")
                        ), 0, "🔵 실패 시 책임 여부나 소명 의무를 반대로 착각하게 합니다.",
                                "[크라우드펀딩 마인드맵] 실패해도 → 소명 자료 제출 의무. 색칠하세요."))
        );

        return new WeekSeed("1~2급 컬러맵 마무리",
                "생활 행정과 여가 실무 어휘를 색깔 코딩과 마인드맵으로 마무리한다.",
                WEEK4_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(자동차 정비, 운전면허 시험, 주차 문제, 교통 법규, 대중교통 민원) + 읽기 20(자동차 정비소 안내문, 운전면허 시험 안내, 주차장 이용 안내문, 교통 법규 위반 통지서, 대중교통 민원 게시판). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening1to10, listening11to20, reading21to30, reading31to40)),
                        day("2차(40문항) - 듣기 20(재난 대비, 화재 대피, 지진 대응, 응급 신고, 안전 교육) + 읽기 20(재난 대비 안내문, 화재 대피 요령, 지진 행동 요령, 응급 신고 안내, 안전 교육 공지). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening2nd1to10, listening2nd11to20, reading2nd21to30, reading2nd31to40)),
                        day("3차(40문항) - 듣기 20(부동산 계약, 전월세 계약, 이사 견적, 등기 신청, 관리비 정산) + 읽기 20(부동산 매물 정보, 전월세 계약서 요약, 이사 견적서, 등기소 안내문, 관리비 명세서). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening3rd1to10, listening3rd11to20, reading3rd21to30, reading3rd31to40)),
                        day("4차(40문항) - 듣기 20(도서 대출 연장, 문화센터 강좌, 청소년 상담, 노인 복지관, 다문화 지원 센터) + 읽기 20(도서관 대출 연장 안내, 문화센터 강좌 안내, 청소년 상담 안내문, 노인 복지관 프로그램, 다문화 지원 안내문). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening4th1to10, listening4th11to20, reading4th21to30, reading4th31to40)),
                        day("5차(40문항) - 듣기 20(이력서 작성, 면접 준비, 인턴십 지원, 연봉 협상, 퇴사 절차) + 읽기 20(이력서 작성 가이드, 면접 안내문, 인턴십 모집 공고, 연봉 협상 후기, 퇴사 절차 안내문). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening5th1to10, listening5th11to20, reading5th21to30, reading5th31to40)),
                        day("6차(40문항) - 듣기 20(유학 준비, 비자 신청, 외국인 등록, 교환학생 프로그램, 졸업 논문) + 읽기 20(유학 안내서, 비자 신청 안내문, 외국인등록증 안내, 교환학생 모집 공고, 논문 제출 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening6th1to10, listening6th11to20, reading6th21to30, reading6th31to40)),
                        day("7차(40문항) - 듣기 20(창업 준비, 사업자 등록, 프리랜서 세무, 소상공인 지원, 크라우드펀딩) + 읽기 20(창업 지원 안내문, 사업자 등록 안내, 세무 신고 안내문, 소상공인 지원 공고, 크라우드펀딩 안내). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(listening7th1to10, listening7th11to20, reading7th21to30, reading7th31to40))
                ));
    }

    // ===================== WEEK 5: 1~2급 컬러맵 문법 심화 기초 =====================

    private static final String WEEK5_ANSWER_NOTE_TEMPLATE = """
            [🎨 오답 노트 템플릿 - WEEK5용]
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

    private WeekSeed week5() {
        List<PassageSeed> lv12w5_1st1to2 = List.of(
                grammarUnit("-으면 안 되다 (해서는 안 됨을 나타냄)",
                        "[문법 포인트] -으면 안 되다\n의미: 어떤 행동을 하면 안 된다는 금지를 나타내는 표현입니다.\n형태: 동사 어간 + (으)면 안 되다\n예문1: 여기에서 담배를 피우면 안 돼요.\n예문2: 수업 시간에 늦으면 안 돼요.\n예문3: 도서관에서 큰 소리로 떠들면 안 돼요.",
                        q("가: 여기에 주차해도 될까요?\n나: 아니요, 여기에 __________. 저쪽에 주차장이 있어요.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("주차하면 안 돼요", "정답: 금지를 나타낼 때는 '-으면 안 되다'가 맞습니다."),
                                opt("주차해야 해요", "🟢 초록 - 의무를 나타내는 표현으로 금지와는 다릅니다."),
                                opt("주차할 수 있어요", "🟢 초록 - 가능을 나타내는 표현으로 금지와 반대됩니다."),
                                opt("주차하고 싶어요", "🟢 초록 - 희망을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "🟢 의무·가능·희망 표현과 혼동하기 쉽습니다.", "[금지 마인드맵] 여기 주차 → 금지(안 됨). '아니요'라는 부정 답변을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n수업 시간에 __________.", List.of(
                                opt("늦으면 안 돼요", "정답: 수업에 늦는 행동을 금지할 때는 '-으면 안 되다'가 맞습니다."),
                                opt("늦어야 해요", "🟢 초록 - 의무를 나타내는 표현으로 금지와는 다릅니다."),
                                opt("늦을 수 있어요", "🟢 초록 - 가능을 나타내는 표현으로 금지와 반대됩니다."),
                                opt("늦고 싶어요", "🟢 초록 - 희망을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "🟢 의무·가능·희망 표현과 혼동하기 쉽습니다.", "[금지 마인드맵] 수업 시간 → 지각 금지. 규칙을 나타내는 문장임을 확인하세요."),
                        q("다음 밑줄 친 '-으면 안 되다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"도서관에서 큰 소리로 떠들면 안 돼요.\"", List.of(
                                opt("어떤 행동을 하면 안 된다는 금지", "정답: '-으면 안 되다'는 금지를 나타냅니다."),
                                opt("어떤 행동을 반드시 해야 한다는 의무", "의무가 아니라 금지를 나타냅니다."),
                                opt("어떤 행동을 할 수 있다는 가능", "가능이 아니라 금지를 나타냅니다."),
                                opt("어떤 행동을 하고 싶다는 희망", "희망이 아니라 금지를 나타냅니다.")
                        ), 0, "의무·가능·희망으로 오해하기 쉽습니다.", "[금지 마인드맵] 도서관 → 떠들기 금지. 장소 규칙을 나타냅니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("여기에서 담배를 피우면 안 돼요.", "정답: 동사 '피우다'에 '-으면 안 되다'가 올바르게 결합했습니다."),
                                opt("여기에서 담배를 피우으면 안 돼요.", "'피우다'는 모음으로 끝나므로 '-면 안 되다'가 결합해야 하는데 '으'가 잘못 삽입됐습니다."),
                                opt("여기에서 담배를 피움 안 돼요.", "'-으면'이 빠지고 명사형 어미로 잘못 변형되었습니다."),
                                opt("여기에서 담배를 피우 안 돼요.", "연결어미 '-으면'이 통째로 빠져 어색한 형태가 되었습니다.")
                        ), 0, "받침 유무에 따른 '-면/-으면' 결합, 어미 누락에 주의해야 합니다.", "[금지 마인드맵] 담배 피우다(모음 끝) → 피우면 안 돼요. 받침 없는 동사는 '-면'과 결합합니다."),
                        q("가: 이 우유 아직 마셔도 될까요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("유통기한이 지나서 마시면 안 돼요.", "정답: 유통기한이 지났다는 이유로 금지하는 자연스러운 응답입니다."),
                                opt("유통기한이 지나서 마셔야 해요.", "의무 표현으로는 이 문맥과 어울리지 않습니다."),
                                opt("유통기한이 지나서 마실 수 있어요.", "가능 표현으로는 이 문맥과 맞지 않습니다."),
                                opt("유통기한이 지나서 마시고 싶어요.", "희망 표현으로는 이 문맥과 어울리지 않습니다.")
                        ), 0, "의무·가능·희망 표현과 혼동하기 쉽습니다.", "[금지 마인드맵] 유통기한 지남 → 마시면 안 됨. 이유와 금지를 색으로 연결하세요.")),
                grammarUnit("-을 수 있다 (가능함을 나타냄)",
                        "[문법 포인트] -을 수 있다\n의미: 어떤 일을 할 능력이나 가능성이 있음을 나타내는 표현입니다.\n형태: 동사 어간 + (으)ㄹ 수 있다\n예문1: 저는 한국어를 할 수 있어요.\n예문2: 이 문제는 혼자서도 풀 수 있어요.\n예문3: 내일은 일찍 올 수 있어요.",
                        q("가: 한국어로 편지를 쓸 수 있어요?\n나: 네, __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("쓸 수 있어요", "정답: 가능함을 나타낼 때는 '-을 수 있다'가 맞습니다."),
                                opt("쓰면 안 돼요", "🟢 초록 - 금지를 나타내는 표현으로 가능과는 다릅니다."),
                                opt("써야 해요", "🟢 초록 - 의무를 나타내는 표현으로 가능과는 다릅니다."),
                                opt("쓰고 싶어 해요", "🟢 초록 - 제3자의 희망을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "금지·의무·희망 표현과 혼동하기 쉽습니다.", "[가능 마인드맵] 한국어 편지 → 가능(쓸 수 있음). '네'라는 긍정 답변을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n이 문제는 혼자서도 __________.", List.of(
                                opt("풀 수 있어요", "정답: 혼자 풀 수 있는 능력을 나타내는 '-을 수 있다'가 맞습니다."),
                                opt("풀면 안 돼요", "금지를 나타내는 표현으로 이 문맥에는 어울리지 않습니다."),
                                opt("풀어야 해요", "의무를 나타내는 표현으로 가능과는 다릅니다."),
                                opt("풀고 싶어 해요", "제3자의 희망을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "금지·의무·희망 표현과 혼동하기 쉽습니다.", "[가능 마인드맵] 혼자서 → 가능(풀 수 있음). 능력을 나타내는 문장임을 확인하세요."),
                        q("다음 밑줄 친 '-을 수 있다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"내일은 일찍 올 수 있어요.\"", List.of(
                                opt("어떤 일을 할 능력이나 가능성이 있음", "정답: '-을 수 있다'는 가능성을 나타냅니다."),
                                opt("어떤 일을 하면 안 된다는 금지", "금지가 아니라 가능성을 나타냅니다."),
                                opt("어떤 일을 반드시 해야 한다는 의무", "의무가 아니라 가능성을 나타냅니다."),
                                opt("어떤 일을 하지 못한다는 불가능", "불가능이 아니라 가능성을 나타냅니다.")
                        ), 0, "금지·의무·불가능으로 오해하기 쉽습니다.", "[가능 마인드맵] 내일 → 일찍 올 가능성. 긍정적 가능성을 나타냅니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("저는 한국어를 할 수 있어요.", "정답: 동사 '하다'에 '-ㄹ 수 있다'가 올바르게 결합했습니다."),
                                opt("저는 한국어를 하을 수 있어요.", "'하다'는 모음으로 끝나므로 '-ㄹ 수 있다'가 결합해야 하는데 '으'가 잘못 삽입됐습니다."),
                                opt("저는 한국어를 함 수 있어요.", "'-ㄹ'이 명사형 어미 '-ㅁ'으로 잘못 변형되었습니다."),
                                opt("저는 한국어를 하 수 있어요.", "필수적인 관형형 어미 '-ㄹ'이 빠져 어색한 형태가 되었습니다.")
                        ), 0, "받침 유무에 따른 결합, 어미 누락에 주의해야 합니다.", "[가능 마인드맵] 하다(모음 끝) → 할 수 있어요. 받침 없는 동사는 'ㄹ'과 결합합니다."),
                        q("가: 내일 회의에 참석할 수 있어요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("네, 내일은 시간이 있어서 참석할 수 있어요.", "정답: 시간이 있어서 참석 가능하다는 것을 나타내는 자연스러운 응답입니다."),
                                opt("네, 내일은 시간이 있어서 참석하면 안 돼요.", "금지 표현으로는 이 문맥과 어울리지 않습니다."),
                                opt("네, 내일은 시간이 있어서 참석해야 해요.", "의무 표현으로 답하기엔 어색합니다."),
                                opt("네, 내일은 시간이 있어서 참석하지 못해요.", "불가능 표현은 질문의 답변과 모순됩니다.")
                        ), 0, "금지·의무·불가능 표현과 혼동하기 쉽습니다.", "[가능 마인드맵] 시간 있음 → 참석 가능. 이유와 가능성을 색으로 연결하세요."))
        );

        List<PassageSeed> lv12w5_1st3to4 = List.of(
                grammarUnit("-고 싶어 하다 (제3자의 희망을 나타냄)",
                        "[문법 포인트] -고 싶어 하다\n의미: 말하는 사람이 아닌 다른 사람이 무엇을 하고 싶어 함을 나타내는 표현입니다.\n형태: 동사 어간 + 고 싶어 하다\n예문1: 동생이 새 신발을 사고 싶어 해요.\n예문2: 친구가 한국에 가고 싶어 해요.\n예문3: 아이가 강아지를 키우고 싶어 해요.",
                        q("가: 동생이 요즘 뭘 사고 싶어 해요?\n나: 동생이 새 신발을 __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("사고 싶어 해요", "정답: 제3자(동생)의 희망을 나타낼 때는 '-고 싶어 하다'가 맞습니다."),
                                opt("사고 싶어요", "🔴 빨강 - 말하는 사람 자신의 희망을 나타내는 표현이라 주어(동생)와 맞지 않습니다."),
                                opt("사면 안 돼요", "🔴 빨강 - 금지를 나타내는 표현으로 희망과는 다릅니다."),
                                opt("살 수 있어요", "🔴 빨강 - 가능을 나타내는 표현으로 희망과는 다릅니다.")
                        ), 0, "1인칭 희망('-고 싶다')과 3인칭 희망('-고 싶어 하다')을 혼동하기 쉽습니다.", "[희망 마인드맵] 주어(동생, 3인칭) → -고 싶어 하다. 주어를 먼저 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n친구가 한국에 __________.", List.of(
                                opt("가고 싶어 해요", "정답: 3인칭 주어(친구)의 희망을 나타내는 '-고 싶어 하다'가 맞습니다."),
                                opt("가고 싶어요", "1인칭 희망 표현이라 주어(친구)와 맞지 않습니다."),
                                opt("가면 안 돼요", "금지를 나타내는 표현으로 이 문맥과 맞지 않습니다."),
                                opt("갈 수 있어요", "가능을 나타내는 표현으로 희망과는 다릅니다.")
                        ), 0, "1인칭/3인칭 희망 표현을 혼동하기 쉽습니다.", "[희망 마인드맵] 주어(친구, 3인칭) → -고 싶어 하다. 인칭에 따라 형태가 달라짐을 기억하세요."),
                        q("다음 밑줄 친 '-고 싶어 하다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"아이가 강아지를 키우고 싶어 해요.\"", List.of(
                                opt("말하는 사람이 아닌 다른 사람이 무엇을 하고 싶어 함", "정답: '-고 싶어 하다'는 제3자의 희망을 나타냅니다."),
                                opt("말하는 사람 자신이 무엇을 하고 싶음", "1인칭이 아니라 3인칭(아이)의 희망을 나타냅니다."),
                                opt("어떤 행동을 금지함", "금지가 아니라 희망을 나타냅니다."),
                                opt("어떤 행동이 가능함", "가능이 아니라 희망을 나타냅니다.")
                        ), 0, "1인칭 희망, 금지, 가능으로 오해하기 쉽습니다.", "[희망 마인드맵] 아이(3인칭) → 강아지 키우고 싶어 함. 주어에 형광펜을 칠하세요."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("동생이 새 신발을 사고 싶어 해요.", "정답: 3인칭 주어 '동생'에 '-고 싶어 하다'가 올바르게 결합했습니다."),
                                opt("동생이 새 신발을 사고 싶어요.", "3인칭 주어에는 '-고 싶어 하다'를 써야 하는데 1인칭 형태를 사용했습니다."),
                                opt("동생이 새 신발을 사고 싶은 해요.", "'싶어'가 '싶은'으로 잘못 변형되었습니다."),
                                opt("동생이 새 신발을 사고 싶 해요.", "'싶어'에서 '어'가 빠져 어색한 형태가 되었습니다.")
                        ), 0, "인칭 불일치, 어미 변형, 어미 누락에 주의해야 합니다.", "[희망 마인드맵] 3인칭 주어에는 반드시 '-고 싶어 하다'를 사용합니다."),
                        q("가: 조카가 요즘 뭘 배우고 싶어 해요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("피아노를 배우고 싶어 해요.", "정답: 3인칭 주어(조카)의 희망을 나타내는 자연스러운 응답입니다."),
                                opt("피아노를 배우고 싶어요.", "1인칭 희망 표현이라 주어(조카)와 맞지 않습니다."),
                                opt("피아노를 배우면 안 돼요.", "금지 표현으로는 질문과 어울리지 않습니다."),
                                opt("피아노를 배울 수 있어요.", "가능 표현으로는 질문의 의도와 맞지 않습니다.")
                        ), 0, "1인칭/3인칭 희망 표현을 혼동하기 쉽습니다.", "[희망 마인드맵] 조카(3인칭) → 배우고 싶어 함. 인칭 표현을 확인하세요.")),
                grammarUnit("-으려고 하다 (어떤 행동을 하려는 의도를 나타냄)",
                        "[문법 포인트] -으려고 하다\n의미: 어떤 행동을 할 의도나 계획이 있음을 나타내는 표현입니다.\n형태: 동사 어간 + (으)려고 하다\n예문1: 저는 내년에 유학을 가려고 해요.\n예문2: 오늘은 일찍 자려고 해요.\n예문3: 주말에 집을 청소하려고 해요.",
                        q("가: 주말에 뭐 할 거예요?\n나: 집을 __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("청소하려고 해요", "정답: 청소할 의도를 나타낼 때는 '-으려고 하다'가 맞습니다."),
                                opt("청소하면 안 돼요", "금지를 나타내는 표현으로 의도와는 다릅니다."),
                                opt("청소한 적이 있어요", "과거 경험을 나타내는 표현으로 미래 의도와는 다릅니다."),
                                opt("청소할 수 있어요", "가능을 나타내는 표현으로 의도와는 다릅니다.")
                        ), 0, "금지·경험·가능 표현과 혼동하기 쉽습니다.", "[의도 마인드맵] 주말 계획 → 청소하려는 의도. 미래 계획 표현임을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n저는 내년에 유학을 __________.", List.of(
                                opt("가려고 해요", "정답: 유학을 갈 의도를 나타내는 '-으려고 하다'가 맞습니다."),
                                opt("가면 안 돼요", "금지를 나타내는 표현으로 이 문맥에는 어울리지 않습니다."),
                                opt("간 적이 있어요", "과거 경험을 나타내는 표현으로 미래 의도와는 다릅니다."),
                                opt("갈 수 있어요", "가능을 나타내는 표현으로 의도와는 다릅니다.")
                        ), 0, "금지·경험·가능 표현과 혼동하기 쉽습니다.", "[의도 마인드맵] 내년 → 유학 가려는 의도. 시점과 계획을 색으로 연결하세요."),
                        q("다음 밑줄 친 '-으려고 하다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"오늘은 일찍 자려고 해요.\"", List.of(
                                opt("어떤 행동을 할 의도나 계획이 있음", "정답: '-으려고 하다'는 의도·계획을 나타냅니다."),
                                opt("어떤 행동을 이미 완료함", "완료가 아니라 앞으로의 계획을 나타냅니다."),
                                opt("어떤 행동을 하면 안 됨", "금지가 아니라 의도를 나타냅니다."),
                                opt("어떤 행동을 할 수 없음", "불가능이 아니라 의도를 나타냅니다.")
                        ), 0, "완료·금지·불가능으로 오해하기 쉽습니다.", "[의도 마인드맵] 오늘 → 일찍 자려는 계획. 미래 지향적 표현입니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("주말에 집을 청소하려고 해요.", "정답: 동사 '청소하다'에 '-려고 하다'가 올바르게 결합했습니다."),
                                opt("주말에 집을 청소으려고 해요.", "'청소하다'는 모음으로 끝나므로 '-려고 하다'가 결합해야 하는데 '으'가 잘못 삽입됐습니다."),
                                opt("주말에 집을 청소함 하려고 해요.", "불필요한 명사형 어미가 삽입되어 어색합니다."),
                                opt("주말에 집을 청소하려 안 해요.", "'-려고 해요'가 '-려 안 해요'로 어색하게 변형되었습니다.")
                        ), 0, "받침 유무에 따른 결합, 불필요한 어미 삽입에 주의해야 합니다.", "[의도 마인드맵] 청소하다(모음 끝) → 청소하려고 해요. 받침 없는 동사는 '려고'와 결합합니다."),
                        q("가: 이번 방학에 뭐 할 계획이에요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("고향에 다녀오려고 해요.", "정답: 방학 계획을 나타내는 자연스러운 응답입니다."),
                                opt("고향에 다녀오면 안 돼요.", "금지 표현으로는 계획을 묻는 질문과 어울리지 않습니다."),
                                opt("고향에 다녀온 적이 있어요.", "과거 경험 표현으로는 계획을 묻는 질문과 맞지 않습니다."),
                                opt("고향에 다녀올 수 있어요.", "가능 표현으로는 계획을 나타내기엔 어색합니다.")
                        ), 0, "금지·경험·가능 표현과 혼동하기 쉽습니다.", "[의도 마인드맵] 방학 계획 → 고향 방문 의도. 미래 계획을 나타냅니다."))
        );

        List<PassageSeed> lv12w5_1st5to6 = List.of(
                grammarUnit("-은 적이 있다 (과거의 경험을 나타냄)",
                        "[문법 포인트] -은 적이 있다\n의미: 과거에 어떤 일을 경험한 적이 있음을 나타내는 표현입니다.\n형태: 동사 어간 + (으)ㄴ 적이 있다\n예문1: 저는 제주도에 가 본 적이 있어요.\n예문2: 이 노래를 들은 적이 있어요.\n예문3: 김치를 직접 만든 적이 있어요.",
                        q("가: 제주도에 가 봤어요?\n나: 네, 작년에 __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("가 본 적이 있어요", "정답: 과거 경험을 나타낼 때는 '-은 적이 있다'가 맞습니다."),
                                opt("가려고 해요", "🔴 빨강 - 미래 의도를 나타내는 표현으로 과거 경험과는 다릅니다."),
                                opt("갈 수 있어요", "🔴 빨강 - 가능을 나타내는 표현으로 경험과는 다릅니다."),
                                opt("가면 안 돼요", "🔴 빨강 - 금지를 나타내는 표현으로 경험과는 다릅니다.")
                        ), 0, "미래 의도·가능·금지 표현과 혼동하기 쉽습니다.", "[경험 마인드맵] 작년(과거) → 가 본 경험. 시제 표현을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n이 노래를 예전에 __________.", List.of(
                                opt("들은 적이 있어요", "정답: 예전에 들은 경험을 나타내는 '-은 적이 있다'가 맞습니다."),
                                opt("들으려고 해요", "미래 의도를 나타내는 표현으로 과거 경험과는 다릅니다."),
                                opt("들을 수 있어요", "가능을 나타내는 표현으로 경험과는 다릅니다."),
                                opt("들으면 안 돼요", "금지를 나타내는 표현으로 경험과는 다릅니다.")
                        ), 0, "미래 의도·가능·금지 표현과 혼동하기 쉽습니다.", "[경험 마인드맵] 예전(과거) → 들은 경험. 시점 표현에 형광펜을 칠하세요."),
                        q("다음 밑줄 친 '-은 적이 있다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"김치를 직접 만든 적이 있어요.\"", List.of(
                                opt("과거에 어떤 일을 경험한 적이 있음", "정답: '-은 적이 있다'는 과거의 경험을 나타냅니다."),
                                opt("앞으로 어떤 일을 할 계획임", "미래 계획이 아니라 과거 경험을 나타냅니다."),
                                opt("어떤 일을 지금 하고 있음", "현재 진행이 아니라 과거 경험을 나타냅니다."),
                                opt("어떤 일을 한 번도 해 본 적이 없음", "경험 없음이 아니라 경험 있음을 나타냅니다.")
                        ), 0, "미래 계획·현재 진행·경험 없음으로 오해하기 쉽습니다.", "[경험 마인드맵] 김치 만들기 → 과거 경험 있음. 완료된 경험을 나타냅니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("저는 제주도에 가 본 적이 있어요.", "정답: '가 보다'에 '-ㄴ 적이 있다'가 올바르게 결합했습니다."),
                                opt("저는 제주도에 가 본 적을 있어요.", "조사 '이'가 '을'로 잘못 바뀌었습니다."),
                                opt("저는 제주도에 가 본 적이 했어요.", "'있어요'가 '했어요'로 잘못 바뀌어 어색합니다."),
                                opt("저는 제주도에 가 본 적 있어요.", "조사 '이'가 빠져 다소 구어체적이나 격식체로는 부적절합니다.")
                        ), 0, "조사 오류, 서술어 오류에 주의해야 합니다.", "[경험 마인드맵] '적이 있다'처럼 명사 '적'에 조사 '이'가 결합해야 올바른 형태입니다."),
                        q("가: 외국에서 살아 본 적이 있어요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("네, 어렸을 때 미국에서 살아 본 적이 있어요.", "정답: 과거 경험을 나타내는 자연스러운 응답입니다."),
                                opt("네, 어렸을 때 미국에서 살려고 해요.", "미래 의도 표현으로는 질문(경험 여부)과 맞지 않습니다."),
                                opt("네, 어렸을 때 미국에서 살 수 있어요.", "가능 표현으로는 질문과 맞지 않습니다."),
                                opt("네, 어렸을 때 미국에서 살면 안 돼요.", "금지 표현으로는 질문과 전혀 맞지 않습니다.")
                        ), 0, "미래 의도·가능·금지 표현과 혼동하기 쉽습니다.", "[경험 마인드맵] 어렸을 때(과거) → 살아 본 경험. 시제를 확인하세요.")),
                grammarUnit("-는 동안에 (어떤 일이 진행되는 시간 동안)",
                        "[문법 포인트] -는 동안에\n의미: 어떤 일이 진행되는 시간 동안 다른 일이 함께 일어남을 나타내는 표현입니다.\n형태: 동사 어간 + 는 동안에\n예문1: 제가 요리하는 동안에 청소를 해 주세요.\n예문2: 잠을 자는 동안에 전화가 왔어요.\n예문3: 아이가 노는 동안에 숙제를 봐 줬어요.",
                        q("가: 언제 청소를 하면 좋을까요?\n나: 제가 요리하는 __________ 청소를 해 주세요.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("동안에", "정답: 요리하는 시간 동안 청소를 함께 하는 것을 나타낼 때는 '-는 동안에'가 맞습니다."),
                                opt("전에", "🔵 파랑 - 시간의 순서(이전)를 나타내는 표현으로 동시 진행과는 다릅니다."),
                                opt("후에", "🔵 파랑 - 시간의 순서(이후)를 나타내는 표현으로 동시 진행과는 다릅니다."),
                                opt("때문에", "🔵 파랑 - 이유를 나타내는 표현으로 시간과는 다릅니다.")
                        ), 0, "시간 순서(전/후)나 이유 표현과 혼동하기 쉽습니다.", "[동시진행 마인드맵] 요리(진행) + 청소(동시 진행). 두 행동이 겹치는 시간을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n잠을 자는 __________ 전화가 왔어요.", List.of(
                                opt("동안에", "정답: 자는 시간 동안 전화가 온 것을 나타내는 '-는 동안에'가 맞습니다."),
                                opt("전에", "시간의 순서(이전)를 나타내는 표현으로 동시 진행과는 다릅니다."),
                                opt("후에", "시간의 순서(이후)를 나타내는 표현으로 동시 진행과는 다릅니다."),
                                opt("때문에", "이유를 나타내는 표현으로 이 문맥과는 다릅니다.")
                        ), 0, "시간 순서나 이유 표현과 혼동하기 쉽습니다.", "[동시진행 마인드맵] 자는 시간 중 → 전화가 옴. 겹치는 시간을 색칠하세요."),
                        q("다음 밑줄 친 '-는 동안에'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"아이가 노는 동안에 숙제를 봐 줬어요.\"", List.of(
                                opt("어떤 일이 진행되는 시간 동안 다른 일이 함께 일어남", "정답: '-는 동안에'는 두 행동의 시간적 동시성을 나타냅니다."),
                                opt("어떤 일이 끝난 후에 다른 일이 일어남", "순차적 완료가 아니라 동시 진행을 나타냅니다."),
                                opt("어떤 일이 일어나기 전에 다른 일이 일어남", "사전 순서가 아니라 동시 진행을 나타냅니다."),
                                opt("어떤 일 때문에 다른 일이 일어남", "인과관계가 아니라 시간적 동시성을 나타냅니다.")
                        ), 0, "순차적 완료·사전 순서·인과관계로 오해하기 쉽습니다.", "[동시진행 마인드맵] 아이가 노는 시간 중 → 숙제 봐 줌. 동시성을 나타냅니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("제가 요리하는 동안에 청소를 해 주세요.", "정답: 동사 '요리하다'에 '-는 동안에'가 올바르게 결합했습니다."),
                                opt("제가 요리한 동안에 청소를 해 주세요.", "관형형 어미가 '-는'에서 '-ㄴ'으로 잘못 바뀌었습니다."),
                                opt("제가 요리하기 동안에 청소를 해 주세요.", "'-는'이 명사형 어미 '-기'로 잘못 바뀌었습니다."),
                                opt("제가 요리하다 동안에 청소를 해 주세요.", "어간에 관형형 어미 없이 기본형이 그대로 쓰여 어색합니다.")
                        ), 0, "관형형 어미 오류에 주의해야 합니다.", "[동시진행 마인드맵] 동사 어간 + '-는 동안에'가 올바른 결합 형태입니다."),
                        q("가: 아기가 잘 때 뭐 하세요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("아기가 자는 동안에 집안일을 해요.", "정답: 아기가 자는 시간 동안 집안일을 한다는 동시 진행을 나타내는 자연스러운 응답입니다."),
                                opt("아기가 자는 전에 집안일을 해요.", "'전에'는 '자기 전'을 뜻해 질문(잘 때)과 시점이 어긋납니다."),
                                opt("아기가 자는 후에 집안일을 해요.", "'후에'는 '자고 난 후'를 뜻해 질문과 시점이 어긋납니다."),
                                opt("아기가 자는 때문에 집안일을 해요.", "'때문에'는 이유를 나타내어 문법적으로 어색합니다.")
                        ), 0, "시간 순서 표현이나 어색한 이유 표현과 혼동하기 쉽습니다.", "[동시진행 마인드맵] 아기 수면 시간 중 → 집안일. 동시 진행을 나타냅니다."))
        );

        List<PassageSeed> lv12w5_1st7to8 = List.of(
                grammarUnit("-으면서 (두 가지 동작이 동시에 일어남을 나타냄)",
                        "[문법 포인트] -으면서\n의미: 한 사람이 두 가지 이상의 동작을 동시에 함을 나타내는 표현입니다.\n형태: 동사 어간 + (으)면서\n예문1: 저는 음악을 들으면서 공부해요.\n예문2: 그는 웃으면서 이야기했어요.\n예문3: 밥을 먹으면서 텔레비전을 봐요.",
                        q("가: 공부할 때 조용한 게 좋아요?\n나: 아니요, 저는 음악을 __________ 공부하는 걸 좋아해요.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("들으면서", "정답: 음악을 듣는 것과 공부하는 것을 동시에 함을 나타낼 때는 '-으면서'가 맞습니다."),
                                opt("들은 후에", "🔵 파랑 - 순서(이후)를 나타내는 표현으로 동시 동작과는 다릅니다."),
                                opt("듣기 전에", "🔵 파랑 - 순서(이전)를 나타내는 표현으로 동시 동작과는 다릅니다."),
                                opt("들어서", "🔵 파랑 - 이유를 나타내는 표현으로 동시 동작과는 다릅니다.")
                        ), 0, "순서(전/후)나 이유 표현과 혼동하기 쉽습니다.", "[동시동작 마인드맵] 듣기 + 공부하기 (동시). 같은 사람이 두 동작을 함께 함을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n그는 __________ 이야기했어요.", List.of(
                                opt("웃으면서", "정답: 웃는 동작과 이야기하는 동작을 동시에 함을 나타내는 '-으면서'가 맞습니다."),
                                opt("웃은 후에", "순서(이후)를 나타내는 표현으로 동시 동작과는 다릅니다."),
                                opt("웃기 전에", "순서(이전)를 나타내는 표현으로 동시 동작과는 다릅니다."),
                                opt("웃어서", "이유를 나타내는 표현으로 동시 동작과는 다릅니다.")
                        ), 0, "순서나 이유 표현과 혼동하기 쉽습니다.", "[동시동작 마인드맵] 웃기 + 이야기하기 (동시). 두 동작이 함께 일어남을 확인하세요."),
                        q("다음 밑줄 친 '-으면서'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"밥을 먹으면서 텔레비전을 봐요.\"", List.of(
                                opt("한 사람이 두 가지 이상의 동작을 동시에 함", "정답: '-으면서'는 동시 동작을 나타냅니다."),
                                opt("한 동작이 끝난 후에 다른 동작을 함", "순차적 완료가 아니라 동시 동작을 나타냅니다."),
                                opt("한 동작을 하기 전에 다른 동작을 함", "사전 순서가 아니라 동시 동작을 나타냅니다."),
                                opt("한 동작 때문에 다른 동작을 함", "인과관계가 아니라 동시 동작을 나타냅니다.")
                        ), 0, "순차적 완료·사전 순서·인과관계로 오해하기 쉽습니다.", "[동시동작 마인드맵] 먹기 + 보기 (동시). 같은 시간에 일어나는 두 동작입니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("저는 음악을 들으면서 공부해요.", "정답: 동사 '듣다'에 '-으면서'가 올바르게 결합했습니다."),
                                opt("저는 음악을 듣면서 공부해요.", "'듣다'는 'ㄷ' 불규칙 동사로 '들으면서'가 되어야 하는데 '듣면서'로 잘못 활용됐습니다."),
                                opt("저는 음악을 들었면서 공부해요.", "과거 시제 '-었-'이 불필요하게 삽입되어 어색합니다."),
                                opt("저는 음악을 들으며서 공부해요.", "'-으면서'가 '-으며서'로 잘못 변형되었습니다.")
                        ), 0, "'ㄷ' 불규칙 활용, 시제 삽입 오류, 어미 변형에 주의해야 합니다.", "[동시동작 마인드맵] 듣다('ㄷ' 불규칙) → 들으면서. 불규칙 활용에 주의하세요."),
                        q("가: 친구랑 어떻게 시간을 보내요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("커피를 마시면서 이야기를 많이 해요.", "정답: 커피를 마시는 것과 이야기하는 것을 동시에 함을 나타내는 자연스러운 응답입니다."),
                                opt("커피를 마신 후에 이야기를 많이 해요.", "순서(마신 후)를 나타내는 표현으로 동시성과는 다릅니다."),
                                opt("커피를 마시기 전에 이야기를 많이 해요.", "순서(마시기 전)를 나타내는 표현으로 동시성과는 다릅니다."),
                                opt("커피를 마셔서 이야기를 많이 해요.", "이유를 나타내는 표현으로 동시성과는 다릅니다.")
                        ), 0, "순서나 이유 표현과 혼동하기 쉽습니다.", "[동시동작 마인드맵] 마시기 + 이야기하기 (동시). 두 동작이 함께임을 확인하세요.")),
                grammarUnit("-을 텐데 (추측이나 예상되는 상황을 나타냄)",
                        "[문법 포인트] -을 텐데\n의미: 앞으로 그럴 것이라고 추측하며, 그 상황과 관련된 다른 말을 이어갈 때 쓰는 표현입니다.\n형태: 동사/형용사 어간 + (으)ㄹ 텐데\n예문1: 비가 올 텐데 우산을 가져가세요.\n예문2: 배고플 텐데 얼른 드세요.\n예문3: 길이 막힐 텐데 일찍 출발하세요.",
                        q("가: 우산을 안 가져왔는데 괜찮을까요?\n나: 비가 __________ 우산을 가져가는 게 좋겠어요.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("올 텐데", "정답: 비가 올 것이라는 추측을 나타낼 때는 '-을 텐데'가 맞습니다."),
                                opt("온 적이 있는데", "🔵 파랑 - 과거 경험을 나타내는 표현으로 미래 추측과는 다릅니다."),
                                opt("오면 안 되는데", "🔵 파랑 - 금지를 나타내는 표현으로 추측과는 다릅니다."),
                                opt("오지 않을 수 있는데", "🔵 파랑 - 가능성 부정을 나타내는 표현으로 이 문맥과는 다릅니다.")
                        ), 0, "과거 경험·금지·가능성 부정 표현과 혼동하기 쉽습니다.", "[추측 마인드맵] 비 올 것 같음(추측) → 우산 권유. 추측과 조언을 색으로 연결하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n배고플 __________ 얼른 드세요.", List.of(
                                opt("텐데", "정답: 배고플 것이라는 추측을 나타내는 '-을 텐데'가 맞습니다."),
                                opt("적이 있는데", "과거 경험을 나타내는 표현으로 추측과는 다릅니다."),
                                opt("수 없는데", "불가능을 나타내는 표현으로 추측과는 다릅니다."),
                                opt("면 안 되는데", "금지를 나타내는 표현으로 추측과는 다릅니다.")
                        ), 0, "과거 경험·불가능·금지 표현과 혼동하기 쉽습니다.", "[추측 마인드맵] 배고플 것 같음(추측) → 식사 권유. 추측 표현을 확인하세요."),
                        q("다음 밑줄 친 '-을 텐데'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"길이 막힐 텐데 일찍 출발하세요.\"", List.of(
                                opt("앞으로 그럴 것이라고 추측하며 관련된 말을 이어감", "정답: '-을 텐데'는 추측을 바탕으로 조언이나 권유를 이어갑니다."),
                                opt("과거에 그런 일이 실제로 있었음", "과거 사실이 아니라 미래에 대한 추측을 나타냅니다."),
                                opt("어떤 일을 절대로 하면 안 됨", "금지가 아니라 추측을 나타냅니다."),
                                opt("어떤 일이 이미 끝났음", "완료가 아니라 앞으로의 추측을 나타냅니다.")
                        ), 0, "과거 사실·금지·완료로 오해하기 쉽습니다.", "[추측 마인드맵] 길 막힐 것 같음(추측) → 일찍 출발 권유. 추측 뒤에 오는 조언을 확인하세요."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("비가 올 텐데 우산을 가져가세요.", "정답: 동사 '오다'에 '-ㄹ 텐데'가 올바르게 결합했습니다."),
                                opt("비가 오을 텐데 우산을 가져가세요.", "'오다'는 모음으로 끝나므로 '-ㄹ 텐데'가 결합해야 하는데 '으'가 잘못 삽입됐습니다."),
                                opt("비가 옴 텐데 우산을 가져가세요.", "'-ㄹ'이 명사형 어미 '-ㅁ'으로 잘못 변형되었습니다."),
                                opt("비가 오 텐데 우산을 가져가세요.", "필수적인 관형형 어미 '-ㄹ'이 빠져 어색한 형태가 되었습니다.")
                        ), 0, "받침 유무에 따른 결합, 어미 누락에 주의해야 합니다.", "[추측 마인드맵] 오다(모음 끝) → 올 텐데. 받침 없는 동사는 'ㄹ'과 결합합니다."),
                        q("가: 오늘 발표 준비 다 됐어요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("많이 떨릴 텐데 마음 편하게 하세요.", "정답: 떨릴 것이라는 추측을 바탕으로 조언하는 자연스러운 응답입니다."),
                                opt("많이 떨린 적이 있는데 마음 편하게 하세요.", "과거 경험 표현으로는 이 문맥과 어울리지 않습니다."),
                                opt("많이 떨리면 안 되는데 마음 편하게 하세요.", "금지 표현으로는 자연스럽지 않습니다."),
                                opt("많이 떨릴 수 없는데 마음 편하게 하세요.", "불가능 표현으로는 문맥과 맞지 않습니다.")
                        ), 0, "과거 경험·금지·불가능 표현과 혼동하기 쉽습니다.", "[추측 마인드맵] 떨릴 것 같음(추측) → 격려. 추측과 위로를 색으로 연결하세요."))
        );

        List<PassageSeed> lv12w5_2nd1to2 = List.of(
                grammarUnit("-는데 (뒤 내용의 배경이나 상황을 설명함)",
                        "[문법 포인트] -는데\n의미: 뒤에 오는 말을 하기 위해 관련된 상황이나 배경을 미리 설명하는 표현입니다.\n형태: 동사 어간 + 는데\n예문1: 지금 밥을 먹는데 같이 드실래요?\n예문2: 비가 오는데 우산 있어요?\n예문3: 이 옷이 마음에 드는데 좀 비싸요.",
                        q("가: 지금 뭐 하세요?\n나: 밥을 __________ 같이 드실래요?\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("먹는데", "정답: 배경 상황을 설명하며 제안할 때는 '-는데'가 맞습니다."),
                                opt("먹어서", "🔵 파랑 - 이유를 나타내는 표현으로 배경 설명과는 다릅니다."),
                                opt("먹으면", "🔵 파랑 - 조건을 나타내는 표현으로 배경 설명과는 다릅니다."),
                                opt("먹으려고", "🔵 파랑 - 의도를 나타내는 표현으로 배경 설명과는 다릅니다.")
                        ), 0, "이유·조건·의도 표현과 혼동하기 쉽습니다.", "[배경설명 마인드맵] 상황(밥 먹는 중) → 제안(같이 먹기). 배경과 제안을 색으로 연결하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n비가 __________ 우산 있어요?", List.of(
                                opt("오는데", "정답: 비가 오는 상황을 배경으로 설명하는 '-는데'가 맞습니다."),
                                opt("와서", "이유를 나타내는 표현으로 배경 설명과는 다릅니다."),
                                opt("오면", "조건을 나타내는 표현으로 배경 설명과는 다릅니다."),
                                opt("오려고", "의도를 나타내는 표현으로 배경 설명과는 다릅니다.")
                        ), 0, "이유·조건·의도 표현과 혼동하기 쉽습니다.", "[배경설명 마인드맵] 상황(비 옴) → 질문(우산 있는지). 배경 뒤에 질문이 이어집니다."),
                        q("다음 밑줄 친 '-는데'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"이 옷이 마음에 드는데 좀 비싸요.\"", List.of(
                                opt("뒤에 오는 말을 위해 관련 상황을 미리 설명함", "정답: '-는데'는 배경 설명 후 관련 내용을 이어갑니다."),
                                opt("어떤 일의 이유를 강하게 주장함", "이유 강조가 아니라 배경 설명을 나타냅니다."),
                                opt("어떤 일을 반드시 해야 한다고 명령함", "명령이 아니라 배경 설명을 나타냅니다."),
                                opt("어떤 일이 완전히 끝났음을 선언함", "완료 선언이 아니라 배경 설명을 나타냅니다.")
                        ), 0, "이유 강조·명령·완료 선언으로 오해하기 쉽습니다.", "[배경설명 마인드맵] 마음에 듦(배경) → 비쌈(대조). 앞뒤 내용이 자연스럽게 이어집니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("지금 밥을 먹는데 같이 드실래요?", "정답: 동사 '먹다'에 '-는데'가 올바르게 결합했습니다."),
                                opt("지금 밥을 먹은데 같이 드실래요?", "'-는데'가 '-은데'로 잘못 변형되었습니다."),
                                opt("지금 밥을 먹기는데 같이 드실래요?", "불필요한 '-기'가 삽입되어 어색합니다."),
                                opt("지금 밥을 먹데 같이 드실래요?", "'-는'이 빠져 어색한 형태가 되었습니다.")
                        ), 0, "어미 변형, 불필요한 삽입, 어미 누락에 주의해야 합니다.", "[배경설명 마인드맵] 동사 어간 + '-는데'가 올바른 결합 형태입니다."),
                        q("가: 이 식당 어때요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("음식은 맛있는데 좀 시끄러워요.", "정답: 배경(맛있음)을 설명한 후 대조되는 내용(시끄러움)을 이어가는 자연스러운 응답입니다."),
                                opt("음식은 맛있어서 좀 시끄러워요.", "이유 표현으로는 두 내용의 관계가 어색합니다."),
                                opt("음식은 맛있으면 좀 시끄러워요.", "조건 표현으로는 이 문맥과 맞지 않습니다."),
                                opt("음식은 맛있으려고 좀 시끄러워요.", "의도 표현으로는 문맥과 전혀 맞지 않습니다.")
                        ), 0, "이유·조건·의도 표현과 혼동하기 쉽습니다.", "[배경설명 마인드맵] 맛있음(배경) → 시끄러움(대조). 두 정보를 이어주는 연결어미입니다.")),
                grammarUnit("-아서 그런지 (추측되는 원인을 나타냄)",
                        "[문법 포인트] -아서 그런지\n의미: 확실하지 않지만 그것이 원인인 것 같다고 추측할 때 쓰는 표현입니다.\n형태: 동사/형용사 어간 + 아서/어서 그런지\n예문1: 날씨가 추워서 그런지 사람이 적어요.\n예문2: 급하게 먹어서 그런지 배가 아파요.\n예문3: 오랜만에 만나서 그런지 어색해요.",
                        q("가: 오늘 왜 이렇게 사람이 적어요?\n나: 날씨가 __________ 사람이 적은 것 같아요.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("추워서 그런지", "정답: 추운 날씨가 원인일 것이라는 추측을 나타낼 때는 '-아서 그런지'가 맞습니다."),
                                opt("추우면 안 돼서", "금지를 나타내는 표현으로 추측과는 다릅니다."),
                                opt("추울 수 있어서", "가능을 나타내는 표현으로 추측 원인과는 다릅니다."),
                                opt("춥고 싶어서", "희망을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "금지·가능·희망 표현과 혼동하기 쉽습니다.", "[추측원인 마인드맵] 추운 날씨(추정 원인) → 사람 적음(결과). 인과관계를 색으로 연결하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n급하게 __________ 배가 아파요.", List.of(
                                opt("먹어서 그런지", "정답: 급하게 먹은 것이 원인일 것이라는 추측을 나타내는 '-아서 그런지'가 맞습니다."),
                                opt("먹으면 안 돼서", "금지를 나타내는 표현으로 추측과는 다릅니다."),
                                opt("먹을 수 있어서", "가능을 나타내는 표현으로 추측 원인과는 다릅니다."),
                                opt("먹고 싶어서", "희망을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "금지·가능·희망 표현과 혼동하기 쉽습니다.", "[추측원인 마인드맵] 급하게 먹음(추정 원인) → 배 아픔(결과). 인과관계를 확인하세요."),
                        q("다음 밑줄 친 '-아서 그런지'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"오랜만에 만나서 그런지 어색해요.\"", List.of(
                                opt("확실하지 않지만 그것이 원인인 것 같다고 추측함", "정답: '-아서 그런지'는 불확실한 원인 추측을 나타냅니다."),
                                opt("그것이 원인임을 확실하게 단정함", "확실한 단정이 아니라 불확실한 추측을 나타냅니다."),
                                opt("그것과 전혀 상관없다고 부정함", "부정이 아니라 원인 추측을 나타냅니다."),
                                opt("앞으로 그런 일이 일어날 것이라고 예고함", "예고가 아니라 현재 상황의 원인 추측을 나타냅니다.")
                        ), 0, "확실한 단정·부정·예고로 오해하기 쉽습니다.", "[추측원인 마인드맵] 오랜만의 만남(추정 원인) → 어색함(결과). 불확실한 추측 표현입니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("날씨가 추워서 그런지 사람이 적어요.", "정답: 형용사 '춥다'에 '-어서 그런지'가 올바르게 결합했습니다."),
                                opt("날씨가 춥어서 그런지 사람이 적어요.", "'춥다'는 'ㅂ' 불규칙 형용사로 '추워서'가 되어야 하는데 '춥어서'로 잘못 활용됐습니다."),
                                opt("날씨가 추워서 그럴지 사람이 적어요.", "'그런지'가 '그럴지'로 잘못 변형되었습니다."),
                                opt("날씨가 추워 그런지 사람이 적어요.", "'-서'가 빠져 어색한 형태가 되었습니다.")
                        ), 0, "'ㅂ' 불규칙 활용, 어미 변형, 어미 누락에 주의해야 합니다.", "[추측원인 마인드맵] 춥다('ㅂ' 불규칙) → 추워서 그런지. 불규칙 활용에 주의하세요."),
                        q("가: 아이가 오늘따라 왜 이렇게 조용해요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("피곤해서 그런지 일찍 잠들었어요.", "정답: 피곤함이 원인일 것이라는 추측을 나타내는 자연스러운 응답입니다."),
                                opt("피곤하면 안 돼서 일찍 잠들었어요.", "금지 표현으로는 이 문맥과 어울리지 않습니다."),
                                opt("피곤할 수 있어서 일찍 잠들었어요.", "가능 표현으로는 원인 추측을 나타내기 어색합니다."),
                                opt("피곤하고 싶어서 일찍 잠들었어요.", "희망 표현으로는 문맥과 전혀 맞지 않습니다.")
                        ), 0, "금지·가능·희망 표현과 혼동하기 쉽습니다.", "[추측원인 마인드맵] 피곤함(추정 원인) → 일찍 잠듦(결과). 인과관계를 색칠하세요."))
        );

        List<PassageSeed> lv12w5_2nd3to4 = List.of(
                grammarUnit("-을까 하다 (막연한 의향을 나타냄)",
                        "[문법 포인트] -을까 하다\n의미: 아직 확실히 정하지는 않았지만 어떤 행동을 할까 생각하고 있음을 나타내는 표현입니다.\n형태: 동사 어간 + (으)ㄹ까 하다\n예문1: 주말에 영화를 볼까 해요.\n예문2: 이번 방학에 여행을 갈까 해요.\n예문3: 저녁에 운동을 할까 해요.",
                        q("가: 주말에 뭐 할 거예요?\n나: 아직 확실하지 않은데 영화를 __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("볼까 해요", "정답: 아직 확실하지 않은 막연한 의향을 나타낼 때는 '-을까 하다'가 맞습니다."),
                                opt("봐야 해요", "🔴 빨강 - 의무를 나타내는 표현으로 막연한 의향과는 다릅니다."),
                                opt("본 적이 있어요", "🔴 빨강 - 과거 경험을 나타내는 표현으로 미래 의향과는 다릅니다."),
                                opt("보면 안 돼요", "🔴 빨강 - 금지를 나타내는 표현으로 의향과는 다릅니다.")
                        ), 0, "의무·경험·금지 표현과 혼동하기 쉽습니다.", "[막연의향 마인드맵] 아직 미확정 → 영화 볼까 생각 중. '아직 확실하지 않다'는 표현을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n이번 방학에 여행을 __________.", List.of(
                                opt("갈까 해요", "정답: 막연한 의향을 나타내는 '-을까 하다'가 맞습니다."),
                                opt("가야 해요", "의무를 나타내는 표현으로 막연한 의향과는 다릅니다."),
                                opt("간 적이 있어요", "과거 경험을 나타내는 표현으로 미래 의향과는 다릅니다."),
                                opt("가면 안 돼요", "금지를 나타내는 표현으로 의향과는 다릅니다.")
                        ), 0, "의무·경험·금지 표현과 혼동하기 쉽습니다.", "[막연의향 마인드맵] 방학 → 여행 갈까 생각 중. 확정되지 않은 계획을 나타냅니다."),
                        q("다음 밑줄 친 '-을까 하다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"저녁에 운동을 할까 해요.\"", List.of(
                                opt("아직 확실히 정하지는 않았지만 어떤 행동을 할까 생각함", "정답: '-을까 하다'는 막연하고 유동적인 의향을 나타냅니다."),
                                opt("이미 확실하게 결정했음", "확정이 아니라 막연한 의향을 나타냅니다."),
                                opt("그 일을 절대 하지 않겠다고 다짐함", "거부가 아니라 막연한 긍정적 의향을 나타냅니다."),
                                opt("과거에 그 일을 여러 번 했음", "과거 반복이 아니라 미래의 막연한 계획을 나타냅니다.")
                        ), 0, "확정·거부·과거 반복으로 오해하기 쉽습니다.", "[막연의향 마인드맵] 저녁 → 운동할까 생각 중. 확정되지 않은 유동적 계획입니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("주말에 영화를 볼까 해요.", "정답: 동사 '보다'에 '-ㄹ까 하다'가 올바르게 결합했습니다."),
                                opt("주말에 영화를 보을까 해요.", "'보다'는 모음으로 끝나므로 '-ㄹ까 하다'가 결합해야 하는데 '으'가 잘못 삽입됐습니다."),
                                opt("주말에 영화를 봄까 해요.", "'-ㄹ'이 명사형 어미 '-ㅁ'으로 잘못 변형되었습니다."),
                                opt("주말에 영화를 보까 해요.", "필수적인 관형형 어미 '-ㄹ'이 빠져 어색한 형태가 되었습니다.")
                        ), 0, "받침 유무에 따른 결합, 어미 누락에 주의해야 합니다.", "[막연의향 마인드맵] 보다(모음 끝) → 볼까 해요. 받침 없는 동사는 'ㄹ'과 결합합니다."),
                        q("가: 이번 생일에 뭐 할 거예요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("친구들을 초대해서 파티를 할까 해요.", "정답: 아직 확정되지 않은 막연한 계획을 나타내는 자연스러운 응답입니다."),
                                opt("친구들을 초대해서 파티를 해야 해요.", "의무 표현으로는 계획을 묻는 질문의 답으로 어색합니다."),
                                opt("친구들을 초대해서 파티를 한 적이 있어요.", "과거 경험 표현으로는 미래 계획을 묻는 질문과 맞지 않습니다."),
                                opt("친구들을 초대해서 파티를 하면 안 돼요.", "금지 표현으로는 질문과 전혀 맞지 않습니다.")
                        ), 0, "의무·경험·금지 표현과 혼동하기 쉽습니다.", "[막연의향 마인드맵] 생일 → 파티 할까 생각 중. 유동적 계획 표현입니다.")),
                grammarUnit("-을 뻔하다 (하마터면 그럴 뻔했음을 나타냄)",
                        "[문법 포인트] -을 뻔하다\n의미: 실제로 일어나지는 않았지만 그런 일이 일어날 것 같은 상황이었음을 나타내는 표현입니다.\n형태: 동사 어간 + (으)ㄹ 뻔하다\n예문1: 길이 미끄러워서 넘어질 뻔했어요.\n예문2: 하마터면 약속을 잊을 뻔했어요.\n예문3: 버스를 놓칠 뻔했지만 겨우 탔어요.",
                        q("가: 오늘 왜 이렇게 늦었어요?\n나: 길이 미끄러워서 __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("넘어질 뻔했어요", "정답: 실제로 넘어지지는 않았지만 그럴 뻔했음을 나타낼 때는 '-을 뻔하다'가 맞습니다."),
                                opt("넘어진 적이 있어요", "🔴 빨강 - 실제로 넘어진 과거 경험을 나타내는 표현으로 다릅니다."),
                                opt("넘어지려고 해요", "🔴 빨강 - 미래 의도를 나타내는 표현으로 이 문맥과 맞지 않습니다."),
                                opt("넘어지면 안 돼요", "🔴 빨강 - 금지를 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "실제 경험·의도·금지 표현과 혼동하기 쉽습니다.", "[하마터면 마인드맵] 미끄러움(상황) → 넘어질 뻔(미실현). 실제로 일어나지 않았음을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n하마터면 약속을 __________.", List.of(
                                opt("잊을 뻔했어요", "정답: 실제로 잊지는 않았지만 그럴 뻔했음을 나타내는 '-을 뻔하다'가 맞습니다."),
                                opt("잊은 적이 있어요", "실제로 잊은 과거 경험을 나타내는 표현으로 다릅니다."),
                                opt("잊으려고 해요", "미래 의도를 나타내는 표현으로 이 문맥과 맞지 않습니다."),
                                opt("잊으면 안 돼요", "금지를 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "실제 경험·의도·금지 표현과 혼동하기 쉽습니다.", "[하마터면 마인드맵] '하마터면'이라는 부사와 함께 자주 쓰입니다."),
                        q("다음 밑줄 친 '-을 뻔하다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"버스를 놓칠 뻔했지만 겨우 탔어요.\"", List.of(
                                opt("실제로 일어나지는 않았지만 그런 일이 일어날 것 같은 상황이었음", "정답: '-을 뻔하다'는 위기를 겨우 피한 상황을 나타냅니다."),
                                opt("실제로 그 일이 일어났음", "실제 발생이 아니라 아슬아슬하게 피한 상황을 나타냅니다."),
                                opt("그 일이 절대 일어나지 않을 것임", "단순 부정이 아니라 위험했던 상황을 나타냅니다."),
                                opt("그 일이 자주 반복해서 일어남", "반복이 아니라 한 번의 아슬아슬한 상황을 나타냅니다.")
                        ), 0, "실제 발생·단순 부정·반복으로 오해하기 쉽습니다.", "[하마터면 마인드맵] 버스 놓침(미실현) → 겨우 탐(결과). 위기를 피한 상황입니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("길이 미끄러워서 넘어질 뻔했어요.", "정답: 동사 '넘어지다'에 '-ㄹ 뻔하다'가 올바르게 결합했습니다."),
                                opt("길이 미끄러워서 넘어지을 뻔했어요.", "'넘어지다'는 모음으로 끝나므로 '-ㄹ 뻔하다'가 결합해야 하는데 '으'가 잘못 삽입됐습니다."),
                                opt("길이 미끄러워서 넘어짐 뻔했어요.", "'-ㄹ'이 명사형 어미 '-ㅁ'으로 잘못 변형되었습니다."),
                                opt("길이 미끄러워서 넘어지 뻔했어요.", "필수적인 관형형 어미 '-ㄹ'이 빠져 어색한 형태가 되었습니다.")
                        ), 0, "받침 유무에 따른 결합, 어미 누락에 주의해야 합니다.", "[하마터면 마인드맵] 넘어지다(모음 끝) → 넘어질 뻔했어요. 받침 없는 동사는 'ㄹ'과 결합합니다."),
                        q("가: 아까 무슨 일 있었어요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("계단에서 발을 헛디뎌서 크게 다칠 뻔했어요.", "정답: 실제로 다치지는 않았지만 그럴 뻔했음을 나타내는 자연스러운 응답입니다."),
                                opt("계단에서 발을 헛디뎌서 크게 다친 적이 있어요.", "실제 발생한 과거 경험 표현으로는 뉘앙스가 다릅니다."),
                                opt("계단에서 발을 헛디뎌서 크게 다치려고 해요.", "미래 의도 표현으로는 문맥과 전혀 맞지 않습니다."),
                                opt("계단에서 발을 헛디뎌서 크게 다치면 안 돼요.", "금지 표현으로는 문맥과 전혀 맞지 않습니다.")
                        ), 0, "실제 경험·의도·금지 표현과 혼동하기 쉽습니다.", "[하마터면 마인드맵] 발 헛디딤(상황) → 다칠 뻔(미실현). 위기를 피한 상황을 나타냅니다."))
        );

        List<PassageSeed> lv12w5_2nd5to6 = List.of(
                grammarUnit("-은 대로 (앞의 내용과 똑같이)",
                        "[문법 포인트] -은 대로\n의미: 앞에서 말한 상태나 방식과 똑같이 함을 나타내는 표현입니다.\n형태: 동사 어간 + (으)ㄴ 대로\n예문1: 제가 말한 대로 해 주세요.\n예문2: 선생님이 가르쳐 준 대로 따라 했어요.\n예문3: 계획한 대로 일이 잘 진행됐어요.",
                        q("가: 이 일을 어떻게 해야 해요?\n나: 제가 __________ 해 주세요.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("말한 대로", "정답: 말한 내용과 똑같이 하라는 것을 나타낼 때는 '-은 대로'가 맞습니다."),
                                opt("말하면서", "🔵 파랑 - 동시 동작을 나타내는 표현으로 '똑같이'와는 다릅니다."),
                                opt("말하기 전에", "🔵 파랑 - 순서를 나타내는 표현으로 '똑같이'와는 다릅니다."),
                                opt("말할 텐데", "🔵 파랑 - 추측을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "동시 동작·순서·추측 표현과 혼동하기 쉽습니다.", "[동일방식 마인드맵] 말한 내용 = 실행 방식. 앞뒤 내용이 똑같음을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n선생님이 가르쳐 준 __________ 따라 했어요.", List.of(
                                opt("대로", "정답: 가르쳐 준 방식과 똑같이 했음을 나타내는 '-은 대로'가 맞습니다."),
                                opt("동안", "기간을 나타내는 표현으로 '똑같이'와는 다릅니다."),
                                opt("김에", "겸사겸사를 나타내는 표현으로 '똑같이'와는 다릅니다."),
                                opt("탓에", "부정적 원인을 나타내는 표현으로 이 문맥과 맞지 않습니다.")
                        ), 0, "기간·겸사겸사·원인 표현과 혼동하기 쉽습니다.", "[동일방식 마인드맵] 가르쳐 준 방식 = 따라 한 방식. 방식이 일치함을 나타냅니다."),
                        q("다음 밑줄 친 '-은 대로'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"계획한 대로 일이 잘 진행됐어요.\"", List.of(
                                opt("앞에서 말한 상태나 방식과 똑같이 함", "정답: '-은 대로'는 동일한 방식을 나타냅니다."),
                                opt("앞의 내용과 전혀 다르게 함", "다르게가 아니라 똑같이 함을 나타냅니다."),
                                opt("앞으로 계획을 세울 것임", "미래 계획 수립이 아니라 이미 정해진 방식대로 진행됨을 나타냅니다."),
                                opt("계획이 취소됨", "취소가 아니라 계획대로 진행됨을 나타냅니다.")
                        ), 0, "전혀 다르게 함·미래 계획 수립·취소로 오해하기 쉽습니다.", "[동일방식 마인드맵] 계획 = 실제 진행. 두 내용이 일치함을 나타냅니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("제가 말한 대로 해 주세요.", "정답: 동사 '말하다'에 '-ㄴ 대로'가 올바르게 결합했습니다."),
                                opt("제가 말하는 대로 해 주세요.", "과거 완료된 내용을 가리킬 때는 '-는'이 아니라 '-ㄴ'을 써야 합니다(문맥상 이미 말한 내용)."),
                                opt("제가 말함 대로 해 주세요.", "'-ㄴ'이 명사형 어미 '-ㅁ'으로 잘못 변형되었습니다."),
                                opt("제가 말하 대로 해 주세요.", "필수적인 관형형 어미가 빠져 어색한 형태가 되었습니다.")
                        ), 0, "시제 관형형 어미 선택, 어미 변형에 주의해야 합니다.", "[동일방식 마인드맵] 이미 말한 내용을 가리킬 때는 '-ㄴ 대로'를 씁니다."),
                        q("가: 이 가구는 어떻게 조립해요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("설명서에 나온 대로 순서대로 하면 돼요.", "정답: 설명서 내용과 똑같이 하라는 자연스러운 응답입니다."),
                                opt("설명서에 나오면서 순서대로 하면 돼요.", "동시 동작 표현으로는 이 문맥과 어울리지 않습니다."),
                                opt("설명서에 나오기 전에 순서대로 하면 돼요.", "순서 표현으로는 문맥과 맞지 않습니다."),
                                opt("설명서에 나올 텐데 순서대로 하면 돼요.", "추측 표현으로는 문맥과 맞지 않습니다.")
                        ), 0, "동시 동작·순서·추측 표현과 혼동하기 쉽습니다.", "[동일방식 마인드맵] 설명서 내용 = 조립 방식. 동일함을 나타냅니다.")),
                grammarUnit("-은 편이다 (대체로 어떤 경향이 있음을 나타냄)",
                        "[문법 포인트] -은 편이다\n의미: 어떤 것이 완전히 그렇다고 단정하기는 어렵지만 대체로 그런 경향이 있음을 나타내는 표현입니다.\n형태: 동사/형용사 어간 + (으)ㄴ 편이다\n예문1: 저는 아침에 일찍 일어나는 편이에요.\n예문2: 이 식당은 값이 싼 편이에요.\n예문3: 그는 말이 많은 편이에요.",
                        q("가: 아침에 몇 시에 일어나요?\n나: 저는 일찍 __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("일어나는 편이에요", "정답: 대체로 그런 경향이 있음을 나타낼 때는 '-는 편이다'가 맞습니다."),
                                opt("일어나야 해요", "🟢 초록 - 의무를 나타내는 표현으로 경향과는 다릅니다."),
                                opt("일어난 적이 있어요", "🟢 초록 - 과거 경험을 나타내는 표현으로 경향과는 다릅니다."),
                                opt("일어나면 안 돼요", "🟢 초록 - 금지를 나타내는 표현으로 경향과는 다릅니다.")
                        ), 0, "의무·경험·금지 표현과 혼동하기 쉽습니다.", "[경향 마인드맵] 대체로 → 일찍 일어남(경향). 단정이 아닌 경향임을 확인하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n이 식당은 값이 __________.", List.of(
                                opt("싼 편이에요", "정답: 대체로 싸다는 경향을 나타내는 '-은 편이다'가 맞습니다."),
                                opt("싸야 해요", "의무를 나타내는 표현으로 경향과는 다릅니다."),
                                opt("싼 적이 있어요", "과거 경험을 나타내는 표현으로 경향과는 다릅니다."),
                                opt("싸면 안 돼요", "금지를 나타내는 표현으로 경향과는 다릅니다.")
                        ), 0, "의무·경험·금지 표현과 혼동하기 쉽습니다.", "[경향 마인드맵] 대체로 → 값이 쌈(경향). 단정적 표현이 아닙니다."),
                        q("다음 밑줄 친 '-은 편이다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"그는 말이 많은 편이에요.\"", List.of(
                                opt("완전히 그렇다고 단정하기는 어렵지만 대체로 그런 경향이 있음", "정답: '-은 편이다'는 대략적인 경향을 나타냅니다."),
                                opt("항상 예외 없이 그러함", "예외 없는 단정이 아니라 대체적인 경향을 나타냅니다."),
                                opt("전혀 그렇지 않음", "부정이 아니라 그런 경향이 있음을 나타냅니다."),
                                opt("가끔 한 번씩만 그러함", "드문 빈도가 아니라 비교적 자주 그러한 경향을 나타냅니다.")
                        ), 0, "예외 없는 단정·부정·드문 빈도로 오해하기 쉽습니다.", "[경향 마인드맵] 대체로 → 말이 많음(경향). 완전한 단정이 아님을 기억하세요."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("저는 아침에 일찍 일어나는 편이에요.", "정답: 동사 '일어나다'에 '-는 편이다'가 올바르게 결합했습니다."),
                                opt("저는 아침에 일찍 일어난 편이에요.", "동사 현재 습관을 나타낼 때는 '-는 편이다'를 써야 하는데 '-ㄴ 편이다'로 잘못 썼습니다."),
                                opt("저는 아침에 일찍 일어나기 편이에요.", "'-는'이 명사형 어미 '-기'로 잘못 바뀌었습니다."),
                                opt("저는 아침에 일찍 일어나 편이에요.", "필수적인 관형형 어미 '-는'이 빠져 어색한 형태가 되었습니다.")
                        ), 0, "동사/형용사에 따른 관형형 어미 선택에 주의해야 합니다.", "[경향 마인드맵] 동사의 현재 습관은 '-는 편이다', 형용사는 '-은 편이다'를 씁니다."),
                        q("가: 매운 음식 잘 드세요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("네, 저는 매운 음식을 잘 먹는 편이에요.", "정답: 대체로 매운 음식을 잘 먹는 경향을 나타내는 자연스러운 응답입니다."),
                                opt("네, 저는 매운 음식을 잘 먹어야 해요.", "의무 표현으로는 질문의 답으로 어색합니다."),
                                opt("네, 저는 매운 음식을 잘 먹은 적이 있어요.", "과거 경험 표현으로는 현재의 경향을 묻는 질문과 맞지 않습니다."),
                                opt("네, 저는 매운 음식을 잘 먹으면 안 돼요.", "금지 표현으로는 질문과 전혀 맞지 않습니다.")
                        ), 0, "의무·경험·금지 표현과 혼동하기 쉽습니다.", "[경향 마인드맵] 대체로 → 매운 음식 잘 먹음(경향). 습관적 경향을 나타냅니다."))
        );

        List<PassageSeed> lv12w5_2nd7to8 = List.of(
                grammarUnit("-았으면 좋겠다 (바라는 마음을 나타냄)",
                        "[문법 포인트] -았으면 좋겠다\n의미: 실현되기를 간절히 바라는 마음을 나타내는 표현입니다.\n형태: 동사/형용사 어간 + 았으면/었으면 좋겠다\n예문1: 내일 날씨가 좋았으면 좋겠어요.\n예문2: 빨리 방학이 왔으면 좋겠어요.\n예문3: 시험에 합격했으면 좋겠어요.",
                        q("가: 내일 소풍 가는데 걱정이에요.\n나: 날씨가 __________.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("좋았으면 좋겠어요", "정답: 날씨가 좋기를 바라는 마음을 나타낼 때는 '-았으면 좋겠다'가 맞습니다."),
                                opt("좋아야 해요", "🔵 파랑 - 의무를 나타내는 표현으로 바람과는 다릅니다."),
                                opt("좋을 수 있어요", "🔵 파랑 - 가능을 나타내는 표현으로 바람과는 다릅니다."),
                                opt("좋으면 안 돼요", "🔵 파랑 - 금지를 나타내는 표현으로 이 문맥과 반대됩니다.")
                        ), 0, "의무·가능·금지 표현과 혼동하기 쉽습니다.", "[바람 마인드맵] 걱정 → 좋은 날씨를 바람. 간절한 소망을 나타냅니다."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n빨리 방학이 __________.", List.of(
                                opt("왔으면 좋겠어요", "정답: 방학이 오기를 바라는 마음을 나타내는 '-았으면 좋겠다'가 맞습니다."),
                                opt("와야 해요", "의무를 나타내는 표현으로 바람과는 다릅니다."),
                                opt("올 수 있어요", "가능을 나타내는 표현으로 바람과는 다릅니다."),
                                opt("오면 안 돼요", "금지를 나타내는 표현으로 이 문맥과 반대됩니다.")
                        ), 0, "의무·가능·금지 표현과 혼동하기 쉽습니다.", "[바람 마인드맵] 방학 → 빨리 오기를 바람. 소망 표현임을 확인하세요."),
                        q("다음 밑줄 친 '-았으면 좋겠다'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"시험에 합격했으면 좋겠어요.\"", List.of(
                                opt("실현되기를 간절히 바라는 마음", "정답: '-았으면 좋겠다'는 간절한 바람을 나타냅니다."),
                                opt("이미 그 일이 실현됐다는 확신", "확신이 아니라 아직 이루어지지 않은 것에 대한 바람을 나타냅니다."),
                                opt("그 일이 절대 일어나지 않기를 바람", "부정적 바람이 아니라 긍정적 바람을 나타냅니다."),
                                opt("그 일에 대해 무관심함", "무관심이 아니라 간절한 관심과 바람을 나타냅니다.")
                        ), 0, "확신·부정적 바람·무관심으로 오해하기 쉽습니다.", "[바람 마인드맵] 시험 → 합격을 바람. 아직 이루어지지 않은 소망입니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("내일 날씨가 좋았으면 좋겠어요.", "정답: 형용사 '좋다'에 '-았으면 좋겠다'가 올바르게 결합했습니다."),
                                opt("내일 날씨가 좋면 좋겠어요.", "'-았으면'에서 '았'이 빠져 어색한 형태가 되었습니다."),
                                opt("내일 날씨가 좋었으면 좋겠어요.", "모음조화에 따라 '좋다'는 '좋았으면'이 되어야 하는데 '좋었으면'으로 잘못 활용됐습니다."),
                                opt("내일 날씨가 좋았으려면 좋겠어요.", "'-으면'이 '-으려면'으로 잘못 변형되었습니다.")
                        ), 0, "어미 누락, 모음조화 오류, 어미 변형에 주의해야 합니다.", "[바람 마인드맵] 좋다(양성모음) → 좋았으면. 모음조화 규칙에 주의하세요."),
                        q("가: 새해 소원이 뭐예요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("가족 모두가 건강했으면 좋겠어요.", "정답: 가족의 건강을 바라는 간절한 소망을 나타내는 자연스러운 응답입니다."),
                                opt("가족 모두가 건강해야 해요.", "의무 표현으로는 소원을 묻는 질문의 답으로 다소 어색합니다."),
                                opt("가족 모두가 건강할 수 있어요.", "가능 표현으로는 소원의 간절함이 드러나지 않습니다."),
                                opt("가족 모두가 건강하면 안 돼요.", "금지 표현으로는 문맥과 전혀 맞지 않습니다.")
                        ), 0, "의무·가능·금지 표현과 혼동하기 쉽습니다.", "[바람 마인드맵] 새해 소원 → 가족 건강을 바람. 간절한 소망 표현입니다.")),
                grammarUnit("-고 나니 (어떤 일을 끝낸 후에 새롭게 깨달음을 나타냄)",
                        "[문법 포인트] -고 나니\n의미: 어떤 일을 다 끝내고 난 후에 어떤 사실을 새롭게 깨닫거나 느꼈음을 나타내는 표현입니다.\n형태: 동사 어간 + 고 나니\n예문1: 운동을 하고 나니 기분이 좋아졌어요.\n예문2: 이야기를 다 듣고 나니 이해가 됐어요.\n예문3: 청소를 하고 나니 방이 훨씬 넓어 보여요.",
                        q("가: 운동한 후에 기분이 어때요?\n나: 운동을 __________ 기분이 좋아졌어요.\n빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("하고 나니", "정답: 운동을 끝낸 후 새롭게 느낀 것을 나타낼 때는 '-고 나니'가 맞습니다."),
                                opt("하는 동안", "동시 진행을 나타내는 표현으로 완료 후 깨달음과는 다릅니다."),
                                opt("하기 전에", "순서(이전)를 나타내는 표현으로 완료 후 깨달음과는 다릅니다."),
                                opt("하려고", "의도를 나타내는 표현으로 완료 후 깨달음과는 다릅니다.")
                        ), 0, "동시 진행·순서·의도 표현과 혼동하기 쉽습니다.", "[완료깨달음 마인드맵] 운동 완료 → 기분 좋아짐(새로운 깨달음). 순서를 화살표로 표시하세요."),
                        q("다음 빈칸에 알맞은 것을 고르십시오.\n이야기를 다 __________ 이해가 됐어요.", List.of(
                                opt("듣고 나니", "정답: 이야기를 다 들은 후 새롭게 이해하게 됐음을 나타내는 '-고 나니'가 맞습니다."),
                                opt("듣는 동안", "동시 진행을 나타내는 표현으로 완료 후 깨달음과는 다릅니다."),
                                opt("듣기 전에", "순서(이전)를 나타내는 표현으로 완료 후 깨달음과는 다릅니다."),
                                opt("들으려고", "의도를 나타내는 표현으로 완료 후 깨달음과는 다릅니다.")
                        ), 0, "동시 진행·순서·의도 표현과 혼동하기 쉽습니다.", "[완료깨달음 마인드맵] 다 들음(완료) → 이해됨(깨달음). 완료 후 결과를 나타냅니다."),
                        q("다음 밑줄 친 '-고 나니'가 나타내는 의미로 알맞은 것을 고르십시오.\n\"청소를 하고 나니 방이 훨씬 넓어 보여요.\"", List.of(
                                opt("어떤 일을 다 끝내고 난 후에 새롭게 깨닫거나 느낌", "정답: '-고 나니'는 완료 후의 새로운 인식을 나타냅니다."),
                                opt("어떤 일을 하는 도중에 깨달음", "도중이 아니라 완료 후의 깨달음을 나타냅니다."),
                                opt("어떤 일을 하기 전에 미리 예상함", "사전 예상이 아니라 완료 후의 실제 느낌을 나타냅니다."),
                                opt("어떤 일을 앞으로 할 계획임", "미래 계획이 아니라 이미 끝낸 일에 대한 느낌을 나타냅니다.")
                        ), 0, "진행 중 깨달음·사전 예상·미래 계획으로 오해하기 쉽습니다.", "[완료깨달음 마인드맵] 청소 완료 → 넓어 보임(깨달음). 완료 후의 새로운 인식입니다."),
                        q("다음 중 문법적으로 올바른 문장을 고르십시오.", List.of(
                                opt("운동을 하고 나니 기분이 좋아졌어요.", "정답: 동사 '하다'에 '-고 나니'가 올바르게 결합했습니다."),
                                opt("운동을 하고 나면 기분이 좋아졌어요.", "'나니'가 조건을 나타내는 '나면'으로 잘못 바뀌어 과거 사실 서술과 맞지 않습니다."),
                                opt("운동을 하고 났으니 기분이 좋아졌어요.", "'나니'가 '났으니'로 불필요하게 변형되어 어색합니다."),
                                opt("운동을 하 나니 기분이 좋아졌었어요.", "'-고'가 빠지고 뒷문장도 어색하게 변형되었습니다.")
                        ), 0, "어미 변형, 불필요한 변형, 어미 누락에 주의해야 합니다.", "[완료깨달음 마인드맵] 동사 어간 + '-고 나니'가 올바른 결합 형태입니다."),
                        q("가: 새 회사에서 일해 보니까 어때요?\n나: __________.\n대화의 빈칸에 들어갈 말로 가장 자연스러운 것을 고르십시오.", List.of(
                                opt("막상 일을 시작하고 나니 생각보다 재미있어요.", "정답: 일을 시작한 후 새롭게 느낀 것을 나타내는 자연스러운 응답입니다."),
                                opt("막상 일을 시작하는 동안 생각보다 재미있어요.", "동시 진행 표현으로는 '해 보니까'라는 질문의 시제와 어색합니다."),
                                opt("막상 일을 시작하기 전에 생각보다 재미있어요.", "순서(이전) 표현으로는 이미 겪은 일에 대한 질문과 맞지 않습니다."),
                                opt("막상 일을 시작하려고 생각보다 재미있어요.", "의도 표현으로는 이미 겪은 느낌을 묻는 질문과 맞지 않습니다.")
                        ), 0, "동시 진행·순서·의도 표현과 혼동하기 쉽습니다.", "[완료깨달음 마인드맵] 일 시작(완료) → 재미있음(깨달음). 완료 후 느낌을 나타냅니다."))
        );

        return new WeekSeed("1~2급 컬러맵 문법 심화 기초",
                "기초 문법·어휘를 색깔 코딩과 마인드맵으로 심화 학습한다.",
                WEEK5_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 문법 8개: -으면 안 되다(금지)/-을 수 있다(가능)/-고 싶어 하다(3인칭 희망)/-으려고 하다(의도)/-은 적이 있다(경험)/-는 동안에(동시 진행)/-으면서(동시 동작)/-을 텐데(추측). 학습 후 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(lv12w5_1st1to2, lv12w5_1st3to4, lv12w5_1st5to6, lv12w5_1st7to8)),
                        day("2차(40문항) - 문법 8개: -는데(배경 설명)/-아서 그런지(추측 원인)/-을까 하다(막연한 의향)/-을 뻔하다(하마터면)/-은 대로(그대로)/-은 편이다(대체로)/-았으면 좋겠다(바람)/-고 나니(완료 후 깨달음). 학습 후 오답 노트 템플릿에 취약 유형을 기록하세요.",
                                merge(lv12w5_2nd1to2, lv12w5_2nd3to4, lv12w5_2nd5to6, lv12w5_2nd7to8))
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
