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
@CrossOrigin(origins = "*")
public class ContentController {

    private final ContentRepository contentRepository;

    @GetMapping
    public ResponseEntity<List<Content>> getAllContents() {
        List<Content> contents = contentRepository.findAll();
        return ResponseEntity.ok(contents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Content> getContentById(@PathVariable Long id) {
        return contentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<Content>> getContentsByCategory(@PathVariable String category) {
        List<Content> contents = contentRepository.findByCategory(category);
        return ResponseEntity.ok(contents);
    }

    @PostMapping
    public ResponseEntity<Content> createContent(@RequestBody Content content) {
        Content savedContent = contentRepository.save(content);
        return ResponseEntity.ok(savedContent);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Content> updateContent(
            @PathVariable Long id,
            @RequestBody Content contentDetails) {
        return contentRepository.findById(id)
                .map(content -> {
                    content.setTitle(contentDetails.getTitle());
                    content.setDescription(contentDetails.getDescription());
                    content.setCategory(contentDetails.getCategory());
                    content.setKoreanLevel(contentDetails.getKoreanLevel());
                    Content updatedContent = contentRepository.save(content);
                    return ResponseEntity.ok(updatedContent);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContent(@PathVariable Long id) {
        return contentRepository.findById(id)
                .map(content -> {
                    contentRepository.delete(content);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}