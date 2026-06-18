package com.ktalk.domain.content.controller;

import com.ktalk.domain.content.entity.Content;
import com.ktalk.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // CORS 설정 (프론트엔드 연동용)
public class ContentController {

    private final ContentRepository contentRepository;

    // 모든 콘텐츠 조회
    @GetMapping
    public ResponseEntity<List<Content>> getAllContents() {
        List<Content> contents = contentRepository.findAll();
        return ResponseEntity.ok(contents);
    }

    // ID로 콘텐츠 조회
    @GetMapping("/{id}")
    public ResponseEntity<Content> getContentById(@PathVariable Long id) {
        return contentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 새 콘텐츠 생성
    @PostMapping
    public ResponseEntity<Content> createContent(@RequestBody Content content) {
        Content savedContent = contentRepository.save(content);
        return ResponseEntity.ok(savedContent);
    }
}