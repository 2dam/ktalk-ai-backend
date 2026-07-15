package com.ktalk.domain.ai.service;

import com.ktalk.domain.content.entity.Content;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MockAIService {

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

    public String generateDialogue(Content content) {
        String level = content.getKoreanLevel();
        String title = content.getTitle();

        // 레벨별 대화문 템플릿
        if (level.equals("beginner")) {
            return generateBeginnerDialogue(title);
        } else if (level.equals("intermediate")) {
            return generateIntermediateDialogue(title);
        } else if (level.equals("advanced")) {
            return generateAdvancedDialogue(title);
        }

        return generateBeginnerDialogue(title);
    }

    private String generateBeginnerDialogue(String title) {
        return """
            [대화문 - 초급]
            
            A: 안녕하세요!
            B: 네, 안녕하세요! 반갑습니다.
            A: 이름이 뭐예요?
            B: 저는 김민수예요. 당신은요?
            A: 저는 박지영이에요. 처음 뵙겠습니다.
            B: 네, 잘 부탁드립니다.
            A: 감사합니다. 좋은 하루 보내세요!
            B: 네, 감사합니다!
            """;
    }

    private String generateIntermediateDialogue(String title) {
        return """
            [대화문 - 중급]
            
            A: 안녕하세요, 어디 가세요?
            B: 도서관에 가려고 해요. 한국어 공부를 하려고요.
            A: 아, 그렇군요. 저는 오늘 한국 드라마를 볼 거예요.
            B: 어떤 드라마를 좋아하세요?
            A: 저는 로맨틱 코미디를 좋아해요. 재미있어요.
            B: 저도 좋아해요. 추천해 줄 드라마가 있어요.
            A: 정말요? 뭐예요?
            B: '사랑의 불시착'이에요. 정말 재미있어요.
            A: 알겠어요. 꼭 볼게요. 감사합니다!
            """;
    }

    private String generateAdvancedDialogue(String title) {
        return """
            [대화문 - 고급]
            
            A: 안녕하세요, 김부장님. 오늘 회의 준비는 잘 되셨나요?
            B: 네, 어느 정도 준비는 했습니다만, 아직 보완할 부분이 있는 것 같습니다.
            A: 어떤 부분이 걱정되십니까?
            B: 예산 관련 자료인데, 최신 데이터를 반영해야 할 것 같아요.
            A: 알겠습니다. 제가 오후까지 최신 통계를 조사해 오겠습니다.
            B: 감사합니다. 그리고 발표 자료도 함께 검토해 주시면 좋겠어요.
            A: 네, 알겠습니다. 3시까지 완료해서 보내드리겠습니다.
            B: 수고 많습니다. 좋은 결과 있기를 바랍니다.
            """;
    }
}