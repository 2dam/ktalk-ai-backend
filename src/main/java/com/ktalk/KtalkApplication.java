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
        setIfPresent(dotenv, "GOOGLE_TTS_LANGUAGE");
        setIfPresent(dotenv, "GOOGLE_TTS_MALE_VOICE");
        setIfPresent(dotenv, "GOOGLE_TTS_FEMALE_VOICE");
        setIfPresent(dotenv, "STRIPE_SECRET_KEY");
        setIfPresent(dotenv, "STRIPE_WEBHOOK_SECRET");
        setIfPresent(dotenv, "STRIPE_PRO_PRICE_ID");
        setIfPresent(dotenv, "STRIPE_BUSINESS_PRICE_ID");
        setIfPresent(dotenv, "BILLING_PRO_MONTHLY_PRICE_USD");
        setIfPresent(dotenv, "BILLING_BUSINESS_MONTHLY_PRICE_USD");
        setIfPresent(dotenv, "FRONTEND_URL");

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
