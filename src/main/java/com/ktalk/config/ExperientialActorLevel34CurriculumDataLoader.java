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
 * 체험적 실행형(EXPERIENTIAL_ACTOR) 유형의 3~4급 "TOPIK 직접 실행 워크북" 커리큘럼을 심는다.
 * ExperientialActorCurriculumDataLoader(1~2급)와 동일한 골격(레코드/헬퍼, 8주 + 모의고사 2회
 * + Final 1회, 총 2,450문항)을 쓰되, trapNote/strategyTip을 동일한 "직접 해보기" 체험 활동
 * 언어로 유지하면서 3~4급 수준(더 긴 문장, 의견 제시·화자의 태도 파악 등 고급 유형)에 맞게 새로
 * 설계한다 — LearnerType.EXPERIENTIAL_ACTOR의 studyTip("뽀모도로 기법과 즉시 채점·즉시
 * 피드백을 반복하세요")을 모든 문항에 반영한다.
 * 1~2급/5~6급 과정과는 완전히 분리된 별도의 8주 과정으로, 같은 learner_type이라도
 * targetLevelFrom(LEVEL_3)으로 구분되는 별도 Curriculum 레코드를 갖는다.
 *
 * [체험 활동 태그 설계 - EXPERIENTIAL_ACTOR 고유 4종, 1~2급과 동일]
 * ✍️ 직접 써보기 함정: 손으로 옮겨 쓰거나 밑줄을 그어야 잡히는 세부 정보(숫자·시간·이름)를 놓침.
 * 🗣️ 소리 내어 말하기 함정: 대화를 실제로 소리 내어 읽거나 역할을 나눠 연기해야 잡히는 어조·의도를 놓침.
 * 👆 손으로 짚어보기 함정: 지문의 순서나 구조를 손가락으로 짚어가며 확인해야 잡히는 흐름·순서를 놓침.
 * ⏱️ 즉시 재도전 함정: 타이머를 맞추고 즉시 다시 풀어야 잡히는 부주의·성급함으로 인한 실수.
 * strategyTip은 항상 "[체험미션: ○○] ..." 형식으로 시작해 구체적인 신체 활동 지시로 끝난다.
 */
@Component
@RequiredArgsConstructor
@Order(20)
public class ExperientialActorLevel34CurriculumDataLoader implements CommandLineRunner {

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
        curriculumRepository.findByLearnerTypeAndTargetLevelFrom(LearnerType.EXPERIENTIAL_ACTOR, TopikLevel.LEVEL_3)
                .ifPresent(this::deleteExisting);

        Curriculum curriculum = new Curriculum();
        curriculum.setLearnerType(LearnerType.EXPERIENTIAL_ACTOR);
        curriculum.setTitle("TOPIK 직접 실행 워크북");
        curriculum.setTargetLevelLabel("3~4급 전 과정");
        curriculum.setTargetLevelFrom(TopikLevel.LEVEL_3);
        curriculum.setTargetLevelTo(TopikLevel.LEVEL_4);
        curriculum.setUsageNote(
                "모든 문제에 체험 활동 태그(✍️ 직접 써보기 / 🗣️ 소리 내어 말하기 / 👆 손으로 짚어보기 / ⏱️ 즉시 재도전)로 "
                        + "함정 포인트를 구분합니다. 문제를 풀고 정답을 확인한 뒤에는 반드시 strategyTip의 체험미션을 "
                        + "그 자리에서 직접 실행하세요 — 눈으로만 읽고 넘어가지 말고, 노트에 옮겨 쓰거나 소리 내어 "
                        + "말하거나 손가락으로 짚어보거나 타이머로 재도전하며 몸으로 기억하세요. 3~4급부터는 문장이 "
                        + "길어지므로 의견 제시나 화자의 태도 같은 고급 유형도 몸으로 체험하며 익히세요. 25분 집중·5분 "
                        + "휴식의 뽀모도로 리듬을 지키고, 채점은 그 즉시 하세요.");

        List<WeekSeed> weeks = List.of(week1());
        saveCurriculumWithDays(curriculum, weeks);

        System.out.println("✍️ TOPIK 커리큘럼(체험적 실행형, 3~4급) WEEK1 1차 시딩 완료!");
    }

    /** 재시딩 전 기존 커리큘럼을 지운다. day는 부모의 cascade 대상이 아니라 먼저 지워야 한다. */
    private void deleteExisting(Curriculum existing) {
        List<CurriculumDay> days = curriculumDayRepository.findByCurriculumId(existing.getId());
        curriculumDayRepository.deleteAll(days);
        userCurriculumProgressRepository.deleteByCurriculumId(existing.getId());
        curriculumRepository.delete(existing);
        curriculumRepository.flush();
    }

    // ===================== WEEK 1: 3~4급 체험 기초 다지기 =====================

    private static final String WEEK1_ANSWER_NOTE_TEMPLATE = """
            [✍️ 체험 기록장 - WEEK1용]
            문제를 틀렸을 때 체험 활동 태그로 표시하며 나의 취약 유형을 확인해보세요.

            문제 번호(1~40) | 틀린 이유(해당 태그 동그라미) | 체험미션 실행 여부(V표시)
            예) 3번 | ✍️ (숫자·시간 놓침) | V

            [✍️ 체험 활동 태그별 취약 유형 가이드]
            ✍️ 직접 써보기 함정: 숫자·시간·이름 등 세부 정보를 손으로 옮겨 쓰지 않아 놓침.
            🗣️ 소리 내어 말하기 함정: 대화를 소리 내어 읽거나 역할을 나눠 연기하지 않아 어조·의도를 놓침.
            👆 손으로 짚어보기 함정: 지문의 순서나 구조를 손가락으로 짚어보지 않아 흐름을 놓침.
            ⏱️ 즉시 재도전 함정: 타이머로 즉시 다시 풀어보지 않아 부주의한 실수를 반복함.

            같은 태그가 반복해서 표시된다면, 그 유형의 체험미션을 다음 학습 때 우선적으로 실행하세요.
            채점은 문제를 푼 직후 즉시 하고, 25분 학습·5분 휴식의 뽀모도로 리듬을 지키세요.
            """;

    private WeekSeed week1() {
        List<PassageSeed> listening1to10 = List.of(
                onePassage(PassageCategory.LISTENING, "의견 제시",
                        "여자: 요즘 회사에서 자율 출근제를 도입한다는데 어떻게 생각하세요?\n남자: 저는 찬성이에요. 개인 컨디션에 맞춰 일할 수 있어서 효율이 오를 것 같아요.",
                        q("남자의 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("자율 출근제에 찬성한다.", "정답: '찬성이에요'라는 말과 뒤이은 이유가 근거입니다."),
                                opt("자율 출근제에 반대한다.", "🗣️ 남자의 말과 반대되는 내용입니다."),
                                opt("자율 출근제는 상관없다고 생각한다.", "🗣️ 명확히 찬성 의사를 밝혔으므로 무관심이 아닙니다."),
                                opt("기존 출근 방식이 더 좋다고 생각한다.", "🗣️ 효율이 오를 것 같다고 했으므로 반대되는 내용입니다.")
                        ), 0, "🗣️ 이유를 설명하는 어조에 집중하다가 정작 찬반 입장 자체를 놓치기 쉽습니다.", "[체험미션: 역할연기하기] '찬성이에요(입장)-효율 오를 것(이유)'을 두 사람 역할로 나눠 소리 내어 연기해 보세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 이번 세미나는 며칠 동안 진행돼요?\n여자: 원래 이틀이었는데 참가 신청이 많아서 사흘로 늘었어요.",
                        q("세미나가 진행되는 기간으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("사흘", "정답: '늘었어요'라는 말 뒤의 최종 기간이 정답입니다."),
                                opt("이틀", "✍️ 원래 기간(변경 전)에만 꽂혀 최종 정보를 놓치게 합니다."),
                                opt("나흘", "✍️ 대화에 없는 기간을 임의로 만든 오답입니다."),
                                opt("하루", "✍️ 대화에 없는 기간입니다.")
                        ), 0, "✍️ 기간이 두 번 언급될 때 먼저 들린 정보를 정답처럼 착각하게 합니다.", "[체험미션: 직접써보기] '원래 이틀 → 신청 많음 → 사흘(최종)'을 노트에 화살표로 직접 옮겨 적어 보세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도 파악",
                        "여자: 이번 프로젝트 결과 발표 정말 잘하셨어요.\n남자: 아니에요, 팀원들이 다 같이 준비해 준 덕분이죠. 저 혼자 한 게 아니에요.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("공을 팀원들에게 돌리며 겸손해하고 있다.", "정답: '팀원들 덕분'이라고 공을 돌리는 겸손한 태도입니다."),
                                opt("자신의 성과를 자랑스러워하고 있다.", "👆 공을 돌리는 태도와 반대됩니다."),
                                opt("발표 결과에 불만을 가지고 있다.", "👆 칭찬을 받아들이는 긍정적 반응이므로 불만이 아닙니다."),
                                opt("팀원들을 원망하고 있다.", "👆 오히려 팀원들에게 감사하는 태도입니다.")
                        ), 0, "👆 칭찬에 대한 응답 표현('아니에요')만 보면 겸손의 대상을 놓치기 쉽습니다.", "[체험미션: 손가락짚기] '아니에요(겸손)'와 '팀원들 덕분(공 돌리기)'에 손가락을 짚으며 태도를 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동 고르기",
                        "여자: 이번 주말에 이사하는데 도와줄 사람이 부족해요.\n남자: 제가 토요일 오전에 시간 되니까 도와드릴게요.\n여자: 정말요? 그럼 아홉 시까지 와 주시겠어요?",
                        q("남자가 이 대화 후에 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("토요일 아홉 시에 이사를 도우러 간다.", "정답: 여자의 요청에 남자가 응답할 차례이며 앞선 제안과 일치합니다."),
                                opt("이사를 대신 해 준다.", "👆 돕는 것이지 대신 하는 것이 아닙니다."),
                                opt("일요일에 방문한다.", "👆 토요일이라고 했으므로 틀린 정보입니다."),
                                opt("이사를 취소한다.", "👆 언급되지 않은 내용입니다.")
                        ), 0, "⏱️ 요일과 시간이 대화 중간에 바뀌면 최종 약속 시간을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '토요일 오전(제안)-아홉 시(확정)' 흐름을 바로 다시 소리 내어 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 저는 회의할 때 결론부터 먼저 말하는 게 좋다고 생각해요. 배경 설명이 길어지면 정작 중요한 결론을 놓치기 쉽거든요.",
                        q("남자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("회의에서는 결론을 먼저 말해야 한다.", "정답: 남자의 핵심 주장이 첫 문장에 제시되어 있습니다."),
                                opt("배경 설명이 가장 중요하다.", "👆 남자의 생각과 반대됩니다."),
                                opt("회의는 짧을수록 좋다.", "👆 언급되지 않은 내용입니다."),
                                opt("결론은 마지막에 말해야 한다.", "👆 남자의 생각과 반대됩니다.")
                        ), 0, "👆 이유 설명(배경 설명이 길어지면)에만 집중하면 앞의 핵심 주장을 놓치기 쉽습니다.", "[체험미션: 손가락짚기] '결론부터 말하기(주장)'와 '배경 설명이 길면 놓침(이유)'에 손가락을 짚으며 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "장소 추론",
                        "여자: 여기 이 서류 접수하려고 하는데요.\n남자: 네, 신분증 먼저 확인할게요. 번호표 뽑고 잠시만 기다려 주세요.",
                        q("두 사람이 있는 곳으로 가장 알맞은 곳을 고르십시오.", List.of(
                                opt("행정 민원 창구", "정답: 서류 접수와 신분증 확인, 번호표라는 단서로 민원 창구임을 알 수 있습니다."),
                                opt("도서관", "👆 서류 접수와 관련 없는 장소입니다."),
                                opt("병원 대기실", "👆 서류 접수와 신분증 확인이라는 단서가 병원보다 민원 창구에 더 알맞습니다."),
                                opt("편의점", "👆 언급되지 않은 장소입니다.")
                        ), 0, "👆 '서류 접수', '신분증 확인', '번호표'라는 세 가지 단서를 함께 짚어야 정확한 장소를 알 수 있습니다.", "[체험미션: 손가락짚기] '서류 접수(1)-신분증 확인(2)-번호표(3)'에 순서대로 손가락을 짚으며 장소를 추론해 보세요.")),
                onePassage(PassageCategory.LISTENING, "이유 추론",
                        "남자: 오늘 회의에 왜 늦으셨어요?\n여자: 죄송합니다. 지하철이 갑자기 멈춰서 한참을 기다렸어요.",
                        q("여자가 회의에 늦은 이유로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("지하철이 멈춰서", "정답: '지하철이 갑자기 멈춰서'라고 직접 말했습니다."),
                                opt("늦잠을 자서", "✍️ 언급되지 않은 이유입니다."),
                                opt("길을 잃어서", "✍️ 언급되지 않은 이유입니다."),
                                opt("회의 시간을 착각해서", "✍️ 언급되지 않은 이유입니다.")
                        ), 0, "✍️ 이유를 나타내는 문장을 손으로 표시하지 않으면 다른 추측성 이유와 헷갈리기 쉽습니다.", "[체험미션: 직접써보기] '지하철 멈춤(이유)-한참 기다림(결과)'을 노트에 화살표로 직접 옮겨 적어 보세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 의도 고르기",
                        "여자: 이 보고서 마감이 내일인데 아직 반도 못 끝냈어요.\n남자: 저도 오늘 여유가 좀 있으니까 같이 나눠서 해요.",
                        q("남자가 여자에게 말하는 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("업무를 나눠서 함께 하자고 제안하려고", "정답: '같이 나눠서 해요'라는 제안이 핵심입니다."),
                                opt("마감을 미루라고 말하려고", "🗣️ 언급되지 않은 내용입니다."),
                                opt("혼자 다 하라고 재촉하려고", "🗣️ 함께 하자고 제안했으므로 반대됩니다."),
                                opt("보고서를 취소하라고 말하려고", "🗣️ 언급되지 않은 내용입니다.")
                        ), 0, "🗣️ 공감하는 어조('저도 여유가')에만 집중하면 실제 제안 내용을 놓치기 쉽습니다.", "[체험미션: 역할연기하기] 이 대화를 두 사람 역할로 나눠 소리 내어 연기하며 제안의 의도를 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 신청서는 어디로 제출하면 되나요?\n여자: 온라인으로 제출하시면 되는데, 마감은 이번 주 금요일 오후 여섯 시까지입니다.",
                        q("신청서 제출 마감 시간으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("금요일 오후 여섯 시", "정답: '마감은 이번 주 금요일 오후 여섯 시까지'라고 명시되어 있습니다."),
                                opt("금요일 오전 여섯 시", "✍️ 오전과 오후를 혼동한 오답입니다."),
                                opt("목요일 오후 여섯 시", "✍️ 요일을 혼동한 오답입니다."),
                                opt("토요일 오후 여섯 시", "✍️ 언급되지 않은 요일입니다.")
                        ), 0, "✍️ 요일과 오전/오후를 정확히 옮겨 적지 않으면 비슷한 다른 시간과 헷갈리기 쉽습니다.", "[체험미션: 직접써보기] '금요일-오후-여섯 시'를 노트에 순서대로 직접 옮겨 적어 보세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도 파악",
                        "여자: 새로 오신 팀장님 어떠세요?\n남자: 아직은 잘 모르겠어요. 좀 더 지켜봐야 할 것 같아요.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("판단을 유보하며 신중한 태도를 보이고 있다.", "정답: '아직은 잘 모르겠다', '지켜봐야 한다'는 말이 신중한 태도를 나타냅니다."),
                                opt("팀장님을 매우 긍정적으로 평가하고 있다.", "👆 아직 판단하지 않았으므로 틀린 내용입니다."),
                                opt("팀장님을 매우 부정적으로 평가하고 있다.", "👆 아직 판단하지 않았으므로 틀린 내용입니다."),
                                opt("팀장님에 대해 전혀 관심이 없다.", "👆 지켜보겠다고 했으므로 관심이 없는 것이 아닙니다.")
                        ), 0, "👆 '아직 잘 모르겠다'는 표현을 부정적 평가로 착각하기 쉽습니다.", "[체험미션: 손가락짚기] '아직 모름(유보)'과 '지켜봐야 함(신중)'에 손가락을 짚으며 태도를 확인해 보세요."))
        );

        List<PassageSeed> listening11to20 = List.of(
                onePassage(PassageCategory.LISTENING, "일치하는 내용 고르기",
                        "여자: 이번 신제품 설명회는 다음 달 둘째 주 화요일에 본사 대강당에서 열립니다. 참석을 원하시는 분은 이번 주까지 신청해 주세요.",
                        q("들은 내용과 같은 것을 고르십시오.", List.of(
                                opt("설명회는 본사 대강당에서 열린다.", "정답: '본사 대강당에서 열립니다'라는 말과 일치합니다."),
                                opt("설명회는 이번 주에 열린다.", "🗣️ 다음 달 둘째 주라고 했으므로 틀린 정보입니다."),
                                opt("신청 기한은 다음 달까지이다.", "🗣️ 이번 주까지라고 했으므로 틀린 정보입니다."),
                                opt("설명회는 지사에서 열린다.", "🗣️ 본사라고 했으므로 틀린 정보입니다.")
                        ), 0, "🗣️ 개최 시점(다음 달)과 신청 기한(이번 주)이라는 두 시점을 혼동하기 쉽습니다.", "[체험미션: 소리내어시점확인하기] '개최-다음 달 둘째 주'와 '신청-이번 주'를 소리 내어 구분해 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "중심 생각 고르기",
                        "남자: 저는 여행을 갈 때 계획을 세우지 않는 편이에요. 오히려 즉흥적으로 다니다 보면 예상치 못한 좋은 경험을 하게 되더라고요.",
                        q("남자의 중심 생각으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("즉흥적인 여행이 뜻밖의 좋은 경험을 줄 수 있다.", "정답: 남자의 핵심 생각이 마지막 문장에 제시되어 있습니다."),
                                opt("여행은 반드시 계획을 세워야 한다.", "👆 남자의 생각과 반대됩니다."),
                                opt("여행은 위험하므로 조심해야 한다.", "👆 언급되지 않은 내용입니다."),
                                opt("계획 없는 여행은 늘 실패한다.", "👆 남자의 생각과 반대됩니다.")
                        ), 0, "👆 '계획을 세우지 않는다'는 방법에만 집중하면 그로 인한 긍정적 효과를 놓치기 쉽습니다.", "[체험미션: 손가락짚기] '계획 없이 다님(방법)'과 '좋은 경험(효과)'에 손가락을 짚으며 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 의도 고르기",
                        "여자: 이번 분기 실적이 목표보다 낮게 나왔네요.\n남자: 네, 다음 분기에는 마케팅 전략을 좀 더 보완해야 할 것 같습니다.",
                        q("남자가 여자에게 말하는 의도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("실적 부진의 원인을 분석하고 개선 방안을 제시하려고", "정답: 목표 미달 상황에 대해 마케팅 보완이라는 개선 방안을 제시하고 있습니다."),
                                opt("실적 부진의 책임을 회피하려고", "🗣️ 개선 방안을 제시했으므로 회피가 아닙니다."),
                                opt("목표치를 낮추자고 제안하려고", "🗣️ 언급되지 않은 내용입니다."),
                                opt("사업을 그만두자고 말하려고", "🗣️ 언급되지 않은 내용입니다.")
                        ), 0, "🗣️ 문제 상황(낮은 실적)에만 집중하면 뒤이어 나오는 개선 방안 제시의 의도를 놓치기 쉽습니다.", "[체험미션: 역할연기하기] '실적 낮음(문제)-전략 보완 필요(개선 방안)'를 두 사람 역할로 나눠 소리 내어 연기해 보세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 이 강좌는 몇 명까지 신청할 수 있나요?\n여자: 정원은 스무 명인데 현재 열다섯 명 신청하셨어요. 다섯 자리 남았습니다.",
                        q("강좌에 남은 자리 수로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("다섯 자리", "정답: '다섯 자리 남았습니다'라고 명시되어 있습니다."),
                                opt("스무 자리", "✍️ 전체 정원이므로 남은 자리가 아닙니다."),
                                opt("열다섯 자리", "✍️ 이미 신청한 인원수이므로 남은 자리가 아닙니다."),
                                opt("열 자리", "✍️ 언급되지 않은 숫자입니다.")
                        ), 0, "✍️ 정원, 신청 인원, 남은 자리라는 세 숫자를 정확히 계산해 옮겨 적지 않으면 헷갈리기 쉽습니다.", "[체험미션: 계산해보기] 노트에 '정원 20 - 신청 15 = ?'를 직접 손으로 써서 계산해 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "화자의 태도 파악",
                        "여자: 이번 계약 조건에 대해 어떻게 생각하세요?\n남자: 나쁘지 않은데, 조금 더 협상의 여지가 있어 보여요.",
                        q("남자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("조건에 대체로 만족하지만 개선 여지를 검토하고 있다.", "정답: '나쁘지 않다'는 긍정과 '협상 여지'라는 신중한 검토가 함께 나타납니다."),
                                opt("조건에 완전히 만족하고 있다.", "👆 협상 여지가 있다고 했으므로 완전한 만족이 아닙니다."),
                                opt("조건에 매우 불만족하고 있다.", "👆 나쁘지 않다고 했으므로 불만족이 아닙니다."),
                                opt("계약을 즉시 취소하려고 한다.", "👆 언급되지 않은 내용입니다.")
                        ), 0, "👆 '나쁘지 않다'는 표현만 보면 완전한 만족으로 착각하기 쉽습니다.", "[체험미션: 손가락짚기] '나쁘지 않음(긍정)'과 '협상 여지 있음(신중)'에 손가락을 짚으며 태도를 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "이어질 행동 고르기",
                        "남자: 발표 자료 다 만들었어요?\n여자: 네, 거의 다 됐는데 마지막 그래프만 확인하면 돼요.\n남자: 그럼 제가 확인해 볼게요.",
                        q("남자가 이 대화 후에 할 행동으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("발표 자료의 그래프를 확인한다.", "정답: '제가 확인해 볼게요'라고 직접 말했습니다."),
                                opt("발표 자료를 처음부터 다시 만든다.", "👆 언급되지 않은 내용입니다."),
                                opt("발표를 취소한다.", "👆 언급되지 않은 내용입니다."),
                                opt("여자에게 자료를 맡긴다.", "👆 남자가 직접 확인하겠다고 했으므로 반대됩니다.")
                        ), 0, "⏱️ 마지막 문장을 놓치면 누가 무엇을 확인할지 헷갈리기 쉽습니다.", "[체험미션: 즉시재도전하기] '그래프만 남음(상황)-제가 확인(행동)' 흐름을 바로 다시 소리 내어 확인해 보세요.")),
                onePassage(PassageCategory.LISTENING, "이유 추론",
                        "여자: 오늘 왜 이렇게 피곤해 보여요?\n남자: 어제 마감 때문에 밤을 새워서요.",
                        q("남자가 피곤한 이유로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("밤을 새워서", "정답: '밤을 새워서'라고 직접 말했습니다."),
                                opt("운동을 많이 해서", "✍️ 언급되지 않은 이유입니다."),
                                opt("잠자리가 불편해서", "✍️ 언급되지 않은 이유입니다."),
                                opt("감기에 걸려서", "✍️ 언급되지 않은 이유입니다.")
                        ), 0, "✍️ 이유를 나타내는 문장을 손으로 표시하지 않으면 다른 추측성 이유와 헷갈리기 쉽습니다.", "[체험미션: 직접써보기] '마감(이유)-밤새움(행동)-피곤함(결과)'을 노트에 화살표로 직접 옮겨 적어 보세요.")),
                onePassage(PassageCategory.LISTENING, "장소 추론",
                        "남자: 이 원단으로 셔츠를 맞추고 싶은데요.\n여자: 네, 치수 먼저 재드릴게요. 이쪽으로 오세요.",
                        q("두 사람이 있는 곳으로 가장 알맞은 곳을 고르십시오.", List.of(
                                opt("맞춤 양복점", "정답: 원단으로 셔츠를 맞추고 치수를 잰다는 내용으로 보아 맞춤 양복점입니다."),
                                opt("세탁소", "👆 세탁이 아니라 맞춤 제작을 하는 상황입니다."),
                                opt("미용실", "👆 언급되지 않은 장소입니다."),
                                opt("서점", "👆 언급되지 않은 장소입니다.")
                        ), 0, "👆 '원단', '치수 재기'라는 단서를 놓치면 다른 장소로 착각하기 쉽습니다.", "[체험미션: 손가락짚기] '원단으로 셔츠(1)'와 '치수 재기(2)'에 손가락을 짚으며 장소를 추론해 보세요.")),
                onePassage(PassageCategory.LISTENING, "의견 제시",
                        "여자: 요즘 온라인 강의가 많아졌는데 어떻게 생각해요?\n남자: 저는 시간과 장소에 구애받지 않는다는 점에서 긍정적으로 봐요.",
                        q("남자의 의견으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("온라인 강의에 긍정적이다.", "정답: '긍정적으로 봐요'라는 말과 뒤이은 이유가 근거입니다."),
                                opt("온라인 강의에 부정적이다.", "🗣️ 남자의 말과 반대되는 내용입니다."),
                                opt("온라인 강의는 효과가 없다고 생각한다.", "🗣️ 언급되지 않은 내용입니다."),
                                opt("오프라인 강의만 들어야 한다고 생각한다.", "🗣️ 언급되지 않은 내용입니다.")
                        ), 0, "🗣️ 이유 설명에만 집중하면 정작 찬반 입장 자체를 놓치기 쉽습니다.", "[체험미션: 역할연기하기] '긍정적(입장)-시간·장소 자유(이유)'를 두 사람 역할로 나눠 소리 내어 연기해 보세요.")),
                onePassage(PassageCategory.LISTENING, "세부 정보 파악",
                        "남자: 이 제품 보증 기간이 어떻게 되나요?\n여자: 구매일로부터 이 년입니다. 다만 소모품은 보증에서 제외됩니다.",
                        q("이 제품의 보증 기간으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("구매일로부터 이 년", "정답: '구매일로부터 이 년'이라고 명시되어 있습니다."),
                                opt("구매일로부터 일 년", "✍️ 언급되지 않은 기간입니다."),
                                opt("구매일로부터 삼 년", "✍️ 언급되지 않은 기간입니다."),
                                opt("평생 보증", "✍️ 언급되지 않은 내용입니다.")
                        ), 0, "✍️ 보증 기간과 예외 조건(소모품 제외)을 함께 옮겨 적지 않으면 헷갈리기 쉽습니다.", "[체험미션: 직접써보기] '이 년 보증(기간)+소모품 제외(예외)'를 노트에 직접 옮겨 적어 보세요."))
        );

        List<PassageSeed> reading1to10 = List.of(
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 프로젝트는 여러 부서가 함께 참여하는 만큼 (        ) 소통이 무엇보다 중요하다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("원활한", "정답: 여러 부서가 함께 참여하는 상황에서는 원활한 소통이 중요하다는 문맥에 자연스럽습니다."),
                                opt("불필요한", "👆 소통이 중요하다는 문맥과 반대됩니다."),
                                opt("일방적인", "👆 여러 부서 협업이라는 문맥과 어울리지 않습니다."),
                                opt("형식적인", "👆 소통의 중요성을 강조하는 문맥과 어울리지 않습니다.")
                        ), 0, "⏱️ '무엇보다 중요하다'는 강조 표현 앞에는 긍정적 수식어가 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '여러 부서 참여(상황)-원활한 소통(핵심)' 흐름을 바로 다시 소리 내어 말해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "회사는 직원들의 복지를 (        ) 다양한 프로그램을 새로 도입하기로 했다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("향상시키기 위해", "정답: 새 프로그램 도입의 목적으로 복지 향상이 자연스럽습니다."),
                                opt("무시하기 위해", "👆 복지 프로그램 도입이라는 결과와 반대되는 목적입니다."),
                                opt("축소하기 위해", "👆 프로그램을 새로 도입한다는 내용과 반대됩니다."),
                                opt("폐지하기 위해", "👆 도입한다는 내용과 반대됩니다.")
                        ), 0, "⏱️ '새로 도입하다'라는 긍정적 행동의 목적으로 향상·개선 표현이 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '복지 향상(목적)-새 프로그램 도입(행동)' 흐름을 바로 다시 확인해 보세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "최근 한 조사에 따르면 직장인들이 이직을 고려하는 가장 큰 이유는 급여가 아니라 조직 문화인 것으로 나타났다. 아무리 급여가 높아도 소통이 원활하지 않은 조직에서는 오래 근무하기 어렵다는 것이다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("조직 문화가 급여보다 이직 결정에 더 큰 영향을 미친다.", "정답: 조사 결과와 뒤이은 설명이 이를 뒷받침합니다."),
                                opt("급여가 이직의 가장 큰 이유이다.", "👆 조사 결과와 반대되는 내용입니다."),
                                opt("이직은 항상 나쁜 선택이다.", "👆 언급되지 않은 내용입니다."),
                                opt("소통은 이직과 관련이 없다.", "👆 글의 내용과 반대됩니다.")
                        ), 0, "👆 세부 조사 내용에만 집중하면 글 전체의 핵심 결론을 놓치기 쉽습니다.", "[체험미션: 손가락짚기] '급여 아님(부정)'과 '조직 문화(핵심 원인)'에 손가락을 짚으며 중심 내용을 확인해 보세요.")),
                onePassage(PassageCategory.READING, "필자의 태도 파악",
                        "일부에서는 인공지능이 일자리를 빼앗을 것이라며 우려하지만, 필자는 오히려 인공지능이 반복적인 업무를 대신함으로써 인간이 더 창의적인 일에 집중할 기회를 줄 것이라고 본다.",
                        q("필자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("인공지능 도입에 대해 긍정적으로 전망하고 있다.", "정답: '창의적인 일에 집중할 기회를 줄 것'이라는 긍정적 전망을 제시하고 있습니다."),
                                opt("인공지능 도입을 강하게 우려하고 있다.", "👆 필자는 우려하는 일부 의견과 다른 입장입니다."),
                                opt("인공지능에 대해 무관심하다.", "👆 명확한 전망을 제시했으므로 무관심이 아닙니다."),
                                opt("인공지능 도입에 반대하고 있다.", "👆 필자의 입장과 반대됩니다.")
                        ), 0, "👆 '일부에서는 우려하지만'이라는 반대 의견을 필자 본인의 생각으로 착각하기 쉽습니다.", "[체험미션: 손가락짚기] '일부의 우려(타인 의견)'와 '필자는 오히려(반박)'에 손가락을 짚으며 태도를 확인해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 정책은 시행 초기에 (        ) 시간이 지나면서 점차 긍정적인 평가를 받고 있다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("반발이 있었으나", "정답: '점차 긍정적인 평가'라는 결과로 볼 때 초기에는 부정적 반응이 있었다는 대조가 자연스럽습니다."),
                                opt("환영을 받았으며", "👆 이미 긍정적이었다면 '점차 긍정적'이라는 변화 표현과 어울리지 않습니다."),
                                opt("무시당했으나", "👆 정책 시행과 직접 관련 없는 표현입니다."),
                                opt("성공적이었으며", "👆 이미 성공적이었다면 '점차'라는 변화 표현과 어울리지 않습니다.")
                        ), 0, "⏱️ '점차 긍정적으로 변했다'는 결과 앞에는 초기의 부정적 반응이 와야 한다는 대조 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '초기 반발(과거)-점차 긍정 평가(현재)' 대조를 바로 다시 소리 내어 말해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "그는 실패를 (        ) 다시 도전하는 모습을 보여 주었다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("두려워하지 않고", "정답: 다시 도전했다는 결과로 볼 때 실패를 두려워하지 않았다는 문맥이 자연스럽습니다."),
                                opt("두려워하며", "👆 다시 도전했다는 결과와 어울리지 않습니다."),
                                opt("무시한 채", "👆 실패를 인정하지 않았다는 의미로 문맥과 어울리지 않습니다."),
                                opt("피하기 위해", "👆 다시 도전했다는 결과와 반대됩니다.")
                        ), 0, "⏱️ '다시 도전했다'는 결과의 원인으로 긍정적 태도(두려워하지 않음)가 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '실패 두려워하지 않음(태도)-다시 도전(결과)' 흐름을 바로 다시 확인해 보세요.")),
                onePassage(PassageCategory.READING, "문장 순서 배열",
                        "다음을 순서에 맞게 배열한 것을 고르십시오.\n(가) 그 결과 매출이 전년 대비 두 배로 늘었다.\n(나) 회사는 작년부터 온라인 판매 채널을 확대했다.\n(다) 이는 온라인 채널 확대 전략이 성공했음을 보여 준다.",
                        q("문장 순서로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("(나) - (가) - (다)", "정답: 전략 시행(나) → 결과(가) → 해석(다) 순서가 자연스럽습니다."),
                                opt("(가) - (나) - (다)", "⏱️ 결과가 전략 시행보다 먼저 나오면 흐름이 어색합니다."),
                                opt("(다) - (나) - (가)", "⏱️ 해석이 먼저 나오고 전략·결과가 뒤에 오면 논리적으로 어색합니다."),
                                opt("(나) - (다) - (가)", "⏱️ 해석(다)이 결과(가)보다 먼저 나오면 순서가 어색합니다.")
                        ), 0, "⏱️ '그 결과'와 '이는'이라는 두 접속 표현의 순서를 혼동하기 쉽습니다.", "[체험미션: 즉시재도전하기] '전략 시행(나)-그 결과 매출 증가(가)-성공 증명(다)' 순서를 바로 다시 확인해 보세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "많은 기업이 신입사원 채용 시 스펙보다 실무 역량을 중시하는 방향으로 전환하고 있다. 이는 단순한 이력보다 실제 업무 수행 능력이 조직 성과에 더 직결된다는 인식이 확산되었기 때문이다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("기업의 채용 기준이 스펙에서 실무 역량 중심으로 바뀌고 있다.", "정답: 첫 문장과 이유 설명이 중심 내용을 뒷받침합니다."),
                                opt("스펙은 여전히 가장 중요한 채용 기준이다.", "👆 글의 내용과 반대됩니다."),
                                opt("신입사원 채용은 점점 줄어들고 있다.", "👆 언급되지 않은 내용입니다."),
                                opt("실무 역량은 채용과 관련이 없다.", "👆 글의 내용과 반대됩니다.")
                        ), 0, "👆 이유 설명(성과에 직결)에만 집중하면 앞의 핵심 변화(채용 기준 전환)를 놓치기 쉽습니다.", "[체험미션: 손가락짚기] '스펙에서 실무 역량으로(변화)'와 '성과 직결(이유)'에 손가락을 짚으며 확인해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "그 배우는 대사를 완벽하게 소화하기 위해 (        ) 대본을 수십 번 반복해서 읽었다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("매일같이", "정답: 수십 번 반복해서 읽었다는 결과로 볼 때 꾸준한 반복을 나타내는 표현이 자연스럽습니다."),
                                opt("한 번도", "👆 부정 표현과 어울리는 부사로 반복해서 읽었다는 내용과 반대됩니다."),
                                opt("전혀", "👆 부정문에 어울리는 부사로 문맥과 안 맞습니다."),
                                opt("가끔씩만", "👆 수십 번 반복이라는 강도와 어울리지 않습니다.")
                        ), 0, "⏱️ '수십 번 반복했다'는 결과의 원인으로 강한 빈도 부사가 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '매일같이(빈도)-수십 번 반복(결과)' 흐름을 바로 다시 소리 내어 말해 보세요.")),
                onePassage(PassageCategory.READING, "필자의 태도 파악",
                        "재택근무가 확산되면서 일과 삶의 균형이 개선되었다는 평가가 있지만, 필자는 오히려 업무와 사생활의 경계가 모호해져 장기적으로는 피로도가 높아질 수 있다고 본다.",
                        q("필자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("재택근무 확산에 대해 우려를 나타내고 있다.", "정답: '피로도가 높아질 수 있다'는 우려를 제시하고 있습니다."),
                                opt("재택근무를 적극적으로 지지하고 있다.", "👆 필자는 긍정적 평가와 다른 입장입니다."),
                                opt("재택근무에 대해 무관심하다.", "👆 명확한 우려를 제시했으므로 무관심이 아닙니다."),
                                opt("재택근무가 완전히 실패했다고 본다.", "👆 언급되지 않은 내용입니다.")
                        ), 0, "👆 '균형이 개선되었다는 평가'라는 타인 의견을 필자 본인의 생각으로 착각하기 쉽습니다.", "[체험미션: 손가락짚기] '타인 평가(긍정)'와 '필자는 오히려(반박·우려)'에 손가락을 짚으며 태도를 확인해 보세요."))
        );

        List<PassageSeed> reading11to20 = List.of(
                onePassage(PassageCategory.READING, "문장 순서 배열",
                        "다음을 순서에 맞게 배열한 것을 고르십시오.\n(가) 그러나 최근 조사에서는 오히려 역효과가 있다는 결과가 나왔다.\n(나) 오랫동안 야근이 생산성을 높인다는 인식이 있었다.\n(다) 이에 따라 많은 기업이 근무 시간 단축을 검토하고 있다.",
                        q("문장 순서로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("(나) - (가) - (다)", "정답: 기존 인식(나) → 반박 결과(가) → 대응 방안(다) 순서가 자연스럽습니다."),
                                opt("(가) - (나) - (다)", "⏱️ 반박 결과가 기존 인식보다 먼저 나오면 흐름이 어색합니다."),
                                opt("(다) - (나) - (가)", "⏱️ 대응 방안이 먼저 나오고 원인이 뒤에 오면 논리적으로 어색합니다."),
                                opt("(나) - (다) - (가)", "⏱️ 대응 방안(다)이 반박 결과(가)보다 먼저 나오면 순서가 어색합니다.")
                        ), 0, "⏱️ '그러나'와 '이에 따라'라는 두 접속 표현의 순서를 혼동하기 쉽습니다.", "[체험미션: 즉시재도전하기] '기존 인식(나)-그러나 반박(가)-대응 방안(다)' 순서를 바로 다시 확인해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 제도는 여러 시행착오를 거쳐 (        ) 지금의 안정적인 형태로 자리 잡았다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("점진적으로", "정답: 여러 시행착오를 거쳐 안정적으로 자리 잡았다는 문맥에 자연스러운 과정 표현입니다."),
                                opt("갑작스럽게", "👆 여러 시행착오를 거쳤다는 점진적 과정과 반대됩니다."),
                                opt("일시적으로", "👆 지금의 안정적인 형태로 자리 잡았다는 결과와 어울리지 않습니다."),
                                opt("무작위로", "👆 시행착오를 거친 체계적 과정과 어울리지 않습니다.")
                        ), 0, "⏱️ '여러 시행착오를 거쳐'라는 과정 표현 뒤에는 점진적 변화를 나타내는 부사가 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '시행착오(과정)-점진적으로(속도)-안정적 형태(결과)' 흐름을 바로 다시 소리 내어 말해 보세요.")),
                onePassage(PassageCategory.READING, "실용문 독해",
                        "[사내 공모전 안내]\n주제: 업무 효율화 아이디어\n접수 기간: 이번 달 15일~30일\n제출 방법: 사내 포털을 통해 온라인 제출\n우수작에는 포상금이 지급됩니다.",
                        q("이 안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("접수는 온라인으로만 가능하다.", "정답: '사내 포털을 통해 온라인 제출'이라는 안내와 일치합니다."),
                                opt("주제는 자유 주제이다.", "👆 업무 효율화 아이디어로 주제가 정해져 있으므로 틀린 정보입니다."),
                                opt("접수 기간은 한 달이다.", "👆 15일~30일이므로 보름간이며 틀린 정보입니다."),
                                opt("우수작에는 상장만 수여된다.", "👆 포상금이 지급된다고 했으므로 틀린 정보입니다.")
                        ), 0, "👆 '온라인 제출'이라는 방법 조건을 놓치면 오프라인 제출도 가능하다고 오해하기 쉽습니다.", "[체험미션: 손가락짚기] '접수 기간(조건1)+온라인 제출(조건2)+포상금(혜택)'에 손가락을 짚으며 확인해 보세요.")),
                onePassage(PassageCategory.READING, "필자의 태도 파악",
                        "구독 경제가 확산되면서 소비자의 선택권이 넓어졌다는 긍정적 시각이 많지만, 필자는 오히려 불필요한 지출이 누적되어 가계에 부담을 줄 수 있다는 점을 지적하고 싶다.",
                        q("필자의 태도로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("구독 경제 확산의 부작용을 우려하고 있다.", "정답: '가계에 부담을 줄 수 있다'는 우려를 제시하고 있습니다."),
                                opt("구독 경제를 적극적으로 지지하고 있다.", "👆 필자는 긍정적 시각과 다른 입장입니다."),
                                opt("구독 경제에 대해 무관심하다.", "👆 명확한 우려를 제시했으므로 무관심이 아닙니다."),
                                opt("구독 경제가 완전히 사라져야 한다고 본다.", "👆 언급되지 않은 내용입니다.")
                        ), 0, "👆 '선택권이 넓어졌다는 긍정적 시각'이라는 타인 의견을 필자 본인의 생각으로 착각하기 쉽습니다.", "[체험미션: 손가락짚기] '타인 시각(긍정)'과 '필자는 오히려(우려)'에 손가락을 짚으며 태도를 확인해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "그는 협상 중 상대방의 입장을 (        ) 최선의 합의점을 찾아냈다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("충분히 고려하여", "정답: 최선의 합의점을 찾았다는 결과로 볼 때 상대방 입장을 고려했다는 문맥이 자연스럽습니다."),
                                opt("완전히 무시하여", "👆 최선의 합의점을 찾았다는 결과와 반대됩니다."),
                                opt("전혀 신경 쓰지 않고", "👆 합의점을 찾았다는 결과와 어울리지 않습니다."),
                                opt("일방적으로 강요하여", "👆 합의점을 찾았다는 결과와 어울리지 않습니다.")
                        ), 0, "⏱️ '최선의 합의점을 찾았다'는 결과의 원인으로 긍정적 태도가 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '상대방 입장 고려(태도)-최선의 합의(결과)' 흐름을 바로 다시 확인해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이 지역은 교통이 (        ) 최근 몇 년간 인구가 꾸준히 증가하고 있다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("편리해지면서", "정답: 인구가 증가했다는 결과로 볼 때 교통이 편리해졌다는 원인이 자연스럽습니다."),
                                opt("불편해지면서", "👆 인구 증가라는 결과와 반대되는 원인입니다."),
                                opt("복잡해지면서", "👆 인구 증가라는 긍정적 결과와 어울리지 않습니다."),
                                opt("사라지면서", "👆 교통이 사라진다는 것은 문맥상 어색합니다.")
                        ), 0, "⏱️ '인구가 증가했다'는 결과의 원인으로 긍정적 변화(편리해짐)가 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '교통 편리해짐(원인)-인구 증가(결과)' 흐름을 바로 다시 소리 내어 말해 보세요.")),
                onePassage(PassageCategory.READING, "문장 순서 배열",
                        "다음을 순서에 맞게 배열한 것을 고르십시오.\n(가) 그 결과 고객 만족도가 크게 상승했다.\n(나) 이는 고객 중심 서비스 전략이 효과적이었음을 시사한다.\n(다) 회사는 고객 불만 접수 후 처리 절차를 대폭 개선했다.",
                        q("문장 순서로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("(다) - (가) - (나)", "정답: 개선 조치(다) → 결과(가) → 해석(나) 순서가 자연스럽습니다."),
                                opt("(가) - (다) - (나)", "⏱️ 결과가 개선 조치보다 먼저 나오면 흐름이 어색합니다."),
                                opt("(나) - (다) - (가)", "⏱️ 해석이 먼저 나오고 조치·결과가 뒤에 오면 논리적으로 어색합니다."),
                                opt("(다) - (나) - (가)", "⏱️ 해석(나)이 결과(가)보다 먼저 나오면 순서가 어색합니다.")
                        ), 0, "⏱️ '그 결과'와 '이는'이라는 두 접속 표현의 순서를 혼동하기 쉽습니다.", "[체험미션: 즉시재도전하기] '절차 개선(다)-그 결과 만족도 상승(가)-전략 효과 증명(나)' 순서를 바로 다시 확인해 보세요.")),
                onePassage(PassageCategory.READING, "중심 내용 파악",
                        "전문가들은 기후 변화 대응을 위해 개인의 실천도 중요하지만, 근본적으로는 산업 구조 전환과 정책적 지원이 병행되어야 실질적인 효과를 볼 수 있다고 강조한다.",
                        q("이 글의 중심 내용으로 가장 알맞은 것을 고르십시오.", List.of(
                                opt("기후 변화 대응은 개인 실천과 함께 산업·정책적 전환이 병행되어야 한다.", "정답: 마지막 문장이 전문가들의 핵심 주장입니다."),
                                opt("개인의 실천만으로 기후 변화를 막을 수 있다.", "👆 근본적으로는 산업·정책 전환이 필요하다고 했으므로 정확한 요약이 아닙니다."),
                                opt("기후 변화 대응은 불가능하다.", "👆 언급되지 않은 내용입니다."),
                                opt("정책적 지원은 필요하지 않다.", "👆 글의 내용과 반대됩니다.")
                        ), 0, "👆 '개인의 실천도 중요하다'는 부분만 보면 그것만으로 충분하다고 오해하기 쉽습니다.", "[체험미션: 손가락짚기] '개인 실천(부분)'과 '산업·정책 전환 병행(핵심)'에 손가락을 짚으며 중심 내용을 확인해 보세요.")),
                onePassage(PassageCategory.READING, "빈칸에 알맞은 것 고르기",
                        "이번 신제품은 기존 제품과 (        ) 완전히 새로운 방식으로 설계되었다.",
                        q("빈칸에 알맞은 것을 고르십시오.", List.of(
                                opt("달리", "정답: '완전히 새로운 방식'이라는 결과와 대조되는 기존 제품과의 차이를 나타내는 표현이 자연스럽습니다."),
                                opt("똑같이", "👆 완전히 새로운 방식이라는 내용과 반대됩니다."),
                                opt("비슷하게", "👆 완전히 새로운 방식이라는 내용과 반대됩니다."),
                                opt("동일하게", "👆 완전히 새로운 방식이라는 내용과 반대됩니다.")
                        ), 0, "⏱️ '완전히 새로운 방식'이라는 결과 앞에는 기존과의 차이를 나타내는 표현이 와야 한다는 패턴을 놓치기 쉽습니다.", "[체험미션: 즉시재도전하기] '기존과 다름(대조)-새로운 방식(결과)' 흐름을 바로 다시 소리 내어 말해 보세요.")),
                onePassage(PassageCategory.READING, "실용문 독해",
                        "[사무실 이전 안내]\n다음 달 첫째 주 월요일부터 본사 사무실이 이전됩니다.\n새 주소: 서울시 강남구 테헤란로 100\n택배 및 우편물은 이전 완료 후부터 새 주소로 보내 주세요.",
                        q("이 안내문의 내용과 같은 것을 고르십시오.", List.of(
                                opt("이전 완료 전에는 기존 주소로 우편물을 보내야 한다.", "정답: '이전 완료 후부터 새 주소로'라는 안내로 볼 때 완료 전에는 기존 주소가 맞습니다."),
                                opt("사무실은 이번 달에 이전한다.", "👆 다음 달이라고 했으므로 틀린 정보입니다."),
                                opt("우편물은 항상 새 주소로 보내야 한다.", "👆 이전 완료 후부터라는 조건이 있으므로 정확한 정보가 아닙니다."),
                                opt("이전 후 주소는 안내되지 않았다.", "👆 새 주소가 명시되어 있으므로 틀린 정보입니다.")
                        ), 0, "👆 '이전 완료 후부터'라는 시점 조건을 놓치면 언제나 새 주소를 써야 한다고 오해하기 쉽습니다.", "[체험미션: 손가락짚기] '이전 시점(조건1)+새 주소(정보2)+완료 후 발송(조건3)'에 손가락을 짚으며 확인해 보세요."))
        );

        return new WeekSeed("WEEK 1: 3~4급 체험 기초 다지기",
                "매일 40문항씩 직접 풀고, 쓰고, 소리 내어 말하며 TOPIK 3~4급 유형(의견 제시, 화자의 태도 파악 등)을 몸으로 익힙니다.",
                WEEK1_ANSWER_NOTE_TEMPLATE,
                List.of(
                        day("1차(40문항) - 듣기 20(의견 제시, 세부 정보 파악, 화자의 태도 파악, 이어질 행동, 중심 생각, 장소·이유 추론, 화자의 의도, 일치하는 내용) + 읽기 20(빈칸, 중심 내용, 필자의 태도, 문장 순서, 실용문 독해). 체험 활동 태그로 오답을 표시하고 체험미션을 직접 실행하세요.",
                                merge(listening1to10, listening11to20, reading1to10, reading11to20))
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
