package com.ktalk.domain.ai.dto;

public record TutorChatResponse(
        String role,
        String reply,
        String label
) {}
