package com.ktalk;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KtalkApplication {

    public static void main(String[] args) {
        // .env 파일 로드 (없어도 앱은 부팅되어야 하므로 ignoreIfMissing 사용)
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        setIfPresent(dotenv, "GOOGLE_CLIENT_ID");
        setIfPresent(dotenv, "GOOGLE_CLIENT_SECRET");
        setIfPresent(dotenv, "YOUTUBE_API_KEY");
        setIfPresent(dotenv, "GEMINI_API_KEY");
        setIfPresent(dotenv, "GOOGLE_TTS_API_KEY");

        SpringApplication.run(KtalkApplication.class, args);
    }

    // .env에 값이 없으면 System property를 건드리지 않는다.
    // (System.setProperty에 null을 넘기면 NPE가 발생하고, application.properties의 기본값(${VAR:default})이 대신 적용된다)
    private static void setIfPresent(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null) {
            System.setProperty(key, value);
        }
    }
}