package com.ktalk.domain.topik.service;

import com.ktalk.domain.block.service.TagEmbeddingService;
import com.ktalk.domain.topik.dto.DistractorResponse;
import com.ktalk.domain.topik.entity.TopikLevel;
import com.ktalk.domain.topik.entity.Word;
import com.ktalk.domain.topik.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 정답 단어와 임베딩이 "적당히" 비슷한 같은 등급대 단어를 오답 선택지로 고른다.
 * 표기가 달라도 의미가 가까운 단어를 오답으로 쓰는 게, 무작위 오답보다 실제
 * TOPIK 문제의 변별력에 가깝다 — 벡터 공간에서 정답 주변의 "포텐셜이 낮은" 이웃을
 * 찾는 것과 같은 원리다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistractorGeneratorService {

    // 이 범위를 벗어나면 오답으로 부적절하다: 너무 낮으면 관련 없어 보여 문제가 쉬워지고,
    // 너무 높으면 사실상 동의어라 정답이 여러 개인 문제가 된다.
    private static final double MIN_SIMILARITY = 0.30;
    private static final double MAX_SIMILARITY = 0.90;
    private static final int CANDIDATE_POOL_LIMIT = 200;

    private final WordRepository wordRepository;
    private final TagEmbeddingService tagEmbeddingService;

    @Transactional
    public List<DistractorResponse> generate(String wordId, int count) {
        Word correct = wordRepository.findById(wordId)
                .orElseThrow(() -> new IllegalArgumentException("단어를 찾을 수 없습니다: " + wordId));
        if (correct.getTopikLevel() == null) {
            throw new IllegalStateException("난이도가 분류되지 않은 단어입니다. 먼저 classify를 호출하세요: " + correct.getText());
        }

        List<Word> pool = candidatePool(correct);
        if (pool.isEmpty()) {
            log.warn("오답 후보 풀이 비어 있음: word='{}', level={}", correct.getText(), correct.getTopikLevel());
            return List.of();
        }

        List<String> texts = new ArrayList<>();
        texts.add(correct.getText());
        pool.forEach(w -> texts.add(w.getText()));
        tagEmbeddingService.ensureEmbeddings(texts);

        double[] selfVector = tagEmbeddingService.getVector(correct.getText())
                .orElseThrow(() -> new IllegalStateException("정답 단어 임베딩 생성 실패: " + correct.getText()));

        List<DistractorResponse> scored = new ArrayList<>();
        for (Word candidate : pool) {
            tagEmbeddingService.getVector(candidate.getText()).ifPresent(vector -> {
                double similarity = tagEmbeddingService.similarity(selfVector, vector);
                if (similarity >= MIN_SIMILARITY && similarity <= MAX_SIMILARITY) {
                    scored.add(new DistractorResponse(candidate.getId(), candidate.getText(), candidate.getMeaning(), similarity));
                }
            });
        }
        scored.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));

        List<DistractorResponse> result = scored.size() > count ? scored.subList(0, count) : scored;
        if (result.size() < count) {
            log.warn("요청한 오답 개수({})보다 적게 생성됨: {} (word='{}')", count, result.size(), correct.getText());
        }
        return result;
    }

    private List<Word> candidatePool(Word correct) {
        List<Word> pool = wordRepository.findByTopikLevelAndIdNot(correct.getTopikLevel(), correct.getId());
        if (pool.size() < 3) {
            // 같은 급수 단어가 부족하면 같은 상/중/하 그룹의 인접 등급까지 넓힌다.
            List<TopikLevel> groupLevels = Arrays.stream(TopikLevel.values())
                    .filter(level -> level.getGroup() == correct.getTopikLevel().getGroup())
                    .toList();
            pool = wordRepository.findByTopikLevelIn(groupLevels).stream()
                    .filter(w -> !w.getId().equals(correct.getId()))
                    .toList();
        }
        return pool.size() > CANDIDATE_POOL_LIMIT ? pool.subList(0, CANDIDATE_POOL_LIMIT) : pool;
    }
}
