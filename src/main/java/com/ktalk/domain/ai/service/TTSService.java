package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import reactor.core.publisher.Flux;

@Service
@Slf4j
@RequiredArgsConstructor
public class TTSService {

    private static final int MAX_TEXT_LENGTH = 3000;
    private static final int CACHE_SIZE = 500;
    private static final String MALE_VOICE = "ko-KR-Chirp3-HD-Charon";
    private static final String FEMALE_VOICE = "ko-KR-Chirp3-HD-Kore";
    private static final Pattern SPEAKER_LINE = Pattern.compile("^([A-Za-z]+):\\s*(.*)$");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${GOOGLE_TTS_API_KEY:}")
    private String apiKey;

    // 같은 문장을 다시 재생할 때 Google TTS를 또 호출하지 않도록 캐싱 (같은 대화문 재생이 가장 흔한 사용 패턴)
    private final Map<String, String> audioCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public String synthesize(String text, String gender) {
        String voiceName = "FEMALE".equalsIgnoreCase(gender) ? FEMALE_VOICE : MALE_VOICE;
        return synthesizeWithVoice(text, voiceName);
    }

    public String synthesizeWithVoice(String text, String voiceName) {
        String truncated = text.substring(0, Math.min(text.length(), MAX_TEXT_LENGTH));
        String cacheKey = voiceName + "|" + truncated;

        String cached = audioCache.get(cacheKey);
        if (cached != null) {
            log.info("TTS 캐시 적중: voice={}, textLength={}", voiceName, truncated.length());
            return cached;
        }

        Map<String, Object> requestBody = Map.of(
                "input", Map.of("text", truncated),
                "voice", Map.of(
                        "languageCode", "ko-KR",
                        "name", voiceName
                ),
                "audioConfig", Map.of(
                        "audioEncoding", "MP3",
                        "speakingRate", 0.95
                )
        );

        try {
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("texttospeech.googleapis.com")
                            .path("/v1/text:synthesize")
                            .queryParam("key", apiKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode audioContentNode = root.path("audioContent");
            if (audioContentNode.isMissingNode()) {
                throw new RuntimeException("TTS 응답에 audioContent가 없습니다: " + response);
            }

            String audioContent = audioContentNode.asText();
            audioCache.put(cacheKey, audioContent);
            log.info("TTS 합성 완료: textLength={}, voice={}", truncated.length(), voiceName);
            return audioContent;
        } catch (Exception e) {
            log.error("TTS API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("음성 합성 실패: " + e.getMessage());
        }
    }

    /**
     * 대화문을 화자(A/B)별로 나눠 각각 다른 성별 음성으로 합성한다.
     * A는 기본 남성, B는 기본 여성 (swap=true면 반대).
     */
    public List<Map<String, String>> synthesizeDialogue(String title, String description, String dialogue, boolean swap) {
        List<String[]> pending = new ArrayList<>(); // [text, gender]

        String intro = (title + ". " + description).trim();
        if (!intro.isEmpty()) {
            pending.add(new String[]{intro, "MALE"});
        }

        if (dialogue != null) {
            for (String rawLine : dialogue.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[") && line.endsWith("]")) continue; // 섹션 헤더 스킵

                Matcher matcher = SPEAKER_LINE.matcher(line);
                if (matcher.matches()) {
                    String speaker = matcher.group(1);
                    String text = matcher.group(2).trim();
                    if (text.isEmpty()) continue;
                    pending.add(new String[]{text, resolveGender(speaker, swap)});
                } else {
                    pending.add(new String[]{line, "MALE"});
                }
            }
        }

        List<String> audioContents = Flux.fromIterable(pending)
                .flatMapSequential(seg -> Mono.fromCallable(() -> synthesize(seg[0], seg[1]))
                        .subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .block();

        List<Map<String, String>> segments = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            Map<String, String> segment = new LinkedHashMap<>();
            segment.put("gender", pending.get(i)[1]);
            segment.put("audioContent", audioContents.get(i));
            segments.add(segment);
        }
        return segments;
    }

    private String resolveGender(String speaker, boolean swap) {
        boolean isA = "A".equalsIgnoreCase(speaker);
        boolean maleTurn = swap != isA;
        return maleTurn ? "MALE" : "FEMALE";
    }
}
