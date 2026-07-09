package com.ktalk.domain.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TTSService {

    private static final int MAX_TEXT_LENGTH = 3000;
    private static final int CACHE_SIZE = 500;
    private static final String ELEVENLABS_TTS_HOST = "api.elevenlabs.io";
    private static final String ELEVENLABS_TTS_PATH = "/v1/text-to-speech/{voiceId}";
    private static final String DEFAULT_MALE_VOICE_ID = "ErXwobaYiN019PkySvjV";
    private static final String DEFAULT_FEMALE_VOICE_ID = "EXAVITQu4vr4xnSDxMaL";
    private static final Pattern SPEAKER_LINE = Pattern.compile("^([A-Za-z]+):\\s*(.*)$");

    private final WebClient webClient;

    @Value("${ELEVENLABS_API_KEY:}")
    private String elevenLabsApiKey;

    @Value("${ELEVENLABS_MODEL_ID:eleven_multilingual_v2}")
    private String elevenLabsModelId;

    @Value("${ELEVENLABS_MALE_VOICE_ID:" + DEFAULT_MALE_VOICE_ID + "}")
    private String maleVoiceId;

    @Value("${ELEVENLABS_FEMALE_VOICE_ID:" + DEFAULT_FEMALE_VOICE_ID + "}")
    private String femaleVoiceId;

    @Value("${ELEVENLABS_STABILITY:0.45}")
    private double stability;

    @Value("${ELEVENLABS_SIMILARITY_BOOST:0.80}")
    private double similarityBoost;

    @Value("${ELEVENLABS_STYLE:0.25}")
    private double style;

    @Value("${ELEVENLABS_MAX_CONCURRENT_REQUESTS:3}")
    private int maxConcurrentRequests;

    @Value("${ELEVENLABS_QUEUE_TIMEOUT_SECONDS:60}")
    private int queueTimeoutSeconds;

    private Semaphore elevenLabsSlots;

    public TTSService(@Qualifier("audioWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @PostConstruct
    void initConcurrencyLimit() {
        int slots = Math.max(1, maxConcurrentRequests);
        this.elevenLabsSlots = new Semaphore(slots, true);
        log.info("ElevenLabs concurrency limit initialized: maxConcurrentRequests={}", slots);
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
        String voiceId = "FEMALE".equalsIgnoreCase(gender) ? femaleVoiceId : maleVoiceId;
        return synthesizeWithVoice(text, voiceId);
    }

    public String synthesizeWithVoice(String text, String voiceId) {
        String truncated = text.substring(0, Math.min(text.length(), MAX_TEXT_LENGTH));
        String resolvedVoiceId = resolveElevenLabsVoiceId(voiceId);
        String modelId = firstPresent(elevenLabsModelId, "eleven_multilingual_v2");
        String cacheKey = modelId + "|" + resolvedVoiceId + "|" + truncated;

        String cached = audioCache.get(cacheKey);
        if (cached != null) {
            log.info("TTS cache hit: voiceId={}, textLength={}", resolvedVoiceId, truncated.length());
            return cached;
        }

        CompletableFuture<String> inFlight = new CompletableFuture<>();
        CompletableFuture<String> existing = inFlightAudio.putIfAbsent(cacheKey, inFlight);
        if (existing != null) {
            log.info("TTS in-flight cache wait: voiceId={}, textLength={}", resolvedVoiceId, truncated.length());
            try {
                return existing.join();
            } catch (CompletionException e) {
                throw unwrapCompletionException(e);
            }
        }

        String apiKey = firstPresent(elevenLabsApiKey, System.getenv("ELEVENLABS_API_KEY"));

        if (apiKey == null) {
            log.error("ElevenLabs API key is missing.");
            RuntimeException exception = new RuntimeException("ElevenLabs API key is missing.");
            inFlight.completeExceptionally(exception);
            inFlightAudio.remove(cacheKey);
            throw exception;
        }

        Map<String, Object> body = Map.of(
                "text", truncated,
                "model_id", modelId,
                "voice_settings", Map.of(
                        "stability", stability,
                        "similarity_boost", similarityBoost,
                        "style", style,
                        "use_speaker_boost", true
                )
        );

        boolean acquired = false;
        try {
            acquired = elevenLabsSlots.tryAcquire(queueTimeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("Voice synthesis is busy. Please try again shortly.");
            }

            byte[] audioBytes = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(ELEVENLABS_TTS_HOST)
                            .path(ELEVENLABS_TTS_PATH)
                            .queryParam("output_format", "mp3_44100_128")
                            .build(resolvedVoiceId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.valueOf("audio/mpeg"))
                    .header("xi-api-key", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (audioBytes == null || audioBytes.length == 0) {
                throw new RuntimeException("ElevenLabs returned empty audio.");
            }

            String audioContent = Base64.getEncoder().encodeToString(audioBytes);
            audioCache.put(cacheKey, audioContent);
            inFlight.complete(audioContent);
            log.info("ElevenLabs synthesis completed: textLength={}, voiceId={}", truncated.length(), resolvedVoiceId);
            return audioContent;
        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("ElevenLabs API failed: status={}, body={}", e.getStatusCode(), responseBody, e);
            RuntimeException exception = new RuntimeException("Voice synthesis failed: ElevenLabs " + e.getStatusCode() + " " + responseBody);
            inFlight.completeExceptionally(exception);
            throw exception;
        } catch (Exception e) {
            log.error("ElevenLabs API call failed: {}", e.getMessage(), e);
            RuntimeException exception = new RuntimeException("Voice synthesis failed: " + e.getMessage());
            inFlight.completeExceptionally(exception);
            throw exception;
        } finally {
            if (acquired) {
                elevenLabsSlots.release();
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

    private String resolveElevenLabsVoiceId(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            return firstPresent(femaleVoiceId, DEFAULT_FEMALE_VOICE_ID);
        }
        String normalized = voiceId.trim();
        String lower = normalized.toLowerCase();
        if (lower.contains("female") || lower.equals("nara") || lower.equals("rachel") || lower.equals("bella")) {
            return firstPresent(femaleVoiceId, DEFAULT_FEMALE_VOICE_ID);
        }
        if (lower.contains("male") || lower.equals("jinho") || lower.equals("antoni") || lower.equals("adam")) {
            return firstPresent(maleVoiceId, DEFAULT_MALE_VOICE_ID);
        }
        return normalized;
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
