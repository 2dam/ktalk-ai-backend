package com.ktalk.domain.dictionary.controller;

import com.ktalk.domain.dictionary.service.OpenDictService;
import com.ktalk.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단어를 클릭했을 때 뜻풀이 팝업에 쓰는 사전 조회 API. 프론트는 이 엔드포인트만 호출하면
 * 되고, 실제 외부 API(우리말샘) 키는 서버에만 있다.
 */
@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
@Slf4j
public class DictionaryController {

    private final OpenDictService openDictService;

    @GetMapping
    public ResponseEntity<ApiResponse> search(
            @RequestParam String query, @RequestParam(defaultValue = "5") int limit) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("검색어가 비어 있습니다."));
        }
        try {
            var entries = openDictService.search(query.strip(), limit);
            if (entries.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success(entries, "뜻풀이를 찾지 못했어요."));
            }
            return ResponseEntity.ok(ApiResponse.success(entries));
        } catch (IllegalStateException e) {
            log.warn("사전 조회 실패: query='{}'", query, e);
            return ResponseEntity.status(502).body(ApiResponse.error(e.getMessage()));
        }
    }
}
