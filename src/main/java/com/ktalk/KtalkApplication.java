package com.ktalk;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KtalkApplication {

    public static void main(String[] args) {
        // .env 파일 로드
        Dotenv dotenv = Dotenv.load();
        System.setProperty("GOOGLE_CLIENT_ID", dotenv.get("GOOGLE_CLIENT_ID"));
        System.setProperty("GOOGLE_CLIENT_SECRET", dotenv.get("GOOGLE_CLIENT_SECRET"));
        System.setProperty("YOUTUBE_API_KEY", dotenv.get("YOUTUBE_API_KEY"));
        System.setProperty("GEMINI_API_KEY", dotenv.get("GEMINI_API_KEY"));

        SpringApplication.run(KtalkApplication.class, args);
    }
}