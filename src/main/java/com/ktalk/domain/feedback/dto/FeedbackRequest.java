package com.ktalk.domain.feedback.dto;

public record FeedbackRequest(
        String category,
        String message,
        String page
) {}
