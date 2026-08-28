package com.ktalk.domain.flashcard.repository;

import com.ktalk.domain.flashcard.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {

    List<Flashcard> findByUserIdOrderByCreatedAtDesc(String userId);

    // 복습이 도래한 카드 (nextReviewAt <= now)
    @Query("SELECT f FROM Flashcard f WHERE f.userId = :userId AND f.nextReviewAt <= :now ORDER BY f.nextReviewAt ASC")
    List<Flashcard> findDueByUser(String userId, LocalDateTime now);

    long countByUserId(String userId);
}
