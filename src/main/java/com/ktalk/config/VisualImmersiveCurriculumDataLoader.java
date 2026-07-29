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
                                merge(listening4th1to10, listening4th11to20, reading4th21to30, reading4th31to40))
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
