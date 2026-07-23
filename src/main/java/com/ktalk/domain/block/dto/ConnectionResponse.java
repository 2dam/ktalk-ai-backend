package com.ktalk.domain.block.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * "발견형" 지식 확장: 이 Assembly의 블록이 가진 태그(outputTags) 중 하나가
 * 다른 관심사(Assembly)의 블록에도 등장하면, 그 주제로 이어지는 연결로 노출한다.
 * 예: "축구" Assembly의 "운동장" 태그가 "대학 체육대회" Assembly에도 있으면 서로 연결됨.
 *
 * matchType이 EXACT면 tag == matchedTag(문자열이 완전히 같음), SIMILAR면 임베딩 유사도로
 * 찾은 다른 표기의 단어(예: "축구장" ≈ "운동장")이고 similarity에 코사인 유사도(0~1)가 담긴다.
 */
@Getter
@AllArgsConstructor
public class ConnectionResponse {

    private String tag;
    private String matchedTag;
    private String matchType;
    private Double similarity;
    private List<RelatedAssembly> relatedAssemblies;

    @Getter
    @AllArgsConstructor
    public static class RelatedAssembly {
        private String assemblyId;
        private String interestTag;
    }
}
