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
 * 시각적 몰입형(VISUAL_IMMERSIVE) 유형의 3~4급 "TOPIK 컬러맵 완전분석" 커리큘럼을 심는다.
 * VisualImmersiveCurriculumDataLoader(1~2급)와 동일한 골격(레코드/헬퍼, 8주 + 모의고사 2회 + Final 1회,
 * 총 2,450문항)을 쓰되, trapNote/strategyTip을 색깔 코딩·마인드맵·도식화 언어로 재구성한다 —
 * LearnerType.VISUAL_IMMERSIVE의 studyTip("도식화·마인드맵 정리와 색상별 어휘 분류")을 모든 문항에
 * 반영한다. 1~2급 과정과는 완전히 분리된 별도의 8주 과정으로, 같은 learner_type이라도
 * targetLevelFrom(LEVEL_3)으로 구분되는 별도 Curriculum 레코드를 갖는다.
 */
@Component
@RequiredArgsConstructor
@Order(14)
public class VisualImmersiveLevel34CurriculumDataLoader implements CommandLineRunner {

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
        curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.VISUAL_IMMERSIVE, TopikLevel.LEVEL_3)
                .ifPresent(this::deleteExisting);

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.VISUAL_IMMERSIVE);
        curriculum.setTitle("TOPIK 컬러맵 완전분석 노트");
        curriculum.setTargetLevelLabel("3~4급 전 과정");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_3);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_4);
        curriculum.setUsageNote(
                "모든 문제에 색깔 태그(🔴🟢🔵🟣)로 함정 포인트를 구분하고, 문법·어휘는 마인드맵 구조로 "
                        + "설명합니다. 색깔 펜 3~4자루를 준비해 오답 노트를 도식화하며 시각적으로 기억을 강화하세요.");

        List<WeekSeed> weeks = List.of(week1());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("🎨 TOPIK 커리큘럼(시각적 몰입형, 3~4급) WEEK1 1차(40문항) 신설 - 계속 진행 중!");
    }

    /** 재시딩 전 기존 커리큘럼을 지운다. day는 부모의 cascade 대상이 아니라 먼저 지워야 한다. */
    private void deleteExisting(Curriculum existing) {
        List<CurriculumDay> days = curriculumDayRepository.findByCurriculumId(existing.getId());
        curriculumDayRepository.deleteAll(days);
        userCurriculumProgressRepository.deleteByCurriculumId(existing.getId());
        curriculumRepository.delete(existing);
        curriculumRepository.flush();
    }

    // ===================== WEEK 1: 3~4급 컬러맵 기초 다지기 =====================

    private static final String WEEK1_ANSWER_NOTE_TEMPLATE = """
            [🎨 오답 노트 템플릿 - WEEK1용]
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
                onePassage(PassageCategory.LISTENING, "의견 제시",
                        "여자: 요즘 재택근무가 늘고 있는데 어떻게 생각하세요?\n남자: 저는 찬성이에요. 출퇴근 시간도 아끼고 집중도 더 잘되는 것 같아요.",
                        q("남자의 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("재택근무에 찬성한다.", "정답: '찬성이에요'라고 명확히 밝히고 이유(시간 절약, 집중력)도 제시했습니다."),
                                opt("재택근무에 반대한다.", "찬성이라고 언급되어 반대됩니다."),
                                opt("재택근무에 관심이 없다.", "명확한 의견을 밝혔으므로 무관심이 아닙니다."),
                                opt("출퇴근 시간이 늘었다고 생각한다.", "출퇴근 시간을 아낀다고 언급되어 반대됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 찬반 의견을 반대로 착각하기 쉽습니다.", "[의견파악 마인드맵] 찬성/반대(입장) → 이유(근거). 첫 문장에서 입장을 먼저 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "비교/선택",
                        "남자: 이 두 회사 중에 어디에 지원할지 고민돼요.\n여자: 저라면 연봉보다는 성장 가능성을 보고 결정할 것 같아요.",
                        q("여자가 중요하게 생각하는 것으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("회사의 성장 가능성", "정답: '성장 가능성을 보고 결정하겠다'고 명확히 밝혔습니다."),
                                opt("높은 연봉", "연봉보다 성장 가능성을 본다고 언급되어 반대됩니다."),
                                opt("회사의 위치", "언급되지 않은 내용입니다."),
                                opt("근무 시간", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — '연봉보다'라는 비교 표현을 놓치면 반대로 답하기 쉽습니다.", "[비교선택 마인드맵] A보다 B(비교 기준) → B를 선택(결론). 비교 표현 뒤의 내용에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "뉴스/보도",
                        "다음은 뉴스입니다. 오늘 오전 서울 지역에 내린 폭우로 일부 도로가 침수되어 통제되고 있습니다. 시민들은 우회 도로를 이용하시기 바랍니다.",
                        q("이 뉴스의 내용과 같은 것을 고르십시오.", List.of(
                                opt("폭우로 인해 일부 도로가 통제되고 있습니다.", "정답: '일부 도로가 침수되어 통제되고 있다'고 명시되어 있습니다."),
                                opt("모든 도로가 정상적으로 운영되고 있습니다.", "일부 도로가 통제되고 있다고 언급되었습니다."),
                                opt("이 뉴스는 어제 있었던 일을 전합니다.", "오늘 오전이라고 언급되었습니다."),
                                opt("우회 도로도 통제되었습니다.", "우회 도로 이용을 권장한다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 통제된 도로와 우회 도로를 혼동하기 쉽습니다.", "[뉴스파악 마인드맵] 사건(폭우) → 결과(도로 통제) → 대응(우회 도로 이용). 순서대로 정리하세요.")),
                onePassage(PassageCategory.LISTENING, "직장 생활",
                        "여자: 김 대리님, 이번 프로젝트 마감이 촉박한데 인력을 좀 더 투입할 수 있을까요?\n남자: 검토해 보겠습니다. 다른 부서와 협의가 필요할 것 같아요.",
                        q("남자의 대답으로 알 수 있는 것으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("바로 결정하기 어려운 사안이다.", "정답: '검토해 보겠다', '협의가 필요하다'는 표현에서 즉답이 어려운 상황임을 알 수 있습니다."),
                                opt("이미 인력 투입을 확정했다.", "검토해 보겠다고 언급되어 아직 확정되지 않았습니다."),
                                opt("인력 투입을 거절했다.", "명확한 거절이 아니라 검토를 언급했습니다."),
                                opt("다른 부서와 이미 협의를 마쳤다.", "협의가 필요하다고 언급되어 아직 하지 않았습니다.")
                        ), 0, "🟢 의도 파악 실패 — 완곡한 보류 표현을 확정이나 거절로 착각하기 쉽습니다.", "[직장대화 마인드맵] 검토/협의 필요(완곡 표현) → 즉답 보류(실제 의미). 완곡한 표현의 속뜻을 파악하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용",
                        "남자: 이번 박람회는 몇 시부터 몇 시까지 해요?\n여자: 오전 10시부터 오후 6시까지인데, 마지막 날은 4시에 끝나요.\n남자: 아, 그럼 마지막 날은 일찍 가야겠네요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("박람회 마지막 날은 오후 4시에 끝납니다.", "정답: '마지막 날은 4시에 끝난다'고 명시되어 있습니다."),
                                opt("박람회는 매일 오후 6시에 끝납니다.", "마지막 날은 4시에 끝난다고 예외가 언급되었습니다."),
                                opt("박람회는 오전 9시에 시작합니다.", "오전 10시부터라고 언급되었습니다."),
                                opt("박람회 마지막 날도 6시까지 운영됩니다.", "마지막 날은 4시에 끝난다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 평소 운영 시간과 마지막 날의 예외 시간을 혼동하기 쉽습니다.", "[일치판단 마인드맵] 일반 규칙(10시~6시) vs 예외(마지막 날 4시). 예외 사항을 따로 표시하세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도",
                        "여자: 이번 제안서 정말 꼼꼼하게 준비하셨네요. 데이터 분석도 인상적이에요.\n남자: 감사합니다. 팀원들과 함께 열심히 준비했어요.",
                        q("여자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("제안서의 완성도를 높이 평가하고 있다.", "정답: '꼼꼼하게 준비했다', '인상적이다'라는 표현에서 긍정적 평가가 드러납니다."),
                                opt("제안서의 문제점을 지적하고 있다.", "칭찬하고 있으므로 반대됩니다."),
                                opt("제안서 작성을 요청하고 있다.", "이미 완성된 제안서를 평가하는 상황입니다."),
                                opt("데이터 분석 방법을 궁금해하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 칭찬 표현에서 화자의 긍정적 태도를 놓치기 쉽습니다.", "[태도파악 마인드맵] 꼼꼼하다+인상적이다(긍정 어휘) → 높은 평가(태도). 평가 어휘의 뉘앙스에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "문제 상황",
                        "남자: 어제 주문한 물건이 다른 색깔로 왔어요.\n여자: 죄송합니다. 확인해 보고 바로 교환해 드리겠습니다.",
                        q("두 사람의 대화 상황으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("잘못 배송된 물건에 대해 문의하고 있다.", "정답: 다른 색깔로 온 물건에 대한 문의와 교환 안내 상황입니다."),
                                opt("새로운 제품을 홍보하고 있다.", "언급되지 않은 내용입니다."),
                                opt("물건의 가격을 흥정하고 있다.", "언급되지 않은 내용입니다."),
                                opt("배송 일정을 예약하고 있다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟣 기타(부주의) — 상황의 핵심(오배송 문의)을 놓치고 세부 정보에만 집중하기 쉽습니다.", "[상황파악 마인드맵] 문제 제기(다른 색깔로 옴) → 해결 제안(교환). 문제와 해결을 함께 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "예측/추론",
                        "여자: 요즘 물가가 계속 오르고 있잖아요. 이러다가 다음 달에는 더 오를 것 같아요.\n남자: 맞아요. 미리 필요한 것들을 사 두는 게 나을 것 같아요.",
                        q("두 사람의 대화를 통해 추론할 수 있는 것으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("두 사람은 물가 상승을 우려하고 있다.", "정답: 물가 상승 전망과 대비책을 이야기하는 것에서 우려가 드러납니다."),
                                opt("물가가 이미 안정되었다.", "계속 오르고 있다고 언급되어 반대됩니다."),
                                opt("두 사람은 다음 달에 여행을 갈 계획이다.", "언급되지 않은 내용입니다."),
                                opt("남자는 물건을 사지 않으려고 한다.", "미리 사 두자고 언급되어 반대됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 전망에 대한 우려를 무관심으로 착각하기 쉽습니다.", "[추론 마인드맵] 물가 상승 전망(상황) → 미리 구매(대응) → 우려(속마음). 대화의 흐름에서 감정을 추론하세요.")),
                onePassage(PassageCategory.LISTENING, "공식 발표",
                        "안내 말씀드립니다. 다음 달부터 도서관 대출 기간이 2주에서 3주로 연장됩니다. 자세한 사항은 홈페이지를 참고해 주시기 바랍니다.",
                        q("이 발표의 내용과 같은 것을 고르십시오.", List.of(
                                opt("도서관 대출 기간이 늘어납니다.", "정답: '2주에서 3주로 연장된다'고 명시되어 있습니다."),
                                opt("대출 기간이 줄어듭니다.", "연장된다고 언급되어 반대됩니다."),
                                opt("변경 사항은 이번 달부터 적용됩니다.", "다음 달부터라고 언급되었습니다."),
                                opt("자세한 사항은 전화로만 안내됩니다.", "홈페이지를 참고하라고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 적용 시점과 안내 방법을 혼동하기 쉽습니다.", "[공식발표 마인드맵] 변경 전(2주) → 변경 후(3주), 적용 시점(다음 달)을 각각 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "제안/권유",
                        "남자: 이번 워크숍 장소를 도심보다는 좀 한적한 곳으로 정하는 게 어때요?\n여자: 좋은 생각이에요. 그럼 근교 리조트를 알아볼게요.",
                        q("남자가 여자에게 제안한 것으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("워크숍 장소를 한적한 곳으로 정하자는 것", "정답: '한적한 곳으로 정하는 게 어때요?'라는 표현에서 제안이 드러납니다."),
                                opt("워크숍을 취소하자는 것", "언급되지 않은 내용입니다."),
                                opt("워크숍 참석 인원을 줄이자는 것", "언급되지 않은 내용입니다."),
                                opt("워크숍을 도심에서 진행하자는 것", "도심보다 한적한 곳을 제안했으므로 반대됩니다.")
                        ), 0, "🟢 의도 파악 실패 — '도심보다'라는 비교 표현을 놓치면 반대로 이해하기 쉽습니다.", "[제안파악 마인드맵] A보다 B가 어때요?(제안 표현) → B를 제안(결론). 비교 표현 뒤의 내용에 주목하세요."))
        );

        List<PassageSeed> listening11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "화제 고르기",
                        "여자: 요즘 전기차 충전소가 많이 늘었잖아요. 저도 전기차로 바꿀까 고민 중이에요.\n남자: 저도 관심 있어요. 그런데 충전 시간이 좀 걸린다고 하더라고요.",
                        q("두 사람이 무엇에 대해 이야기하고 있는지 고르십시오.", List.of(
                                opt("전기차 구매", "정답: '전기차로 바꿀까 고민', '충전 시간'이라는 표현이 주제를 알려줍니다."),
                                opt("대중교통 이용", "언급되지 않은 주제입니다."),
                                opt("자동차 정비", "언급되지 않은 주제입니다."),
                                opt("주유소 위치", "전기차 충전에 대한 이야기이지 주유소가 아닙니다.")
                        ), 0, "🔵 어휘 부족 — '충전소'라는 단어를 놓치면 다른 주제로 오해하기 쉽습니다.", "[화제파악 마인드맵] 전기차+충전소+충전 시간(핵심 단어) → 전기차 구매(화제). 핵심 단어를 모아 주제를 파악하세요.")),
                onePassage(PassageCategory.LISTENING, "화제 고르기",
                        "남자: 요즘 1인 가구가 많이 늘면서 소형 가전제품 판매도 늘고 있대요.\n여자: 맞아요, 저도 얼마 전에 미니 냉장고를 샀어요.",
                        q("두 사람이 무엇에 대해 이야기하고 있는지 고르십시오.", List.of(
                                opt("1인 가구를 위한 소형 가전", "정답: '1인 가구', '소형 가전제품'이라는 표현이 주제를 알려줍니다."),
                                opt("가전제품 수리", "언급되지 않은 주제입니다."),
                                opt("가구 인테리어", "가전제품에 대한 이야기이지 가구가 아닙니다."),
                                opt("냉장고 청소 방법", "언급되지 않은 주제입니다.")
                        ), 0, "🟢 의도 파악 실패 — '냉장고'라는 단어만 보고 세부 주제로 좁혀 오해하기 쉽습니다.", "[화제파악 마인드맵] 1인 가구+소형 가전(핵심 단어) → 소형 가전 트렌드(화제). 전체 맥락에 집중하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동",
                        "남자: 발표 자료 검토 다 하셨어요?\n여자: 네, 몇 가지 수정할 부분을 표시해 뒀어요. 지금 바로 보내 드릴게요.\n남자: 감사합니다. 참고해서 수정할게요.",
                        q("여자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("수정 사항을 표시한 자료를 보낸다.", "정답: 여자가 바로 하겠다고 말한 행동입니다."),
                                opt("발표 자료를 직접 수정한다.", "수정은 남자가 하기로 했습니다."),
                                opt("발표를 다시 준비한다.", "언급되지 않은 행동입니다."),
                                opt("회의를 취소한다.", "언급되지 않은 행동입니다.")
                        ), 0, "🟣 기타(부주의) — 누가 자료를 보내고 누가 수정하는지 헷갈리기 쉽습니다.", "[행동추론 마인드맵] 여자의 마지막 말(바로 보내 드릴게요) → 이어질 행동. 역할 분담을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동",
                        "여자: 이 계약서에 서명하기 전에 조항을 좀 더 살펴봐야 할 것 같아요.\n남자: 그럼 제가 법무팀에 검토 요청을 넣어 볼게요.\n여자: 네, 부탁드려요.",
                        q("남자가 이어서 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("법무팀에 검토를 요청한다.", "정답: 남자가 직접 하겠다고 말한 행동입니다."),
                                opt("계약서에 바로 서명한다.", "조항을 더 살펴봐야 한다고 했으므로 아직 서명하지 않습니다."),
                                opt("계약을 취소한다.", "언급되지 않은 행동입니다."),
                                opt("여자가 검토를 직접 한다.", "남자가 검토 요청을 넣겠다고 했습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 검토 후 서명이라는 순서를 헷갈리기 쉽습니다.", "[행동추론 마인드맵] 남자의 마지막 말(검토 요청을 넣어 볼게요) → 이어질 행동. 마지막 발화자의 말에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용",
                        "여자: 이번 학회는 어디에서 열려요?\n남자: 컨벤션 센터 3층 대회의실이에요. 등록은 오전 9시부터 시작해요.\n여자: 발표는 몇 시부터예요?\n남자: 10시 반부터 시작할 예정이에요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("발표는 오전 10시 반에 시작합니다.", "정답: '10시 반부터 시작할 예정'이라고 명시되어 있습니다."),
                                opt("등록은 오후에 시작합니다.", "오전 9시부터라고 언급되었습니다."),
                                opt("학회는 2층에서 열립니다.", "3층 대회의실이라고 언급되었습니다."),
                                opt("등록과 발표는 동시에 시작합니다.", "등록은 9시, 발표는 10시 반으로 시간이 다릅니다.")
                        ), 0, "🔴 시간/장소 혼동 — 등록 시간과 발표 시간을 혼동하기 쉽습니다.", "[일치판단 마인드맵] 장소, 등록 시간, 발표 시간을 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "일치하는 내용",
                        "남자: 이번 설문조사 결과가 어떻게 나왔어요?\n여자: 응답자의 70%가 재택근무에 만족한다고 답했어요. 다만 소통 문제를 지적한 사람도 30% 정도 있었어요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("응답자의 70%가 재택근무에 만족했습니다.", "정답: '응답자의 70%가 만족한다고 답했다'고 명시되어 있습니다."),
                                opt("모든 응답자가 재택근무에 만족했습니다.", "70%만 만족했다고 언급되었습니다."),
                                opt("소통 문제를 지적한 사람은 없었습니다.", "30% 정도가 지적했다고 언급되었습니다."),
                                opt("설문조사는 아직 진행 중입니다.", "결과가 이미 나왔다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 만족한 비율과 불만족 비율의 숫자를 혼동하기 쉽습니다.", "[일치판단 마인드맵] 만족 비율(70%)과 문제 지적 비율(30%)을 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각",
                        "여자: 요즘 온라인 강의가 많잖아요. 저는 그래도 직접 만나서 배우는 게 더 효과적인 것 같아요.\n남자: 왜요? 온라인이 시간 활용하기에는 더 좋지 않아요?\n여자: 편하긴 한데, 직접 질문하고 피드백을 받아야 이해가 확실히 되는 것 같아요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("직접 만나서 배우는 것이 이해에 더 도움이 된다.", "정답: '직접 질문하고 피드백을 받아야 확실히 이해된다'는 말에서 여자의 생각이 드러납니다."),
                                opt("온라인 강의가 훨씬 더 효과적이다.", "여자의 생각과 다릅니다."),
                                opt("온라인 강의는 전혀 도움이 안 된다.", "여자의 생각과 다소 다릅니다(편하다고는 인정함)."),
                                opt("시간 활용이 가장 중요하다.", "여자의 생각과 다릅니다.")
                        ), 0, "🟢 의도 파악 실패 — '편하다'는 말만 듣고 온라인 강의를 선호한다고 착각하기 쉽습니다.", "[중심생각 마인드맵] 여자의 마지막 말(질문하고 피드백 받아야 확실히 이해됨)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각",
                        "남자: 요즘 청년들이 결혼을 늦게 하는 게 문제라고들 하잖아요.\n여자: 저는 꼭 문제라고 생각하지 않아요. 각자의 상황에 맞게 선택하는 거니까요.\n남자: 그렇게 생각할 수도 있겠네요.",
                        q("여자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("결혼 시기는 개인의 상황에 맞게 선택하는 것이다.", "정답: '각자의 상황에 맞게 선택하는 것'이라는 말에서 여자의 생각이 드러납니다."),
                                opt("결혼을 늦게 하는 것은 큰 문제다.", "여자의 생각과 반대됩니다."),
                                opt("모든 사람이 일찍 결혼해야 한다.", "언급되지 않은 내용입니다."),
                                opt("결혼은 하지 않는 것이 좋다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 남자의 전제(문제다)를 여자의 생각으로 착각하기 쉽습니다.", "[중심생각 마인드맵] 여자의 말(문제라고 생각하지 않는다, 선택의 문제다)에서 중심 생각을 확인하세요.")),
                onePassage(PassageCategory.LISTENING, "이유/원인",
                        "여자: 이번 신제품 출시가 예정보다 늦어진다고 들었어요.\n남자: 네, 부품 수급에 문제가 생겨서 일정을 조정할 수밖에 없었어요.",
                        q("신제품 출시가 늦어지는 이유로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("부품 수급에 문제가 생겨서", "정답: '부품 수급에 문제가 생겼다'고 명시되어 있습니다."),
                                opt("디자인을 변경해서", "언급되지 않은 내용입니다."),
                                opt("가격을 조정하기 위해서", "언급되지 않은 내용입니다."),
                                opt("고객 반응이 좋지 않아서", "언급되지 않은 내용입니다.")
                        ), 0, "🔵 어휘 부족 — '부품 수급'이라는 단어를 모르면 이유를 파악하기 어렵습니다.", "[이유파악 마인드맵] 결과(출시 지연) ← 원인(부품 수급 문제). '~서/~때문에' 앞의 내용에 주목하세요.")),
                onePassage(PassageCategory.LISTENING, "이유/원인",
                        "남자: 오늘 회의가 갑자기 취소됐다고 하던데요?\n여자: 네, 대표님이 갑자기 급한 출장을 가시게 돼서 다음 주로 미뤄졌어요.",
                        q("회의가 취소된 이유로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("대표가 갑자기 출장을 가게 되어서", "정답: '대표님이 급한 출장을 가시게 됐다'고 명시되어 있습니다."),
                                opt("회의실을 예약하지 못해서", "언급되지 않은 내용입니다."),
                                opt("참석자가 부족해서", "언급되지 않은 내용입니다."),
                                opt("회의 자료가 준비되지 않아서", "언급되지 않은 내용입니다.")
                        ), 0, "🔵 어휘 부족 — 이유를 나타내는 문장 구조를 놓치면 다른 정보로 착각하기 쉽습니다.", "[이유파악 마인드맵] 결과(회의 취소) ← 원인(대표 출장). '~게 되어서' 앞의 내용에 주목하세요."))
        );

        List<PassageSeed> reading21to30 = List.of(
                onePassage(PassageCategory.READING, "화제 고르기",
                        "이 회사는 ( ) 유명합니다. 직원들의 만족도가 업계에서 가장 높다는 조사 결과가 있습니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("복지 제도로", "정답: '직원 만족도가 높다'는 결과와 자연스럽게 연결되는 것은 복지 제도입니다."),
                                opt("매출 규모로", "직원 만족도에 대한 설명과 어울리지 않습니다."),
                                opt("역사로", "직원 만족도에 대한 설명과 어울리지 않습니다."),
                                opt("위치로", "직원 만족도에 대한 설명과 어울리지 않습니다.")
                        ), 0, "🔵 어휘 부족 — 빈칸 뒤의 결과(직원 만족도)와 자연스럽게 연결되는 단어를 찾아야 합니다.", "[빈칸추론 마인드맵] 결과(직원 만족도 높음) ← 원인(복지 제도). 뒤 문장에서 단서를 찾으세요.")),
                onePassage(PassageCategory.READING, "화제 고르기",
                        "이 정책은 ( ) 논란이 되고 있습니다. 찬성하는 쪽과 반대하는 쪽의 의견이 팽팽하게 맞서고 있습니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("여전히", "정답: '찬반 의견이 팽팽하다'는 것은 논란이 계속되고 있다는 의미로 '여전히'가 자연스럽습니다."),
                                opt("전혀", "논란이 되고 있다는 내용과 모순됩니다."),
                                opt("이미", "여전히 논란 중이라는 문맥과 다소 어울리지 않습니다."),
                                opt("결코", "논란이 되고 있다는 내용과 모순됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 지속되는 논란을 나타내는 부사를 정확히 골라야 합니다.", "[빈칸추론 마인드맵] 결과(찬반 대립 지속) ← 원인(여전히 논란). 뒤 문장에서 단서를 찾으세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 조사 결과는 예상과 크게 달라서 연구진도 ( ). 추가 연구가 필요할 것으로 보입니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("당황했습니다", "정답: 예상과 크게 다른 결과에 대한 자연스러운 반응은 당황함입니다."),
                                opt("만족했습니다", "예상과 다르다는 부정적 뉘앙스와 어울리지 않습니다."),
                                opt("무관심했습니다", "추가 연구가 필요하다는 문맥과 어울리지 않습니다."),
                                opt("확신했습니다", "예상과 다르다는 내용과 모순됩니다.")
                        ), 0, "🔵 어휘 부족 — 예상과 다른 결과에 대한 자연스러운 감정 표현을 찾아야 합니다.", "[빈칸추론 마인드맵] 예상과 다른 결과(원인) → 당황함(결과). 앞 문장에서 단서를 찾으세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 지역은 대중교통이 잘 갖춰져 있지 않아서 주민들이 자가용에 ( ) 있습니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("의존할 수밖에 없", "정답: 대중교통이 부족한 상황에서 자가용 의존은 불가피한 선택입니다."),
                                opt("전혀 의존하지 않", "대중교통이 부족하다는 내용과 모순됩니다."),
                                opt("가끔 의존하", "'~수밖에 없다'는 강한 필연성과 어울리는 문맥입니다."),
                                opt("의존을 거부하", "대중교통 부족 상황과 모순됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 대중교통 부족이라는 원인에서 불가피한 결과를 추론해야 합니다.", "[빈칸추론 마인드맵] 대중교통 부족(원인) → 자가용 의존(불가피한 결과). 앞 문장의 정보에 주목하세요.")),
                multiQ(PassageCategory.READING, "안내문 일치",
                        "[전시회 관람 안내]\n관람 시간: 오전 10시 ~ 오후 6시(입장 마감 오후 5시)\n휴관일: 매주 월요일\n※ 사전 예약자에 한해 도슨트 설명이 제공됩니다.",
                        q("이 안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("입장은 오후 5시까지만 가능합니다.", "정답: '입장 마감 오후 5시'라고 명시되어 있습니다."),
                                opt("전시회는 매일 관람할 수 있습니다.", "매주 월요일은 휴관이라고 언급되었습니다."),
                                opt("모든 관람객에게 도슨트 설명이 제공됩니다.", "사전 예약자에 한해 제공된다고 언급되었습니다."),
                                opt("관람 시간은 오후 6시부터입니다.", "오전 10시부터라고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 관람 종료 시간과 입장 마감 시간을 혼동하기 쉽습니다.", "[일치판단 마인드맵] 관람 시간, 입장 마감, 휴관일, 도슨트 조건을 각각 구분해서 확인하세요."),
                        q("이 안내문을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("전시회 관람 조건을 안내하기 위해서", "정답: 관람 시간과 조건 등을 안내하는 글입니다."),
                                opt("전시회 작품을 소개하기 위해서", "작품 소개가 아닌 관람 조건을 안내하고 있습니다."),
                                opt("전시회 개최를 홍보하기 위해서", "이미 진행 중인 전시회의 관람 안내입니다."),
                                opt("입장료 인상을 안내하기 위해서", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 안내문의 여러 정보 중 핵심 목적을 놓치기 쉽습니다.", "[일치판단 마인드맵] 안내문 제목(전시회 관람 안내)에서 목적을 확인하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 회사는 신입사원 채용 시 학벌보다는 실무 능력을 ( ) 평가한다고 밝혔습니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("우선적으로", "정답: '학벌보다는'이라는 비교 표현 뒤에는 실무 능력을 더 중요하게 본다는 의미가 자연스럽습니다."),
                                opt("전혀", "실무 능력으로 평가한다는 문맥과 모순됩니다."),
                                opt("나중에", "학벌보다 실무 능력을 우선한다는 문맥과 어울리지 않습니다."),
                                opt("동등하게", "'~보다는'이라는 비교 표현은 우선순위를 나타내므로 동등함과는 다릅니다.")
                        ), 0, "🟢 의도 파악 실패 — '~보다는'이라는 비교 표현을 놓치면 반대로 이해하기 쉽습니다.", "[빈칸추론 마인드맵] A보다는(비교 기준) → B를 우선(결론). 비교 표현 뒤의 내용에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 태풍으로 인한 피해가 예상보다 커서 복구 작업에 ( ) 시간이 걸릴 것으로 보입니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("상당한", "정답: 피해가 예상보다 크다는 것과 자연스럽게 연결되는 것은 긴 복구 시간을 뜻하는 '상당한'입니다."),
                                opt("아주 적은", "피해가 크다는 내용과 모순됩니다."),
                                opt("전혀 없는", "복구 작업에 시간이 걸린다는 내용과 모순됩니다."),
                                opt("최소한의", "피해가 예상보다 크다는 문맥과 어울리지 않습니다.")
                        ), 0, "🔵 어휘 부족 — '상당한'이라는 단어의 뜻(꽤 많은)을 모르면 반대로 고르기 쉽습니다.", "[빈칸추론 마인드맵] 피해 큼(원인) → 상당한 시간(결과). 앞 문장에서 단서를 찾으세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 제도는 시행 초기에는 반응이 미지근했지만 시간이 지나면서 ( ) 자리 잡았습니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("점차", "정답: 시간이 지나면서 서서히 정착되었다는 흐름과 자연스럽게 연결됩니다."),
                                opt("갑자기", "'시간이 지나면서'라는 점진적 변화를 나타내는 표현과 어울리지 않습니다."),
                                opt("잠깐", "제도가 자리 잡았다는 지속적 결과와 어울리지 않습니다."),
                                opt("전혀", "자리 잡았다는 결과와 모순됩니다.")
                        ), 0, "🟢 의도 파악 실패 — '시간이 지나면서'라는 점진적 변화 표현을 놓치면 다른 부사를 고르기 쉽습니다.", "[빈칸추론 마인드맵] 초기 미지근함(원인) → 점차 자리 잡음(결과). 시간의 흐름을 나타내는 표현에 주목하세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "회사 측은 이번 구조조정이 ( ) 조치라고 해명했지만, 직원들의 불안감은 쉽게 가라앉지 않고 있습니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("불가피한", "정답: 회사가 해명하는 문맥에서 구조조정을 정당화하는 표현으로 '불가피한'이 자연스럽습니다."),
                                opt("불필요한", "회사가 스스로 정당화하려는 해명 문맥과 어울리지 않습니다."),
                                opt("갑작스러운", "해명의 논리(어쩔 수 없었다)와는 다른 뉘앙스입니다."),
                                opt("일시적인", "구조조정의 정당성을 설명하는 문맥과 다소 어울리지 않습니다.")
                        ), 0, "🔵 어휘 부족 — '불가피하다'라는 단어의 뜻(피할 수 없다)을 모르면 반대로 고르기 쉽습니다.", "[빈칸추론 마인드맵] 회사의 해명(입장) → 불가피함 강조(논리). 해명의 목적에 맞는 표현을 찾으세요."))
        );

        List<PassageSeed> reading31to40 = List.of(
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "최근 기업들 사이에서 주 4일 근무제 도입 논의가 활발합니다. 일부 기업은 이미 시범 운영을 시작했고, 생산성 저하 없이 직원 만족도가 크게 높아졌다는 결과를 발표했습니다. 앞으로 더 많은 기업이 이 제도의 도입을 검토할 것으로 보입니다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("주 4일 근무제가 직원 만족도를 높이며 확산될 전망이다.", "정답: 시범 운영 결과와 전망을 종합한 중심 내용입니다."),
                                opt("주 4일 근무제는 생산성을 크게 떨어뜨린다.", "생산성 저하 없이 만족도가 높아졌다고 언급되어 반대됩니다."),
                                opt("기업들은 주 4일 근무제에 전혀 관심이 없다.", "논의가 활발하다고 언급되어 반대됩니다."),
                                opt("주 4일 근무제는 아직 시행된 적이 없다.", "일부 기업이 이미 시범 운영을 시작했다고 언급되었습니다.")
                        ), 0, "🟢 의도 파악 실패 — 시범 운영이라는 부분적 사실만 보고 전체 결론을 놓치기 쉽습니다.", "[중심내용 마인드맵] 도입 논의 → 시범 운영 결과(긍정적) → 확산 전망. 글 전체의 흐름을 정리하세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "인공지능 기술이 빠르게 발전하면서 여러 산업 분야에서 활용도가 높아지고 있습니다. 하지만 일자리 감소에 대한 우려도 함께 커지고 있습니다. 전문가들은 기술 발전과 더불어 새로운 직업군이 생겨날 것이라며 균형 잡힌 시각이 필요하다고 조언합니다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("인공지능 발전에 대해 우려와 기대를 함께 고려하는 균형 잡힌 시각이 필요하다.", "정답: 마지막 문장에서 전문가의 조언으로 중심 내용이 드러납니다."),
                                opt("인공지능은 일자리를 전혀 줄이지 않는다.", "우려가 커지고 있다고 언급되어 단정할 수 없습니다."),
                                opt("인공지능 기술 발전을 완전히 중단해야 한다.", "언급되지 않은 내용입니다."),
                                opt("새로운 직업군은 절대 생기지 않을 것이다.", "전문가들이 새 직업군이 생길 것이라고 언급해 반대됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 우려라는 한쪽 측면만 보고 균형 잡힌 결론을 놓치기 쉽습니다.", "[중심내용 마인드맵] 발전(긍정) + 우려(부정) → 균형 잡힌 시각(결론). 양면을 종합한 결론에 주목하세요.")),
                onePassage(PassageCategory.READING, "순서 배열하기",
                        "(가) 그 결과 매출이 전년 대비 30% 증가했습니다.\n(나) 이 회사는 작년부터 온라인 마케팅을 대폭 강화했습니다.\n(다) 특히 SNS를 활용한 홍보에 집중적으로 투자했습니다.\n(라) 이러한 성과를 바탕으로 올해도 같은 전략을 이어 갈 계획입니다.",
                        q("순서대로 맞게 배열한 것을 고르십시오.", List.of(
                                opt("(나)-(다)-(가)-(라)", "정답: 전략 시작(나) → 구체적 방법(다) → 결과(가) → 향후 계획(라) 순서가 자연스럽습니다."),
                                opt("(가)-(나)-(다)-(라)", "결과(가)가 전략 설명(나)보다 먼저 나오면 순서가 어색합니다."),
                                opt("(다)-(나)-(가)-(라)", "구체적 방법(다)이 전체 전략(나)보다 먼저 나오면 흐름이 끊깁니다."),
                                opt("(라)-(나)-(다)-(가)", "향후 계획(라)이 맨 앞에 나오면 전체 흐름과 맞지 않습니다.")
                        ), 0, "🟣 기타(부주의) — 전체 전략과 구체적 방법의 순서를 헷갈리기 쉽습니다.", "[순서배열 마인드맵] 전략 시작 → 구체적 방법 → 결과(그 결과) → 향후 계획. 흐름 순서를 확인하세요.")),
                onePassage(PassageCategory.READING, "순서 배열하기",
                        "(가) 이 정책의 목적은 저소득층의 주거 안정을 지원하는 것입니다.\n(나) 정부는 최근 새로운 주거 지원 정책을 발표했습니다.\n(다) 그래서 임대료 일부를 지원하는 방식으로 운영됩니다.\n(라) 전문가들은 이 정책이 실효성이 있을지 지켜봐야 한다고 평가합니다.",
                        q("순서대로 맞게 배열한 것을 고르십시오.", List.of(
                                opt("(나)-(가)-(다)-(라)", "정답: 발표 소식(나) → 목적(가) → 구체적 방식(다) → 전문가 평가(라) 순서가 자연스럽습니다."),
                                opt("(가)-(나)-(다)-(라)", "목적(가)이 발표 소식(나)보다 먼저 나오면 무엇에 대한 목적인지 알 수 없습니다."),
                                opt("(다)-(가)-(나)-(라)", "구체적 방식(다)이 목적(가)보다 먼저 나오면 흐름이 끊깁니다."),
                                opt("(라)-(나)-(가)-(다)", "전문가 평가(라)가 맨 앞에 나오면 무엇에 대한 평가인지 알 수 없습니다.")
                        ), 0, "🟣 기타(부주의) — 정책 발표와 목적 설명의 순서를 헷갈리기 쉽습니다.", "[순서배열 마인드맵] 발표 소식 → 목적 → 방식(그래서) → 평가. 정보가 소개되는 자연스러운 흐름을 확인하세요.")),
                onePassage(PassageCategory.READING, "중심 생각 고르기",
                        "일회용품 사용 규제에 대해 불편하다는 의견이 많습니다. 하지만 환경 보호라는 더 큰 가치를 위해서는 당장의 불편함을 감수할 필요가 있다고 생각합니다. 작은 불편이 미래의 큰 이익으로 돌아올 것입니다.",
                        q("이 글에 나타난 글쓴이의 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("환경 보호를 위해 당장의 불편함을 감수해야 한다.", "정답: '불편함을 감수할 필요가 있다'는 말에서 글쓴이의 생각이 드러납니다."),
                                opt("일회용품 규제는 불필요하다.", "글쓴이의 생각과 반대됩니다."),
                                opt("환경 보호보다 편리함이 더 중요하다.", "글쓴이의 생각과 반대됩니다."),
                                opt("규제는 즉시 폐지되어야 한다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 초반의 불편하다는 의견을 글쓴이 자신의 생각으로 착각하기 쉽습니다.", "[중심생각 마인드맵] 하지만(역접) 뒤에 나오는 문장(감수할 필요가 있다)에서 글쓴이의 진짜 생각을 확인하세요.")),
                multiQ(PassageCategory.READING, "빈칸/일치",
                        "최근 반려동물을 키우는 가구가 늘면서 관련 산업도 빠르게 성장하고 있습니다. 특히 반려동물 전용 보험 상품에 대한 관심이 ( ) 있는데, 이는 의료비 부담을 줄이려는 보호자들의 요구가 반영된 결과입니다.",
                        q("( )에 들어갈 가장 알맞은 것을 고르십시오.", List.of(
                                opt("높아지고", "정답: 관련 산업 성장과 보호자 요구 증가라는 문맥과 자연스럽게 연결됩니다."),
                                opt("낮아지고", "산업이 빠르게 성장한다는 내용과 모순됩니다."),
                                opt("사라지고", "관심이 반영된 결과라는 내용과 모순됩니다."),
                                opt("멈추고", "산업이 성장하고 있다는 내용과 모순됩니다.")
                        ), 0, "🟢 의도 파악 실패 — 산업 성장이라는 전체 흐름에서 세부 관심도의 방향을 놓치기 쉽습니다.", "[빈칸추론 마인드맵] 산업 성장(전체 흐름) → 관심 증가(세부 내용). 앞뒤 문맥의 방향이 일치하는지 확인하세요."),
                        q("이 글의 내용과 같은 것을 고르십시오.", List.of(
                                opt("반려동물 전용 보험에 대한 관심이 커지고 있습니다.", "정답: '관심이 높아지고 있다'고 명시되어 있습니다."),
                                opt("반려동물 관련 산업은 위축되고 있습니다.", "빠르게 성장하고 있다고 언급되어 반대됩니다."),
                                opt("보호자들은 의료비 부담에 관심이 없습니다.", "의료비 부담을 줄이려는 요구가 있다고 언급되어 반대됩니다."),
                                opt("반려동물을 키우는 가구는 줄어들고 있습니다.", "늘고 있다고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 산업 성장과 위축을 반대로 착각하기 쉽습니다.", "[일치판단 마인드맵] 가구 증가, 산업 성장, 보험 관심 증가를 각각 확인하세요.")),
                multiQ(PassageCategory.READING, "목적/일치",
                        "시청 여러분께 안내드립니다. 다음 달부터 시내버스 요금이 200원 인상됩니다. 이번 인상은 유가 상승과 운영비 증가에 따른 불가피한 조치로, 시민 여러분의 양해를 부탁드립니다.",
                        q("이 안내문을 쓴 목적으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("버스 요금 인상을 안내하기 위해서", "정답: '시내버스 요금이 200원 인상된다'고 명시되어 있습니다."),
                                opt("새로운 버스 노선을 홍보하기 위해서", "언급되지 않은 내용입니다."),
                                opt("버스 운행 시간을 안내하기 위해서", "언급되지 않은 내용입니다."),
                                opt("버스 요금 할인 혜택을 안내하기 위해서", "인상 안내이지 할인 안내가 아닙니다.")
                        ), 0, "🟢 의도 파악 실패 — 마지막의 양해 요청만 보고 목적을 다르게 착각하기 쉽습니다.", "[목적파악 마인드맵] 첫 문장(요금 인상 안내)에서 전체 목적을 확인하세요."),
                        q("이 글의 내용과 같은 것을 고르십시오.", List.of(
                                opt("버스 요금은 다음 달부터 오릅니다.", "정답: '다음 달부터 200원 인상된다'고 명시되어 있습니다."),
                                opt("요금 인상은 이번 달부터 적용됩니다.", "다음 달부터라고 언급되었습니다."),
                                opt("요금이 인상되는 이유는 언급되지 않았습니다.", "유가 상승과 운영비 증가가 이유로 언급되었습니다."),
                                opt("버스 요금은 300원 인상됩니다.", "200원 인상이라고 언급되었습니다.")
                        ), 0, "🔴 시간/장소 혼동 — 적용 시점과 인상 금액을 혼동하기 쉽습니다.", "[일치판단 마인드맵] 적용 시점, 인상 금액, 인상 이유를 각각 구분해서 확인하세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "전문가들은 청소년의 과도한 스마트폰 사용이 집중력 저하로 이어질 수 있다고 경고합니다. 다만 무조건적인 사용 금지보다는 사용 시간을 스스로 조절하는 습관을 길러 주는 것이 장기적으로 더 효과적이라고 조언합니다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("스마트폰 사용을 금지하기보다 스스로 조절하는 습관을 기르는 것이 효과적이다.", "정답: 마지막 문장에서 전문가의 조언으로 중심 내용이 드러납니다."),
                                opt("청소년의 스마트폰 사용을 완전히 금지해야 한다.", "무조건적인 금지보다 조절 습관이 낫다고 언급되어 반대됩니다."),
                                opt("스마트폰 사용은 집중력에 전혀 영향을 주지 않는다.", "집중력 저하로 이어질 수 있다고 언급되어 반대됩니다."),
                                opt("청소년은 스마트폰을 자유롭게 사용해도 문제없다.", "언급되지 않은 내용입니다.")
                        ), 0, "🟢 의도 파악 실패 — 완전 금지와 스스로 조절이라는 두 입장을 헷갈리기 쉽습니다.", "[중심내용 마인드맵] 경고(집중력 저하) + 조언(조절 습관)에서 핵심 결론을 확인하세요."))
        );

        return new WeekSeed("3~4급 컬러맵 기초 다지기",
                "TOPIK II 수준의 듣기·읽기 기본기를 색깔 코딩과 마인드맵으로 시각화하며 다진다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(의견 제시, 비교/선택, 뉴스/보도, 직장 생활, 일치하는 내용, 화자의 태도, 문제 상황, 예측/추론, 공식 발표, 화제, 이어질 행동, 중심 생각, 이유/원인) + 읽기 20(화제, 빈칸 추론, 안내문 일치, 중심 내용 파악, 순서 배열). 색깔 펜으로 오답을 표시하고 오답 노트 템플릿에 취약 유형을 기록하세요.",
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
