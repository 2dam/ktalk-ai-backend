package com.ktalk.domain.content.controller;

import com.ktalk.domain.content.entity.Content;
import com.ktalk.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
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

    // 개인 맞춤 추천: 아직 대화문을 만들지 않은 콘텐츠를 최근 생성순으로 우선 추천하고,
    // 전부 대화문이 있다면 최근 생성된 콘텐츠 순으로 대체 추천한다.
    @GetMapping("/recommend")
    public ResponseEntity<List<Content>> recommend() {
        List<Content> all = contentRepository.findAll();

        List<Content> withoutDialogue = all.stream()
                .filter(c -> c.getDialogue() == null || c.getDialogue().isBlank())
                .sorted(Comparator.comparing(Content::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();

        if (!withoutDialogue.isEmpty()) {
            return ResponseEntity.ok(withoutDialogue);
        }

        List<Content> recent = all.stream()
                .sorted(Comparator.comparing(Content::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
        return ResponseEntity.ok(recent);
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