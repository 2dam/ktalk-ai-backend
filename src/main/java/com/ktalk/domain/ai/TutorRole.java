package com.ktalk.domain.ai;

/**
 * 역할 기반 AI 튜터의 역할 정의.
 *
 * <p>everyone-can-use-english(ecue)의 "스마트 어시스턴트" 방식을 한국어 학습용으로 차용한 것이다.
 * ecue는 사용자가 AI에 역할(role/persona)을 부여해 원하는 답을 얻는 패러다임을 쓴다.
 * 여기서는 GeminiService 위에 얇은 프롬프트 템플릿 레이어로만 존재하며, 기존 GeminiService 코드는
 * 전혀 건드리지 않는다(개방-폐쇄 원칙).
 *
 * <p>원문/코드 복사는 하지 않았다(GPL-3.0). 아이디어(역할 부여 패러다임)만 차용하고
 * 한국어 학습 도메인에 맞춰 독자 작성했다.
 */
public enum TutorRole {

    PRONUNCIATION_COACH(
            "발음 코치",
            "당신은 친절한 한국어 발음 코치입니다. 학습자가 제시한 한국어 문장의 발음을 평가하고, "
                    + "틀린 부분을 부드럽게 짚어주며 더 자연스러운 발음 팁을 줍니다. 예시 발음은 별도 TTS로 제공합니다."
    ),
    CONVERSATION_PARTNER(
            "회화 파트너",
            "당신은 학습자의 수준에 맞춰 천천히 말하는 한국어 원어민 친구입니다. "
                    + "어려운 단어는 피하고, 학습자가 이어갈 수 있게 짧은 질문으로 대화를 이끕니다. "
                    + "문법 오류를 고치기보다 의사소통이 먼저입니다."
    ),
    GRAMMAR_TUTOR(
            "문법 선생님",
            "당신은 인내심 많은 한국어 문법 선생님입니다. 학습자가 쓴 문장의 문법/어미/조사 오류를 "
                    + "규칙과 함께 명확히 설명하고, 비슷한 패턴의 예문을 2~3개 줍니다."
    ),
    COMPREHENSIBLE_INPUT_GUIDE(
            "이해 입력 가이드",
            "당신은 K-콘텐츠(드라마/유튜브) 기반 한국어 학습 안내자입니다. 학습자 수준에 맞는 "
                    + "원본 지문을 제시하고, 모르는 단어 없이 의미를 유추할 수 있게 단계별 힌트를 줍니다(천 시간 법칙의 입력 우선 원칙)."
    );

    private final String label;
    private final String systemPrompt;

    TutorRole(String label, String systemPrompt) {
        this.label = label;
        this.systemPrompt = systemPrompt;
    }

    public String getLabel() {
        return label;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }
}
