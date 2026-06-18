package com.ktalk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WelcomeController {

    @GetMapping("/")
    public String welcome() {
        return "🎉 K-Talk AI Backend API 서버가 실행 중입니다!\n\n" +
                "API 엔드포인트:\n" +
                "- GET /api/contents - 모든 콘텐츠 조회\n" +
                "- GET /api/contents/{id} - 특정 콘텐츠 조회\n" +
                "- POST /api/contents - 새 콘텐츠 생성";
    }
}