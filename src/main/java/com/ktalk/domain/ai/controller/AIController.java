package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.service.AIService;
import com.ktalk.domain.content.entity.Content;
import com.ktalk.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AIController {

    private final AIService aiService;
    private final ContentRepository contentRepository;

    @PostMapping("/generate")
    public ResponseEntity<?> generateContent(@RequestBody Map<String, String> request) {
        try {
            String topic = request.get("topic");

            if (topic == null || topic.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "주제를 입력해주세요."
                ));
            }

            Content generatedContent = aiService.generateContent(topic);
            Content savedContent = contentRepository.save(generatedContent);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "AI가 콘텐츠를 생성했습니다!",
                    "content", Map.of(
                            "id", savedContent.getId(),
                            "title", savedContent.getTitle(),
                            "description", savedContent.getDescription(),
                            "category", savedContent.getCategory(),
                            "koreanLevel", savedContent.getKoreanLevel()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "AI 콘텐츠 생성 실패: " + e.getMessage()
            ));
        }
    }
}