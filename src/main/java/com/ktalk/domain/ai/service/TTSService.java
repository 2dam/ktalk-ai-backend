package com.ktalk.domain.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class TTSService {

    private static final int MAX_TEXT_LENGTH = 3000;
    private static final int CACHE_SIZE = 500;
    private static final String CLOVA_TTS_HOST = "naveropenapi.apigw.ntruss.com";
    private static final String CLOVA_TTS_PATH = "/tts-premium/v1/tts";
    private static final String MALE_VOICE = "jinho";
    private static final String FEMALE_VOICE = "nara";
    private static final Pattern SPEAKER_LINE = Pattern.compile("^([A-Za-z]+):\\s*(.*)$");

    private final WebClient webClient;

    @Value("${NAVER_CLOVA_VOICE_CLIENT_ID:${NAVER_CLOVA_CLIENT_ID:}}")
    private String clovaClientId;

    @Value("${NAVER_CLOVA_VOICE_CLIENT_SECRET:${NAVER_CLOVA_CLIENT_SECRET:}}")
    private String clovaClientSecret;

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
        String speaker = resolveClovaSpeaker(voiceName);
        String cacheKey = speaker + "|" + truncated;

        String cached = audioCache.get(cacheKey);
        if (cached != null) {
            log.info("TTS cache hit: speaker={}, textLength={}", speaker, truncated.length());
            return cached;
        }

        String clientId = firstPresent(
                clovaClientId,
                System.getenv("NAVER_CLOVA_VOICE_CLIENT_ID"),
                System.getenv("NAVER_CLOVA_CLIENT_ID")
        );
        String clientSecret = firstPresent(
                clovaClientSecret,
                System.getenv("NAVER_CLOVA_VOICE_CLIENT_SECRET"),
                System.getenv("NAVER_CLOVA_CLIENT_SECRET")
        );

        if (clientId == null || clientSecret == null) {
            log.error("CLOVA Voice credentials are missing. idPresent={}, secretPresent={}",
                    clientId != null, clientSecret != null);
            throw new RuntimeException("CLOVA Voice API credentials are missing.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("speaker", speaker);
        form.add("volume", "0");
        form.add("speed", "-1");
        form.add("pitch", "0");
        form.add("format", "mp3");
        form.add("text", truncated);

        try {
            byte[] audioBytes = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(CLOVA_TTS_HOST)
                            .path(CLOVA_TTS_PATH)
                            .build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (audioBytes == null || audioBytes.length == 0) {
                throw new RuntimeException("CLOVA Voice returned empty audio.");
            }

            String audioContent = Base64.getEncoder().encodeToString(audioBytes);
            audioCache.put(cacheKey, audioContent);
            log.info("CLOVA Voice synthesis completed: textLength={}, speaker={}", truncated.length(), speaker);
            return audioContent;
        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("CLOVA Voice API failed: status={}, body={}", e.getStatusCode(), responseBody, e);
            throw new RuntimeException("Voice synthesis failed: CLOVA Voice " + e.getStatusCode() + " " + responseBody);
        } catch (Exception e) {
            log.error("CLOVA Voice API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Voice synthesis failed: " + e.getMessage());
        }
    }

    public List<Map<String, String>> synthesizeDialogue(String title, String description, String dialogue, boolean swap) {
        List<String[]> pending = new ArrayList<>();

        String intro = (title + ". " + description).trim();
        if (!intro.isEmpty()) {
            pending.add(new String[]{intro, "MALE", "narrator"});
        }

        if (dialogue != null) {
            for (String rawLine : dialogue.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[") && line.endsWith("]")) continue;

                Matcher matcher = SPEAKER_LINE.matcher(line);
                if (matcher.matches()) {
                    String speaker = matcher.group(1).toUpperCase();
                    String text = matcher.group(2).trim();
                    if (text.isEmpty()) continue;
                    pending.add(new String[]{text, resolveGender(speaker, swap), speaker});
                } else {
                    pending.add(new String[]{line, "MALE", "narrator"});
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
            segment.put("speaker", pending.get(i)[2]);
            segment.put("gender", pending.get(i)[1]);
            segment.put("text", pending.get(i)[0]);
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

    private String resolveClovaSpeaker(String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return FEMALE_VOICE;
        }
        String normalized = voiceName.trim().toLowerCase();
        if (normalized.equals(MALE_VOICE) || normalized.equals(FEMALE_VOICE)) {
            return normalized;
        }
        if (normalized.contains("charon") || normalized.contains("male")) {
            return MALE_VOICE;
        }
        return FEMALE_VOICE;
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
