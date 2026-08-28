package com.ktalk.domain.flashcard.service;

import com.ktalk.domain.flashcard.dto.FlashcardCreateRequest;
import com.ktalk.domain.flashcard.dto.FlashcardResponse;
import com.ktalk.domain.flashcard.entity.Flashcard;
import com.ktalk.domain.flashcard.repository.FlashcardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public FlashcardResponse create(String userId, FlashcardCreateRequest req) {
        Flashcard card = new Flashcard();
        card.setUserId(userId);
        card.setFront(req.front());
        card.setBack(req.back());
        card.setAudioBase64(req.audioBase64());
        card.setSource(req.source());
        return toResponse(flashcardRepository.save(card));
    }

    public List<FlashcardResponse> listByUser(String userId) {
        return flashcardRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // 복습이 도래한 카드
    public List<FlashcardResponse> listDue(String userId) {
        return flashcardRepository.findDueByUser(userId, LocalDateTime.now()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FlashcardResponse review(Long id, int quality) {
        Flashcard card = flashcardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다: " + id));
        card.applyReview(quality);
        return toResponse(card);
    }

    public long countByUser(String userId) {
        return flashcardRepository.countByUserId(userId);
    }

    private FlashcardResponse toResponse(Flashcard card) {
        return new FlashcardResponse(
                card.getId(),
                card.getFront(),
                card.getBack(),
                card.getAudioBase64(),
                card.getSource(),
                card.getBoxLevel(),
                card.getNextReviewAt() == null ? null : card.getNextReviewAt().format(FMT)
        );
    }
}
