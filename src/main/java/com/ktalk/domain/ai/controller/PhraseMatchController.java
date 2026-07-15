package com.ktalk.domain.ai.controller;

import com.ktalk.domain.ai.dto.PhraseMatchRequest;
import com.ktalk.domain.ai.dto.PhraseMatchResponse;
import com.ktalk.domain.ai.service.PhraseMatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class PhraseMatchController {

    private final PhraseMatchService phraseMatchService;

    /**
     * POST /api/ai/phrase-match
     *
     * 외국어 문장을 받아 Kpop/Kdrama에서 유사한 한국어 표현을 찾아 반환합니다.
     *
     * Request body:
     *   { "text": "I miss you so much", "language": "en" }
     *
     * Response:
     *   {
     *     "inputText": "I miss you so much",
     *     "detectedLanguage": "en",
     *     "phrases": [
     *       {
     *         "korean": "보고 싶다",
     *         "romanization": "bogo sipda",
     *         "meaning": "I miss you",
     *         "source": "2NE1 - Missing You",
     *         "sourceType": "KPOP",
     *         "usageContext": "그리운 감정을 표현할 때 씁니다."
     *       }, ...
     *     ]
     *   }
     */
    @PostMapping("/phrase-match")
    public ResponseEntity<PhraseMatchResponse> matchPhrase(
            @Valid @RequestBody PhraseMatchRequest request) {
        PhraseMatchResponse response = phraseMatchService.matchPhrases(request);
        return ResponseEntity.ok(response);
    }
}
