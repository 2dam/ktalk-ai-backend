package com.ktalk.domain.block.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Assembly를 순서대로 나열한 조회 응답. 프런트가 하드코딩된 STAGES 대신 이 목록을 그대로 렌더링할 수 있다.
 */
@Getter
@AllArgsConstructor
public class AssemblyResponse {

    private String id;
    private String interestTag;
    private String koreanLevel;
    private String status;
    private List<BlockView> blocks;

    @Getter
    @AllArgsConstructor
    public static class BlockView {
        private String assemblyBlockId;
        private int position;
        private String type;
        private JsonNode payload;
        private boolean completed;
    }
}
