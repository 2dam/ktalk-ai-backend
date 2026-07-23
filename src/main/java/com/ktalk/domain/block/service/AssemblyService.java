package com.ktalk.domain.block.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktalk.domain.block.dto.AssemblyResponse;
import com.ktalk.domain.block.dto.ConnectionResponse;
import com.ktalk.domain.block.entity.Assembly;
import com.ktalk.domain.block.entity.AssemblyBlock;
import com.ktalk.domain.block.entity.LearningBlock;
import com.ktalk.domain.block.repository.AssemblyBlockRepository;
import com.ktalk.domain.block.repository.AssemblyRepository;
import com.ktalk.domain.block.repository.LearningBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssemblyService {

    private final AssemblyRepository assemblyRepository;
    private final LearningBlockRepository learningBlockRepository;
    private final AssemblyBlockRepository assemblyBlockRepository;
    private final TagEmbeddingService tagEmbeddingService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AssemblyResponse getAssembly(String assemblyId) {
        Assembly assembly = assemblyRepository.findById(assemblyId)
                .orElseThrow(() -> new NoSuchElementException("Assembly를 찾을 수 없습니다: " + assemblyId));

        List<AssemblyResponse.BlockView> blocks = assembly.getBlocks().stream()
                .map(this::toBlockView)
                .toList();

        return new AssemblyResponse(
                assembly.getId(),
                assembly.getInterestTag(),
                assembly.getKoreanLevel(),
                assembly.getStatus().name(),
                blocks
        );
    }

    /**
     * "발견형" 지식 확장: 이 Assembly의 블록들이 가진 태그 중, 다른 Assembly의 블록에도
     * 등장하는(문자열이 같은, EXACT) 또는 임베딩상 뜻이 가까운(SIMILAR) 태그를 찾아
     * "이 단어로 이어지는 다른 주제" 목록을 만든다. 예: "축구장" 태그가 다른 Assembly의
     * "운동장" 태그와 유사도가 높으면 SIMILAR로 연결된다.
     */
    @Transactional(readOnly = true)
    public List<ConnectionResponse> getConnections(String assemblyId) {
        Assembly assembly = assemblyRepository.findById(assemblyId)
                .orElseThrow(() -> new NoSuchElementException("Assembly를 찾을 수 없습니다: " + assemblyId));

        Set<String> ownBlockIds = new LinkedHashSet<>();
        LinkedHashSet<String> ownTags = new LinkedHashSet<>();
        for (AssemblyBlock ab : assembly.getBlocks()) {
            ownBlockIds.add(ab.getBlock().getId());
            ownTags.addAll(ab.getBlock().getOutputTags());
        }

        List<ConnectionResponse> connections = new ArrayList<>();
        for (String tag : ownTags) {
            addConnection(connections, tag, tag, null, ownBlockIds, assemblyId);

            for (TagEmbeddingService.SimilarTag similar : tagEmbeddingService.findSimilarTags(tag)) {
                if (ownTags.contains(similar.tag())) continue; // 이미 우리 태그이기도 하면 EXACT로 충분
                addConnection(connections, tag, similar.tag(), similar.similarity(), ownBlockIds, assemblyId);
            }
        }
        return connections;
    }

    private void addConnection(List<ConnectionResponse> connections, String ownTag, String matchedTag,
                                Double similarity, Set<String> ownBlockIds, String assemblyId) {
        List<ConnectionResponse.RelatedAssembly> related = new ArrayList<>();
        Set<String> seenAssemblyIds = new LinkedHashSet<>();
        seenAssemblyIds.add(assemblyId);

        for (LearningBlock candidate : learningBlockRepository.findByOutputTag(matchedTag)) {
            if (ownBlockIds.contains(candidate.getId())) continue;
            for (AssemblyBlock owner : assemblyBlockRepository.findByBlock_Id(candidate.getId())) {
                Assembly otherAssembly = owner.getAssembly();
                if (seenAssemblyIds.add(otherAssembly.getId())) {
                    related.add(new ConnectionResponse.RelatedAssembly(
                            otherAssembly.getId(), otherAssembly.getInterestTag()));
                }
            }
        }

        if (!related.isEmpty()) {
            connections.add(new ConnectionResponse(
                    ownTag, matchedTag, similarity == null ? "EXACT" : "SIMILAR", similarity, related));
        }
    }

    private AssemblyResponse.BlockView toBlockView(AssemblyBlock assemblyBlock) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(assemblyBlock.getBlock().getPayload());
        } catch (Exception e) {
            log.error("블록 payload 파싱 실패: {}", assemblyBlock.getBlock().getId(), e);
            payload = objectMapper.getNodeFactory().objectNode();
        }

        return new AssemblyResponse.BlockView(
                assemblyBlock.getId(),
                assemblyBlock.getPosition(),
                assemblyBlock.getBlock().getType().name(),
                payload,
                Boolean.TRUE.equals(assemblyBlock.getCompleted())
        );
    }
}
