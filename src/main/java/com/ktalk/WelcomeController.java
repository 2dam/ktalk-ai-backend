package com.ktalk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WelcomeController {

    @GetMapping("/api")
    public String welcome() {
        return "K-Talk AI Backend API is running.\n\n" +
                "API endpoints:\n" +
                "- GET /api/contents - List all contents\n" +
                "- GET /api/contents/{id} - Get a content item\n" +
                "- POST /api/contents - Create content";
    }
}
