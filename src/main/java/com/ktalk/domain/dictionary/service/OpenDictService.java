package com.ktalk.domain.dictionary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.dictionary.dto.DictionaryEntryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 국립국어원 우리말샘 오픈 API로 단어 뜻을 조회한다(네이버 사전은 공식 조회 API가 없고
 * 스크래핑은 약관상 위험해서, 같은 용도의 공식 무료 API로 대체).
 * 단어 하나가 여러 뜻을 가질 수 있어 응답의 item[] 각각을 뜻풀이 한 건으로 매핑한다.
 * https://opendict.korean.go.kr/service/openApiInfo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenDictService {

    private static final String SEARCH_URL = "https://opendict.korean.go.kr/api/search";

    @Value("${opendict.api.key:}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public List<DictionaryEntryResponse> search(String query, int limit) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("사전 API 키가 설정되지 않았습니다. OPENDICT_API_KEY 환경변수를 확인하세요.");
        }
        if (limit < 1) {
            limit = 5;
        }

        // num 파라미터는 API 문서상 10~100 범위만 허용된다. 호출자가 더 적게 원하면
        // 일단 최소치(10)로 받아온 뒤 자바 쪽에서 원하는 개수만큼 잘라낸다.
        int requestNum = Math.max(10, Math.min(limit, 100));
        URI uri = UriComponentsBuilder.fromHttpUrl(SEARCH_URL)
                .queryParam("key", apiKey)
                .queryParam("q", query)
                .queryParam("req_type", "json")
                .queryParam("part", "word")
                .queryParam("num", requestNum)
                .build()
                .encode()
                .toUri();

        String response = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        List<DictionaryEntryResponse> entries = parse(response, query);
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private List<DictionaryEntryResponse> parse(String response, String query) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("우리말샘 응답 파싱 실패: {}", response, e);
            throw new IllegalStateException("사전 응답을 처리하지 못했습니다.", e);
        }

        if (root.has("error")) {
            String message = root.path("error").path("message").asText("사전 조회 실패");
            throw new IllegalStateException("우리말샘 API 오류: " + message);
        }

        List<DictionaryEntryResponse> results = new ArrayList<>();
        for (JsonNode item : root.path("channel").path("item")) {
            String word = item.path("word").asText(query);

            List<JsonNode> senses = new ArrayList<>();
            JsonNode senseNode = item.path("sense");
            if (senseNode.isArray()) {
                senseNode.forEach(senses::add);
            } else if (!senseNode.isMissingNode()) {
                senses.add(senseNode);
            }

            for (JsonNode sense : senses) {
                String definition = sense.path("definition").asText("");
                if (definition.isBlank()) {
                    continue;
                }
                results.add(new DictionaryEntryResponse(word, sense.path("pos").asText(""), definition));
            }
        }
        return results;
    }
}
