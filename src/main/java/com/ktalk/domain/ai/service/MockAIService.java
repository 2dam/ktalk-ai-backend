package com.ktalk.domain.ai.service;

import com.ktalk.domain.content.entity.Content;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MockAIService implements AIService {

    @Override
    public Content generateContent(String topic) {
        Content content = new Content();

        Map<String, String[]> templates = new HashMap<>();
        templates.put("인사", new String[]{
                "한국어 인사말",
                "안녕하세요, 감사합니다, 죄송합니다 등 기본적인 한국어 인사말을 배워봅시다.",
                "beginner"
        });
        templates.put("문법", new String[]{
                "한국어 기본 문법",
                "주어+목적어+동사 구조, 조사 사용법 등 한국어의 기본 문법을 학습합니다.",
                "intermediate"
        });
        templates.put("문화", new String[]{
                "한국 문화 이해",
                "한국의 전통 문화, 현대 문화, 예절 등을 배워봅니다.",
                "beginner"
        });
        templates.put("여행", new String[]{
                "한국 여행 회화",
                "공항, 호텔, 식당 등에서 사용하는 실용적인 한국어 회화를 학습합니다.",
                "intermediate"
        });
        templates.put("비즈니스", new String[]{
                "비즈니스 한국어",
                "직장에서 사용하는 공식적인 한국어 표현과 이메일 작성법을 배웁니다.",
                "advanced"
        });

        String title = topic + " 학습";
        String description = topic + "에 관한 한국어 학습 콘텐츠입니다. 다양한 예문과 연습을 통해 실력을 향상시켜 보세요.";
        String level = "intermediate";
        String category = "korean";

        for (Map.Entry<String, String[]> entry : templates.entrySet()) {
            if (topic.contains(entry.getKey())) {
                title = entry.getValue()[0];
                description = entry.getValue()[1];
                level = entry.getValue()[2];
                break;
            }
        }

        content.setTitle(title);
        content.setDescription(description);
        content.setCategory(category);
        content.setKoreanLevel(level);

        return content;
    }
}