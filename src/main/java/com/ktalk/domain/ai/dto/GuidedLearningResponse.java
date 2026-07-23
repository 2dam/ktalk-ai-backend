package com.ktalk.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Learning Navigation 1단계(유추)~3단계(언어 감각) 콘텐츠를 한 번에 담는다.
 * 관심사 입력 한 번으로 세 단계를 모두 진행할 수 있도록 Gemini에게 한 번에 생성시킨다.
 */
@Getter
@Setter
@AllArgsConstructor
public class GuidedLearningResponse {

    private String interest;               // 입력받은 관심사
    private String sentence;                // 관심사를 소재로 한 한국어 문장
    private List<String> hints;             // 뜻을 유추할 때 점진적으로 제공할 힌트 (모호함 -> 구체적)
    private String meaning;                 // 문장의 정답 뜻 (영어)
    private List<VocabItem> vocab;          // 문장을 구성하는 핵심 단어 풀이
    private String pattern;                 // 문장에서 추출한 문형 패턴
    private String patternExplanation;      // 그 패턴에 대한 참고 설명 (선생님 확인용)
    private String sensoryWord;             // 언어 감각 훈련에 쓸 핵심 단어
    private String sensoryImagery;          // 그 단어를 익힐 때 떠올릴 감각적 이미지/상황
    private String sensoryInstruction;      // 소리 내어 반복하라는 구체적 지시문
    private String assemblyId;              // 이 레슨을 구성하는 블록들을 묶은 Assembly ID (조회/진행상태 갱신용)

    // Jackson이 Gemini 응답(JsonNode)을 convertValue로 역직렬화하려면
    // 기본 생성자 + setter가 필요하다 (AllArgsConstructor만으로는 인식 못 함)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VocabItem {
        private String word;
        private String meaning;
    }
}
