package com.ktalk.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class YouTubeService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${youtube.api.key}")
    private String apiKey;

    // preferShort: 특정 대사/가사 하나만 담긴 짧은 클립(쇼츠, 하이라이트)을 우선적으로 찾고 싶을 때 사용.
    // 유튜브 API는 특정 순간의 타임스탬프를 알려주지 않으므로, 이미 그 부분만 잘라서 올라온
    // 4분 미만 영상(주로 쇼츠/하이라이트)을 우선 검색하는 방식으로 근접하게 구현한다.
    public List<VideoInfo> searchVideos(String query, int maxResults, boolean preferShort) {
        try {
            String effectiveQuery = preferShort ? query + " shorts" : query;
            String response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder
                                .scheme("https")
                                .host("www.googleapis.com")
                                .path("/youtube/v3/search")
                                .queryParam("part", "snippet")
                                .queryParam("q", effectiveQuery)
                                .queryParam("maxResults", maxResults)
                                .queryParam("type", "video")
                                .queryParam("key", apiKey);
                        if (preferShort) {
                            uriBuilder.queryParam("videoDuration", "short");
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");
            List<VideoInfo> videos = new ArrayList<>();

            for (JsonNode item : items) {
                JsonNode snippet = item.path("snippet");
                String videoId = item.path("id").path("videoId").asText();
                String title = snippet.path("title").asText();
                String description = snippet.path("description").asText();
                String thumbnailUrl = snippet.path("thumbnails").path("high").path("url").asText();
                String channelName = snippet.path("channelTitle").asText();

                videos.add(new VideoInfo(videoId, title, description, thumbnailUrl, channelName));
            }

            log.info("YouTube 검색 완료: query={}, count={}", query, videos.size());
            return videos;
        } catch (Exception e) {
            log.error("YouTube API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("YouTube 검색 실패: " + e.getMessage());
        }
    }

    public record VideoInfo(String videoId, String title, String description, String thumbnailUrl, String channelName) {}
}