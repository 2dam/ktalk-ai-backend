package com.ktalk.domain.review.dto;

/**
 * 복습 카드 한 장을 채점한 결과.
 * quality 0~5 (SM-2). 프론트의 4단계 버튼과 매핑:
 * 다시=2, 어려움=3, 좋음=4, 쉬움=5.
 */
public record GradeReviewRequest(int quality) {}
