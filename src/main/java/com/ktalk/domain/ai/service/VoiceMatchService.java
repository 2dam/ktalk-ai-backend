package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.ai.dto.VoiceMatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VoiceMatchService {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TTSService ttsService;
    private final GeminiApiClient geminiApiClient;

    public VoiceMatchService(@Qualifier("audioWebClient") WebClient webClient,
                             ObjectMapper objectMapper,
                             TTSService ttsService,
                             GeminiApiClient geminiApiClient) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.ttsService = ttsService;
        this.geminiApiClient = geminiApiClient;
    }

    public VoiceMatchResponse processVoice(MultipartFile audioFile) throws Exception {
        // 1. 오디오 파일을 base64로 인코딩
        byte[] audioBytes = audioFile.getBytes();
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
        String mimeType = AudioMimeTypeResolver.resolveMimeType(audioFile.getOriginalFilename(), audioFile.getContentType());

        // 2. Gemini에 오디오 전송 → 외국어 인식 + Kpop/Kdrama 한국어 표현 탐색
        String geminiJson = callGeminiWithAudio(audioBase64, mimeType);
        ParsedGeminiResult parsed = parseGeminiResult(geminiJson);

        // 3. 한국어 표현들을 읽어줄 TTS 스크립트 생성
        String ttsScript = buildTtsScript(parsed.phrases());

        // 4. Google TTS API로 한국어 음성 생성
        String audioContent = callGoogleTts(ttsScript);

        return new VoiceMatchResponse(
                parsed.transcription(),
                parsed.detectedLanguage(),
                parsed.phrases(),
                audioContent
        );
    }

    // ── Gemini multimodal 호출 ──────────────────────────────────────────────

    private String callGeminiWithAudio(String audioBase64, String mimeType) {
        String prompt = buildGeminiPrompt();

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", audioBase64
                                )),
                                Map.of("text", prompt)
                        )
                ))
        );

        return geminiApiClient.generateText(webClient, geminiApiKey, body);
    }

    private String buildGeminiPrompt() {
        return """
                You are a Korean language assistant specializing in K-pop and K-drama.

                Listen to the audio clip. The speaker is using a foreign language.

                Your tasks:
                1. Transcribe what was said.
                2. Understand the meaning/emotion.
                3. Find 3 to 5 Korean expressions from real K-pop songs or K-dramas that convey a similar meaning or emotion.

                Return ONLY a pure JSON object with NO markdown formatting:
                {
                  "transcription": "<what the speaker said, in the original language>",
                  "detectedLanguage": "<ISO 639-1 code, e.g. en, ja, zh, es>",
                  "phrases": [
                    {
                      "korean": "<Korean expression>",
                      "romanization": "<romanized pronunciation>",
                      "meaning": "<English meaning>",
                      "source": "<specific song or drama title and artist/episode>",
                      "sourceType": "<KPOP or KDRAMA>",
                      "usageContext": "<when and how this expression is used>"
                    }
                  ]
                }

                Use only real, well-known K-pop songs or K-dramas for "source".
                """;
    }

    // ── 결과 파싱 ───────────────────────────────────────────────────────────

    private ParsedGeminiResult parseGeminiResult(String rawJson) {
        try {
            String cleaned = GeminiApiClient.stripMarkdownFences(rawJson);

            JsonNode root = objectMapper.readTree(cleaned);
            String transcription = root.path("transcription").asText();
            String detectedLanguage = root.path("detectedLanguage").asText("unknown");

            List<VoiceMatchResponse.KoreanPhrase> phrases = objectMapper.convertValue(
                    root.path("phrases"),
                    new TypeReference<>() {}
            );

            return new ParsedGeminiResult(transcription, detectedLanguage, phrases);
        } catch (Exception e) {
            log.error("Gemini 결과 파싱 실패: {}", rawJson, e);
            throw new RuntimeException("응답 파싱 실패: " + e.getMessage());
        }
    }

    // ── TTS 호출 ───────────────────────────────────────────────────────────

    private String buildTtsScript(List<VoiceMatchResponse.KoreanPhrase> phrases) {
        // 한국어 표현들을 자연스럽게 읽어주는 스크립트
        StringBuilder sb = new StringBuilder();
        sb.append("비슷한 한국어 표현을 알려드릴게요. ");

        for (int i = 0; i < phrases.size(); i++) {
            VoiceMatchResponse.KoreanPhrase p = phrases.get(i);
            sb.append(String.format(
                    "%d번. %s. 뜻은 %s 이고, %s 에서 나온 표현입니다. %s. ",
                    i + 1, p.getKorean(), p.getMeaning(), p.getSource(), p.getUsageContext()
            ));
        }

        return sb.toString();
    }

    private String callGoogleTts(String text) {
        // 자연스러운 한국어 여성 음성, 약간 천천히 (학습용) — 캐싱은 TTSService가 처리
        return ttsService.synthesize(text, "FEMALE");
    }

    private record ParsedGeminiResult(
            String transcription,
            String detectedLanguage,
            List<VoiceMatchResponse.KoreanPhrase> phrases
    ) {}
}
