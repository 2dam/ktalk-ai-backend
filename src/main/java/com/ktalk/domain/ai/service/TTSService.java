package com.ktalk.domain.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
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

    public TTSService(@Qualifier("audioWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    private final Map<String, String> audioCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

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

        String apiKey = firstPresent(elevenLabsApiKey, System.getenv("ELEVENLABS_API_KEY"));

        if (apiKey == null) {
            log.error("ElevenLabs API key is missing.");
            throw new RuntimeException("ElevenLabs API key is missing.");
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

        try {
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
            log.info("ElevenLabs synthesis completed: textLength={}, voiceId={}", truncated.length(), resolvedVoiceId);
            return audioContent;
        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("ElevenLabs API failed: status={}, body={}", e.getStatusCode(), responseBody, e);
            throw new RuntimeException("Voice synthesis failed: ElevenLabs " + e.getStatusCode() + " " + responseBody);
        } catch (Exception e) {
            log.error("ElevenLabs API call failed: {}", e.getMessage(), e);
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
}
