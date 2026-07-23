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
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class YouTubeService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${youtube.api.key}")
    private String apiKey;

    public List<VideoInfo> searchVideos(String query, int maxResults, boolean preferShort) {
        return searchVideos(query, maxResults, preferShort, null);
    }

    // preferShort: 특정 대사/가사 하나만 담긴 짧은 클립(쇼츠, 하이라이트)을 우선적으로 찾고 싶을 때 사용.
    // 유튜브 API는 특정 순간의 타임스탬프를 알려주지 않으므로, 이미 그 부분만 잘라서 올라온
    // 4분 미만 영상(주로 쇼츠/하이라이트)을 우선 검색하는 방식으로 근접하게 구현한다.
    //
    // keyword: YouTube 검색 자체는 제목/설명 관련도로 순위를 매길 뿐 자막 속 실제 대사를 검색하지
    // 않는다. "축구" 같은 흔한 검색어는 결과가 많아 관계없는 영상까지 섞이기 쉬우므로, keyword가
    // 주어지면 제목/설명에 그 단어가 실제로 포함된 결과만 남긴다 (자막 검증은 아니지만 최소한의 확인).
    // 필터링으로 결과가 줄어드는 걸 감안해 후보를 넉넉히 받아온 뒤 걸러서 maxResults로 자른다.
    public List<VideoInfo> searchVideos(String query, int maxResults, boolean preferShort, String keyword) {
        boolean filtering = keyword != null && !keyword.isBlank();
        int fetchCount = filtering ? Math.min(maxResults * 5, 50) : maxResults;

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
                                .queryParam("maxResults", fetchCount)
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

                videos.add(new VideoInfo(videoId, title, description, thumbnailUrl, channelName, true));
            }

            if (filtering) {
                String needle = keyword.strip().toLowerCase(Locale.ROOT);
                List<VideoInfo> matched = videos.stream()
                        .filter(v -> v.title().toLowerCase(Locale.ROOT).contains(needle)
                                || v.description().toLowerCase(Locale.ROOT).contains(needle))
                        .limit(maxResults)
                        .toList();

                if (!matched.isEmpty()) {
                    log.info("YouTube 검색 완료: query={}, keyword={}, fetched={}, matched={}",
                            query, keyword, videos.size(), matched.size());
                    return matched;
                }

                // 실제로 단어가 들어간 결과가 하나도 없으면, 빈 화면 대신 관련 있을 만한 영상을
                // matched=false로 표시해서 보여준다 (프런트가 "확인된 건 아님"을 안내할 수 있게).
                List<VideoInfo> fallback = videos.stream()
                        .limit(maxResults)
                        .map(v -> new VideoInfo(v.videoId(), v.title(), v.description(), v.thumbnailUrl(), v.channelName(), false))
                        .toList();
                log.info("YouTube 검색: query={}, keyword={} 일치 결과 없어 일반 결과 {}개로 대체",
                        query, keyword, fallback.size());
                return fallback;
            }

            log.info("YouTube 검색 완료: query={}, count={}", query, videos.size());
            return videos;
        } catch (Exception e) {
            log.error("YouTube API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("YouTube 검색 실패: " + e.getMessage());
        }
    }

    public record VideoInfo(String videoId, String title, String description, String thumbnailUrl,
                             String channelName, boolean matched) {}
}