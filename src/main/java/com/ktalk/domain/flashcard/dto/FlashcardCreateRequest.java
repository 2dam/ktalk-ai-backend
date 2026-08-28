package com.ktalk.domain.flashcard.dto;

public record FlashcardCreateRequest(
        String front,
        String back,
        String audioBase64,
        String source
) {}
