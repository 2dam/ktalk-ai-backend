package com.ktalk.domain.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TTSService {

    private static final int MAX_TEXT_BYTES = 4500;
    private static final int CACHE_SIZE = 500;
    private static final String GOOGLE_TTS_HOST = "texttospeech.googleapis.com";
    private static final String GOOGLE_TTS_PATH = "/v1/text:synthesize";
    private static final String DEFAULT_LANGUAGE_CODE = "ko-KR";
    private static final String DEFAULT_MALE_VOICE = "ko-KR-Chirp3-HD-Rasalgethi";
    private static final String DEFAULT_FEMALE_VOICE = "ko-KR-Chirp3-HD-Zephyr";
    private static final Pattern SPEAKER_LINE = Pattern.compile("^([A-Za-z]+):\\s*(.*)$");

    private final WebClient webClient;

    @Value("${GOOGLE_TTS_API_KEY:${VITE_GOOGLE_TTS_API_KEY:${VITE_GOOGLE_API_KEY:}}}")
    private String googleTtsApiKey;

    @Value("${GOOGLE_TTS_LANGUAGE:" + DEFAULT_LANGUAGE_CODE + "}")
    private String languageCode;

    @Value("${GOOGLE_TTS_MALE_VOICE:" + DEFAULT_MALE_VOICE + "}")
    private String maleVoiceName;

    @Value("${GOOGLE_TTS_FEMALE_VOICE:" + DEFAULT_FEMALE_VOICE + "}")
    private String femaleVoiceName;

    @Value("${GOOGLE_TTS_SPEAKING_RATE:0.95}")
    private double speakingRate;

    @Value("${GOOGLE_TTS_PITCH:0.0}")
    private double pitch;

    @Value("${GOOGLE_TTS_MAX_CONCURRENT_REQUESTS:8}")
    private int maxConcurrentRequests;

    @Value("${GOOGLE_TTS_QUEUE_TIMEOUT_SECONDS:60}")
    private int queueTimeoutSeconds;

    private Semaphore googleTtsSlots;

    public TTSService(@Qualifier("audioWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @PostConstruct
    void initConcurrencyLimit() {
        int slots = Math.max(1, maxConcurrentRequests);
        this.googleTtsSlots = new Semaphore(slots, true);
        log.info("Google TTS concurrency limit initialized: maxConcurrentRequests={}", slots);
    }

    private final Map<String, String> audioCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });
    private final Map<String, CompletableFuture<String>> inFlightAudio = new ConcurrentHashMap<>();

    public String synthesize(String text, String gender) {
        String voiceName = "FEMALE".equalsIgnoreCase(gender) ? femaleVoiceName : maleVoiceName;
        return synthesizeWithVoice(text, voiceName);
    }

    public String synthesizeWithVoice(String text, String voiceName) {
        String truncated = truncateUtf8(text == null ? "" : text, MAX_TEXT_BYTES);
        String resolvedVoiceName = resolveGoogleVoiceName(voiceName);
        String resolvedLanguage = firstPresent(languageCode, DEFAULT_LANGUAGE_CODE);
        String cacheKey = resolvedLanguage + "|" + resolvedVoiceName + "|" + speakingRate + "|" + pitch + "|" + truncated;

        String cached = audioCache.get(cacheKey);
        if (cached != null) {
            log.info("TTS cache hit: voiceName={}, textBytes={}", resolvedVoiceName, truncated.getBytes(StandardCharsets.UTF_8).length);
            return cached;
        }

        CompletableFuture<String> inFlight = new CompletableFuture<>();
        CompletableFuture<String> existing = inFlightAudio.putIfAbsent(cacheKey, inFlight);
        if (existing != null) {
            log.info("TTS in-flight cache wait: voiceName={}, textBytes={}", resolvedVoiceName, truncated.getBytes(StandardCharsets.UTF_8).length);
            try {
                return existing.join();
            } catch (CompletionException e) {
                throw unwrapCompletionException(e);
            }
        }

        String apiKey = firstPresent(
                googleTtsApiKey,
                System.getenv("GOOGLE_TTS_API_KEY"),
                System.getenv("VITE_GOOGLE_TTS_API_KEY"),
                System.getenv("VITE_GOOGLE_API_KEY")
        );

        if (apiKey == null) {
            RuntimeException exception = new RuntimeException("Google TTS API key is missing.");
            log.error(exception.getMessage());
            inFlight.completeExceptionally(exception);
            inFlightAudio.remove(cacheKey);
            throw exception;
        }

        Map<String, Object> body = Map.of(
                "input", Map.of("text", truncated),
                "voice", Map.of(
                        "languageCode", resolvedLanguage,
                        "name", resolvedVoiceName
                ),
                "audioConfig", Map.of(
                        "audioEncoding", "MP3",
                        "speakingRate", speakingRate,
                        "pitch", pitch
                )
        );

        boolean acquired = false;
        try {
            acquired = googleTtsSlots.tryAcquire(queueTimeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("Voice synthesis is busy. Please try again shortly.");
            }

            Map<?, ?> response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(GOOGLE_TTS_HOST)
                            .path(GOOGLE_TTS_PATH)
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            Object audioContentValue = response == null ? null : response.get("audioContent");
            if (!(audioContentValue instanceof String audioContent) || audioContent.isBlank()) {
                throw new RuntimeException("Google TTS returned empty audio.");
            }

            audioCache.put(cacheKey, audioContent);
            inFlight.complete(audioContent);
            log.info("Google TTS synthesis completed: textBytes={}, voiceName={}",
                    truncated.getBytes(StandardCharsets.UTF_8).length, resolvedVoiceName);
            return audioContent;
        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            RuntimeException exception = new RuntimeException("Voice synthesis failed: Google TTS " + e.getStatusCode() + " " + responseBody);
            log.error("Google TTS API failed: status={}, body={}", e.getStatusCode(), responseBody, e);
            inFlight.completeExceptionally(exception);
            throw exception;
        } catch (Exception e) {
            RuntimeException exception = new RuntimeException("Voice synthesis failed: " + e.getMessage());
            log.error("Google TTS API call failed: {}", e.getMessage(), e);
            inFlight.completeExceptionally(exception);
            throw exception;
        } finally {
            if (acquired) {
                googleTtsSlots.release();
            }
            inFlightAudio.remove(cacheKey);
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

        List<Map<String, String>> segments = new ArrayList<>();
        for (String[] pendingSegment : pending) {
            Map<String, String> segment = new LinkedHashMap<>();
            segment.put("speaker", pendingSegment[2]);
            segment.put("gender", pendingSegment[1]);
            segment.put("text", pendingSegment[0]);
            segment.put("audioContent", synthesize(pendingSegment[0], pendingSegment[1]));
            segments.add(segment);
        }
        return segments;
    }

    private String resolveGender(String speaker, boolean swap) {
        boolean isA = "A".equalsIgnoreCase(speaker);
        boolean maleTurn = swap != isA;
        return maleTurn ? "MALE" : "FEMALE";
    }

    private String resolveGoogleVoiceName(String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return firstPresent(femaleVoiceName, DEFAULT_FEMALE_VOICE);
        }
        String normalized = voiceName.trim();
        String lower = normalized.toLowerCase();
        if (normalized.startsWith("ko-KR-")) {
            return normalized;
        }
        if (lower.contains("female") || lower.equals("nara") || lower.equals("zephyr")) {
            return firstPresent(femaleVoiceName, DEFAULT_FEMALE_VOICE);
        }
        if (lower.contains("male") || lower.equals("jinho") || lower.equals("rasalgethi")) {
            return firstPresent(maleVoiceName, DEFAULT_MALE_VOICE);
        }
        return normalized;
    }

    private String truncateUtf8(String text, int maxBytes) {
        if (text.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String value = new String(Character.toChars(codePoint));
            int valueBytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + valueBytes > maxBytes) {
                break;
            }
            builder.append(value);
            bytes += valueBytes;
            i += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private RuntimeException unwrapCompletionException(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException("Voice synthesis failed: " + e.getMessage(), e);
    }
}
