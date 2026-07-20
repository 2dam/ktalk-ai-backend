package com.ktalk.domain.review.dto;

/**
 * 언어 감각 단계를 마친 뒤, 방금 배운 문장을 복습 목록에 추가할 때 보내는 요청.
 */
public record SaveReviewItemRequest(
        String interest,
        String sentence,
        String meaning,
        String pattern,
        String sensoryWord
) {}
