package com.ktalk.domain.review.dto;

import com.ktalk.domain.review.entity.ReviewItem;

import java.time.LocalDateTime;

/**
 * 복습 항목을 프론트로 내려줄 때 쓰는 응답 DTO (엔티티 직접 노출 방지).
 */
public record ReviewItemResponse(
        String id,
        String interest,
        String sentence,
        String meaning,
        String pattern,
        String sensoryWord,
        int repetitions,
        int intervalDays,
        double easeFactor,
        LocalDateTime nextReviewAt,
        LocalDateTime lastReviewedAt,
        int reviewCount,
        int correctCount,
        boolean due
) {
    public static ReviewItemResponse from(ReviewItem item, LocalDateTime now) {
        return new ReviewItemResponse(
                item.getId(),
                item.getInterest(),
                item.getSentence(),
                item.getMeaning(),
                item.getPattern(),
                item.getSensoryWord(),
                item.getRepetitions(),
                item.getIntervalDays(),
                item.getEaseFactor(),
                item.getNextReviewAt(),
                item.getLastReviewedAt(),
                item.getReviewCount(),
                item.getCorrectCount(),
                !item.getNextReviewAt().isAfter(now)
        );
    }
}
