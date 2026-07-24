package com.ktalk.domain.topik.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.ai.service.GeminiTextService;
import com.ktalk.domain.topik.entity.TopikLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 단어의 TOPIK 난이도(1~6급 → 하/중/상급)를 LLM으로 추정한다.
 *
 * <p>정식 빈도 사전이 없는 상태라, "이 단어가 등장했을 때 학습자가 느낄 정보량(놀람도)"을
 * LLM의 상식적 판단으로 대신 추정하는 방식이다. 어휘 빈도가 낮을수록(=흔치 않을수록)
 * 상급으로 분류되도록 프롬프트에 기준을 명시했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopikLevelClassifierService {

    /** LLM 호출이 실패했을 때 쓰는 중립값. 관리자가 나중에 재분류할 수 있도록 로그를 남긴다. */
    private static final TopikLevel FALLBACK_LEVEL = TopikLevel.LEVEL_3;

    private final GeminiTextService geminiTextService;
    private final ObjectMapper objectMapper;

    public TopikLevel classify(String word, String meaning) {
        try {
            String response = geminiTextService.generate(buildPrompt(word, meaning));
            return parseLevel(response);
        } catch (Exception e) {
            log.warn("TOPIK 난이도 분류 실패, 폴백 등급({}) 사용: word='{}'",
                    FALLBACK_LEVEL.getDisplayName(), word, e);
            return FALLBACK_LEVEL;
        }
    }

    private String buildPrompt(String word, String meaning) {
        return """
                다음 한국어 단어가 TOPIK 시험에서 몇 급 수준인지 분류해줘.
                단어: %s
                뜻: %s

                TOPIK 등급 기준:
                - 1~2급(하급): 일상생활 기본 어휘, 인사, 숫자, 아주 흔한 명사/동사
                - 3~4급(중급): 사회생활·업무·뉴스에서 자주 쓰이는 어휘, 일부 추상적 표현
                - 5~6급(상급): 전문 용어, 학술/시사 어휘, 한자어 기반의 추상적·문어체 표현

                아래 형식의 JSON으로만 답해줘. 다른 설명은 붙이지 마.
                {"level": 1부터 6 사이의 정수}
                """.formatted(word, meaning == null ? "" : meaning);
    }

    private TopikLevel parseLevel(String response) {
        String cleaned = response.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new IllegalStateException("분류 응답 파싱 실패: " + response, e);
        }

        int grade = node.path("level").asInt(-1);
        if (grade < 1 || grade > 6) {
            throw new IllegalStateException("유효하지 않은 등급 값: " + response);
        }
        return TopikLevel.fromGrade(grade);
    }
}
