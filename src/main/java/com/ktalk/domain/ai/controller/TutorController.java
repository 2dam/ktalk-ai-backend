package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.TutorRole;
import com.ktalk.domain.ai.dto.TutorChatRequest;
import com.ktalk.domain.ai.dto.TutorChatResponse;
import com.ktalk.domain.ai.service.TutorService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tutor")
@RequiredArgsConstructor
public class TutorController {

    private final TutorService tutorService;

    // 역할 목록 조회 (프론트 역할 선택 UI용)
    @GetMapping("/roles")
    public ApiResponse<List<Map<String, String>>> listRoles() {
        List<Map<String, String>> roles = tutorService.listRoles().stream()
                .map(r -> Map.of(
                        "id", r.name(),
                        "label", r.getLabel()
                ))
                .toList();
        return ApiResponse.success(roles, "튜터 역할 목록");
    }

    // 역할 기반 채팅
    @PostMapping("/chat")
    public ApiResponse<TutorChatResponse> chat(@RequestBody TutorChatRequest request) {
        try {
            if (request.message() == null || request.message().isBlank()) {
                return ApiResponse.error("메시지를 입력해주세요.");
            }
            TutorChatResponse response = tutorService.chat(request);
            return ApiResponse.success(response, "튜터 응답을 생성했습니다.");
        } catch (Exception e) {
            return ApiResponse.error("튜터 응답 실패: " + e.getMessage());
        }
    }
}
