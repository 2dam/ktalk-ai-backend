package com.ktalk.domain.ai.dto;

public record TutorChatRequest(
        String role,     // TutorRole 이름 (예: "CONVERSATION_PARTNER")
        String message,  // 학습자가 보낸 메시지
        String context   // 선택: 현재 학습 중인 문장/지문 등 맥락
) {}
