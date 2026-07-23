package com.ktalk.domain.block.controller;

import com.ktalk.domain.block.dto.AssemblyResponse;
import com.ktalk.domain.block.dto.ConnectionResponse;
import com.ktalk.domain.block.service.AssemblyService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@RestController
@RequestMapping("/api/assembly")
@RequiredArgsConstructor
public class AssemblyController {

    private final AssemblyService assemblyService;

    /**
     * GET /api/assembly/{id}
     *
     * Assembly에 속한 LearningBlock들을 position 순서대로 내려준다.
     * 프런트의 하드코딩된 STAGES 배열을 이 응답으로 대체하는 것이 목표.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssemblyResponse>> getAssembly(@PathVariable String id) {
        try {
            AssemblyResponse response = assemblyService.getAssembly(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Assembly 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Assembly 조회 실패: " + e.getMessage()));
        }
    }

    /**
     * GET /api/assembly/{id}/connections
     *
     * 이 Assembly의 블록들이 가진 태그가 다른 주제(Assembly)의 블록에도 등장하면,
     * 그 주제로 확장할 수 있는 연결 목록을 내려준다 ("발견형" 지식 확장).
     */
    @GetMapping("/{id}/connections")
    public ResponseEntity<ApiResponse<List<ConnectionResponse>>> getConnections(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.success(assemblyService.getConnections(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Assembly 연결 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("연결 조회 실패: " + e.getMessage()));
        }
    }
}
