package com.ktalk.domain.block.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.ai.service.EmbeddingService;
import com.ktalk.domain.block.entity.TagEmbedding;
import com.ktalk.domain.block.repository.TagEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 표기가 달라도 뜻이 가까운 태그(예: "축구장" ≈ "운동장")를 찾기 위한 임베딩 캐시 + 유사도 검색.
 * AssemblyService.getConnections의 "SIMILAR" 매칭 소스.
 *
 * 브루트포스 코사인 유사도 비교라 태그 수가 아주 많아지면(수만 개 이상) 느려진다 —
 * 그 단계에 가면 pgvector 같은 벡터 인덱스로 옮기는 게 맞다. 지금 규모에선 충분하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagEmbeddingService {

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.75;
    private static final int DEFAULT_LIMIT = 3;

    private final TagEmbeddingRepository tagEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public record SimilarTag(String tag, double similarity) {}

    /** 아직 임베딩이 없는 태그만 Gemini에 물어서 캐시에 저장한다. */
    @Transactional
    public void ensureEmbeddings(Collection<String> tags) {
        for (String tag : tags) {
            String normalized = normalize(tag);
            if (normalized.isBlank() || tagEmbeddingRepository.findByTag(normalized).isPresent()) {
                continue;
            }
            try {
                List<Double> vector = embeddingService.embed(normalized);
                TagEmbedding entity = new TagEmbedding();
                entity.setTag(normalized);
                entity.setEmbedding(objectMapper.writeValueAsString(vector));
                tagEmbeddingRepository.save(entity);
            } catch (Exception e) {
                log.warn("태그 임베딩 생성 실패 (유사어 매칭 없이 계속 진행): {}", tag, e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<SimilarTag> findSimilarTags(String tag) {
        return findSimilarTags(tag, DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<SimilarTag> findSimilarTags(String tag, double threshold, int limit) {
        String normalized = normalize(tag);
        TagEmbedding self = tagEmbeddingRepository.findByTag(normalized).orElse(null);
        if (self == null) return List.of();

        double[] selfVector = parse(self.getEmbedding());
        List<SimilarTag> results = new ArrayList<>();
        for (TagEmbedding other : tagEmbeddingRepository.findAll()) {
            if (other.getTag().equals(normalized)) continue;
            double similarity = cosineSimilarity(selfVector, parse(other.getEmbedding()));
            if (similarity >= threshold) {
                results.add(new SimilarTag(other.getTag(), similarity));
            }
        }
        results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        return results.size() > limit ? results.subList(0, limit) : results;
    }

    private String normalize(String tag) {
        return tag == null ? "" : tag.strip().toLowerCase(Locale.ROOT);
    }

    private double[] parse(String embeddingJson) {
        try {
            List<Double> values = objectMapper.readValue(embeddingJson, new TypeReference<>() {});
            double[] result = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        } catch (Exception e) {
            log.error("임베딩 파싱 실패", e);
            return new double[0];
        }
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length == 0 || a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
