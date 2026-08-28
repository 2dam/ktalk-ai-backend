package com.ktalk.domain.ai.service;

import com.ktalk.domain.ai.TutorRole;
import com.ktalk.domain.ai.dto.TutorChatRequest;
import com.ktalk.domain.ai.dto.TutorChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * 역할 기반 튜터 서비스. ecue의 "AI에게 역할을 부여" 패러다임을 한국어 학습용으로 차용.
 * GeminiService의 텍스트 생성 경로를 그대로 재사용하고, 시스템 프롬프트(역할)만 앞에 붙인다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TutorService {

    private final GeminiApiClient geminiApiClient;
    private final WebClient webClient;

    @Value("${gemini.api.key}")
    private String apiKey;

    public TutorChatResponse chat(TutorChatRequest request) {
        TutorRole role = resolveRole(request.role());

        String prompt = """
                [역할]
                %s

                [맥락]
                %s

                [학습자 메시지]
                %s

                위 역할에 맞춰 한국어로, 학습자 수준에 맞게 친절하게 답하세요.
                """.formatted(
                role.getSystemPrompt(),
                request.context() == null || request.context().isBlank()
                        ? "(맥락 없음)" : request.context(),
                request.message() == null ? "" : request.message()
        );

        try {
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );
            String text = geminiApiClient.generateText(webClient, apiKey, requestBody);
            return new TutorChatResponse(role.name(), text, role.getLabel());
        } catch (Exception e) {
            log.error("튜터 응답 생성 실패: {}", e.getMessage(), e);
            return new TutorChatResponse(
                    role.name(),
                    "지금은 답변을 만들 수 없어요. 잠시 후 다시 시도해주세요.",
                    role.getLabel()
            );
        }
    }

    public List<TutorRole> listRoles() {
        return List.of(TutorRole.values());
    }

    private TutorRole resolveRole(String roleName) {
        if (roleName == null || roleName.isBlank()) return TutorRole.CONVERSATION_PARTNER;
        try {
            return TutorRole.valueOf(roleName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TutorRole.CONVERSATION_PARTNER;
        }
    }
}
