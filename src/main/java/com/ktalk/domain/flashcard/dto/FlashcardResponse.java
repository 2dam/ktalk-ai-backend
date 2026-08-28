package com.ktalk.domain.flashcard.dto;

public record FlashcardResponse(
        Long id,
        String front,
        String back,
        String audioBase64,
        String source,
        int boxLevel,
        String nextReviewAt
) {}
